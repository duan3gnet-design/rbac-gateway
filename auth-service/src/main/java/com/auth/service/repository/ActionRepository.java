package com.auth.service.repository;

import com.auth.service.entity.Action;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ActionRepository extends JpaRepository<Action, Long> {

    Optional<Action> findByName(String name);

    boolean existsByName(String name);

    /** Đếm số permissions đang dùng action này — dùng để guard khi xóa. */
    @Query("SELECT COUNT(p) FROM Permission p WHERE p.action.id = :actionId")
    int countPermissionsByActionId(Long actionId);
}
