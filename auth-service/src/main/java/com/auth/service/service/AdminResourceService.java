package com.auth.service.service;

import com.auth.service.dto.ResourceRequest;
import com.auth.service.dto.ResourceResponse;
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
public class AdminResourceService {

    private final ResourceRepository   resourceRepository;
    private final ActionRepository     actionRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionService    permissionService;

    // ─── Resources ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ResourceResponse> findAllResources() {
        return resourceRepository.findAll()
                .stream()
                .map(this::toResourceResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResourceResponse findResourceById(Long id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Resource not found: " + id));
        return toResourceResponse(resource);
    }

    @Transactional
    public ResourceResponse createResource(ResourceRequest req) {
        String name = req.name().trim().toLowerCase();
        if (resourceRepository.existsByName(name)) {
            throw new IllegalArgumentException("Resource already exists: " + name);
        }
        Resource r = new Resource();
        r.setName(name);
        Resource saved = resourceRepository.save(r);
        log.info("[Admin] Created resource id={} name={}", saved.getId(), saved.getName());
        return toResourceResponse(saved);
    }

    @Transactional
    public ResourceResponse updateResource(Long id, ResourceRequest req) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Resource not found: " + id));

        String name = req.name().trim().toLowerCase();
        if (!resource.getName().equals(name) && resourceRepository.existsByName(name)) {
            throw new IllegalArgumentException("Resource name already taken: " + name);
        }
        resource.setName(name);
        Resource saved = resourceRepository.save(resource);
        log.info("[Admin] Updated resource id={} name={}", id, name);
        permissionService.evictAllPermissions();
        return toResourceResponse(saved);
    }

    @Transactional
    public void deleteResource(Long id) {
        if (!resourceRepository.existsById(id)) {
            throw new EntityNotFoundException("Resource not found: " + id);
        }
        int permCount = resourceRepository.countPermissionsByResourceId(id);
        if (permCount > 0) {
            throw new IllegalStateException(
                    "Cannot delete resource — it is used by %d permission(s). Remove those permissions first."
                            .formatted(permCount));
        }
        resourceRepository.deleteById(id);
        log.info("[Admin] Deleted resource id={}", id);
        permissionService.evictAllPermissions();
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ResourceResponse.ActionDto> findAllActions() {
        return actionRepository.findAll()
                .stream()
                .map(a -> new ResourceResponse.ActionDto(a.getId(), a.getName()))
                .toList();
    }

    @Transactional
    public ResourceResponse.ActionDto createAction(String name) {
        String normalized = name.trim().toUpperCase();
        if (actionRepository.existsByName(normalized)) {
            throw new IllegalArgumentException("Action already exists: " + normalized);
        }
        Action a = new Action();
        a.setName(normalized);
        Action saved = actionRepository.save(a);
        log.info("[Admin] Created action id={} name={}", saved.getId(), saved.getName());
        return new ResourceResponse.ActionDto(saved.getId(), saved.getName());
    }

    @Transactional
    public ResourceResponse.ActionDto updateAction(Long id, String name) {
        Action action = actionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Action not found: " + id));
        String normalized = name.trim().toUpperCase();
        if (!action.getName().equals(normalized) && actionRepository.existsByName(normalized)) {
            throw new IllegalArgumentException("Action name already taken: " + normalized);
        }
        action.setName(normalized);
        Action saved = actionRepository.save(action);
        log.info("[Admin] Updated action id={} name={}", id, normalized);
        permissionService.evictAllPermissions();
        return new ResourceResponse.ActionDto(saved.getId(), saved.getName());
    }

    @Transactional
    public void deleteAction(Long id) {
        if (!actionRepository.existsById(id)) {
            throw new EntityNotFoundException("Action not found: " + id);
        }
        int permCount = actionRepository.countPermissionsByActionId(id);
        if (permCount > 0) {
            throw new IllegalStateException(
                    "Cannot delete action — it is used by %d permission(s). Remove those permissions first."
                            .formatted(permCount));
        }
        actionRepository.deleteById(id);
        log.info("[Admin] Deleted action id={}", id);
        permissionService.evictAllPermissions();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ResourceResponse toResourceResponse(Resource resource) {
        // Lấy các actions đang được dùng với resource này (qua permission table)
        List<Permission> permissions = permissionRepository.findAllByResourceId(resource.getId());
        List<ResourceResponse.ActionDto> actions = permissions.stream()
                .map(Permission::getAction)
                .distinct()
                .map(a -> new ResourceResponse.ActionDto(a.getId(), a.getName()))
                .toList();
        return new ResourceResponse(
                resource.getId(),
                resource.getName(),
                actions,
                permissions.size()
        );
    }
}
