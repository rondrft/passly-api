package com.ron.passly.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RiskAssessmentService {

    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastFailedAttempts = new ConcurrentHashMap<>();

    public SecurityRiskLevel assessRisk(String clientId, HttpServletRequest request) {
        int riskScore = 0;

        //Recently Failed attempts
        riskScore += calculateFailedAttemptsScore(clientId);

        //Suspicious User-Agent
        riskScore += calculateUserAgentScore(request);

        //Request timing patterns
        riskScore += calculateTimingScore(clientId);

        return mapScoreToRiskLevel(riskScore);
    }


    //Calculate points

    private int calculateFailedAttemptsScore(String clientId) {
        Integer attempts = failedAttempts.getOrDefault(clientId, 0);

        if (attempts == 0) return 0;
        if (attempts <= 2) return 10;
        if (attempts <= 5) return 20;
        return 30;
    }

    private int calculateUserAgentScore(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");

        if (userAgent == null || userAgent.trim().isEmpty()) {
            return 15;
        }

        String ua = userAgent.toLowerCase();
        if (ua.contains("bot") || ua.contains("crawler") || ua.contains("python")) {
            return 10;
        }

        return 0;

    }

    private int calculateTimingScore(String clientId) {
        return 0;
    }

    private SecurityRiskLevel mapScoreToRiskLevel(int score) {
        if (score >= 40) return SecurityRiskLevel.CRITICAL;
        if (score >= 25) return SecurityRiskLevel.HIGH;
        if (score >= 15) return SecurityRiskLevel.MEDIUM;
        return SecurityRiskLevel.LOW;
    }

    public void recordFailedAttempt(String clientId) {
        failedAttempts.merge(clientId, 1, Integer::sum);
        lastFailedAttempts.put(clientId, LocalDateTime.now());
    }

    public void recordSuccessfulAttempt(String clientId) {
        // Reset failed attempts on successful login
        failedAttempts.remove(clientId);
        lastFailedAttempts.remove(clientId);
    }
}
