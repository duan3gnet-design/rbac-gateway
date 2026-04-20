package com.api.gateway.repository;

import com.api.gateway.entity.GatewayRouteEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

/**
 * Spring Data R2DBC repository cho bảng gateway_routes.
 */
public interface GatewayRouteR2dbcRepository extends R2dbcRepository<GatewayRouteEntity, String> {

    /** Chỉ load các route đang bật, sắp xếp theo thứ tự ưu tiên. */
    Flux<GatewayRouteEntity> findByEnabledTrueOrderByRouteOrderAsc();
}
