package dev.simulated_team.simulated.content.items.spring;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlock;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity;
import dev.simulated_team.simulated.index.SimulatedBlocks;
import dev.simulated_team.simulated.index.SimulatedConfig;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

public class SpringItem extends Item {

    private static final String FIRST_BLOCK = "M23SpringFirstBlock";
    private static final String FIRST_FACE = "M23SpringFirstFace";
    private static final String FIRST_SUBLEVEL = "M23SpringFirstSubLevel";

    public SpringItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final Player player = context.getPlayer();
        final Level level = context.getLevel();
        if (player == null || level.isClientSide || !SimulatedConfig.ENABLE_M23_SPRING_CONSTRAINTS.get()) {
            return InteractionResult.SUCCESS;
        }

        final ItemStack stack = context.getItemInHand();
        final CompoundTag tag = stack.getOrCreateTag();
        final Target target = resolveTarget(context);
        if (target == null) {
            send(player, "SABLE_M23_CONSTRAINT phase=ERROR family=spring reason=invalid_target");
            clearSelection(tag);
            return InteractionResult.FAIL;
        }
        logTarget(player, context, target);

        if (!tag.contains(FIRST_BLOCK) || player.isShiftKeyDown()) {
            if (!canPlaceSpring(level, target.supportPos(), target.placedPos(), target.face())) {
                send(player, "SABLE_M23_CONSTRAINT phase=ERROR family=spring reason=invalid_first_endpoint");
                clearSelection(tag);
                return InteractionResult.FAIL;
            }
            tag.putLong(FIRST_BLOCK, target.supportPos().asLong());
            tag.putInt(FIRST_FACE, target.face().ordinal());
            if (target.subLevelId() != null) {
                tag.putUUID(FIRST_SUBLEVEL, target.subLevelId());
            }
            send(player, "SABLE_M23_CONSTRAINT phase=CREATE_REQUEST family=spring endpointA="
                    + target.supportPos().toShortString() + " face=" + target.face().getName()
                    + " targetType=" + target.targetType()
                    + " targetSableId=" + (target.subLevelId() == null ? "static_world" : target.subLevelId())
                    + " selectedEndpointPos=" + target.placedPos().toShortString());
            return InteractionResult.SUCCESS;
        }

        final BlockPos firstBlock = BlockPos.of(tag.getLong(FIRST_BLOCK));
        final int firstFaceOrdinal = tag.getInt(FIRST_FACE);
        final UUID firstSubLevel = tag.hasUUID(FIRST_SUBLEVEL) ? tag.getUUID(FIRST_SUBLEVEL) : null;
        clearSelection(tag);
        if (firstFaceOrdinal < 0 || firstFaceOrdinal >= Direction.values().length) {
            send(player, "SABLE_M23_CONSTRAINT phase=ERROR family=spring reason=invalid_saved_face");
            return InteractionResult.FAIL;
        }
        final Direction firstFace = Direction.values()[firstFaceOrdinal];

        final BlockPos firstPlaced = firstBlock.relative(firstFace);
        if (firstPlaced.equals(target.placedPos())) {
            send(player, "SABLE_M23_CONSTRAINT phase=ERROR family=spring reason=same_endpoint");
            return InteractionResult.FAIL;
        }
        if (!canPlaceSpring(level, firstBlock, firstPlaced, firstFace)
                || !canPlaceSpring(level, target.supportPos(), target.placedPos(), target.face())) {
            send(player, "SABLE_M23_CONSTRAINT phase=ERROR family=spring reason=invalid_endpoint");
            return InteractionResult.FAIL;
        }

        final double distanceSquared = Sable.HELPER.distanceSquaredWithSubLevels(level,
                firstPlaced.getCenter(), target.placedPos().getCenter());
        final double maxLength = SpringBlockEntity.MAX_LENGTH + 1.0D;
        if (distanceSquared > maxLength * maxLength) {
            send(player, "SABLE_M23_CONSTRAINT phase=ERROR family=spring reason=out_of_range");
            return InteractionResult.FAIL;
        }

