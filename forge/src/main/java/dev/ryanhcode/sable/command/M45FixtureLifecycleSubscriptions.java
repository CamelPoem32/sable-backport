package dev.ryanhcode.sable.command;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.forge.event.ForgeSableAssemblyCompletedEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Operator fixture observers; Forge registers this class independently of gameplay lifecycles. */
@Mod.EventBusSubscriber(modid = Sable.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class M45FixtureLifecycleSubscriptions {
    private M45FixtureLifecycleSubscriptions() {
    }

    @SubscribeEvent
    public static void levelTick(final TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof final ServerLevel level) {
            M25AeronauticsCommands.tick(level);
            M26AeronauticsPropulsionCommands.tick(level);
            M27AerodynamicsCommands.tick(level);
        }
    }

    @SubscribeEvent
    public static void assemblyCompleted(final ForgeSableAssemblyCompletedEvent event) {
        M24SimulatedSystemsCommands.onM22AssemblyCreated(
                event.getLevel(), event.getAssemblerPos(), event.getSubLevel());
        M27AerodynamicsCommands.onM22AssemblyCreated(
                event.getLevel(), event.getAssemblerPos(), event.getSubLevel(), event.getAssemblyOffset());
    }
}
