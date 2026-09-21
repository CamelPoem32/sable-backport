package dev.ryanhcode.sable.forge;

/** Focused M29.6 checks for bounded, reverse-restore-only controller synchronization. */
public final class M29RestoredControllerSyncStateTest {
    private M29RestoredControllerSyncStateTest() {
    }

    public static void run() {
        controllerAppearsAfterOneTick();
        missingControllerTimesOut();
        ordinaryCreateIsIneligible();
        legitimateRemovalTerminatesGrace();
        simultaneousRestoresRemainIndependent();
        synchronizedControllerNeedsNoGrace();
    }

    private static void controllerAppearsAfterOneTick() {
        final RestoredContraptionControllerSyncState state = new RestoredContraptionControllerSyncState();
        require(RestoredContraptionControllerSyncState.suppressesDiscard(state.controllerMissing()),
                "first missing-controller tick must suppress the Create discard");
        require(state.controllerAvailable()
                        == RestoredContraptionControllerSyncState.Transition.CONTROLLER_AVAILABLE,
                "controller appearance must resume normal Create ticking");
        require(state.terminalReason()
                        == RestoredContraptionControllerSyncState.TerminalReason.CONTROLLER_APPEARED,
                "controller appearance must permanently end grace");
    }

    private static void missingControllerTimesOut() {
        final RestoredContraptionControllerSyncState state = new RestoredContraptionControllerSyncState();
        for (int tick = 0; tick < RestoredContraptionControllerSyncState.MAX_MISSING_CONTROLLER_TICKS; tick++) {
            require(RestoredContraptionControllerSyncState.suppressesDiscard(state.controllerMissing()),
                    "grace must remain active only inside its bounded window");
        }
        require(state.controllerMissing() == RestoredContraptionControllerSyncState.Transition.TIMEOUT,
                "the first tick after the grace window must restore Create's normal failure behavior");
        require(state.terminalReason() == RestoredContraptionControllerSyncState.TerminalReason.TIMEOUT,
                "timeout must be explicit and terminal");
        require(state.controllerMissing() == RestoredContraptionControllerSyncState.Transition.NONE,
                "a timed-out state must never restart its grace window");
    }

    private static void ordinaryCreateIsIneligible() {
        require(!RestoredContraptionControllerSyncState.eligible(
                        false, true, true, true, true, true, false),
                "an ordinary Create CCE must retain Create's immediate missing-controller discard");
        require(!RestoredContraptionControllerSyncState.eligible(
                        true, false, true, true, true, true, false),
                "server entities must never enter client synchronization grace");
        require(!RestoredContraptionControllerSyncState.eligible(
                        true, true, true, true, false, true, false),
                "invalid restored payload must fail normally");
    }

    private static void legitimateRemovalTerminatesGrace() {
        final RestoredContraptionControllerSyncState state = new RestoredContraptionControllerSyncState();
        state.controllerMissing();
        require(state.entityRemoved() == RestoredContraptionControllerSyncState.Transition.ENTITY_REMOVED,
                "a legitimate remove packet must terminate grace");
        require(state.terminalReason()
                        == RestoredContraptionControllerSyncState.TerminalReason.SERVER_ENTITY_REMOVED,
                "server removal must remain authoritative");
    }

    private static void simultaneousRestoresRemainIndependent() {
        final RestoredContraptionControllerSyncState first = new RestoredContraptionControllerSyncState();
        final RestoredContraptionControllerSyncState second = new RestoredContraptionControllerSyncState();
        first.controllerMissing();
        second.controllerMissing();
        first.controllerAvailable();
        require(first.terminal(), "first restored CCE must complete independently");
        require(!second.terminal() && second.missingControllerTicks() == 1,
                "first completion must not consume the second CCE's grace state");
    }

    private static void synchronizedControllerNeedsNoGrace() {
        final RestoredContraptionControllerSyncState state = new RestoredContraptionControllerSyncState();
        state.controllerAvailable();
        require(state.missingControllerTicks() == 0,
                "an already synchronized controller must never consume a grace tick");
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
