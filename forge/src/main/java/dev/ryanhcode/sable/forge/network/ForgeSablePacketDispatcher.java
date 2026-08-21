package dev.ryanhcode.sable.forge.network;

import dev.ryanhcode.sable.network.tcp.SablePacketDirection;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class ForgeSablePacketDispatcher {

    private ForgeSablePacketDispatcher() {
    }

    static boolean dispatch(final SablePacketDirection expectedDirection,
                            final SablePacketDirection actualDirection,
                            final Consumer<Runnable> enqueue,
                            final Supplier<Runnable> invocationFactory,
                            final Runnable markHandled) {
        Objects.requireNonNull(expectedDirection, "expectedDirection");
        Objects.requireNonNull(actualDirection, "actualDirection");
        Objects.requireNonNull(enqueue, "enqueue");
        Objects.requireNonNull(invocationFactory, "invocationFactory");
        Objects.requireNonNull(markHandled, "markHandled");

        if (actualDirection != expectedDirection) {
            markHandled.run();
            return false;
        }

        enqueue.accept(() -> {
            final Runnable invocation = invocationFactory.get();
            if (invocation != null) {
                invocation.run();
            }
        });
        markHandled.run();
        return true;
    }
}
