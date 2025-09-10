package com.ron.passly.security;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {

    private final Map<String, List<LocalDateTime>> requestCounts = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS = 5;
    private static final Duration TIME_WINDOW = Duration.ofMinutes(1);

    public boolean isAllowed(String clientId) {
        List<LocalDateTime> requests = requestCounts.computeIfAbsent(clientId, k -> new ArrayList<>());

        LocalDateTime cutoff = LocalDateTime.now().minus(TIME_WINDOW);
        requests.removeIf(time -> time.isBefore(cutoff));

        if (requests.size() >= MAX_REQUESTS) {
            return false;
        }

        requests.add(LocalDateTime.now());
        return true;
    }
}