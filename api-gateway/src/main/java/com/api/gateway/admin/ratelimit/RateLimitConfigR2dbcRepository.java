package com.api.gateway.admin.ratelimit;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC repository cho bảng rate_limit_config.
 */
public interface RateLimitConfigR2dbcRepository extends R2dbcRepository<RateLimitConfigEntity, Long> {

    /** Tìm config override riêng của một user cụ thể. */
    Mono<RateLimitConfigEntity> findByUsername(String username);

    /** Tìm global default row (username IS NULL). */
    @Query("SELECT * FROM rate_limit_config WHERE username IS NULL AND enabled = TRUE LIMIT 1")
    Mono<RateLimitConfigEntity> findGlobalDefault();

    /** Kiểm tra username đã tồn tại chưa (dùng khi tạo mới). */
    Mono<Boolean> existsByUsername(String username);
}
