package dev.ryanhcode.sable.mixin.m11.interaction;

import dev.ryanhcode.sable.network.client.ClientSubLevelInteractionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Catches Sable block right-clicks before vanilla can drop them or serialize hidden plot coordinates. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    @Nullable
    public LocalPlayer player;

    @Shadow
    @Nullable
    public ClientLevel level;

    @Shadow
    @Nullable
    public HitResult hitResult;

    @Shadow
    private int rightClickDelay;

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void sable$startUseItemOnSubLevel(final CallbackInfo ci) {
        if (this.player == null || this.level == null) {
            return;
        }

        final InteractionHand hand = this.player.getMainHandItem().isEmpty()
                && !this.player.getOffhandItem().isEmpty() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        final InteractionResult result =
                ClientSubLevelInteractionHelper.tryUseOnCurrentTarget(this.player, this.level, hand, this.hitResult);
        if (result != null) {
            this.rightClickDelay = 4;
            if (result.consumesAction()) {
                if (result.shouldSwing()) {
                    this.player.swing(hand);
                }
                Minecraft.getInstance().gameRenderer.itemInHandRenderer.itemUsed(hand);
            }
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;pick(F)V", shift = Shift.AFTER))
    private void sable$refreshHeldUseSubLevelTarget(final CallbackInfo ci) {
        if (this.player == null || this.level == null) {
            return;
        }
        ClientSubLevelInteractionHelper.refreshHeldUseTarget(this.player, this.level, this.hitResult);
    }
}