        final SpringBlockEntity first = placeSpring(level, firstPlaced, target.placedPos(),
                target.subLevelId(), firstFace, true);
        final SpringBlockEntity second = placeSpring(level, target.placedPos(), firstPlaced,
                firstSubLevel, target.face(), false);
        if (first == null || second == null) {
            level.setBlock(firstPlaced, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(target.placedPos(), Blocks.AIR.defaultBlockState(), 3);
            send(player, "SABLE_M23_CONSTRAINT phase=ERROR family=spring reason=placement_failed");
            return InteractionResult.FAIL;
        }

        final double desiredLength = clamp(Math.sqrt(distanceSquared) + 1.0D, 1.0D, SpringBlockEntity.MAX_LENGTH);
        first.setDesiredLength(desiredLength);
        second.setDesiredLength(desiredLength);
        first.sendData();
        second.sendData();

        player.awardStat(Stats.ITEM_USED.get(this));
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        final SubLevel firstOwner = Sable.HELPER.getContaining(level, firstPlaced);
        final SubLevel secondOwner = Sable.HELPER.getContaining(level, target.placedPos());
        final boolean sableToSable = firstOwner instanceof ServerSubLevel && secondOwner instanceof ServerSubLevel;
        final boolean sameBody = firstOwner != null && firstOwner == secondOwner;
        send(player, "SABLE_M23_CONSTRAINT phase=CREATE_SUCCESS family=spring endpointA="
                + firstPlaced.toShortString() + " endpointB=" + target.placedPos().toShortString()
                + " restLength=" + String.format("%.2f", desiredLength)
                + " bodyA=" + (firstOwner == null ? "static_world" : firstOwner.getUniqueId())
                + " bodyB=" + (secondOwner == null ? "static_world" : secondOwner.getUniqueId())
                + " sameBody=" + sameBody
                + " constraintMode=" + (sableToSable ? "SABLE_TO_SABLE" : "STATIC_WORLD"));
        logConnectedBodySnapshot(level, firstPlaced, "after_spring_connection");
        logConnectedBodySnapshot(level, target.placedPos(), "after_spring_connection");
        return InteractionResult.SUCCESS;
    }

    private static void clearSelection(final CompoundTag tag) {
        tag.remove(FIRST_BLOCK);
        tag.remove(FIRST_FACE);
        tag.remove(FIRST_SUBLEVEL);
    }

    private static boolean canPlaceSpring(final Level level, final BlockPos support, final BlockPos placed,
                                          final Direction face) {
        return level.getBlockState(placed).canBeReplaced()
                && Block.canSupportCenter(level, support, face);
    }

    private static SpringBlockEntity placeSpring(final Level level, final BlockPos placed, final BlockPos partner,
                                                 @Nullable final UUID partnerSubLevelId, final Direction face,
                                                 final boolean controller) {
        final BlockState state = SimulatedBlocks.SPRING.get().defaultBlockState()
                .setValue(SpringBlock.FACING, face);
        if (!level.setBlock(placed, state, 3)) {
            return null;
        }
        if (level.getBlockEntity(placed) instanceof final SpringBlockEntity spring) {
            final SubLevel partnerSubLevel = Sable.HELPER.getContaining(level, partner);
            spring.setController(controller);
            spring.setPartnerPos(partner, partnerSubLevelId != null
                    ? partnerSubLevelId
                    : partnerSubLevel == null ? null : partnerSubLevel.getUniqueId());
            return spring;
        }
        return null;
    }

