package dev.ryanhcode.sable.mixin.compatibility.create.behaviour_compatibility.funnel;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelMovementBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableCreateActorWorldContext;
import dev.ryanhcode.sable.compatibility.create.funnel.SableMovingFunnelTarget;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** Keeps Create's mounted-storage and item-entity logic, changing only its physical active cell. */
@Mixin(value = FunnelMovementBehaviour.class, remap = false)
public class FunnelMovementBehaviourMixin implements MovementBehaviour {
    @Unique
    private static final String SABLE$EXTERNAL_FUNNEL_CELL = "SableExternalFunnelCell";
    @Unique
    private static final Map<MovementContext, String> SABLE$TRACE_KEYS =
            Collections.synchronizedMap(new WeakHashMap<>());

    @WrapMethod(method = "visitNewPosition")
    public void sable$visitParentVisibleCell(final MovementContext context, final BlockPos proposedRaw,
                                             final Operation<Void> original) {
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null) {
            original.call(context, proposedRaw);
            return;
        }
        if (context.world.isClientSide) {
            return;
        }
        final SableMovingFunnelTarget.Result target = SableMovingFunnelTarget.resolve(context, owner);
        sable$trace(context, owner, target);
        if (!target.accepted()) {
            context.data.remove(SABLE$EXTERNAL_FUNNEL_CELL);
            return;
        }
        context.data.put(SABLE$EXTERNAL_FUNNEL_CELL, NbtUtils.writeBlockPos(target.parentBlock()));
        final int before = SableDiagnosticFlags.TRACE_CREATE_ACTORS ? sable$mountedItemCount(context) : 0;
        try (SableCreateActorWorldContext.ParentSpaceScope ignored =
                     SableCreateActorWorldContext.enterParentSpace(context, owner)) {
            original.call(context, target.parentBlock());
        }
        if (SableDiagnosticFlags.TRACE_CREATE_ACTORS) {
            final int after = sable$mountedItemCount(context);
            if (before != after) {
                Sable.LOGGER.info("SABLE_M37_FUNNEL event={} actorLocal={} target={} mountedBefore={} "
                                + "mountedAfter={} decision=NATIVE_CREATE_STORAGE_TRANSFER",
                        context.state.getValue(FunnelBlock.EXTRACTING) ? "EXTRACT_SUCCESS" : "INSERT_SUCCESS",
                        context.localPos, target.parentBlock(), before, after);
            }
        }
    }

    @Override
    public void tick(final MovementContext context) {
        if (context.world.isClientSide) {
            return;
        }
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null) {
            return;
        }
        final SableMovingFunnelTarget.Result target = SableMovingFunnelTarget.resolve(context, owner);
        if (!target.accepted()) {
            context.data.remove(SABLE$EXTERNAL_FUNNEL_CELL);
            sable$trace(context, owner, target);
            return;
        }
        final BlockPos previous = context.data.contains(SABLE$EXTERNAL_FUNNEL_CELL)
                ? NbtUtils.readBlockPos(context.data.getCompound(SABLE$EXTERNAL_FUNNEL_CELL))
                : null;
        if (!target.parentBlock().equals(previous)) {
            ((FunnelMovementBehaviour) (Object) this).visitNewPosition(context, target.parentBlock());
        }
    }

    @WrapOperation(method = "succ", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
            remap = true))
    private List<Entity> sable$parentItemsOnly(final Level level, final Entity excluded, final AABB bounds,
                                               final Predicate<? super Entity> nativePredicate,
                                               final Operation<List<Entity>> original,
                                               @Local(argsOnly = true) final MovementContext context) {
        if (SableCreateActorWorldContext.owner(context) == null) {
            return original.call(level, excluded, bounds, nativePredicate);
        }
        return original.call(level, excluded, bounds,
                (Predicate<Entity>) entity -> nativePredicate.test(entity)
                        && Sable.HELPER.getContaining(level, entity.position()) == null);
    }

    @WrapOperation(method = "extract", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;",
            remap = true))
    private List<ItemEntity> sable$parentOutputCellOnly(final Level level, final Class<ItemEntity> type,
                                                        final AABB bounds, final Operation<List<ItemEntity>> original,
                                                        @Local(argsOnly = true) final MovementContext context) {
        final List<ItemEntity> candidates = original.call(level, type, bounds);
        if (SableCreateActorWorldContext.owner(context) == null) {
            return candidates;
        }
        return candidates.stream()
                .filter(entity -> Sable.HELPER.getContaining(level, entity.position()) == null)
                .toList();
    }

    @Unique
    private static void sable$trace(final MovementContext context, final SubLevel owner,
                                    final SableMovingFunnelTarget.Result target) {
        if (SableDiagnosticFlags.TRACE_CREATE_ACTORS) {
            final String key = target.decision() + ":" + target.parentBlock();
            if (key.equals(SABLE$TRACE_KEYS.put(context, key))) {
                return;
            }
            Sable.LOGGER.info("SABLE_M37_FUNNEL event=TARGET_CHANGED actorLocal={} subLevel={} rawPosition={} "
                            + "visiblePosition={} parentFacing={} target={} decision={}",
                    context.localPos, owner.getUniqueId(), context.position,
                    context.position == null ? null : owner.logicalPose().transformPosition(context.position),
                    target.parentFacing(), target.parentBlock(), target.decision());
        }
    }

    @Unique
    private static int sable$mountedItemCount(final MovementContext context) {
        final IItemHandler storage = context.contraption.getStorage().getAllItems();
        int total = 0;
        for (int slot = 0; slot < storage.getSlots(); slot++) {
            total += storage.getStackInSlot(slot).getCount();
        }
        return total;
    }
}
