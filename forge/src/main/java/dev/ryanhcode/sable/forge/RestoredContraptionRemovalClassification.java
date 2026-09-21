package dev.ryanhcode.sable.forge;

/** Pure M29.5 classification for the first observed restored-CCE removal boundary. */
public final class RestoredContraptionRemovalClassification {
    private RestoredContraptionRemovalClassification() {
    }

    public enum Result {
        R1_SERVER_REMOVE_PACKET,
        R2_ID_OR_UUID_COLLISION,
        R3_SABLE_CLIENT_TEARDOWN,
        R4_CLIENT_TRACKING_TRANSITION,
        R5_BROAD_CLEANUP_MATCHER,
        R6_OTHER
    }

    public static Result classify(final boolean removePacketReceived,
                                  final boolean idOrUuidCollision,
                                  final boolean sableTeardownActive,
                                  final boolean trackingEndObserved,
                                  final boolean broadCleanupObserved) {
        if (removePacketReceived) {
            return Result.R1_SERVER_REMOVE_PACKET;
        }
        if (idOrUuidCollision) {
            return Result.R2_ID_OR_UUID_COLLISION;
        }
        if (sableTeardownActive) {
            return Result.R3_SABLE_CLIENT_TEARDOWN;
        }
        if (broadCleanupObserved) {
            return Result.R5_BROAD_CLEANUP_MATCHER;
        }
        if (trackingEndObserved) {
            return Result.R4_CLIENT_TRACKING_TRANSITION;
        }
        return Result.R6_OTHER;
    }
}
