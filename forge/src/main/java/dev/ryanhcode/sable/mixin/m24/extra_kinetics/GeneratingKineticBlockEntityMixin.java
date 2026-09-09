package dev.ryanhcode.sable.mixin.m24.extra_kinetics;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraBlockPos;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GeneratingKineticBlockEntity.class)
public abstract class GeneratingKineticBlockEntityMixin extends KineticBlockEntity {
    protected GeneratingKineticBlockEntityMixin(final BlockEntityType<?> type, final BlockPos pos,
                                                 final BlockState state) {
        super(type, pos, state);
    }

    @Redirect(method = "setSource", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity simulated$resolveExtraSource(final Level level, final BlockPos pos) {
        final BlockEntity found = level.getBlockEntity(pos);
        return found instanceof final ExtraKinetics extra && pos instanceof ExtraBlockPos
                ? extra.getExtraKinetics()
                : found;
    }
}
