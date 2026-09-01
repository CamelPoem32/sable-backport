package dev.ryanhcode.sable.mixin.m20.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.compatibility.create.controllers.SableCreateControllerBlockLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps Create 6.0.8 ElevatorColumn floor discovery on Sable plot storage. */
@Mixin(targets = "com.simibubi.create.content.contraptions.elevator.ElevatorColumn", remap = false)
public class ElevatorColumnMixin {
    @WrapOperation(
            method = {
                    "markDirty",
                    "lambda$compileNamesList$3"
            },
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
                    remap = true))
    private BlockEntity sable$getElevatorColumnBlockEntity(final LevelAccessor level,
                                                           final BlockPos pos,
                                                           final Operation<BlockEntity> original) {
        return SableCreateControllerBlockLookup.getBlockEntity(level, pos, original,
                "elevator_column_contact_entity");
    }

    @WrapOperation(
            method = "lambda$floorReached$2(Lnet/minecraft/world/level/LevelAccessor;Ljava/lang/String;Lnet/minecraft/core/BlockPos;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
                    remap = true))
    private static BlockEntity sable$getFloorReachedBlockEntity(final LevelAccessor level,
                                                                final BlockPos pos,
                                                                final Operation<BlockEntity> original) {
        return SableCreateControllerBlockLookup.getBlockEntity(level, pos, original,
                "elevator_column_floor_reached_entity");
    }

    @WrapOperation(
            method = "initNames",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
                    remap = true))
    private BlockEntity sable$getInitNamesBlockEntity(final Level level,
                                                      final BlockPos pos,
                                                      final Operation<BlockEntity> original) {
        return SableCreateControllerBlockLookup.getBlockEntity(level, pos, original,
                "elevator_column_init_names_entity");
    }

    @WrapOperation(
            method = "lambda$gatherAll$5",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
                    remap = true))
    private BlockState sable$getElevatorColumnBlockState(final LevelAccessor level,
                                                         final BlockPos pos,
                                                         final Operation<BlockState> original) {
        return SableCreateControllerBlockLookup.getBlockState(level, pos, original,
                "elevator_column_gather_state");
    }

    @WrapOperation(
            method = "lambda$gatherAll$5",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
                    remap = true))
    private boolean sable$setElevatorColumnBlock(final LevelAccessor level,
                                                 final BlockPos pos,
                                                 final BlockState state,
                                                 final int flags,
                                                 final Operation<Boolean> original) {
        return SableCreateControllerBlockLookup.setBlock(level, pos, state, flags,
                original, "elevator_column_gather_set");
    }
}
