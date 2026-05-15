package com.api.gateway.validator;

import com.api.gateway.entity.RoutePermissionRule;
import com.api.gateway.repository.RoutePermissionRuleRepository;
import com.api.gateway.route.RouteRefreshEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * RBAC permission checker — load rules từ DB thay vì hardcode.
 *
 * <h3>Data flow</h3>
 * <pre>
 *   DB: gateway_routes ──┐
 *       route_permissions ├──► view route_permission_rules
 *       permissions       │         │
 *       resources         │         │ findAll()
 *       actions  ─────────┘         ▼
 *                           RoutePermissionRuleRepository
 *                                   │
 *                                   ▼
 *                           RbacPermissionChecker.rulesCache
 *                           (AtomicReference<List<RoutePermissionRule>>)
 *                                   │
 *                           hasPermission(permissions, method, path)
 *                                   │
 *                           ┌───────┴────────┐
 *                           │  AntPathMatcher │
 *                           └───────┬────────┘
 *                                   │ match → check permissionCode ∈ JWT claims
 *                                   ▼
 *                              true / false
 * </pre>
 *
 * <h3>Cache strategy</h3>
 * <ul>
 *   <li>Lazy load lần đầu khi {@code hasPermission()} được gọi.</li>
 *   <li>Invalidate (set null) khi nhận {@link RouteRefreshEvent} —
 *       cùng event mà {@code DatabaseRouteLocator} dùng để reload routes.</li>
 *   <li>Reload tự động tại request tiếp theo sau invalidation.</li>
 *   <li>{@code AtomicReference} đảm bảo thread-safety với Virtual Threads
 *       mà không cần {@code synchronized} block.</li>
 * </ul>
 *
 * <h3>http_method = NULL</h3>
 * <p>Route không có {@code Method} predicate trong DB → {@code httpMethod = null}
 * → rule áp dụng cho mọi HTTP method (ví dụ: route {@code /api/resources/**}
 * không filter method, chỉ filter path + permission).</p>
 *
 * <h3>No matching rule → deny</h3>
 * <p>Nếu không có rule nào match (path + method) → {@code hasPermission()} trả
 * {@code false} → 403. Đây là fail-secure behavior: route chưa được cấu hình
 * permission sẽ bị chặn thay vì được phép mặc định.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RbacPermissionChecker {

    private final RoutePermissionRuleRepository ruleRepository;
    private final AntPathMatcher                matcher = new AntPathMatcher();
    private final Lock lock = new ReentrantLock();

    /**
     * Cache danh sách rules từ DB.
     * {@code null} = chưa load hoặc đã bị invalidate → trigger reload.
     */
    private final AtomicReference<List<RoutePermissionRule>> rulesCache =
            new AtomicReference<>(null);

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Kiểm tra xem JWT claims có chứa permission phù hợp với request không.
     *
     * @param jwtPermissions set permissions từ JWT claim ("products:READ", ...)
     * @param method         HTTP method của request ("GET", "POST", ...)
     * @param path           request path ("/api/resources/products/123")
     * @return {@code true} nếu có ít nhất 1 rule match và permission thoả mãn
     */
    public boolean hasPermission(Set<String> jwtPermissions, String method, String path) {
        List<RoutePermissionRule> rules = getRules();

        return rules.stream()
                .filter(rule -> matchesMethod(rule.httpMethod(), method))
                .filter(rule -> rule.pathPattern() != null
                        && matcher.match(rule.pathPattern(), path))
                .map(RoutePermissionRule::permissionCode)
                .anyMatch(jwtPermissions::contains);
    }

    /**
     * Invalidate cache khi admin thay đổi routes hoặc permissions.
     * Cùng event với {@code DatabaseRouteLocator.onRefreshRoutes()}.
     */
    @EventListener(RouteRefreshEvent.class)
    public void onRefreshRoutes(RouteRefreshEvent event) {
        rulesCache.set(null);
        log.info("[RbacPermissionChecker] Rules cache invalidated");
        getRules();
    }

    /**
     * Reload cache thủ công — gọi từ {@code AdminRouteService}
     * sau khi assign/remove permissions (không phát RouteRefreshEvent
     * vì không thay đổi route structure, chỉ thay đổi permission mapping).
     */
    public void invalidateCache() {
        rulesCache.set(null);
        log.info("[RbacPermissionChecker] Rules cache manually invalidated");
        getRules();
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Trả về rules từ cache, load từ DB nếu cache null.
     * Double-checked pattern với AtomicReference — safe với Virtual Threads.
     */
    private List<RoutePermissionRule> getRules() {
        List<RoutePermissionRule> cached = rulesCache.get();
        if (cached != null) return cached;

        try {
            lock.lock();
            List<RoutePermissionRule> loaded = ruleRepository.findAll();

            loaded.add(new RoutePermissionRule("admin-api", "/api/admin/**", "GET", "admin:READ"));
            loaded.add(new RoutePermissionRule("admin-api", "/api/admin/**", "POST", "admin:CREATE"));
            loaded.add(new RoutePermissionRule("admin-api", "/api/admin/**", "PUT", "admin:UPDATE"));
            loaded.add(new RoutePermissionRule("admin-api", "/api/admin/**", "PATCH", "admin:UPDATE"));
            loaded.add(new RoutePermissionRule("admin-api", "/api/admin/**", "DELETE", "admin:DELETE"));

            // compareAndSet: chỉ update nếu vẫn còn null
            // (tránh race condition 2 thread cùng load)
            rulesCache.compareAndSet(null, loaded);

            log.info("[RbacPermissionChecker] Rules reloaded: {} rules from DB", loaded.size());
            return rulesCache.get();
        } catch (Exception ignored) {
            return List.of();
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@code ruleMethod = null} → route không có Method predicate → match mọi method.
     * {@code ruleMethod != null} → phải match chính xác (case-insensitive).
     */
    private boolean matchesMethod(String ruleMethod, String requestMethod) {
        return ruleMethod == null || ruleMethod.equalsIgnoreCase(requestMethod);
    }
}
