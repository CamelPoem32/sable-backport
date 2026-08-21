package dev.ryanhcode.sable.network.udp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;

import java.io.IOException;
import java.util.List;

public class SableUDPPacketDecoder extends MessageToMessageDecoder<DatagramPacket> {

    private final PacketFlow flow;

    public SableUDPPacketDecoder(final PacketFlow flow) {
        super(DatagramPacket.class);
        this.flow = flow;
    }

    /**
     * Decode from one message to an other. This method will be called for each written message that can be handled
     * by this decoder.
     *
     * @param ctx the {@link ChannelHandlerContext} which this {@link MessageToMessageDecoder} belongs to
     * @param msg the message to decode to an other one
     * @param out the {@link List} to which decoded messages should be added
     * @throws Exception is thrown if an error occurs
     */
    @Override
    protected void decode(final ChannelHandlerContext ctx, final DatagramPacket msg, final List<Object> out) throws Exception {
        final ByteBuf byteBuf = msg.content();
        final int i = byteBuf.readableBytes();
        if (i != 0) {
            final short packetID = byteBuf.readUnsignedByte();

            if (packetID >= SableUDPPacketType.VALUES.length) {
                throw new IOException("Received an invalid packet ID: " + packetID);
            }

            final SableUDPPacketType packetType = SableUDPPacketType.VALUES[packetID];
            if (packetType.flow() != this.flow) {
                throw new IOException("Received " + packetType + " on " + this.flow + " UDP flow");
            }
            final SableUDPPacket packet;
            try {
                packet = packetType.create(new FriendlyByteBuf(byteBuf));
            } catch (final Exception e) {
                throw new DecoderException("Failed to decode UDP packet of type " + packetType, e);
            }

            if (byteBuf.readableBytes() > 0) {
                throw new DecoderException("Sable UDP packet " + packetType + " has "
                        + byteBuf.readableBytes() + " trailing bytes");
            }

            out.add(new AddressedSableUDPPacket(packet, msg.sender()));
        }
    }
}
