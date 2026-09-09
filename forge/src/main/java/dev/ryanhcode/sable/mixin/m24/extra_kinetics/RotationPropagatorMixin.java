package dev.ryanhcode.sable.mixin.m24.extra_kinetics;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraBlockPos;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.ListIterator;

@Mixin(RotationPropagator.class)
public abstract class RotationPropagatorMixin {
    @WrapOperation(method = {"handleRemoved", "propagateMissingSource", "findConnectedNeighbour"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private static BlockEntity simulated$resolveExtra(final Level level, final BlockPos pos,
                                                       final Operation<BlockEntity> original) {
        final BlockEntity found = original.call(level, pos);
        return found instanceof final ExtraKinetics extra && pos instanceof ExtraBlockPos
                ? extra.getExtraKinetics() : found;
    }

    @ModifyReceiver(method = "getRotationSpeedModifier", remap = false, at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/base/IRotate;hasShaftTowards(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z", ordinal = 0))
    private static IRotate simulated$fromShaftConfig(final IRotate original, final LevelReader level,
                                                      final BlockPos pos, final BlockState state,
                                                      final Direction direction,
                                                      @Local(argsOnly = true, ordinal = 0) final KineticBlockEntity from) {
        return simulated$config(original, from);
    }

    @ModifyReceiver(method = "getRotationSpeedModifier", remap = false, at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/base/IRotate;hasShaftTowards(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z", ordinal = 1))
    private static IRotate simulated$toShaftConfig(final IRotate original, final LevelReader level,
                                                    final BlockPos pos, final BlockState state,
                                                    final Direction direction,
                                                    @Local(argsOnly = true, ordinal = 1) final KineticBlockEntity to) {
        return simulated$config(original, to);
    }

    @ModifyReceiver(method = "getRotationSpeedModifier", remap = false, at = {
            @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/base/IRotate;getRotationAxis(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/core/Direction$Axis;", ordinal = 0),
            @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/base/IRotate;getRotationAxis(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/core/Direction$Axis;", ordinal = 1)
    })
    private static IRotate simulated$fromAxisConfig(final IRotate original, final BlockState state,
                                                     @Local(argsOnly = true, ordinal = 0) final KineticBlockEntity from) {
        return simulated$config(original, from);
    }

    @ModifyReceiver(method = "getRotationSpeedModifier", remap = false, at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/base/IRotate;getRotationAxis(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/core/Direction$Axis;", ordinal = 2))
    private static IRotate simulated$toAxisConfig(final IRotate original, final BlockState state,
                                                   @Local(argsOnly = true, ordinal = 1) final KineticBlockEntity to) {
        return simulated$config(original, to);
    }

    @Inject(method = "getPotentialNeighbourLocations", at = @At("TAIL"), remap = false)
    private static void simulated$addExtraPositions(final KineticBlockEntity be,
                                                     final CallbackInfoReturnable<List<BlockPos>> cir) {
        final ListIterator<BlockPos> iterator = cir.getReturnValue().listIterator();
        while (iterator.hasNext()) {
            final BlockPos current = iterator.next();
            if (be.getLevel().getBlockState(current).getBlock() instanceof ExtraKinetics.ExtraKineticsBlock) {
                iterator.add(new ExtraBlockPos(current));
            }
        }
    }

    @Unique
    private static IRotate simulated$config(final IRotate original, final KineticBlockEntity be) {
        if (be.getBlockPos() instanceof ExtraBlockPos
                && be.getBlockState().getBlock() instanceof final ExtraKinetics.ExtraKineticsBlock block) {
            return block.getExtraKineticsRotationConfiguration();
        }
        return original;
    }
}
