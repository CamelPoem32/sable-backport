package dev.ryanhcode.sable.network.udp;


import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageEncoder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;

import java.util.List;

public class SableUDPPacketEncoder extends MessageToMessageEncoder<AddressedSableUDPPacket> {

    private final PacketFlow flow;

    public SableUDPPacketEncoder(final PacketFlow flow) {
        this.flow = flow;
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx, final AddressedSableUDPPacket envelope, final List<Object> out) throws Exception {
        final SableUDPPacket msg = envelope.packet();
        final SableUDPPacketType packetType = msg.getType();

        try {
            if (packetType.flow() != this.flow) {
                throw new EncoderException("Cannot encode " + packetType + " on " + this.flow + " UDP flow");
            }
            final ByteBuf buf = ctx.alloc().ioBuffer();
            buf.writeByte(packetType.ordinal());
            packetType.write(new FriendlyByteBuf(buf), msg);

//            out.add(new DefaultAddressedEnvelope<ByteBuf, SocketAddress>(buf, envelope.address()));
            out.add(new DatagramPacket(buf, envelope.address()));
        } catch (final Exception e) {
            throw new EncoderException("Failed to encode %s packet of type %s".formatted(msg.getClass().getSimpleName(), packetType), e);
        }
    }

}
