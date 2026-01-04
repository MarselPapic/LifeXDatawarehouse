package at.htlle.freq.web;

import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

class IndexAdminControllerTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;
    private Level originalLevel;
    private boolean originalAdditive;

    private void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(IndexAdminController.class);
        originalLevel = logger.getLevel();
        originalAdditive = logger.isAdditive();
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        if (logger != null && appender != null) {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            logger.setAdditive(originalAdditive);
        }
    }

    @Test
    void reindexRunsInBackgroundThread() throws InterruptedException {
        LuceneIndexService lucene = mock(LuceneIndexService.class);
        TaskExecutor taskExecutor = runnable -> runnable.run();
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> { latch.countDown(); return null; }).when(lucene).reindexAll();

        IndexAdminController controller = new IndexAdminController(lucene, taskExecutor);
        Principal principal = () -> "test-user";
        controller.reindex(principal);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Lucene reindexAll should be invoked asynchronously");
        verify(lucene).reindexAll();
    }

    @Test
    void reindexLogsRequestStartAndSuccess() {
        attachAppender();

        LuceneIndexService lucene = mock(LuceneIndexService.class);
        TaskExecutor taskExecutor = runnable -> runnable.run();

        IndexAdminController controller = new IndexAdminController(lucene, taskExecutor);
        Principal principal = () -> "tester";
        controller.reindex(principal);

        verify(lucene).reindexAll();

        var messages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());

        assertTrue(messages.stream().anyMatch(m -> m.contains("Manual reindex requested (principal=tester)")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("Manual reindex task started (principal=tester)")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("Manual reindex task completed successfully (principal=tester)")));
    }

    @Test
    void reindexLogsErrorWhenTaskFails() {
        attachAppender();

        LuceneIndexService lucene = mock(LuceneIndexService.class);
        doThrow(new RuntimeException("boom")).when(lucene).reindexAll();
        TaskExecutor taskExecutor = runnable -> runnable.run();

        IndexAdminController controller = new IndexAdminController(lucene, taskExecutor);
        Principal principal = () -> "tester";
        controller.reindex(principal);

        verify(lucene).reindexAll();

        var messages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());

        assertTrue(messages.stream().anyMatch(m -> m.contains("Manual reindex task failed (principal=tester)")));
    }
}
