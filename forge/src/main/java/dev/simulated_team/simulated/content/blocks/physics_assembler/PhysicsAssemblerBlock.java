package dev.simulated_team.simulated.content.blocks.physics_assembler;

import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class PhysicsAssemblerBlock extends BaseEntityBlock implements BlockSubLevelAssemblyListener {

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
    public InteractionResult use(final BlockState state, final Level level, final BlockPos pos,
                                 final Player player, final InteractionHand hand, final BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof final PhysicsAssemblerBlockEntity assembler) {
            assembler.assembleOrDisassemble(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
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

    @Override
    public void afterMove(final ServerLevel originLevel, final ServerLevel resultingLevel, final BlockState newState,
                          final BlockPos oldPos, final BlockPos newPos) {
        final BlockEntity blockEntity = resultingLevel.getBlockEntity(newPos);
        if (blockEntity instanceof final PhysicsAssemblerBlockEntity assembler) {
            assembler.setParent(resultingLevel);
        }
    }
}
