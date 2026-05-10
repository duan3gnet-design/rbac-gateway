package com.common.observability.tracing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Tail-based sampling: filter spans SAU KHI xử lý xong (đã có status code).
 *
 * <ul>
 *   <li>actuator / noise paths → DROP</li>
 *   <li>OTel ERROR status      → luôn export</li>
 *   <li>HTTP 4xx/5xx           → luôn export</li>
 *   <li>2xx/3xx                → export theo {@code successRate}</li>
 * </ul>
 */
public class FilteringSpanExporter implements SpanExporter {

    private static final Logger log = LoggerFactory.getLogger(FilteringSpanExporter.class);

    private static final AttributeKey<String>   HTTP_STATUS = AttributeKey.stringKey("status");
    private static final AttributeKey<String> URL_PATH    = AttributeKey.stringKey("uri");

    private final SpanExporter    delegate;
    private final double          successRate;
    private final List<String>    excludedPaths;
    private final RandomGenerator rng = RandomGenerator.getDefault();

    public FilteringSpanExporter(SpanExporter delegate,
                                 double successRate,
                                 List<String> excludedPaths) {
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

    // ── filter logic ─────────────────────────────────────────────────────────

    private boolean shouldExport(SpanData span) {
        if (span.getSpanContext() != null) return true;

        if (isExcluded(getPath(span))) return false;

        if (span.getStatus().getStatusCode() == StatusCode.ERROR) return true;

        String status = span.getAttributes().get(HTTP_STATUS);
        if (status != null && Long.parseLong(status) >= 400) {
            return true;
        }

        return successRate >= 1.0 || rng.nextDouble() < successRate;
    }

    private String getPath(SpanData span) {
        return span.getAttributes().get(URL_PATH);
    }

    private boolean isExcluded(String path) {
        if (path == null || path.equals("none")) return true;
        return excludedPaths.stream().anyMatch(path::startsWith);
    }
}
