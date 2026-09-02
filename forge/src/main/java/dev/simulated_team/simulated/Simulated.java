package dev.simulated_team.simulated;

import com.mojang.logging.LogUtils;
import dev.simulated_team.simulated.index.SimulatedBlockEntityTypes;
import dev.simulated_team.simulated.index.SimulatedBlocks;
import dev.simulated_team.simulated.index.SimulatedConfig;
import dev.simulated_team.simulated.index.SimulatedCreativeTabs;
import dev.simulated_team.simulated.index.SimulatedItems;
import dev.simulated_team.simulated.network.SimulatedNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
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

        LOGGER.info("{} M21 bootstrap initialized from {}", MOD_NAME, BASELINE_COMMIT);
    }

    public static ResourceLocation path(final String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
