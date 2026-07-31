package com.example.backend.global.util;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 메모리 기반 슬라이딩 윈도우 요청 제한기. 단일 인스턴스 배포를 전제로 한다.
 */
@Component
public class RateLimiter {

    private final Map<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    public boolean allow(String key, int maxRequests, Duration window) {
        long now = System.currentTimeMillis();
        long windowStart = now - window.toMillis();
        Deque<Long> timestamps = requestLog.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
