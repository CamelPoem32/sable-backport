package dev.ryanhcode.sable.mixin.m20.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.compatibility.create.controllers.SableCreateControllerBlockLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Sable-aware elevator column lookup for Create 6.0.8 Elevator Contact blocks. */
@Mixin(targets = "com.simibubi.create.content.contraptions.elevator.ElevatorContactBlock", remap = false)
public class ElevatorContactBlockMixin {
    @WrapOperation(
            method = "getColumnCoords(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)Lcom/simibubi/create/content/contraptions/elevator/ElevatorColumn$ColumnCoords;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
                    remap = true))
    private static BlockState sable$getElevatorContactState(final LevelAccessor level,
                                                            final BlockPos pos,
                                                            final Operation<BlockState> original) {
        return SableCreateControllerBlockLookup.getBlockState(level, pos, original,
                "elevator_column_coords");
    }
}
