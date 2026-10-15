package dev.ryanhcode.sable.mixin.compatibility.create.behaviour_compatibility.sliding_door;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorMovementBehaviour;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableCreateActorWorldContext;
import dev.ryanhcode.sable.compatibility.create.sliding_door.SableSlidingDoorTarget;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only tickOpen's adjacent-world segment is bridged; local toggle/controller decisions stay native. */
@Mixin(value = SlidingDoorMovementBehaviour.class, remap = false)
public class SlidingDoorMovementBehaviourMixin {
    @WrapOperation(method = "tickOpen", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/decoration/slidingDoor/SlidingDoorMovementBehaviour;getDoorFacing(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;)Lnet/minecraft/core/Direction;"), require = 1, allow = 1)
    private Direction sable$physicalOutward(final SlidingDoorMovementBehaviour self, final MovementContext context,
                                            final Operation<Direction> original) {
        final Direction inner = original.call(self, context);
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null || context.world.isClientSide) return inner;
        final var result = SableSlidingDoorTarget.resolve(context, owner, inner);
        return result.accepted() ? result.facing() : inner;
    }

    @WrapOperation(method = "tickOpen", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/core/BlockPos;containing(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;"), require = 2, allow = 2)
    private BlockPos sable$physicalCell(final Position point, final Operation<BlockPos> original,
                                       final MovementContext context, final boolean currentlyOpen) {
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null || context.world.isClientSide) return original.call(point);
        // Even rejected orientations never send a raw cell to an external query/event.
        return original.call(owner.logicalPose().transformPosition(context.position));
    }

    @WrapOperation(method = "tickOpen", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"), require = 1, allow = 1)
    private BlockState sable$parentDoor(final Level level, final BlockPos target, final Operation<BlockState> original,
                                        final MovementContext context, final boolean currentlyOpen) {
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null || level.isClientSide) return original.call(level, target);
        final var decision = SableSlidingDoorTarget.validate(context, owner, target);
        if (decision != SableSlidingDoorTarget.Decision.PARENT_TARGET) {
            SableSlidingDoorTarget.rejected(context, decision);
            return Blocks.AIR.defaultBlockState();
        }
        return original.call(level, target);
    }

    @WrapOperation(method = "tickOpen", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/block/DoorBlock;setOpen(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Z)V"), require = 1, allow = 1)
    private void sable$observeNativeMutation(final DoorBlock door, final Entity entity, final Level level,
                                             final BlockState state, final BlockPos target, final boolean open,
                                             final Operation<Void> original, final MovementContext context,
                                             final boolean currentlyOpen) {
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null || level.isClientSide) {
            original.call(door, entity, level, state, target, open);
            return;
        }
        final var decision = SableSlidingDoorTarget.validate(context, owner, target);
        if (decision != SableSlidingDoorTarget.Decision.PARENT_TARGET) {
            SableSlidingDoorTarget.rejected(context, decision);
            return;
        }
        SableSlidingDoorTarget.trace(context, "MUTATION_ATTEMPT");
        original.call(door, entity, level, state, target, open);
        SableSlidingDoorTarget.committed(context, target, state, level.getBlockState(target));
    }
}
