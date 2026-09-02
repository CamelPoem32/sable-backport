package dev.simulated_team.simulated.content.items.spring;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlock;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity;
import dev.simulated_team.simulated.index.SimulatedBlocks;
import dev.simulated_team.simulated.index.SimulatedConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

public class SpringItem extends Item {

    private static final String FIRST_BLOCK = "M23SpringFirstBlock";
    private static final String FIRST_FACE = "M23SpringFirstFace";

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
        final BlockPos clicked = context.getClickedPos();
        final Direction face = context.getClickedFace();
        final BlockPos placed = clicked.relative(face);

        if (!tag.contains(FIRST_BLOCK) || player.isShiftKeyDown()) {
            if (!canPlaceSpring(level, clicked, placed, face)) {
                send(player, "SABLE_M23_CONSTRAINT phase=ERROR family=spring reason=invalid_first_endpoint");
                clearSelection(tag);
                return InteractionResult.FAIL;
            }
            tag.putLong(FIRST_BLOCK, clicked.asLong());
            tag.putInt(FIRST_FACE, face.ordinal());
            send(player, "SABLE_M23_CONSTRAINT phase=CREATE_REQUEST family=spring endpointA="
                    + clicked.toShortString() + " face=" + face.getName());
            return InteractionResult.SUCCESS;
        }

        final BlockPos firstBlock = BlockPos.of(tag.getLong(FIRST_BLOCK));
        final int firstFaceOrdinal = tag.getInt(FIRST_FACE);
        clearSelection(tag);
        if (firstFaceOrdinal < 0 || firstFaceOrdinal >= Direction.values().length) {
            send(player, "SABLE_M23_CONSTRAINT phase=ERROR family=spring reason=invalid_saved_face");
            return InteractionResult.FAIL;
        }
        final Direction firstFace = Direction.values()[firstFaceOrdinal];

        final BlockPos firstPlaced = firstBlock.relative(firstFace);
        if (firstPlaced.equals(placed)) {
            send(player, "SABLE_M23_CONSTRAINT phase=ERROR family=spring reason=same_endpoint");
            return InteractionResult.FAIL;
        }
        if (!canPlaceSpring(level, firstBlock, firstPlaced, firstFace) || !canPlaceSpring(level, clicked, placed, face)) {
            send(player, "SABLE_M23_CONSTRAINT phase=ERROR family=spring reason=invalid_endpoint");
            return InteractionResult.FAIL;
        }

        final double distanceSquared = Sable.HELPER.distanceSquaredWithSubLevels(level,
                firstPlaced.getCenter(), placed.getCenter());
        final double maxLength = SpringBlockEntity.MAX_LENGTH + 1.0D;
        if (distanceSquared > maxLength * maxLength) {
            send(player, "SABLE_M23_CONSTRAINT phase=ERROR family=spring reason=out_of_range");
            return InteractionResult.FAIL;
        }

        final SpringBlockEntity first = placeSpring(level, firstPlaced, placed, firstFace, true);
        final SpringBlockEntity second = placeSpring(level, placed, firstPlaced, face, false);
        if (first == null || second == null) {
            level.setBlock(firstPlaced, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(placed, Blocks.AIR.defaultBlockState(), 3);
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
        send(player, "SABLE_M23_CONSTRAINT phase=CREATE_SUCCESS family=spring endpointA="
                + firstPlaced.toShortString() + " endpointB=" + placed.toShortString()
                + " restLength=" + String.format("%.2f", desiredLength));
        return InteractionResult.SUCCESS;
    }

    private static void clearSelection(final CompoundTag tag) {
        tag.remove(FIRST_BLOCK);
        tag.remove(FIRST_FACE);
    }

    private static boolean canPlaceSpring(final Level level, final BlockPos support, final BlockPos placed,
                                          final Direction face) {
        return level.getBlockState(placed).canBeReplaced()
                && Block.canSupportCenter(level, support, face);
    }

    private static SpringBlockEntity placeSpring(final Level level, final BlockPos placed, final BlockPos partner,
                                                 final Direction face, final boolean controller) {
        final BlockState state = SimulatedBlocks.SPRING.get().defaultBlockState()
                .setValue(SpringBlock.FACING, face);
        if (!level.setBlock(placed, state, 3)) {
            return null;
        }
        if (level.getBlockEntity(placed) instanceof final SpringBlockEntity spring) {
            final SubLevel partnerSubLevel = Sable.HELPER.getContaining(level, partner);
            spring.setController(controller);
            spring.setPartnerPos(partner, partnerSubLevel == null ? null : partnerSubLevel.getUniqueId());
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
}
