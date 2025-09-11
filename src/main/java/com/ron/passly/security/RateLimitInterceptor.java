package com.ron.passly.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitingService rateLimitingService;

    public RateLimitInterceptor(RateLimitingService rateLimitingService) {
        this.rateLimitingService = rateLimitingService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        if  (request.getRequestURI().endsWith("/api/auth/")) {
            String clientId = getClientId(request);

            if (!RateLimitingService.isAllowed(clientId, request)) {
                handleRateLimitExceeded(response, request);
                return false;
            }
        }
    }

    private void handleRateLimitExceeded(HttpServletResponse response, HttpServletRequest request) {
        response.setStatus(429);
        response.setContentType("application/json");

        String jsonResponse = """
        {
            "error": "Too Many Requests",
            "message": "Rate limit exceeded, Please try again later"
            "retryAfter": 60
        }
        """;

        response.getWriter().write(jsonResponse);
    }

    private String getClientId(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

}