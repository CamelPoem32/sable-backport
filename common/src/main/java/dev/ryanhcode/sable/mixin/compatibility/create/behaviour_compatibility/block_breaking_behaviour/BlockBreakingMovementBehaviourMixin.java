package dev.ryanhcode.sable.mixin.compatibility.create.behaviour_compatibility.block_breaking_behaviour;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SubLevelBlockBreakingUtility;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = BlockBreakingMovementBehaviour.class, remap = false)
public abstract class BlockBreakingMovementBehaviourMixin implements MovementBehaviour {
    @Unique
    private static final Map<Integer, Integer> SABLE_M16_LAST_PROGRESS = new ConcurrentHashMap<>();

    @Shadow
    public abstract boolean canBreak(Level world, BlockPos breakingPos, BlockState state);

    @WrapMethod(method = "visitNewPosition")
    public void sable$checkPosition(final MovementContext context, final BlockPos pos, final Operation<Void> original) {
        if (context.stall) {
            return;
        }

        original.call(context, pos);
        if (context.stall) {
            sable$logContext("breaking_begins_original", context, pos, getBreakingPos(context.data));
            return;
        }

        final SubLevel containingSubLevel = Sable.HELPER.getContaining(context.world, context.contraption.anchor);
        if (containingSubLevel == null) {
            return;
        }

        final Vec3 localCenter = context.localPos.getCenter();
        final Vec3 subLevelLocalCenter = context.contraption.entity.toGlobalVector(localCenter, 1);
        final Vec3 subLevelLocalDir = context.rotation.apply(this.getActiveAreaOffset(context));

        sable$logContext("original_create_target_miss", context, pos, null);
        final BlockPos selectedBreakingPos = SubLevelBlockBreakingUtility.findBreakingPos(
                (blockPos, state) -> this.canBreak(context.world, blockPos, state),
                containingSubLevel,
                context.world,
                subLevelLocalDir,
                subLevelLocalCenter,
                pos
        );

        sable$logContext("sable_candidate_selected", context, pos, selectedBreakingPos);
        original.call(context, selectedBreakingPos);
        if (context.stall) {
            sable$logContext("breaking_begins_sable", context, pos, selectedBreakingPos);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    public void sable$clearStaleBreakingPos(final MovementContext context, final CallbackInfo ci) {
        final CompoundTag data = context.data;
        final BlockPos blockPos = getBreakingPosOrLastPos(data);
        if (blockPos == null) {
            return;
        }

        sable$logProgressTransition(context, blockPos);

        final Vec3 localCenter = context.localPos.getCenter();
        Vec3 subLevelLocalCenter = context.contraption.entity.toGlobalVector(localCenter, 1);
        Vec3 targetCenter = blockPos.getCenter();

        final ActiveSableCompanion helper = Sable.HELPER;
        final SubLevel parentSubLevel = helper.getContaining(context.world, context.contraption.anchor);
        final SubLevel targetSubLevel = helper.getContaining(context.world, blockPos);

        if (parentSubLevel != null) {
            subLevelLocalCenter = parentSubLevel.logicalPose().transformPosition(subLevelLocalCenter);
        }

        if (targetSubLevel != null) {
            targetCenter = targetSubLevel.logicalPose().transformPosition(targetCenter);
        }

        if (subLevelLocalCenter.distanceToSqr(targetCenter) > 2 * 2) {
            data.remove("Progress");
            data.remove("TicksUntilNextProgress");
            data.remove("BreakingPos");
            data.remove("LastPos");
            data.remove("WaitingTicks");

            context.stall = false;
            context.world.destroyBlockProgress(data.getInt("BreakerId"), blockPos, -1);

            sable$logContext("stale_breaking_state_cleared", context, blockPos, null);
            ci.cancel();
        }
    }

    @Unique
    private static @Nullable BlockPos getBreakingPosOrLastPos(final CompoundTag data) {
        final BlockPos breakingPos = getBreakingPos(data);
        return breakingPos != null ? breakingPos : getBlockPos(data, "LastPos");
    }

    @Unique
    private static @Nullable BlockPos getBreakingPos(final CompoundTag data) {
        return getBlockPos(data, "BreakingPos");
    }

    @Unique
    private static @Nullable BlockPos getBlockPos(final CompoundTag data, final String key) {
        return data.contains(key) && data.get(key) instanceof CompoundTag ? NbtUtils.readBlockPos(data.getCompound(key)) : null;
    }

    @Unique
    private static void sable$logProgressTransition(final MovementContext context, final BlockPos blockPos) {
        final int breakerId = context.data.getInt("BreakerId");
        final int progress = context.data.getInt("Progress");
        final Integer previous = SABLE_M16_LAST_PROGRESS.put(breakerId, progress);
        if (previous == null || previous != progress) {
            sable$logContext("progress", context, blockPos, blockPos);
        }
    }

    @Unique
    private static void sable$logContext(final String phase,
                                         final MovementContext context,
                                         final BlockPos proposed,
                                         @Nullable final BlockPos selected) {
        final SubLevel containingSubLevel = Sable.HELPER.getContaining(context.world, context.contraption.anchor);
        if (containingSubLevel == null) {
            return;
        }

        final BlockPos plotCenter = containingSubLevel.getPlot().getCenterBlock();
        final BlockState state = selected == null ? context.state : context.world.getBlockState(selected);
        Sable.LOGGER.info("SABLE_M16_BREAK phase={} entityId={} subLevel={} actorLocal={} actorState={} actorCenter={} proposedRaw={} proposedFixtureLocal={} selectedRaw={} selectedFixtureLocal={} blockId={} progress={} breakerId={} stall={}",
                phase,
                context.contraption.entity.getId(),
                containingSubLevel.getUniqueId(),
                formatBlockPos(context.localPos),
                BuiltInRegistries.BLOCK.getKey(context.state.getBlock()),
                formatVec(context.position),
                formatBlockPos(proposed),
                formatBlockPos(proposed.subtract(plotCenter)),
                formatBlockPos(selected),
                selected == null ? "none" : formatBlockPos(selected.subtract(plotCenter)),
                BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                context.data.getInt("Progress"),
                context.data.getInt("BreakerId"),
                context.stall);
    }

    @Unique
    private static String formatBlockPos(@Nullable final BlockPos pos) {
        return pos == null ? "null" : "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    @Unique
    private static String formatVec(@Nullable final Vec3 vec) {
        return vec == null ? "null" : String.format(java.util.Locale.ROOT, "(%.4f,%.4f,%.4f)", vec.x, vec.y, vec.z);
    }
}
