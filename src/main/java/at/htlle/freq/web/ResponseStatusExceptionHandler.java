package at.htlle.freq.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

/**
 * Central logging handler for {@link ResponseStatusException}.
 *
 * <p>Keeps audit logging consistent and adds request identifiers from the MDC.</p>
 */
@ControllerAdvice
public class ResponseStatusExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ResponseStatusExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handle(ResponseStatusException ex, WebRequest request) {
        String requestId = MDC.get("requestId");
        String path = request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : "(unknown path)";

        String statusValue = Integer.toString(ex.getStatusCode().value());
        String reason = ex.getReason() == null ? ex.getStatusCode().toString() : ex.getReason();
        log.warn("Request {} to {} failed with status {}: {}", requestId == null ? "(no-request-id)" : requestId, path,
                statusValue, reason);

        return ResponseEntity.status(ex.getStatusCode()).body(ex.getReason());
    }
}
