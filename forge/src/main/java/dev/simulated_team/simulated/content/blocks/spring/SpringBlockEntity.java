package dev.simulated_team.simulated.content.blocks.spring;

import dev.simulated_team.simulated.index.SimulatedBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SpringBlockEntity extends BlockEntity {

    public SpringBlockEntity(final BlockPos pos, final BlockState state) {
        super(SimulatedBlockEntityTypes.SPRING.get(), pos, state);
    }
}
