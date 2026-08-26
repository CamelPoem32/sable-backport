package dev.ryanhcode.sable.forge;

import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;

final class SableForgeClientRenderEvents {

    private static final RenderType BASIC_SINGLE_BLOCK_LAYER = RenderType.solid();

    private SableForgeClientRenderEvents() {
    }

    static void register() {
        MinecraftForge.EVENT_BUS.<RenderLevelStageEvent>addListener(SableForgeClientRenderEvents::onRenderLevelStage);
    }

    private static void onRenderLevelStage(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        final ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }

        final Vec3 cameraPosition = event.getCamera().getPosition();
        SubLevelRenderDispatcher.get().renderBasicSingleBlockLayer(
                container.getAllSubLevels(),
                BASIC_SINGLE_BLOCK_LAYER,
                cameraPosition.x,
                cameraPosition.y,
                cameraPosition.z,
                event.getPoseStack().last().pose(),
                event.getProjectionMatrix(),
                event.getPartialTick());
    }
}
