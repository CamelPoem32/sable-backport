package dev.ryanhcode.sable.mixin.compatibility.create.behaviour_compatibility.block_breaking_behaviour;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.saw.SawMovementBehaviour;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableCreateActorWorldContext;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** Keeps native Create tree cutting while making overflow drops parent-visible. */
@Mixin(value = SawMovementBehaviour.class, remap = false)
public class SawMovementBehaviourMixin {
    @WrapMethod(method = "dropItemFromCutTree")
    public void sable$dropCutTreeItemInParentSpace(final MovementContext context,
                                                   final BlockPos dropPos,
                                                   final ItemStack stack,
                                                   final Operation<Void> original) {
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null) {
            original.call(context, dropPos, stack);
            return;
        }
        try (SableCreateActorWorldContext.ParentSpaceScope ignored =
                     SableCreateActorWorldContext.enterParentSpace(context, owner)) {
            original.call(context, dropPos, stack);
        }
    }
}
