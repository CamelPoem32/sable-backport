package dev.ryanhcode.sable.network.packets;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPPacketType;
import dev.ryanhcode.sable.util.SableBufferUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;
import java.util.Objects;

public final class ClientboundSableSnapshotDualPacket implements SableUDPPacket, SableTCPPacket {
    public static final SablePacketCodec<ClientboundSableSnapshotDualPacket> CODEC =
            SablePacketCodec.of((buf, value) -> value.encode(buf), ClientboundSableSnapshotDualPacket::new);
    private final int interpolationTick;
    private final List<Entry> entries;

    public ClientboundSableSnapshotDualPacket(final int interpolationTick, final List<Entry> entries) {
        this.interpolationTick = interpolationTick;
        this.entries = entries;
    }


    public record Entry(long plotCoordinate, Pose3d pose, Vector3fc linearVelocity,
                        Vector3fc angularVelocity) {

    }

    public ClientboundSableSnapshotDualPacket(final FriendlyByteBuf buf) {
        this(buf.readInt(), readList(buf));
    }

    private static List<Entry> readList(final FriendlyByteBuf buf) {
        final List<Entry> list = new ObjectArrayList<>();

        final int length = buf.readVarInt();

        for (int i = 0; i < length; i++) {
            list.add(new Entry(buf.readLong(), SableBufferUtils.read(buf, new Pose3d()), SableBufferUtils.read(buf, new Vector3f()), SableBufferUtils.read(buf, new Vector3f())));
        }

        return list;
    }

    public void encode(final FriendlyByteBuf buf) {
        buf.writeInt(this.interpolationTick);
        buf.writeVarInt(this.entries.size());
        for (final Entry entry : this.entries) {
            buf.writeLong(entry.plotCoordinate);
            SableBufferUtils.write(buf, entry.pose);
            SableBufferUtils.write(buf, entry.linearVelocity);
            SableBufferUtils.write(buf, entry.angularVelocity);
        }
    }

    @Override
    public SableUDPPacketType getType() {
        return SableUDPPacketType.SNAPSHOT;
    }

    public int interpolationTick() {
        return this.interpolationTick;
    }

    public List<Entry> entries() {
        return this.entries;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        final var that = (ClientboundSableSnapshotDualPacket) obj;
        return Objects.equals(this.entries, that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.entries);
    }

    @Override
    public String toString() {
        return "ClientboundSableSnapshotDualPacket[" +
                "entries=" + this.entries + ']';
    }

}
