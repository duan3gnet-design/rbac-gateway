package com.common.observability.tracing;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "tracing")
public record TracingProperties(
        double successRate,
        List<String> excludedPaths,
        ExportProperties export) {

    public record ExportProperties(OtlpProperties otlp) {}
    public record OtlpProperties(String endpoint) {}

    public TracingProperties {
        if (excludedPaths == null) excludedPaths = List.of("/actuator", "/favicon.ico");
        if (successRate <= 0)      successRate   = 0.1;
    }

    public String resolvedEndpoint() {
        if (export != null && export.otlp() != null && export.otlp().endpoint() != null) {
            return export.otlp().endpoint();
        }
        return "http://localhost:4317";
    }
}
