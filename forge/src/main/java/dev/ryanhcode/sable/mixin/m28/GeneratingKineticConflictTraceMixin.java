package dev.ryanhcode.sable.mixin.m28;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes Create's existing incompatible-generator destruction without changing the call. */
@Mixin(GeneratingKineticBlockEntity.class)
public abstract class GeneratingKineticConflictTraceMixin {
    @Inject(method = "applyNewSpeed", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z"),
            require = 1)
    private void sable$beforeGeneratorDestruction(final float newSpeed, final float currentSpeed,
                                                   final CallbackInfo ci) {
        final GeneratingKineticBlockEntity generator = (GeneratingKineticBlockEntity) (Object) this;
        final Level level = generator.getLevel();
        final BlockPos pos = generator.getBlockPos();
        if (level != null) {
            SteeringWheelDiagnostics.recordKineticDestruction(level, pos, generator,
                    "GeneratingKineticBlockEntity.applyNewSpeed", generator.source, newSpeed, currentSpeed,
                    "GENERATOR_SPEED_CONFLICT");
        }
    }
}
