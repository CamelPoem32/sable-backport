package dev.ryanhcode.sable.forge;

/** Deterministic lifecycle cases for M29.1's event-only sail visual classifier. */
public final class M29SailVisualLifecycleStateTest {
    private M29SailVisualLifecycleStateTest() {
    }

    public static void run() {
        final SailVisualLifecycleState healthy = new SailVisualLifecycleState();
        require(healthy.entityDiscovered(true) == SailVisualLifecycleState.Transition.ENTITY_DISCOVERED,
                "healthy target discovery failed");
        require(healthy.visualCreated() == SailVisualLifecycleState.Transition.VISUAL_CREATED,
                "healthy visual creation failed");
        require(healthy.geometrySubmitted(10L) == SailVisualLifecycleState.Transition.FIRST_GEOMETRY_SUBMITTED,
                "healthy first geometry failed");
        require(healthy.frame(40L, true) == SailVisualLifecycleState.Transition.NONE,
                "gap threshold must be exclusive");
        require(healthy.frame(41L, true) == SailVisualLifecycleState.Transition.SUSPECTED_VISUAL_GAP,
                "missing geometry gap was not classified");
        require(healthy.visualGap(), "confirmed visual gap was not retained for reassembly correlation");
        require(healthy.geometrySubmitted(42L)
                        == SailVisualLifecycleState.Transition.GEOMETRY_SUBMISSION_RESUMED,
                "geometry recovery was not classified");
        require(!healthy.visualGap(), "geometry recovery did not clear the visual gap");

        require(healthy.visualRemoved() == SailVisualLifecycleState.Transition.VISUAL_REMOVED,
                "visual removal was not classified");
        require(healthy.payload(false) == SailVisualLifecycleState.Transition.PAYLOAD_CHANGED,
                "payload loss was not classified");
        require(healthy.entityRemoved() == SailVisualLifecycleState.Transition.ENTITY_REMOVED,
                "entity retirement was not classified");

        final SailVisualLifecycleState angle = new SailVisualLifecycleState();
        angle.entityDiscovered(true);
        require(angle.angle(179.0F, 30.0F)
                        == SailVisualLifecycleState.Transition.ANGLE_CHANGED_SIGNIFICANTLY,
                "first angle sample missing");
        require(angle.angle(-179.0F, 30.0F) == SailVisualLifecycleState.Transition.NONE,
                "angle wrap incorrectly appeared as a 358-degree transition");
        require(angle.angle(-120.0F, 30.0F)
                        == SailVisualLifecycleState.Transition.ANGLE_CHANGED_SIGNIFICANTLY,
                "real wrapped angle movement was missed");
        require(angle.running(true) == SailVisualLifecycleState.Transition.MOVEMENT_STATE_CHANGED,
                "running transition missing");
        require(angle.running(true) == SailVisualLifecycleState.Transition.NONE,
                "stable running state emitted twice");
        require(angle.running(false) == SailVisualLifecycleState.Transition.MOVEMENT_STATE_CHANGED,
                "running-to-stopped transition missing");

        final SailVisualLifecycleState owner = new SailVisualLifecycleState();
        int ownerTransitions = 0;
        for (int observation = 0; observation < 1_000; observation++) {
            if (owner.owner(SailVisualLifecycleState.Owner.CPU_BRIDGE)) {
                ownerTransitions++;
            }
        }
        require(ownerTransitions == 1, "stable CPU ownership must emit exactly one transition");
        require(owner.owner(SailVisualLifecycleState.Owner.FLYWHEEL),
                "real CPU-to-Flywheel owner transition missing");
        require(!SailVisualLifecycleState.canDiscover(false, true),
                "removed worker-thread target must not be rediscovered");
        require(SailVisualLifecycleState.canDiscover(true, false),
                "live target must remain discoverable");

        final SailVisualLifecycleState repeated = new SailVisualLifecycleState();
        repeated.entityDiscovered(true);
        repeated.visualCreated();
        repeated.geometrySubmitted(1L);
        repeated.frame(33L, true);
        repeated.visualRemoved();
        require(repeated.visualCreated()
                        == SailVisualLifecycleState.Transition.RECOVERY_AFTER_VISUAL_RECREATION,
                "reassembly visual recreation did not classify recovery");
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
