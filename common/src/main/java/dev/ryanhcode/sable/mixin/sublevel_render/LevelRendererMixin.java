package dev.ryanhcode.sable.mixin.sublevel_render;

import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Collections;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Shadow
    @Nullable
    private ClientLevel level;

    @Inject(method = "allChanged", at = @At("TAIL"))
    private void sable$allChanged(final CallbackInfo ci) {
        if (this.level == null) {
            return;
        }

        SubLevelRenderDispatcher.get().rebuild(this.sable$getSubLevels());
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;constantAmbientLight()Z", ordinal = 0, shift = At.Shift.BEFORE))
    private void sable$renderSingleBlockSubLevels(final PoseStack poseStack, final float partialTick, final long finishTimeNano,
                                                  final boolean renderBlockOutline, final Camera camera, final GameRenderer gameRenderer,
                                                  final LightTexture lightTexture, final Matrix4f projection, final CallbackInfo ci) {
        final Iterable<ClientSubLevel> sublevels = this.sable$getSubLevels();
        final Vec3 cameraPosition = camera.getPosition();
        SubLevelRenderDispatcher.get().renderAfterSections(sublevels, cameraPosition.x, cameraPosition.y, cameraPosition.z,
                poseStack.last().pose(), projection, partialTick);
    }

    @Unique
    private Iterable<ClientSubLevel> sable$getSubLevels() {
        if (this.level == null) {
            return Collections.emptyList();
        }

        final ClientSubLevelContainer container = (ClientSubLevelContainer) ((SubLevelContainerHolder) this.level).sable$getPlotContainer();
        return container == null ? Collections.emptyList() : container.getAllSubLevels();
    }

}
