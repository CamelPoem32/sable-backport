package dev.simulated_team.simulated.forge;

import dev.simulated_team.simulated.Simulated;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Simulated.MOD_ID)
public final class SimulatedForge {

    public SimulatedForge() {
        Simulated.init(FMLJavaModLoadingContext.get().getModEventBus());
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON,
                dev.simulated_team.simulated.index.SimulatedConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT,
                dev.simulated_team.simulated.index.SimulatedConfig.CLIENT_SPEC);
    }
}
