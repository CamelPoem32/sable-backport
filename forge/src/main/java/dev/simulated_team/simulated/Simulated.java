package dev.simulated_team.simulated;

import com.mojang.logging.LogUtils;
import dev.simulated_team.simulated.index.SimulatedBlockEntityTypes;
import dev.simulated_team.simulated.index.SimulatedBlocks;
import dev.simulated_team.simulated.index.SimulatedConfig;
import dev.simulated_team.simulated.index.SimulatedCreativeTabs;
import dev.simulated_team.simulated.index.SimulatedItems;
import dev.simulated_team.simulated.index.SimulatedKineticStress;
import dev.simulated_team.simulated.network.SimulatedNetwork;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableNestedBearingOwnershipTransfer;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableM28NormalWorldCceSync;
import dev.ryanhcode.sable.util.SableM29EntityQueryTrace;
import dev.ryanhcode.sable.forge.SableM28RestoredContraptionClientSync;
import dev.ryanhcode.sable.forge.SableM29SailVisualLifecycle;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

public final class Simulated {

    public static final String MOD_ID = "simulated";
    public static final String MOD_NAME = "Create Simulated";
    public static final String BASELINE_COMMIT = "9e60263fb5cb00033f14af655a7e72cf7aebb3e2";
    public static final String BASELINE_LABEL = "Simulated 1.3.0 / mc1.21.1-neoforge / Sable 2.0.0";

    public static final Logger LOGGER = LogUtils.getLogger();

    private Simulated() {
    }

    public static void init(final IEventBus modBus) {
        SimulatedBlocks.register(modBus);
        SimulatedItems.register(modBus);
        SimulatedBlockEntityTypes.register(modBus);
        SimulatedCreativeTabs.register(modBus);
        SimulatedNetwork.init();
        SimulatedConfig.init();
        modBus.addListener(Simulated::commonSetup);
        MinecraftForge.EVENT_BUS.addListener(Simulated::serverTick);
        MinecraftForge.EVENT_BUS.addListener(Simulated::entityJoined);
        MinecraftForge.EVENT_BUS.addListener(Simulated::entityLeft);
        MinecraftForge.EVENT_BUS.addListener(Simulated::startTracking);
        MinecraftForge.EVENT_BUS.addListener(Simulated::levelUnloaded);

        LOGGER.info("{} M21 bootstrap initialized from {}", MOD_NAME, BASELINE_COMMIT);
    }

    private static void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(SimulatedKineticStress::register);
    }

    private static void serverTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        event.getServer().getAllLevels().forEach(SableNestedBearingOwnershipTransfer::verifyPending);
    }

    private static void entityJoined(final EntityJoinLevelEvent event) {
        SableM28NormalWorldCceSync.entityJoined(event.getEntity(), event.getLevel());
        SableM29SailVisualLifecycle.entityJoined(event.getEntity(), event.getLevel());
    }

    private static void entityLeft(final EntityLeaveLevelEvent event) {
        SableM28NormalWorldCceSync.entityLeft(event.getEntity(), event.getLevel());
        SableM28RestoredContraptionClientSync.entityLeft(event.getEntity(), event.getLevel());
        SableM29SailVisualLifecycle.entityLeft(event.getEntity(), event.getLevel());
    }

    private static void startTracking(final PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof final net.minecraft.server.level.ServerPlayer player) {
            SableM28NormalWorldCceSync.startTracking(player, event.getTarget());
        }
    }

    private static void levelUnloaded(final LevelEvent.Unload event) {
        if (event.getLevel() instanceof final net.minecraft.world.level.Level level) {
            SableM28RestoredContraptionClientSync.levelUnloaded(level);
            SableM29SailVisualLifecycle.levelUnloaded(level);
        }
        SableM29EntityQueryTrace.summaryAndReset("LEVEL_UNLOAD",
                event.getLevel().getClass().getName() + '@'
                        + System.identityHashCode(event.getLevel()));
    }

    public static ResourceLocation path(final String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
