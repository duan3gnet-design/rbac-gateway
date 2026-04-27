package com.api.gateway.admin.ratelimit;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JDBC repository cho bảng rate_limit_config.
 */
@Repository
public interface RateLimitConfigRepository extends CrudRepository<RateLimitConfigEntity, Long> {

    /** Tìm config override riêng của một user cụ thể. */
    Optional<RateLimitConfigEntity> findByUsername(String username);

    /** Tìm global default row (username IS NULL). */
    @Query("SELECT * FROM rate_limit_config WHERE username IS NULL AND enabled = TRUE LIMIT 1")
    Optional<RateLimitConfigEntity> findGlobalDefault();

    /** Kiểm tra username đã tồn tại chưa (dùng khi tạo mới). */
    boolean existsByUsername(String username);
}
