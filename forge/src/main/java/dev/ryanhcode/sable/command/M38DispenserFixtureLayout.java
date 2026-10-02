package dev.ryanhcode.sable.command;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** One block map shared by synchronous setup, footprint checks and legacy recovery. */
public final class M38DispenserFixtureLayout {
    public static final BlockPos LEGACY_ORIGIN = new BlockPos(-31, 101, 69);
    public static final String READY_TAG = "sable_m38_ready_v2";

    private M38DispenserFixtureLayout() {
    }

    public static Map<BlockPos, String> blocks(final boolean dropper, final boolean ignition) {
        final Map<BlockPos, String> cells = new LinkedHashMap<>();
        for (int x = -12; x <= -1; x++) {
            cells.put(new BlockPos(x, 0, 0), "create:piston_extension_pole[facing=east]");
        }
        cells.put(BlockPos.ZERO, "create:sticky_mechanical_piston[facing=east,axis_along_first=true,state=retracted]");
        cells.put(new BlockPos(1, 0, 0), "create:radial_chassis[axis=x,sticky_north=true,sticky_east=true]");
        cells.put(new BlockPos(1, 0, 1), "minecraft:chest[facing=north,type=single,waterlogged=false]");
        cells.put(new BlockPos(0, -2, 0), "create:creative_motor[facing=up]");
        cells.put(new BlockPos(0, -1, 0), "create:clutch[axis=y,powered=false]");
        cells.put(new BlockPos(0, -1, 1), "minecraft:lever[face=wall,facing=south,powered=true]");
        cells.put(new BlockPos(0, 0, -2), "minecraft:iron_block");
        cells.put(new BlockPos(0, 0, -1), "minecraft:iron_block");
        cells.put(new BlockPos(0, 1, -2), "simulated:physics_assembler");
        cells.put(new BlockPos(1, 1, 0), (dropper ? "minecraft:dropper" : "minecraft:dispenser") + "[facing=east]");
        if (ignition) cells.put(new BlockPos(14, 0, 0), "minecraft:stone");
        return Collections.unmodifiableMap(cells);
    }

    public static String blockId(final String state) {
        final int properties = state.indexOf('[');
        return properties < 0 ? state : state.substring(0, properties);
    }

    public static AABB outerGlueBounds(final BlockPos origin) {
        return new AABB(origin.getX(), origin.getY() - 2, origin.getZ() - 2,
                origin.getX() + 1, origin.getY() + 2, origin.getZ() + 2);
    }

    /** Mutable piston/clutch states and contents do not change structural ownership. */
    public static boolean controllerSignature(final Function<BlockPos, String> actual) {
        for (final var cell : blocks(true, false).entrySet()) {
            final BlockPos pos = cell.getKey();
            if (pos.getX() == 1) continue; // The payload can be in a native PistonContraption.
            if (!blockId(cell.getValue()).equals(actual.apply(pos))) return false;
        }
        return true;
    }

    public static boolean legacyPartialSignature(final Function<BlockPos, String> actual) {
        for (final BlockPos pos : new BlockPos[] {BlockPos.ZERO, new BlockPos(0, -2, 0),
                new BlockPos(0, -1, 0), new BlockPos(0, 1, -2)}) {
            if (!blockId(blocks(true, false).get(pos)).equals(actual.apply(pos))) return false;
        }
        for (int x = -12; x < 0; x++) {
            if (!"create:piston_extension_pole".equals(actual.apply(new BlockPos(x, 0, 0)))) return false;
        }
        return true;
    }

    public static boolean ownedRestoreResidue(final BlockPos relative, final String occupant,
                                             final String restoring, final boolean dropper, final boolean ignition) {
        final String expected = blocks(dropper, ignition).get(relative);
        return expected != null && blockId(expected).equals(occupant) && occupant.equals(restoring);
    }

    public static boolean footprintClear(final boolean dropper, final boolean ignition,
                                         final Function<BlockPos, Boolean> air) {
        return blocks(dropper, ignition).keySet().stream().allMatch(pos -> air.apply(pos));
    }

    public enum Status {
        NONE, STATIC_VALID, STATIC_PARTIAL, ASSEMBLED_VALID, ASSEMBLED_RECOVERABLE,
        ORPHAN_MARKER, CORRUPT_AMBIGUOUS
    }

    public static Status classify(final boolean marker, final boolean signature, final boolean assembled,
                                  final boolean ready, final boolean clear, final boolean ambiguous) {
        if (ambiguous) return Status.CORRUPT_AMBIGUOUS;
        if (assembled && signature) return ready ? Status.ASSEMBLED_VALID : Status.ASSEMBLED_RECOVERABLE;
        if (signature) return ready ? Status.STATIC_VALID : Status.STATIC_PARTIAL;
        if (marker && clear) return Status.ORPHAN_MARKER;
        return marker ? Status.STATIC_PARTIAL : Status.NONE;
    }
}
