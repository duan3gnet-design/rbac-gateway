package com.auth.service.service;

import com.auth.service.dto.PermissionResponse;
import com.auth.service.dto.RoleRequest;
import com.auth.service.dto.RoleResponse;
import com.auth.service.entity.Permission;
import com.auth.service.entity.Role;
import com.auth.service.repository.PermissionRepository;
import com.auth.service.repository.RoleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRoleService {

    private final RoleRepository       roleRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionService    permissionService;

    // ─── Read ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RoleResponse> findAll() {
        return roleRepository.findAllWithPermissions()
                .stream()
                .map(this::toResponse)
                .sorted(Comparator.comparing(RoleResponse::name))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse findById(Long id) {
        Role role = roleRepository.findAllWithPermissions()
                .stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + id));
        return toResponse(role);
    }

    // ─── Create ───────────────────────────────────────────────────────────────

    @Transactional
    public RoleResponse create(RoleRequest req) {
        if (roleRepository.existsByName(req.name())) {
            throw new IllegalArgumentException("Role already exists: " + req.name());
        }
        Role role = new Role();
        role.setName(req.name());
        role.setPermissions(resolvePermissions(req.permissionIds()));

        Role saved = roleRepository.save(role);
        log.info("[Admin] Created role id={} name={}", saved.getId(), saved.getName());
        permissionService.evictAllPermissions();
        return toResponse(saved);
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    @Transactional
    public RoleResponse update(Long id, RoleRequest req) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + id));

        if (!role.getName().equals(req.name()) && roleRepository.existsByName(req.name())) {
            throw new IllegalArgumentException("Role name already taken: " + req.name());
        }
        role.setName(req.name());
        role.setPermissions(resolvePermissions(req.permissionIds()));

        Role saved = roleRepository.save(role);
        log.info("[Admin] Updated role id={} name={}", id, req.name());
        permissionService.evictAllPermissions();
        return toResponse(saved);
    }

    // ─── Assign permissions ───────────────────────────────────────────────────

    @Transactional
    public RoleResponse assignPermissions(Long id, Set<Long> permissionIds) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + id));

        role.setPermissions(resolvePermissions(permissionIds));
        Role saved = roleRepository.save(role);
        log.info("[Admin] Assigned {} permissions to role id={}", permissionIds.size(), id);
        permissionService.evictAllPermissions();
        return toResponse(saved);
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    @Transactional
    public void delete(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + id));

        int userCount = roleRepository.countUsersByRoleId(id);
        if (userCount > 0) {
            throw new IllegalStateException(
                    "Cannot delete role '%s' — assigned to %d user(s). Unassign first."
                            .formatted(role.getName(), userCount));
        }
        roleRepository.delete(role);
        log.info("[Admin] Deleted role id={} name={}", id, role.getName());
        permissionService.evictAllPermissions();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Set<Permission> resolvePermissions(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return new HashSet<>();
        List<Permission> found = permissionRepository.findAllById(ids);
        if (found.size() != ids.size()) {
            Set<Long> foundIds = new HashSet<>();
            found.forEach(p -> foundIds.add(p.getId()));
            ids.stream()
               .filter(pid -> !foundIds.contains(pid))
               .findFirst()
               .ifPresent(missing -> {
                   throw new EntityNotFoundException("Permission not found: " + missing);
               });
        }
        return new HashSet<>(found);
    }

    private RoleResponse toResponse(Role role) {
        List<PermissionResponse> perms = role.getPermissions().stream()
                .map(p -> new PermissionResponse(
                        p.getId(),
                        p.getResource().getName(),
                        p.getAction().getName(),
                        p.getResource().getName() + ":" + p.getAction().getName()))
                .sorted(Comparator.comparing(PermissionResponse::code))
                .toList();

        int userCount = roleRepository.countUsersByRoleId(role.getId());
        return new RoleResponse(role.getId(), role.getName(), perms, userCount);
    }
}
