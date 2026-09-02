package dev.simulated_team.simulated.index;

import dev.simulated_team.simulated.Simulated;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class SimulatedCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Simulated.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = REGISTER.register("main_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.simulated.main_tab"))
                    .icon(() -> new ItemStack(SimulatedItems.PHYSICS_ASSEMBLER.get()))
                    .displayItems((parameters, output) -> SimulatedItems.REGISTER.getEntries()
                            .forEach(entry -> output.accept(entry.get())))
                    .build());

    private SimulatedCreativeTabs() {
    }

    public static void register(final IEventBus bus) {
        REGISTER.register(bus);
    }
}
