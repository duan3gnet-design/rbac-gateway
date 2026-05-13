package com.common.observability.tracing;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GroupedTraceSpanProcessor — gom tất cả spans của cùng một trace lại
 * rồi flush cả nhóm một lần duy nhất khi trace hoàn chỉnh.
 *
 * <h3>Tại sao cần group?</h3>
 * <p>BatchSpanProcessor mặc định export từng span ngay khi nó kết thúc.
 * Kết quả là backend (Jaeger/Tempo) nhận spans rải rác, chưa có đủ context
 * để quyết định có nên lưu trace hay không (tail-based sampling không thể
 * biết trace lỗi hay không cho đến khi root span kết thúc).</p>
 *
 * <p>GroupedTraceSpanProcessor giữ toàn bộ spans của một trace trong bộ nhớ,
 * chờ đến khi root span kết thúc (in-flight counter về 0), sau đó giao
 * toàn bộ cho {@link SpanExporter} delegate một lần — cho phép
 * {@link FilteringSpanExporter} quyết định export hay drop dựa trên
 * kết quả thực của cả trace (status, latency, error...).</p>
 *
 * <h3>Truyền sampling decision sang downstream services</h3>
 * <p>{@code state.shouldExport} (random double [0,1)) được ghi vào
 * <b>W3C Baggage</b> với key {@code "sampling.rate"} ngay tại {@code onStart}
 * của span đầu tiên trong trace (root span, tức span không có parentId).
 * Baggage tự động lan truyền qua HTTP headers ({@code baggage: sampling.rate=0.42})
 * sang tất cả downstream services nhờ OpenTelemetry propagator, giúp mọi
 * service đưa ra quyết định sampling nhất quán với gateway.</p>
 *
 * <pre>
 *  onStart(rootSpan)
 *    → traceStates.computeIfAbsent(traceId, TraceState::new)  // state.shouldExport khởi tạo ở đây
 *    → nếu span là root (không có parentId hợp lệ):
 *        → Baggage.current().toBuilder()
 *               .put("sampling.rate", String.valueOf(state.shouldExport))
 *               .build()
 *               .storeInContext(Context.current())
 *               .makeCurrent()           // gắn vào context hiện tại
 *        → span.setAttribute(ATTR_SAMPLING_RATE, state.shouldExport)  // ghi vào span để query sau
 *    → state.inFlight.incrementAndGet()
 * </pre>
 *
 * <h3>Cơ chế hoàn chỉnh</h3>
 * <pre>
 *  onStart(span)
 *    → traceStates.computeIfAbsent(traceId, TraceState::new)
 *    → state.inFlight.incrementAndGet()
 *
 *  onEnd(span)
 *    → state.buffer.add(span)
 *    → nếu state.inFlight.decrementAndGet() == 0
 *        → flush state.buffer → delegate.export(spans)
 *        → traceStates.remove(traceId)
 *
 *  TTL eviction (mỗi 30s)
 *    → quét traceStates tìm state.createdAt < now - ttl
 *    → force-flush spans đã buffer để không bị mất
 *    → traceStates.remove(traceId)
 * </pre>
 *
 * <h3>Thread safety</h3>
 * <ul>
 *   <li>{@code traceStates} là {@link ConcurrentHashMap} — safe với concurrent onStart/onEnd</li>
 *   <li>{@code TraceState.buffer} là {@link CopyOnWriteArrayList} — đọc nhiều, ghi ít</li>
 *   <li>{@code TraceState.inFlight} là {@link AtomicInteger} — compare-and-set để tránh
 *       double-flush khi counter về 0</li>
 *   <li>TTL eviction chạy trên single-thread scheduled executor — không race với onEnd
 *       vì flush được guard bởi {@code state.flushed} AtomicBoolean</li>
 * </ul>
 *
 * <h3>Memory bound</h3>
 * <p>Mỗi {@link ReadableSpan} giữ reference đến span data nhưng không sao chép byte[].
 * Với {@code maxTracesInFlight=10_000} và trung bình 10 spans/trace, tổng ~100k spans
 * in-memory — acceptable cho gateway service với Virtual Threads.</p>
 */
public class GroupedTraceSpanProcessor implements SpanProcessor {

    private static final Logger log = LoggerFactory.getLogger(GroupedTraceSpanProcessor.class);

    // ── Config defaults ──────────────────────────────────────────────────────

    private static final AttributeKey<String> HTTP_STATUS = AttributeKey.stringKey("status");
    private static final AttributeKey<String> URL_PATH    = AttributeKey.stringKey("uri");

    /**
     * Baggage key dùng để truyền sampling decision sang downstream services.
     * Downstream đọc: {@code Baggage.current().getEntryValue("should.export")}
     */
    static final String BAGGAGE_SHOULD_EXPORT = "should.export";