    private static double clamp(final double value, final double min, final double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void send(final Player player, final String message) {
        player.displayClientMessage(Component.literal(message), false);
    }

    private static @Nullable Target resolveTarget(final UseOnContext context) {
        final Level level = context.getLevel();
        final BlockPos clicked = context.getClickedPos().immutable();
        final Direction face = context.getClickedFace();
        final SubLevel direct = Sable.HELPER.getContaining(level, clicked);
        if (direct instanceof final ServerSubLevel serverSubLevel) {
            return new Target(clicked, clicked.relative(face), face, serverSubLevel.getUniqueId(),
                    "SABLE_RAW", localPos(serverSubLevel, clicked), clicked,
                    visibleBlockPos(level, clicked));
        }

        final Target visible = resolveVisibleSubLevelTarget(context);
        if (visible != null) {
            return visible;
        }
        return new Target(clicked, clicked.relative(face), face, null, "STATIC_WORLD", clicked,
                clicked, clicked);
    }

    private static @Nullable Target resolveVisibleSubLevelTarget(final UseOnContext context) {
        if (!(context.getLevel() instanceof final ServerLevel serverLevel)) {
            return null;
        }
        final BlockPos clicked = context.getClickedPos().immutable();
        final Vec3 clickedCenter = clicked.getCenter();
        final Direction visibleFace = context.getClickedFace();
        final Iterable<SubLevel> candidates = Sable.HELPER.getAllIntersecting(serverLevel,
                new BoundingBox3d(clicked).expand(1.0D));
        for (final SubLevel candidate : candidates) {
            if (!(candidate instanceof final ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
                continue;
            }
            final Vector3d rawCenter = serverSubLevel.logicalPose().transformPositionInverse(
                    new Vector3d(clickedCenter.x, clickedCenter.y, clickedCenter.z));
            final BlockPos rawSupport = BlockPos.containing(rawCenter.x, rawCenter.y, rawCenter.z);
            if (Sable.HELPER.getContaining(serverLevel, rawSupport) != serverSubLevel
                    || serverLevel.getBlockState(rawSupport).isAir()) {
                continue;
            }
            final Direction rawFace = transformFaceToSubLevel(serverSubLevel, visibleFace);
            final BlockPos rawPlaced = rawSupport.relative(rawFace);
            if (canPlaceSpring(serverLevel, rawSupport, rawPlaced, rawFace)) {
                return new Target(rawSupport, rawPlaced, rawFace, serverSubLevel.getUniqueId(),
                        "SABLE_VISIBLE_REPROJECTED", localPos(serverSubLevel, rawSupport),
                        rawSupport, clicked);
            }
        }
        return null;
    }

    private static Direction transformFaceToSubLevel(final ServerSubLevel subLevel, final Direction face) {
        final Vector3d normal = JOMLConversion.atLowerCornerOf(face.getNormal());
        subLevel.logicalPose().transformNormalInverse(normal);
        return Direction.getNearest(normal.x, normal.y, normal.z);
    }

    private static BlockPos localPos(final ServerSubLevel subLevel, final BlockPos rawPos) {
        return rawPos.subtract(subLevel.getPlot().getCenterBlock());
    }

    private static BlockPos visibleBlockPos(final Level level, final BlockPos rawPos) {
        final Vec3 visible = Sable.HELPER.projectOutOfSubLevel(level, rawPos.getCenter());
        return BlockPos.containing(visible);
    }

    private static void logTarget(final Player player, final UseOnContext context, final Target target) {
        final String interactionLevel = Sable.HELPER.getContaining(context.getLevel(), context.getClickedPos()) == null
                ? "PARENT_WORLD"
                : "SABLE_RAW";
        send(player, "SABLE_M23_SPRING_TARGET visibleHit=" + context.getClickLocation()
                + " targetType=" + target.targetType()
                + " targetSableId=" + (target.subLevelId() == null ? "static_world" : target.subLevelId())
                + " targetLocalPos=" + target.localPos().toShortString()
                + " targetRawPos=" + target.rawSupportPos().toShortString()
                + " parentWorldPos=" + target.parentWorldPos().toShortString()
                + " interactionLevel=" + interactionLevel
                + " placementLevel=" + (target.subLevelId() == null ? "PARENT_WORLD" : "SABLE_RAW")
                + " selectedEndpointPos=" + target.placedPos().toShortString());
    }

    private static void logConnectedBodySnapshot(final Level level, final BlockPos pos, final String phase) {
        if (level instanceof final ServerLevel serverLevel
                && Sable.HELPER.getContaining(level, pos) instanceof final dev.ryanhcode.sable.sublevel.ServerSubLevel subLevel) {
            SimAssemblyHelper.logBodySnapshot(serverLevel, subLevel, phase, 0);
        }
    }

    private record Target(BlockPos supportPos, BlockPos placedPos, Direction face,
                          @Nullable UUID subLevelId, String targetType, BlockPos localPos,
                          BlockPos rawSupportPos, BlockPos parentWorldPos) {
    }
}
