package dev.ryanhcode.sable.forge;

/** Bounded client grace for a reverse-restored CCE waiting for its controller block update. */
public final class RestoredContraptionControllerSyncState {
    public static final int MAX_MISSING_CONTROLLER_TICKS = 4;

    private int missingControllerTicks;
    private boolean terminal;
    private TerminalReason terminalReason;

    public static boolean eligible(final boolean restoredMarker,
                                   final boolean clientSide,
                                   final boolean normalWorld,
                                   final boolean entityAlive,
                                   final boolean payloadValid,
                                   final boolean controllerMatchesBearing,
                                   final boolean alreadyCompleted) {
        return restoredMarker && clientSide && normalWorld && entityAlive && payloadValid
                && controllerMatchesBearing && !alreadyCompleted;
    }

    public synchronized Transition controllerMissing() {
        if (this.terminal) {
            return Transition.NONE;
        }
        this.missingControllerTicks++;
        if (this.missingControllerTicks > MAX_MISSING_CONTROLLER_TICKS) {
            this.terminate(TerminalReason.TIMEOUT);
            return Transition.TIMEOUT;
        }
        if (this.missingControllerTicks == 1) {
            return Transition.WAIT_BEGIN;
        }
        if (this.missingControllerTicks == 2) {
            return Transition.STILL_MISSING_CHECKPOINT;
        }
        return Transition.SUPPRESS_DISCARD;
    }

    public synchronized Transition controllerAvailable() {
        if (this.terminal) {
            return Transition.NONE;
        }
        this.terminate(TerminalReason.CONTROLLER_APPEARED);
        return Transition.CONTROLLER_AVAILABLE;
    }

    public synchronized Transition payloadInvalid() {
        if (this.terminal) {
            return Transition.NONE;
        }
        this.terminate(TerminalReason.PAYLOAD_INVALID);
        return Transition.PAYLOAD_INVALID;
    }

    public synchronized Transition entityRemoved() {
        if (this.terminal) {
            return Transition.NONE;
        }
        this.terminate(TerminalReason.SERVER_ENTITY_REMOVED);
        return Transition.ENTITY_REMOVED;
    }

    public synchronized Transition levelUnloaded() {
        if (this.terminal) {
            return Transition.NONE;
        }
        this.terminate(TerminalReason.LEVEL_UNLOAD);
        return Transition.LEVEL_UNLOAD;
    }

    public synchronized int missingControllerTicks() {
        return this.missingControllerTicks;
    }

    public synchronized boolean terminal() {
        return this.terminal;
    }

    public synchronized TerminalReason terminalReason() {
        return this.terminalReason;
    }

    public static boolean suppressesDiscard(final Transition transition) {
        return transition == Transition.WAIT_BEGIN
                || transition == Transition.STILL_MISSING_CHECKPOINT
                || transition == Transition.SUPPRESS_DISCARD;
    }

    private void terminate(final TerminalReason reason) {
        this.terminal = true;
        this.terminalReason = reason;
    }

    public enum Transition {
        NONE,
        WAIT_BEGIN,
        STILL_MISSING_CHECKPOINT,
        SUPPRESS_DISCARD,
        CONTROLLER_AVAILABLE,
        PAYLOAD_INVALID,
        ENTITY_REMOVED,
        LEVEL_UNLOAD,
        TIMEOUT
    }

    public enum TerminalReason {
        CONTROLLER_APPEARED,
        SERVER_ENTITY_REMOVED,
        PAYLOAD_INVALID,
        TIMEOUT,
        LEVEL_UNLOAD
    }
}
