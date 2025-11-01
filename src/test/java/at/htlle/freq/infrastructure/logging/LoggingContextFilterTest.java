package at.htlle.freq.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoggingContextFilterTest {

    private LoggingContextFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new LoggingContextFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        MDC.clear();
    }

    private AtomicReference<String> runFilterAndCaptureUser() throws ServletException, IOException {
        AtomicReference<String> capturedUser = new AtomicReference<>();
        FilterChain chain = new CapturingFilterChain(capturedUser);
        filter.doFilterInternal(request, response, chain);
        return capturedUser;
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
