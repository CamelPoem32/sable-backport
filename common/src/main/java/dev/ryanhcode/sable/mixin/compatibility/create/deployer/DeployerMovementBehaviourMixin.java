package dev.ryanhcode.sable.mixin.compatibility.create.deployer;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.deployer.SubLevelDeployerInteractionUtility;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * M18: only Sable-contained moving Deployers get an adapted target position.
 * The wrapped Create method still performs shouldActivate, fake-player use,
 * fake-player punch progress, inventory handling, drops, and stall semantics.
 */
@Mixin(targets = "com.simibubi.create.content.kinetics.deployer.DeployerMovementBehaviour", remap = false)
public abstract class DeployerMovementBehaviourMixin implements MovementBehaviour {
    @WrapMethod(method = "visitNewPosition")
    private void sable$visitNewSableInteractionPosition(final MovementContext context,
                                                        final BlockPos pos,
                                                        final Operation<Void> original) {
        final SubLevel containingSubLevel = Sable.HELPER.getContaining(context.world, context.contraption.anchor);
        if (containingSubLevel == null) {
            original.call(context, pos);
            return;
        }

        final Vec3 actorCenter = context.contraption.entity.toGlobalVector(context.localPos.getCenter(), 1.0F);
        final Vec3 activeDirection = context.rotation.apply(this.getActiveAreaOffset(context).normalize());
        final BlockPos selectedPos = SubLevelDeployerInteractionUtility.findInteractionPos(containingSubLevel,
                context.world, activeDirection, actorCenter, pos, sable$isUseMode(context));
        original.call(context, selectedPos);
    }

    @Unique
    private static boolean sable$isUseMode(final MovementContext context) {
        return context.blockEntityData == null
                || !context.blockEntityData.contains("Mode")
                || !"PUNCH".equalsIgnoreCase(context.blockEntityData.getString("Mode"));
    }
}
