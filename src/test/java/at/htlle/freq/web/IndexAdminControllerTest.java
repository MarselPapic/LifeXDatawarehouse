package at.htlle.freq.web;

import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class IndexAdminControllerTest {

    @Test
    void reindexRunsInBackgroundThread() throws InterruptedException {
        LuceneIndexService lucene = mock(LuceneIndexService.class);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> { latch.countDown(); return null; }).when(lucene).reindexAll();

        IndexAdminController controller = new IndexAdminController(lucene);
        Principal principal = () -> "test-user";
        controller.reindex(principal);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Lucene reindexAll should be invoked asynchronously");
    }
}
