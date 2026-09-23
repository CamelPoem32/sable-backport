package dev.ryanhcode.sable.compatibility.create.deployer;

/** Classifies a native Create refill attempt without changing its result. */
public final class DeployerRefillDecision {
    private DeployerRefillDecision() {
    }

    public static String classify(final int capturedChestCount, final int mountedStorageCount,
                                  final int matchingItemCount, final boolean extracted) {
        if (extracted) {
            return "NATIVE_EXTRACTION_SUCCEEDED";
        }
        if (capturedChestCount == 0) {
            return "CHEST_NOT_CAPTURED_BY_INNER_CONTRAPTION";
        }
        if (mountedStorageCount == 0) {
            return "CHEST_CAPTURED_BUT_NOT_MOUNTED";
        }
        if (matchingItemCount == 0) {
            return "NO_MATCHING_MOUNTED_ITEM";
        }
        return "NATIVE_EXTRACTION_RETURNED_EMPTY";
    }
}
