package dev.ryanhcode.sable.forge.platform;

import dev.ryanhcode.sable.api.event.SablePostPhysicsTickEvent;
import dev.ryanhcode.sable.api.event.SablePrePhysicsTickEvent;
import dev.ryanhcode.sable.api.event.SableSubLevelContainerReadyEvent;
import dev.ryanhcode.sable.forge.event.ForgeSablePostPhysicsTickEvent;
import dev.ryanhcode.sable.forge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.forge.event.ForgeSableSubLevelContainerReadyEvent;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import net.minecraftforge.common.MinecraftForge;

public final class SableEventPlatformImpl implements SableEventPlatform {
    @Override
    public void onSubLevelContainerReady(final SableSubLevelContainerReadyEvent event) {
        MinecraftForge.EVENT_BUS.<ForgeSableSubLevelContainerReadyEvent>addListener(forgeEvent ->
                event.onSubLevelContainerReady(forgeEvent.getLevel(), forgeEvent.getContainer()));
    }

    @Override
    public void onPhysicsTick(final SablePrePhysicsTickEvent event) {
        MinecraftForge.EVENT_BUS.<ForgeSablePrePhysicsTickEvent>addListener(forgeEvent ->
                event.prePhysicsTick(forgeEvent.getPhysicsSystem(), forgeEvent.getTimeStep()));
    }

    @Override
    public void onPostPhysicsTick(final SablePostPhysicsTickEvent event) {
        MinecraftForge.EVENT_BUS.<ForgeSablePostPhysicsTickEvent>addListener(forgeEvent ->
                event.postPhysicsTick(forgeEvent.getPhysicsSystem(), forgeEvent.getTimeStep()));
    }
}
