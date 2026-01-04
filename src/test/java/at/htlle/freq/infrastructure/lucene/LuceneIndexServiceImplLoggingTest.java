package at.htlle.freq.infrastructure.lucene;

import at.htlle.freq.domain.Account;
import at.htlle.freq.domain.AccountRepository;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class LuceneIndexServiceImplLoggingTest {

    @TempDir
    Path tempDir;

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;
    private Level originalLevel;
    private boolean originalAdditive;

    private void attachAppender(Class<?> target) {
        logger = (Logger) LoggerFactory.getLogger(target);
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
    void reindexAllLogsStartProgressAndFinish() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        UUID id = UUID.randomUUID();
        Account account = new Account(id, "Acme", null, "contact@example.test", null, null, "AT");
        Mockito.when(accountRepository.findAll()).thenReturn(List.of(account));

        attachAppender(LuceneIndexServiceImpl.class);

        LuceneIndexServiceImpl service = new LuceneIndexServiceImpl(
                accountRepository, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );
        service.setIndexPath(tempDir.resolve("index"));

        service.reindexAll();

        List<String> messages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());

        assertTrue(messages.stream().anyMatch(m -> m.contains("Lucene index cleared (ready for reindex)")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("Starting full Lucene reindex with 1 records.")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("Lucene reindex progress: 1/1 documents (100%)")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("Lucene reindex finished. 1 documents processed.")));
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("Indexed account:")));
    }

    @Test
    void reindexAllLogsErrorWhenClearIndexFails() throws IOException {
        attachAppender(LuceneIndexServiceImpl.class);

        LuceneIndexServiceImpl service = new LuceneIndexServiceImpl();
        service.setIndexPath(tempDir.resolve("error-index"));
        LuceneIndexServiceImpl spyService = spy(service);
        doThrow(new IOException("forced failure")).when(spyService).clearIndex();

        spyService.reindexAll();

        List<String> messages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());

        assertTrue(messages.stream().anyMatch(m -> m.contains("Failed to delete Lucene index before reindexing")));
    }
}
