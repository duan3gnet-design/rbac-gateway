package com.api.gateway.repository;

import com.api.gateway.entity.RoutePermissionRule;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Query view {@code route_permission_rules}.
 *
 * <p>Không extend {@code CrudRepository} vì view là read-only —
 * extend {@link Repository} (marker interface) để tránh Spring Data
 * expose các method write không cần thiết.</p>
 */
@Component
public interface RoutePermissionRuleRepository extends Repository<RoutePermissionRule, String> {

    /**
     * Load toàn bộ rules từ view.
     *
     * <p>Kết quả được cache trong {@link com.api.gateway.validator.RbacPermissionChecker}
     * và chỉ reload khi có {@link com.api.gateway.route.RouteRefreshEvent}
     * hoặc khi admin assign/remove permissions qua Admin API.</p>
     */
    @Query("SELECT route_id, path_pattern, http_method, permission_code FROM route_permission_rules")
    List<RoutePermissionRule> findAll();
}
