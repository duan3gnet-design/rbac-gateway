package com.auth.service.service;

import com.auth.service.entity.Action;
import com.auth.service.entity.Permission;
import com.auth.service.entity.Resource;
import com.auth.service.repository.PermissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionService")
class PermissionServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private PermissionService permissionService;

    // ─── Helper ──────────────────────────────────────────────────────────────

    private Permission permission(String resource, String action) {
        Permission p = new Permission();
        Resource r = new Resource(); r.setName(resource);
        Action a = new Action(); a.setName(action);
        p.setResource(r);
        p.setAction(a);
        return p;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getPermissions
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getPermissions")
    class GetPermissions {

        @Test
        @DisplayName("Trả đúng set permission theo format resource:action")
        void shouldReturnFormattedPermissions() {
            when(permissionRepository.findByRoles(List.of("ROLE_USER")))
                    .thenReturn(List.of(
                            permission("products", "READ"),
                            permission("orders", "READ"),
                            permission("orders", "CREATE")
                    ));

            Set<String> result = permissionService.getPermissions(List.of("ROLE_USER"));

            assertThat(result).containsExactlyInAnyOrder(
                    "products:READ", "orders:READ", "orders:CREATE");
        }

        @Test
        @DisplayName("Role không có permission nào → trả empty set")
        void noPermissions_shouldReturnEmpty() {
            when(permissionRepository.findByRoles(List.of("ROLE_GUEST")))
                    .thenReturn(List.of());

            Set<String> result = permissionService.getPermissions(List.of("ROLE_GUEST"));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Multiple roles → tổng hợp tất cả permissions")
        void multipleRoles_shouldAggregatePermissions() {
            when(permissionRepository.findByRoles(List.of("ROLE_USER", "ROLE_ADMIN")))
                    .thenReturn(List.of(
                            permission("products", "READ"),
                            permission("users", "DELETE")
                    ));

            Set<String> result = permissionService.getPermissions(List.of("ROLE_USER", "ROLE_ADMIN"));

            assertThat(result).containsExactlyInAnyOrder("products:READ", "users:DELETE");
        }

        @Test
        @DisplayName("Duplicate permissions từ DB → Set tự deduplicate")
        void duplicatePermissions_shouldBeDeduplicated() {
            when(permissionRepository.findByRoles(any()))
                    .thenReturn(List.of(
                            permission("orders", "READ"),
                            permission("orders", "READ") // duplicate
                    ));

            Set<String> result = permissionService.getPermissions(List.of("ROLE_USER"));

            assertThat(result).hasSize(1).containsExactly("orders:READ");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // evictPermissions & evictAllPermissions
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("cache eviction")
    class CacheEviction {

        @Test
        @DisplayName("evictPermissions không throw và không gọi repository")
        void evictPermissions_shouldNotInteractWithRepository() {
            permissionService.evictPermissions(List.of("ROLE_USER"));
            verifyNoInteractions(permissionRepository);
        }

        @Test
        @DisplayName("evictAllPermissions không throw và không gọi repository")
        void evictAllPermissions_shouldNotInteractWithRepository() {
            permissionService.evictAllPermissions();
            verifyNoInteractions(permissionRepository);
        }
    }
}
