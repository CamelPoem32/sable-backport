package dev.simulated_team.simulated.content.blocks.steering_wheel;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.network.client.ClientSubLevelHoldUseGuard;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.network.SimulatedNetwork;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

public final class SteeringWheelClientControl {

    private static UUID activeSableId;
    private static BlockPos activeLocalPos;
    private static InteractionHand activeHand;
    private static ResourceKey<Level> activeDimension;
    private static long activeSessionToken;
    private static long nextSessionToken = 1L;
    private static float targetAngle;
    private static float previousYaw;
    private static boolean suppressUntilRelease;

    private SteeringWheelClientControl() {
    }

    public static void register() {
        ClientSubLevelHoldUseGuard.register(SteeringWheelClientControl::isActive);
        MinecraftForge.EVENT_BUS.addListener(SteeringWheelClientControl::clientTick);
        MinecraftForge.EVENT_BUS.addListener(SteeringWheelClientControl::logout);
    }

    public static boolean isActive() {
        return activeLocalPos != null || suppressUntilRelease;
    }

    public static void begin(final BlockPos rawPos, final InteractionHand hand) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (!(minecraft.level.getBlockEntity(rawPos) instanceof final SteeringWheelBlockEntity wheel)) {
            return;
        }
        final SubLevel owner = Sable.HELPER.getContaining(minecraft.level, rawPos);
        final UUID sableId = owner == null ? null : owner.getUniqueId();
        final BlockPos localPos = owner == null ? rawPos.immutable()
                : rawPos.subtract(owner.getPlot().getCenterBlock()).immutable();
        if (localPos.equals(activeLocalPos) && java.util.Objects.equals(sableId, activeSableId)) {
            return;
        }
        stop(true);
        activeSableId = sableId;
        activeLocalPos = localPos;
        activeHand = hand;
        activeDimension = minecraft.level.dimension();
        activeSessionToken = nextSessionToken++;
        suppressUntilRelease = false;
        targetAngle = wheel.getTargetAngle();
        previousYaw = minecraft.player.getYRot();
        wheel.simulated$setClientTarget(targetAngle, true);
        send(false);
    }

    private static void clientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (activeLocalPos == null) {
            if (suppressUntilRelease && !minecraft.options.keyUse.isDown()) {
                suppressUntilRelease = false;
            }
            return;
        }
        if (!minecraft.options.keyUse.isDown()) {
            stop(false);
            return;
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || !java.util.Objects.equals(minecraft.level.dimension(), activeDimension)) {
            stop(true);
            return;
        }
        final SteeringWheelBlockEntity wheel = resolveCapturedWheel(minecraft);
        if (wheel == null) {
            stop(true);
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
        send(false);
    }

    private static void logout(final ClientPlayerNetworkEvent.LoggingOut event) {
        stop(false);
    }

    private static void stop(final boolean consumeUntilRelease) {
        if (activeLocalPos != null) {
            final Minecraft minecraft = Minecraft.getInstance();
            final SteeringWheelBlockEntity wheel = resolveCapturedWheel(minecraft);
            if (wheel != null) {
                wheel.simulated$setClientTarget(targetAngle, false);
            }
            send(true);
        }
        activeSableId = null;
        activeLocalPos = null;
        activeHand = null;
        activeDimension = null;
        activeSessionToken = 0L;
        suppressUntilRelease = consumeUntilRelease;
    }

    private static void send(final boolean stop) {
        SimulatedNetwork.sendToServer(new SteeringWheelControlPacket(
                activeSableId, activeLocalPos, activeHand, activeSessionToken, targetAngle, stop));
    }

    private static SteeringWheelBlockEntity resolveCapturedWheel(final Minecraft minecraft) {
        if (minecraft.level == null || activeLocalPos == null) {
            return null;
        }
        final BlockPos rawPos;
        if (activeSableId == null) {
            rawPos = activeLocalPos;
        } else {
            final var container = SubLevelContainer.getContainer(minecraft.level);
            final SubLevel owner = container == null ? null : container.getSubLevel(activeSableId);
            if (owner == null) {
                return null;
            }
            rawPos = owner.getPlot().getCenterBlock().offset(activeLocalPos);
        }
        return minecraft.level.getBlockEntity(rawPos) instanceof final SteeringWheelBlockEntity wheel ? wheel : null;
    }
}
