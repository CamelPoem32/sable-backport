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

/**
 * Sable-aware read-only block lookup for Create 6.0.8 inherited contraption
 * structure search. Rope Pulley and Elevator Pulley pass raw Sable plot
 * positions into this shared path while their blocks are owned by Sable plot
 * storage.
 */
@Mixin(targets = "com.simibubi.create.content.contraptions.Contraption", remap = false)
public class ContraptionMixin {
    @WrapOperation(
            method = {"searchMovedStructure", "moveBlock", "capture"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
                    remap = true))
    private BlockState sable$getContraptionAssemblyState(final Level level,
                                                         final BlockPos pos,
                                                         final Operation<BlockState> original) {
        return SableCreateControllerBlockLookup.getBlockState(level, pos, original,
                "contraption_structure_search");
    }

    @WrapOperation(
            method = {"moveBlock", "capture", "getBlockEntityNBT"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
                    remap = true))
    private BlockEntity sable$getContraptionAssemblyBlockEntity(final Level level,
                                                                final BlockPos pos,
                                                                final Operation<BlockEntity> original) {
        return SableCreateControllerBlockLookup.getBlockEntity(level, pos, original,
                "contraption_structure_search");
    }
}
