package com.api.gateway.entity;

import org.springframework.data.relational.core.mapping.Table;

/**
 * Ánh xạ view {@code route_permission_rules}.
 *
 * <p>Mỗi row = 1 rule: request khớp {@code pathPattern} và {@code httpMethod}
 * phải có {@code permissionCode} trong JWT claims.</p>
 *
 * <ul>
 *   <li>{@code httpMethod} = {@code null} → rule áp dụng cho mọi HTTP method</li>
 *   <li>{@code permissionCode} = {@code "resource:action"}, ví dụ {@code "products:READ"}</li>
 * </ul>
 */
@Table("route_permission_rules")
public record RoutePermissionRule(
        String routeId,
        String pathPattern,
        String httpMethod,      // null = all methods
        String permissionCode   // "products:READ", "orders:CREATE", ...
) {}
