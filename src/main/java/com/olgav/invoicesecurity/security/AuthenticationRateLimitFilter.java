package com.olgav.invoicesecurity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthenticationRateLimitFilter extends OncePerRequestFilter {
    private static final int LIMIT = 10;
    private static final long WINDOW_MILLIS = 60_000;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.equals("/api/v1/auth/login") && !path.equals("/api/v1/auth/login/face-verify")
                && !path.equals("/api/v1/auth/verify-email")
                && !path.equals("/api/v1/auth/resend-verification")) {
            chain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);
        String clientIp = resolveClientIp(wrapped);
        String email = extractEmail(wrapped);
        long now = Instant.now().toEpochMilli();
        String key = path + ':' + clientIp + ':' + (email == null ? "unknown" : email.toLowerCase());
        Window current = windows.compute(key, (ignored, old) ->
                old == null || now - old.startedAt() >= WINDOW_MILLIS
                        ? new Window(now, 1) : new Window(old.startedAt(), old.count() + 1));
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= WINDOW_MILLIS);
        }
        if (current.count() > LIMIT) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"data\":null,"
                    + "\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"Too many authentication attempts\"}}");
            return;
        }
        chain.doFilter(wrapped, response);
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private static String extractEmail(ContentCachingRequestWrapper request) {
        try {
            byte[] body = request.getContentAsByteArray();
            if (body == null || body.length == 0) {
                return null;
            }
            String raw = new String(body, StandardCharsets.UTF_8);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            int index = raw.indexOf("\"email\"");
            if (index < 0) {
                return null;
            }
            int start = raw.indexOf(':', index);
            if (start < 0) {
                return null;
            }
            int valueStart = raw.indexOf('"', start + 1);
            if (valueStart < 0) {
                return null;
            }
            int valueEnd = raw.indexOf('"', valueStart + 1);
            if (valueEnd < 0) {
                return null;
            }
            return raw.substring(valueStart + 1, valueEnd).trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    private record Window(long startedAt, int count) {
    }
}
