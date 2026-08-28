package dev.ryanhcode.sable.mixin.m11.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.network.client.ClientSubLevelCreateValueSettingsHelper;
import net.minecraftforge.network.simple.SimpleChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Routes Create short-interaction value packets for Sable sub-level BEs through Sable target identity. */
@Mixin(targets = "com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsClient", remap = false)
public class ValueSettingsClientMixin {
    @WrapOperation(
            method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/network/simple/SimpleChannel;sendToServer(Ljava/lang/Object;)V"))
    private void sable$sendSubLevelShortValueSettings(final SimpleChannel channel, final Object packet,
                                                      final Operation<Void> original) {
        if (ClientSubLevelCreateValueSettingsHelper.trySendValueSettingsPacket(packet)) {
            return;
        }
        original.call(channel, packet);
    }
}
