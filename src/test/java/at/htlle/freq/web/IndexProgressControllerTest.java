package at.htlle.freq.web;

import at.htlle.freq.infrastructure.lucene.IndexProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IndexProgressControllerTest {

    @BeforeEach
    void init() {
        IndexProgress.get().start(Map.of("Account", 1));
        IndexProgress.get().finish();
    }

    @Test
    void getStatusDelegatesToSingleton() {
        IndexProgressController controller = new IndexProgressController();
        IndexProgress.Status status = controller.getStatus();
        assertEquals(100, status.percent());
    }
}
