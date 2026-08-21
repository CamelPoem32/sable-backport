package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.util.SableBufferUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Sends hit position relative to center of mass of target sublevel, and target angle relative to sublevel player is tracking
 *
 * @param punchedBlock blockpos that is being punched. used to get target sublevel
 * @param localPosition position relative to center of mass of hit sublevel in global space
 * @param direction direction in world space (or plot space of tracking sublevel)
 */
public record ServerboundPunchSubLevelPacket(BlockPos punchedBlock, Vector3dc localPosition,
                                             Vector3dc direction) implements SableTCPPacket {
    public static final SablePacketCodec<ServerboundPunchSubLevelPacket> CODEC =
            SablePacketCodec.of((buf, value) -> value.write(buf), ServerboundPunchSubLevelPacket::read);

    private static ServerboundPunchSubLevelPacket read(final FriendlyByteBuf buf) {
        return new ServerboundPunchSubLevelPacket(
                buf.readBlockPos(), SableBufferUtils.read(buf, new Vector3d()), SableBufferUtils.read(buf, new Vector3d()));
    }

    private void write(final FriendlyByteBuf buf) {
        buf.writeBlockPos(this.punchedBlock);
        SableBufferUtils.write(buf, this.localPosition);
        SableBufferUtils.write(buf, this.direction);
    }

    /**
     * <a href="https://www.desmos.com/calculator/ziovwc5a2v">Tuning Desmos page</a>
     * @author Eriksonn
     */
    public static double punchCurve(final double x) {
        // falloff scale when x >= 1
        final double S = 2;

        // falloff exponent when x >= 1
        final double E = 0.5;

        // slope at mass = 1
        final double k = 0.8;

        // velocity impulse at zero mass
        final double p = 1.75;

        final double u = x - 1;
        final double g = k / (S * E);

        if (x < 1) {
            return (((p + k - 2) * u + k - 1) * u + 1) * x;
        } else {
            final double inverseE = 1 / (E - 1);
            return S * (Math.pow(u + Math.pow(g, inverseE), E) - Math.pow(g, E * inverseE)) + 1;
        }
    }

}
