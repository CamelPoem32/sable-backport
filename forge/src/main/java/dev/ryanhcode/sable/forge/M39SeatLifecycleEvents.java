package dev.ryanhcode.sable.forge;

import dev.ryanhcode.sable.compatibility.create.seat.SableSeatPassengers;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Release the native Seat mapping before the parent-world entity leaves its lifecycle. */
public final class M39SeatLifecycleEvents {
    private M39SeatLifecycleEvents() {
    }

    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(M39SeatLifecycleEvents::logout);
        MinecraftForge.EVENT_BUS.addListener(M39SeatLifecycleEvents::death);
        MinecraftForge.EVENT_BUS.addListener(M39SeatLifecycleEvents::dimension);
    }

    private static void detach(final Entity entity) {
        if (!entity.level().isClientSide && SableSeatPassengers.riding(entity)) entity.stopRiding();
    }

    private static void logout(final PlayerEvent.PlayerLoggedOutEvent event) {
        detach(event.getEntity());
    }

    private static void death(final LivingDeathEvent event) {
        if (!event.isCanceled()) detach(event.getEntity());
    }

    private static void dimension(final EntityTravelToDimensionEvent event) {
        if (!event.isCanceled()) detach(event.getEntity());
    }
}
