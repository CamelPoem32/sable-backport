package dev.ryanhcode.sable.mixin.m11.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.network.client.ClientSubLevelCreateValueSettingsHelper;
import net.minecraftforge.network.simple.SimpleChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Routes Create value-setting confirmation packets for Sable sub-level BEs through Sable target identity. */
@Mixin(targets = "com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsScreen", remap = false)
public class ValueSettingsScreenMixin {
    @WrapOperation(
            method = "saveAndClose(DD)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/network/simple/SimpleChannel;sendToServer(Ljava/lang/Object;)V"))
    private void sable$sendSubLevelValueSettings(final SimpleChannel channel, final Object packet,
                                                 final Operation<Void> original) {
        if (ClientSubLevelCreateValueSettingsHelper.trySendValueSettingsPacket(packet)) {
            return;
        }
        original.call(channel, packet);
    }
}
