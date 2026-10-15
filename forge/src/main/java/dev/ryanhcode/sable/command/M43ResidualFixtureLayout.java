package dev.ryanhcode.sable.command;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Four isolated copies of the accepted native piston/chassis attachment, behind one outer assembler. */
public final class M43ResidualFixtureLayout {
    public static final String[] KINDS = {"PLOUGH", "DRILL", "SAW", "BLAZE"};
    public static final BlockPos ASSEMBLER = new BlockPos(0, 1, -2);
    private M43ResidualFixtureLayout() { }
    public static BlockPos lane(final int index) { return new BlockPos(0, 0, index * 5); }

    public static Map<BlockPos, String> blocks() {
        final Map<BlockPos, String> result = new LinkedHashMap<>();
        final String[] actors = {"create:mechanical_plough[facing=east]", "create:mechanical_drill[facing=east]",
                "create:mechanical_saw[facing=east]", "create:blaze_burner[blaze=smouldering]"};
        for (int lane = 0; lane < 4; lane++) {
            final int index = lane;
            M38DispenserFixtureLayout.blocks(true, false).forEach((p, s) -> {
                if (p.getZ() >= -1 && !p.equals(new BlockPos(0, 1, -2))) result.put(p.offset(lane(index)), s);
            });
            result.put(lane(lane).offset(1, 1, 0), actors[lane]);
        }
        for (int z = -2; z <= 16; z++) result.putIfAbsent(new BlockPos(0, -2, z), "minecraft:iron_block");
        result.put(new BlockPos(0, -1, -2), "minecraft:iron_block");
        result.put(new BlockPos(0, 0, -2), "minecraft:iron_block");
        result.put(ASSEMBLER, "simulated:physics_assembler");
        return Collections.unmodifiableMap(result);
    }

    public static AABB glue(final BlockPos origin) {
        return new AABB(origin.getX(), origin.getY() - 2, origin.getZ() - 2,
                origin.getX() + 1, origin.getY() + 2, origin.getZ() + 17);
    }

    public static Set<BlockPos> reservation() {
        final Set<BlockPos> result = new LinkedHashSet<>(blocks().keySet());
        for (final BlockPos p : blocks().keySet()) result.add(new BlockPos(p.getZ(), p.getY(), -p.getX()));
        // Exact four payload lanes, their twelve-block strokes, and yawed counterparts, not an empty cuboid.
        for (int lane = 0; lane < 4; lane++) for (int x = 1; x <= 14; x++) {
            for (int y = 0; y <= 2; y++) for (int z = lane * 5; z <= lane * 5 + 1; z++) {
                final BlockPos p = new BlockPos(x, y, z);
                result.add(p); result.add(new BlockPos(z, y, -x));
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public static boolean allPassed(final boolean plough, final boolean drill, final boolean saw,
                                    final boolean blaze, final boolean drillBlock, final boolean sawBlock) {
        return plough && drill && saw && blaze && drillBlock && sawBlock;
    }
}
