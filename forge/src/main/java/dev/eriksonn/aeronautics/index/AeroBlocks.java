package dev.eriksonn.aeronautics.index;

import dev.eriksonn.aeronautics.Aeronautics;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AeroBlocks {
    public static final DeferredRegister<Block> REGISTER =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Aeronautics.MOD_ID);

    public static final RegistryObject<Block> LEVITITE = REGISTER.register("levitite",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .lightLevel(state -> 10)
                    .noLootTable()
                    .strength(7.0F, 20.0F)
                    .sound(SoundType.AMETHYST)));

    private AeroBlocks() {
    }

    public static void register(final IEventBus bus) {
        REGISTER.register(bus);
    }
}
