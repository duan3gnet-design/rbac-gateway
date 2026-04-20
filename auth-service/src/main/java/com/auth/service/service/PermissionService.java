package com.auth.service.service;

import com.auth.service.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;

    @Cacheable(value = "permissions", key = "#roles.toString()")
    public Set<String> getPermissions(List<String> roles) {
        log.debug("[Cache MISS] Loading permissions from DB for roles: {}", roles);
        return permissionRepository.findByRoles(roles)
                .stream()
                .map(p -> p.getResource().getName() + ":" + p.getAction().getName())
                .collect(Collectors.toSet());
    }

    // Gọi khi role của user thay đổi
    @CacheEvict(value = "permissions", key = "#roles.toString()")
    public void evictPermissions(List<String> roles) {
        log.info("[Cache EVICT] Evicted permissions cache for roles: {}", roles);
    }

    // Xóa toàn bộ cache permissions (dùng khi admin cập nhật RBAC)
    @CacheEvict(value = "permissions", allEntries = true)
    public void evictAllPermissions() {
        log.info("[Cache EVICT] Evicted ALL permissions cache");
    }
}
