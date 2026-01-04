package at.htlle.freq.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResponseStatusExceptionHandlerTest {

    private ResponseStatusExceptionHandler handler;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;
    private Level originalLevel;
    private boolean originalAdditive;

    @BeforeEach
    void setUp() {
        handler = new ResponseStatusExceptionHandler();
        listAppender = new ListAppender<>();
        logger = (Logger) LoggerFactory.getLogger(ResponseStatusExceptionHandler.class);
        originalLevel = logger.getLevel();
        originalAdditive = logger.isAdditive();
        logger.setLevel(Level.WARN);
        logger.setAdditive(false);
        listAppender.start();
        logger.addAppender(listAppender);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        if (logger != null && listAppender != null) {
            logger.detachAppender(listAppender);
            logger.setLevel(originalLevel);
            logger.setAdditive(originalAdditive);
            listAppender.stop();
        }
        MDC.clear();
    }

    @Test
    void returnsResponseStatusAndLogsPathWithoutRequestId() {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRequestURI()).thenReturn("/test/path");
        ServletWebRequest webRequest = new ServletWebRequest(servletRequest);
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid input");

        ResponseEntity<Object> response = handler.handle(exception, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("Invalid input");

        List<ILoggingEvent> events = listAppender.list;
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getFormattedMessage())
                .contains("(no-request-id)")
                .contains("/test/path")
                .contains("400")
                .contains("Invalid input");
    }

    @Test
    void logsStatusCodeStringWhenReasonMissing() {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRequestURI()).thenReturn("/status-only");
        ServletWebRequest webRequest = new ServletWebRequest(servletRequest);
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.NOT_FOUND, null);

        handler.handle(exception, webRequest);

        List<ILoggingEvent> events = listAppender.list;
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getFormattedMessage())
                .contains(HttpStatus.NOT_FOUND.toString());
    }
}
