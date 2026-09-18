package dev.ryanhcode.sable.compatibility.create.contraptions;

/** Pure postconditions for restoring a nested ControlledContraptionEntity. */
public final class NestedBearingRegistrationDecision {
    private NestedBearingRegistrationDecision() {
    }

    public static Failure firstFailure(final Observation observation) {
        if (!observation.addFreshEntityAccepted()) {
            return Failure.ADD_REJECTED;
        }
        if (observation.entityRemoved()) {
            return Failure.ENTITY_REMOVED;
        }
        if (!observation.idLookupMatches()) {
            return Failure.ID_LOOKUP_MISMATCH;
        }
        if (!observation.uuidLookupMatches()) {
            return Failure.UUID_LOOKUP_MISMATCH;
        }
        if (!observation.subLevelOwnershipMatches()) {
            return Failure.SUBLEVEL_OWNERSHIP_MISMATCH;
        }
        if (!observation.controllerReferenceMatches()) {
            return Failure.CONTROLLER_REFERENCE_MISMATCH;
        }
        if (!observation.angleMatches()) {
            return Failure.ANGLE_MISMATCH;
        }
        if (!observation.movementModeAvailable()) {
            return Failure.MOVEMENT_MODE_UNAVAILABLE;
        }
        if (!observation.movementModeMatches()) {
            return Failure.MOVEMENT_MODE_MISMATCH;
        }
        return Failure.NONE;
    }

    public record Observation(boolean addFreshEntityAccepted, boolean entityRemoved,
                              boolean idLookupMatches, boolean uuidLookupMatches,
                              boolean subLevelOwnershipMatches, boolean controllerReferenceMatches,
                              boolean angleMatches, boolean movementModeAvailable,
                              boolean movementModeMatches) {
    }

    public enum Failure {
        NONE,
        ADD_REJECTED,
        ENTITY_REMOVED,
        ID_LOOKUP_MISMATCH,
        UUID_LOOKUP_MISMATCH,
        SUBLEVEL_OWNERSHIP_MISMATCH,
        CONTROLLER_REFERENCE_MISMATCH,
        ANGLE_MISMATCH,
        MOVEMENT_MODE_UNAVAILABLE,
        MOVEMENT_MODE_MISMATCH
    }
}
