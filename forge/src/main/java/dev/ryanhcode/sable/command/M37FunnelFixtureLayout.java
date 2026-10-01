package dev.ryanhcode.sable.command;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Physical footprint of the command-generated INSERT lane, not Create gameplay geometry. */
public final class M37FunnelFixtureLayout {
    private M37FunnelFixtureLayout() {
    }

    public static AABB movingPayloadSweep(final BlockPos origin) {
        return new AABB(origin.getX() + 1, origin.getY(), origin.getZ(),
                origin.getX() + 14, origin.getY() + 2, origin.getZ() + 2);
    }

    public static boolean collisionFree(final BlockPos origin, final BlockPos collector) {
        final AABB sweep = movingPayloadSweep(origin);
        return !sweep.intersects(new AABB(collector))
                && !sweep.intersects(new AABB(collector.below()))
                && !sweep.intersects(new AABB(collector.south()));
    }

    public static double minimumBlockClearance(final BlockPos origin, final BlockPos collector) {
        final AABB sweep = movingPayloadSweep(origin);
        final AABB stationary = new AABB(collector);
        return Math.max(0, Math.max(stationary.minY - sweep.maxY, sweep.minY - stationary.maxY));
    }

    public static boolean fallingItemReachesIntake(final Vec3 spawn, final BlockPos collector) {
        final double x = spawn.x;
        final double z = spawn.z;
        final AABB intake = new AABB(collector);
        return x + 0.125 > intake.minX && x - 0.125 < intake.maxX
                && z + 0.125 > intake.minZ && z - 0.125 < intake.maxZ
                && spawn.y > intake.maxY;
    }
}
