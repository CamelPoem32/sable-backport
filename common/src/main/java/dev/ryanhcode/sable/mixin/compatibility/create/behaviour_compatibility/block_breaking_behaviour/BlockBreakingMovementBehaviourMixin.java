package dev.ryanhcode.sable.mixin.compatibility.create.behaviour_compatibility.block_breaking_behaviour;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.ryanhcode.sable.Sable;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.content.contraptions.actors.plough.PloughMovementBehaviour;
import com.simibubi.create.content.kinetics.drill.DrillMovementBehaviour;
import com.simibubi.create.content.kinetics.saw.SawMovementBehaviour;
import com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour;
import dev.ryanhcode.sable.compatibility.create.block_breakers.CreateActorTargetGeometry;
import dev.ryanhcode.sable.compatibility.create.block_breakers.ExternalBlockBreakingTargetState;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableM31CreateActorTrace;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableCreateActorWorldContext;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableRollerTerrain;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SubLevelBlockBreakingUtility;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import dev.ryanhcode.sable.compatibility.create.residuals.ResidualActorGeometry;
import dev.ryanhcode.sable.compatibility.create.residuals.SableActorEntityEvidence;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.AABB;
import java.util.List;
import net.minecraft.core.BlockPos;
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

/** Keeps Create's breaker semantics while resolving supported Sable actors in parent-visible space. */
@Mixin(value = BlockBreakingMovementBehaviour.class, remap = false)
public abstract class BlockBreakingMovementBehaviourMixin implements MovementBehaviour {
    @Unique
    private static final String SABLE$EXTERNAL_TARGET = "SableExternalActorTarget";

    @Shadow
    public abstract boolean canBreak(Level world, BlockPos breakingPos, BlockState state);

