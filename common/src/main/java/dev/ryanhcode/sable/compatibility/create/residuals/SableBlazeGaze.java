package dev.ryanhcode.sable.compatibility.create.residuals;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Client native-gaze evidence; no camera/animation mutation and no fixture dependency. */
public final class SableBlazeGaze {
    private static final Map<MovementContext, Sample> SAMPLES = new WeakHashMap<>();
    private SableBlazeGaze() { }

    public record Sample(UUID vehicle, UUID owner, long gameTime, Vec3 raw, Vec3 visible, Vec3 camera,
                         Vec3 physicalGaze, Vec3 localGaze, Pose3dc pose, float nativeAngle, double directionDot) { }

    public static synchronized Sample sample(final MovementContext context) { return SAMPLES.get(context); }

    public static Vec3 prepare(final MovementContext context, final SubLevel owner, final Vec3 camera,
                               final Vec3 rawActor) {
        // Native getTargetAngle is tick-based and uses reverseRotation(..., 1), not a render-frame callback.
        final Pose3dc pose = new Pose3d().set(owner instanceof ClientSubLevel client ? client.renderPose(1) : owner.logicalPose());
        final Vec3 visible = pose.transformPosition(rawActor);
        final Vec3 physical = camera.subtract(visible);
        final Vec3 sableVector = ResidualActorGeometry.inverseRotate(pose, physical);
        final Vec3 local = context.contraption.entity.reverseRotation(sableVector, 1);
        synchronized (SableBlazeGaze.class) {
            SAMPLES.put(context, new Sample(context.contraption.entity.getUUID(), owner.getUniqueId(),
                    context.world.getGameTime(), rawActor, visible, camera, physical, local, pose, Float.NaN, Double.NaN));
        }
        return sableVector;
    }

    public static synchronized void completed(final MovementContext context, final float nativeAngle) {
        final Sample sample = SAMPLES.get(context);
        if (sample == null || sample.gameTime != context.world.getGameTime()) return;
        final double radians = Math.toRadians(-nativeAngle - 90);
        final Vec3 direction = new Vec3(Math.cos(radians), 0, Math.sin(radians));
        final Vec3 expected = new Vec3(sample.localGaze.x, 0, sample.localGaze.z).normalize();
        final double dot = direction.dot(expected);
        SAMPLES.put(context, new Sample(sample.vehicle, sample.owner, sample.gameTime, sample.raw, sample.visible,
                sample.camera, sample.physicalGaze, sample.localGaze, sample.pose, nativeAngle, dot));
        if (SableDiagnosticFlags.TRACE_CREATE_ACTORS && Math.floorMod(sample.gameTime, 100) == 0) {
            Sable.LOGGER.info("SABLE_M43_BLAZE_GAZE actorLocal={} rawActorPosition={} visibleActorPosition={} "
                            + "cameraPosition={} rawMixedVector=SUPPRESSED_MIXED_FRAME physicalGazeVector={} "
                            + "normalizedPhysicalGaze={} nativeAngle={} directionDot={} outerPose={} decision=NATIVE_GAZE",
                    context.localPos, sample.raw, sample.visible, sample.camera, sample.physicalGaze,
                    sample.physicalGaze.normalize(), nativeAngle, dot, sample.pose);
        }
    }
}
