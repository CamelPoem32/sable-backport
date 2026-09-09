package dev.simulated_team.simulated.content.blocks.m24;

import dev.simulated_team.simulated.index.SimulatedBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Owns the normal Create server-tick lifecycle used by the Rope Winch. */
public final class M24WinchBlockEntity extends M24PhysicalBlockEntity {

    public M24WinchBlockEntity(final BlockPos pos, final BlockState state) {
        super(M24Family.ROPE_WINCH, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        this.simulated$tickWinchProduction();
    }
}
