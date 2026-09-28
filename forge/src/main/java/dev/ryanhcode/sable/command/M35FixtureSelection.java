package dev.ryanhcode.sable.command;

/** Shared decision for setup and teardown; a tagged but damaged fixture is not "absent". */
final class M35FixtureSelection {
    private M35FixtureSelection() {
    }

    static Status classify(final int markerCount, final int matchingControllers, final boolean orphanMarker) {
        if (markerCount == 0) {
            return Status.NONE;
        }
        if (markerCount > 1 || matchingControllers > 1) {
            return Status.AMBIGUOUS;
        }
        if (orphanMarker) {
            return Status.ORPHAN_MARKER;
        }
        return matchingControllers == 1 ? Status.READY : Status.CORRUPT;
    }

    enum Status {
        NONE, READY, ORPHAN_MARKER, AMBIGUOUS, CORRUPT;

        boolean setupAllowed() {
            return this == NONE;
        }

        boolean cleanupAllowed() {
            return this == READY || this == ORPHAN_MARKER;
        }
    }
}
