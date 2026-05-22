package com.auth.service.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> rbacProducerFactory() {
        return new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class,
                // Idempotent producer — đảm bảo exactly-once delivery ở broker
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.RETRIES_CONFIG, 3,
                // Thêm type info vào header để consumer deserialize đúng class
                JacksonJsonSerializer.ADD_TYPE_INFO_HEADERS, true
        ));
    }

    @Bean
    public KafkaTemplate<String, Object> rbacKafkaTemplate(
            ProducerFactory<String, Object> rbacProducerFactory) {
        return new KafkaTemplate<>(rbacProducerFactory);
    }
}
