package dev.ryanhcode.sable.mixin.compatibility.create.behaviour_compatibility.plough_behaviour;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.contraptions.actors.plough.PloughMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.ryanhcode.sable.compatibility.create.block_breakers.CreateActorTargetGeometry;
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

/** Routes the Plough's breaker and hoe-use phases to one parent-visible active cell. */
@Mixin(value = PloughMovementBehaviour.class, remap = false)
public class PloughMovementBehaviourMixin {
    @Unique
    private static final String SABLE$EXTERNAL_TARGET = "SableExternalActorTarget";

    @WrapMethod(method = "visitNewPosition")
    public void sable$visitParentVisibleTerrain(final MovementContext context,
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
        final BlockPos soil = resolution.target().below();
        final BlockState before = context.world.getBlockState(soil);
        SableM31CreateActorTrace.action("ACTION_STARTED", context, owner, soil, before,
                "NATIVE_PLOUGH", resolution.actorSpace());
        original.call(context, resolution.target());
        final BlockState after = context.world.getBlockState(soil);
        SableM31CreateActorTrace.action(before.equals(after) ? "ACTION_REJECTED" : "ACTION_SUCCESS",
                context, owner, soil, after,
                before.equals(after) ? "TARGET_UNCHANGED" : "PARENT_TERRAIN_MUTATED", resolution.actorSpace());
    }

    @WrapMethod(method = "onBlockBroken")
    protected void sable$dropBrokenTerrainInParentSpace(final MovementContext context,
                                                        final BlockPos target,
                                                        final BlockState state,
                                                        final Operation<Void> original) {
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null) {
            original.call(context, target, state);
            return;
        }
        try (SableCreateActorWorldContext.ParentSpaceScope ignored =
                     SableCreateActorWorldContext.enterParentSpace(context, owner)) {
            original.call(context, target, state);
        }
    }

    @Unique
    private SubLevelBlockBreakingUtility.TargetResolution sable$resolve(final MovementContext context,
                                                                        final SubLevel owner) {
        final Vec3 storageDirection = context.rotation.apply(
                ((PloughMovementBehaviour) (Object) this).getActiveAreaOffset(context)).normalize();
        final CreateActorTargetGeometry.ActorSpace actorSpace =
                SableCreateActorWorldContext.resolveActivePoint(context, owner, storageDirection);
        return SubLevelBlockBreakingUtility.findExternalPointTarget(owner, context.world, actorSpace);
    }
}
