package dev.ryanhcode.sable.network.tcp;

@FunctionalInterface
public interface SablePacketSink {

    void sendPacket(SableTCPPacket packet);

    default void sendPacket(final SableTCPPacket... packets) {
        for (final SableTCPPacket packet : packets) {
            this.sendPacket(packet);
        }
    }
}
