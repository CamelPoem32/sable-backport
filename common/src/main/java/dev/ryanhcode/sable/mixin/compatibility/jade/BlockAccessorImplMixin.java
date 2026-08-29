package dev.ryanhcode.sable.mixin.compatibility.jade;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "snownee.jade.impl.BlockAccessorImpl", remap = false)
public class BlockAccessorImplMixin {

    @WrapOperation(method = "lambda$handleRequest$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D",
            remap = true))
    private static double sable$distSqr(final BlockPos instance, final Vec3i vec3i, final Operation<Double> original, @Local final ServerLevel world) {
        if (Sable.HELPER.getContaining(world, instance) == null
                && Sable.HELPER.getContaining(world, vec3i) == null) {
            return original.call(instance, vec3i);
        }
        return Sable.HELPER.distanceSquaredWithSubLevels(world, instance.getX(), instance.getY(), instance.getZ(), vec3i.getX() + 0.5, vec3i.getY() + 0.5, vec3i.getZ() + 0.5);
    }
}
