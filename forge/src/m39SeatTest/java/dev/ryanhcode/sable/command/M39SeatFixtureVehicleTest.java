package dev.ryanhcode.sable.command;

import java.util.List;
import java.util.UUID;

public final class M39SeatFixtureVehicleTest {
    private M39SeatFixtureVehicleTest() { }

    private static final UUID SEAT = new UUID(39, 1);
    private static final UUID OTHER = new UUID(39, 2);

    private static M39SeatFixtureVehicleIdentity.Candidate valid(final UUID uuid) {
        return new M39SeatFixtureVehicleIdentity.Candidate(uuid, true, true, false, true, true, true, true, true);
    }

    private static void expect(final UUID expected, final String reason, final M39SeatFixtureVehicleIdentity.Selection actual) {
        if (!java.util.Objects.equals(expected, actual.uuid()) || !reason.equals(actual.reason())) {
            throw new AssertionError("Seat identity decision " + actual + " expected=" + expected + "/" + reason);
        }
    }

    public static void run() {
        final var discovered = M39SeatFixtureVehicleIdentity.select(null, List.of(valid(SEAT)));
        expect(SEAT, "SEMANTIC_CONTROLLER", discovered);
        expect(SEAT, "EXACT_UUID", M39SeatFixtureVehicleIdentity.select(discovered.uuid(), List.of(valid(SEAT))));
        // Missing parent lookup does not veto a semantically valid controller candidate.
        expect(SEAT, "EXACT_UUID", M39SeatFixtureVehicleIdentity.select(SEAT, List.of(valid(SEAT))));
        expect(SEAT, "EXACT_UUID", M39SeatFixtureVehicleIdentity.select(SEAT, List.of(valid(SEAT), valid(SEAT))));
        expect(null, "ENTITY_REMOVED", M39SeatFixtureVehicleIdentity.select(SEAT, List.of(
                new M39SeatFixtureVehicleIdentity.Candidate(SEAT, true, false, true, true, true, true, true, true))));
        expect(null, "ENTITY_REPLACED", M39SeatFixtureVehicleIdentity.select(SEAT, List.of(valid(OTHER))));
        expect(null, "VEHICLE_UNAVAILABLE", M39SeatFixtureVehicleIdentity.select(SEAT, List.of()));
        for (int broken = 0; broken < 6; broken++) {
            expect(null, "NO_NATIVE_SEAT_VEHICLE", M39SeatFixtureVehicleIdentity.select(null, List.of(
                    new M39SeatFixtureVehicleIdentity.Candidate(OTHER, broken != 0, true, false,
                            broken != 1, broken != 2, broken != 3, broken != 4, broken != 5))));
        }
        expect(null, "AMBIGUOUS_CONTROLLER", M39SeatFixtureVehicleIdentity.select(SEAT, List.of(valid(SEAT), valid(OTHER))));
        // Human input can take minutes; only machine motion/cleanup have a bounded lifecycle.
        for (int ticks = 0; ticks <= 100_000; ticks++) {
            if (M39SeatFixtureVehicleIdentity.lifecycleTimedOut(ticks, true, false)) throw new AssertionError("boarding timed out");
        }
        if (!M39SeatFixtureVehicleIdentity.lifecycleTimedOut(1201, false, false)
                || !M39SeatFixtureVehicleIdentity.lifecycleTimedOut(1201, true, true)) throw new AssertionError("machine timeout removed");
        for (final double offset : new double[] {0, .125, .49, .5, Double.NaN}) {
            if (M39SeatFixtureVehicleIdentity.canBeginReturnedPlacement(offset)) throw new AssertionError("zero-speed resumption can place boarding Seat");
        }
        if (!M39SeatFixtureVehicleIdentity.canBeginReturnedPlacement(11)) throw new AssertionError("native return mode unavailable at extension hold");
        // Later block restoration cannot retroactively identify a lost vehicle as a new captured one.
        expect(null, "VEHICLE_UNAVAILABLE", M39SeatFixtureVehicleIdentity.select(SEAT, List.of()));
        expect(null, "ENTITY_REPLACED", M39SeatFixtureVehicleIdentity.select(SEAT, List.of(valid(OTHER))));
        // A fresh cleanup has no stale transient identity and resolves the current owned vehicle.
        expect(OTHER, "SEMANTIC_CONTROLLER", M39SeatFixtureVehicleIdentity.select(null, List.of(valid(OTHER))));
    }
}
