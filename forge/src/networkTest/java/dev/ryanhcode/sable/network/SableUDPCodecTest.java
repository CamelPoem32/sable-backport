package dev.ryanhcode.sable.network;

import dev.ryanhcode.sable.network.packets.udp.SableUDPAuthenticationPacket;
import dev.ryanhcode.sable.network.packets.udp.SableUDPServerboundAlivePacket;
import dev.ryanhcode.sable.network.udp.AddressedSableUDPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPPacketDecoder;
import dev.ryanhcode.sable.network.udp.SableUDPPacketEncoder;
import dev.ryanhcode.sable.network.udp.SableUDPPacketType;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.net.InetSocketAddress;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SableUDPCodecTest {

    private static final InetSocketAddress LOCAL = new InetSocketAddress("127.0.0.1", 25565);
    private static final InetSocketAddress REMOTE = new InetSocketAddress("127.0.0.1", 25566);

    @Test
    void encoderAndDecoderPreserveAddressAndPayload() {
        final SableUDPAuthenticationPacket packet = new SableUDPAuthenticationPacket(
                "12345678-1234-5678-9abc-def012345678");
        final EmbeddedChannel encoder = new EmbeddedChannel(new SableUDPPacketEncoder(PacketFlow.SERVERBOUND));
        assertTrue(encoder.writeOutbound(new AddressedSableUDPPacket(packet, REMOTE)));
        final DatagramPacket datagram = encoder.readOutbound();
        assertEquals(REMOTE, datagram.recipient());
        assertEquals(SableUDPPacketType.AUTH.ordinal(), datagram.content().getUnsignedByte(0));

        final EmbeddedChannel decoder = new EmbeddedChannel(new SableUDPPacketDecoder(PacketFlow.SERVERBOUND));
        final DatagramPacket inbound = new DatagramPacket(datagram.content().retain(), LOCAL, REMOTE);
        assertTrue(decoder.writeInbound(inbound));
        final AddressedSableUDPPacket decoded = decoder.readInbound();
        assertEquals(REMOTE, decoded.address());
        assertEquals(packet, decoded.packet());
        datagram.release();
        encoder.finishAndReleaseAll();
        decoder.finishAndReleaseAll();
    }

    @Test
    void rejectsInvalidIdWrongDirectionTrailingBytesAndMalformedPayload() {
        assertRejected(PacketFlow.CLIENTBOUND, buffer(99));

        final FriendlyByteBuf wrongDirection = buffer(SableUDPPacketType.AUTH.ordinal());
        wrongDirection.writeUtf("12345678-1234-5678-9abc-def012345678");
        assertRejected(PacketFlow.CLIENTBOUND, wrongDirection);

        final FriendlyByteBuf trailing = buffer(SableUDPPacketType.ALIVE_SERVERBOUND.ordinal());
        trailing.writeByte(1);
        assertRejected(PacketFlow.SERVERBOUND, trailing);

        final FriendlyByteBuf malformed = buffer(SableUDPPacketType.AUTH.ordinal());
        malformed.writeVarInt(16);
        malformed.writeByte(1);
        assertRejected(PacketFlow.SERVERBOUND, malformed);

        final FriendlyByteBuf invalidUuid = buffer(SableUDPPacketType.AUTH.ordinal());
        invalidUuid.writeUtf("not-a-uuid");
        assertRejected(PacketFlow.SERVERBOUND, invalidUuid);
    }

    @Test
    void encoderRejectsWrongDirection() {
        final EmbeddedChannel encoder = new EmbeddedChannel(new SableUDPPacketEncoder(PacketFlow.CLIENTBOUND));
        final AddressedSableUDPPacket packet = new AddressedSableUDPPacket(
                new SableUDPServerboundAlivePacket(), REMOTE);
        final Throwable thrown = assertThrows(Throwable.class, () -> encoder.writeOutbound(packet));
        assertInstanceOf(EncoderException.class, rootCodecException(thrown));
        encoder.finishAndReleaseAll();
    }

    private static FriendlyByteBuf buffer(final int packetId) {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeByte(packetId);
        return buffer;
    }

    private static void assertRejected(final PacketFlow flow, final FriendlyByteBuf buffer) {
        final EmbeddedChannel decoder = new EmbeddedChannel(new SableUDPPacketDecoder(flow));
        final DatagramPacket datagram = new DatagramPacket(buffer, LOCAL, REMOTE);
        final Throwable thrown = assertThrows(Throwable.class, () -> decoder.writeInbound(datagram));
        assertInstanceOf(DecoderException.class, rootCodecException(thrown));
        decoder.finishAndReleaseAll();
    }

    private static Throwable rootCodecException(final Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null
                && !(current instanceof DecoderException)
                && !(current instanceof EncoderException)) {
            current = current.getCause();
        }
        return current;
    }
}
