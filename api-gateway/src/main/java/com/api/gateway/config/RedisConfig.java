//package com.api.gateway.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.redis.cache.RedisCacheConfiguration;
//import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
//import org.springframework.data.redis.serializer.RedisSerializationContext;
//
//import java.time.Duration;
//
//@Configuration
//public class RedisConfig {
//
//    @Bean
//    public RedisCacheConfiguration cacheConfiguration() {
//        // Tận dụng hàm unsafe tích hợp sẵn để tự động mapping thông tin class của Jackson 3
//        GenericJacksonJsonRedisSerializer jsonSerializer = GenericJacksonJsonRedisSerializer.builder()
//                .enableSpringCacheNullValueSupport()
//                .enableUnsafeDefaultTyping() // <--- Giải pháp không lo sai package import
//                .build();
//
//        return RedisCacheConfiguration.defaultCacheConfig()
//                .entryTtl(Duration.ofMinutes(10))
//                .disableCachingNullValues()
//                .serializeValuesWith(RedisSerializationContext.SerializationPair
//                        .fromSerializer(jsonSerializer));
//    }
//}