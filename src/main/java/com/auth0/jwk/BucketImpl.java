package com.auth0.jwk;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token Bucket implementation to guarantee availability of a fixed amount of tokens in a given time rate.
 */
class BucketImpl implements Bucket {

    private final long size;
    private final long rate;
    private final TimeUnit rateUnit;
    private final long beginTime = System.currentTimeMillis();
    private AtomicLong available;
    private AtomicLong lastTokenAddedAt;

    BucketImpl(long size, long rate, TimeUnit rateUnit) {
        assertPositiveValue(size, "Invalid bucket size.");
        assertPositiveValue(rate, "Invalid bucket refill rate.");
        this.size = size;
        this.available = new AtomicLong(size);
        this.lastTokenAddedAt = new AtomicLong(System.currentTimeMillis());
        this.rate = rate;
        this.rateUnit = rateUnit;

        beginRefillAtRate();
    }

    private void beginRefillAtRate() {
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        Runnable refillTask = new Runnable() {
            public void run() {
                try {
                    rateUnit.sleep(rate);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                addToken();
            }
        };
        executorService.scheduleAtFixedRate(refillTask, 0, rate, rateUnit);
    }

    private void addToken() {
        if (available.get() < size) {
            available.incrementAndGet();
            log(String.format("Added 1 token.. Current state: %d/%d", available.get(), size));
        }
        lastTokenAddedAt.set(System.currentTimeMillis());
    }

    private void assertPositiveValue(long value, long maxValue, String exceptionMessage) {
        if (value < 1 || value > maxValue) {
            throw new IllegalArgumentException(exceptionMessage);
        }
    }

    private void assertPositiveValue(Number value, String exceptionMessage) {
        this.assertPositiveValue(value.intValue(), value.intValue(), exceptionMessage);
    }

    private void log(String message) {
        long msDiff = System.currentTimeMillis() - beginTime;
        System.out.println(String.format("%-8d - %s", msDiff, message));
    }

    @Override
    public long willLeakIn() {
        return willLeakIn(1);
    }

    @Override
    public long willLeakIn(long count) {
        assertPositiveValue(count, size, String.format("Cannot consume %d tokens when the BucketImpl size is %d!", count, size));
        long av = available.get();
        if (av >= count) {
            log(String.format("%d Tokens are available already.", count));
            return 0;
        }

        long nextIn = rateUnit.toMillis(rate) - (System.currentTimeMillis() - lastTokenAddedAt.get());
        final long remaining = count - av - 1;
        if (remaining > 0) {
            nextIn += rateUnit.toMillis(rate) * remaining;
        }
        log(String.format("Can't consume %d. Actual state is: %d/%d. Retry in %d ms.", count, available.get(), size, nextIn));
        return nextIn;
    }

    @Override
    public boolean consume() {
        return consume(1);
    }

    @Override
    public boolean consume(long count) {
        assertPositiveValue(count, size, String.format("Cannot consume %d tokens when the BucketImpl size is %d!", count, size));
        if (count <= available.get()) {
            available.addAndGet(-count);
            log(String.format("Consumed %d tokens.. Current state: %d/%d", count, available.get(), size));
            return true;
        }
        log(String.format("Not enough tokens available to consume %d", count));
        System.out.println();
        return false;
    }
}
