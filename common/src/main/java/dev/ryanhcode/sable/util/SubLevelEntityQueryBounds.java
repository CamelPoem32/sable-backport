package dev.ryanhcode.sable.util;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Coordinate-space conversions shared by entity-query call sites. */
public final class SubLevelEntityQueryBounds {
    private SubLevelEntityQueryBounds() {
    }

    /** Builds a parent-visible ray box even when one or both endpoints came from a raw Sable plot hit. */
    public static AABB parentVisibleRay(final Level level, final Vec3 first, final Vec3 second) {
        return new AABB(toParentVisible(level, first), toParentVisible(level, second));
    }

    public static Vec3 toParentVisible(final Level level, final Vec3 position) {
        final SubLevel subLevel = Sable.HELPER.getContaining(level, position);
        return subLevel == null ? position : pose(level, subLevel).transformPosition(position);
    }

    public static Pose3dc pose(final Level level, final SubLevel subLevel) {
        if (level instanceof final LevelPoseProviderExtension extension) {
            return extension.sable$getPose(subLevel);
        }
        return subLevel.logicalPose();
    }
}
