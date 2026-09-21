package dev.ryanhcode.sable.compatibility.create.contraptions;

/** Pure policy for the M28.14.3 post-spawn visual refresh. */
public final class RestoredContraptionClientSyncDecision {
    private RestoredContraptionClientSyncDecision() {
    }

    public static Action evaluate(final boolean restoredAfterOuterDisassembly,
                                  final boolean clientSide,
                                  final boolean normalWorldDestination,
                                  final int expectedCapturedBlocks,
                                  final int actualCapturedBlocks) {
        return evaluateDetailed(restoredAfterOuterDisassembly, clientSide, normalWorldDestination,
                false, expectedCapturedBlocks, actualCapturedBlocks, 0, 0).action();
    }

    public static Decision evaluateDetailed(final boolean restoredAfterOuterDisassembly,
                                            final boolean clientSide,
                                            final boolean normalWorldDestination,
                                            final boolean entityRemoved,
                                            final int expectedCapturedBlocks,
                                            final int actualCapturedBlocks,
                                            final int expectedSymmetricSails,
                                            final int actualSymmetricSails) {
        if (!restoredAfterOuterDisassembly) {
            return new Decision(Action.NONE, Reason.NO_RESTORE_MARKER);
        }
        if (!clientSide) {
            return new Decision(Action.NONE, Reason.NOT_CLIENT);
        }
        if (!normalWorldDestination) {
            return new Decision(Action.NONE, Reason.NOT_NORMAL_WORLD);
        }
        if (entityRemoved) {
            return new Decision(Action.NONE, Reason.ENTITY_REMOVED);
        }
        if (expectedCapturedBlocks <= 0) {
            return new Decision(Action.FAIL_PAYLOAD_VALIDATION, Reason.EXPECTED_COUNT_ZERO);
        }
        if (actualCapturedBlocks != expectedCapturedBlocks) {
            return new Decision(Action.FAIL_PAYLOAD_VALIDATION, Reason.PAYLOAD_COUNT_MISMATCH);
        }
        if (expectedSymmetricSails > 0 && actualSymmetricSails != expectedSymmetricSails) {
            return new Decision(Action.FAIL_PAYLOAD_VALIDATION, Reason.SYMMETRIC_SAIL_COUNT_MISMATCH);
        }
        return new Decision(Action.REQUEUE_FLYWHEEL_VISUAL, Reason.ACCEPTED);
    }

    public record Decision(Action action, Reason reason) {
    }

    public enum Action {
        NONE,
        FAIL_PAYLOAD_VALIDATION,
        REQUEUE_FLYWHEEL_VISUAL
    }

    public enum Reason {
        ACCEPTED,
        NO_RESTORE_MARKER,
        NOT_CLIENT,
        NOT_NORMAL_WORLD,
        EXPECTED_COUNT_ZERO,
        PAYLOAD_COUNT_MISMATCH,
        SYMMETRIC_SAIL_COUNT_MISMATCH,
        ENTITY_REMOVED
    }
}
