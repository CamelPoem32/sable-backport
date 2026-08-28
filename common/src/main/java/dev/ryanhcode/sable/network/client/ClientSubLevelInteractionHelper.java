package dev.ryanhcode.sable.network.client;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.mixin.punching.ItemInvoker;
import dev.ryanhcode.sable.network.packets.tcp.ServerboundUseItemOnSubLevelPacket;
import dev.ryanhcode.sable.network.tcp.SableTCPPackets;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;

/** Client-side right-click bridge for blocks hit inside Sable sub-levels. */
public final class ClientSubLevelInteractionHelper {
    private ClientSubLevelInteractionHelper() {
    }

    public static @Nullable InteractionResult tryUseOnCurrentTarget(final LocalPlayer player,
                                                                    final Level level,
                                                                    final InteractionHand hand,
                                                                    @Nullable final HitResult currentHit) {
        if (currentHit instanceof final BlockHitResult blockHitResult
                && isSubLevelHit(level, blockHitResult)) {
            return null;
        }

        if (currentHit != null && currentHit.getType() == HitResult.Type.ENTITY) {
            return null;
        }

        final BlockHitResult povHitResult = ItemInvoker.sable$getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        final InteractionResult result = sendIfSubLevelHit(player, level, hand, povHitResult, currentHit, "sable");
        if (result == null) {
            logClientAttempt(player, hand, currentHit, false, null, null, null, null, null, "vanilla", InteractionResult.PASS);
        }
        return result;
    }

