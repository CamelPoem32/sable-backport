package dev.ryanhcode.sable.compatibility.create.residuals;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.actors.plough.PloughBlock;
import com.simibubi.create.content.kinetics.drill.DrillBlock;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Bounded read-only native-operation observations. No fixture identity or transfer/damage implementation. */
public final class SableActorEntityEvidence {
    private static final Map<MovementContext, ArrayDeque<Event>> EVENTS = new WeakHashMap<>();
    private SableActorEntityEvidence() { }

    public record Event(long gameTime, String operation, BlockPos cell, UUID entity,
                        Vec3 nativeMotion, Vec3 parentMotion, Vec3 beforeVelocity, Vec3 afterVelocity,
                        float healthBefore, float healthAfter, boolean accepted, boolean air, int candidates) { }

    public static synchronized List<Event> events(final MovementContext context) {
        final var events = EVENTS.get(context);
        return events == null ? List.of() : List.copyOf(events);
    }

    public static synchronized void clear(final MovementContext context) { EVENTS.remove(context); }

    public static float health(final Entity entity) { return entity instanceof LivingEntity living ? living.getHealth() : 0; }

    public static synchronized void record(final MovementContext context, final Event event) {
        final var events = EVENTS.computeIfAbsent(context, ignored -> new ArrayDeque<>());
        if (events.size() == 64) events.removeFirst();
        events.addLast(event);
        if (SableDiagnosticFlags.TRACE_CREATE_ACTORS && events.size() <= 8) {
            Sable.LOGGER.info("SABLE_M43_{}_ENTITY actorLocal={} rawPosition={} event={}",
                    context.state.getBlock() instanceof PloughBlock ? "PLOUGH"
                            : context.state.getBlock() instanceof DrillBlock ? "DRILL_AIR" : "SAW_AIR",
                    context.localPos, context.position, event);
        }
    }
}
