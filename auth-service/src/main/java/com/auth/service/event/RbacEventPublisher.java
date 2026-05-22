package com.auth.service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publish RBAC change events lên Kafka.
 * Key = entity id (string) → đảm bảo cùng entity luôn vào cùng partition
 * → consumer xử lý đúng thứ tự cho mỗi entity.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RbacEventPublisher {

    public static final String TOPIC_PERMISSION = "rbac.permission.events";
    public static final String TOPIC_RESOURCE   = "rbac.resource.events";
    public static final String TOPIC_ACTION     = "rbac.action.events";

    private final KafkaTemplate<String, Object> rbacKafkaTemplate;

    public void publishPermission(PermissionEvent event) {
        send(TOPIC_PERMISSION, String.valueOf(event.id()), event);
    }

    public void publishResource(ResourceEvent event) {
        send(TOPIC_RESOURCE, String.valueOf(event.id()), event);
    }

    public void publishAction(ActionEvent event) {
        send(TOPIC_ACTION, String.valueOf(event.id()), event);
    }

    private void send(String topic, String key, Object payload) {
        CompletableFuture<SendResult<String, Object>> future =
                rbacKafkaTemplate.send(topic, key, payload);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[Kafka] Failed to publish to topic={} key={}: {}", topic, key, ex.getMessage());
            } else {
                log.debug("[Kafka] Published to topic={} partition={} offset={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
