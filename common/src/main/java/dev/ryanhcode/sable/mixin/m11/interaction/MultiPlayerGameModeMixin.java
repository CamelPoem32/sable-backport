package dev.ryanhcode.sable.mixin.m11.interaction;

import dev.ryanhcode.sable.network.client.ClientSubLevelInteractionHelper;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sends sub-level right-clicks through a Sable intent packet instead of vanilla hidden plot coordinates. */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void sable$useItemOnSubLevel(final LocalPlayer player,
                                         final InteractionHand hand,
                                         final BlockHitResult hitResult,
                                         final CallbackInfoReturnable<InteractionResult> cir) {
        final InteractionResult result = ClientSubLevelInteractionHelper.tryUseOnSubLevel(player, player.level(), hand, hitResult);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
