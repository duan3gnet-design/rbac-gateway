package com.api.gateway.filter;

import com.api.gateway.validator.JwtValidator;
import com.api.gateway.validator.RbacPermissionChecker;
import com.auth.service.dto.ClaimsResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * JWT Authentication Interceptor cho Gateway MVC.
 *
 * <p>Chạy trước mọi request (order = Ordered.HIGHEST_PRECEDENCE trong WebMvcConfig).
 * Claims được lưu vào request attribute "jwt.claims" để RateLimitFilter tái dụng.</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements HandlerInterceptor {

    private final JwtValidator jwtValidator;
    private final RbacPermissionChecker rbacChecker;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/logout",
            "/api/auth/validate",
            "/api/auth/google", "/oauth2/**", "/login/oauth2/**"
    );

    private static final List<String> ADMIN_PATHS = List.of("/api/admin/**");

    @Override
    public boolean preHandle(HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String path   = request.getRequestURI();
        String method = request.getMethod();

        if (isPublicPath(path)) {
            return true;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        String token = authHeader.substring(7);

        try {
            ClaimsResponse claims = jwtValidator.validate(token);

            if (isAdminPath(path) && !claims.roles().contains("ROLE_ADMIN")) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                return false;
            }

            if (!isAdminPath(path) && !rbacChecker.hasPermission(claims.permissions(), method, path)) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                return false;
            }

            // Inject headers cho downstream service
            request.setAttribute("X-User-Name", claims.username());
            request.setAttribute("X-User-Roles", String.join(",", claims.roles()));
            request.setAttribute("X-User-Permissions", String.join(",", claims.permissions()));

            // Lưu claims vào attribute để RateLimitFilter tái dụng (tránh parse JWT lần 2)
            request.setAttribute("jwt.claims", claims);

            return true;

        } catch (Exception e) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(p -> PATH_MATCHER.match(p, path));
    }

    private boolean isAdminPath(String path) {
        return ADMIN_PATHS.stream().anyMatch(p -> PATH_MATCHER.match(p, path));
    }
}
