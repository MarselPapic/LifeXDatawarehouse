package at.htlle.freq.infrastructure.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessLoggingFilterTest {

    private AccessLoggingFilter filter;
    private HttpServletRequest request;
    private MockHttpServletResponse response;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        filter = new AccessLoggingFilter();
        request = mock(HttpServletRequest.class);
        response = new MockHttpServletResponse();
        listAppender = new ListAppender<>();
        Logger logger = (Logger) LoggerFactory.getLogger("at.htlle.freq.web");
        listAppender.start();
        logger.addAppender(listAppender);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger("at.htlle.freq.web");
        logger.detachAppender(listAppender);
        listAppender.stop();
        MDC.clear();
    }

    @Test
    void logsMdcAndCleansUpOnSuccess() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");

        FilterChain chain = (ServletRequest req, ServletResponse res) ->
                ((HttpServletResponse) res).setStatus(HttpServletResponse.SC_CREATED);

        filter.doFilterInternal(request, response, chain);

        assertThat(listAppender.list).hasSize(1);
        Map<String, String> mdc = listAppender.list.get(0).getMDCPropertyMap();
        assertThat(mdc)
                .containsEntry("httpMethod", "GET")
                .containsEntry("path", "/api/test")
                .containsEntry("status", Integer.toString(HttpServletResponse.SC_CREATED));
        assertThat(mdc.get("durationMs")).isNotNull();

        assertThat(MDC.get("httpMethod")).isNull();
        assertThat(MDC.get("path")).isNull();
        assertThat(MDC.get("status")).isNull();
        assertThat(MDC.get("durationMs")).isNull();
    }

    @Test
    void logsAndSetsInternalServerErrorOnException() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/fail");

        FilterChain chain = (req, res) -> {
            throw new RuntimeException("boom");
        };

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, chain))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("boom");

        assertThat(listAppender.list).hasSize(1);
        Map<String, String> mdc = listAppender.list.get(0).getMDCPropertyMap();
        assertThat(mdc)
                .containsEntry("httpMethod", "POST")
                .containsEntry("path", "/api/fail")
                .containsEntry("status", Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        assertThat(MDC.get("httpMethod")).isNull();
        assertThat(MDC.get("path")).isNull();
        assertThat(MDC.get("status")).isNull();
        assertThat(MDC.get("durationMs")).isNull();
    }

    @Test
    void exposesFinalStatusIncludingRedirects() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/redirect");

        FilterChain chain = (req, res) -> ((HttpServletResponse) res).sendRedirect("/target");

        filter.doFilterInternal(request, response, chain);

        assertThat(listAppender.list).hasSize(1);
        Map<String, String> mdc = listAppender.list.get(0).getMDCPropertyMap();
        assertThat(mdc.get("status")).isEqualTo(Integer.toString(HttpServletResponse.SC_FOUND));
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FOUND);
    }
}
