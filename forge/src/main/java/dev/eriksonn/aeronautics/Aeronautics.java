package dev.eriksonn.aeronautics;

import com.mojang.logging.LogUtils;
import dev.eriksonn.aeronautics.index.AeroBlocks;
import dev.eriksonn.aeronautics.index.AeroCreativeTabs;
import dev.eriksonn.aeronautics.index.AeroItems;
import dev.eriksonn.aeronautics.index.AeroPropulsionRegistries;
import dev.eriksonn.aeronautics.network.AeroNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;

public final class Aeronautics {
    public static final String MOD_ID = "aeronautics";
    public static final String MOD_NAME = "Create Aeronautics";
    public static final String BASELINE_COMMIT = "9e60263fb5cb00033f14af655a7e72cf7aebb3e2";
    public static final String BASELINE_LABEL = "Aeronautics 1.3.0 / mc1.21.1-neoforge / Sable 2.0.0";
    public static final String IMPLEMENTATION_REVISION = "M28.11";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static boolean initialized;

    private Aeronautics() {
    }

    public static void init(final IEventBus modBus) {
        AeroBlocks.register(modBus);
        AeroItems.register(modBus);
        AeroPropulsionRegistries.register(modBus);
        AeroCreativeTabs.register(modBus);
        AeroNetwork.init();
        initialized = true;
        LOGGER.info("{} {} bootstrap initialized from {}", MOD_NAME, IMPLEMENTATION_REVISION, BASELINE_COMMIT);
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static ResourceLocation path(final String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
