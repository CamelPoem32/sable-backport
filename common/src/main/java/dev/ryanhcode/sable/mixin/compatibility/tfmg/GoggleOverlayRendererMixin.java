package dev.ryanhcode.sable.mixin.compatibility.tfmg;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer;
import dev.ryanhcode.sable.annotation.MixinModVersionConstraint;
import dev.ryanhcode.sable.compatibility.create.goggles.SableGoggleOverlayGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;

/** WrapMethod surrounds the already-injected HEAD, without relying on TFMG's generated handler name or priority. */
@Mixin(value = GoggleOverlayRenderer.class, remap = false)
@MixinModVersionConstraint("[1.0.2f]")
public abstract class GoggleOverlayRendererMixin {
    @WrapMethod(method = "renderOverlay(Lnet/minecraftforge/client/gui/overlay/ForgeGui;Lnet/minecraft/client/gui/GuiGraphics;FII)V")
    private static void sable$guardTfmgOverlayEntry(final ForgeGui gui, final GuiGraphics graphics,
                                                    final float partialTicks, final int width, final int height,
                                                    final Operation<Void> original) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (!SableGoggleOverlayGuard.allow(minecraft.level, minecraft.hitResult,
                minecraft.level != null && minecraft.player != null && minecraft.gameMode != null)) {
            GoggleOverlayRenderer.lastHovered = null;
            GoggleOverlayRenderer.hoverTicks = 0;
            return;
        }
        original.call(gui, graphics, partialTicks, width, height);
    }
}
