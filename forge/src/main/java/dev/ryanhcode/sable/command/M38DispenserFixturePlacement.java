package dev.ryanhcode.sable.command;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Block-accurate static and moving-space reservation for the M38 test fixture. */
public final class M38DispenserFixturePlacement {
    private M38DispenserFixturePlacement() {
    }

    public static Map<BlockPos, Category> requiredAir(final boolean ignition, final boolean yaw) {
        final Map<BlockPos, Category> cells = new LinkedHashMap<>();
        for (int x = 2; x <= 13; x++) {
            cells.put(new BlockPos(x, 0, 0), Category.MUST_BE_AIR);
            cells.put(new BlockPos(x, 0, 1), Category.MUST_BE_AIR);
            cells.put(new BlockPos(x, 1, 0), Category.MUST_BE_AIR);
        }
        for (int x = 14; x <= 18; x++) {
            for (int y = 0; y <= 2; y++) {
                cells.put(new BlockPos(x, y, 0), Category.EMISSION_CORRIDOR);
            }
        }
        if (yaw) {
            for (int z = -3; z >= -13; z--) {
                cells.put(new BlockPos(0, 0, z), Category.MUST_BE_AIR);
                cells.put(new BlockPos(1, 0, z), Category.MUST_BE_AIR);
                cells.put(new BlockPos(0, 1, z), Category.MUST_BE_AIR);
            }
            for (int z = -14; z >= -18; z--) {
                for (int y = 0; y <= 2; y++) {
                    cells.put(new BlockPos(0, y, z), Category.EMISSION_CORRIDOR);
                }
            }
        }
        for (int x = -12; x <= -1; x++) {
            cells.put(new BlockPos(x, 0, 0), Category.INNER_CREATE_MECHANISM);
        }
        for (final BlockPos cell : List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0),
                new BlockPos(1, 0, 1), new BlockPos(1, 1, 0), new BlockPos(0, -2, 0),
                new BlockPos(0, -1, 0), new BlockPos(0, -1, 1))) {
            cells.put(cell, Category.INNER_CREATE_MECHANISM);
        }
        for (final BlockPos cell : List.of(new BlockPos(0, 0, -2), new BlockPos(0, 0, -1),
                new BlockPos(0, 1, -2))) {
            cells.put(cell, Category.ASSEMBLER_HULL);
        }
        if (ignition) {
            cells.put(new BlockPos(14, 0, 0), Category.WILL_BE_REPLACED_BY_FIXTURE);
        }
        return Collections.unmodifiableMap(cells);
    }

    public static Category classify(final BlockPos relative, final boolean ignition, final boolean yaw) {
        final Category required = requiredAir(ignition, yaw).get(relative);
        return required != null ? required : relative.getY() <= -3
                ? Category.MAY_BE_SUPPORT_GROUND : Category.UNRESERVED;
    }

    public static SearchResult find(final BlockPos preferred, final boolean ignition, final boolean yaw,
                                    final CellProbe probe) {
        final Map<BlockPos, Category> required = requiredAir(ignition, yaw);
        final List<Rejection> rejected = new ArrayList<>();
        int checked = 0;
        for (final BlockPos offset : candidateOffsets()) {
            final BlockPos origin = preferred.offset(offset);
            checked++;
            Rejection first = null;
            for (final Map.Entry<BlockPos, Category> entry : required.entrySet()) {
                first = probe.inspect(origin.offset(entry.getKey()), entry.getValue());
                if (first != null) {
                    break;
                }
            }
            if (first == null) {
                return new SearchResult(origin, checked, List.copyOf(rejected));
            }
            rejected.add(first);
        }
        return new SearchResult(null, checked, List.copyOf(rejected));
    }

    public static List<BlockPos> candidateOffsets() {
        final List<BlockPos> offsets = new ArrayList<>();
        for (final int lift : new int[] {0, 2, 4}) {
            for (final BlockPos horizontal : List.of(BlockPos.ZERO,
                    new BlockPos(8, 0, 0), new BlockPos(0, 0, 8),
                    new BlockPos(-8, 0, 0), new BlockPos(0, 0, -8),
                    new BlockPos(8, 0, 8), new BlockPos(-8, 0, 8),
                    new BlockPos(-8, 0, -8), new BlockPos(8, 0, -8))) {
                offsets.add(horizontal.above(lift));
            }
        }
        return List.copyOf(offsets);
    }

    public enum Category {
        MUST_BE_AIR, WILL_BE_REPLACED_BY_FIXTURE, MAY_BE_SUPPORT_GROUND,
        EMISSION_CORRIDOR, ASSEMBLER_HULL, INNER_CREATE_MECHANISM, UNRESERVED
    }

    public enum Reason {
        UNLOADED, OCCUPIED, BLOCK_ENTITY, OTHER
    }

    public interface CellProbe {
        Rejection inspect(BlockPos pos, Category category);
    }

    public record Rejection(BlockPos pos, Category category, boolean chunkLoaded, String state,
                            String fluid, String blockEntity, boolean replaceable, Reason reason) {
    }

    public record SearchResult(BlockPos origin, int checked, List<Rejection> rejected) {
    }
}
