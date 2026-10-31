package com.olgav.invoicesecurity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
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

        long now = Instant.now().toEpochMilli();
        String key = path + ':' + request.getRemoteAddr();
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
        chain.doFilter(request, response);
    }

    private record Window(long startedAt, int count) {
    }
}
