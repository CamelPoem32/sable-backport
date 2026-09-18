package dev.ryanhcode.sable.mixin.m28;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableM28BearingAssemblyTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MechanicalBearingBlockEntity.class, remap = false)
public abstract class MechanicalBearingAssemblyProbeMixin {
    @Shadow protected boolean running;
    @Shadow protected boolean assembleNextTick;
    @Shadow protected ControlledContraptionEntity movedContraption;
    @Shadow protected AssemblyException lastException;

    @Inject(method = "onSpeedChanged(F)V", at = @At("HEAD"))
    private void sable$m28SpeedChangedHead(final float previousSpeed, final CallbackInfo ci) {
        SableM28BearingAssemblyTrace.speedChanged((MechanicalBearingBlockEntity) (Object) this, previousSpeed,
                this.running, this.assembleNextTick, "ENTER");
    }

    @Inject(method = "onSpeedChanged(F)V", at = @At("RETURN"))
    private void sable$m28SpeedChangedReturn(final float previousSpeed, final CallbackInfo ci) {
        SableM28BearingAssemblyTrace.speedChanged((MechanicalBearingBlockEntity) (Object) this, previousSpeed,
                this.running, this.assembleNextTick, "RETURN");
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void sable$m28TickGate(final CallbackInfo ci) {
        SableM28BearingAssemblyTrace.tickGate((MechanicalBearingBlockEntity) (Object) this, this.running,
                this.assembleNextTick, this.movedContraption);
    }

    @Inject(method = "assemble()V", at = @At("HEAD"))
    private void sable$m28AssembleHead(final CallbackInfo ci) {
        SableM28BearingAssemblyTrace.beginAssembly((MechanicalBearingBlockEntity) (Object) this,
                this.running, this.assembleNextTick);
    }

    @Inject(method = "assemble()V", at = @At("RETURN"))
    private void sable$m28AssembleReturn(final CallbackInfo ci) {
        SableM28BearingAssemblyTrace.endAssembly((MechanicalBearingBlockEntity) (Object) this,
                this.running, this.assembleNextTick, this.movedContraption, this.lastException);
    }

    @WrapOperation(method = "assemble()V", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/bearing/BearingContraption;assemble(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean sable$m28ContraptionAssemble(final BearingContraption contraption, final Level level,
                                                 final BlockPos bearingPos, final Operation<Boolean> original)
            throws AssemblyException {
        return SableM28BearingAssemblyTrace.contraptionAssemble(contraption, level, bearingPos,
                () -> original.call(contraption, level, bearingPos));
    }

    @WrapOperation(method = "assemble()V", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/bearing/BearingContraption;removeBlocksFromWorld(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private void sable$m28RemoveCapturedBlocks(final BearingContraption contraption, final Level level,
                                               final BlockPos offset, final Operation<Void> original) {
        original.call(contraption, level, offset);
        SableM28BearingAssemblyTrace.blocksRemoved(contraption, level, offset);
    }

    @WrapOperation(method = "assemble()V", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/ControlledContraptionEntity;create(Lnet/minecraft/world/level/Level;Lcom/simibubi/create/content/contraptions/IControlContraption;Lcom/simibubi/create/content/contraptions/Contraption;)Lcom/simibubi/create/content/contraptions/ControlledContraptionEntity;"))
    private ControlledContraptionEntity sable$m28CreateEntity(final Level level, final IControlContraption controller,
                                                              final Contraption contraption,
                                                              final Operation<ControlledContraptionEntity> original) {
        return SableM28BearingAssemblyTrace.entityCreated(level, controller, contraption,
                original.call(level, controller, contraption));
    }

    @WrapOperation(method = "assemble()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", remap = true))
    private boolean sable$m28AddEntity(final Level level, final Entity entity, final Operation<Boolean> original) {
        return SableM28BearingAssemblyTrace.addEntity(level, entity, () -> original.call(level, entity));
    }

    @Inject(method = "disassemble()V", at = @At("HEAD"))
    private void sable$m28DisassembleHead(final CallbackInfo ci) {
        SableM28BearingAssemblyTrace.disassemble((MechanicalBearingBlockEntity) (Object) this, this.running,
                this.movedContraption, "ENTER");
    }

    @Inject(method = "disassemble()V", at = @At("RETURN"))
    private void sable$m28DisassembleReturn(final CallbackInfo ci) {
        SableM28BearingAssemblyTrace.disassemble((MechanicalBearingBlockEntity) (Object) this, this.running,
                this.movedContraption, "RETURN");
    }
}
