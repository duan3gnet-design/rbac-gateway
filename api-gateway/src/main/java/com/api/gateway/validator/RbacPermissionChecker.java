package com.api.gateway.validator;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Set;

@Component
public class RbacPermissionChecker {

    // Map HTTP method + path pattern → permission key
    private static final List<PermissionRule> RULES = List.of(
            new PermissionRule("GET",    "/api/resources/products",        "products:READ"),
            new PermissionRule("GET",    "/api/resources/products/**",     "products:READ"),
            new PermissionRule("GET",    "/api/resources/orders",          "orders:READ"),
            new PermissionRule("GET",    "/api/resources/orders/**",       "orders:READ"),
            new PermissionRule("POST",   "/api/resources/orders",          "orders:CREATE"),
            new PermissionRule("POST",   "/api/resources/orders/**",       "orders:CREATE"),
            new PermissionRule("PUT",    "/api/resources/orders/**",       "orders:UPDATE"),
            new PermissionRule("DELETE", "/api/resources/orders/**",       "orders:DELETE"),
            new PermissionRule("GET",    "/api/resources/admin/users",     "users:READ"),
            new PermissionRule("GET",    "/api/resources/admin/users/**",  "users:READ"),
            new PermissionRule("POST",   "/api/resources/admin/users/**",  "users:CREATE"),
            new PermissionRule("PUT",    "/api/resources/admin/users/**",  "users:UPDATE"),
            new PermissionRule("DELETE", "/api/resources/admin/users/**",  "users:DELETE"),
            new PermissionRule("GET",    "/api/resources/profile/**",      "profile:READ"),
            new PermissionRule("PUT",    "/api/resources/profile/**",      "profile:UPDATE"),
            new PermissionRule("POST",   "/api/auth/logout-all",           "auth:LOGOUT_ALL")
    );

    private final AntPathMatcher matcher = new AntPathMatcher();

    public boolean hasPermission(Set<String> permissions, String method, String path) {
        return RULES.stream()
                .filter(rule -> rule.method().equals(method))
                .filter(rule -> matcher.match(rule.pattern(), path))
                .map(PermissionRule::permission)
                .anyMatch(permissions::contains);
    }

    private record PermissionRule(String method, String pattern, String permission) {}
}