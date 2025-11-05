package at.htlle.freq.infrastructure.lucene;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * Fortschritts-Drehscheibe für das Lucene-Reindexing.
 *
 * Datenfluss:
 *  - reindexAll() lädt Totals aus sämtlichen Repositories und ruft start() auf.
 *  - Während Camel/Scheduler die indexXxx()-Methoden abarbeiten, erhöhen diese via IndexProgress.inc() die jeweiligen Zähler.
 *  - REST-/UI-Ebene liest status() aus (siehe IndexProgressController) und zeigt Prozentzahlen an.
 *
 * Retry-/Locking-Aspekte:
 *  - start()/finish() sind synchronized und sichern, dass parallele Reindex-Läufe den Zustand nicht überschreiben.
 *  - done-Map nutzt ConcurrentHashMap + AtomicInteger, wodurch inkrementelle Updates ohne globale Sperren möglich sind.
 *
 * Integrationspunkte:
 *  - Wird von LuceneIndexServiceImpl, Camel-Timern und dem IndexProgressController genutzt.
 *  - Der Status wird im Log ausgegeben, sobald reindexAll() fertig ist (vgl. log.info in LuceneIndexServiceImpl).
 */

/**
 * Thread-sicherer, sehr einfacher Fortschritt-Tracker für das Reindexing.
 *
 * <p>Nutzung:</p>
 * <pre>{@code
 * IndexProgress p = IndexProgress.get();
 * p.start(totalsMap);  // Map<Tabelle, Gesamtanzahl>
 * p.inc("Account");    // pro indexiertem Datensatz
 * p.totalDone();       // Summe Done
 * p.finish();          // Ende markieren
 * }</pre>
 *
 * <p>Status kann als JSON via Controller ausgeliefert werden.</p>
 */
public final class IndexProgress {

    private static final IndexProgress INSTANCE = new IndexProgress();
    public static IndexProgress get() { return INSTANCE; }

    private volatile Map<String, Integer> totals = Map.of();              // Reihenfolge behalten
    private final Map<String, AtomicInteger> done = new ConcurrentHashMap<>();
    private volatile long startedAtMs = 0L;
    private volatile boolean active = false;

    private IndexProgress() { }

    /**
     * Setzt neue Gesamtsummen und startet eine neue Messung.
     * Synchronisiert, damit parallele Scheduler-Läufe nicht konkurrierend start() aufrufen können.
     */
    public synchronized void start(Map<String, Integer> totals) {
        LinkedHashMap<String, Integer> copy = new LinkedHashMap<>(totals); // stabile Reihenfolge
        this.totals = copy;
        this.done.clear();
        for (String k : copy.keySet()) {
            this.done.put(k, new AtomicInteger(0));
        }
        this.startedAtMs = System.currentTimeMillis();
        this.active = true;
    }

    /**
     * Erhöht den Zähler für eine Tabelle. Unbekannte Keys werden on-the-fly angelegt.
     * Thread-sicher dank {@link ConcurrentHashMap} + {@link AtomicInteger}; wird parallel aus unterschiedlichen Camel-Threads genutzt.
     */
    public void inc(String key) {
        done.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }

    /** Gesamte erledigte Datensätze (über alle Tabellen). */
    public int totalDone() {
        int sum = 0;
        for (AtomicInteger ai : done.values()) sum += ai.get();
        return sum;
    }

    /** Gesamtsoll (über alle Tabellen). */
    public int grandTotal() {
        int sum = 0;
        for (Integer v : totals.values()) sum += (v == null ? 0 : v);
        return sum;
    }

    /**
     * Markiert den Durchlauf als beendet.
     * Zusätzlich werden die "done"-Zähler auf die jeweiligen Totals gesetzt,
     * damit die UI garantiert 100% sieht, sobald der Lauf abgeschlossen ist.
     * Nebenwirkung: active=false verhindert weitere status()-Updates durch laufende Timer.
     */
    public synchronized void finish() {
        for (Map.Entry<String, Integer> e : totals.entrySet()) {
            int target = (e.getValue() == null ? 0 : e.getValue());
            done.computeIfAbsent(e.getKey(), k -> new AtomicInteger())
                    .set(target);
        }
        this.active = false;
    }

    /** Gibt an, ob aktuell ein Lauf aktiv ist. */
    public boolean isActive() {
        return active;
    }

    /**
     * Liefert eine Momentaufnahme für die UI/REST.
     * Nutzt defensive Kopien in Einfügereihenfolge, damit der REST-Controller deterministische JSON-Ausgaben liefern kann.
     */
    public Status status() {
        // Done-Snapshot in gleicher Reihenfolge wie totals
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

    /** Kompaktes Status-Record; wird von Jackson problemlos als JSON serialisiert. */
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