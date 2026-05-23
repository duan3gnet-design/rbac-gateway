package com.api.gateway.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Consumer group riêng cho api-gateway — mỗi instance trong cluster
     * đều nhận message (fan-out behavior không cần, dùng group để load-balance
     * nếu scale gateway lên nhiều pod).
     */
    private static final String GROUP_ID = "api-gateway-rbac-sync";

    @Bean
    public ConsumerFactory<String, Object> rbacConsumerFactory() {
        JacksonJsonDeserializer<Object> jsonDeserializer = new JacksonJsonDeserializer<>(Object.class);
        // Trust tất cả packages từ auth-service event package
        jsonDeserializer.addTrustedPackages("com.auth.service.event");
        jsonDeserializer.setUseTypeMapperForKey(false);

        return new DefaultKafkaConsumerFactory<>(
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                        ConsumerConfig.GROUP_ID_CONFIG,           GROUP_ID,
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest",
                        // Manual ACK — chỉ commit offset sau khi xử lý thành công
                        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                            ErrorHandlingDeserializer.class,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                            ErrorHandlingDeserializer.class,
                        ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS,
                            StringDeserializer.class,
                        ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS,
                        JacksonJsonDeserializer.class
                ),
                new StringDeserializer(),
                jsonDeserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> rbacKafkaListenerContainerFactory(
            ConsumerFactory<String, Object> rbacConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(rbacConsumerFactory);
        factory.getContainerProperties().setObservationEnabled(true);

        // Manual ACK — đảm bảo at-least-once processing
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Retry 3 lần, cách nhau 1 giây, trước khi gửi vào dead-letter
        factory.setCommonErrorHandler(
                new DefaultErrorHandler(new FixedBackOff(1_000L, 3L)));

        return factory;
    }
}
