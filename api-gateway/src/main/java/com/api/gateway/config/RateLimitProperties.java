package com.api.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(

        /**
         * Số token được nạp lại vào bucket mỗi giây (sustained rate).
         * Ví dụ: 20 → tối đa 20 req/s trong điều kiện bình thường.
         */
        int replenishRate,

        /**
         * Dung lượng tối đa của bucket (burst capacity).
         * Ví dụ: 40 → cho phép burst 40 req/s trong thời gian ngắn.
         */
        int burstCapacity,

        /**
         * Nếu Redis down, true = cho request đi qua (fail-open),
         * false = reject với 429 (fail-closed).
         */
        boolean allowOnRedisFailure,

        /**
         * Danh sách paths không áp dụng rate limit (AntPathMatcher).
         */
        List<String> excludedPaths

) {
    public RateLimitProperties {
        if (replenishRate <= 0) replenishRate = 20;
        if (burstCapacity <= 0) burstCapacity = 40;
        if (excludedPaths == null) excludedPaths = List.of();
    }
}
