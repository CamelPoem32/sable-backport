package dev.ryanhcode.sable.physics.impl.rapier.constraint.rotary;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;
import dev.ryanhcode.sable.diagnostic.RotaryPipelineTraceRegistry;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.physics.impl.rapier.constraint.RapierConstraintHandle;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public class RapierRotaryConstraintHandle extends RapierConstraintHandle implements RotaryConstraintHandle {
    /**
     * Creates a rapier constraint handle
     */
    public static RapierRotaryConstraintHandle create(final ServerLevel serverLevel, @Nullable final PhysicsPipelineBody bodyA, @Nullable final PhysicsPipelineBody bodyB, final RotaryConstraintConfiguration config) {
        final long sceneHandle = Rapier3D.getSceneHandle(serverLevel);
        final Vector3d anchorA = bodyA == null || bodyA.getMassTracker().getCenterOfMass() == null
                ? new Vector3d(config.pos1())
                : config.pos1().sub(bodyA.getMassTracker().getCenterOfMass(), new Vector3d());
        final Vector3d anchorB = bodyB == null || bodyB.getMassTracker().getCenterOfMass() == null
                ? new Vector3d(config.pos2())
                : config.pos2().sub(bodyB.getMassTracker().getCenterOfMass(), new Vector3d());
        final Vector3d axisA = config.normal1().normalize(new Vector3d());
        final Vector3d axisB = config.normal2().normalize(new Vector3d());
        final double axisDot = axisA.dot(axisB);

        Sable.LOGGER.info("SABLE_ROTARY_BACKEND_CONSTRUCT_JAVA"
                        + " bodyAHandle={} bodyBHandle={}"
                        + " publicRawAnchorA={} publicRawAnchorB={}"
                        + " convertedLocalAnchorA={} convertedLocalAnchorB={}"
                        + " finalRapierLocalAnchorA={} finalRapierLocalAnchorB={}"
                        + " publicAxisA={} publicAxisB={}"
                        + " convertedLocalAxisA={} convertedLocalAxisB={}"
                        + " finalRapierAxisA={} finalRapierAxisB={}"
                        + " axisDot={} antiParallelAxesConverted=at_native_rotary_boundary"
                        + " bodyAMass={} bodyBMass={} bodyAInertia={} bodyBInertia={}",
                bodyA == null ? -1 : Rapier3D.getID(bodyA),
                bodyB == null ? -1 : Rapier3D.getID(bodyB),
                config.pos1(), config.pos2(), anchorA, anchorB, anchorA, anchorB,
                config.normal1(), config.normal2(), axisA, axisB, axisA, axisB,
                axisDot,
                bodyA == null ? "static_world" : bodyA.getMassTracker().getMass(),
                bodyB == null ? "static_world" : bodyB.getMassTracker().getMass(),
                bodyA == null ? "static_world" : bodyA.getMassTracker().getInertiaTensor(),
                bodyB == null ? "static_world" : bodyB.getMassTracker().getInertiaTensor());

        final long handle = Rapier3D.addRotaryConstraint(
                sceneHandle,
                bodyA == null ? -1 :  Rapier3D.getID(bodyA),
                bodyB == null ? -1 :  Rapier3D.getID(bodyB),
                config.pos1().x(),
                config.pos1().y(),
                config.pos1().z(),
                config.pos2().x(),
                config.pos2().y(),
                config.pos2().z(),
                config.normal1().x(),
                config.normal1().y(),
                config.normal1().z(),
                config.normal2().x(),
                config.normal2().y(),
                config.normal2().z()
        );
        RotaryPipelineTraceRegistry.attachConstraintHandle(serverLevel,
                bodyA == null ? -1 : Rapier3D.getID(bodyA),
                bodyB == null ? -1 : Rapier3D.getID(bodyB),
                handle);

        return new RapierRotaryConstraintHandle(sceneHandle, handle);
    }

    /**
     * Creates a new constraint handle
     *
     * @param sceneHandle the scene ID that this constraint is in
     * @param handle the handle from the physics engine
     */
    public RapierRotaryConstraintHandle(final long sceneHandle, final long handle) {
        super(sceneHandle, handle);
    }
}
