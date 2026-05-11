package com.common.observability.tracing;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "tracing")
public record TracingProperties(
        double       successRate,
        List<String> excludedPaths,
        ExportProperties export,
        GroupingProperties grouping) {

    public record ExportProperties(OtlpProperties otlp) {}
    public record OtlpProperties(String endpoint) {}

    /**
     * Config cho {@link GroupedTraceSpanProcessor}.
     *
     * @param enabled         bật/tắt grouped processor (default: true)
     * @param ttl             thời gian tối đa giữ trace trong bộ nhớ (default: 30s)
     * @param maxTracesInFlight số traces đồng thời tối đa (default: 10000)
     */
    public record GroupingProperties(
            boolean  enabled,
            Duration ttl,
            int      maxTracesInFlight) {

        public GroupingProperties {
            if (ttl               == null) ttl               = Duration.ofSeconds(30);
            if (maxTracesInFlight <= 0)    maxTracesInFlight = 10_000;
        }
    }

    public TracingProperties {
        if (excludedPaths == null) excludedPaths = List.of("/actuator", "/favicon.ico");
        if (successRate   <= 0)    successRate   = 0.1;
        if (grouping      == null) grouping      = new GroupingProperties(true, Duration.ofSeconds(30), 10_000);
    }

    public String resolvedEndpoint() {
        if (export != null && export.otlp() != null && export.otlp().endpoint() != null) {
            return export.otlp().endpoint();
        }
        return "http://localhost:4317";
    }
}
