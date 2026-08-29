package dev.ryanhcode.sable.mixin.compatibility.jade;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Pseudo;

import java.util.Optional;

@Pseudo
@Mixin(targets = "snownee.jade.overlay.RayTracing", remap = false)
public class RayTracingMixin {

    @WrapOperation(method = "getEntityHitResult", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/AABB;clip(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;",
            remap = true))
	private static Optional<Vec3> sable$clip(final AABB aabb, Vec3 start, Vec3 end, final Operation<Optional<Vec3>> original, @Local(argsOnly = true) final Level worldIn) {
		final ClientSubLevel subLevel = Sable.HELPER.getContaining(worldIn, aabb.getCenter()) instanceof final ClientSubLevel clientSubLevel
				? clientSubLevel : null;
		if(subLevel != null) {
            final Pose3dc renderPose = subLevel.renderPose();
            start = renderPose.transformPositionInverse(start);
			end = renderPose.transformPositionInverse(end);
			return aabb.clip(start, end);
		}

		return original.call(aabb, start, end);
	}

    @WrapOperation(method = "getEntityHitResult", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D",
            remap = true))
	private static double sable$distanceToSqr(final Vec3 instance, final Vec3 vec3, final Operation<Double> original, @Local(argsOnly = true) final Level worldIn) {
		if (Sable.HELPER.getContaining(worldIn, vec3) == null) {
			return original.call(instance, vec3);
		}
		return original.call(instance, Sable.HELPER.projectOutOfSubLevel(worldIn, vec3));
	}

}
