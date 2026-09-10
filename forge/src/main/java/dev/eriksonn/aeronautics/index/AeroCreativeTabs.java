package dev.eriksonn.aeronautics.index;

import dev.eriksonn.aeronautics.Aeronautics;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class AeroCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Aeronautics.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = REGISTER.register("main_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.aeronautics.main_tab"))
                    .icon(() -> new ItemStack(AeroItems.LEVITITE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(AeroItems.LEVITITE.get());
                        output.accept(AeroPropulsionRegistries.WOODEN_PROPELLER_ITEM.get());
                    })
                    .build());

    private AeroCreativeTabs() {
    }

    public static void register(final IEventBus bus) {
        REGISTER.register(bus);
    }
}
