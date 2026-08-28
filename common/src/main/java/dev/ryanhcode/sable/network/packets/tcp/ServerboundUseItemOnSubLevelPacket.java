package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.util.SableBufferUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

/**
 * Client intent for using the held item on a block inside a sub-level.
 * The server resolves the UUID and current pose authoritatively before invoking normal item use.
 */
public record ServerboundUseItemOnSubLevelPacket(UUID subLevelId, BlockPos localBlockPos,
                                                 Vector3dc localHit, Direction localFace,
                                                 InteractionHand hand) implements SableTCPPacket {
    public static final SablePacketCodec<ServerboundUseItemOnSubLevelPacket> CODEC =
            SablePacketCodec.of((buf, value) -> value.write(buf), ServerboundUseItemOnSubLevelPacket::read);

    private static ServerboundUseItemOnSubLevelPacket read(final FriendlyByteBuf buf) {
        return new ServerboundUseItemOnSubLevelPacket(
                buf.readUUID(),
                buf.readBlockPos(),
                SableBufferUtils.read(buf, new Vector3d()),
                buf.readEnum(Direction.class),
                buf.readEnum(InteractionHand.class));
    }

    private void write(final FriendlyByteBuf buf) {
        buf.writeUUID(this.subLevelId);
        buf.writeBlockPos(this.localBlockPos);
        SableBufferUtils.write(buf, this.localHit);
        buf.writeEnum(this.localFace);
        buf.writeEnum(this.hand);
    }
}
