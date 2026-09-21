package dev.ryanhcode.sable.util;

/** Stress regression proving normal query volume cannot produce unbounded example output. */
public final class M29BoundedDiagnosticCountersTest {
    private M29BoundedDiagnosticCountersTest() {
    }

    public static void run() {
        final BoundedDiagnosticCounters counters = new BoundedDiagnosticCounters(4, 3);
        int emittedExamples = 0;
        for (int index = 0; index < 1_000_000; index++) {
            if (counters.record(0)) {
                emittedExamples++;
            }
        }
        require(emittedExamples == 3, "one million normal events must emit only three examples");
        require(counters.count(0) == 1_000_000L, "normal route counter lost increments");

        for (int route = 1; route < 4; route++) {
            for (int sample = 0; sample < 10; sample++) {
                counters.record(route);
            }
            require(counters.count(route) == 10L, "route counter mismatch");
        }
        require(counters.getAndResetCount(0) == 1_000_000L, "summary reset lost total");
        require(counters.count(0) == 0L, "summary reset retained prior session count");
        counters.resetExamples();
        require(counters.record(0), "new diagnostic session must regain its bounded example budget");
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
