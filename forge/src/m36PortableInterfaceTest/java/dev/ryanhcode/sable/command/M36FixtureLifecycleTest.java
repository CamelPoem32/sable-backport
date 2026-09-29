package dev.ryanhcode.sable.command;

import dev.ryanhcode.sable.command.M36PortableInterfaceFixtureCommands.CleanupPhase;
import dev.ryanhcode.sable.command.M36PortableInterfaceFixtureCommands.LookupStatus;

/** Deterministic lookup/teardown policy checks; native Create motion still needs runtime validation. */
public final class M36FixtureLifecycleTest {
    private M36FixtureLifecycleTest() {
    }

    public static void run() {
        for (final LookupStatus status : LookupStatus.values()) {
            if (status.allowsSetup() != (status == LookupStatus.NONE)) {
                throw new AssertionError("setup lookup mismatch: " + status);
            }
            if (status.allowsSetup() && status.allowsCleanup()) {
                throw new AssertionError("setup and cleanup disagree: " + status);
            }
        }
        assertRecovery(LookupStatus.ACTIVE_STATIC, false, CleanupPhase.DISCONNECT_WAIT);
        assertRecovery(LookupStatus.ACTIVE_OUTER, false, CleanupPhase.DISCONNECT_WAIT);
        assertRecovery(LookupStatus.ACTIVE_STATIC, true, CleanupPhase.INNER_DISASSEMBLED);
        assertRecovery(LookupStatus.ACTIVE_OUTER, true, CleanupPhase.INNER_DISASSEMBLED);
        assertRecovery(LookupStatus.PARTIALLY_CLEANED, false, CleanupPhase.REMOVE);
        assertRecovery(LookupStatus.ORPHAN_MARKER, false, CleanupPhase.REMOVE);
        if (LookupStatus.AMBIGUOUS.allowsCleanup() || LookupStatus.CORRUPT.allowsCleanup()) {
            throw new AssertionError("unsafe fixture snapshot must not be cleaned automatically");
        }
        // A timed-out task carries no tombstone; a new invocation uses the latest snapshot.
        assertRecovery(LookupStatus.ACTIVE_OUTER, true, CleanupPhase.INNER_DISASSEMBLED);
        assertRecovery(LookupStatus.PARTIALLY_CLEANED, true, CleanupPhase.REMOVE);
    }

    private static void assertRecovery(final LookupStatus status, final boolean retracted,
                                       final CleanupPhase expected) {
        if (!status.allowsCleanup() || status.startsAt(retracted) != expected) {
            throw new AssertionError(status + " retracted=" + retracted + " expected " + expected);
        }
    }
}
