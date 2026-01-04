package at.htlle.freq.infrastructure.lucene;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * Progress hub for Lucene reindexing.
 *
 * Data flow:
 *  - reindexAll() loads totals from every repository and calls start().
 *  - While Camel/schedulers execute the indexXxx() methods they increment the counters through IndexProgress.inc().
 *  - REST/UI layers read status() (see IndexProgressController) and display percentage values.
 *
 * Retry / locking considerations:
 *  - start()/finish() are synchronized to prevent concurrent reindex runs from overwriting the state.
 *  - The done map uses ConcurrentHashMap + AtomicInteger so incremental updates are possible without global locks.
 *
 * Integration points:
 *  - Used by LuceneIndexServiceImpl, Camel timers, and the IndexProgressController.
 *  - The status is logged once reindexAll() completes (see the log.info in LuceneIndexServiceImpl).
 */

/**
 * Thread-safe, lightweight progress tracker for reindexing.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * IndexProgress p = IndexProgress.get();
 * p.start(totalsMap);  // Map<table, total count>
 * p.inc("Account");    // per indexed record
 * p.totalDone();       // sum of completed records
 * p.finish();          // mark completion
 * }</pre>
 *
 * <p>The status can be exposed as JSON via the controller.</p>
 */
public final class IndexProgress {

    private static final IndexProgress INSTANCE = new IndexProgress();
    /**
     * Executes the get operation.
     * @return the  value.
     */
    public static IndexProgress get() { return INSTANCE; }

    private volatile Map<String, Integer> totals = Map.of();              // preserve insertion order
    private final Map<String, AtomicInteger> done = new ConcurrentHashMap<>();
    private volatile long startedAtMs = 0L;
    private volatile boolean active = false;

    /**
     * Creates a new IndexProgress instance.
     */
    private IndexProgress() { }

    /**
     * Sets new totals and begins a fresh measurement.
     * Synchronized so parallel scheduler runs cannot race when calling start().
     */
    public synchronized void start(Map<String, Integer> totals) {
        LinkedHashMap<String, Integer> copy = new LinkedHashMap<>(totals); // stable ordering
        this.totals = copy;
        this.done.clear();
        for (String k : copy.keySet()) {
            this.done.put(k, new AtomicInteger(0));
        }
        this.startedAtMs = System.currentTimeMillis();
        this.active = true;
    }

    /**
     * Increments the counter for a table. Unknown keys are created on the fly.
     * Thread-safe thanks to {@link ConcurrentHashMap} + {@link AtomicInteger}; used concurrently from various Camel threads.
     */
    public void inc(String key) {
        done.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }

    /** Total number of completed records across all tables. */
    /**
     * Executes the total Done operation.
     * @return the computed result.
     */
    public int totalDone() {
        int sum = 0;
        for (AtomicInteger ai : done.values()) sum += ai.get();
        return sum;
    }

    /** Overall target count across all tables. */
    /**
     * Executes the grand Total operation.
     * @return the computed result.
     */
    public int grandTotal() {
        int sum = 0;
        for (Integer v : totals.values()) sum += (v == null ? 0 : v);
        return sum;
    }

    /**
     * Marks the run as finished.
     * Additionally copies the done counters to their totals so the UI reliably sees 100% when the run is complete.
     * Side effect: active=false prevents further status() updates from ongoing timers.
     */
    public synchronized void finish() {
        for (Map.Entry<String, Integer> e : totals.entrySet()) {
            int target = (e.getValue() == null ? 0 : e.getValue());
            done.computeIfAbsent(e.getKey(), k -> new AtomicInteger())
                    .set(target);
        }
        this.active = false;
    }

    /** Indicates whether a run is currently active. */
    /**
     * Returns whether Active is enabled.
     * @return true when Active is enabled; otherwise false.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Provides a snapshot for UI/REST consumption.
     * Uses defensive copies in insertion order so the REST controller returns deterministic JSON.
     */
    public Status status() {
        // Done snapshot in the same order as totals
        LinkedHashMap<String, Integer> doneSnap = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : totals.entrySet()) {
            doneSnap.put(e.getKey(), done.getOrDefault(e.getKey(), new AtomicInteger()).get());
        }
        for (Map.Entry<String, AtomicInteger> entry : done.entrySet()) {
            doneSnap.putIfAbsent(entry.getKey(), entry.getValue().get());
        }
        int gt = grandTotal();
        int td = totalDone();
        int pct = (gt == 0) ? 100 : Math.min(100, (td * 100) / gt);
        return new Status(
                active,
                totals,
                doneSnap,
                gt,
                td,
                pct,
                startedAtMs,
                System.currentTimeMillis()
        );
    }

    /** Compact status record; Jackson serializes it as JSON without additional configuration. */
    public static record Status(
            boolean active,
            Map<String, Integer> totals,
            Map<String, Integer> done,
            int grandTotal,
            int totalDone,
            int percent,
            long startedAtMs,
            long nowMs
    ) {}
}
