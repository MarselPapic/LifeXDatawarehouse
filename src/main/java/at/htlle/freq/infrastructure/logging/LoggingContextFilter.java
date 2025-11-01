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

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggingContextFilter extends OncePerRequestFilter {

    static final String MDC_REQUEST_ID = "requestId";
    static final String MDC_USER = "user";
    private static final String HEADER_REQUEST_ID = "X-Request-Id";

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
