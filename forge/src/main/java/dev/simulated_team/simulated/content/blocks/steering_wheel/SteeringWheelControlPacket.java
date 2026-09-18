package dev.simulated_team.simulated.content.blocks.steering_wheel;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

public record SteeringWheelControlPacket(@Nullable UUID sableId, BlockPos localPos, InteractionHand hand,
                                         long sessionToken, float targetAngle, boolean stop) {

    public static void encode(final SteeringWheelControlPacket packet, final FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.sableId != null);
        if (packet.sableId != null) {
            buffer.writeUUID(packet.sableId);
        }
        buffer.writeBlockPos(packet.localPos);
        buffer.writeEnum(packet.hand);
        buffer.writeLong(packet.sessionToken);
        buffer.writeFloat(packet.targetAngle);
        buffer.writeBoolean(packet.stop);
    }

    public static SteeringWheelControlPacket decode(final FriendlyByteBuf buffer) {
        final UUID sableId = buffer.readBoolean() ? buffer.readUUID() : null;
        return new SteeringWheelControlPacket(sableId, buffer.readBlockPos(), buffer.readEnum(InteractionHand.class),
                buffer.readLong(), buffer.readFloat(), buffer.readBoolean());
    }

    public static void handle(final SteeringWheelControlPacket packet,
                              final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            final ServerPlayer player = context.getSender();
            if (player == null || !Float.isFinite(packet.targetAngle) || packet.sessionToken == 0L) {
                return;
            }
            final SubLevel owner;
            final BlockPos rawPos;
            if (packet.sableId == null) {
                owner = null;
                rawPos = packet.localPos;
            } else {
                final var container = SubLevelContainer.getContainer(player.serverLevel());
                owner = container == null ? null : container.getSubLevel(packet.sableId);
                if (owner == null || owner.isRemoved()) {
                    return;
                }
                rawPos = owner.getPlot().getCenterBlock().offset(packet.localPos);
            }
            if (Sable.HELPER.getContaining(player.level(), rawPos) != owner
                    || !(player.level().getBlockEntity(rawPos) instanceof final SteeringWheelBlockEntity wheel)) {
                return;
            }
            final Vec3 visibleCenter = owner == null ? Vec3.atCenterOf(rawPos)
                    : owner.logicalPose().transformPosition(Vec3.atCenterOf(rawPos));
            if (player.getEyePosition().distanceToSqr(visibleCenter) > 64.0D) {
                return;
            }
            wheel.acceptControl(player, packet.sableId, packet.localPos, packet.hand, packet.sessionToken,
                    packet.targetAngle, packet.stop);
        });
        context.setPacketHandled(true);
    }
}
