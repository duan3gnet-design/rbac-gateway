package com.api.gateway.route;

import java.io.Serializable;
import java.time.Instant;

/**
 * Payload được serialize thành JSON và publish lên Redis channel.
 *
 * <p>Mỗi node subscribe channel sẽ nhận message này và tự invalidate
 * local cache — không cần gọi trực tiếp sang node khác.</p>
 *
 * @param originNodeId  ID của node đã publish (spring.application.instance-id
 *                      hoặc hostname) — dùng để debug/log, không dùng để filter.
 * @param action        Hành động đã xảy ra: "create", "update", "delete", "toggle".
 * @param routeId       ID của route bị thay đổi (nullable với action "reload-all").
 * @param timestamp     Thời điểm publish (epoch millis).
 */
public record RouteRefreshMessage(
        String originNodeId,
        String action,
        String routeId,
        long   timestamp
) implements Serializable {

    /** Factory dùng trong {@link RouteRefreshPublisher}. */
    public static RouteRefreshMessage of(String originNodeId, String action, String routeId) {
        return new RouteRefreshMessage(originNodeId, action, routeId, Instant.now().toEpochMilli());
    }
}
