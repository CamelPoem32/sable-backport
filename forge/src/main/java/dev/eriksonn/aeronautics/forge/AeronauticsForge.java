package dev.eriksonn.aeronautics.forge;

import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.config.AeroConfig;
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
    }
}
