package com.side_project.chat_service.common.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Dua traceId, requestId va userId vao MDC de moi dong log truy duoc ve mot request. */
@Component
public class LogContextFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "traceId";
    private static final String REQUEST_ID = "requestId";
    private static final String USER_ID = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        // shortcut: tin header X-User-Id, thay bang JWT khi bat auth
        String userId = request.getHeader("X-User-Id");

        MDC.put(TRACE_ID, traceId);
        MDC.put(REQUEST_ID, requestId);
        MDC.put(USER_ID, userId == null ? "-" : userId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
            MDC.remove(REQUEST_ID);
            MDC.remove(USER_ID);
        }
    }
}
