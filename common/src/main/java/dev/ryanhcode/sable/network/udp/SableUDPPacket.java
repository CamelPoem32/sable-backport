package dev.ryanhcode.sable.network.udp;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.flow.FlowControlHandler;
import net.minecraft.network.Varint21FrameDecoder;
import net.minecraft.network.Varint21LengthFieldPrepender;
import net.minecraft.network.protocol.PacketFlow;

public interface SableUDPPacket {

    static void configureSerialization(final ChannelPipeline pipeline, final PacketFlow flow, final boolean memoryOnly) {
        if (!memoryOnly) {
            pipeline.addLast("splitter", new Varint21FrameDecoder());
        }
        pipeline.addLast(new FlowControlHandler())
                .addLast("decoder", new SableUDPPacketDecoder(flow));
        if (!memoryOnly) {
            pipeline.addLast("prepender", new Varint21LengthFieldPrepender());
        }
        pipeline.addLast("encoder", new SableUDPPacketEncoder(flow.getOpposite()));
    }

    static void configureInMemoryPipeline(final ChannelPipeline channelPipeline, final PacketFlow arg) {
        configureSerialization(channelPipeline, arg, true);
    }

    SableUDPPacketType getType();
}
