package com.fenmo.expense.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that runs once per HTTP request.
 *
 * Responsibilities:
 *  1. Generate a short correlation ID and put it in MDC ("requestId").
 *     All log lines emitted during this request will include this ID,
 *     making it trivial to correlate logs in Railway / Datadog / CloudWatch.
 *
 *  2. Expose the ID to the client via the X-Request-Id response header,
 *     so support can ask users to share this ID when reporting bugs.
 *
 *  3. Log method + path + status + duration for every request at INFO level.
 *     Actuator health-check polls (/actuator/health) are filtered out to
 *     avoid log noise.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String requestId = shortUuid();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            String path = request.getRequestURI();

            // Skip noisy actuator health-check polls from the access log
            if (!path.startsWith("/actuator")) {
                log.info("{} {} → {} ({}ms)",
                        request.getMethod(), path, response.getStatus(), durationMs);
            }
            MDC.clear();
        }
    }

    /** First 8 chars of a UUID — collision risk is negligible for request tracing. */
    private static String shortUuid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
