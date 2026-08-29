package dev.ryanhcode.sable.forge;

import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSubLevelBlockEntityRenderer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;

import java.util.SortedSet;

final class SableForgeClientRenderEvents {

    private static final RenderType BASIC_SINGLE_BLOCK_LAYER = RenderType.solid();
    private static final Long2ObjectMap<SortedSet<BlockDestructionProgress>> NO_DESTRUCTION_PROGRESS = Long2ObjectMaps.emptyMap();

    private SableForgeClientRenderEvents() {
    }

    static void register() {
        MinecraftForge.EVENT_BUS.<RenderLevelStageEvent>addListener(SableForgeClientRenderEvents::onRenderLevelStage);
    }

    private static void onRenderLevelStage(final RenderLevelStageEvent event) {
        final boolean renderBasicBlocks = event.getStage() == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS;
        final boolean renderBlockEntities = event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        if (!renderBasicBlocks && !renderBlockEntities) {
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
        if (renderBasicBlocks) {
            SubLevelRenderDispatcher.get().renderBasicSingleBlockLayer(
                    container.getAllSubLevels(),
                    BASIC_SINGLE_BLOCK_LAYER,
                    cameraPosition.x,
                    cameraPosition.y,
                    cameraPosition.z,
                    event.getPoseStack().last().pose(),
                    event.getProjectionMatrix(),
                    event.getPartialTick());
            return;
        }

        final VanillaSubLevelBlockEntityRenderer blockEntityRenderer = new VanillaSubLevelBlockEntityRenderer(
                minecraft.getBlockEntityRenderDispatcher(),
                minecraft.renderBuffers(),
                NO_DESTRUCTION_PROGRESS);
        SubLevelRenderDispatcher.get().renderBlockEntities(
                container.getAllSubLevels(),
                blockEntityRenderer,
                cameraPosition.x,
                cameraPosition.y,
                cameraPosition.z,
                event.getPoseStack().last().pose(),
                event.getPartialTick());

        if (ModList.get().isLoaded("create")) {
            SableForgeCreateContraptionRenderBridge.render(event, level, cameraPosition);
        }
    }
}
