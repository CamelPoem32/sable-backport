package dev.ryanhcode.sable.util;

import java.util.concurrent.atomic.AtomicLongArray;

/** Fixed-size counters with a bounded first-N example budget per category. */
public final class BoundedDiagnosticCounters {
    private final AtomicLongArray counts;
    private final AtomicLongArray examples;
    private final int exampleLimit;

    public BoundedDiagnosticCounters(final int categoryCount, final int exampleLimit) {
        if (categoryCount <= 0 || exampleLimit < 0) {
            throw new IllegalArgumentException("Invalid bounded diagnostic dimensions");
        }
        this.counts = new AtomicLongArray(categoryCount);
        this.examples = new AtomicLongArray(categoryCount);
        this.exampleLimit = exampleLimit;
    }

    /** Records an event and returns true only while that category still has an example slot. */
    public boolean record(final int category) {
        this.checkCategory(category);
        this.counts.incrementAndGet(category);
        if (this.exampleLimit == 0) {
            return false;
        }
        while (true) {
            final long current = this.examples.get(category);
            if (current >= this.exampleLimit) {
                return false;
            }
            if (this.examples.compareAndSet(category, current, current + 1)) {
                return true;
            }
        }
    }

    public long count(final int category) {
        this.checkCategory(category);
        return this.counts.get(category);
    }

    public long getAndResetCount(final int category) {
        this.checkCategory(category);
        return this.counts.getAndSet(category, 0L);
    }

    public void resetExamples() {
        for (int category = 0; category < this.examples.length(); category++) {
            this.examples.set(category, 0L);
        }
    }

    private void checkCategory(final int category) {
        if (category < 0 || category >= this.counts.length()) {
            throw new IndexOutOfBoundsException("Unknown diagnostic category " + category);
        }
    }
}
