package dev.ryanhcode.sable.forge;

/** Small deterministic state machine behind the event-only M29.1 sail visual trace. */
public final class SailVisualLifecycleState {
    public static final long GEOMETRY_GAP_FRAMES = 30L;

    private boolean entityAlive;
    private boolean payloadPresent;
    private boolean visualPresent;
    private boolean visualGap;
    private boolean running;
    private boolean runningKnown;
    private Owner owner = Owner.OTHER;
    private float lastAngle = Float.NaN;
    private long lastGeometryFrame = -1L;

    public Transition entityDiscovered(final boolean payload) {
        this.entityAlive = true;
        this.payloadPresent = payload;
        return Transition.ENTITY_DISCOVERED;
    }

    public Transition payload(final boolean present) {
        if (present == this.payloadPresent) {
            return Transition.NONE;
        }
        this.payloadPresent = present;
        return present ? Transition.PAYLOAD_PRESENT : Transition.PAYLOAD_CHANGED;
    }

    public Transition visualCreated() {
        final boolean recreated = !this.visualPresent && this.visualGap;
        this.visualPresent = true;
        return recreated ? Transition.RECOVERY_AFTER_VISUAL_RECREATION : Transition.VISUAL_CREATED;
    }

    public Transition visualRemoved() {
        if (!this.visualPresent) {
            return Transition.NONE;
        }
        this.visualPresent = false;
        return Transition.VISUAL_REMOVED;
    }

    public Transition angle(final float value, final float minimumDelta) {
        if (Float.isNaN(this.lastAngle)) {
            this.lastAngle = value;
            return Transition.ANGLE_CHANGED_SIGNIFICANTLY;
        }
        final float delta = wrapDegrees(value - this.lastAngle);
        if (Math.abs(delta) < minimumDelta) {
            return Transition.NONE;
        }
        this.lastAngle = value;
        return Transition.ANGLE_CHANGED_SIGNIFICANTLY;
    }

    public Transition running(final boolean value) {
        if (this.runningKnown && this.running == value) {
            return Transition.NONE;
        }
        this.runningKnown = true;
        this.running = value;
        return Transition.MOVEMENT_STATE_CHANGED;
    }

    public Transition geometrySubmitted(final long frame) {
        this.lastGeometryFrame = frame;
        if (this.visualGap) {
            this.visualGap = false;
            return Transition.GEOMETRY_SUBMISSION_RESUMED;
        }
        return Transition.FIRST_GEOMETRY_SUBMITTED;
    }

    public Transition frame(final long frame, final boolean expectedVisible) {
        if (!expectedVisible || !this.entityAlive || !this.payloadPresent || !this.visualPresent
                || this.lastGeometryFrame < 0L || frame - this.lastGeometryFrame <= GEOMETRY_GAP_FRAMES
                || this.visualGap) {
            return Transition.NONE;
        }
        this.visualGap = true;
        return Transition.SUSPECTED_VISUAL_GAP;
    }

    public Transition entityRemoved() {
        if (!this.entityAlive) {
            return Transition.NONE;
        }
        this.entityAlive = false;
        return Transition.ENTITY_REMOVED;
    }

    public boolean owner(final Owner value) {
        if (this.owner == value) {
            return false;
        }
        this.owner = value;
        return true;
    }

    public Owner owner() {
        return this.owner;
    }

    public static boolean canDiscover(final boolean alive, final boolean removed) {
        return alive && !removed;
    }

    public boolean visualPresent() {
        return this.visualPresent;
    }

    public boolean payloadPresent() {
        return this.payloadPresent;
    }

    public boolean visualGap() {
        return this.visualGap;
    }

    public long lastGeometryFrame() {
        return this.lastGeometryFrame;
    }

    private static float wrapDegrees(final float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    public enum Transition {
        NONE,
        ENTITY_DISCOVERED,
        PAYLOAD_PRESENT,
        PAYLOAD_CHANGED,
        VISUAL_CREATED,
        VISUAL_REMOVED,
        ANGLE_CHANGED_SIGNIFICANTLY,
        MOVEMENT_STATE_CHANGED,
        FIRST_GEOMETRY_SUBMITTED,
        SUSPECTED_VISUAL_GAP,
        GEOMETRY_SUBMISSION_RESUMED,
        RECOVERY_AFTER_VISUAL_RECREATION,
        ENTITY_REMOVED
    }

    public enum Owner {
        OTHER,
        FLYWHEEL,
        CPU_BRIDGE
    }
}
