package dev.eriksonn.aeronautics.network;

import dev.eriksonn.aeronautics.Aeronautics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class AeroNetwork {
    public static final String PROTOCOL_VERSION = "m25-bootstrap";
    public static final ResourceLocation CHANNEL_ID = Aeronautics.path("main");

    private static SimpleChannel channel;

    private AeroNetwork() {
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

    public static int packetCount() {
        return 0;
    }
}
