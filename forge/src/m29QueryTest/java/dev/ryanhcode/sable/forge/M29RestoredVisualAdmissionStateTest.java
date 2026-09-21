package dev.ryanhcode.sable.forge;

/** Focused M29.4 checks for lifecycle-driven, terminal restored-visual admission. */
public final class M29RestoredVisualAdmissionStateTest {
    private M29RestoredVisualAdmissionStateTest() {
    }

    public static void run() {
        delayedPrerequisiteRequestsOnceWhenReady();
        failedInitialRequestRetriesAtClientBoundary();
        simultaneousRestoresRemainIndependent();
        retiredAndInvalidPayloadsTerminateExplicitly();
        healthyVisualIsNotRecreated();
        missingVisualTimesOutExactlyOnce();
        visualWithoutGeometryStillTimesOut();
    }

    private static void delayedPrerequisiteRequestsOnceWhenReady() {
        final RestoredContraptionVisualAdmissionState state = new RestoredContraptionVisualAdmissionState();
        require(state.afterDecode(true, false, false)
                        == RestoredContraptionVisualAdmissionState.Transition.WAIT_FOR_PREREQUISITES,
                "decode before Flywheel admission is ready must wait");
        require(state.clientTick(true, true, true, false, false)
                        == RestoredContraptionVisualAdmissionState.Transition.WAIT_FOR_PREREQUISITES,
                "missing prerequisite must not cause a destructive refresh loop");
        require(state.clientTick(true, true, true, true, false)
                        == RestoredContraptionVisualAdmissionState.Transition.REQUEST_INITIAL_REFRESH,
                "the first ready client boundary must request admission once");
        require(state.visualCreated() == RestoredContraptionVisualAdmissionState.Transition.VISUAL_PRESENT,
                "actual visual construction must be observed");
        require(state.geometryObserved() == RestoredContraptionVisualAdmissionState.Transition.FIRST_GEOMETRY,
                "the first embedding frame must terminate successfully");
        require(state.terminalReason() == RestoredContraptionVisualAdmissionState.TerminalReason.SUCCESS,
                "geometry must terminate admission with SUCCESS");
    }

    private static void failedInitialRequestRetriesAtClientBoundary() {
        final RestoredContraptionVisualAdmissionState state = new RestoredContraptionVisualAdmissionState();
        require(state.afterDecode(true, true, false)
                        == RestoredContraptionVisualAdmissionState.Transition.REQUEST_INITIAL_REFRESH,
                "ready post-decode admission must submit the first request");
        require(state.clientTick(true, true, true, true, false)
                        == RestoredContraptionVisualAdmissionState.Transition.REQUEST_SAFE_BOUNDARY_RETRY,
                "a failed request must survive until one safe-boundary retry");
        require(state.refreshRequests() == 2, "refresh attempts must be bounded at two");
    }

    private static void simultaneousRestoresRemainIndependent() {
        final RestoredContraptionVisualAdmissionState first = new RestoredContraptionVisualAdmissionState();
        final RestoredContraptionVisualAdmissionState second = new RestoredContraptionVisualAdmissionState();
        first.afterDecode(true, true, false);
        second.afterDecode(true, false, false);

        first.visualCreated();
        first.geometryObserved();
        require(first.terminalReason() == RestoredContraptionVisualAdmissionState.TerminalReason.SUCCESS,
                "first restored CCE must complete independently");
        require(!second.terminal() && second.refreshRequests() == 0,
                "first completion must not consume or mutate the second CCE");
        require(second.clientTick(true, true, true, true, false)
                        == RestoredContraptionVisualAdmissionState.Transition.REQUEST_INITIAL_REFRESH,
                "second restored CCE must retain its own admission request");
    }

    private static void retiredAndInvalidPayloadsTerminateExplicitly() {
        final RestoredContraptionVisualAdmissionState retired = new RestoredContraptionVisualAdmissionState();
        retired.afterDecode(true, true, false);
        require(retired.clientTick(false, true, true, true, false)
                        == RestoredContraptionVisualAdmissionState.Transition.ENTITY_RETIRED,
                "removed entity must terminate pending admission");
        require(retired.terminalReason() == RestoredContraptionVisualAdmissionState.TerminalReason.ENTITY_REMOVED,
                "removed entity must retain an explicit terminal reason");

        final RestoredContraptionVisualAdmissionState mismatch = new RestoredContraptionVisualAdmissionState();
        mismatch.afterDecode(true, true, false);
        require(mismatch.clientTick(true, false, true, true, false)
                        == RestoredContraptionVisualAdmissionState.Transition.PAYLOAD_REJECTED,
                "payload mismatch must fail before visual creation");
        require(mismatch.terminalReason() == RestoredContraptionVisualAdmissionState.TerminalReason.PAYLOAD_INVALID,
                "payload mismatch must retain an explicit terminal reason");
    }

    private static void healthyVisualIsNotRecreated() {
        final RestoredContraptionVisualAdmissionState state = new RestoredContraptionVisualAdmissionState();
        require(state.afterDecode(true, true, true)
                        == RestoredContraptionVisualAdmissionState.Transition.VISUAL_ALREADY_PRESENT,
                "healthy visual must be recognized directly");
        require(state.refreshRequests() == 0, "healthy visual must not be requeued");
    }

    private static void missingVisualTimesOutExactlyOnce() {
        final RestoredContraptionVisualAdmissionState state = new RestoredContraptionVisualAdmissionState();
        state.afterDecode(true, true, false);
        RestoredContraptionVisualAdmissionState.Transition transition =
                RestoredContraptionVisualAdmissionState.Transition.NONE;
        for (int tick = 0; tick < RestoredContraptionVisualAdmissionState.MAX_CLIENT_TICK_AGE; tick++) {
            transition = state.clientTick(true, true, true, true, false);
        }
        require(transition == RestoredContraptionVisualAdmissionState.Transition.TIMED_OUT,
                "missing visual must reach one bounded timeout");
        require(state.terminalReason() == RestoredContraptionVisualAdmissionState.TerminalReason.TIMEOUT,
                "timeout must be terminal and explicit");
        require(state.clientTick(true, true, true, true, false)
                        == RestoredContraptionVisualAdmissionState.Transition.NONE,
                "terminal timeout must remain quiet");
    }

    private static void visualWithoutGeometryStillTimesOut() {
        final RestoredContraptionVisualAdmissionState state = new RestoredContraptionVisualAdmissionState();
        state.afterDecode(true, true, false);
        state.visualCreated();
        RestoredContraptionVisualAdmissionState.Transition transition =
                RestoredContraptionVisualAdmissionState.Transition.NONE;
        for (int tick = 0; tick < RestoredContraptionVisualAdmissionState.MAX_CLIENT_TICK_AGE; tick++) {
            transition = state.clientTick(true, true, true, true, true);
        }
        require(transition == RestoredContraptionVisualAdmissionState.Transition.TIMED_OUT,
                "a visual with no embedding frame must not remain pending forever");
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
