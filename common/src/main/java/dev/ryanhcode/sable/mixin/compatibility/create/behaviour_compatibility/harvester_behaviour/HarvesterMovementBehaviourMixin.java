package dev.ryanhcode.sable.mixin.compatibility.create.behaviour_compatibility.harvester_behaviour;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.ryanhcode.sable.compatibility.create.block_breakers.CreateActorTargetGeometry;
import dev.ryanhcode.sable.compatibility.create.block_breakers.ExternalBlockBreakingTargetState;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableCreateActorWorldContext;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableM31CreateActorTrace;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SubLevelBlockBreakingUtility;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Routes native Create crop harvesting to the current physical parent-world cell. */
@Mixin(value = HarvesterMovementBehaviour.class, remap = false)
public class HarvesterMovementBehaviourMixin implements MovementBehaviour {
    @Unique
    private static final String SABLE$EXTERNAL_TARGET = "SableExternalActorTarget";

    @WrapMethod(method = "visitNewPosition")
    public void sable$visitParentVisibleCrop(final MovementContext context,
                                             final BlockPos proposedRaw,
                                             final Operation<Void> original) {
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null) {
            original.call(context, proposedRaw);
            return;
        }

        final SubLevelBlockBreakingUtility.TargetResolution resolution = sable$resolve(context, owner);
        SableM31CreateActorTrace.resolution(context, owner, proposedRaw, resolution);
        if (resolution.target() == null) {
            context.data.remove(SABLE$EXTERNAL_TARGET);
            return;
        }

        context.data.put(SABLE$EXTERNAL_TARGET, NbtUtils.writeBlockPos(resolution.target()));
        final BlockState before = resolution.targetState();
        SableM31CreateActorTrace.action("ACTION_STARTED", context, owner, resolution.target(), before,
                "NATIVE_HARVESTER", resolution.actorSpace());
        try (SableCreateActorWorldContext.ParentSpaceScope ignored =
                     SableCreateActorWorldContext.enterParentSpace(context, owner)) {
            original.call(context, resolution.target());
        }
        final BlockState after = context.world.getBlockState(resolution.target());
        SableM31CreateActorTrace.action(before.equals(after) ? "ACTION_REJECTED" : "ACTION_SUCCESS",
                context, owner, resolution.target(), after,
                before.equals(after) ? "TARGET_UNCHANGED" : "PARENT_CROP_MUTATED", resolution.actorSpace());
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
        final SubLevelBlockBreakingUtility.TargetResolution resolution = sable$resolve(context, owner);
        final BlockPos current = context.data.contains(SABLE$EXTERNAL_TARGET)
                ? NbtUtils.readBlockPos(context.data.getCompound(SABLE$EXTERNAL_TARGET))
                : null;
        if (ExternalBlockBreakingTargetState.transition(current, resolution.target())
                == ExternalBlockBreakingTargetState.Transition.UNCHANGED) {
            return;
        }
        if (resolution.target() == null) {
            context.data.remove(SABLE$EXTERNAL_TARGET);
            return;
        }
        ((HarvesterMovementBehaviour) (Object) this).visitNewPosition(context, resolution.target());
    }

    @Unique
    private SubLevelBlockBreakingUtility.TargetResolution sable$resolve(final MovementContext context,
                                                                        final SubLevel owner) {
        final Vec3 storageDirection = context.rotation.apply(this.getActiveAreaOffset(context)).normalize();
        final CreateActorTargetGeometry.ActorSpace actorSpace =
                SableCreateActorWorldContext.resolveActivePoint(context, owner, storageDirection);
        return SubLevelBlockBreakingUtility.findExternalPointTarget(owner, context.world, actorSpace);
    }
}
