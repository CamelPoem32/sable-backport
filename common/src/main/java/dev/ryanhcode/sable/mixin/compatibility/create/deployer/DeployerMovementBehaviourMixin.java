package dev.ryanhcode.sable.mixin.compatibility.create.deployer;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import dev.ryanhcode.sable.compatibility.create.block_breakers.CreateActorTargetGeometry;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableCreateActorWorldContext;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableM31CreateActorTrace;
import dev.ryanhcode.sable.compatibility.create.deployer.SableDeployerMountedStorageTrace;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SubLevelBlockBreakingUtility;
import dev.ryanhcode.sable.compatibility.create.deployer.SubLevelDeployerInteractionUtility;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Routes only Sable-contained moving Deployers through native Create in parent-visible space. */
@Mixin(targets = "com.simibubi.create.content.kinetics.deployer.DeployerMovementBehaviour", remap = false)
public abstract class DeployerMovementBehaviourMixin implements MovementBehaviour {
    @WrapMethod(method = "tryGrabbingItem")
    private void sable$traceNativeRefill(final MovementContext context, final Operation<Void> original) {
        if (!SableDiagnosticFlags.TRACE_CREATE_ACTORS) {
            original.call(context);
            return;
        }
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null) {
            original.call(context);
            return;
        }
        final SableDeployerMountedStorageTrace.RefillSample before =
                SableDeployerMountedStorageTrace.capture(context);
        original.call(context);
        SableDeployerMountedStorageTrace.completed(context, owner, before);
    }

    @WrapMethod(method = "visitNewPosition")
    private void sable$visitParentVisibleInteraction(final MovementContext context,
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
            return;
        }
        original.call(context, resolution.target());
    }

    @WrapMethod(method = "activate")
    private void sable$activateInParentVisibleSpace(final MovementContext context,
                                                    final BlockPos parentTarget,
                                                    final DeployerFakePlayer player,
                                                    @Coerce final Object mode,
                                                    final Operation<Void> original) {
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null) {
            original.call(context, parentTarget, player, mode);
            return;
        }

        final Vec3 storageDirection = sable$storageDirection(context);
        final CreateActorTargetGeometry.ActorSpace actorSpace =
                SableCreateActorWorldContext.resolveActivePoint(context, owner, storageDirection);
        final CreateActorTargetGeometry.DeployerSpace deployerSpace = CreateActorTargetGeometry.resolveDeployer(
                owner.logicalPose(), actorSpace.storageCenter(), storageDirection);
        final ItemStack heldBefore = player.getMainHandItem().copy();
        final BlockState stateBefore = context.world.getBlockState(parentTarget);
        try (SableCreateActorWorldContext.ParentInteractionScope ignored =
                     SableCreateActorWorldContext.enterParentInteractionSpace(context, owner)) {
            original.call(context, parentTarget, player, mode);
        }
        final ItemStack heldAfter = player.getMainHandItem().copy();
        final BlockState stateAfter = context.world.getBlockState(parentTarget);
        SableM31CreateActorTrace.deployerInteraction(context, owner, parentTarget, mode.toString(), player.position(),
                deployerSpace, heldBefore, heldAfter, stateBefore, stateAfter);
    }

    @Unique
    private SubLevelBlockBreakingUtility.TargetResolution sable$resolve(final MovementContext context,
                                                                        final SubLevel owner) {
        final Vec3 storageDirection = sable$storageDirection(context);
        final CreateActorTargetGeometry.ActorSpace actorSpace =
                SableCreateActorWorldContext.resolveActivePoint(context, owner, storageDirection);
        return SubLevelDeployerInteractionUtility.resolveExternalTarget(owner, context.world, actorSpace);
    }

    @Unique
    private Vec3 sable$storageDirection(final MovementContext context) {
        return context.rotation.apply(this.getActiveAreaOffset(context).normalize()).normalize();
    }
}
