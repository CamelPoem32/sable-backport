package dev.ryanhcode.sable.compatibility.create.block_breakers;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Bounded, transition-only diagnostics for M31 external Create actors. */
public final class SableM31CreateActorTrace {
    private static final Map<MovementContext, TraceState> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    private SableM31CreateActorTrace() {
    }

    public static void resolution(final MovementContext context,
                                  final SubLevel owner,
                                  final BlockPos proposedRaw,
                                  final SubLevelBlockBreakingUtility.TargetResolution resolution) {
        if (!SableDiagnosticFlags.TRACE_CREATE_ACTORS) {
            return;
        }

        final TraceState state = STATES.computeIfAbsent(context, ignored -> new TraceState());
        if (!state.discovered) {
            state.discovered = true;
            log("ACTOR_DISCOVERED", context, owner, proposedRaw, resolution, "decision=SEMANTIC_CREATE_ACTOR");
        }

        final TargetKey key = new TargetKey(resolution.target(), resolution.decision());
        if (key.equals(state.target)) {
            return;
        }

        final String event = state.target == null ? "TARGET_RESOLVED" : "TARGET_CHANGED";
        state.target = key;
        log(event, context, owner, proposedRaw, resolution, "decision=" + resolution.decision());
    }

    public static void progressStarted(final MovementContext context,
                                       final SubLevel owner,
                                       final BlockPos target,
                                       final SubLevelBlockBreakingUtility.TargetResolution resolution) {
        if (SableDiagnosticFlags.TRACE_CREATE_ACTORS) {
            log("BREAK_PROGRESS_STARTED", context, owner, target, resolution,
                    "decision=CREATE_BREAKING_POS_SET progress=" + context.data.getInt("Progress"));
        }
    }

    public static void progressReset(final MovementContext context,
                                     final SubLevel owner,
                                     final BlockPos previous,
                                     final SubLevelBlockBreakingUtility.TargetResolution resolution) {
        if (SableDiagnosticFlags.TRACE_CREATE_ACTORS) {
            log("BREAK_PROGRESS_RESET", context, owner, previous, resolution,
                    "decision=PHYSICAL_PARENT_TARGET_CHANGED");
        }
    }

    public static void mutation(final String event,
                                final MovementContext context,
                                final SubLevel owner,
                                final BlockPos target,
                                final BlockState state,
                                final String decision,
                                final CreateActorTargetGeometry.ActorSpace actorSpace) {
        if (!SableDiagnosticFlags.TRACE_CREATE_ACTORS) {
            return;
        }
        log(event, context, owner, target,
                new SubLevelBlockBreakingUtility.TargetResolution(target, state, actorSpace,
                        SubLevelBlockBreakingUtility.Decision.PARENT_TARGET_RESOLVED),
                "decision=" + decision);
    }

    public static void action(final String event,
                              final MovementContext context,
                              final SubLevel owner,
                              final BlockPos target,
                              final BlockState state,
                              final String decision,
                              final CreateActorTargetGeometry.ActorSpace actorSpace) {
        mutation(event, context, owner, target, state, decision, actorSpace);
    }

    private static void log(final String event,
                            final MovementContext context,
                            final SubLevel owner,
                            final BlockPos proposedRaw,
                            final SubLevelBlockBreakingUtility.TargetResolution resolution,
                            final String detail) {
        final Direction localFacing = localFacing(context.state);
        Sable.LOGGER.info("SABLE_M31_CREATE_ACTOR event={} side={} gameTime={} actorType={} "
                        + "subLevel={} actorLocal={} rawPosition={} visibleParentPosition={} localFacing={} "
                        + "visibleFacing={} proposedRaw={} resolvedParentTarget={} targetState={} {}",
                event,
                context.world.isClientSide ? "CLIENT" : "SERVER",
                context.world.getGameTime(),
                actorType(context.state),
                owner.getUniqueId(),
                format(context.localPos),
                format(resolution.actorSpace().storageCenter()),
                format(resolution.actorSpace().visibleCenter()),
                localFacing,
                format(resolution.actorSpace().visibleDirection()),
                format(proposedRaw),
                format(resolution.target()),
                BuiltInRegistries.BLOCK.getKey(resolution.targetState().getBlock()),
                detail);
    }

    private static String actorType(final BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().toUpperCase(java.util.Locale.ROOT);
    }

    private static @Nullable Direction localFacing(final BlockState state) {
        for (final Property<?> property : state.getProperties()) {
            if (!"facing".equals(property.getName())) {
                continue;
            }
            final Comparable<?> value = state.getValue(property);
            if (value instanceof final Direction direction) {
                return direction;
            }
        }
        return null;
    }

    private static String format(@Nullable final BlockPos pos) {
        return pos == null ? "none" : "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    private static String format(final net.minecraft.world.phys.Vec3 vec) {
        return String.format(java.util.Locale.ROOT, "(%.4f,%.4f,%.4f)", vec.x, vec.y, vec.z);
    }

    private static final class TraceState {
        private boolean discovered;
        private TargetKey target;
    }

    private record TargetKey(@Nullable BlockPos target, SubLevelBlockBreakingUtility.Decision decision) {
        private TargetKey {
            Objects.requireNonNull(decision, "decision");
        }
    }
}
