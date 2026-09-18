package dev.ryanhcode.sable.mixin.m28;

import dev.engine_room.flywheel.lib.visual.AbstractVisual;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exact Flywheel 1.0.5 render-origin boundary used by M28 diagnostics. */
@Mixin(value = AbstractVisual.class, remap = false)
public interface AbstractVisualAccessor {
    @Invoker("renderOrigin")
    Vec3i sable$invokeRenderOrigin();
}
