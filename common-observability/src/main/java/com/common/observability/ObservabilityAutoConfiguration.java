package com.common.observability;

import com.common.observability.tracing.FilteringSpanExporter;
import com.common.observability.tracing.GroupedTraceSpanProcessor;
import com.common.observability.tracing.TracingProperties;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.concurrent.Executors;

/**
 * Auto-configuration cho observability — load tự động bởi Spring Boot
 * qua META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports.
 *
 * <h3>Bean được cung cấp</h3>
 * <ul>
 *   <li>{@code spanExporter} — {@link FilteringSpanExporter} với tail-based sampling</li>
 *   <li>{@code groupedTraceSpanProcessor} — {@link GroupedTraceSpanProcessor} buffer spans
 *       theo traceId, flush khi trace hoàn chỉnh (in-flight counter = 0)</li>
 *   <li>{@code restClientBuilder} — RestClient tích hợp tracing propagation</li>
 * </ul>
 *
 * <h3>Pipeline tracing</h3>
 * <pre>
 *   Span bắt đầu
 *     → GroupedTraceSpanProcessor.onStart()  — đăng ký, tăng in-flight counter
 *
 *   Span kết thúc
 *     → GroupedTraceSpanProcessor.onEnd()    — buffer span, giảm counter
 *       → khi counter == 0 (trace hoàn chỉnh):
 *           → FilteringSpanExporter.export() — tail-based sampling
 *               → OtlpGrpcSpanExporter       — gửi sang Jaeger/Tempo
 * </pre>
 *
 * <h3>Tại sao GroupedTraceSpanProcessor + FilteringSpanExporter?</h3>
 * <p>Tail-based sampling cần biết kết quả của toàn bộ trace (có lỗi không? latency cao không?)
 * trước khi quyết định export. Nếu dùng {@link BatchSpanProcessor} trực tiếp,
 * spans được export ngay khi kết thúc — {@link FilteringSpanExporter} chỉ thấy từng span
 * riêng lẻ, không thể quyết định dựa trên trace-level context.
 * {@link GroupedTraceSpanProcessor} giải quyết vấn đề này bằng cách buffer
 * và flush cả nhóm một lần.</p>
 */
@AutoConfiguration
@ConditionalOnClass(SpanExporter.class)
@EnableConfigurationProperties(TracingProperties.class)
public class ObservabilityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityAutoConfiguration.class);

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    // ── SpanExporter (tail-based filtering) ─────────────────────────────────

    /**
     * FilteringSpanExporter — nhận spans từ GroupedTraceSpanProcessor
     * và quyết định export hay drop dựa trên kết quả của toàn trace.
     *
     * <p>Chỉ active khi {@code tracing.export.otlp.endpoint} được set.
     * Service không cần tracing sẽ không có property này → NoopExporter.</p>
     */
    @Bean("spanExporter")
    @ConditionalOnProperty("tracing.export.otlp.endpoint")
    public SpanExporter spanExporter(TracingProperties props) {
        String endpoint = props.resolvedEndpoint();

        var otlpExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpoint)
                .build();

        log.info("[Observability] FilteringSpanExporter → {} | errors=100%, success={}%, excluded={}",
                endpoint, (int) (props.successRate() * 100), props.excludedPaths());

        return new FilteringSpanExporter(otlpExporter);
    }

    // ── GroupedTraceSpanProcessor ────────────────────────────────────────────

    /**
     * GroupedTraceSpanProcessor — gom spans theo traceId, flush khi trace hoàn chỉnh.
     *
     * <p>Được đăng ký vào {@link SdkTracerProvider} bởi Spring Boot OTel autoconfiguration
     * khi bean này có type {@link SpanProcessor} và tên {@code "groupedTraceSpanProcessor"}.</p>
     *
     * <p>Disabled khi {@code tracing.grouping.enabled=false} — trong trường hợp đó
     * OTel dùng {@link BatchSpanProcessor} mặc định (không có grouping).</p>
     *
     * <h4>Quan hệ với spanExporter</h4>
     * <p>GroupedTraceSpanProcessor nhận {@link SpanExporter} (FilteringSpanExporter nếu endpoint
     * được config, NoopExporter nếu không). Nó wrap exporter bên trong BatchSpanProcessor
     * của riêng mình để có batching + retry, sau đó thêm grouping logic ở trên.</p>
     */
    @Bean("groupedTraceSpanProcessor")
    @ConditionalOnMissingBean(name = "groupedTraceSpanProcessor")
    @ConditionalOnProperty("tracing.export.otlp.endpoint")
    public GroupedTraceSpanProcessor groupedTraceSpanProcessor(
            SpanExporter spanExporter,
            TracingProperties props) {

        BatchSpanProcessor batchProcessor = BatchSpanProcessor.builder(spanExporter).build();
        TracingProperties.GroupingProperties grouping = props.grouping();

        log.info("[Observability] GroupedTraceSpanProcessor — ttl={}s, maxTraces={}",
                grouping.ttl().toSeconds(), grouping.maxTracesInFlight());
        return new GroupedTraceSpanProcessor(
                batchProcessor,
                grouping.ttl(),
                grouping.maxTracesInFlight(),
                props.successRate(),
                props.excludedPaths()
        );
    }

    @Bean
    @ConditionalOnProperty("tracing.export.otlp.endpoint")
    public SdkTracerProvider sdkTracerProvider(GroupedTraceSpanProcessor groupedTraceSpanProcessor) {
        Resource serviceResource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), serviceName
                )));

        return SdkTracerProvider.builder()
                .setResource(serviceResource)
                .addSpanProcessor(groupedTraceSpanProcessor)
                .build();
    }

    // ── RestClient ───────────────────────────────────────────────────────────

    /**
     * RestClient.Builder với tracing propagation tự động.
     *
     * <p>{@code observationRegistry} bắt buộc để RestClient:</p>
     * <ol>
     *   <li>Tạo child span cho mỗi outgoing HTTP call</li>
     *   <li>Inject {@code traceparent} header → downstream tiếp tục trace chain</li>
     * </ol>
     * <p>Nếu thiếu, mỗi downstream call là trace riêng lẻ — Jaeger thấy trace rời rạc.</p>
     */
    @Bean
    @ConditionalOnProperty("tracing.export.otlp.endpoint")
    public RestClient.Builder restClientBuilder(ObservationRegistry observationRegistry) {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder()
                                .executor(Executors.newVirtualThreadPerTaskExecutor())
                                .build()))
                .observationRegistry(observationRegistry);
    }
}
