package dev.ryanhcode.sable.mixin.m24.extra_kinetics;

import com.simibubi.create.content.kinetics.KineticNetwork;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraBlockPos;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KineticNetwork.class)
public final class KineticNetworkMixin {
    @Redirect(method = {"calculateCapacity", "calculateStress"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity simulated$resolveExtraMember(final Level level, final BlockPos pos) {
        final BlockEntity found = level.getBlockEntity(pos);
        return found instanceof final ExtraKinetics extra && pos instanceof ExtraBlockPos
                ? extra.getExtraKinetics()
                : found;
    }
}
