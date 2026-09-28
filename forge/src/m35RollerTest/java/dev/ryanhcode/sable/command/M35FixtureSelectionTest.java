package dev.ryanhcode.sable.command;

public final class M35FixtureSelectionTest {
    private M35FixtureSelectionTest() {
    }

    public static void run() {
        check("no fixture", 0, 0, M35FixtureSelection.Status.NONE);
        // The live lookup supplies one match for static, moving, or Sable-owned payloads.
        check("current static", 1, 1, M35FixtureSelection.Status.READY);
        check("current moving", 1, 1, M35FixtureSelection.Status.READY);
        check("assembled Sable", 1, 1, M35FixtureSelection.Status.READY);
        check("previous static", 1, 1, M35FixtureSelection.Status.READY);
        check("previous moving", 1, 1, M35FixtureSelection.Status.READY);
        check("version-4 partial hull", 1, 1, M35FixtureSelection.Status.READY);
        check("orphan marker", 1, 0, true, M35FixtureSelection.Status.ORPHAN_MARKER);
        check("damaged controller", 1, 0, M35FixtureSelection.Status.CORRUPT);
        check("two controllers", 1, 2, M35FixtureSelection.Status.AMBIGUOUS);
        check("two markers", 2, 2, M35FixtureSelection.Status.AMBIGUOUS);
        for (final M35FixtureSelection.Status status : M35FixtureSelection.Status.values()) {
            if (status.setupAllowed() && status.cleanupAllowed()) {
                throw new AssertionError("setup and cleanup cannot both own a fixture");
            }
            if (!status.setupAllowed() && status == M35FixtureSelection.Status.NONE) {
                throw new AssertionError("empty world must allow setup");
            }
        }
    }

    private static void check(final String scenario, final int markers, final int matches,
                              final M35FixtureSelection.Status expected) {
        check(scenario, markers, matches, false, expected);
    }

    private static void check(final String scenario, final int markers, final int matches,
                              final boolean orphan, final M35FixtureSelection.Status expected) {
        final M35FixtureSelection.Status actual = M35FixtureSelection.classify(markers, matches, orphan);
        if (actual != expected) {
            throw new AssertionError(scenario + " markers=" + markers + " matches=" + matches + " expected=" + expected
                    + " actual=" + actual);
        }
        if (actual.setupAllowed() == actual.cleanupAllowed() && actual != M35FixtureSelection.Status.CORRUPT
                && actual != M35FixtureSelection.Status.AMBIGUOUS) {
            throw new AssertionError("setup/cleanup disagree on fixture ownership: " + actual);
        }
    }
}
