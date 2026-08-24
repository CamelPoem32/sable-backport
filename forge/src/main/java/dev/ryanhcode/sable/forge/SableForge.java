package dev.ryanhcode.sable.forge;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableCommonEvents;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.command.SableCommand;
import dev.ryanhcode.sable.command.argument.SubLevelSelectorModifiers;
import dev.ryanhcode.sable.index.SableAttributes;
import dev.ryanhcode.sable.network.tcp.SableTCPPackets;
import dev.ryanhcode.sable.physics.config.FloatingBlockMaterialDataHandler;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinitionLoader;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.CrashReportCallables;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(Sable.MOD_ID)
public final class SableForge {

    public SableForge() {
        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        Sable.init();
        SableForgeRuntimeSmoke.installCommon(modBus);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommand);
        MinecraftForge.EVENT_BUS.addListener(this::registerReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(this::syncDataPack);
        modBus.addListener(SableAttributes::register);
        modBus.addListener(this::commonSetup);

        SubLevelSelectorModifiers.registerModifiers();

        final DeferredRegister<Attribute> attributes = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, Sable.MOD_ID);
        attributes.register(SableAttributes.PUNCH_STRENGTH_NAME, () -> SableAttributes.PUNCH_STRENGTH);
        attributes.register(SableAttributes.PUNCH_COOLDOWN_NAME, () -> SableAttributes.PUNCH_COOLDOWN);
        attributes.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SableConfig.SPEC);
        CrashReportCallables.registerCrashCallable("Sable", Sable::getCrashHeader);

        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> SableForgeClient::init);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SableForgeRuntimeSmoke.commonSetup();
        });
    }

    private void registerReloadListeners(final AddReloadListenerEvent event) {
        event.addListener(PhysicsBlockPropertiesDefinitionLoader.INSTANCE);
        event.addListener(DimensionPhysicsData.ReloadListener.INSTANCE);
        event.addListener(FloatingBlockMaterialDataHandler.ReloadListener.INSTANCE);
        SableForgeRuntimeSmoke.reloadListenersRegistered();
    }

    private void registerCommand(final RegisterCommandsEvent event) {
        SableCommand.register(event.getDispatcher(), event.getBuildContext());
        SableForgeRuntimeSmoke.commandRegistered();
    }

    private void syncDataPack(final OnDatapackSyncEvent event) {
        SableForgeRuntimeSmoke.dataPackSync(event.getPlayers().size());
        event.getPlayers().forEach(player ->
                SableCommonEvents.syncDataPacket(SableTCPPackets.player(player)));
    }
}
