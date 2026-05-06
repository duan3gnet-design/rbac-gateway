package com.api.gateway.filter;

import com.api.gateway.validator.JwtValidator;
import com.api.gateway.validator.RbacPermissionChecker;
import com.auth.service.dto.ClaimsResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements HandlerInterceptor {

    private final JwtValidator          jwtValidator;
    private final RbacPermissionChecker rbacChecker;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<String> PUBLIC_PATHS = List.of(
            "/error",
            "/fallback/auth",
            "/fallback/resource",
            "/actuator/**",
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/validate",
            "/api/auth/google",
            "/oauth2/**",
            "/login/oauth2/**"
    );

    @Override
    public boolean preHandle(HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        String path   = request.getRequestURI();
        String method = request.getMethod();

        log.debug("[JWT] {} {}", method, path);

        // ── 1. Public paths ──────────────────────────────────────────────────
        if (isPublicPath(path)) return true;

        // ── 2. Bearer token ──────────────────────────────────────────────────
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("[JWT] Missing token: {} {}", method, path);
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        // ── 3. Validate JWT ──────────────────────────────────────────────────
        ClaimsResponse claims;
        try {
            claims = jwtValidator.validate(authHeader.substring(7));
        } catch (Exception e) {
            log.debug("[JWT] Invalid token: {}", e.getMessage());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        // ── 4. RBAC check ────────────────────────────────────────────────────
        // Wrap riêng để exception từ DB (view query fail, connection error...)
        // không propagate lên Spring MVC error handler → /error → public path
        // → filter skip → router 404. Fail-secure: mọi lỗi → 403.
        boolean permitted;
        try {
            permitted = rbacChecker.hasPermission(claims.permissions(), method, path);
        } catch (Exception e) {
            log.error("[JWT] RBAC check error for {} {}: {}", method, path, e.getMessage());
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return false;
        }

        if (!permitted) {
            log.debug("[JWT] Forbidden: {} {} | permissions={}", method, path, claims.permissions());
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return false;
        }

        // ── 5. Inject attributes ─────────────────────────────────────────────
        request.setAttribute("X-User-Name",       claims.username());
        request.setAttribute("X-User-Roles",       String.join(",", claims.roles()));
        request.setAttribute("X-User-Permissions", String.join(",", claims.permissions()));
        request.setAttribute("jwt.claims",         claims);

        return true;
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(p -> PATH_MATCHER.match(p, path));
    }
}
