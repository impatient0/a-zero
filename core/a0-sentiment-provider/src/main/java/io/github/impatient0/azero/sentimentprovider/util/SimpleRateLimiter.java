package io.github.impatient0.azero.sentimentprovider.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class SimpleRateLimiter {

    private final long minIntervalNanos;
    private long nextAvailableTime;
    private final ReentrantLock lock = new ReentrantLock();

    public SimpleRateLimiter(int requestsPerMinute) {
        if (requestsPerMinute <= 0) {
            throw new IllegalArgumentException("RPM must be positive");
        }
        this.minIntervalNanos = 60_000_000_000L / requestsPerMinute;
        this.nextAvailableTime = System.nanoTime();
    }

    public void acquire() {
        long waitTimeNanos = 0;

        lock.lock();
        try {
            long now = System.nanoTime();
            long scheduledTime = Math.max(now, nextAvailableTime);

            waitTimeNanos = scheduledTime - now;

            nextAvailableTime = scheduledTime + minIntervalNanos;
        } finally {
            lock.unlock();
        }

        if (waitTimeNanos > 0) {
            try {
                TimeUnit.NANOSECONDS.sleep(waitTimeNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Rate limiter interrupted while waiting.");
                throw new RuntimeException("Rate limiter interrupted", e);
            }
        }
    }
}