package dev.ryanhcode.sable.compatibility.create.contraptions;

public final class RestoredContraptionClientSyncDecisionTest {
    private RestoredContraptionClientSyncDecisionTest() {
    }

    public static void run() {
        expect(RestoredContraptionClientSyncDecision.Action.REQUEUE_FLYWHEEL_VISUAL,
                RestoredContraptionClientSyncDecision.evaluate(true, true, true, 2, 2),
                "valid two-block reverse restore");
        expect(RestoredContraptionClientSyncDecision.Action.FAIL_PAYLOAD_VALIDATION,
                RestoredContraptionClientSyncDecision.evaluate(true, true, true, 2, 0),
                "missing client payload");
        expect(RestoredContraptionClientSyncDecision.Action.NONE,
                RestoredContraptionClientSyncDecision.evaluate(true, false, true, 2, 2),
                "server side");
        expect(RestoredContraptionClientSyncDecision.Action.NONE,
                RestoredContraptionClientSyncDecision.evaluate(true, true, false, 2, 2),
                "Sable destination");
        expect(RestoredContraptionClientSyncDecision.Action.NONE,
                RestoredContraptionClientSyncDecision.evaluate(false, true, true, 2, 2),
                "ordinary Create contraption");
        expectReason(RestoredContraptionClientSyncDecision.Reason.ACCEPTED,
                RestoredContraptionClientSyncDecision.evaluateDetailed(
                        true, true, true, false, 2, 2, 2, 2),
                "valid symmetric-sail payload");
        expectReason(RestoredContraptionClientSyncDecision.Reason.SYMMETRIC_SAIL_COUNT_MISMATCH,
                RestoredContraptionClientSyncDecision.evaluateDetailed(
                        true, true, true, false, 2, 2, 2, 1),
                "symmetric-sail mismatch");
        expectReason(RestoredContraptionClientSyncDecision.Reason.ENTITY_REMOVED,
                RestoredContraptionClientSyncDecision.evaluateDetailed(
                        true, true, true, true, 2, 2, 2, 2),
                "removed restored entity");
    }

    private static void expect(final RestoredContraptionClientSyncDecision.Action expected,
                               final RestoredContraptionClientSyncDecision.Action actual,
                               final String scenario) {
        if (expected != actual) {
            throw new AssertionError(scenario + ": expected " + expected + " but got " + actual);
        }
    }

    private static void expectReason(final RestoredContraptionClientSyncDecision.Reason expected,
                                     final RestoredContraptionClientSyncDecision.Decision actual,
                                     final String scenario) {
        if (expected != actual.reason()) {
            throw new AssertionError(scenario + ": expected " + expected + " but got " + actual.reason());
        }
    }
}
