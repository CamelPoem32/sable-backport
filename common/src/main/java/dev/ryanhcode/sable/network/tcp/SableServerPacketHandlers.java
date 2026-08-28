package dev.ryanhcode.sable.network.tcp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.SubLevelCreateValueSettingsHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelInteractionHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.index.SableAttributes;
import dev.ryanhcode.sable.network.packets.tcp.ServerboundCreateValueSettingsSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ServerboundPunchSubLevelPacket;
import dev.ryanhcode.sable.network.packets.tcp.ServerboundUseItemOnSubLevelPacket;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.Objects;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class SableServerPacketHandlers {

    private SableServerPacketHandlers() {
    }

    public static void handlePunch(final ServerboundPunchSubLevelPacket packet,
                                   final SablePacketContext context) {
        final ServerLevel level = (ServerLevel) context.level();
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            Sable.LOGGER.error("Received a sub-level punch packet for a level without a sub-level container");
            return;
        }

        final Player player = context.player();
        if (!player.onGround() && !player.isInWater() && !player.getAbilities().flying && !player.onClimbable()) {
            return;
        }

        final ServerSubLevel standingSubLevel = (ServerSubLevel) Sable.HELPER.getTrackingSubLevel(player);
        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final SubLevel targetSubLevel = Sable.HELPER.getContaining(level, packet.punchedBlock());
        if (standingSubLevel == targetSubLevel) {
            return;
        }

        final Vector3d localHitPosition = new Vector3d(packet.localPosition());
        final Vector3d globalDirection = new Vector3d(packet.direction()).normalize();
        if (targetSubLevel != null) {
            localHitPosition.add(targetSubLevel.logicalPose().position());
            targetSubLevel.logicalPose().transformPositionInverse(localHitPosition);
        }
        if (standingSubLevel != null) {
            standingSubLevel.logicalPose().transformNormal(globalDirection);
        }

        final double attributeStrength = Objects.requireNonNull(player.getAttribute(SableAttributes.PUNCH_STRENGTH)).getValue();
        final int customCooldown = SableAttributes.getPushCooldownTicks(player);
        if (!physicsSystem.tryPunch(player.getGameProfile().getId(), customCooldown)) {
            return;
        }
        player.getCooldowns().addCooldown(player.getMainHandItem().getItem(), customCooldown);

        if (globalDirection.y < 0.0) {
            globalDirection.mul(1.0, SableConfig.SUB_LEVEL_PUNCH_DOWNWARD_STRENGTH_MULTIPLIER.getAsDouble(), 1.0);
        }
        if (!(targetSubLevel instanceof final ServerSubLevel punchedSubLevel)) {
            if (standingSubLevel != null) {
                final Pose3d pose = standingSubLevel.logicalPose();
                final Vector3d localPosition = pose.transformPositionInverse(JOMLConversion.toJOML(player.position()));
                final Vector3d localDirection = pose.transformNormalInverse(globalDirection);
                localDirection.negate();
                final double strengthScalar = computeStrengthScalar(standingSubLevel, localPosition, localDirection);
                physicsSystem.getPipeline().applyImpulse(
                        standingSubLevel, localPosition,
                        localDirection.mul(attributeStrength * strengthScalar, new Vector3d()));
            }
        } else {
            final double strengthScalar;
            final Vector3d localHitDirection = punchedSubLevel.logicalPose()
                    .transformNormalInverse(globalDirection, new Vector3d());
            if (standingSubLevel == null) {
                strengthScalar = computeStrengthScalar(punchedSubLevel, localHitPosition, localHitDirection);
            } else {
                final Vector3d localPosition = standingSubLevel.logicalPose()
                        .transformPositionInverse(JOMLConversion.toJOML(player.position()));
                final Vector3d localDirection = standingSubLevel.logicalPose()
                        .transformNormalInverse(new Vector3d(globalDirection));
                final double standingStrength = computeStrengthScalar(standingSubLevel, localPosition, localDirection);
                final double punchedStrength = computeStrengthScalar(punchedSubLevel, localHitPosition, localHitDirection);
                strengthScalar = Math.min(punchedStrength, standingStrength);
                localDirection.negate();
                physicsSystem.getPipeline().applyImpulse(
                        standingSubLevel, localPosition, localDirection.mul(attributeStrength * strengthScalar));
            }
            physicsSystem.getPipeline().applyImpulse(
                    punchedSubLevel, localHitPosition, localHitDirection.mul(attributeStrength * strengthScalar));
        }

        final BlockState blockState = level.getBlockState(packet.punchedBlock());
        if (blockState.getFluidState().isEmpty()) {
            final Vector3d particlePos = new Vector3d(localHitPosition).fma(-0.1, globalDirection);
            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, blockState),
                    particlePos.x(), particlePos.y(), particlePos.z(), (int) (Math.random() * 3.0),
                    0.0, 0.0, 0.0, 0.0);
        } else {
            sendFluidParticles(packet, level, blockState, globalDirection);
        }
    }

    public static void handleUseItemOnSubLevel(final ServerboundUseItemOnSubLevelPacket packet,
                                               final SablePacketContext context) {
        if (!(context.level() instanceof final ServerLevel level) || !(context.player() instanceof final ServerPlayer player)) {
            Sable.LOGGER.error("SABLE_M11_INTERACT_SERVER rejected=wrong_context sublevel={}", packet.subLevelId());
            return;
        }

        SubLevelInteractionHelper.handleUseItemOnSubLevel(level, player, packet);
    }

    public static void handleCreateValueSettingsOnSubLevel(final ServerboundCreateValueSettingsSubLevelPacket packet,
                                                           final SablePacketContext context) {
        if (!(context.level() instanceof final ServerLevel level) || !(context.player() instanceof final ServerPlayer player)) {
            Sable.LOGGER.error("SABLE_M11_VALUE_SERVER rejected=wrong_context sublevel={}", packet.subLevelId());
            return;
        }

        SubLevelCreateValueSettingsHelper.handleValueSettingsOnSubLevel(level, player, packet);
    }

    private static void sendFluidParticles(final ServerboundPunchSubLevelPacket packet,
                                           final ServerLevel level,
                                           final BlockState blockState,
                                           final Vector3dc transformedDirection) {
        if (blockState.getFluidState().is(FluidTags.WATER)) {
            final Vector3d particlePos = new Vector3d(packet.localPosition()).fma(0.1, transformedDirection);
            level.sendParticles(ParticleTypes.SPLASH, particlePos.x(), particlePos.y(), particlePos.z(),
                    10, 0.2, 0.2, 0.2, 0.0);
            particlePos.fma(0.2, transformedDirection);
            level.sendParticles(ParticleTypes.BUBBLE, particlePos.x(), particlePos.y(), particlePos.z(),
                    5, 0.2, 0.1, 0.2, 0.0);
            level.playSound(null, particlePos.x(), particlePos.y(), particlePos.z(),
                    SoundEvents.PLAYER_SWIM, SoundSource.BLOCKS, 0.2F, 1.0F);
        } else {
            final Vector3d particlePos = new Vector3d(packet.localPosition()).fma(0.1, transformedDirection);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockState),
                    particlePos.x(), particlePos.y(), particlePos.z(), (int) (Math.random() * 3.0),
                    0.2, 0.2, 0.2, 0.0);
        }
    }

    private static double computeStrengthScalar(final ServerSubLevel subLevel,
                                                final Vector3dc localPosition,
                                                final Vector3dc localDirection) {
        final MassData massTracker = subLevel.getMassTracker();
        final double mass = 1.0 / massTracker.getInverseNormalMass(localPosition, localDirection);
        return ServerboundPunchSubLevelPacket.punchCurve(mass)
                * SableConfig.SUB_LEVEL_PUNCH_STRENGTH_MULTIPLIER.getAsDouble();
    }
}
