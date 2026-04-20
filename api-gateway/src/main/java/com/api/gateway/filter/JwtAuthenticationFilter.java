package com.api.gateway.filter;

import com.api.gateway.validator.JwtValidator;
import com.api.gateway.validator.RbacPermissionChecker;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtValidator jwtValidator;
    private final RbacPermissionChecker rbacChecker;
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/logout",
            "/api/auth/google", "/oauth2/**", "/login/oauth2/**"
    );

    private static final List<String> ADMIN_PATHS = List.of("/api/admin/**");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest()
                .getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        String token = authHeader.substring(7);

        return jwtValidator.validate(token)
                .flatMap(claims -> {
                    // Admin paths: chỉ cho phép ROLE_ADMIN
                    if (isAdminPath(path) && !claims.roles().contains("ROLE_ADMIN")) {
                        return forbidden(exchange);
                    }

                    // RBAC check cho non-admin paths
                    if (!isAdminPath(path) && !rbacChecker.hasPermission(claims.permissions(), method, path)) {
                        return forbidden(exchange);
                    }

                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header("X-User-Name", claims.username())
                            .header("X-User-Roles", String.join(",", claims.roles()))
                            .header("X-User-Permissions", String.join(",", claims.permissions())) // ✅ optional
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                .onErrorResume(e -> unauthorized(exchange));
    }

    private boolean isPublicPath(String path) {
        AntPathMatcher matcher = new AntPathMatcher();
        return PUBLIC_PATHS.stream().anyMatch(publicPath -> matcher.match(publicPath, path));
    }

    private boolean isAdminPath(String path) {
        AntPathMatcher matcher = new AntPathMatcher();
        return ADMIN_PATHS.stream().anyMatch(adminPath -> matcher.match(adminPath, path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}