    /** Chu kỳ chạy TTL eviction job. */
    private static final Duration EVICTION_INTERVAL = Duration.ofSeconds(30);

    // ── State ────────────────────────────────────────────────────────────────

    private final BatchSpanProcessor delegate;
    private final Duration                       ttl;
    private final int                            maxTracesInFlight;
    private final ConcurrentHashMap<String, TraceState> traceStates = new ConcurrentHashMap<>();
    private final ScheduledExecutorService       evictionScheduler;

    private final double          successRate;
    private final List<String>    excludedPaths;

    // ── Constructors ─────────────────────────────────────────────────────────

    public GroupedTraceSpanProcessor(BatchSpanProcessor delegate, Duration ttl, int maxTracesInFlight,
                                     double successRate,
                                     List<String> excludedPaths) {
        this.delegate          = delegate;
        this.ttl               = ttl;
        this.maxTracesInFlight = maxTracesInFlight;
        this.successRate = successRate;
        this.excludedPaths = excludedPaths;

        // Dùng virtual thread để không block carrier thread
        this.evictionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = Thread.ofVirtual().unstarted(r);
            t.setName("trace-eviction");
            t.setDaemon(true);
            return t;
        });
        evictionScheduler.scheduleAtFixedRate(
                this::evictStaledTraces,
                EVICTION_INTERVAL.toSeconds(),
                EVICTION_INTERVAL.toSeconds(),
                TimeUnit.SECONDS
        );

        log.info("[GroupedTraceSpanProcessor] init — ttl={}s, maxTraces={}",
                ttl.toSeconds(), maxTracesInFlight);
    }

    // ── SpanProcessor ────────────────────────────────────────────────────────

    /**
     * onStart — tăng in-flight counter cho trace này.
     * Mỗi span được đăng ký ngay khi bắt đầu để processor biết
     * còn bao nhiêu spans đang chạy trong trace.
     *
     * <p>Với root span (không có parentId hợp lệ): ghi {@code state.shouldExport}
     * vào W3C Baggage ({@value BAGGAGE_SHOULD_EXPORT}) và span attribute
     * để truyền sampling decision sang tất cả downstream services.</p>
     */
    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        String traceId = span.getSpanContext().getTraceId();

        if (traceStates.size() >= maxTracesInFlight) {
            log.warn("[GroupedTraceSpanProcessor] traceStates đầy ({}/{}), evict stale traces",
                    traceStates.size(), maxTracesInFlight);
            evictStaledTraces();
        }

        TraceState state = traceStates.computeIfAbsent(traceId, id -> new TraceState());
        state.inFlight.incrementAndGet();

        String shouldExport = Baggage.current().getEntryValue(BAGGAGE_SHOULD_EXPORT);

        if (shouldExport != null) {
            state.shouldExport = Boolean.valueOf(shouldExport);
        } else state.shouldExport = successRate >= 1.0 || Math.random() < successRate;

        // Truyền sampling decision sang downstream qua W3C Baggage.
        // Chỉ set trên root span (span không có parent) để tránh ghi đè
        // khi downstream service tạo child spans của chính nó.
        boolean isRootSpan = !span.getParentSpanContext().isValid();

        if (isRootSpan) {
            Baggage updatedBaggage = Baggage.fromContext(parentContext)
                    .toBuilder()
                    .put(BAGGAGE_SHOULD_EXPORT, String.valueOf(state.shouldExport))
                    .build();

            Context updatedContext = updatedBaggage.storeInContext(parentContext);
            // Baggage sẽ được đọc lại bởi HTTP client instrumentation khi tạo outbound request
            updatedContext.makeCurrent();

            log.debug("[GroupedTraceSpanProcessor] root span traceId={} — set sampling.rate={} vào Baggage",
                    traceId, state.shouldExport);
        }
    }

    @Override
    public boolean isStartRequired() {
        return true;
    }

    /**
     * onEnd — buffer span, giảm in-flight counter.
     * Khi counter về 0 → toàn bộ spans của trace đã kết thúc → flush.
     */
    @Override
    public void onEnd(ReadableSpan span) {
        String traceId = span.getSpanContext().getTraceId();

        TraceState state = traceStates.get(traceId);
        if (state == null) {
            // Span kết thúc nhưng không có onStart tương ứng
            // (hiếm — có thể do processor được thêm sau khi span đã start)
            log.debug("[GroupedTraceSpanProcessor] orphan span traceId={}, export trực tiếp", traceId);
            return;
        }

        state.buffer.add(span);

        int remaining = state.inFlight.decrementAndGet();

        if (remaining <= 0) {
            if (traceStates.remove(traceId, state)) {
                flushTrace(traceId, state);
            }
        }
    }

    @Override
    public boolean isEndRequired() {
        return true;
    }

    /**
     * Flush tất cả traces còn trong buffer khi shutdown.
     */
    @Override
    public CompletableResultCode forceFlush() {
        log.info("[GroupedTraceSpanProcessor] forceFlush — {} traces đang buffer", traceStates.size());
        traceStates.forEach((traceId, state) -> {
            if (traceStates.remove(traceId, state)) {
                flushTrace(traceId, state);
            }
        });
        return delegate.forceFlush();
    }

    /**
     * Shutdown: flush còn lại, dừng eviction scheduler, shutdown delegate.
     */
    @Override
    public CompletableResultCode shutdown() {
        evictionScheduler.shutdown();
        forceFlush();
        return delegate.shutdown();
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    /**
     * Evict các traces đã tồn tại quá TTL.
     * Force-flush spans đã buffer để không mất dữ liệu.
     */
    private void evictStaledTraces() {
        Instant cutoff = Instant.now().minus(ttl);
        int[] evicted  = {0};

        traceStates.forEach((traceId, state) -> {
            if (state.createdAt.isBefore(cutoff)) {
                if (traceStates.remove(traceId, state)) {
                    flushTrace(traceId, state);
                    evicted[0]++;
                }
            }
        });

        if (evicted[0] > 0) {
            log.info("[GroupedTraceSpanProcessor] TTL evict: {} traces, {} still in-flight",
                    evicted[0], traceStates.size());
        }
    }

    /**
     * Export toàn bộ spans của một trace ra delegate.
     * Guard bởi {@code state.flushed} CAS để tránh double-export
     * khi onEnd và eviction thread race nhau.
     */
    private void flushTrace(String traceId, TraceState state) {
        if (!state.markFlushed()) {
            return; // đã được flush bởi thread khác
        }

        try {
            List<ReadableSpan> spans = List.copyOf(state.buffer);

            if (isExcluded(spans)) return;

            boolean shouldExport = spans.stream().anyMatch(span -> shouldExport(span.toSpanData()));

            shouldExport = shouldExport || state.shouldExport;
            if (shouldExport) spans.forEach(delegate::onEnd);
        } catch (Exception e) {
            log.error("[GroupedTraceSpanProcessor] export lỗi traceId={}: {}", traceId, e.getMessage());
        }
    }

    private boolean shouldExport(SpanData span) {
        if (span.getStatus().getStatusCode() == StatusCode.ERROR) return true;

        String status = span.getAttributes().get(HTTP_STATUS);

        return status != null && Long.parseLong(status) >= 400;
    }

    private String getPath(SpanData span) {
        return span.getAttributes().get(URL_PATH);
    }

    private boolean isExcluded(List<ReadableSpan> spans) {
        return spans.stream().anyMatch(span -> {
            String path = getPath(span.toSpanData());

            if (!SpanId.getInvalid().equals(span.toSpanData().getParentSpanId())) return false;
            if (path == null || path.equals("none")) return true;
            return excludedPaths.stream().anyMatch(path::startsWith);
        });
    }
    // ── Inner classes ────────────────────────────────────────────────────────

    /**
     * Trạng thái của một trace đang được theo dõi.
     * Immutable fields (createdAt) + thread-safe mutable fields.
     */
    private static final class TraceState {

        /** Thời điểm trace được đăng ký — dùng cho TTL eviction. */
        final Instant createdAt = Instant.now();

        /**
         * Số spans đang "in-flight" (đã onStart nhưng chưa onEnd).
         * Khi về 0 → trace hoàn chỉnh.
         */
        final AtomicInteger inFlight = new AtomicInteger(0);

        /**
         * Buffer các spans đã onEnd.
         * CopyOnWriteArrayList vì đọc (copyOf) xảy ra 1 lần khi flush,
         * ghi (add) xảy ra nhiều lần từ các threads khác nhau.
         */
        final CopyOnWriteArrayList<ReadableSpan> buffer = new CopyOnWriteArrayList<>();

        /**
         * quyết định có export không.
         * Đồng thời được ghi vào W3C Baggage ({@value BAGGAGE_SHOULD_EXPORT})
         * để truyền sang tất cả downstream services nhằm đảm bảo
         * consistent sampling trên toàn bộ distributed trace.
         */
        Boolean shouldExport;

        /**
         * Guard double-flush — CAS từ false → true trước khi export.
         * Nếu trả false → đã flushed bởi thread khác.
         */
        private final java.util.concurrent.atomic.AtomicBoolean flushed =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        boolean markFlushed() {
            return flushed.compareAndSet(false, true);
        }
    }
}
