package dev.ryanhcode.sable.mixin.compatibility.jadeaddons;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "snownee.jade.addon.create.CreatePlugin", remap = false)
public class CreatePluginMixin {

    @WrapOperation(method = "lambda$registerClient$1", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",
            remap = true))
	private static Vec3 sable$getEyePosition(final Entity instance, final float f, final Operation<Vec3> original, @Local(argsOnly = true) final Entity e) {
		final ClientSubLevel subLevel = Sable.HELPER.getContaining(e) instanceof final ClientSubLevel clientSubLevel
				? clientSubLevel : null;
		if(subLevel != null) {
			return subLevel.renderPose().transformPositionInverse(original.call(instance, f));
		}
		return original.call(instance, f);
	}

    @WrapOperation(method = "lambda$registerClient$1", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getViewVector(F)Lnet/minecraft/world/phys/Vec3;",
            remap = true))
	private static Vec3 sable$getViewVector(final Entity instance, final float f, final Operation<Vec3> original, @Local(argsOnly = true) final Entity e) {
		final ClientSubLevel subLevel = Sable.HELPER.getContaining(e) instanceof final ClientSubLevel clientSubLevel
				? clientSubLevel : null;
		if(subLevel != null) {
			return subLevel.renderPose().transformNormalInverse(original.call(instance, f));
		}
		return original.call(instance, f);
	}

}
