package dev.ryanhcode.sable.mixin.compatibility.create.behaviour_compatibility.dispenser;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.dispenser.DefaultMountedDispenseBehavior;
import com.simibubi.create.api.contraption.dispenser.MountedDispenseBehavior;
import com.simibubi.create.api.contraption.dispenser.MountedProjectileDispenseBehavior;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.behaviour.dispenser.DispenserMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.dispenser.DropperMovementBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableCreateActorWorldContext;
import dev.ryanhcode.sable.compatibility.create.dispenser.SableMountedDispenserTarget;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Presents only audited mounted behaviors with parent-visible geometry. */
@Mixin(value = DropperMovementBehaviour.class, remap = false)
public class DropperMovementBehaviourMixin implements MovementBehaviour {
    @Unique
    private static final String SABLE$VISIBLE_DISPENSER_CELL = "SableVisibleDispenserCell";

    @WrapMethod(method = "visitNewPosition")
    public void sable$dispenseAtVisibleCell(final MovementContext context, final BlockPos rawCell,
                                            final Operation<Void> original) {
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null || context.world.isClientSide) {
            original.call(context, rawCell);
            return;
        }
        final SableMountedDispenserTarget.Result target = SableMountedDispenserTarget.resolve(context, owner);
        final String behavior = sable$auditedBehavior(context);
        if (!target.accepted() || behavior == null) {
            context.data.remove(SABLE$VISIBLE_DISPENSER_CELL);
            if (SableDiagnosticFlags.TRACE_CREATE_ACTORS) {
                Sable.LOGGER.info("SABLE_M38_DISPENSER event=DISPENSE_REJECTED actorLocal={} rawPosition={} "
                                + "decision={} behavior={}", context.localPos, context.position,
                        target.decision(), behavior);
            }
            return;
        }
        context.data.put(SABLE$VISIBLE_DISPENSER_CELL, NbtUtils.writeBlockPos(target.source()));
        final int before = sable$mountedCount(context);
        if (SableDiagnosticFlags.TRACE_CREATE_ACTORS) {
            Sable.LOGGER.info("SABLE_M38_DISPENSER event=VISIBLE_SOURCE_RESOLVED actorLocal={} subLevel={} "
                            + "rawPosition={} visiblePosition={} localFacing={} parentFacing={} behaviorClass={} "
                            + "target={} decision=PARENT_SOURCE", context.localPos, owner.getUniqueId(),
                    context.position, owner.logicalPose().transformPosition(context.position),
                    context.state.getValue(net.minecraft.world.level.block.DispenserBlock.FACING),
                    target.facing(), behavior, target.source().relative(target.facing()));
        }
        try (SableCreateActorWorldContext.ParentInteractionScope ignored =
                     SableCreateActorWorldContext.enterParentInteractionSpace(context, owner)) {
            original.call(context, target.source());
        }
        if (SableDiagnosticFlags.TRACE_CREATE_ACTORS) {
            final int after = sable$mountedCount(context);
            if (before != after) {
                Sable.LOGGER.info("SABLE_M38_DISPENSER event=STACK_CHANGED actorLocal={} source={} "
                                + "behaviorClass={} mountedBefore={} mountedAfter={}", context.localPos,
                        target.source(), behavior, before, after);
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
        final SableMountedDispenserTarget.Result target = SableMountedDispenserTarget.resolve(context, owner);
        if (!target.accepted()) {
            context.data.remove(SABLE$VISIBLE_DISPENSER_CELL);
            return;
        }
        final BlockPos previous = context.data.contains(SABLE$VISIBLE_DISPENSER_CELL)
                ? NbtUtils.readBlockPos(context.data.getCompound(SABLE$VISIBLE_DISPENSER_CELL)) : null;
        if (!target.source().equals(previous)) {
            ((DropperMovementBehaviour) (Object) this).visitNewPosition(context, target.source());
        }
    }

    @Unique
    private String sable$auditedBehavior(final MovementContext context) {
        final MountedItemStorage storage = context.getItemStorage();
        if (storage == null) {
            return null;
        }
        if (!((Object) this instanceof DispenserMovementBehaviour)) {
            return DefaultMountedDispenseBehavior.class.getName();
        }
        String selectedClass = null;
        for (int slot = 0; slot < storage.getSlots(); slot++) {
            final ItemStack stack = storage.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            final MountedDispenseBehavior registered = MountedDispenseBehavior.REGISTRY.get(stack.getItem());
            final MountedDispenseBehavior nativeBehavior = registered == null
                    ? DefaultMountedDispenseBehavior.INSTANCE : registered;
            if (nativeBehavior != DefaultMountedDispenseBehavior.INSTANCE
                    && !(stack.is(Items.ARROW) && nativeBehavior instanceof MountedProjectileDispenseBehavior)
                    && !(stack.is(Items.FLINT_AND_STEEL)
                            && nativeBehavior.getClass().getName().endsWith("$FallbackBehavior"))) {
                return null;
            }
            selectedClass = nativeBehavior.getClass().getName();
        }
        return selectedClass == null ? DefaultMountedDispenseBehavior.class.getName() : selectedClass;
    }

    @Unique
    private static int sable$mountedCount(final MovementContext context) {
        final MountedItemStorage storage = context.getItemStorage();
        if (storage == null) {
            return -1;
        }
        int count = 0;
        for (int slot = 0; slot < storage.getSlots(); slot++) {
            count += storage.getStackInSlot(slot).getCount();
        }
        return count;
    }
}
