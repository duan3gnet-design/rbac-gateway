package com.auth.service.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Filter xử lý authentication cho hai loại request:
 *
 * 1. Request từ api-gateway (user request):
 *    Header: X-User-Name + X-User-Roles
 *    → Set UsernamePasswordAuthenticationToken với roles của user
 *
 * 2. Request từ internal service (service-to-service):
 *    Header: X-Internal-Secret
 *    → Verify secret, nếu đúng set system principal với ROLE_INTERNAL
 */
@Component
public class GatewayAuthFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PRINCIPAL = "system";

    @Value("${app.internal.secret}")
    private String internalSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String username       = request.getHeader("X-User-Name");
        String rolesHeader    = request.getHeader("X-User-Roles");
        String receivedSecret = request.getHeader("X-Internal-Secret");

        if (username != null && rolesHeader != null) {
            // ── Case 1: request từ api-gateway ──────────────────────────
            List<GrantedAuthority> authorities = Arrays.stream(rolesHeader.split(","))
                    .map(String::trim)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(username, null, authorities));

        } else if (receivedSecret != null && internalSecret.equals(receivedSecret)) {
            // ── Case 2: request từ internal service ─────────────────────
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            INTERNAL_PRINCIPAL,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))));
        }
        // else: không có header hợp lệ → SecurityContext trống → Spring Security tự xử lý 401/403

        chain.doFilter(request, response);
    }
}
