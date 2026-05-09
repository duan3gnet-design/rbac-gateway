package com.api.gateway.filter;

import com.api.gateway.validator.JwtValidator;
import com.api.gateway.validator.RbacPermissionChecker;
import com.auth.service.dto.ClaimsResponse;
import io.micrometer.core.instrument.Counter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
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
@Slf4j
public class JwtAuthenticationFilter implements HandlerInterceptor {

    private final JwtValidator          jwtValidator;
    private final RbacPermissionChecker rbacChecker;
    private final Counter               jwtAuthFailureCounter;
    private final Counter               rbacDeniedCounter;

    public JwtAuthenticationFilter(JwtValidator jwtValidator,
                                   RbacPermissionChecker rbacChecker,
                                   @Qualifier("jwtAuthFailureCounter") Counter jwtAuthFailureCounter,
                                   @Qualifier("rbacDeniedCounter") Counter rbacDeniedCounter) {
        this.jwtValidator          = jwtValidator;
        this.rbacChecker           = rbacChecker;
        this.jwtAuthFailureCounter = jwtAuthFailureCounter;
        this.rbacDeniedCounter     = rbacDeniedCounter;
    }

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
            jwtAuthFailureCounter.increment();
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        // ── 3. Validate JWT ──────────────────────────────────────────────────
        ClaimsResponse claims;
        try {
            claims = jwtValidator.validate(authHeader.substring(7));
        } catch (Exception e) {
            log.debug("[JWT] Invalid token: {}", e.getMessage());
            jwtAuthFailureCounter.increment();
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        // ── 4. RBAC check ────────────────────────────────────────────────────
        boolean permitted;
        try {
            permitted = rbacChecker.hasPermission(claims.permissions(), method, path);
        } catch (Exception e) {
            log.error("[JWT] RBAC check error for {} {}: {}", method, path, e.getMessage());
            rbacDeniedCounter.increment();
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return false;
        }

        if (!permitted) {
            log.debug("[JWT] Forbidden: {} {} | permissions={}", method, path, claims.permissions());
            rbacDeniedCounter.increment();
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
