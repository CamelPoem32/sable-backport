package dev.ryanhcode.sable.mixin.compatibility.create.behaviour_compatibility.portable_interface;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceMovement;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlockEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableCreateActorWorldContext;
import dev.ryanhcode.sable.compatibility.create.portable_interface.SablePortableInterfaceTarget;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Presents only Sable-hosted portable-interface actors to Create in physical parent coordinates. */
@Mixin(value = PortableStorageInterfaceMovement.class, remap = false)
public abstract class PortableStorageInterfaceMovementMixin {
    @Unique
    private static final Map<MovementContext, SablePortableInterfaceTarget.Result> SABLE$LAST_TARGETS =
            Collections.synchronizedMap(new WeakHashMap<>());
    @Unique
    private static final Map<MovementContext, String> SABLE$LAST_TRACES =
            Collections.synchronizedMap(new WeakHashMap<>());

    @Shadow
    protected abstract boolean findInterface(MovementContext context, BlockPos pos);

    @Shadow
    public abstract void reset(MovementContext context);

    @WrapMethod(method = "visitNewPosition")
    private void sable$visitPhysicalInterface(final MovementContext context, final BlockPos rawVisit,
                                              final Operation<Void> original) {
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null) {
            original.call(context, rawVisit);
            return;
        }
        final SablePortableInterfaceTarget.Result target = SablePortableInterfaceTarget.resolve(context, owner);
        if (!target.accepted()) {
            this.reset(context);
            sable$record(context, owner, target, "CONNECT_REJECTED");
            return;
        }
        try (SableCreateActorWorldContext.ParentInteractionScope ignored =
                     SableCreateActorWorldContext.enterParentInteractionSpace(context, owner)) {
            original.call(context, target.parentBlock());
        }
        sable$record(context, owner, target, "CANDIDATE_RESOLVED");
    }

    @WrapMethod(method = "tick")
    private void sable$tickPhysicalInterface(final MovementContext context, final Operation<Void> original) {
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null) {
            original.call(context);
            return;
        }
        final SablePortableInterfaceTarget.Result target = SablePortableInterfaceTarget.resolve(context, owner);
        if (!target.accepted()) {
            this.reset(context);
            SABLE$LAST_TARGETS.remove(context);
            sable$record(context, owner, target, "CONNECT_REJECTED");
            return;
        }
        try (SableCreateActorWorldContext.ParentInteractionScope ignored =
                     SableCreateActorWorldContext.enterParentInteractionSpace(context, owner)) {
            if (!context.world.isClientSide
                    && (!target.equals(SABLE$LAST_TARGETS.get(context))
                    || !context.data.contains("WorkingPos"))) {
                if (!this.findInterface(context, target.parentBlock())) {
                    this.reset(context);
                }
            }
            original.call(context);
        }
        sable$record(context, owner, target, "CONNECTION_KEEPALIVE");
    }

    @Unique
    private static void sable$record(final MovementContext context, final SubLevel owner,
                                     final SablePortableInterfaceTarget.Result target, final String event) {
        final SablePortableInterfaceTarget.Result prior = target.accepted()
                ? SABLE$LAST_TARGETS.put(context, target) : SABLE$LAST_TARGETS.remove(context);
        if (!SableDiagnosticFlags.TRACE_CREATE_ACTORS) {
            return;
        }
        final BlockPos working = context.data.contains("WorkingPos")
                ? NbtUtils.readBlockPos(context.data.getCompound("WorkingPos")) : null;
        final PortableStorageInterfaceBlockEntity stationary = working != null
                && context.world.getBlockEntity(working) instanceof final PortableStorageInterfaceBlockEntity interfaceEntity
                ? interfaceEntity : null;
        final boolean transferring = stationary != null && stationary.canTransfer();
        final String trace = target.decision() + ":" + target.parentBlock() + ":" + target.parentFacing()
                + ":" + working + ":" + transferring;
        if (trace.equals(SABLE$LAST_TRACES.put(context, trace))) {
            return;
        }
        final String phase = transferring ? "CONNECT_SUCCESS" : working != null ? "CONNECT_ATTEMPT"
                : prior != null && !target.accepted() ? "DISCONNECT" : event;
        Sable.LOGGER.info("SABLE_M36_PSI event={} type={} subLevel={} actorLocal={} rawPosition={} "
                        + "parentBlock={} parentFacing={} workingPos={} canTransfer={} decision={}",
                phase, context.state.getBlock(), owner.getUniqueId(), context.localPos, context.position,
                target.parentBlock(), target.parentFacing(), working, transferring, target.decision());
    }
}
