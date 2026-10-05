package dev.ryanhcode.sable.command;

import java.util.List;
import java.util.UUID;

/** Fixture-only identity policy; no entity references survive between server ticks. */
public final class M39SeatFixtureVehicleIdentity {
    private M39SeatFixtureVehicleIdentity() { }

    public record Candidate(UUID uuid, boolean controlled, boolean alive, boolean removed,
                            boolean sameLevel, boolean sameBody, boolean sameController,
                            boolean expectedSeat, boolean visiblePoseMatches) {
        boolean valid() {
            return controlled && alive && !removed && sameLevel && sameBody && sameController
                    && expectedSeat && visiblePoseMatches;
        }
    }

    public record Selection(UUID uuid, String reason) { }

    public static Selection select(final UUID expected, final List<Candidate> candidates) {
        final List<Candidate> valid = candidates.stream().filter(Candidate::valid).distinct().toList();
        if (expected != null) {
            if (valid.stream().anyMatch(candidate -> expected.equals(candidate.uuid))) {
                if (valid.stream().anyMatch(candidate -> !expected.equals(candidate.uuid))) return new Selection(null, "AMBIGUOUS_CONTROLLER");
                return new Selection(expected, "EXACT_UUID");
            }
            if (candidates.stream().anyMatch(candidate -> expected.equals(candidate.uuid) && candidate.removed)) {
                return new Selection(null, "ENTITY_REMOVED");
            }
            return new Selection(null, valid.isEmpty() ? "VEHICLE_UNAVAILABLE" : "ENTITY_REPLACED");
        }
        if (valid.size() > 1) return new Selection(null, "AMBIGUOUS_CONTROLLER");
        return valid.isEmpty() ? new Selection(null, "NO_NATIVE_SEAT_VEHICLE") : new Selection(valid.get(0).uuid, "SEMANTIC_CONTROLLER");
    }

    public static boolean lifecycleTimedOut(final int age, final boolean boarding, final boolean cleaning) {
        return age > 1200 && (!boarding || cleaning);
    }

    public static boolean canBeginReturnedPlacement(final double offset) {
        return Double.isFinite(offset) && offset >= 1;
    }
}
