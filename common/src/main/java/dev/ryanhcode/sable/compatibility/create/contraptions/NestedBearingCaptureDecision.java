package dev.ryanhcode.sable.compatibility.create.contraptions;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Pure capture eligibility and overlap rules for nested bearing ownership transfer. */
public final class NestedBearingCaptureDecision {
    private NestedBearingCaptureDecision() {
    }

    public static Action evaluate(final boolean hasLiveNestedContraption, final Set<?> steeringNetworks,
                                  final Object bearingNetwork, final boolean payloadIsAir) {
        Objects.requireNonNull(steeringNetworks, "steeringNetworks");
        if (hasLiveNestedContraption) {
            return Action.SNAPSHOT_EXISTING;
        }
        if (bearingNetwork == null || !steeringNetworks.contains(bearingNetwork)) {
            return Action.SKIP_NO_SELECTED_STEERING_NETWORK;
        }
        if (payloadIsAir) {
            return Action.SKIP_AIR_PAYLOAD;
        }
        return Action.DISCOVER_STATIC_PAYLOAD;
    }

    public static <T> Set<T> overlap(final Set<T> capturedPositions, final Set<T> outerSelectedPositions) {
        Objects.requireNonNull(capturedPositions, "capturedPositions");
        Objects.requireNonNull(outerSelectedPositions, "outerSelectedPositions");
        final Set<T> overlap = new LinkedHashSet<>();
        for (final T position : capturedPositions) {
            if (position == null) {
                throw new IllegalArgumentException("Nested captured position must not be null");
            }
            if (outerSelectedPositions.contains(position)) {
                overlap.add(position);
            }
        }
        return Set.copyOf(overlap);
    }

    public enum Action {
        SNAPSHOT_EXISTING,
        DISCOVER_STATIC_PAYLOAD,
        SKIP_NO_SELECTED_STEERING_NETWORK,
        SKIP_AIR_PAYLOAD
    }
}
