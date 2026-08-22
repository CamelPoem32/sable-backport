package dev.ryanhcode.sable.mixin.camera.camera_zoom;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private BlockGetter level;
    @Shadow
    private Vec3 position;
    @Shadow
    @Final
    private Vector3f forwards;
    @Shadow
    private Entity entity;
    @Unique
    private boolean sable$pushed = false;

    @Unique
    private double sable$clampZoom(final double maxZoom, final SubLevel ignoredSubLevel) {
        double zoom = maxZoom;

        final float partialTick = Minecraft.getInstance().getFrameTime();

        final Level level = this.entity.level();
        final LevelPoseProviderExtension extension = ((LevelPoseProviderExtension) this.level);
        assert extension != null;

        final Collection<SubLevel> ignoredChain = SubLevelHelper.getConnectedChain(ignoredSubLevel);

        extension.sable$pushPoseSupplier((subLevel) -> ((ClientSubLevel) subLevel).renderPose(partialTick));

        for (int i = 0; i < 8; i++) {
            final float offsetX = (float) ((i & 1) * 2 - 1);
            final float offsetY = (float) ((i >> 1 & 1) * 2 - 1);
            final float offsetZ = (float) ((i >> 2 & 1) * 2 - 1);

            final Vec3 vec3 = this.position.add(offsetX * 0.1F, offsetY * 0.1F, offsetZ * 0.1F);
            final Vec3 vec32 = vec3.add(new Vec3(this.forwards).scale(-zoom));

            final ClipContext clipContext = new ClipContext(vec3, vec32, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this.entity);
            ((ClipContextExtension) clipContext).sable$setSubLevelIgnoring(ignoredChain::contains);
            final HitResult hitResult = this.level.clip(clipContext);

            if (hitResult.getType() != HitResult.Type.MISS) {
                final double distanceSquared = Sable.HELPER.distanceSquaredWithSubLevels(level, hitResult.getLocation(), this.position);
                if (distanceSquared < zoom * zoom) {
                    zoom = Math.sqrt(distanceSquared);
                }
            }
        }

        extension.sable$popPoseSupplier();

        return zoom;
    }

    @Inject(method = "getMaxZoom", at = @At(value = "HEAD"), cancellable = true)
    private void sable$getMaxZoomHead(final double distance, final CallbackInfoReturnable<Double> cir) {
        final Minecraft minecraft = Minecraft.getInstance();

        final LevelPoseProviderExtension extension = ((LevelPoseProviderExtension) minecraft.level);
        assert extension != null;
        extension.sable$pushPoseSupplier((subLevel) -> ((ClientSubLevel) subLevel).renderPose(minecraft.getFrameTime()));
        this.sable$pushed = true;
    }

    @Redirect(method = "getMaxZoom", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"))
    private double sable$getMaxZoom(final Vec3 instance, final Vec3 vec3) {
        return Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels((Level) this.level, instance, vec3));
    }

    @Inject(method = "getMaxZoom", at = @At(value = "RETURN"))
    private void sable$getMaxZoomTail(final double distance, final CallbackInfoReturnable<Double> cir) {
        if (this.sable$pushed) {
            final LevelPoseProviderExtension extension = ((LevelPoseProviderExtension) Minecraft.getInstance().level);
            assert extension != null;
            extension.sable$popPoseSupplier();
            this.sable$pushed = false;
        }
    }

}
