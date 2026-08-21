package dev.ryanhcode.sable.forge;

import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.SableClientConfig;
import dev.ryanhcode.sable.physics.config.FloatingBlockMaterialDataHandler;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

final class SableForgeClient {

    private SableForgeClient() {
    }

    static void init(final IEventBus modBus) {
        SableClient.init();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SableClientConfig.SPEC);

        modBus.<ModConfigEvent.Loading>addListener(event -> SableClientConfig.onUpdate(false));
        modBus.<ModConfigEvent.Reloading>addListener(event -> SableClientConfig.onUpdate(true));
        modBus.<RegisterClientReloadListenersEvent>addListener(event ->
                event.registerReloadListener(SubLevelRenderDispatcher.get()));
        MinecraftForge.EVENT_BUS.<ClientPlayerNetworkEvent.LoggingOut>addListener(event -> {
            if (event.getPlayer() != null) {
                FloatingBlockMaterialDataHandler.clearMaterials();
            }
        });
    }
}
