package com.api.gateway.repository;

import com.api.gateway.entity.GatewayRouteEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JDBC repository cho bảng gateway_routes.
 */
@Repository
public interface GatewayRouteRepository extends CrudRepository<GatewayRouteEntity, String> {

    /** Chỉ load các route đang bật, sắp xếp theo thứ tự ưu tiên. */
    @Query("SELECT * FROM gateway_routes WHERE enabled = TRUE ORDER BY route_order ASC")
    List<GatewayRouteEntity> findByEnabledTrueOrderByRouteOrderAsc();
}
