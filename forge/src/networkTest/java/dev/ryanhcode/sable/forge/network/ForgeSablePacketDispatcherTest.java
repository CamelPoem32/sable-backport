package dev.ryanhcode.sable.forge.network;

import dev.ryanhcode.sable.network.SablePacketCodec;
import dev.ryanhcode.sable.network.tcp.SablePacketContext;
import dev.ryanhcode.sable.network.tcp.SablePacketDirection;
import dev.ryanhcode.sable.network.tcp.SablePacketRegistration;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeSablePacketDispatcherTest {

    @Test
    void acceptedPacketIsDeferredHandledAndInvokedExactlyOnceWithItsContext() {
        final SablePacketContext context = new RecordingPacketContext(SablePacketDirection.SERVERBOUND);
        final TestPacket packet = new TestPacket(7);
        final AtomicReference<SablePacketContext> receivedContext = new AtomicReference<>();
        final AtomicInteger invocations = new AtomicInteger();
        final AtomicInteger handled = new AtomicInteger();
        final List<Runnable> queued = new ArrayList<>();
        final SablePacketRegistration<TestPacket> registration = new SablePacketRegistration<>(
                13, TestPacket.class, SablePacketDirection.SERVERBOUND, TestPacket.CODEC,
                (received, receivedPacketContext) -> {
                    assertSame(packet, received);
                    receivedContext.set(receivedPacketContext);
                    invocations.incrementAndGet();
                });

        final boolean accepted = ForgeSablePacketDispatcher.dispatch(
                registration.direction(), SablePacketDirection.SERVERBOUND, queued::add,
                () -> () -> registration.handler().accept(packet, context), handled::incrementAndGet);

        assertTrue(accepted);
        assertEquals(1, handled.get());
        assertEquals(1, queued.size());
        assertEquals(0, invocations.get());
        queued.get(0).run();
        assertEquals(1, invocations.get());
        assertSame(context, receivedContext.get());
    }

    @Test
    void wrongDirectionIsHandledWithoutEnqueueOrContextCreation() {
        final AtomicInteger contextCreations = new AtomicInteger();
        final AtomicInteger handled = new AtomicInteger();
        final List<Runnable> queued = new ArrayList<>();
        final boolean accepted = ForgeSablePacketDispatcher.dispatch(
                SablePacketDirection.CLIENTBOUND, SablePacketDirection.SERVERBOUND, queued::add,
                () -> {
                    contextCreations.incrementAndGet();
                    return () -> { };
                }, handled::incrementAndGet);

        assertFalse(accepted);
        assertEquals(1, handled.get());
        assertEquals(0, contextCreations.get());
        assertTrue(queued.isEmpty());
    }

    @Test
    void missingSenderContextDoesNotInvokeHandler() {
        final AtomicInteger contextChecks = new AtomicInteger();
        final AtomicInteger handled = new AtomicInteger();
        final List<Runnable> queued = new ArrayList<>();
        final boolean accepted = ForgeSablePacketDispatcher.dispatch(
                SablePacketDirection.SERVERBOUND, SablePacketDirection.SERVERBOUND, queued::add,
                () -> {
                    contextChecks.incrementAndGet();
                    return null;
                }, handled::incrementAndGet);

        assertTrue(accepted);
        assertEquals(1, handled.get());
        assertEquals(1, queued.size());
        queued.get(0).run();
        assertEquals(1, contextChecks.get());
    }

    private record TestPacket(int value) implements SableTCPPacket {
        private static final SablePacketCodec<TestPacket> CODEC = SablePacketCodec.of(
                (buffer, packet) -> buffer.writeInt(packet.value),
                buffer -> new TestPacket(buffer.readInt()));
    }

    private record RecordingPacketContext(SablePacketDirection direction) implements SablePacketContext {
        @Override
        public net.minecraft.world.level.Level level() {
            return null;
        }

        @Override
        public net.minecraft.world.entity.player.Player player() {
            return null;
        }
    }
}
