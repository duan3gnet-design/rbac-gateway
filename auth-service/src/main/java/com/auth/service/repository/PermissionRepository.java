package com.auth.service.repository;

import com.auth.service.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    @Query("""
        SELECT p FROM Permission p
        JOIN FETCH p.resource
        JOIN FETCH p.action
        WHERE p.role IN :roles
    """)
    List<Permission> findByRoles(List<String> roles);

    @Query("""
        SELECT p FROM Permission p
        JOIN FETCH p.resource
        JOIN FETCH p.action
    """)
    List<Permission> findAllWithDetails();

    boolean existsByRoleAndResourceIdAndActionId(String role, Long resourceId, Long actionId);

    Optional<Permission> findByRoleAndResourceIdAndActionId(String role, Long resourceId, Long actionId);
}
