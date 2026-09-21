package dev.ryanhcode.sable.forge;

/** Bounded state machine for one reverse-restored contraption's Flywheel admission. */
public final class RestoredContraptionVisualAdmissionState {
    public static final int MAX_CLIENT_TICK_AGE = 4;

    private int clientTickAge;
    private int refreshRequests;
    private boolean firstTickObserved;
    private boolean visualPresent;
    private boolean geometryObserved;
    private boolean terminal;
    private TerminalReason terminalReason;

    public synchronized Transition afterDecode(final boolean managerPresent,
                                               final boolean admissionReady,
                                               final boolean visualAlreadyPresent) {
        if (visualAlreadyPresent) {
            this.visualPresent = true;
            return Transition.VISUAL_ALREADY_PRESENT;
        }
        if (!managerPresent) {
            return Transition.WAIT_FOR_MANAGER;
        }
        if (!admissionReady) {
            return Transition.WAIT_FOR_PREREQUISITES;
        }
        this.refreshRequests++;
        return Transition.REQUEST_INITIAL_REFRESH;
    }

    public synchronized Transition clientTick(final boolean entityAlive,
                                              final boolean payloadValid,
                                              final boolean managerPresent,
                                              final boolean admissionReady,
                                              final boolean visualNowPresent) {
        if (this.terminal) {
            return Transition.NONE;
        }
        this.clientTickAge++;
        if (!entityAlive) {
            this.terminate(TerminalReason.ENTITY_REMOVED);
            return Transition.ENTITY_RETIRED;
        }
        if (!payloadValid) {
            this.terminate(TerminalReason.PAYLOAD_INVALID);
            return Transition.PAYLOAD_REJECTED;
        }
        if (visualNowPresent) {
            this.visualPresent = true;
            return this.timeoutOr(Transition.VISUAL_PRESENT);
        }
        if (!managerPresent) {
            return this.timeoutOr(Transition.WAIT_FOR_MANAGER);
        }
        if (!admissionReady) {
            return this.timeoutOr(Transition.WAIT_FOR_PREREQUISITES);
        }
        if (this.refreshRequests < 2) {
            this.refreshRequests++;
            return this.refreshRequests == 1
                    ? Transition.REQUEST_INITIAL_REFRESH
                    : Transition.REQUEST_SAFE_BOUNDARY_RETRY;
        }
        return this.timeoutOr(Transition.WAIT_FOR_VISUAL);
    }

    public synchronized boolean markFirstTick() {
        if (this.firstTickObserved) {
            return false;
        }
        this.firstTickObserved = true;
        return true;
    }

    public synchronized Transition visualCreated() {
        if (this.terminal || this.visualPresent) {
            return Transition.NONE;
        }
        this.visualPresent = true;
        return Transition.VISUAL_PRESENT;
    }

    public synchronized Transition geometryObserved() {
        if (this.terminal || this.geometryObserved) {
            return Transition.NONE;
        }
        this.visualPresent = true;
        this.geometryObserved = true;
        this.terminate(TerminalReason.SUCCESS);
        return Transition.FIRST_GEOMETRY;
    }

    public synchronized boolean retire(final TerminalReason reason) {
        if (this.terminal) {
            return false;
        }
        this.terminate(reason);
        return true;
    }

    public synchronized int clientTickAge() {
        return this.clientTickAge;
    }

    public synchronized int refreshRequests() {
        return this.refreshRequests;
    }

    public synchronized boolean visualPresent() {
        return this.visualPresent;
    }

    public synchronized boolean hasObservedGeometry() {
        return this.geometryObserved;
    }

    public synchronized boolean terminal() {
        return this.terminal;
    }

    public synchronized TerminalReason terminalReason() {
        return this.terminalReason;
    }

    private Transition timeoutOr(final Transition pending) {
        if (this.clientTickAge < MAX_CLIENT_TICK_AGE) {
            return pending;
        }
        this.terminate(TerminalReason.TIMEOUT);
        return Transition.TIMED_OUT;
    }

    private void terminate(final TerminalReason reason) {
        this.terminal = true;
        this.terminalReason = reason;
    }

    public enum Transition {
        NONE,
        WAIT_FOR_MANAGER,
        WAIT_FOR_PREREQUISITES,
        WAIT_FOR_VISUAL,
        REQUEST_INITIAL_REFRESH,
        REQUEST_SAFE_BOUNDARY_RETRY,
        VISUAL_ALREADY_PRESENT,
        VISUAL_PRESENT,
        FIRST_GEOMETRY,
        ENTITY_RETIRED,
        PAYLOAD_REJECTED,
        TIMED_OUT
    }

    public enum TerminalReason {
        SUCCESS,
        ENTITY_REMOVED,
        LEVEL_UNLOAD,
        PAYLOAD_INVALID,
        STATE_REPLACED,
        TRANSFER_CLEANUP,
        TIMEOUT,
        OTHER
    }
}
