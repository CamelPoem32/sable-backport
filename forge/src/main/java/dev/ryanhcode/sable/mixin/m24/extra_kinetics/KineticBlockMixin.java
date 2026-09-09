package dev.ryanhcode.sable.mixin.m24.extra_kinetics;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KineticBlock.class)
public final class KineticBlockMixin {
    @Inject(method = "updateIndirectNeighbourShapes", at = @At("TAIL"))
    private void simulated$resetExtra(final BlockState state, final LevelAccessor level, final BlockPos pos,
                                      final int flags, final int count, final CallbackInfo ci,
                                      @Local final BlockEntity blockEntity) {
        if (blockEntity instanceof final ExtraKinetics extra) {
            final KineticBlockEntity output = extra.getExtraKinetics();
            if (output != null) {
                output.warnOfMovement();
                output.clearKineticInformation();
                output.updateSpeed = true;
            }
        }
    }
}
