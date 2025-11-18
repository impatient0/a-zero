package io.github.impatient0.azero.preprocessor.cli;

import lombok.extern.slf4j.Slf4j;

/**
 * A simple, thread-safe rate limiter that throttles execution by blocking
 * the calling thread until a permit is available.
 */
@Slf4j
public class SimpleRateLimiter {

    private final long minIntervalMillis;
    private long nextAvailableTime;

    /**
     * @param requestsPerMinute The maximum number of requests allowed per minute.
     */
    public SimpleRateLimiter(int requestsPerMinute) {
        if (requestsPerMinute <= 0) {
            throw new IllegalArgumentException("RPM must be positive");
        }
        this.minIntervalMillis = 60000 / requestsPerMinute;
        this.nextAvailableTime = System.currentTimeMillis();
    }

    /**
     * Blocks the calling thread until it is allowed to proceed according to the rate limit.
     */
    public synchronized void acquire() {
        long now = System.currentTimeMillis();
        long waitTime = nextAvailableTime - now;

        if (waitTime > 0) {
            try {
                log.debug("Rate limit engaged. Sleeping for {} ms...", waitTime);
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Rate limiter interrupted while waiting.");
            }
        }

        // Calculate the next available slot based on when we *actually* proceed
        // or the previously scheduled time, ensuring strictly sequential spacing.
        nextAvailableTime = Math.max(System.currentTimeMillis(), nextAvailableTime) + minIntervalMillis;
    }
}