package dev.ryanhcode.sable;

import dev.ryanhcode.sable.network.client.SableClientNetworkEventLoop;
import net.minecraft.client.Minecraft;

public class SableClient {

    public static SableClientNetworkEventLoop NETWORK_EVENT_LOOP = new SableClientNetworkEventLoop();

    public static void init() {
    }

    public static boolean useNativeTransport() {
        final Minecraft client = Minecraft.getInstance();
        return client.options.useNativeTransport();
    }
}
