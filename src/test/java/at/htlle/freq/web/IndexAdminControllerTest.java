package at.htlle.freq.web;

import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.security.Principal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class IndexAdminControllerTest {

    @Test
    void reindexRunsInBackgroundThread() throws InterruptedException {
        LuceneIndexService lucene = mock(LuceneIndexService.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> { latch.countDown(); return null; }).when(lucene).reindexAll();
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; }).when(taskExecutor).execute(any());

        IndexAdminController controller = new IndexAdminController(lucene, taskExecutor);
        Principal principal = () -> "test-user";
        controller.reindex(principal);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Lucene reindexAll should be invoked asynchronously");
        verify(taskExecutor).execute(any());
    }
}
