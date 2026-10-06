package dev.ryanhcode.sable.mixin.compatibility.create.behaviour_compatibility.basin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.processing.basin.BasinMovementBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.basin.SableBasinOutput;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableCreateActorWorldContext;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only the Basin callback sees composed physical geometry; native stacks and dump remain untouched. */
@Mixin(value = BasinMovementBehaviour.class, remap = false)
public class BasinMovementBehaviourMixin {
    @WrapMethod(method = "tick")
    private void sable$physicalDump(final MovementContext context, final Operation<Void> original) {
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null) {
            original.call(context);
            return;
        }
        if (!SableBasinOutput.validPoint(context, owner)) return;
        try (SableCreateActorWorldContext.ParentInteractionScope ignored =
                     SableCreateActorWorldContext.enterParentInteractionSpace(context, owner)) {
            final boolean attempt = (context.temporaryData == null || Boolean.TRUE.equals(context.temporaryData))
                    && SableBasinOutput.nativeDumpDirection(context.rotation.apply(new net.minecraft.world.phys.Vec3(0, 1, 0)));
            if (attempt && SableDiagnosticFlags.TRACE_CREATE_ACTORS) {
                Sable.LOGGER.info("SABLE_M40_BASIN event=OUTPUT_ATTEMPT actorLocal={} subLevel={} visiblePosition={} decision=NATIVE_INVERSION",
                        context.localPos, owner.getUniqueId(), context.position);
            }
            original.call(context);
            if (attempt && SableDiagnosticFlags.TRACE_CREATE_ACTORS) {
                Sable.LOGGER.info("SABLE_M40_BASIN event=OUTPUT_COMMITTED actorLocal={} dumped={} decision=NATIVE_NBT_MUTATION",
                        context.localPos, Boolean.FALSE.equals(context.temporaryData));
            }
        }
    }

    @WrapOperation(method = "lambda$dump$1", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", remap = true))
    private static boolean sable$observeNativeSpawn(final Level level, final Entity entity,
                                                     final Operation<Boolean> original,
                                                     @Local(argsOnly = true) final MovementContext context) {
        final boolean added = original.call(level, entity);
        SableBasinOutput.observe(context, entity, added);
        return added;
    }
}
