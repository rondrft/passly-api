package com.ron.passly.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {

    private final Map<String, List<LocalDateTime>> requestCounts = new ConcurrentHashMap<>();
    private final RiskAssessmentService riskAssessmentService;

    @Scheduled(fixedRate = 300000)
    public void cleanupOldEntries() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        requestCounts.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(time -> time.isBefore(cutoff));
            return entry.getValue().isEmpty(); // Remove empty lists
        });
    }

    public RateLimitingService(RiskAssessmentService riskAssessmentService) {
        this.riskAssessmentService = riskAssessmentService;
    }

    public boolean isAllowed(String clientId, HttpServletRequest request) {

        SecurityRiskLevel riskLevel = riskAssessmentService.assessRisk(clientId, request);

        int maxRequest = riskLevel.getMaxRequest();
        Duration timeWindow = riskLevel.getTimeWindow();

        return checkRateLimit(clientId, maxRequest, timeWindow);
    }

    private boolean checkRateLimit(String clientId, int maxRequests, Duration timeWindow) {

        List<LocalDateTime> requests = requestCounts.computeIfAbsent(clientId, k -> new ArrayList<>());

        LocalDateTime cutoff = LocalDateTime.now().minus(timeWindow);
        requests.removeIf(time -> time.isBefore(cutoff));

        if (requests.size() >= maxRequests) {
            return false;
        }

        requests.add(LocalDateTime.now());
        return true;
    }

}