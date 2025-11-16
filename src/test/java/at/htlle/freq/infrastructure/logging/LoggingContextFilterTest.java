package at.htlle.freq.infrastructure.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LoggingContextFilterTest {

    private LoggingContextFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private ListAppender<ILoggingEvent> listAppender;
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @BeforeEach
    void setUp() {
        filter = new LoggingContextFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        listAppender = new ListAppender<>();
        Logger logger = (Logger) LoggerFactory.getLogger(LoggingContextFilter.class);
        listAppender.start();
        logger.addAppender(listAppender);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(LoggingContextFilter.class);
        logger.detachAppender(listAppender);
        listAppender.stop();
    }

    private AtomicReference<String> runFilterAndCaptureUser() throws ServletException, IOException {
        AtomicReference<String> capturedUser = new AtomicReference<>();
        FilterChain chain = new CapturingFilterChain(capturedUser);
        filter.doFilterInternal(request, response, chain);
        return capturedUser;
    }

    private AtomicReference<String> runFilterAndCaptureRequestId() throws ServletException, IOException {
        AtomicReference<String> capturedRequestId = new AtomicReference<>();
        FilterChain chain = (req, res) -> capturedRequestId.set(MDC.get("requestId"));
        filter.doFilterInternal(request, response, chain);
        return capturedRequestId;
    }

    @Test
    void usesPrincipalNameWhenPresent() throws Exception {
        when(request.getUserPrincipal()).thenReturn((Principal) () -> "principalUser");

        AtomicReference<String> capturedUser = runFilterAndCaptureUser();

        assertThat(capturedUser.get()).isEqualTo("principalUser");
    }

    @Test
    void fallsBackToHeaderWhenPrincipalMissing() throws Exception {
        when(request.getUserPrincipal()).thenReturn(null);
        when(request.getHeader("X-User-Id")).thenReturn("headerUser");

        AtomicReference<String> capturedUser = runFilterAndCaptureUser();

        assertThat(capturedUser.get()).isEqualTo("headerUser");
    }

    @Test
    void ignoresBlankPrincipalNameAndUsesHeader() throws Exception {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("   ");
        when(request.getUserPrincipal()).thenReturn(principal);
        when(request.getHeader("X-User-Id")).thenReturn("headerUser");

        AtomicReference<String> capturedUser = runFilterAndCaptureUser();

        assertThat(capturedUser.get()).isEqualTo("headerUser");
    }

    @Test
    void defaultsToAnonymousWhenNoUserInformationAvailable() throws Exception {
        when(request.getUserPrincipal()).thenReturn(null);
        when(request.getHeader("X-User-Id")).thenReturn("   ");

        AtomicReference<String> capturedUser = runFilterAndCaptureUser();

        assertThat(capturedUser.get()).isEqualTo("anonymous");
    }

    @Test
    void propagatesValidRequestIdToMdcAndResponse() throws Exception {
        String validRequestId = "123e4567-e89b-12d3-a456-426614174000";
        when(request.getHeader("X-Request-Id")).thenReturn(validRequestId);

        AtomicReference<String> capturedRequestId = runFilterAndCaptureRequestId();

        assertThat(capturedRequestId.get()).isEqualTo(validRequestId);
        verify(response).setHeader("X-Request-Id", validRequestId);
        assertThat(listAppender.list).isEmpty();
    }

    @Test
    void acceptsPrintableCustomRequestId() throws Exception {
        String customRequestId = "trace-12345_custom";
        when(request.getHeader("X-Request-Id")).thenReturn(customRequestId);

        AtomicReference<String> capturedRequestId = runFilterAndCaptureRequestId();

        assertThat(capturedRequestId.get()).isEqualTo(customRequestId);
        verify(response).setHeader("X-Request-Id", customRequestId);
        assertThat(listAppender.list).isEmpty();
    }

    @Test
    void generatesNewRequestIdWhenHeaderIsEmpty() throws Exception {
        when(request.getHeader("X-Request-Id")).thenReturn("   ");

        AtomicReference<String> capturedRequestId = runFilterAndCaptureRequestId();

        assertThat(capturedRequestId.get()).matches(UUID_PATTERN);
        verify(response).setHeader(eq("X-Request-Id"), eq(capturedRequestId.get()));
        assertWarnLogged();
    }

    @Test
    void generatesNewRequestIdWhenHeaderIsTooLong() throws Exception {
        String tooLongRequestId = "a".repeat(129);
        when(request.getHeader("X-Request-Id")).thenReturn(tooLongRequestId);

        AtomicReference<String> capturedRequestId = runFilterAndCaptureRequestId();

        assertThat(capturedRequestId.get()).matches(UUID_PATTERN);
        verify(response).setHeader(eq("X-Request-Id"), eq(capturedRequestId.get()));
        assertWarnLogged();
    }

    @Test
    void generatesNewRequestIdWhenHeaderContainsControlCharacters() throws Exception {
        when(request.getHeader("X-Request-Id")).thenReturn("bad\nvalue");

        AtomicReference<String> capturedRequestId = runFilterAndCaptureRequestId();

        assertThat(capturedRequestId.get()).matches(UUID_PATTERN);
        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq("X-Request-Id"), headerCaptor.capture());
        assertThat(headerCaptor.getValue()).isEqualTo(capturedRequestId.get());
        assertWarnLogged();
    }

    private void assertWarnLogged() {
        List<ILoggingEvent> events = listAppender.list;
        assertThat(events).anySatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    }

    private static class CapturingFilterChain implements FilterChain {
        private final AtomicReference<String> capturedUser;

        private CapturingFilterChain(AtomicReference<String> capturedUser) {
            this.capturedUser = capturedUser;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            capturedUser.set(MDC.get("user"));
        }
    }
}
