package dev.ryanhcode.sable.mixin.m28;

import dev.ryanhcode.sable.forge.SableM29RestoredCceRemovalTrace;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStopTrackingSubLevelPacket;
import dev.ryanhcode.sable.network.tcp.SableClientPacketHandlers;
import dev.ryanhcode.sable.network.tcp.SablePacketContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks removals that execute synchronously inside Sable's client sublevel teardown. */
@Mixin(value = SableClientPacketHandlers.class, remap = false)
public abstract class SableClientSubLevelTeardownProbeMixin {
    @Inject(method = "handleStopTracking", at = @At("HEAD"))
    private static void sable$beforeStopTracking(final ClientboundStopTrackingSubLevelPacket packet,
                                                 final SablePacketContext context,
                                                 final CallbackInfo ci) {
        SableM29RestoredCceRemovalTrace.beginSableTeardown(packet.plotCoordinate());
    }

    @Inject(method = "handleStopTracking", at = @At("RETURN"))
    private static void sable$afterStopTracking(final ClientboundStopTrackingSubLevelPacket packet,
                                                final SablePacketContext context,
                                                final CallbackInfo ci) {
        SableM29RestoredCceRemovalTrace.endSableTeardown();
    }
}
