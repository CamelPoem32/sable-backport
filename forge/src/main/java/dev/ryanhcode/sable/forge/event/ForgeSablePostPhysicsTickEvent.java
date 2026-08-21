package dev.ryanhcode.sable.forge.event;

import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraftforge.eventbus.api.Event;

public final class ForgeSablePostPhysicsTickEvent extends Event {
    private final SubLevelPhysicsSystem physicsSystem;
    private final double timeStep;

    public ForgeSablePostPhysicsTickEvent(final SubLevelPhysicsSystem physicsSystem, final double timeStep) {
        this.physicsSystem = physicsSystem;
        this.timeStep = timeStep;
    }

    public SubLevelPhysicsSystem getPhysicsSystem() {
        return this.physicsSystem;
    }

    public double getTimeStep() {
        return this.timeStep;
    }
}
