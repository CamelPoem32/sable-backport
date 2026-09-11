package dev.eriksonn.aeronautics.forge;

import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.config.AeroConfig;
import dev.ryanhcode.sable.command.M25AeronauticsCommands;
import dev.ryanhcode.sable.command.M26AeronauticsPropulsionCommands;
import dev.ryanhcode.sable.command.M27AerodynamicsCommands;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Aeronautics.MOD_ID)
public final class AeronauticsForge {
    public AeronauticsForge() {
        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        Aeronautics.init(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AeroConfig.SPEC);
        MinecraftForge.EVENT_BUS.addListener(this::levelTick);
    }

    private void levelTick(final TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof final ServerLevel level) {
            M25AeronauticsCommands.tick(level);
            M26AeronauticsPropulsionCommands.tick(level);
            M27AerodynamicsCommands.tick(level);
        }
    }
}
