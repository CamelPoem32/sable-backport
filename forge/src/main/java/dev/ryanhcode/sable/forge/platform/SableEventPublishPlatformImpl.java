package dev.ryanhcode.sable.forge.platform;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.forge.event.ForgeSablePostPhysicsTickEvent;
import dev.ryanhcode.sable.forge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.forge.event.ForgeSableSubLevelContainerReadyEvent;
import dev.ryanhcode.sable.platform.SableEventPublishPlatform;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;

public final class SableEventPublishPlatformImpl implements SableEventPublishPlatform {
    @Override
    public void onSubLevelContainerReady(final Level level, final SubLevelContainer container) {
        MinecraftForge.EVENT_BUS.post(new ForgeSableSubLevelContainerReadyEvent(level, container));
    }

    @Override
    public void prePhysicsTick(final SubLevelPhysicsSystem physicsSystem, final double timeStep) {
        MinecraftForge.EVENT_BUS.post(new ForgeSablePrePhysicsTickEvent(physicsSystem, timeStep));
    }

    @Override
    public void postPhysicsTick(final SubLevelPhysicsSystem physicsSystem, final double timeStep) {
        MinecraftForge.EVENT_BUS.post(new ForgeSablePostPhysicsTickEvent(physicsSystem, timeStep));
    }
}
