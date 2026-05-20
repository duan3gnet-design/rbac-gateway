package com.auth.service.repository;

import com.auth.service.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    /** Fetch roles kèm permissions (tránh N+1). */
    @Query("""
        SELECT DISTINCT r FROM Role r
        LEFT JOIN FETCH r.permissions p
        LEFT JOIN FETCH p.resource
        LEFT JOIN FETCH p.action
    """)
    List<Role> findAllWithPermissions();

    /** Đếm số users đang có role này — dùng để hiển thị stats. */
    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.id = :roleId")
    int countUsersByRoleId(Long roleId);
}
