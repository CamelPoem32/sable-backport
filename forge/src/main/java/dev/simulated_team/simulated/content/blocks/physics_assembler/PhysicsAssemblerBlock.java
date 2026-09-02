package dev.simulated_team.simulated.content.blocks.physics_assembler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class PhysicsAssemblerBlock extends BaseEntityBlock {

    public PhysicsAssemblerBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new PhysicsAssemblerBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(final BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean triggerEvent(final BlockState state, final Level level, final BlockPos pos, final int id, final int param) {
        super.triggerEvent(state, level, pos, id, param);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity != null && blockEntity.triggerEvent(id, param);
    }

    @Override
    public int getLightBlock(final BlockState state, final BlockGetter level, final BlockPos pos) {
        return 0;
    }
}