    @WrapMethod(method = "visitNewPosition")
    public void sable$resolveExternalDrillTarget(final MovementContext context,
                                                 final BlockPos proposedRaw,
                                                 final Operation<Void> original) {
        final SubLevel owner = sable$owner(context);
        if (owner == null) {
            original.call(context, proposedRaw);
            return;
        }
        if (context.stall) {
            return;
        }

        final SubLevelBlockBreakingUtility.TargetResolution resolution = sable$resolve(context, owner);
        SableM31CreateActorTrace.resolution(context, owner, proposedRaw, resolution);
        sable$visitPhysicalEntityCell(context, owner);
        if (resolution.target() == null) {
            return;
        }

        original.call(context, resolution.target());
        if (context.stall) {
            SableM31CreateActorTrace.progressStarted(context, owner, resolution.target(), resolution);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void sable$retargetMovingParent(final MovementContext context, final CallbackInfo ci) {
        final SubLevel owner = sable$owner(context);
        if (owner == null || context.world.isClientSide || context.data.contains("WaitingTicks")) {
            return;
        }

        final SubLevelBlockBreakingUtility.TargetResolution resolution = sable$resolve(context, owner);
        SableM31CreateActorTrace.resolution(context, owner, BlockPos.containing(context.position), resolution);
        final boolean pointActor = (Object) this instanceof PloughMovementBehaviour;
        if (!pointActor && !context.stall) {
            sable$visitPhysicalEntityCell(context, owner);
        }
        final BlockPos current = sable$getBlockPos(context.data,
                pointActor ? SABLE$EXTERNAL_TARGET : "BreakingPos");
        final ExternalBlockBreakingTargetState.Transition transition =
                ExternalBlockBreakingTargetState.transition(current, resolution.target());
        if (transition == ExternalBlockBreakingTargetState.Transition.UNCHANGED) {
            return;
        }

        final BlockPos breakingPos = sable$getBlockPos(context.data, "BreakingPos");
        if (breakingPos != null) {
            sable$clearBreakingState(context, breakingPos);
            SableM31CreateActorTrace.progressReset(context, owner, breakingPos, resolution);
        }
        if (pointActor) {
            if (resolution.target() == null) {
                context.data.remove(SABLE$EXTERNAL_TARGET);
            } else {
                context.data.put(SABLE$EXTERNAL_TARGET, NbtUtils.writeBlockPos(resolution.target()));
            }
        }
        if (resolution.target() != null) {
            ((BlockBreakingMovementBehaviour) (Object) this).visitNewPosition(context, resolution.target());
        }
    }

    /** The native non-conductor/entity phase precedes canBreak; lack of a terrain target is not ineligibility. */
    @Unique
    private void sable$visitPhysicalEntityCell(final MovementContext context, final SubLevel owner) {
        if (!((Object) this instanceof DrillMovementBehaviour) && !((Object) this instanceof SawMovementBehaviour)) return;
        if (context.position == null) return;
        final Vec3 direction = context.rotation.apply(this.getActiveAreaOffset(context)).normalize();
        final var point = SubLevelBlockBreakingUtility.findExternalPointTarget(owner, context.world,
                SableCreateActorWorldContext.resolveActivePoint(context, owner, direction));
        if (point.target() == null) return;
        final BlockPos cell = point.target();
        final String key = "SableM43EntityCell";
        if (context.data.contains(key) && NbtUtils.readBlockPos(context.data.getCompound(key)).equals(cell)) return;
        context.data.put(key, NbtUtils.writeBlockPos(cell));
        if (!point.targetState().isRedstoneConductor(context.world, cell)) {
            ((BlockBreakingMovementBehaviour) (Object) this).damageEntities(context, cell, context.world);
        }
    }

    /** The independent physical phase above replaces this one native call, never adds a second query. */
    @WrapOperation(method = "visitNewPosition", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/base/BlockBreakingMovementBehaviour;damageEntities(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;)V"))
    private void sable$avoidSecondEntityPhase(final BlockBreakingMovementBehaviour behaviour,
                                               final MovementContext context, final BlockPos pos, final Level world,
                                               final Operation<Void> original) {
        if (sable$owner(context) == null || !((Object) this instanceof DrillMovementBehaviour)
                && !((Object) this instanceof SawMovementBehaviour)) original.call(behaviour, context, pos, world);
    }

    /** Only the input motion changes; native world-up lift, cap, selection and final mutation remain native. */
    @WrapMethod(method = "throwEntity")
    protected void sable$rotateNativeEntityMotion(final MovementContext context, final Entity entity,
                                                   final Operation<Void> original) {
        final SubLevel owner = sable$owner(context);
        if (owner == null) { original.call(context, entity); return; }
        if (entity.level() != owner.getLevel() || Sable.HELPER.getContaining(entity) != null) return;
        final Vec3 rawMotion = context.motion;
        final Vec3 physicalMotion = ResidualActorGeometry.rotate(owner.logicalPose(), rawMotion);
        final Vec3 before = entity.getDeltaMovement();
        try {
            context.motion = physicalMotion;
            original.call(context, entity);
        } finally { context.motion = rawMotion; }
        SableActorEntityEvidence.record(context, new SableActorEntityEvidence.Event(context.world.getGameTime(),
                "THROW", entity.blockPosition(), entity.getUUID(), rawMotion, physicalMotion, before,
                entity.getDeltaMovement(), 0, 0, true, false, 1));
    }

    @WrapOperation(method = "damageEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;", remap = true))
    private List<Entity> sable$observeNativeEntityQuery(final Level world, final Class<Entity> type, final AABB bounds,
                                                        final Operation<List<Entity>> original,
                                                        final MovementContext context, final BlockPos pos, final Level level) {
        final List<Entity> result = original.call(world, type, bounds);
        if (sable$owner(context) != null) SableActorEntityEvidence.record(context,
                new SableActorEntityEvidence.Event(world.getGameTime(), "QUERY", pos, null, null, null, null, null,
                        0, 0, true, world.getBlockState(pos).isAir(), result.size()));
        return result;
    }

    @WrapOperation(method = "damageEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", remap = true))
    private boolean sable$observeNativeDamage(final Entity entity, final DamageSource source, final float damage,
                                              final Operation<Boolean> original, final MovementContext context,
                                              final BlockPos pos, final Level world) {
        if (sable$owner(context) == null) return original.call(entity, source, damage);
        final float before = SableActorEntityEvidence.health(entity);
        final boolean accepted = original.call(entity, source, damage);
        SableActorEntityEvidence.record(context, new SableActorEntityEvidence.Event(world.getGameTime(), "DAMAGE", pos,
                entity.getUUID(), null, null, null, null, before, SableActorEntityEvidence.health(entity), accepted,
                world.getBlockState(pos).isAir(), 1));
        return accepted;
    }

    @WrapMethod(method = "tick")
    public void sable$tickRollerInParentSpace(final MovementContext context, final Operation<Void> original) {
        if (!((Object) this instanceof RollerMovementBehaviour roller)) {
            original.call(context);
            return;
        }
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null) {
            original.call(context);
            return;
        }
        SableRollerTerrain.retarget(context, owner, roller);
        original.call(context);
    }

