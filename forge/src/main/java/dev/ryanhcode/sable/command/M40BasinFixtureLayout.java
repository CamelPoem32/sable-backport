package dev.ryanhcode.sable.command;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Horizontal native bearing: the Basin must invert to exercise Create's one-shot dump. */
public final class M40BasinFixtureLayout {
    public static final BlockPos BASIN = new BlockPos(1, 1, 0);
    public static final BlockPos MOTOR = new BlockPos(-2, 0, 0);
    public static final BlockPos CLUTCH = new BlockPos(-1, 0, 0);
    public static final BlockPos LEVER = new BlockPos(-1, 1, 0);
    public static final BlockPos ASSEMBLER = new BlockPos(0, 1, -2);

    private M40BasinFixtureLayout() { }

    public static Map<BlockPos, String> mechanism() {
        final Map<BlockPos, String> cells = new LinkedHashMap<>();
        cells.put(BlockPos.ZERO, "create:mechanical_bearing[facing=east]");
        cells.put(MOTOR, "create:creative_motor[facing=east]");
        cells.put(CLUTCH, "create:clutch[axis=x,powered=false]");
        cells.put(LEVER, "minecraft:lever[face=floor,facing=east,powered=true]");
        for (int x = -2; x <= 0; x++) cells.put(new BlockPos(x, 0, -1), "minecraft:iron_block");
        cells.put(new BlockPos(0, 0, -2), "minecraft:iron_block");
        cells.put(ASSEMBLER, "simulated:physics_assembler");
        cells.put(new BlockPos(1, 0, 0), "create:radial_chassis[axis=x,sticky_north=true,sticky_east=true]");
        cells.put(BASIN, "create:basin[facing=down]");
        return Collections.unmodifiableMap(cells);
    }

    public static Map<BlockPos, String> platform() {
        final Map<BlockPos, String> floor = new LinkedHashMap<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) floor.put(new BlockPos(x, -4, z), "minecraft:stone");
        }
        return Collections.unmodifiableMap(floor);
    }

    public static Map<BlockPos, String> all() {
        final Map<BlockPos, String> cells = new LinkedHashMap<>(mechanism());
        cells.putAll(platform());
        return Collections.unmodifiableMap(cells);
    }

    public static Set<BlockPos> reservation() {
        final Set<BlockPos> cells = new LinkedHashSet<>(all().keySet());
        // Exact two rotor planes and fall corridors, not a large empty cuboid.
        for (int y = -3; y <= 2; y++) {
            for (int tangent = -2; tangent <= 2; tangent++) {
                cells.add(new BlockPos(1, y, tangent));
                cells.add(new BlockPos(tangent, y, -1));
            }
        }
        return Collections.unmodifiableSet(cells);
    }

    public static AABB glue(final BlockPos origin) {
        return new AABB(origin.getX() - 2, origin.getY(), origin.getZ() - 2,
                origin.getX() + 1, origin.getY() + 2, origin.getZ() + 1);
    }
}
