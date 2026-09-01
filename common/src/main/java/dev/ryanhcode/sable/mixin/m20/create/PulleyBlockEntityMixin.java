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

/** Sable-aware storage lookups for Create 6.0.8 Rope Pulley assembly scans. */
@Mixin(targets = "com.simibubi.create.content.contraptions.pulley.PulleyBlockEntity", remap = false)
public class PulleyBlockEntityMixin {
    @WrapOperation(
            method = {"visitNewPosition", "assemble", "disassemble"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
                    remap = true))
    private BlockState sable$getPulleyBlockState(final Level level,
                                                 final BlockPos pos,
                                                 final Operation<BlockState> original) {
        return SableCreateControllerBlockLookup.getBlockState(level, pos, original,
                "rope_pulley_scan_or_assembly");
    }

    @WrapOperation(
            method = {"tick", "startMirroringOther", "notifyMirrorsOfDisassembly"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
                    remap = true))
    private BlockEntity sable$getPulleyBlockEntity(final Level level,
                                                   final BlockPos pos,
                                                   final Operation<BlockEntity> original) {
        return SableCreateControllerBlockLookup.getBlockEntity(level, pos, original,
                "rope_pulley_controller_or_mirror");
    }
}
