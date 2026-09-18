package dev.ryanhcode.sable.mixin.m28;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelDiagnostics;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records the initiating source before Create's unchanged propagation-conflict destruction. */
@Mixin(RotationPropagator.class)
public abstract class RotationConflictTraceMixin {
    @Inject(method = "propagateNewSource", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z",
            ordinal = 0), require = 1)
    private static void sable$beforeOverspeedOrFlickerDestruction(final KineticBlockEntity source,
                                                                   final CallbackInfo ci,
                                                                   @Local(index = 4) final KineticBlockEntity neighbour,
                                                                   @Local(index = 7) final float conveyedSpeed) {
        sable$record(source, neighbour, conveyedSpeed, "OVERSPEED_OR_FLICKER");
    }

    @Inject(method = "propagateNewSource", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z",
            ordinal = 1), require = 1)
    private static void sable$beforeOpposingDirectionDestruction(final KineticBlockEntity source,
                                                                  final CallbackInfo ci,
                                                                  @Local(index = 4) final KineticBlockEntity neighbour,
                                                                  @Local(index = 7) final float conveyedSpeed) {
        sable$record(source, neighbour, conveyedSpeed, "OPPOSING_DIRECTION");
    }

    @Inject(method = "propagateNewSource", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z",
            ordinal = 2), require = 1)
    private static void sable$beforeCompetingSpeedDestruction(final KineticBlockEntity source,
                                                               final CallbackInfo ci,
                                                               @Local(index = 4) final KineticBlockEntity neighbour,
                                                               @Local(index = 7) final float conveyedSpeed) {
        sable$record(source, neighbour, conveyedSpeed, "COMPETING_SPEED");
    }

    private static void sable$record(final KineticBlockEntity source, final KineticBlockEntity neighbour,
                                     final float conveyedSpeed, final String conflictType) {
        final Level level = source.getLevel();
        if (level != null) {
            SteeringWheelDiagnostics.recordKineticDestruction(level, source.getBlockPos(), source,
                    "RotationPropagator.propagateNewSource", neighbour.getBlockPos(),
                    conveyedSpeed, source.getTheoreticalSpeed(), conflictType);
        }
    }
}
