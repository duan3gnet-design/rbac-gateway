package com.auth.service.repository;

import com.auth.service.entity.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    Optional<Resource> findByName(String name);

    boolean existsByName(String name);

    /** Đếm số permissions đang dùng resource này — dùng để guard khi xóa. */
    @Query("SELECT COUNT(p) FROM Permission p WHERE p.resource.id = :resourceId")
    int countPermissionsByResourceId(Long resourceId);
}
