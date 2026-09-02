package dev.simulated_team.simulated.content.blocks.physics_assembler;

import dev.simulated_team.simulated.index.SimulatedBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PhysicsAssemblerBlockEntity extends BlockEntity {

    public PhysicsAssemblerBlockEntity(final BlockPos pos, final BlockState state) {
        super(SimulatedBlockEntityTypes.PHYSICS_ASSEMBLER.get(), pos, state);
    }
}
