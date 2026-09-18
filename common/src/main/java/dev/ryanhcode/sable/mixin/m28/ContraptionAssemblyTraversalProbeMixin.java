package dev.ryanhcode.sable.mixin.m28;

import com.simibubi.create.content.contraptions.Contraption;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableM28BearingAssemblyTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Contraption.class, remap = false)
public abstract class ContraptionAssemblyTraversalProbeMixin {
    @Inject(method = "searchMovedStructure(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
            at = @At("HEAD"))
    private void sable$m28SearchHead(final Level level, final BlockPos start,
                                    final @Nullable Direction movementDirection,
                                    final CallbackInfoReturnable<Boolean> cir) {
        SableM28BearingAssemblyTrace.searchEnter((Contraption) (Object) this, level, start, movementDirection);
    }

    @Inject(method = "searchMovedStructure(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
            at = @At("RETURN"))
    private void sable$m28SearchReturn(final Level level, final BlockPos start,
                                      final @Nullable Direction movementDirection,
                                      final CallbackInfoReturnable<Boolean> cir) {
        SableM28BearingAssemblyTrace.searchReturn((Contraption) (Object) this, level, start, movementDirection,
                cir.getReturnValueZ());
    }

    @Inject(method = "capture(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lorg/apache/commons/lang3/tuple/Pair;",
            at = @At("HEAD"))
    private void sable$m28CaptureHead(final Level level, final BlockPos pos,
                                     final CallbackInfoReturnable<Pair<StructureTemplate.StructureBlockInfo, BlockEntity>> cir) {
        SableM28BearingAssemblyTrace.captureEnter(level, pos);
    }

    @Inject(method = "capture(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lorg/apache/commons/lang3/tuple/Pair;",
            at = @At("RETURN"))
    private void sable$m28CaptureReturn(final Level level, final BlockPos pos,
                                       final CallbackInfoReturnable<Pair<StructureTemplate.StructureBlockInfo, BlockEntity>> cir) {
        SableM28BearingAssemblyTrace.captureReturn(level, pos, cir.getReturnValue());
    }
}
