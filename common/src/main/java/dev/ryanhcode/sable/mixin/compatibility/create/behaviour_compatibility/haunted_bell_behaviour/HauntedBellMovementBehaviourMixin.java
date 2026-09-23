package dev.ryanhcode.sable.mixin.compatibility.create.behaviour_compatibility.haunted_bell_behaviour;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.equipment.bell.HauntedBellMovementBehaviour;
import dev.ryanhcode.sable.compatibility.create.block_breakers.CreateActorTargetGeometry;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableCreateActorWorldContext;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableM31CreateActorTrace;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SubLevelBlockBreakingUtility;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/** Keeps the moving bell's pulse chunk and packet position in parent-visible space. */
@Mixin(value = HauntedBellMovementBehaviour.class, remap = false)
public class HauntedBellMovementBehaviourMixin {
    @WrapMethod(method = "visitNewPosition")
    public void sable$pulseAtParentPosition(final MovementContext context,
                                            final BlockPos proposedRaw,
                                            final Operation<Void> original) {
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null || context.world.isClientSide) {
            original.call(context, proposedRaw);
            return;
        }

        final CreateActorTargetGeometry.ActorSpace actorSpace =
                SableCreateActorWorldContext.resolveActivePoint(context, owner, Vec3.ZERO);
        final SubLevelBlockBreakingUtility.TargetResolution resolution =
                SubLevelBlockBreakingUtility.findExternalPointTarget(owner, context.world, actorSpace);
        SableM31CreateActorTrace.resolution(context, owner, proposedRaw, resolution);
        if (resolution.target() != null) {
            original.call(context, resolution.target());
        }
    }
}
