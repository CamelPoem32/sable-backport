package dev.ryanhcode.sable.command;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Uses the proven native piston/chassis payload with a two-half door above the chassis. */
public final class M42SlidingDoorFixtureLayout {
    public static final BlockPos LOWER = new BlockPos(1, 1, 0);
    public static final BlockPos CHASSIS = LOWER.below();
    public static final BlockPos PARKED = new BlockPos(5, 1, -4);
    public static final BlockPos ASSEMBLER = new BlockPos(0, 1, -2);
    private M42SlidingDoorFixtureLayout() { }

    public static Map<BlockPos, String> mechanism() {
        final Map<BlockPos, String> result = new LinkedHashMap<>(M38DispenserFixtureLayout.blocks(true, false));
        result.put(LOWER, "create:andesite_door[facing=north,half=lower,hinge=left,open=true,powered=false]");
        result.put(LOWER.above(), "create:andesite_door[facing=north,half=upper,hinge=left,open=true,powered=false]");
        return Collections.unmodifiableMap(result);
    }

    /** The BUILD map is authoritative; these are its moving payload column, not a second block list. */
    public static Map<BlockPos, String> movingPayload() {
        final Map<BlockPos, String> result = new LinkedHashMap<>();
        mechanism().forEach((pos, state) -> {
            if (pos.getX() == CHASSIS.getX()) result.put(pos, state);
        });
        return Collections.unmodifiableMap(result);
    }

    /** Native radial range skips brittle doors; Contraption.moveBlock attaches their halves separately. */
    public static Set<BlockPos> chassisRangeExpected() {
        final Set<BlockPos> result = new LinkedHashSet<>(movingPayload().keySet());
        result.remove(CHASSIS);
        result.removeIf(pos -> movingPayload().get(pos).startsWith("create:andesite_door["));
        return Collections.unmodifiableSet(result);
    }

    /** Retracted PistonContraption captures the poles and a generated head as well as the payload. */
    public static Map<BlockPos, String> capturedBlockIds() {
        final Map<BlockPos, String> result = new LinkedHashMap<>();
        mechanism().forEach((pos, state) -> {
            if (state.startsWith("create:piston_extension_pole[")) result.put(pos, M38DispenserFixtureLayout.blockId(state));
        });
        movingPayload().forEach((pos, state) -> result.put(pos, M38DispenserFixtureLayout.blockId(state)));
        result.put(BlockPos.ZERO, "create:mechanical_piston_head");
        return Collections.unmodifiableMap(result);
    }

    public static Map<BlockPos, String> staticOuter() {
        final Map<BlockPos, String> result = new LinkedHashMap<>();
        mechanism().forEach((pos, state) -> {
            if (pos.equals(BlockPos.ZERO) || !capturedBlockIds().containsKey(pos)) result.put(pos, state);
        });
        return Collections.unmodifiableMap(result);
    }

    public static Map<BlockPos, String> station(final BlockPos lower, final Direction facing) {
        final Map<BlockPos, String> result = new LinkedHashMap<>();
        result.put(lower.below(), "minecraft:stone");
        result.put(lower, "minecraft:iron_door[facing=" + facing.getName() + ",half=lower,hinge=left,open=true,powered=false]");
        result.put(lower.above(), "minecraft:iron_door[facing=" + facing.getName() + ",half=upper,hinge=left,open=true,powered=false]");
        return result;
    }

    public static Set<BlockPos> reservation() {
        final Set<BlockPos> cells = new LinkedHashSet<>(mechanism().keySet());
        cells.addAll(station(PARKED, Direction.NORTH).keySet());
        // Only the short capture/stop envelope is needed, plus its yawed counterpart.
        for (int x = 1; x <= 3; x++) for (int y = -1; y <= 3; y++) for (int z = -3; z <= 2; z++) {
            cells.add(new BlockPos(x, y, z));
            cells.add(new BlockPos(z, y, -x));
        }
        return Collections.unmodifiableSet(cells);
    }

    public static AABB glue(final BlockPos origin) { return M38DispenserFixtureLayout.outerGlueBounds(origin); }
}
