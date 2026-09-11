package dev.simulated_team.simulated.content.blocks.steering_wheel;

import dev.simulated_team.simulated.network.SimulatedNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

public final class SteeringWheelClientControl {

    private static BlockPos activePos;
    private static float targetAngle;
    private static float previousYaw;

    private SteeringWheelClientControl() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(SteeringWheelClientControl::clientTick);
        MinecraftForge.EVENT_BUS.addListener(SteeringWheelClientControl::logout);
    }

    public static void begin(final BlockPos pos) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (pos.equals(activePos)) {
            return;
        }
        if (!(minecraft.level.getBlockEntity(pos) instanceof final SteeringWheelBlockEntity wheel)) {
            return;
        }
        stop();
        activePos = pos.immutable();
        targetAngle = wheel.getTargetAngle();
        previousYaw = minecraft.player.getYRot();
        wheel.simulated$setClientTarget(targetAngle, true);
        SimulatedNetwork.sendToServer(new SteeringWheelControlPacket(activePos, targetAngle, false));
    }

    private static void clientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || activePos == null) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !minecraft.options.keyUse.isDown()
                || !(minecraft.level.getBlockEntity(activePos) instanceof final SteeringWheelBlockEntity wheel)) {
            stop();
            return;
        }
        final float yaw = minecraft.player.getYRot();
        final float yawDelta = Mth.wrapDegrees(yaw - previousYaw);
        previousYaw = yaw;
        final float next = Mth.clamp(targetAngle + yawDelta / 10.0F * wheel.directionConvert(1.0F),
                -wheel.getAngleLimit(), wheel.getAngleLimit());
        if (Math.abs(next - targetAngle) <= 0.001F) {
            return;
        }
        targetAngle = next;
        wheel.simulated$setClientTarget(targetAngle, true);
        SimulatedNetwork.sendToServer(new SteeringWheelControlPacket(activePos, targetAngle, false));
    }

    private static void logout(final ClientPlayerNetworkEvent.LoggingOut event) {
        stop();
    }

    private static void stop() {
        if (activePos != null) {
            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null
                    && minecraft.level.getBlockEntity(activePos) instanceof final SteeringWheelBlockEntity wheel) {
                wheel.simulated$setClientTarget(targetAngle, false);
                SimulatedNetwork.sendToServer(new SteeringWheelControlPacket(activePos, targetAngle, true));
            }
        }
        activePos = null;
    }
}
