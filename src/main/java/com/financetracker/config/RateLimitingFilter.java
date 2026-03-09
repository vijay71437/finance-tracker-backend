package com.financetracker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.dto.response.ApiResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiting filter using the token bucket algorithm (Bucket4j).
 *
 * <p>At scale, replace this in-process map with a Redis-backed Bucket4j store
 * so limits are enforced across all API pods consistently.
 *
 * <pre>
 * Dependencies (already in pom.xml):
 *   bucket4j-core — in-memory buckets
 *   bucket4j-redis — Redis backend (add for multi-instance deployments)
 * </pre>
 */
@Component
@Slf4j
public class RateLimitingFilter implements Filter {

    private final int requestsPerMinute;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitingFilter(
            @Value("${app.rate-limit.requests-per-minute:100}") int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    public void doFilter(
            ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String clientIp = extractClientIp(httpRequest);

        Bucket bucket = buckets.computeIfAbsent(clientIp, this::newBucket);

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            httpResponse.setHeader("Retry-After", "60");
            httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(requestsPerMinute));
            httpResponse.setHeader("X-RateLimit-Remaining", "0");

            httpResponse.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.error("Rate limit exceeded. Please retry after 1 minute.",
                            ApiResponse.ErrorDetail.builder().code("RATE_LIMITED").build())));
        }
    }

    private Bucket newBucket(String key) {
        Bandwidth limit = Bandwidth.classic(
                requestsPerMinute,
                Refill.greedy(requestsPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private String extractClientIp(HttpServletRequest request) {
        // Respect X-Forwarded-For from load balancer
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}