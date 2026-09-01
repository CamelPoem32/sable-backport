package dev.ryanhcode.sable.mixin.m20.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.compatibility.create.controllers.SableCreateControllerBlockLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Lets Create 6.0.8 ElevatorContraption capture Sable-stored cabin/contact blocks. */
@Mixin(targets = "com.simibubi.create.content.contraptions.elevator.ElevatorContraption", remap = false)
public class ElevatorContraptionMixin {
    @WrapOperation(
            method = "capture(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lorg/apache/commons/lang3/tuple/Pair;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
                    remap = true))
    private BlockState sable$getElevatorCaptureState(final Level level,
                                                     final BlockPos pos,
                                                     final Operation<BlockState> original) {
        return SableCreateControllerBlockLookup.getBlockState(level, pos, original,
                "elevator_contraption_capture");
    }

    @WrapOperation(
            method = "broadcastFloorData(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
                    remap = true))
    private BlockEntity sable$getElevatorContactEntity(final Level level,
                                                       final BlockPos pos,
                                                       final Operation<BlockEntity> original) {
        return SableCreateControllerBlockLookup.getBlockEntity(level, pos, original,
                "elevator_floor_broadcast");
    }
}
