package at.htlle.freq.infrastructure.logging;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

/**
 * Servlet filter that enriches the logging MDC context for every incoming HTTP request.
 * <p>
 * The filter executes with the highest precedence so downstream filters and controllers can rely on the populated
 * MDC keys. It stores the resolved request identifier under {@value #MDC_REQUEST_ID} and the resolved user identifier
 * under {@value #MDC_USER}, making the metadata available to every log statement within the request scope.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggingContextFilter extends OncePerRequestFilter {

    static final String MDC_REQUEST_ID = "requestId";
    static final String MDC_USER = "user";
    private static final String HEADER_REQUEST_ID = "X-Request-Id";

    /**
     * Adds logging metadata for the current request and delegates to the next element of the filter chain.
     * <p>
     * The filter resolves the request identifier from the {@code X-Request-Id} header if present or generates a random
     * UUID otherwise. The active user is determined from the {@link Principal} of the request or, as a fallback, from the
     * {@code X-User-Id} header. Both values are exposed via MDC so that log statements within the request scope contain the
     * populated metadata. The generated or propagated request identifier is also written back to the response header
     * {@code X-Request-Id}.
     * <p>
     * MDC entries are cleaned up in a {@code finally} block to prevent data leakage in case of exceptions thrown by
     * downstream filters or servlet components.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        String user = resolveUser(request);

        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_USER, user);
        response.setHeader(HEADER_REQUEST_ID, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_USER);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(HEADER_REQUEST_ID))
                .filter(id -> !id.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
    }

    private String resolveUser(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal != null) {
            String principalName = principal.getName();
            if (principalName != null && !principalName.isBlank()) {
                return principalName;
            }
        }
        String headerUser = request.getHeader("X-User-Id");
        if (headerUser != null && !headerUser.isBlank()) {
            return headerUser;
        }
        return "anonymous";
    }
}
