package dev.ryanhcode.sable.forge;

/** Focused M29.5 checks for packet, collision, teardown, and tracking removal provenance. */
public final class M29RestoredCceRemovalClassificationTest {
    private M29RestoredCceRemovalClassificationTest() {
    }

    public static void run() {
        require(RestoredContraptionRemovalClassification.classify(true, false, false, true, false)
                        == RestoredContraptionRemovalClassification.Result.R1_SERVER_REMOVE_PACKET,
                "a correlated remove packet must classify as R1");
        require(RestoredContraptionRemovalClassification.classify(false, true, false, true, false)
                        == RestoredContraptionRemovalClassification.Result.R2_ID_OR_UUID_COLLISION,
                "identity replacement must classify as R2");
        require(RestoredContraptionRemovalClassification.classify(false, false, true, true, false)
                        == RestoredContraptionRemovalClassification.Result.R3_SABLE_CLIENT_TEARDOWN,
                "synchronous sublevel teardown must classify as R3");
        require(RestoredContraptionRemovalClassification.classify(false, false, false, true, false)
                        == RestoredContraptionRemovalClassification.Result.R4_CLIENT_TRACKING_TRANSITION,
                "unattributed tracking end must classify as R4");
        require(RestoredContraptionRemovalClassification.classify(false, false, false, true, true)
                        == RestoredContraptionRemovalClassification.Result.R5_BROAD_CLEANUP_MATCHER,
                "a proven broad cleanup matcher must classify as R5");
        require(RestoredContraptionRemovalClassification.classify(false, false, false, false, false)
                        == RestoredContraptionRemovalClassification.Result.R6_OTHER,
                "an unknown path must remain R6 instead of being guessed");
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
