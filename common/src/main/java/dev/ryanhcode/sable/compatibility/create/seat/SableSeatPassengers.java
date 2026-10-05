package dev.ryanhcode.sable.compatibility.create.seat;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Coordinates only. Create retains the seat mapping and vanilla retains the riding relation. */
public final class SableSeatPassengers {
    private static final Map<Entity, Long> LAST_TRACE = Collections.synchronizedMap(new WeakHashMap<>());

    private SableSeatPassengers() {
    }

    public static @Nullable SubLevel owner(final Entity vehicle) {
        if (!(vehicle instanceof ControlledContraptionEntity entity) || entity.getContraption() == null
                || entity.getContraption().getSeats().isEmpty()) return null;
        return SableCreateContraptionContext.getContainingSubLevel(entity);
    }

    public static boolean parentPassenger(final Entity vehicle, final Entity passenger) {
        return owner(vehicle) != null && passenger instanceof LivingEntity
                && passenger.level() == vehicle.level() && EntitySubLevelUtil.shouldKick(passenger)
                && Sable.HELPER.getContaining(passenger) == null;
    }

    public static boolean riding(final Entity passenger) {
        return passenger.getVehicle() != null && parentPassenger(passenger.getVehicle(), passenger);
    }

    public static boolean canMount(final Entity vehicle, final Entity passenger) {
        if (!parentPassenger(vehicle, passenger)) return false;
        if (passenger.getVehicle() != null && passenger.getVehicle() != vehicle) return false;
        // Before boarding, retain semantic ownership of anyone standing on another body.
        // Already-seated riders must not query their tracking getter here (it delegates to riding).
        if (passenger.getVehicle() == vehicle) return true;
        final SubLevel tracking = Sable.HELPER.getTrackingSubLevel(passenger);
        return tracking == null || tracking == owner(vehicle);
    }

    public static @Nullable Vec3 physicalPosition(final AbstractContraptionEntity vehicle,
                                                   final Entity passenger, final @Nullable Vec3 nativePosition, final float partialTicks) {
        if (nativePosition == null || !parentPassenger(vehicle, passenger)
                || vehicle.getContraption().getSeatOf(passenger.getUUID()) == null) return nativePosition;
        // Create transforms a point at passenger height, then subtracts that height in world-up.
        final double height = passenger.getBoundingBox().getYsize();
        final SubLevel body = owner(vehicle);
        final Pose3dc pose = body instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel client
                ? client.renderPose(partialTicks) : body.logicalPose();
        final Vec3 visible = physicalPosition(pose, nativePosition, height);
        trace(vehicle, passenger, "TRANSFORM_RESOLVED", visible);
        return visible;
    }

    public static Vec3 physicalPosition(final Pose3dc pose, final Vec3 nativePosition, final double height) {
        return pose.transformPosition(nativePosition.add(0, height, 0)).subtract(0, height, 0);
    }

    public static Vec3 visibleSeat(final AbstractContraptionEntity vehicle, final BlockPos local) {
        final Vec3 raw = vehicle.toGlobalVector(Vec3.atCenterOf(local), 1);
        final SubLevel body = owner(vehicle);
        return body == null ? raw : body.logicalPose().transformPosition(raw);
    }

    public static AABB visibleBounds(final AbstractContraptionEntity vehicle) {
        final SubLevel body = owner(vehicle);
        final AABB raw = vehicle.getBoundingBox();
        if (body == null) return raw;
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int corner = 0; corner < 8; corner++) {
            final Vec3 p = body.logicalPose().transformPosition(new Vec3(
                    (corner & 1) == 0 ? raw.minX : raw.maxX,
                    (corner & 2) == 0 ? raw.minY : raw.maxY,
                    (corner & 4) == 0 ? raw.minZ : raw.maxZ));
            minX = Math.min(minX, p.x); minY = Math.min(minY, p.y); minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y); maxZ = Math.max(maxZ, p.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static Vec3 dismount(final AbstractContraptionEntity vehicle, final LivingEntity passenger,
                                final Vec3 nativeResult) {
        final SubLevel body = owner(vehicle);
        if (body == null || !parentPassenger(vehicle, passenger)) return nativeResult;
        // removePassenger has already removed the mapping; its native NBT location is now physical.
        final Vec3 center = Sable.HELPER.getContaining(passenger.level(), nativeResult) == null
                ? nativeResult : passenger.position();
        final BlockPos base = BlockPos.containing(center);
        final AABB payload = visibleBounds(vehicle);
        for (int radius = 1; radius <= 8; radius++) {
            for (int dy = 1; dy >= -6; dy--) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        final BlockPos cell = base.offset(dx, dy, dz);
                        if (!passenger.level().hasChunkAt(cell)
                                || Sable.HELPER.getContaining(passenger.level(), cell) != null) continue;
                        final Vec3 safe = DismountHelper.findSafeDismountLocation(passenger.getType(),
                                passenger.level(), cell, true);
                        if (safe == null) continue;
                        final AABB box = passenger.getDimensions(Pose.STANDING).makeBoundingBox(safe);
                        if (box.intersects(payload) || !DismountHelper.canDismountTo(passenger.level(), passenger, box)
                                || dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper.canFallAtleastWithSubLevels(passenger.level(), box) != null) continue;
                        trace(vehicle, passenger, "DISMOUNT_RESOLVED", safe);
                        return safe;
                    }
                }
            }
        }
        // Native-style fallback when no supported floor exists: remain at the physical passenger position.
        trace(vehicle, passenger, "DISMOUNT_NO_SAFE_FLOOR", passenger.position());
        return passenger.position();
    }

    public static void detach(final AbstractContraptionEntity vehicle) {
        if (owner(vehicle) == null || vehicle.level().isClientSide) return;
        for (final Entity passenger : List.copyOf(vehicle.getPassengers())) {
            if (!parentPassenger(vehicle, passenger)) continue;
            passenger.stopRiding();
            // Forge can veto a dismount. Teardown must not remove a still-occupied vehicle.
            if (passenger.getVehicle() == vehicle) throw new IllegalStateException("Seat dismount vetoed during teardown");
            trace(vehicle, passenger, "DISMOUNT_SUCCESS", passenger.position());
        }
    }

    public static void detachOuter(final ServerSubLevel body) {
        for (final Entity entity : body.getLevel().getAllEntities()) {
            if (entity instanceof AbstractContraptionEntity vehicle && owner(vehicle) == body) detach(vehicle);
        }
    }

    public static void trace(final Entity vehicle, final Entity passenger, final String stage, final Vec3 visible) {
        if (!SableDiagnosticFlags.TRACE_CREATE_ACTORS) return;
        final long time = vehicle.level().getGameTime();
        if (stage.equals("TRANSFORM_RESOLVED") && time - LAST_TRACE.getOrDefault(passenger, time - 40) < 40) return;
        LAST_TRACE.put(passenger, time);
        final BlockPos seat = vehicle instanceof AbstractContraptionEntity contraption && contraption.getContraption() != null
                ? contraption.getContraption().getSeatOf(passenger.getUUID()) : null;
        Sable.LOGGER.info("SABLE_M39_SEAT event={} passenger={} vehicle={} subLevel={} passengerLevel={} vehicleLevel={} seatLocal={} visiblePosition={} passengerPosition={} hiddenPlotPosition={}",
                stage, passenger.getUUID(), vehicle.getUUID(), owner(vehicle), passenger.level().getClass().getSimpleName(),
                vehicle.level().getClass().getSimpleName(), seat, visible, passenger.position(), Sable.HELPER.getContaining(passenger) != null);
    }
}
