package at.htlle.freq.infrastructure.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditLoggerTest {

    @Test
    void createdLogsStructuredAuditEntry() {
        AuditLogger audit = new AuditLogger(new ObjectMapper());
        Logger logger = (Logger) LoggerFactory.getLogger("at.htlle.freq.audit");
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);

        try {
            Map<String, Object> identifiers = new HashMap<>();
            identifiers.put("ServerID", "srv-1");

            audit.created("Server", identifiers, Map.of("serverName", "alpha"));

            assertEquals(1, appender.list.size());
            String message = appender.list.get(0).getFormattedMessage();
            assertTrue(message.contains("action=CREATE"));
            assertTrue(message.contains("entity=Server"));
            assertTrue(message.contains("identifiers={ServerID=srv-1}"));
            assertTrue(message.contains("result=OK"));
            assertTrue(message.contains("\"serverName\":\"alpha\""));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void failedLogsReasonAndData() {
        AuditLogger audit = new AuditLogger(new ObjectMapper());
        Logger logger = (Logger) LoggerFactory.getLogger("at.htlle.freq.audit");
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);

        try {
            Map<String, Object> identifiers = new HashMap<>();
            identifiers.put("ServerID", "srv-2");

            audit.failed("UPDATE", "Server", identifiers, "validation error", Map.of("field", "bad"));

            assertEquals(1, appender.list.size());
            String message = appender.list.get(0).getFormattedMessage();
            assertTrue(message.contains("action=UPDATE"));
            assertTrue(message.contains("entity=Server"));
            assertTrue(message.contains("identifiers={ServerID=srv-2}"));
            assertTrue(message.contains("result=FAIL"));
            assertTrue(message.contains("reason=validation error"));
            assertTrue(message.contains("\"field\":\"bad\""));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void updatedLogsUpdateAction() {
        AuditLogger audit = new AuditLogger(new ObjectMapper());
        Logger logger = (Logger) LoggerFactory.getLogger("at.htlle.freq.audit");
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);

        try {
            Map<String, Object> identifiers = new HashMap<>();
            identifiers.put("ServerID", "srv-3");

            audit.updated("Server", identifiers, Map.of("patchLevel", "2024-10"));

            assertEquals(1, appender.list.size());
            String message = appender.list.get(0).getFormattedMessage();
            assertTrue(message.contains("action=UPDATE"));
            assertTrue(message.contains("entity=Server"));
            assertTrue(message.contains("identifiers={ServerID=srv-3}"));
            assertTrue(message.contains("result=OK"));
            assertTrue(message.contains("\"patchLevel\":\"2024-10\""));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void deletedLogsDeleteAction() {
        AuditLogger audit = new AuditLogger(new ObjectMapper());
        Logger logger = (Logger) LoggerFactory.getLogger("at.htlle.freq.audit");
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);

        try {
            Map<String, Object> identifiers = new HashMap<>();
            identifiers.put("ServerID", "srv-4");

            audit.deleted("Server", identifiers);

            assertEquals(1, appender.list.size());
            String message = appender.list.get(0).getFormattedMessage();
            assertTrue(message.contains("action=DELETE"));
            assertTrue(message.contains("entity=Server"));
            assertTrue(message.contains("identifiers={ServerID=srv-4}"));
            assertTrue(message.contains("result=OK"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }
}
