package dev.ryanhcode.sable.compatibility.create.contraptions;

/** Executable M28.14.2 atomicity and dangling-reference regression. */
public final class NestedBearingTransactionDecisionTest {
    private NestedBearingTransactionDecisionTest() {
    }

    public static void run() {
        require(failure(true, true, false, true, true)
                        == NestedBearingTransactionDecision.SourceFailure.NONE,
                "A live two-block nested owner remains valid even though its materialized payload is air");
        require(failure(true, true, true, true, true)
                        == NestedBearingTransactionDecision.SourceFailure.DANGLING_DEAD_NESTED_CCE,
                "A dead referenced CCE must fail loud instead of becoming NO_NESTED_CCE");
        require(failure(true, false, false, false, true)
                        == NestedBearingTransactionDecision.SourceFailure.DANGLING_MISSING_NESTED_CCE,
                "An assembled bearing cannot silently own neither static nor nested payload");
        require(failure(false, false, false, false, false)
                        == NestedBearingTransactionDecision.SourceFailure.NONE,
                "A disassembled bearing with a physical payload is a valid capture candidate");
        require(NestedBearingTransactionDecision.shouldRollbackDestination(false, true),
                "A destination allocated before source commit must be rolled back");
        require(!NestedBearingTransactionDecision.shouldRollbackDestination(true, true),
                "Rollback cannot delete the only owner after source ownership commit");
        require(!NestedBearingTransactionDecision.shouldRollbackDestination(false, false),
                "A capture failure before allocation has no destination to remove");
    }

    private static NestedBearingTransactionDecision.SourceFailure failure(
            final boolean running, final boolean hasEntity, final boolean removed,
            final boolean bearingContraption, final boolean payloadAir) {
        return NestedBearingTransactionDecision.sourceFailure(
                running, hasEntity, removed, bearingContraption, payloadAir);
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
