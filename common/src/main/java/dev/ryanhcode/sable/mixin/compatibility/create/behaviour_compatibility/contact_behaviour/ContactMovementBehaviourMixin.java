package dev.ryanhcode.sable.mixin.compatibility.create.behaviour_compatibility.contact_behaviour;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.redstone.contact.ContactMovementBehaviour;
import dev.ryanhcode.sable.compatibility.create.block_breakers.CreateActorTargetGeometry;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableCreateActorWorldContext;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableM31CreateActorTrace;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SubLevelBlockBreakingUtility;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/** Resolves the moving contact's visited block and facing in the physical parent world. */
@Mixin(value = ContactMovementBehaviour.class, remap = false)
public class ContactMovementBehaviourMixin {
    @WrapMethod(method = "visitNewPosition")
    public void sable$visitParentContact(final MovementContext context,
                                         final BlockPos proposedRaw,
                                         final Operation<Void> original) {
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null || context.world.isClientSide) {
            original.call(context, proposedRaw);
            return;
        }

        final Vec3 storageDirection = context.rotation.apply(
                ((ContactMovementBehaviour) (Object) this).getActiveAreaOffset(context)).normalize();
        final CreateActorTargetGeometry.ActorSpace actorSpace =
                SableCreateActorWorldContext.resolveActivePoint(context, owner, storageDirection);
        final SubLevelBlockBreakingUtility.TargetResolution resolution =
                SubLevelBlockBreakingUtility.findExternalPointTarget(owner, context.world, actorSpace);
        SableM31CreateActorTrace.resolution(context, owner, proposedRaw, resolution);
        if (resolution.target() == null) {
            ((ContactMovementBehaviour) (Object) this).deactivateLastVisitedContact(context);
            return;
        }

        try (SableCreateActorWorldContext.ParentInteractionScope ignored =
                     SableCreateActorWorldContext.enterParentInteractionSpace(context, owner)) {
            original.call(context, resolution.target());
        }
    }
}