    public static void refreshHeldUseTarget(final LocalPlayer player,
                                            final Level level,
                                            @Nullable final HitResult currentHit) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.keyUse.isDown()) {
            return;
        }
        if (currentHit instanceof final BlockHitResult blockHitResult && isSubLevelHit(level, blockHitResult)) {
            return;
        }

        final BlockHitResult povHitResult = ItemInvoker.sable$getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (isSubLevelHit(level, povHitResult)) {
            minecraft.hitResult = povHitResult;
        }
    }

    public static @Nullable InteractionResult tryUseOnSubLevel(final LocalPlayer player,
                                                               final Level level,
                                                               final InteractionHand hand,
                                                               final BlockHitResult hitResult) {
        return sendIfSubLevelHit(player, level, hand, hitResult, hitResult, "sable");
    }

    private static @Nullable InteractionResult sendIfSubLevelHit(final LocalPlayer player,
                                                                 final Level level,
                                                                 final InteractionHand hand,
                                                                 final BlockHitResult hitResult,
                                                                 @Nullable final HitResult vanillaHit,
                                                                 final String packetPath) {
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        final SubLevel subLevel = Sable.HELPER.getContaining(level, hitResult.getBlockPos());
        if (!(subLevel instanceof final ClientSubLevel clientSubLevel)) {
            return null;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        minecraft.hitResult = hitResult;

        final BlockPos center = clientSubLevel.getPlot().getCenterBlock();
        final BlockPos localBlockPos = hitResult.getBlockPos().subtract(center);
        final Vector3d localHit = JOMLConversion.toJOML(hitResult.getLocation())
                .sub(center.getX(), center.getY(), center.getZ());
        final Direction localFace = hitResult.getDirection();
        final ClientValueSettingsDiagnostics valueDiagnostics = inspectCreateValueSettings(level, hitResult);
        final InteractionResult result = runClientUsePath(player, level, hand, hitResult);

        SableTCPPackets.sendToServer(new ServerboundUseItemOnSubLevelPacket(
                clientSubLevel.getUniqueId(),
                localBlockPos.immutable(),
                localHit,
                localFace,
                hand));

        final Vec3 visibleWorldHit = clientSubLevel.renderPose().transformPosition(hitResult.getLocation());
        logClientAttempt(player, hand, vanillaHit, true, clientSubLevel, localBlockPos, localHit, localFace,
                visibleWorldHit, packetPath, result);
        logWrenchClient(player, hand, localBlockPos, result);
        logMotorClient(player, localBlockPos, valueDiagnostics);
        return result;
    }

    private static boolean isSubLevelHit(final Level level, final BlockHitResult hitResult) {
        return hitResult.getType() == HitResult.Type.BLOCK
                && Sable.HELPER.getContaining(level, hitResult.getBlockPos()) instanceof ClientSubLevel;
    }

    private static InteractionResult runClientUsePath(final LocalPlayer player,
                                                      final Level level,
                                                      final InteractionHand hand,
                                                      final BlockHitResult hitResult) {
        final BlockPos blockPos = hitResult.getBlockPos();
        final ItemStack stack = player.getItemInHand(hand);
        final PlayerInteractEvent.RightClickBlock event = ForgeHooks.onRightClickBlock(player, hand, blockPos, hitResult);
        if (event.isCanceled()) {
            return event.getCancellationResult();
        }

        final UseOnContext context = new UseOnContext(player, hand, hitResult);
        if (event.getUseItem() != Event.Result.DENY) {
            final InteractionResult result = stack.onItemUseFirst(context);
            if (result != InteractionResult.PASS) {
                return result;
            }
        }

        final boolean bypass = player.getMainHandItem().doesSneakBypassUse(level, blockPos, player)
                && player.getOffhandItem().doesSneakBypassUse(level, blockPos, player);
        final boolean secondaryUseActive = player.isSecondaryUseActive() && !bypass;
        final BlockState blockState = level.getBlockState(blockPos);
        if (event.getUseBlock() == Event.Result.ALLOW
                || event.getUseBlock() != Event.Result.DENY && !secondaryUseActive) {
            final InteractionResult result = blockState.use(level, player, hand, hitResult);
            if (result.consumesAction()) {
                return result;
            }
        }

        if (event.getUseItem() == Event.Result.DENY) {
            return InteractionResult.PASS;
        }
        if (event.getUseItem() == Event.Result.ALLOW
                || !stack.isEmpty() && !player.getCooldowns().isOnCooldown(stack.getItem())) {
            final int count = stack.getCount();
            final InteractionResult result = stack.useOn(context);
            if (minecraftGameModeCreative()) {
                stack.setCount(count);
            }
            return result;
        }
        return InteractionResult.PASS;
    }

    private static boolean minecraftGameModeCreative() {
        final Minecraft minecraft = Minecraft.getInstance();
        return minecraft.gameMode != null && minecraft.gameMode.getPlayerMode().isCreative();
    }

    private static void logClientAttempt(final LocalPlayer player,
                                         final InteractionHand hand,
                                         @Nullable final HitResult vanillaHit,
                                         final boolean sableHit,
                                         @Nullable final ClientSubLevel subLevel,
                                         @Nullable final BlockPos localBlockPos,
                                         @Nullable final Vector3d localHit,
                                         @Nullable final Direction localFace,
                                         @Nullable final Vec3 visibleWorldHit,
                                         final String packetPath,
                                         final InteractionResult result) {
        final Minecraft minecraft = Minecraft.getInstance();
        Sable.LOGGER.info("SABLE_M11_INTERACT_CLIENT vanillaHitType={} sableHit={} sublevel={} worldHit={} localBlockPos={} localHit={} localFace={} packetPath={} item={} hand={} result={}",
                vanillaHit == null ? (minecraft.hitResult == null ? "null" : minecraft.hitResult.getType()) : vanillaHit.getType(),
                sableHit,
                subLevel == null ? "none" : subLevel.getUniqueId(),
                visibleWorldHit == null ? "none" : visibleWorldHit,
                localBlockPos == null ? "none" : localBlockPos,
                localHit == null ? "none" : localHit,
                localFace,
                packetPath,
                player.getItemInHand(hand).getItem(),
                hand,
                result);
    }

    private static void logWrenchClient(final LocalPlayer player,
                                        final InteractionHand hand,
                                        final BlockPos localBlockPos,
                                        final InteractionResult result) {
        Sable.LOGGER.info("SABLE_M11_WRENCH_CLIENT sableHit=true item={} hand={} localBlockPos={} normalClientPathReached=true swingTriggered={} result={}",
                player.getItemInHand(hand).getItem(),
                hand,
                localBlockPos,
                result.shouldSwing(),
                result);
    }

    private static void logMotorClient(final LocalPlayer player,
                                       final BlockPos localBlockPos,
                                       final ClientValueSettingsDiagnostics diagnostics) {
        if (!diagnostics.createBehaviorFound()) {
            return;
        }

        Sable.LOGGER.info("SABLE_M11_MOTOR_CLIENT sableHit=true localBlockPos={} clientBEClass={} createBehaviorFound={} rmbHeld={} holdTicks={} uiPath={} uiOpened={}",
                localBlockPos,
                diagnostics.blockEntityClass(),
                true,
                Minecraft.getInstance().options.keyUse.isDown(),
                diagnostics.holdTicks(),
                "ForgeHooks.onRightClickBlock->Create_ValueSettingsInputHandler",
                diagnostics.uiOpened());
    }

    private static ClientValueSettingsDiagnostics inspectCreateValueSettings(final Level level,
                                                                            final BlockHitResult hitResult) {
        final Object blockEntity = level.getBlockEntity(hitResult.getBlockPos());
        if (blockEntity == null) {
            return ClientValueSettingsDiagnostics.none();
        }

        boolean found = false;
        try {
            final Method getAllBehaviours = blockEntity.getClass().getMethod("getAllBehaviours");
            if (getAllBehaviours.invoke(blockEntity) instanceof final Collection<?> behaviours) {
                for (final Object behaviour : behaviours) {
                    if (isCreateValueSettingsBehaviour(behaviour)) {
                        found = true;
                        break;
                    }
                }
            }
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            found = false;
        }

        return new ClientValueSettingsDiagnostics(
                blockEntity.getClass().getName(),
                found,
                readCreateValueSettingsHoldTicks(),
                isCreateValueSettingsScreenOpen());
    }

    private static boolean isCreateValueSettingsBehaviour(final Object behaviour) {
        for (Class<?> type = behaviour.getClass(); type != null; type = type.getSuperclass()) {
            for (final Class<?> iface : type.getInterfaces()) {
                if (isCreateValueSettingsInterface(iface)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isCreateValueSettingsInterface(final Class<?> type) {
        if ("com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour".equals(type.getName())) {
            return true;
        }
        for (final Class<?> iface : type.getInterfaces()) {
            if (isCreateValueSettingsInterface(iface)) {
                return true;
            }
        }
        return false;
    }

    private static int readCreateValueSettingsHoldTicks() {
        try {
            final Class<?> createClient = Class.forName("com.simibubi.create.CreateClient");
            final Field handlerField = createClient.getField("VALUE_SETTINGS_HANDLER");
            final Object handler = handlerField.get(null);
            final Field ticksField = handler.getClass().getField("interactHeldTicks");
            return ticksField.getInt(handler);
        } catch (ReflectiveOperationException ignored) {
            return -1;
        }
    }

    private static boolean isCreateValueSettingsScreenOpen() {
        final Minecraft minecraft = Minecraft.getInstance();
        return minecraft.screen != null
                && minecraft.screen.getClass().getName().toLowerCase(Locale.ROOT).contains("valuesettingsscreen");
    }

    private record ClientValueSettingsDiagnostics(String blockEntityClass,
                                                  boolean createBehaviorFound,
                                                  int holdTicks,
                                                  boolean uiOpened) {
        private static ClientValueSettingsDiagnostics none() {
            return new ClientValueSettingsDiagnostics("none", false, -1, false);
        }
    }
}
