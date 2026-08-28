package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Client intent for applying a Create ValueSettingsPacket to a BlockEntity inside a sub-level.
 * The payload preserves Create's setting data; Sable only bridges moving-sublevel target identity.
 */
public record ServerboundCreateValueSettingsSubLevelPacket(UUID subLevelId, BlockPos localBlockPos,
                                                           int row, int value,
                                                           @Nullable InteractionHand interactHand,
                                                           @Nullable BlockHitResult hitResult,
                                                           Direction side, boolean ctrlDown,
                                                           int behaviourIndex) implements SableTCPPacket {
    public static final SablePacketCodec<ServerboundCreateValueSettingsSubLevelPacket> CODEC =
            SablePacketCodec.of((buf, value) -> value.write(buf), ServerboundCreateValueSettingsSubLevelPacket::read);

    private static ServerboundCreateValueSettingsSubLevelPacket read(final FriendlyByteBuf buf) {
        return new ServerboundCreateValueSettingsSubLevelPacket(
                buf.readUUID(),
                buf.readBlockPos(),
                buf.readVarInt(),
                buf.readVarInt(),
                readNullableHand(buf),
                readNullableHitResult(buf),
                buf.readEnum(Direction.class),
                buf.readBoolean(),
                buf.readVarInt());
    }

    private void write(final FriendlyByteBuf buf) {
        buf.writeUUID(this.subLevelId);
        buf.writeBlockPos(this.localBlockPos);
        buf.writeVarInt(this.row);
        buf.writeVarInt(this.value);
        writeNullableHand(buf, this.interactHand);
        writeNullableHitResult(buf, this.hitResult);
        buf.writeEnum(this.side);
        buf.writeBoolean(this.ctrlDown);
        buf.writeVarInt(this.behaviourIndex);
    }

    private static @Nullable InteractionHand readNullableHand(final FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readEnum(InteractionHand.class) : null;
    }

    private static void writeNullableHand(final FriendlyByteBuf buf, @Nullable final InteractionHand hand) {
        buf.writeBoolean(hand != null);
        if (hand != null) {
            buf.writeEnum(hand);
        }
    }

    private static @Nullable BlockHitResult readNullableHitResult(final FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readBlockHitResult() : null;
    }

    private static void writeNullableHitResult(final FriendlyByteBuf buf, @Nullable final BlockHitResult hitResult) {
        buf.writeBoolean(hitResult != null);
        if (hitResult != null) {
            buf.writeBlockHitResult(hitResult);
        }
    }
}
