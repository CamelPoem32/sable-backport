package dev.simulated_team.simulated.util.extra_kinetics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

/** Marks the virtual second kinetic port occupying the parent block's coordinates. */
public final class ExtraBlockPos extends BlockPos {
    public ExtraBlockPos(final Vec3i pos) {
        super(pos.getX(), pos.getY(), pos.getZ());
    }
}
