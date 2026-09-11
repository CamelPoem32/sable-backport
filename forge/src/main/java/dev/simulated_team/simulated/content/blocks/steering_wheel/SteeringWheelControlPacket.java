package dev.simulated_team.simulated.content.blocks.steering_wheel;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

public record SteeringWheelControlPacket(BlockPos pos, float targetAngle, boolean stop) {

    public static void encode(final SteeringWheelControlPacket packet, final FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeFloat(packet.targetAngle);
        buffer.writeBoolean(packet.stop);
    }

    public static SteeringWheelControlPacket decode(final FriendlyByteBuf buffer) {
        return new SteeringWheelControlPacket(buffer.readBlockPos(), buffer.readFloat(), buffer.readBoolean());
    }

    public static void handle(final SteeringWheelControlPacket packet,
                              final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            final ServerPlayer player = context.getSender();
            if (player == null || !Float.isFinite(packet.targetAngle)
                    || !(player.level().getBlockEntity(packet.pos) instanceof final SteeringWheelBlockEntity wheel)) {
                return;
            }
            final SubLevel owner = Sable.HELPER.getContaining(player.level(), packet.pos);
            final Vec3 visibleCenter = owner == null ? Vec3.atCenterOf(packet.pos)
                    : owner.logicalPose().transformPosition(Vec3.atCenterOf(packet.pos));
            if (player.getEyePosition().distanceToSqr(visibleCenter) > 64.0D) {
                return;
            }
            wheel.acceptControl(player, packet.targetAngle, packet.stop);
        });
        context.setPacketHandled(true);
    }
}
