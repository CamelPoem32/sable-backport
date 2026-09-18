package dev.ryanhcode.sable.mixin.m28;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.ryanhcode.sable.forge.SableM28RestoredContraptionClientSync;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractContraptionEntity.class, remap = false)
public class RestoredContraptionClientSpawnMixin {
    @Inject(method = "readSpawnData(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("RETURN"))
    private void sable$refreshRestoredVisualAfterPayload(final FriendlyByteBuf buffer, final CallbackInfo ci) {
        SableM28RestoredContraptionClientSync.afterSpawnData((AbstractContraptionEntity) (Object) this);
    }
}
