package dev.ryanhcode.sable.compatibility.create.block_breakers;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Shared coordinate ownership helpers for Create actors hosted by a Sable body. */
public final class SableCreateActorWorldContext {
    private SableCreateActorWorldContext() {
    }

    public static @Nullable SubLevel owner(final MovementContext context) {
        if (context.contraption == null || context.contraption.entity == null) {
            return null;
        }
        return Sable.HELPER.getContaining(context.world, context.contraption.anchor);
    }

    public static CreateActorTargetGeometry.ActorSpace resolveActivePoint(final MovementContext context,
                                                                          final SubLevel owner,
                                                                          final Vec3 storageDirection) {
        final Vec3 storagePoint = context.position != null
                ? context.position
                : context.contraption.entity.toGlobalVector(context.localPos.getCenter(), 1.0F);
        return CreateActorTargetGeometry.resolvePoint(owner.logicalPose(), storagePoint, storageDirection);
    }

    /**
     * Temporarily exposes parent-visible position and velocity to native Create drop code.
     * World ownership is unchanged and the raw context is restored on close.
     */
    public static ParentSpaceScope enterParentSpace(final MovementContext context, final SubLevel owner) {
        final Vec3 rawPosition = context.position;
        final Vec3 rawMotion = context.motion;
        final Vec3 rawRelativeMotion = context.relativeMotion;

        if (rawPosition != null) {
            final Vec3 visiblePosition = owner.logicalPose().transformPosition(rawPosition);
            context.position = visiblePosition;
            if (rawMotion != null) {
                final Vec3 previousRawPosition = rawPosition.subtract(rawMotion);
                final Vec3 previousVisiblePosition = owner.lastPose().transformPosition(previousRawPosition);
                context.motion = visiblePosition.subtract(previousVisiblePosition);
            }
        }
        if (rawRelativeMotion != null) {
            context.relativeMotion = owner.logicalPose().transformNormal(rawRelativeMotion);
        }
        return new ParentSpaceScope(context, rawPosition, rawMotion, rawRelativeMotion);
    }

    public static final class ParentSpaceScope implements AutoCloseable {
        private final MovementContext context;
        private final Vec3 rawPosition;
        private final Vec3 rawMotion;
        private final Vec3 rawRelativeMotion;
        private boolean closed;

        private ParentSpaceScope(final MovementContext context,
                                 @Nullable final Vec3 rawPosition,
                                 @Nullable final Vec3 rawMotion,
                                 @Nullable final Vec3 rawRelativeMotion) {
            this.context = context;
            this.rawPosition = rawPosition;
            this.rawMotion = rawMotion;
            this.rawRelativeMotion = rawRelativeMotion;
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            this.context.position = this.rawPosition;
            this.context.motion = this.rawMotion;
            this.context.relativeMotion = this.rawRelativeMotion;
        }
    }
}
