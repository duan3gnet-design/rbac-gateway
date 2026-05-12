package com.common.observability.tracing;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

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

    private final SpanExporter    delegate;

    public FilteringSpanExporter(SpanExporter delegate) {
        this.delegate      = delegate;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        log.info("export {} spans", spans.size());
        return delegate.export(spans);
    }

    @Override
    public CompletableResultCode flush()    { return delegate.flush(); }

    @Override
    public CompletableResultCode shutdown() { return delegate.shutdown(); }

    // ── filter logic ─────────────────────────────────────────────────────────

}
