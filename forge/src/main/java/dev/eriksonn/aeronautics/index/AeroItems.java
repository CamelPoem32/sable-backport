package dev.eriksonn.aeronautics.index;

import dev.eriksonn.aeronautics.Aeronautics;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AeroItems {
    public static final DeferredRegister<Item> REGISTER =
            DeferredRegister.create(ForgeRegistries.ITEMS, Aeronautics.MOD_ID);

    public static final RegistryObject<Item> LEVITITE = REGISTER.register("levitite",
            () -> new BlockItem(AeroBlocks.LEVITITE.get(), new Item.Properties()));

    private AeroItems() {
    }

    public static void register(final IEventBus bus) {
        REGISTER.register(bus);
    }
}
