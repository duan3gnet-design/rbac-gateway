package com.auth.service.service;

import com.auth.service.dto.PermissionRequest;
import com.auth.service.dto.PermissionResponse;
import com.auth.service.entity.Action;
import com.auth.service.entity.Permission;
import com.auth.service.entity.Resource;
import com.auth.service.repository.ActionRepository;
import com.auth.service.repository.PermissionRepository;
import com.auth.service.repository.ResourceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPermissionService {

    private final PermissionRepository permissionRepository;
    private final ResourceRepository   resourceRepository;
    private final ActionRepository     actionRepository;
    private final PermissionService    permissionService; // để evict cache

    // ─── Read ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PermissionResponse> findAll() {
        return permissionRepository.findAllWithDetails()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PermissionResponse findById(Long id) {
        return permissionRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Permission not found: " + id));
    }

    // ─── Create ───────────────────────────────────────────────────────────────

    @Transactional
    public PermissionResponse create(PermissionRequest req) {
        Resource resource = resolveResource(req.resource());
        Action   action   = resolveAction(req.action());

        if (permissionRepository.existsByRoleAndResourceIdAndActionId(
                req.role(), resource.getId(), action.getId())) {
            throw new IllegalArgumentException(
                    "Permission already exists: %s on %s:%s"
                            .formatted(req.role(), req.resource(), req.action()));
        }

        Permission p = new Permission();
        p.setRole(req.role().toUpperCase().startsWith("ROLE_")
                ? req.role() : "ROLE_" + req.role());
        p.setResource(resource);
        p.setAction(action);

        Permission saved = permissionRepository.save(p);
        log.info("[Admin] Created permission id={} role={} {}:{}", saved.getId(), saved.getRole(),
                resource.getName(), action.getName());

        permissionService.evictAllPermissions();
        return toResponse(saved);
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    @Transactional
    public PermissionResponse update(Long id, PermissionRequest req) {
        Permission p = permissionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Permission not found: " + id));

        Resource resource = resolveResource(req.resource());
        Action   action   = resolveAction(req.action());

        // Check duplicate (excluding self)
        permissionRepository.findByRoleAndResourceIdAndActionId(req.role(), resource.getId(), action.getId())
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new IllegalArgumentException(
                                "Duplicate permission: %s on %s:%s"
                                        .formatted(req.role(), req.resource(), req.action()));
                    }
                });

        p.setRole(req.role());
        p.setResource(resource);
        p.setAction(action);

        Permission saved = permissionRepository.save(p);
        log.info("[Admin] Updated permission id={}", id);

        permissionService.evictAllPermissions();
        return toResponse(saved);
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    @Transactional
    public void delete(Long id) {
        if (!permissionRepository.existsById(id)) {
            throw new EntityNotFoundException("Permission not found: " + id);
        }
        permissionRepository.deleteById(id);
        log.info("[Admin] Deleted permission id={}", id);
        permissionService.evictAllPermissions();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Tìm Resource theo tên, nếu chưa tồn tại thì tạo mới (upsert).
     * Giúp admin tạo permission với resource mới mà không cần migration.
     */
    private Resource resolveResource(String name) {
        String normalized = name.trim().toLowerCase();
        return resourceRepository.findByName(normalized)
                .orElseGet(() -> {
                    Resource r = new Resource();
                    r.setName(normalized);
                    log.info("[Admin] Auto-created resource '{}'", normalized);
                    return resourceRepository.save(r);
                });
    }

    /**
     * Tìm Action theo tên, nếu chưa tồn tại thì tạo mới (upsert).
     */
    private Action resolveAction(String name) {
        String normalized = name.trim().toUpperCase();
        return actionRepository.findByName(normalized)
                .orElseGet(() -> {
                    Action a = new Action();
                    a.setName(normalized);
                    log.info("[Admin] Auto-created action '{}'", normalized);
                    return actionRepository.save(a);
                });
    }

    private PermissionResponse toResponse(Permission p) {
        String resourceName = p.getResource().getName();
        String actionName   = p.getAction().getName();
        String code         = resourceName + ":" + actionName;
        return new PermissionResponse(
                p.getId(),
                p.getRole(),
                resourceName,
                actionName,
                code,
                null   // description field — schema chưa có, để null
        );
    }
}
