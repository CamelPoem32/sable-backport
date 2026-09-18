package dev.ryanhcode.sable.compatibility.create.contraptions;

import java.util.Set;

/** Executable regression for the M28.14.1 immutable-set null contract. */
public final class NestedBearingCaptureDecisionTest {
    private NestedBearingCaptureDecisionTest() {
    }

    public static void run() {
        final Object steeringNetwork = new Object();
        final Set<Object> selectedNetworks = Set.of(steeringNetwork);

        require(NestedBearingCaptureDecision.evaluate(true, selectedNetworks, null, true)
                == NestedBearingCaptureDecision.Action.SNAPSHOT_EXISTING,
                "A live nested CCE must be snapshotted even when its optional kinetic network is null");
        require(NestedBearingCaptureDecision.evaluate(false, selectedNetworks, null, false)
                == NestedBearingCaptureDecision.Action.SKIP_NO_SELECTED_STEERING_NETWORK,
                "A detached bearing with no nested CCE must be skipped without Set.contains(null)");
        require(NestedBearingCaptureDecision.evaluate(false, selectedNetworks, steeringNetwork, false)
                == NestedBearingCaptureDecision.Action.DISCOVER_STATIC_PAYLOAD,
                "A steering-network bearing with a physical payload must be discovered");
        require(NestedBearingCaptureDecision.evaluate(false, selectedNetworks, new Object(), false)
                == NestedBearingCaptureDecision.Action.SKIP_NO_SELECTED_STEERING_NETWORK,
                "An unrelated bearing must not be captured");
        require(NestedBearingCaptureDecision.evaluate(false, selectedNetworks, steeringNetwork, true)
                == NestedBearingCaptureDecision.Action.SKIP_AIR_PAYLOAD,
                "A disassembled bearing without a physical payload must be skipped");

        final Set<String> twoBlockPayload = Set.of("payload-0", "payload-1");
        final Set<String> disjointHull = Set.of("bearing", "shaft");
        require(NestedBearingCaptureDecision.overlap(twoBlockPayload, disjointHull).isEmpty(),
                "A valid two-block payload must remain disjoint from the hull");
        require(NestedBearingCaptureDecision.overlap(twoBlockPayload, Set.of("bearing", "payload-1"))
                        .equals(Set.of("payload-1")),
                "A valid non-null hull overlap must still fail loud at the production boundary");
        require(twoBlockPayload.size() == 2 && disjointHull.size() == 2,
                "Eligibility and overlap checks must not mutate source ownership sets");
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
