package dev.ryanhcode.sable.compatibility.create.block_breakers;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Coordinate-only portion of external Create actor target resolution. */
public final class CreateActorTargetGeometry {
    private static final double HEAD_INSET = 2.0 / 16.0;
    private static final double HEAD_OFFSET = 12.0 / 16.0 - HEAD_INSET;

    private CreateActorTargetGeometry() {
    }

    public static ActorSpace resolve(final Pose3dc ownerPose,
                                     final Vec3 actorStorageCenter,
                                     final Vec3 storageDirection) {
        final BoundingBox3d storageMiningBounds = storageMiningBounds(actorStorageCenter, storageDirection);
        final BoundingBox3d visibleMiningBounds = new BoundingBox3d(storageMiningBounds);
        visibleMiningBounds.transform(ownerPose, visibleMiningBounds);
        return new ActorSpace(
                actorStorageCenter,
                ownerPose.transformPosition(actorStorageCenter),
                storageDirection,
                ownerPose.transformNormal(storageDirection).normalize(),
                visibleMiningBounds);
    }

    public static ActorSpace resolvePoint(final Pose3dc ownerPose,
                                          final Vec3 actorStoragePoint,
                                          final Vec3 storageDirection) {
        final Vec3 visiblePoint = ownerPose.transformPosition(actorStoragePoint);
        return new ActorSpace(
                actorStoragePoint,
                visiblePoint,
                storageDirection,
                ownerPose.transformNormal(storageDirection).normalize(),
                new BoundingBox3d(new AABB(visiblePoint, visiblePoint)));
    }

    /** Resolves the complete ray geometry used by Create's moving Deployer. */
    public static DeployerSpace resolveDeployer(final Pose3dc ownerPose,
                                                final Vec3 activeStoragePoint,
                                                final Vec3 storageDirection) {
        final ActorSpace actorSpace = resolvePoint(ownerPose, activeStoragePoint, storageDirection);
        final Vec3 visibleDirection = actorSpace.visibleDirection();
        final Vec3 fakePlayerPosition = actorSpace.visibleCenter().subtract(visibleDirection.scale(2.0));
        final Vec3 rayStart = fakePlayerPosition.add(visibleDirection.scale(1.515625));
        final Vec3 rayEnd = fakePlayerPosition.add(visibleDirection.scale(2.484375));
        final Direction fallbackClickedFace = Direction.getNearest(
                visibleDirection.x, visibleDirection.y, visibleDirection.z).getOpposite();
        return new DeployerSpace(actorSpace, fakePlayerPosition, rayStart, rayEnd,
                BlockPos.containing(actorSpace.visibleCenter()), fallbackClickedFace);
    }

    public static BoundingBox3d storageMiningBounds(final Vec3 actorStorageCenter,
                                                    final Vec3 storageDirection) {
        return new BoundingBox3d(new AABB(
                actorStorageCenter.x - 0.5,
                actorStorageCenter.y - 0.5,
                actorStorageCenter.z - 0.5,
                actorStorageCenter.x + 0.5,
                actorStorageCenter.y + 0.5,
                actorStorageCenter.z + 0.5)
                .inflate(-HEAD_INSET)
                .move(storageDirection.scale(HEAD_OFFSET)));
    }

    public record ActorSpace(Vec3 storageCenter,
                             Vec3 visibleCenter,
                             Vec3 storageDirection,
                             Vec3 visibleDirection,
                             BoundingBox3d visibleMiningBounds) {
    }

    public record DeployerSpace(ActorSpace actorSpace,
                                Vec3 fakePlayerPosition,
                                Vec3 rayStart,
                                Vec3 rayEnd,
                                BlockPos target,
                                Direction fallbackClickedFace) {
    }
}
