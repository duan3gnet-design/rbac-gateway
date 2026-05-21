package com.auth.service.repository;

import com.auth.service.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    /**
     * Lấy permissions của user dựa trên danh sách role names.
     * Join qua role_permissions → roles.
     */
    @Query("""
        SELECT DISTINCT p FROM Permission p
        JOIN FETCH p.resource
        JOIN FETCH p.action
        JOIN p.roles r
        WHERE r.name IN :roleNames
    """)
    List<Permission> findByRoleNames(List<String> roleNames);

    @Query("""
        SELECT p FROM Permission p
        JOIN FETCH p.resource
        JOIN FETCH p.action
    """)
    List<Permission> findAllWithDetails();

    @Query("""
        SELECT p FROM Permission p
        JOIN FETCH p.resource
        JOIN FETCH p.action
        WHERE p.resource.id = :resourceId
    """)
    List<Permission> findAllByResourceId(Long resourceId);

    boolean existsByResourceIdAndActionId(Long resourceId, Long actionId);

    Optional<Permission> findByResourceIdAndActionId(Long resourceId, Long actionId);
}
