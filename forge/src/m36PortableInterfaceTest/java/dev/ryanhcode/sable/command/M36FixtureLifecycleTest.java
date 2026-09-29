package dev.ryanhcode.sable.command;

import dev.ryanhcode.sable.command.M36PortableInterfaceFixtureCommands.CleanupPhase;
import dev.ryanhcode.sable.command.M36PortableInterfaceFixtureCommands.LookupStatus;
import dev.ryanhcode.sable.command.M36PortableInterfaceFixtureCommands.Occupancy;
import dev.ryanhcode.sable.command.M36PortableInterfaceFixtureCommands.Kind;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Deterministic lookup/teardown policy checks; native Create motion still needs runtime validation. */
public final class M36FixtureLifecycleTest {
    private M36FixtureLifecycleTest() {
    }

    public static void run() {
        for (final LookupStatus status : LookupStatus.values()) {
            if (status.allowsSetup() != (status == LookupStatus.NONE)) {
                throw new AssertionError("setup lookup mismatch: " + status);
            }
            if (status.allowsSetup() && status.allowsCleanup()) {
                throw new AssertionError("setup and cleanup disagree: " + status);
            }
        }
        assertRecovery(LookupStatus.ACTIVE_STATIC, false, CleanupPhase.DISCONNECT_WAIT);
        assertRecovery(LookupStatus.ACTIVE_OUTER, false, CleanupPhase.DISCONNECT_WAIT);
        assertRecovery(LookupStatus.ACTIVE_STATIC, true, CleanupPhase.INNER_DISASSEMBLED);
        assertRecovery(LookupStatus.ACTIVE_OUTER, true, CleanupPhase.INNER_DISASSEMBLED);
        assertRecovery(LookupStatus.PARTIALLY_CLEANED, false, CleanupPhase.REMOVE);
        assertRecovery(LookupStatus.ORPHAN_MARKER, false, CleanupPhase.REMOVE);
        if (LookupStatus.AMBIGUOUS.allowsCleanup() || LookupStatus.CORRUPT.allowsCleanup()) {
            throw new AssertionError("unsafe fixture snapshot must not be cleaned automatically");
        }
        // A timed-out task carries no tombstone; a new invocation uses the latest snapshot.
        assertRecovery(LookupStatus.ACTIVE_OUTER, true, CleanupPhase.INNER_DISASSEMBLED);
        assertRecovery(LookupStatus.PARTIALLY_CLEANED, true, CleanupPhase.REMOVE);
        final BlockPos origin = new BlockPos(-40, 102, 65);
        for (final int lift : new int[] {0, 8, 16}) {
            final BlockPos target = M36PortableInterfaceFixtureCommands.rotatedStation(origin, lift);
            if (!target.equals(origin.offset(0, lift + 1, -15))) {
                throw new AssertionError("+90 degree yaw station target: " + target);
            }
        }
        for (final Occupancy occupancy : Occupancy.values()) {
            final boolean expected = occupancy == Occupancy.AIR
                    || occupancy == Occupancy.CURRENT_M36_FIXTURE_BLOCK
                    || occupancy == Occupancy.OLD_M36_STATION_BLOCK;
            if (occupancy.allowed() != expected) {
                throw new AssertionError("yaw ownership policy: " + occupancy);
            }
        }
        final BlockPos eastStation = new BlockPos(-17, 102, 67);
        assertCells(eastStation, Direction.EAST, Kind.FLUID, List.of(eastStation,
                eastStation.east(), eastStation.east(2), eastStation.east().north(), eastStation.north()));
        final BlockPos northStation = new BlockPos(-32, 102, 52);
        assertCells(northStation, Direction.NORTH, Kind.FLUID, List.of(northStation,
                northStation.north(), northStation.north(2), northStation.north().west(),
                northStation.west()));
        assertCells(eastStation, Direction.EAST, Kind.ITEM,
                List.of(eastStation, eastStation.below(), eastStation.below().south()));
        assertCells(northStation, Direction.NORTH, Kind.ITEM,
                List.of(northStation, northStation.below(), northStation.below().south()));
        if (!M36PortableInterfaceFixtureCommands.structuralFluidSignatureMatches(true, false, 2)) {
            throw new AssertionError("runtime-mutable tank state must not break structural ownership");
        }
        if (M36PortableInterfaceFixtureCommands.structuralFluidSignatureMatches(true, false, 1)
                || M36PortableInterfaceFixtureCommands.structuralFluidSignatureMatches(false, true, 1)
                || M36PortableInterfaceFixtureCommands.structuralFluidSignatureMatches(false, false, 2)) {
            throw new AssertionError("wrong pump direction or unrelated block must be refused");
        }
    }

    private static void assertCells(final BlockPos station, final Direction outward, final Kind kind,
                                    final List<BlockPos> expected) {
        final List<BlockPos> actual = M36PortableInterfaceFixtureCommands.stationCells(station, outward, kind);
        if (!actual.equals(expected)) {
            throw new AssertionError(kind + " " + outward + " footprint: " + actual + " expected " + expected);
        }
    }

    private static void assertRecovery(final LookupStatus status, final boolean retracted,
                                       final CleanupPhase expected) {
        if (!status.allowsCleanup() || status.startsAt(retracted) != expected) {
            throw new AssertionError(status + " retracted=" + retracted + " expected " + expected);
        }
    }
}
