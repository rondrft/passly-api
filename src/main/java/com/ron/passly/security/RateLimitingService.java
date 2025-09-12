package com.ron.passly.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiting service with Redis backing and in-memory fallback.
 * Provides adaptive rate limiting based on security risk assessment.
 */
@Service
public class RateLimitingService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private final RiskAssessmentService riskAssessmentService;

    // Fallback storage when Redis is unavailable
    private final Map<String, List<LocalDateTime>> requestCounts = new ConcurrentHashMap<>();
    private final Map<String, RateLimitInfo> rateLimitInfoMap = new ConcurrentHashMap<>();
    private boolean redisAvailable = false;

    // Redis key prefixes
    private static final String RATE_LIMIT_PREFIX = "rate_limit:";
    private static final String FAILED_ATTEMPTS_PREFIX = "failed_attempts:";
    private static final String REQUEST_COUNT_PREFIX = "req_count:";

    // Time windows for different operations
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final Duration API_WINDOW = Duration.ofHours(1);
    private static final Duration PASSWORD_RESET_WINDOW = Duration.ofHours(1);

    public RateLimitingService(RiskAssessmentService riskAssessmentService) {
        this.riskAssessmentService = riskAssessmentService;
    }

    @PostConstruct
    private void init() {
        checkRedisAvailability();
    }

    // Test Redis connection and set availability flag
    private void checkRedisAvailability() {
        try {
            if (redisTemplate == null) {
                redisAvailable = false;
                System.out.println("⚠️ RedisTemplate is null, using in-memory storage");
                return;
            }

            redisTemplate.opsForValue().set("test_connection", "ok", 1, TimeUnit.SECONDS);
            redisAvailable = true;
            System.out.println("✅ Redis available for rate limiting");
        } catch (Exception e) {
            redisAvailable = false;
            System.out.println("⚠️ Redis unavailable, using in-memory: " + e.getMessage());
        }
    }

    // Primary rate limiting check
    public boolean isAllowed(String clientId, HttpServletRequest request) {
        try {
            return checkRateLimit(clientId, request, "login");
        } catch (Exception e) {
            System.err.println("Rate limiting error: " + e.getMessage());
            return true; // Fail-open approach
        }
    }

    // Check rate limit with adaptive limits based on risk assessment
    public boolean checkRateLimit(String clientId, HttpServletRequest request, String operationType) {
        SecurityRiskLevel riskLevel = riskAssessmentService.assessRisk(clientId, request);
        int maxRequests = riskLevel.getMaxRequest();
        Duration timeWindow = riskLevel.getTimeWindow();

        String key = operationType + ":" + clientId;

        if (redisAvailable) {
            return checkRateLimitWithRedis(key, maxRequests, timeWindow);
        } else {
            return checkRateLimitWithMemory(key, maxRequests, timeWindow);
        }
    }

    // Redis-based rate limiting using sorted sets
    private boolean checkRateLimitWithRedis(String key, int maxRequests, Duration timeWindow) {
        try {
            String redisKey = RATE_LIMIT_PREFIX + key;
            long now = Instant.now().toEpochMilli();
            long windowStart = now - timeWindow.toMillis();

            ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

            // Clean expired entries
            zSetOps.removeRangeByScore(redisKey, 0, windowStart);

            // Count current requests in window
            Long currentCount = zSetOps.count(redisKey, windowStart, now);

            if (currentCount != null && currentCount >= maxRequests) {
                incrementBlockedCount(redisKey);
                return false;
            }

            // Add current request
            String requestId = now + ":" + UUID.randomUUID().toString();
            zSetOps.add(redisKey, requestId, now);

            // Set TTL for cleanup
            redisTemplate.expire(redisKey, timeWindow.toSeconds(), TimeUnit.SECONDS);

            return true;

        } catch (Exception e) {
            System.err.println("Redis error, falling back to memory: " + e.getMessage());
            redisAvailable = false;
            return checkRateLimitWithMemory(key, maxRequests, timeWindow);
        }
    }

    // In-memory rate limiting fallback
    private boolean checkRateLimitWithMemory(String key, int maxRequests, Duration timeWindow) {
        List<LocalDateTime> requests = requestCounts.computeIfAbsent(key, k -> new ArrayList<>());
        LocalDateTime cutoff = LocalDateTime.now().minus(timeWindow);

        synchronized (requests) {
            // Remove expired requests
            requests.removeIf(time -> time.isBefore(cutoff));

            if (requests.size() >= maxRequests) {
                return false;
            }

            requests.add(LocalDateTime.now());
        }

        return true;
    }

    // Operation-specific rate limit methods
    public boolean checkLoginLimit(String clientId, HttpServletRequest request) {
        return checkSpecificRateLimit(clientId, request, "login", 5, LOGIN_WINDOW);
    }

    public boolean checkApiLimit(String clientId) {
        return checkSpecificRateLimit(clientId, null, "api", 100, API_WINDOW);
    }

    public boolean checkPasswordResetLimit(String clientId) {
        return checkSpecificRateLimit(clientId, null, "pwd_reset", 3, PASSWORD_RESET_WINDOW);
    }

    public boolean checkCreatePasswordLimit(String clientId) {
        return checkSpecificRateLimit(clientId, null, "create_pwd", 50, API_WINDOW);
    }

    public boolean checkDeletePasswordLimit(String clientId) {
        return checkSpecificRateLimit(clientId, null, "delete_pwd", 20, API_WINDOW);
    }

    private boolean checkSpecificRateLimit(String clientId, HttpServletRequest request,
                                           String operationType, int maxRequests, Duration timeWindow) {
        String key = operationType + ":" + clientId;

        if (redisAvailable) {
            return checkRateLimitWithRedis(key, maxRequests, timeWindow);
        } else {
            return checkRateLimitWithMemory(key, maxRequests, timeWindow);
        }
    }

    // Record failed authentication attempt
    public void recordFailedAttempt(String clientId) {
        if (redisAvailable) {
            try {
                String key = FAILED_ATTEMPTS_PREFIX + clientId;
                redisTemplate.opsForValue().increment(key);
                redisTemplate.expire(key, 24, TimeUnit.HOURS);

                riskAssessmentService.recordFailedAttempt(clientId);
            } catch (Exception e) {
                System.err.println("Error recording failed attempt: " + e.getMessage());
                riskAssessmentService.recordFailedAttempt(clientId);
            }
        } else {
            riskAssessmentService.recordFailedAttempt(clientId);
        }
    }

    // Record successful attempt and clear failed counters
    public void recordSuccessfulAttempt(String clientId) {
        if (redisAvailable) {
            try {
                String key = FAILED_ATTEMPTS_PREFIX + clientId;
                redisTemplate.delete(key);
                riskAssessmentService.recordSuccessfulAttempt(clientId);
            } catch (Exception e) {
                System.err.println("Error clearing attempts: " + e.getMessage());
                riskAssessmentService.recordSuccessfulAttempt(clientId);
            }
        } else {
            riskAssessmentService.recordSuccessfulAttempt(clientId);
        }
    }

    // Get failed attempts count
    public int getFailedAttempts(String clientId) {
        if (redisAvailable) {
            try {
                String key = FAILED_ATTEMPTS_PREFIX + clientId;
                String count = redisTemplate.opsForValue().get(key);
                return count != null ? Integer.parseInt(count) : 0;
            } catch (Exception e) {
                System.err.println("Error getting failed attempts: " + e.getMessage());
                return 0;
            }
        }
        return 0; // RiskAssessmentService handles this in memory
    }

    // Increment blocked requests counter for monitoring
    private void incrementBlockedCount(String redisKey) {
        try {
            String blockedKey = redisKey + ":blocked";
            redisTemplate.opsForValue().increment(blockedKey);
            redisTemplate.expire(blockedKey, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            System.err.println("Error incrementing blocked counter: " + e.getMessage());
        }
    }

    // Reset all limits for a client (admin/testing)
    public void resetLimits(String clientId) {
        if (redisAvailable) {
            try {
                Set<String> keys = redisTemplate.keys(RATE_LIMIT_PREFIX + "*:" + clientId);
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }

                String failedKey = FAILED_ATTEMPTS_PREFIX + clientId;
                redisTemplate.delete(failedKey);

                System.out.println("✅ Limits reset for client: " + clientId);
            } catch (Exception e) {
                System.err.println("Error resetting limits: " + e.getMessage());
            }
        }

        // Clear memory storage
        requestCounts.entrySet().removeIf(entry -> entry.getKey().endsWith(":" + clientId));
        riskAssessmentService.recordSuccessfulAttempt(clientId);
    }

    // Get service statistics
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("redisAvailable", redisAvailable);
        stats.put("memoryEntriesCount", requestCounts.size());

        if (redisAvailable) {
            try {
                Set<String> keys = redisTemplate.keys(RATE_LIMIT_PREFIX + "*");
                stats.put("redisKeysCount", keys != null ? keys.size() : 0);
            } catch (Exception e) {
                stats.put("redisKeysCount", "Error retrieving count");
            }
        }

        return stats;
    }

    // Scheduled cleanup every 5 minutes
    @Scheduled(fixedRate = 300000)
    public void cleanupOldEntries() {
        // Check Redis availability periodically
        checkRedisAvailability();

        // Clean memory storage
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        int cleaned = 0;

        Iterator<Map.Entry<String, List<LocalDateTime>>> iterator = requestCounts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, List<LocalDateTime>> entry = iterator.next();
            List<LocalDateTime> requests = entry.getValue();

            synchronized (requests) {
                requests.removeIf(time -> time.isBefore(cutoff));
                if (requests.isEmpty()) {
                    iterator.remove();
                    cleaned++;
                }
            }
        }

        if (cleaned > 0) {
            System.out.println("🧹 Cleaned " + cleaned + " expired rate limit records");
        }
    }

    // Rate limit information data class
    public static class RateLimitInfo {
        private int requestCount;
        private int blockedCount;
        private LocalDateTime lastRequest;

        public RateLimitInfo() {
            this.requestCount = 0;
            this.blockedCount = 0;
            this.lastRequest = LocalDateTime.now();
        }

        public int getRequestCount() { return requestCount; }
        public void setRequestCount(int requestCount) { this.requestCount = requestCount; }
        public void incrementRequests() {
            this.requestCount++;
            this.lastRequest = LocalDateTime.now();
        }

        public int getBlockedCount() { return blockedCount; }
        public void setBlockedCount(int blockedCount) { this.blockedCount = blockedCount; }
        public void incrementBlocked() { this.blockedCount++; }

        public LocalDateTime getLastRequest() { return lastRequest; }
        public void setLastRequest(LocalDateTime lastRequest) { this.lastRequest = lastRequest; }
    }
}