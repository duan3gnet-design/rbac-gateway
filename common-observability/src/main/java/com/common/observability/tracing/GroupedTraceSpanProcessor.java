package com.common.observability.tracing;

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
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

    /** Trace tồn tại quá TTL sẽ bị evict (force-flush spans đã có). */
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(30);

    /** Số traces đồng thời tối đa. Vượt quá → warn + evict oldest. */
    private static final int DEFAULT_MAX_TRACES = 10_000;

    /** Chu kỳ chạy TTL eviction job. */
    private static final Duration EVICTION_INTERVAL = Duration.ofSeconds(30);

    // ── State ────────────────────────────────────────────────────────────────

    private final BatchSpanProcessor delegate;
    private final Duration                       ttl;
    private final int                            maxTracesInFlight;
    private final ConcurrentHashMap<String, TraceState> traceStates = new ConcurrentHashMap<>();
    private final ScheduledExecutorService       evictionScheduler;

    // ── Constructors ─────────────────────────────────────────────────────────

    public GroupedTraceSpanProcessor(BatchSpanProcessor delegate) {
        this(delegate, DEFAULT_TTL, DEFAULT_MAX_TRACES);
    }

    public GroupedTraceSpanProcessor(BatchSpanProcessor delegate, Duration ttl, int maxTracesInFlight) {
        this.delegate          = delegate;
        this.ttl               = ttl;
        this.maxTracesInFlight = maxTracesInFlight;

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
     */
    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        String traceId = span.getSpanContext().getTraceId();

        if (traceStates.size() >= maxTracesInFlight) {
            log.warn("[GroupedTraceSpanProcessor] traceStates đầy ({}/{}), evict stale traces",
                    traceStates.size(), maxTracesInFlight);
            evictStaledTraces();
        }

        traceStates
                .computeIfAbsent(traceId, id -> new TraceState())
                .inFlight
                .incrementAndGet();
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
            // → export ngay để không mất dữ liệu
            log.debug("[GroupedTraceSpanProcessor] orphan span traceId={}, export trực tiếp", traceId);
            delegate.onEnd(span);
            delegate.forceFlush();
            return;
        }

        state.buffer.add(span);

        int remaining = state.inFlight.decrementAndGet();

        if (remaining <= 0) {
            // Tất cả spans của trace đã kết thúc → flush
            if (traceStates.remove(traceId, state)) {
                flushTrace(traceId, state, "complete");
            }
            // Nếu remove trả false → eviction thread đã xử lý rồi → bỏ qua
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
                flushTrace(traceId, state, "force-flush");
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
                    flushTrace(traceId, state, "ttl-evict");
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
    private void flushTrace(String traceId, TraceState state, String reason) {
        if (!state.markFlushed()) {
            return; // đã được flush bởi thread khác
        }

        List<ReadableSpan> spans = List.copyOf(state.buffer);
        int queueSize = getQueueSize(delegate);

        try {
//            log.info("queueSize: {}", queueSize);
            if (queueSize + spans.size() > 512) delegate.forceFlush();
            if (!spans.isEmpty()) spans.forEach(delegate::onEnd);
        } catch (Exception e) {
            log.error("[GroupedTraceSpanProcessor] export lỗi traceId={}: {}", traceId, e.getMessage());
        }
    }

    public int getQueueSize(BatchSpanProcessor processor) {
        try {
            // Access the 'worker' field inside BatchSpanProcessor
            Field workerField = BatchSpanProcessor.class.getDeclaredField("worker");
            workerField.setAccessible(true);
            Object worker = workerField.get(processor);

            // Access the 'queue' field inside the Worker class
            Field queueField = worker.getClass().getDeclaredField("queue");
            queueField.setAccessible(true);
            Queue<?> queue = (Queue<?>) queueField.get(worker);

            return queue.size();
        } catch (Exception e) {
            // Handle reflection exceptions
            return -1;
        }
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
