package dev.simulated_team.simulated.network;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelControlPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class SimulatedNetwork {

    public static final String PROTOCOL_VERSION = "m28-steering-wheel";
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
        channel.messageBuilder(SteeringWheelControlPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SteeringWheelControlPacket::encode)
                .decoder(SteeringWheelControlPacket::decode)
                .consumerNetworkThread(SteeringWheelControlPacket::handle)
                .add();
    }

    public static boolean isReady() {
        return channel != null;
    }

    public static void sendToServer(final SteeringWheelControlPacket packet) {
        if (channel != null) {
            channel.sendToServer(packet);
        }
    }

    public static int packetCount() {
        return 1;
    }
}
