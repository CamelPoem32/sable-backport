package dev.ryanhcode.sable.mixin.clip_overwrite;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClipContext.class)
public interface ClipContextAccessor {

    @Accessor("block")
    ClipContext.Block sable$getBlock();

    @Accessor("fluid")
    ClipContext.Fluid sable$getFluid();

    @Accessor("collisionContext")
    CollisionContext sable$getCollisionContext();
}
