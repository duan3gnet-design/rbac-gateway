package com.common.observability;

import com.common.observability.tracing.FilteringSpanExporter;
import com.common.observability.tracing.TracingProperties;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.concurrent.Executors;

/**
 * Auto-configuration cho observability — được load tự động bởi Spring Boot
 * qua META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports.
 *
 * <p>Cung cấp:</p>
 * <ul>
 *   <li>Bean {@code "spanExporter"} — FilteringSpanExporter với tail-based sampling,
 *       chỉ active khi {@code tracing.export.otlp.endpoint} được set</li>
 *   <li>Eureka Client — được pull in qua dependency, auto-configured bởi Spring Cloud</li>
 *   <li>Micrometer Prometheus — auto-configured bởi Spring Boot Actuator</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(SpanExporter.class)
@EnableConfigurationProperties(TracingProperties.class)
public class ObservabilityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityAutoConfiguration.class);

    /**
     * FilteringSpanExporter — chỉ active khi endpoint được cấu hình tường minh.
     * Các service không cần tracing (e.g. migration) sẽ không có property này
     * → bean không được tạo → OTel dùng NoopExporter mặc định.
     */
    @Bean("spanExporter")
    @ConditionalOnProperty("tracing.export.otlp.endpoint")
    public SpanExporter spanExporter(TracingProperties props) {
        String endpoint = props.resolvedEndpoint();

        var otlpExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpoint)
                .build();

        log.info("[Observability] FilteringSpanExporter → {} | errors=100%, success={}%, excluded={}",
                endpoint, (int)(props.successRate() * 100), props.excludedPaths());

        return new FilteringSpanExporter(otlpExporter, props.successRate(), props.excludedPaths());
    }

    /**
     * RestClient.Builder được share cho toàn bộ gateway proxy.
     *
     * <p><b>Quan trọng — tracing propagation:</b> {@code observationRegistry(...)} bắt buộc
     * để RestClient tự động:</p>
     * <ol>
     *   <li>Tạo child span cho mỗi outgoing HTTP call (gateway → downstream service)</li>
     *   <li>Inject {@code traceparent} / {@code tracestate} / {@code b3} header vào request
     *       → downstream service nhận được và tiếp tục trace chain</li>
     * </ol>
     * <p>Nếu thiếu {@code observationRegistry}, mỗi downstream call sẽ là một span riêng lẻ
     * không liên kết với trace gốc — Jaeger sẽ thấy các trace rời rạc, không có parent-child.</p>
     */
    @Bean
    public RestClient.Builder restClientBuilder(ObservationRegistry observationRegistry) {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder()
                                .executor(Executors.newVirtualThreadPerTaskExecutor())
                                .build()))
                .observationRegistry(observationRegistry);
    }
}
