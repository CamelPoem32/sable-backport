package dev.simulated_team.simulated.network;

import dev.simulated_team.simulated.Simulated;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class SimulatedNetwork {

    public static final String PROTOCOL_VERSION = "m21-bootstrap";
    public static final ResourceLocation CHANNEL_ID = Simulated.path("main");

    private static SimpleChannel channel;

    private SimulatedNetwork() {
    }

    public static void init() {
        channel = NetworkRegistry.newSimpleChannel(
                CHANNEL_ID,
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals);
    }

    public static boolean isReady() {
        return channel != null;
    }
}
