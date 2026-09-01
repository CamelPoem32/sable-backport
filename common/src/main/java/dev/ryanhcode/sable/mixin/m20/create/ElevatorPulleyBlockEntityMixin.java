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

/** Sable-aware storage lookups for Create 6.0.8 Elevator Pulley assembly and contacts. */
@Mixin(targets = "com.simibubi.create.content.contraptions.elevator.ElevatorPulleyBlockEntity", remap = false)
public class ElevatorPulleyBlockEntityMixin {
    @WrapOperation(
            method = {"assemble", "triggerContact"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
                    remap = true))
    private BlockState sable$getElevatorBlockState(final Level level,
                                                   final BlockPos pos,
                                                   final Operation<BlockState> original) {
        return SableCreateControllerBlockLookup.getBlockState(level, pos, original,
                "elevator_assembly_or_contact");
    }

    @WrapOperation(
            method = {"triggerContact"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;isLoaded(Lnet/minecraft/core/BlockPos;)Z",
                    remap = true))
    private boolean sable$isElevatorContactLoaded(final Level level,
                                                  final BlockPos pos,
                                                  final Operation<Boolean> original) {
        return SableCreateControllerBlockLookup.isLoaded(level, pos, original, "elevator_contact_loaded");
    }

    @WrapOperation(
            method = {"assemble", "clicked"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
                    remap = true))
    private BlockEntity sable$getElevatorBlockEntity(final Level level,
                                                     final BlockPos pos,
                                                     final Operation<BlockEntity> original) {
        return SableCreateControllerBlockLookup.getBlockEntity(level, pos, original,
                "elevator_controller_or_mirror");
    }
}
