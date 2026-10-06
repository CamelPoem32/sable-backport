package dev.ryanhcode.sable.command;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Deterministic harness decisions, shared by the runtime resolver and its tests. */
public final class M40FixturePreconditions {
    private M40FixturePreconditions() { }

    public record BodyCandidate(UUID body, boolean identityMatches, boolean signatureMatches) { }

    public static List<UUID> matchingBodies(final List<BodyCandidate> candidates) {
        final Map<UUID, Boolean> matches = new LinkedHashMap<>();
        for (final BodyCandidate candidate : candidates) {
            if (candidate.identityMatches && candidate.signatureMatches) matches.put(candidate.body, true);
        }
        return List.copyOf(matches.keySet());
    }

    public static boolean bearingReady(final boolean motorPresent, final boolean clutchPresent,
                                       final boolean bearingPresent, final float sourceSpeed,
                                       final float clutchSpeed, final float bearingSpeed,
                                       final Long sourceNetwork, final Long clutchNetwork, final Long bearingNetwork) {
        return motorPresent && clutchPresent && bearingPresent && Float.isFinite(sourceSpeed)
                && sourceSpeed != 0 && sourceSpeed == clutchSpeed && clutchSpeed == bearingSpeed
                && sourceNetwork != null && sourceNetwork.equals(clutchNetwork) && sourceNetwork.equals(bearingNetwork);
    }
}
