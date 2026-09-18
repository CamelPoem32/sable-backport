package dev.ryanhcode.sable.mixin.m28;

import dev.engine_room.flywheel.lib.visual.AbstractEntityVisual;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exact Flywheel 1.0.5 entity-visual ownership boundary used by M28 diagnostics. */
@Mixin(value = AbstractEntityVisual.class, remap = false)
public interface AbstractEntityVisualAccessor {
    @Accessor("entity")
    Entity sable$getEntity();
}
