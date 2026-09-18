package dev.ryanhcode.sable.network.client;

import java.util.function.BooleanSupplier;

/** Prevents vanilla repeat-use and attack paths while a client-owned hold interaction is active. */
public final class ClientSubLevelHoldUseGuard {
    private static BooleanSupplier active = () -> false;

    private ClientSubLevelHoldUseGuard() {
    }

    public static void register(final BooleanSupplier activeSupplier) {
        active = activeSupplier;
    }

    public static boolean isActive() {
        return active.getAsBoolean();
    }
}
