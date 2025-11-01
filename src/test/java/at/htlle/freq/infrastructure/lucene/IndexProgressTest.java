package at.htlle.freq.infrastructure.lucene;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IndexProgressTest {

    private IndexProgress progress;

    @BeforeEach
    void setUp() {
        progress = IndexProgress.get();
        progress.finish(); // reset active flag and counters
    }

    @Test
    void startInitialisesCountersAndStatus() {
        Map<String, Integer> totals = new LinkedHashMap<>();
        totals.put("Account", 5);
        totals.put("Project", 3);

        progress.start(totals);
        progress.inc("Account");
        progress.inc("Account");
        progress.inc("Project");

        IndexProgress.Status status = progress.status();
        assertTrue(status.active());
        assertEquals(5, status.totals().get("Account"));
        assertEquals(2, status.done().get("Account"));
        assertEquals(1, status.done().get("Project"));
        assertEquals(8, status.grandTotal());
        assertEquals(3, status.totalDone());
        assertEquals(37, status.percent());
    }

    @Test
    void finishForcesCompletionAndClearsActiveFlag() {
        Map<String, Integer> totals = Map.of("Account", 2);
        progress.start(totals);
        progress.inc("Account");
        progress.finish();

        IndexProgress.Status status = progress.status();
        assertFalse(status.active());
        assertEquals(2, status.done().get("Account"));
        assertEquals(100, status.percent());
    }

    @Test
    void incCreatesUnknownKeysOnTheFly() {
        Map<String, Integer> totals = Map.of();
        progress.start(totals);
        progress.inc("Custom");
        assertEquals(1, progress.status().done().get("Custom"));
    }
}