    @WrapMethod(method = "destroyBlock")
    protected void sable$useParentVisibleDropContext(final MovementContext context,
                                                      final BlockPos target,
                                                      final Operation<Void> original) {
        final SubLevel owner = sable$owner(context);
        if (owner == null) {
            original.call(context, target);
            return;
        }
        try (SableCreateActorWorldContext.ParentSpaceScope ignored =
                     SableCreateActorWorldContext.enterParentSpace(context, owner)) {
            original.call(context, target);
        }
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void sable$traceMutationAttempt(final MovementContext context,
                                            final BlockPos target,
                                            final CallbackInfo ci) {
        final SubLevel owner = sable$owner(context);
        if (owner == null || !SableDiagnosticFlags.TRACE_CREATE_ACTORS) {
            return;
        }
        SableM31CreateActorTrace.mutation("BLOCK_MUTATION_ATTEMPT", context, owner, target,
                context.world.getBlockState(target), "CREATE_BLOCK_HELPER", sable$actorSpace(context, owner));
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void sable$traceMutationResult(final MovementContext context,
                                           final BlockPos target,
                                           final CallbackInfo ci) {
        final SubLevel owner = sable$owner(context);
        if (owner == null || !SableDiagnosticFlags.TRACE_CREATE_ACTORS) {
            return;
        }
        final BlockState after = context.world.getBlockState(target);
        SableM31CreateActorTrace.mutation(after.isAir() ? "BLOCK_MUTATION_SUCCESS" : "BLOCK_MUTATION_REJECTED",
                context, owner, target, after, after.isAir() ? "PARENT_WORLD_MUTATED" : "TARGET_REMAINED",
                sable$actorSpace(context, owner));
    }

    @Unique
    private SubLevelBlockBreakingUtility.TargetResolution sable$resolve(final MovementContext context,
                                                                        final SubLevel owner) {
        final CreateActorTargetGeometry.ActorSpace actorSpace = sable$actorSpace(context, owner);
        if ((Object) this instanceof PloughMovementBehaviour) {
            return SubLevelBlockBreakingUtility.findExternalPointTarget(owner, context.world, actorSpace);
        }
        return SubLevelBlockBreakingUtility.findExternalTarget(
                (blockPos, state) -> this.canBreak(context.world, blockPos, state),
                owner,
                context.world,
                actorSpace);
    }

    @Unique
    private CreateActorTargetGeometry.ActorSpace sable$actorSpace(final MovementContext context,
                                                                  final SubLevel owner) {
        final Vec3 storageDirection = context.rotation.apply(this.getActiveAreaOffset(context)).normalize();
        if ((Object) this instanceof PloughMovementBehaviour) {
            return SableCreateActorWorldContext.resolveActivePoint(context, owner, storageDirection);
        }
        final Vec3 storageCenter = context.contraption.entity.toGlobalVector(context.localPos.getCenter(), 1.0F);
        return CreateActorTargetGeometry.resolve(owner.logicalPose(), storageCenter, storageDirection);
    }

    @Unique
    private SubLevel sable$owner(final MovementContext context) {
        if (!((Object) this instanceof DrillMovementBehaviour)
                && !((Object) this instanceof SawMovementBehaviour)
                && !((Object) this instanceof PloughMovementBehaviour)) {
            return null;
        }
        return SableCreateActorWorldContext.owner(context);
    }

    @Unique
    private static void sable$clearBreakingState(final MovementContext context, final BlockPos previous) {
        final CompoundTag data = context.data;
        context.world.destroyBlockProgress(data.getInt("BreakerId"), previous, -1);
        data.remove("Progress");
        data.remove("TicksUntilNextProgress");
        data.remove("BreakingPos");
        data.remove("LastPos");
        data.remove("WaitingTicks");
        context.stall = false;
    }

    @Unique
    private static @Nullable BlockPos sable$getBlockPos(final CompoundTag data, final String key) {
        return data.contains(key) && data.get(key) instanceof CompoundTag
                ? NbtUtils.readBlockPos(data.getCompound(key))
                : null;
    }
}
