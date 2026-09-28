package dev.ryanhcode.sable.mixin.compatibility.create.behaviour_compatibility.roller_behaviour;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableCreateActorWorldContext;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableM31CreateActorTrace;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableRollerTerrain;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SubLevelBlockBreakingUtility;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps the complete native Roller footprint in parent world space. */
@Mixin(value = RollerMovementBehaviour.class, remap = false)
public class RollerMovementBehaviourMixin {
    @WrapMethod(method = "visitNewPosition")
    public void sable$visitParentTerrain(final MovementContext context, final BlockPos proposedRaw,
                                         final Operation<Void> original) {
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null) {
            original.call(context, proposedRaw);
            return;
        }
        final SubLevelBlockBreakingUtility.TargetResolution resolution = SableRollerTerrain.resolve(
                context, owner, (RollerMovementBehaviour) (Object) this);
        SableM31CreateActorTrace.resolution(context, owner, proposedRaw, resolution);
        if (resolution.target() == null) {
            context.data.remove(SableRollerTerrain.EXTERNAL_TARGET);
            return;
        }
        context.data.put(SableRollerTerrain.EXTERNAL_TARGET, NbtUtils.writeBlockPos(resolution.target()));
        SableM31CreateActorTrace.roller("ROLLER_FOOTPRINT_RESOLVED", context, owner,
                resolution.target(), "NATIVE_CREATE_COLUMN_AND_LAYERS", resolution.actorSpace());
        try (SableCreateActorWorldContext.ParentSpaceScope ignored =
                     SableCreateActorWorldContext.enterParentSpace(context, owner)) {
            original.call(context, resolution.target());
        }
    }

    @WrapMethod(method = "testBreakerTarget")
    protected boolean sable$guardClearanceCell(final MovementContext context, final BlockPos target,
                                                final int columnY, final Operation<Boolean> original) {
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null || SableRollerTerrain.isSafeCell(context, owner, target)) {
            return original.call(context, target, columnY);
        }
        sable$traceCell("ROLLER_TERRAIN_QUERY", context, owner, target, "REJECT_UNSAFE_CLEARANCE_CELL");
        return false;
    }

    @WrapMethod(method = "destroyBlock")
    protected void sable$guardNativeBreaking(final MovementContext context, final BlockPos target,
                                             final Operation<Void> original) {
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null) {
            original.call(context, target);
        } else if (SableRollerTerrain.isSafeCell(context, owner, target)) {
            final BlockState before = SableDiagnosticFlags.TRACE_CREATE_ACTORS
                    ? context.world.getBlockState(target) : null;
            try (SableCreateActorWorldContext.ParentSpaceScope ignored =
                         SableCreateActorWorldContext.enterParentSpace(context, owner)) {
                original.call(context, target);
            }
            if (before != null && !before.isAir() && context.world.getBlockState(target).isAir()) {
                sable$traceCell("ROLLER_CLEAR_SUCCESS", context, owner, target, "NATIVE_CREATE_BREAK");
            }
        } else {
            sable$traceCell("ROLLER_CLEAR_ATTEMPT", context, owner, target, "REJECT_UNSAFE_CELL");
        }
    }

    @WrapOperation(method = "tryFill", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;isLoaded(Lnet/minecraft/core/BlockPos;)Z", remap = true))
    private boolean sable$guardNativePaving(final Level level, final BlockPos target,
                                            final Operation<Boolean> original, final MovementContext context) {
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner != null && !SableRollerTerrain.isSafeCell(context, owner, target)) {
            sable$traceCell("ROLLER_PAVE_REJECTED", context, owner, target, "REJECT_UNSAFE_CELL");
            return false;
        }
        return original.call(level, target);
    }

    @WrapOperation(method = "tryFill", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z", remap = true))
    private boolean sable$traceNativePaving(final Level level, final BlockPos target,
                                            final BlockState placed, final Operation<Boolean> original,
                                            final MovementContext context) {
        final boolean updated = original.call(level, target, placed);
        if (!SableDiagnosticFlags.TRACE_CREATE_ACTORS) {
            return updated;
        }
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner != null) {
            sable$traceCell(updated ? "ROLLER_PAVE_SUCCESS" : "ROLLER_PAVE_REJECTED", context, owner,
                    target, updated ? "NATIVE_CREATE_EXTRACT_ONE_AND_PLACE" : "NATIVE_SET_BLOCK_FAILED");
        }
        return updated;
    }

    private void sable$traceCell(final String event, final MovementContext context, final SubLevel owner,
                                 final BlockPos target, final String decision) {
        if (!SableDiagnosticFlags.TRACE_CREATE_ACTORS) {
            return;
        }
        SableM31CreateActorTrace.roller(event, context, owner, target, decision,
                SableRollerTerrain.resolve(context, owner, (RollerMovementBehaviour) (Object) this).actorSpace());
    }
}
