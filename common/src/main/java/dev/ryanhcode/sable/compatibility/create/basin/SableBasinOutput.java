package dev.ryanhcode.sable.compatibility.create.basin;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableCreateActorWorldContext;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Read-only native emission evidence. No inventory or entity creation is performed here. */
public final class SableBasinOutput {
    private static final Map<MovementContext, List<Spawn>> OUTPUTS = Collections.synchronizedMap(new WeakHashMap<>());

    private SableBasinOutput() { }

    public static boolean validPoint(final MovementContext context, final SubLevel owner) {
        if (context.position == null || context.rotation == null || !finite(context.position)) return false;
        final Vec3 visible = owner.logicalPose().transformPosition(context.position);
        return finite(visible) && context.world == owner.getLevel()
                && Sable.HELPER.getContaining(owner.getLevel(), visible) == null;
    }

    public static boolean finite(final Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    public static void observe(final MovementContext context, final Entity entity, final boolean added) {
        final SubLevel owner = SableCreateActorWorldContext.owner(context);
        if (owner == null || context.world.isClientSide || !(entity instanceof ItemEntity item)) return;
        // This call occurs inside native dump, while the Basin-only context scope is parent-visible.
        final Vec3 expected = context.position;
        final Vec3 raw = owner.logicalPose().transformPositionInverse(expected);
        final Vec3 velocity = context.rotation.apply(new Vec3(0, 1, 0)).scale(.05);
        final boolean parent = item.level() == owner.getLevel();
        final boolean hidden = Sable.HELPER.getContaining(item.level(), item.position()) != null;
        final Spawn evidence = new Spawn(item.getUUID(), owner.getUniqueId(), context.localPos,
                raw, expected, item.position(), item.getDeltaMovement(), velocity,
                item.getItem().getCount(), item.getItem().toString(), added, parent, hidden,
                owner.getLevel().getGameTime());
        synchronized (OUTPUTS) {
            final List<Spawn> records = OUTPUTS.computeIfAbsent(context, ignored -> new ArrayList<>());
            if (records.size() < 32) records.add(evidence);
        }
        if (SableDiagnosticFlags.TRACE_CREATE_ACTORS) {
            Sable.LOGGER.info("SABLE_M40_BASIN event=ITEM_SPAWN subLevel={} actorLocal={} rawPosition={} "
                            + "visiblePosition={} stack={} itemUuid={} actualSpawn={} velocity={} parentWorldEntity={} "
                            + "hiddenPlotSpawn={} added={} decision=NATIVE_CREATE_DUMP",
                    evidence.owner, evidence.local, raw, expected, evidence.stack, evidence.uuid,
                    evidence.actual, evidence.velocity, parent, hidden, added);
        }
    }

    public static List<Spawn> take(final MovementContext context) {
        synchronized (OUTPUTS) {
            final List<Spawn> records = OUTPUTS.remove(context);
            return records == null ? List.of() : List.copyOf(records);
        }
    }

    public static boolean nativeDumpDirection(final Vec3 up) {
        return finite(up) && Direction.getNearest(up.x, up.y, up.z) == Direction.DOWN;
    }

    public record Spawn(UUID uuid, UUID owner, net.minecraft.core.BlockPos local, Vec3 raw,
                        Vec3 expected, Vec3 actual, Vec3 velocity, Vec3 expectedVelocity,
                        int count, String stack, boolean added, boolean parent, boolean hidden, long gameTime) { }
}
