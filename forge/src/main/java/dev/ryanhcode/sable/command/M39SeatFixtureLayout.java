package dev.ryanhcode.sable.command;

import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The proven piston/chassis payload, with a Seat instead of a dispensing actor. */
public final class M39SeatFixtureLayout {
    private M39SeatFixtureLayout() {
    }

    public static Map<BlockPos, String> mechanism() {
        final Map<BlockPos, String> cells = new LinkedHashMap<>(M38DispenserFixtureLayout.blocks(true, false));
        cells.put(new BlockPos(1, 1, 0), "create:red_seat[waterlogged=false]");
        return Collections.unmodifiableMap(cells);
    }

    public static Map<BlockPos, String> platform() {
        final Map<BlockPos, String> cells = new LinkedHashMap<>();
        // A full air gap separates the parent platform from the lowest body block.
        for (int x = -14; x <= 15; x++) {
            for (int z = -15; z <= 6; z++) cells.put(new BlockPos(x, -4, z), "minecraft:stone");
        }
        for (int x = -1; x <= 3; x++) {
            for (int step = 0; step < 4; step++) cells.put(new BlockPos(x, -step, 3 + step), "minecraft:stone");
        }
        return Collections.unmodifiableMap(cells);
    }

    public static Map<BlockPos, String> all() {
        final Map<BlockPos, String> cells = new LinkedHashMap<>(mechanism());
        cells.putAll(platform());
        return Collections.unmodifiableMap(cells);
    }
}
