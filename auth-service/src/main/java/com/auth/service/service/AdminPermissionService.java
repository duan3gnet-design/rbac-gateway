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
    private final PermissionService    permissionService;

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

        if (permissionRepository.existsByResourceIdAndActionId(resource.getId(), action.getId())) {
            throw new IllegalArgumentException(
                    "Permission already exists: %s:%s".formatted(req.resource(), req.action()));
        }

        Permission p = new Permission();
        p.setResource(resource);
        p.setAction(action);

        Permission saved = permissionRepository.save(p);
        log.info("[Admin] Created permission id={} {}:{}", saved.getId(),
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

        permissionRepository.findByResourceIdAndActionId(resource.getId(), action.getId())
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new IllegalArgumentException(
                                "Duplicate permission: %s:%s".formatted(req.resource(), req.action()));
                    }
                });

        p.setResource(resource);
        p.setAction(action);

        Permission saved = permissionRepository.save(p);
        log.info("[Admin] Updated permission id={} → {}:{}", id,
                resource.getName(), action.getName());

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

    public PermissionResponse toResponse(Permission p) {
        String resourceName = p.getResource().getName();
        String actionName   = p.getAction().getName();
        return new PermissionResponse(
                p.getId(),
                resourceName,
                actionName,
                resourceName + ":" + actionName
        );
    }
}
