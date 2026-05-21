package com.auth.service.service;

import com.auth.service.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final Lock lock = new ReentrantLock();

    /**
     * Lấy tập permission codes (resource:ACTION) cho danh sách role names.
     * Kết quả được cache theo key = sorted role names.
     */
    @Cacheable(value = "permissions", key = "#roleNames.toString()")
    public Set<String> getPermissions(List<String> roleNames) {
        log.debug("[Cache MISS] Loading permissions from DB for roles: {}", roleNames);
        lock.lock();
        try {
            return permissionRepository.findByRoleNames(roleNames)
                    .stream()
                    .map(p -> p.getResource().getName() + ":" + p.getAction().getName())
                    .collect(Collectors.toSet());
        } catch (Exception ignored) {
            return Set.of();
        } finally {
            lock.unlock();
        }
    }

    @CacheEvict(value = "permissions", key = "#roleNames.toString()")
    public void evictPermissions(List<String> roleNames) {
        log.info("[Cache EVICT] Evicted permissions cache for roles: {}", roleNames);
    }

    @CacheEvict(value = "permissions", allEntries = true)
    public void evictAllPermissions() {
        log.info("[Cache EVICT] Evicted ALL permissions cache");
    }
}
