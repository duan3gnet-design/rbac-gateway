package com.api.gateway.kafka;

import com.api.gateway.validator.RbacPermissionChecker;
import com.auth.service.event.ActionEvent;
import com.auth.service.event.PermissionEvent;
import com.auth.service.event.ResourceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Nhận RBAC change events từ auth-service và đồng bộ vào gateway DB.
 *
 * <h3>Sau mỗi sync thành công:</h3>
 * <ol>
 *   <li>Commit Kafka offset (manual ACK).</li>
 *   <li>Invalidate {@link RbacPermissionChecker} cache → các request tiếp theo
 *       sẽ reload rules từ DB đã được cập nhật.</li>
 * </ol>
 *
 * <h3>Error handling:</h3>
 * <p>Nếu xử lý thất bại, {@code DefaultErrorHandler} retry 3 lần (cấu hình trong
 * {@link KafkaConsumerConfig}). Offset KHÔNG được commit → message sẽ được
 * re-delivered. Sau 3 lần thất bại, message đi vào dead-letter topic
 * (nếu cấu hình) hoặc bị skip và log error.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RbacSyncConsumer {

    private final RbacSyncService       syncService;
    private final RbacPermissionChecker rbacChecker;

    // ─── Permission ───────────────────────────────────────────────────────────

    @KafkaListener(
            topics   = "rbac.permission.events",
            groupId  = "api-gateway-rbac-sync",
            containerFactory = "rbacKafkaListenerContainerFactory"
    )
    public void onPermissionEvent(@Payload PermissionEvent event, Acknowledgment ack) {
        log.info("[RbacSync] Permission event: type={} id={} code={}",
                event.eventType(), event.id(), event.code());
        try {
            switch (event.eventType()) {
                case CREATED -> syncService.upsertPermission(event.resource(), event.action());
                case UPDATED -> {
                    syncService.deletePermission(event.oldResource(), event.oldAction());
                    syncService.upsertPermission(event.resource(), event.action());
                }
                case DELETED          -> syncService.deletePermission(event.resource(), event.action());
            }
            ack.acknowledge();
            rbacChecker.invalidateCache();
        } catch (Exception e) {
            log.error("[RbacSync] Failed to process permission event id={}: {}",
                    event.id(), e.getMessage(), e);
            throw e; // re-throw → DefaultErrorHandler retry
        }
    }

    // ─── Resource ─────────────────────────────────────────────────────────────

    @KafkaListener(
            topics   = "rbac.resource.events",
            groupId  = "api-gateway-rbac-sync",
            containerFactory = "rbacKafkaListenerContainerFactory"
    )
    public void onResourceEvent(@Payload ResourceEvent event, Acknowledgment ack) {
        log.info("[RbacSync] Resource event: type={} id={} name={}",
                event.eventType(), event.id(), event.name());
        try {
            switch (event.eventType()) {
                case CREATED -> syncService.upsertResource(event.name());
                case UPDATED -> syncService.renameResource(event.oldName(), event.name()); // upsert by name
                case DELETED -> syncService.deleteResource(event.name());
            }
            ack.acknowledge();
            rbacChecker.invalidateCache();
        } catch (Exception e) {
            log.error("[RbacSync] Failed to process resource event id={}: {}",
                    event.id(), e.getMessage(), e);
            throw e;
        }
    }

    // ─── Action ───────────────────────────────────────────────────────────────

    @KafkaListener(
            topics   = "rbac.action.events",
            groupId  = "api-gateway-rbac-sync",
            containerFactory = "rbacKafkaListenerContainerFactory"
    )
    public void onActionEvent(@Payload ActionEvent event, Acknowledgment ack) {
        log.info("[RbacSync] Action event: type={} id={} name={}",
                event.eventType(), event.id(), event.name());
        try {
            switch (event.eventType()) {
                case CREATED -> syncService.upsertAction(event.name());
                case UPDATED -> syncService.renameAction(event.oldName(), event.name()); // upsert by name
                case DELETED -> syncService.deleteAction(event.name());
            }
            ack.acknowledge();
            rbacChecker.invalidateCache();
        } catch (Exception e) {
            log.error("[RbacSync] Failed to process action event id={}: {}",
                    event.id(), e.getMessage(), e);
            throw e;
        }
    }
}
