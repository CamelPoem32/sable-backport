package dev.ryanhcode.sable.compatibility.create.residuals;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** M43: positions include the pose origin; motion vectors deliberately do not include scale or translation. */
public final class ResidualActorGeometry {
    private ResidualActorGeometry() { }

    public static Vec3 rotate(final Pose3dc pose, final Vec3 motion) {
        final Vector3d v = pose.orientation().transform(new Vector3d(motion.x, motion.y, motion.z));
        return new Vec3(v.x, v.y, v.z);
    }

    public static Vec3 inverseRotate(final Pose3dc pose, final Vec3 motion) {
        final Vector3d v = pose.orientation().transformInverse(new Vector3d(motion.x, motion.y, motion.z));
        return new Vec3(v.x, v.y, v.z);
    }

    /** Reference formula only. Runtime mutation remains in Create's original throwEntity. */
    public static Vec3 nativeBoost(final Vec3 parentMotion) {
        final Vec3 boost = parentMotion.add(0, parentMotion.length() / 4, 0);
        return boost.length() > 4 ? boost.normalize().scale(4) : boost;
    }
}
