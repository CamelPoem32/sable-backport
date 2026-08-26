package dev.ryanhcode.sable.mixin.plot.lighting;

import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelLightEngine.class)
public interface LevelLightEngineAccessor {

    @Accessor("blockEngine")
    LightEngine<?, ?> sable$getBlockEngine();

    @Accessor("skyEngine")
    LightEngine<?, ?> sable$getSkyEngine();
}
