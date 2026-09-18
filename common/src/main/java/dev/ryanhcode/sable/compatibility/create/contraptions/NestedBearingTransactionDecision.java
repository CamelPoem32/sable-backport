package dev.ryanhcode.sable.compatibility.create.contraptions;

/** Pure dangling-ownership and pre-commit rollback rules for M28 nested transfers. */
public final class NestedBearingTransactionDecision {
    private NestedBearingTransactionDecision() {
    }

    public static SourceFailure sourceFailure(final boolean bearingRunning, final boolean hasEntityReference,
                                              final boolean entityRemoved, final boolean bearingContraption,
                                              final boolean payloadIsAir) {
        if (hasEntityReference && entityRemoved) {
            return SourceFailure.DANGLING_DEAD_NESTED_CCE;
        }
        if (hasEntityReference && !bearingContraption) {
            return SourceFailure.INVALID_NESTED_CONTRAPTION;
        }
        if (bearingRunning && !hasEntityReference && payloadIsAir) {
            return SourceFailure.DANGLING_MISSING_NESTED_CCE;
        }
        return SourceFailure.NONE;
    }

    public static boolean shouldRollbackDestination(final boolean sourceOwnershipCommitted,
                                                    final boolean destinationAllocated) {
        return !sourceOwnershipCommitted && destinationAllocated;
    }

    public enum SourceFailure {
        NONE,
        DANGLING_DEAD_NESTED_CCE,
        DANGLING_MISSING_NESTED_CCE,
        INVALID_NESTED_CONTRAPTION
    }
}
