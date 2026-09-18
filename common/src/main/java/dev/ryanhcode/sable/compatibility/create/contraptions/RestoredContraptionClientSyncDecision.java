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
        if (!restoredAfterOuterDisassembly || !clientSide || !normalWorldDestination) {
            return Action.NONE;
        }
        if (expectedCapturedBlocks <= 0 || actualCapturedBlocks != expectedCapturedBlocks) {
            return Action.FAIL_PAYLOAD_VALIDATION;
        }
        return Action.REQUEUE_FLYWHEEL_VISUAL;
    }

    public enum Action {
        NONE,
        FAIL_PAYLOAD_VALIDATION,
        REQUEUE_FLYWHEEL_VISUAL
    }
}
