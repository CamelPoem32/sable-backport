package dev.ryanhcode.sable.mixin.m28;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the exact LevelEntityGetter returned after Sable's inclusive wrapper is installed. */
@Mixin(ClientLevel.class)
public interface ClientLevelEntityGetterAccessor {
    @Invoker("getEntities")
    LevelEntityGetter<Entity> sable$invokeGetEntities();
}
