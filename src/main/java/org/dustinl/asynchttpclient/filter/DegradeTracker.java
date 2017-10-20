package org.dustinl.asynchttpclient.filter;

import com.google.common.collect.EvictingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class DegradeTracker {
    private final String hostname;
    private final int errorWindow;
    private final int maxError;
    private final int degradePeriod;
    private final Set<Integer> errorStatuses = new HashSet<>();
    private Long degradeTime;
    private final EvictingQueue<Record> errorQueue;
    private final Clock clock;
    private final ReentrantLock timeLock = new ReentrantLock();

    private static final Logger logger = LoggerFactory.getLogger(DegradeTracker.class);

    /**
     * Instantiates a new Degrade tracker.
     *
     * @param hostname     the host to be tracked
     * @param errorWindow  the the time window
     * @param maxError     the max error
     * @param degradePeriod the degrade period
     */
    public DegradeTracker(String hostname, int errorWindow, int maxError, int degradePeriod) {
        this(hostname, errorWindow, maxError, degradePeriod, null, Clock.systemUTC());
    }

    /**
     * Instantiates a new Degrade tracker.
     *
     * if maxError occurs within the errorWindow, the host will be degrade for degradePeriod, within the degrade period,
     * all requests to the host will be abandoned and received a ServerDegradedException.
     *
     * @param hostname     the hostname
     * @param errorWindow  the error window
     * @param maxError     the max error
     * @param degradePeriod the degrade period
     * @param errors       the errors
     */
    public DegradeTracker(String hostname, int errorWindow, int maxError, int degradePeriod, Set<Integer> errors) {
        this(hostname, errorWindow, maxError, degradePeriod, errors, Clock.systemUTC());
    }

    DegradeTracker(String hostname, int errorWindow, int maxError, int degradePeriod, Clock clock) {
        this(hostname, errorWindow, maxError, degradePeriod, null, clock);
    }

    DegradeTracker(String hostname, int errorWindow, int maxError, int degradePeriod, Set<Integer> errors,
                          Clock clock) {
        this.hostname = hostname;
        this.errorWindow = errorWindow;
        this.maxError = maxError;
        this.degradePeriod = degradePeriod;
        Collections.addAll(errorStatuses,
                DegradingRequestFilter.SERVER_TIMEOUT, DegradingRequestFilter.SERVER_UNREACHABLE);
        Optional.ofNullable(errors).ifPresent(errorStatuses::addAll);
        errorQueue = EvictingQueue.create(maxError);
        this.clock = clock;
    }

    /**
     * Gets hostname.
     *
     * @return the hostname
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * Gets error window.
     *
     * @return the error window
     */
    public int getErrorWindow() {
        return errorWindow;
    }

    /**
     * Gets max error.
     *
     * @return the max error
     */
    public int getMaxError() {
        return maxError;
    }

    /**
     * Gets degrade period.
     *
     * @return the degrade period
     */
    public int getDegradePeriod() {
        return degradePeriod;
    }

    /**
     * Gets error statuses.
     *
     * @return the error statuses
     */
    public Set<Integer> getErrorStatuses() {
        return Collections.unmodifiableSet(errorStatuses);
    }

    /**
     * Add result.
     *
     * @param status the status
     */
    void addResult(int status) {
        logger.trace("response status: {}", status);

        if (!errorStatuses.contains(status)) {
            return;
        }

        long now = clock.millis();
        Record newRecord = new Record(status, now);
        synchronized (errorQueue) {
            errorQueue.add(newRecord);
        }

        if (!isDegraded(now) && errorQueue.size() == maxError
                && newRecord.timestamp - errorQueue.peek().timestamp < TimeUnit.SECONDS.toMillis(errorWindow)
                && timeLock.tryLock()) {
            logger.debug("host {} degraded at {}", hostname, now);
            degradeTime = now;
            timeLock.unlock();
        }
    }

    private boolean isDegraded(long timestamp) {
        return degradeTime != null && timestamp - degradeTime < TimeUnit.SECONDS.toMillis(degradePeriod);
    }

    boolean isDegraded() {
        return isDegraded(clock.millis());
    }

    /**
     * Gets error size.
     *
     * @return the error size
     */
    public int getErrorSize() {
        return errorQueue.size();
    }

    static class Record {
        final int status;
        final long timestamp;

        Record(int status, long timestamp) {
            this.status = status;
            this.timestamp = timestamp;
        }
    }
}

