package dev.ryanhcode.sable.compatibility.create.deployer;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/** Diagnostic only: native Create owns extraction and held-item bookkeeping. */
public final class SableDeployerMountedStorageTrace {
    private static final Map<MovementContext, String> LAST_REJECTION =
            Collections.synchronizedMap(new WeakHashMap<>());

    private SableDeployerMountedStorageTrace() {
    }

    public static @Nullable RefillSample capture(final MovementContext context) {
        if (!SableDiagnosticFlags.TRACE_CREATE_ACTORS || context.world.isClientSide || context.contraption == null) {
            return null;
        }
        final ItemStack held = held(context);
        if (!held.isEmpty()) {
            return null;
        }
        final var blocks = context.contraption.getBlocks();
        final var storages = context.contraption.getStorage().getAllItemStorages();
        final IItemHandler allItems = context.contraption.getStorage().getAllItems();
        final FilterItemStack filter = context.getFilterFromBE();
        int chestCount = 0;
        int chestBlockEntityCount = 0;
        BlockPos firstChestLocal = null;
        for (final var entry : blocks.entrySet()) {
            if (!(entry.getValue().state().getBlock() instanceof ChestBlock)) {
                continue;
            }
            chestCount++;
            if (firstChestLocal == null) {
                firstChestLocal = entry.getKey();
            }
            if (entry.getValue().nbt() != null) {
                chestBlockEntityCount++;
            }
        }
        int candidateCount = 0;
        int matchingCount = 0;
        for (int slot = 0; slot < allItems.getSlots(); slot++) {
            final ItemStack stack = allItems.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            candidateCount += stack.getCount();
            if (filter.test(context.world, stack)) {
                matchingCount += stack.getCount();
            }
        }
        return new RefillSample(chestCount, chestBlockEntityCount, firstChestLocal, storages.size(),
                allItems.getSlots(), candidateCount, matchingCount, item(filter.item()),
                System.identityHashCode(context.contraption.getStorage()), held.copy());
    }

    public static void completed(final MovementContext context, final SubLevel owner,
                                 @Nullable final RefillSample before) {
        if (before == null) {
            return;
        }
        final ItemStack after = held(context);
        final String decision = DeployerRefillDecision.classify(before.capturedChestCount(),
                before.mountedStorageCount(), before.matchingItemCount(), !after.isEmpty());
        if (after.isEmpty()) {
            final String key = decision + ":" + before.capturedChestCount() + ":"
                    + before.mountedStorageCount() + ":" + before.matchingItemCount();
            final String previous = LAST_REJECTION.put(context, key);
            if (key.equals(previous)) {
                return;
            }
        } else {
            LAST_REJECTION.remove(context);
        }
        final String event = after.isEmpty() ? "DEPLOYER_REFILL_REJECTED" : "DEPLOYER_REFILL_SUCCESS";
        final StringBuilder mountedPositions = new StringBuilder("[");
        final Iterator<BlockPos> positions = context.contraption.getStorage().getAllItemStorages().keySet().iterator();
        for (int index = 0; index < 4 && positions.hasNext(); index++) {
            if (index > 0) {
                mountedPositions.append(',');
            }
            mountedPositions.append(positions.next());
        }
        if (positions.hasNext()) {
            mountedPositions.append(",...");
        }
        mountedPositions.append(']');
        Sable.LOGGER.info("SABLE_M31_CREATE_ACTOR event=DEPLOYER_REFILL_CHECK side=SERVER gameTime={} "
                        + "cceUuid={} actorLocal={} heldBefore={} decision=NATIVE_CREATE_REFILL",
                context.world.getGameTime(),
                context.contraption.entity == null ? "none" : context.contraption.entity.getUUID(),
                context.localPos, item(before.heldBefore()));
        Sable.LOGGER.info("SABLE_M31_CREATE_ACTOR event=DEPLOYER_STORAGE_STATE side=SERVER gameTime={} "
                        + "cceUuid={} capturedChestCount={} capturedChestBECount={} "
                        + "mountedStorageCount={} mountedPositions={} candidateItemCount={} matchingItemCount={}",
                context.world.getGameTime(),
                context.contraption.entity == null ? "none" : context.contraption.entity.getUUID(),
                before.capturedChestCount(), before.capturedChestBlockEntityCount(),
                before.mountedStorageCount(), mountedPositions, before.candidateItemCount(), before.matchingItemCount());
        if (before.matchingItemCount() > 0) {
            Sable.LOGGER.info("SABLE_M31_CREATE_ACTOR event=DEPLOYER_REFILL_ATTEMPT side=SERVER "
                            + "gameTime={} cceUuid={} actorLocal={} matchingItemCount={} filter={}",
                    context.world.getGameTime(),
                    context.contraption.entity == null ? "none" : context.contraption.entity.getUUID(),
                    context.localPos, before.matchingItemCount(), before.filterItem());
        }
        Sable.LOGGER.info("SABLE_M31_CREATE_ACTOR event={} side=SERVER gameTime={} actorType=DEPLOYER "
                        + "subLevel={} cceUuid={} actorLocal={} contraptionClass={} contraptionIdentity={} "
                        + "storageManagerIdentity={} capturedBlockCount={} capturedChestCount={} "
                        + "capturedChestBECount={} firstChestLocal={} mountedStorageCount={} mountedPositions={} "
                        + "combinedSlots={} candidateItemCount={} matchingItemCount={} filter={} "
                        + "heldBefore={} heldAfter={} extractedCount={} decision={}",
                event, context.world.getGameTime(), owner.getUniqueId(),
                context.contraption.entity == null ? "none" : context.contraption.entity.getUUID(),
                context.localPos, context.contraption.getClass().getName(),
                System.identityHashCode(context.contraption), before.storageManagerIdentity(),
                context.contraption.getBlocks().size(), before.capturedChestCount(),
                before.capturedChestBlockEntityCount(), before.firstChestLocal(),
                before.mountedStorageCount(), mountedPositions,
                before.combinedSlotCount(), before.candidateItemCount(), before.matchingItemCount(),
                before.filterItem(), item(before.heldBefore()), item(after), after.getCount(), decision);
    }

    private static ItemStack held(final MovementContext context) {
        if (context.temporaryData instanceof final DeployerFakePlayer player) {
            return player.getMainHandItem();
        }
        return ItemStack.of(context.data.getCompound("HeldItem"));
    }

    private static String item(final ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount();
    }

    public record RefillSample(int capturedChestCount, int capturedChestBlockEntityCount,
                               @Nullable BlockPos firstChestLocal, int mountedStorageCount,
                               int combinedSlotCount, int candidateItemCount, int matchingItemCount,
                               String filterItem, int storageManagerIdentity, ItemStack heldBefore) {
    }
}
