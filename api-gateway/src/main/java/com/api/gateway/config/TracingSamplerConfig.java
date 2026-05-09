package com.api.gateway.config;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.random.RandomGenerator;

@Configuration
@EnableConfigurationProperties(TracingSamplerConfig.TracingProperties.class)
public class TracingSamplerConfig {

    private static final Logger log = LoggerFactory.getLogger(TracingSamplerConfig.class);

    private static final AttributeKey<String>   HTTP_STATUS = AttributeKey.stringKey("status");
    private static final AttributeKey<String> URL_PATH    = AttributeKey.stringKey("http.url");

    @ConfigurationProperties(prefix = "tracing")
    public record TracingProperties(
            double successRate,
            List<String> excludedPaths,
            ExportProperties export) {

        public record ExportProperties(OtlpProperties otlp) {}
        public record OtlpProperties(String endpoint) {}

        // fallback defaults khi không có trong yml
        public TracingProperties {
            if (excludedPaths == null) excludedPaths = List.of("/actuator", "/favicon.ico");
            if (successRate <= 0) successRate = 0.1;
        }
    }

    @Bean("spanExporter")
    public SpanExporter spanExporter(TracingProperties props) {
        String endpoint = props.export() != null && props.export().otlp() != null
                ? props.export().otlp().endpoint()
                : "http://localhost:4317";

        var otlpExporter = OtlpGrpcSpanExporter
                .builder()
                .setEndpoint(endpoint)
                .build();

        log.info("[Tracing] FilteringSpanExporter → {} | errors=100%, success={}%, excluded={}",
                endpoint, (int)(props.successRate() * 100), props.excludedPaths());

        return new FilteringSpanExporter(otlpExporter, props.successRate(), props.excludedPaths());
    }

    // ─────────────────────────────────────────────────────────────────────────

    static class FilteringSpanExporter implements SpanExporter {

        private final SpanExporter    delegate;
        private final double          successRate;
        private final List<String>    excludedPaths;
        private final RandomGenerator rng = RandomGenerator.getDefault();

        FilteringSpanExporter(SpanExporter delegate, double successRate, List<String> excludedPaths) {
            this.delegate      = delegate;
            this.successRate   = Math.clamp(successRate, 0.0, 1.0);
            this.excludedPaths = excludedPaths != null ? excludedPaths : List.of();
        }

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            var toExport = spans.stream().filter(this::shouldExport).toList();

            log.debug("[TailSampling] {}/{} spans passed", toExport.size(), spans.size());

            if (toExport.isEmpty()) return CompletableResultCode.ofSuccess();
            return delegate.export(toExport);
        }

        @Override
        public CompletableResultCode flush()    { return delegate.flush(); }

        @Override
        public CompletableResultCode shutdown() { return delegate.shutdown(); }

        private boolean shouldExport(SpanData span) {
            String path = getPath(span);

            if (isExcluded(path)) return false;

            if (span.getStatus().getStatusCode() == StatusCode.ERROR) return true;

            String status = span.getAttributes().get(HTTP_STATUS);
            if (status != null && Long.parseLong(status) >= 400) {
                return true;
            }

            return successRate >= 1.0 || rng.nextDouble() < successRate;
        }

        @SneakyThrows
        private String getPath(SpanData span) {
            String url = span.getAttributes().get(URL_PATH);

            if (url != null) {
                URI uri = new URI(url);

                return uri.getPath();
            }

            return null;
        }

        private boolean isExcluded(String path) {
            if (path == null) return false;
            return excludedPaths.stream().anyMatch(path::startsWith);
        }
    }
}
