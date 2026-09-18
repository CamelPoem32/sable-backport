package dev.ryanhcode.sable.compatibility.create.contraptions;

/** Executable M28.14.2 registration and rollback postcondition regression. */
public final class NestedBearingRegistrationDecisionTest {
    private NestedBearingRegistrationDecisionTest() {
    }

    public static void run() {
        require(failure(true, false, true, true, true, true, true, true, true)
                        == NestedBearingRegistrationDecision.Failure.NONE,
                "A working M13-equivalent registration must pass every authoritative postcondition");
        require(failure(false, false, false, false, false, false, false, false, false)
                        == NestedBearingRegistrationDecision.Failure.ADD_REJECTED,
                "An insertion rejection must remain distinct from a lookup failure");
        require(failure(true, true, true, true, true, true, true, true, true)
                        == NestedBearingRegistrationDecision.Failure.ENTITY_REMOVED,
                "An immediately removed entity must fail before controller commit");
        require(failure(true, false, false, true, true, true, true, true, true)
                        == NestedBearingRegistrationDecision.Failure.ID_LOOKUP_MISMATCH,
                "The authoritative id lookup is required");
        require(failure(true, false, true, false, true, true, true, true, true)
                        == NestedBearingRegistrationDecision.Failure.UUID_LOOKUP_MISMATCH,
                "The authoritative UUID lookup is required");
        require(failure(true, false, true, true, true, false, true, true, true)
                        == NestedBearingRegistrationDecision.Failure.CONTROLLER_REFERENCE_MISMATCH,
                "A dangling controller reference cannot be committed");
        require(failure(true, false, true, true, true, true, true, false, false)
                        == NestedBearingRegistrationDecision.Failure.MOVEMENT_MODE_UNAVAILABLE,
                "Unavailable restored behavior state must fail descriptively");
    }

    private static NestedBearingRegistrationDecision.Failure failure(
            final boolean added, final boolean removed, final boolean idMatch, final boolean uuidMatch,
            final boolean subLevelMatch, final boolean controllerMatch, final boolean angleMatch,
            final boolean movementAvailable, final boolean movementMatch) {
        return NestedBearingRegistrationDecision.firstFailure(new NestedBearingRegistrationDecision.Observation(
                added, removed, idMatch, uuidMatch, subLevelMatch, controllerMatch, angleMatch,
                movementAvailable, movementMatch));
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
