package at.htlle.freq.infrastructure.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;

/**
 * Servlet filter that logs access metadata for every HTTP request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AccessLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger("at.htlle.freq.web");

    /**
     * Logs request/response metadata after processing the request.
     *
     * @param request incoming HTTP request.
     * @param response HTTP response.
     * @param filterChain servlet filter chain.
     * @throws ServletException when the filter chain fails.
     * @throws IOException when the response cannot be written.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        StatusExposingServletResponse wrappedResponse = new StatusExposingServletResponse(response);
        try {
            filterChain.doFilter(request, wrappedResponse);
        } catch (Exception exception) {
            wrappedResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            throw exception;
        } finally {
            int status = wrappedResponse.getStatus();
            long durationMs = System.currentTimeMillis() - startTime;

            MDC.put("httpMethod", request.getMethod());
            MDC.put("path", request.getRequestURI());
            MDC.put("status", Integer.toString(status));
            MDC.put("durationMs", Long.toString(durationMs));

            LOGGER.info("Handled {} {} with status {} in {} ms", request.getMethod(), request.getRequestURI(), status,
                    durationMs);

            MDC.remove("httpMethod");
            MDC.remove("path");
            MDC.remove("status");
            MDC.remove("durationMs");
        }
    }

    private static class StatusExposingServletResponse extends HttpServletResponseWrapper {

        private int httpStatus = HttpServletResponse.SC_OK;

        StatusExposingServletResponse(HttpServletResponse response) {
            super(response);
        }

        /**
         * Captures the status code for logging.
         *
         * @param sc HTTP status code.
         */
        @Override
        public void setStatus(int sc) {
            super.setStatus(sc);
            this.httpStatus = sc;
        }

        /**
         * Captures error responses for logging.
         *
         * @param sc HTTP status code.
         * @throws IOException when the response cannot be written.
         */
        @Override
        public void sendError(int sc) throws IOException {
            super.sendError(sc);
            this.httpStatus = sc;
        }

        /**
         * Captures error responses with a message for logging.
         *
         * @param sc HTTP status code.
         * @param msg error message.
         * @throws IOException when the response cannot be written.
         */
        @Override
        public void sendError(int sc, String msg) throws IOException {
            super.sendError(sc, msg);
            this.httpStatus = sc;
        }

        /**
         * Captures redirect responses for logging.
         *
         * @param location redirect location.
         * @throws IOException when the response cannot be written.
         */
        @Override
        public void sendRedirect(String location) throws IOException {
            super.sendRedirect(location);
            this.httpStatus = HttpServletResponse.SC_FOUND;
        }

        /**
         * Returns the last status code set on the response.
         *
         * @return HTTP status code.
         */
        @Override
        public int getStatus() {
            return this.httpStatus;
        }
    }
}
