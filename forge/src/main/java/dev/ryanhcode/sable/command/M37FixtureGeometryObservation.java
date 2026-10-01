package dev.ryanhcode.sable.command;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Fixture-only observation of a Create actor after its first native actor tick. */
public final class M37FixtureGeometryObservation {
    private M37FixtureGeometryObservation() {
    }

    public static Optional<ActorPose> captureActorPose(final Pose3dc pose, final Vec3 rawPosition,
                                                       final Vec3 activeOffset) {
        if (rawPosition == null) {
            return Optional.empty();
        }
        final Vec3 visible = pose.transformPosition(rawPosition);
        return Optional.of(new ActorPose(visible, BlockPos.containing(visible.subtract(activeOffset))));
    }

    public static Phase nextPhase(final Phase previous, final boolean movingContraptionPresent,
                                  final boolean actorPositionPresent, final boolean relevantItemPresent) {
        if (previous == Phase.CAPTURED) {
            return previous;
        }
        if (!movingContraptionPresent) {
            return Phase.WAIT_FOR_MOVING_CONTRAPTION;
        }
        if (!actorPositionPresent) {
            return Phase.WAIT_FOR_ACTOR_POSITION;
        }
        return relevantItemPresent ? Phase.CAPTURED : Phase.WAIT_FOR_RELEVANT_ITEM_ENTITY;
    }

    public static boolean stationaryItemPrecedesQuery(final long spawnTime, final long queryTime,
                                                       final int candidateCount) {
        return spawnTime >= 0 && queryTime > spawnTime && candidateCount > 0;
    }

    public static boolean actorPositionUnavailable(final Vec3 lastValidPosition, final int age) {
        return age >= 400 && lastValidPosition == null;
    }

    public record ActorPose(Vec3 visibleActivePoint, BlockPos visibleFunnelBlock) {
    }

    public enum Phase {
        WAIT_FOR_MOVING_CONTRAPTION,
        WAIT_FOR_ACTOR_POSITION,
        WAIT_FOR_RELEVANT_ITEM_ENTITY,
        CAPTURED
    }
}
