package dev.ryanhcode.sable.mixin.m28;

import dev.ryanhcode.sable.forge.SableM29RestoredCceRemovalTrace;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Distinguishes vanilla remove packets from local client cleanup for restored CCEs. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerRemovalProbeMixin {
    @Shadow
    private ClientLevel level;

    @Inject(method = "handleRemoveEntities", at = @At("HEAD"))
    private void sable$beforeRemovePacket(final ClientboundRemoveEntitiesPacket packet, final CallbackInfo ci) {
        SableM29RestoredCceRemovalTrace.beginRemovePacket(this.level, packet.getEntityIds());
    }

    @Inject(method = "handleRemoveEntities", at = @At("RETURN"))
    private void sable$afterRemovePacket(final ClientboundRemoveEntitiesPacket packet, final CallbackInfo ci) {
        SableM29RestoredCceRemovalTrace.endRemovePacket();
    }
}
