package com.ron.passly.security;

import java.time.Duration;

public enum SecurityRiskLevel {
    LOW(10, Duration.ofMinutes(1)),      // Normal User
    MEDIUM(5, Duration.ofMinutes(1)),    // Suspicious
    HIGH(2, Duration.ofMinutes(2)),      // Very Suspicious
    CRITICAL(1, Duration.ofMinutes(5));  // Almost blocked

    private final int maxRequest;
    private final Duration timeWindow;

    SecurityRiskLevel(int maxRequest, Duration timeWindow) {
        this.maxRequest = maxRequest;
        this.timeWindow = timeWindow;
    }

    public int getMaxRequest() {
        return maxRequest;
    }

    public Duration getTimeWindow() {
        return timeWindow;
    }
}
