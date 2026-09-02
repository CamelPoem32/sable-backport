package dev.simulated_team.simulated.index;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlock;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class SimulatedBlocks {

    public static final DeferredRegister<Block> REGISTER = DeferredRegister.create(ForgeRegistries.BLOCKS, Simulated.MOD_ID);

    public static final RegistryObject<Block> PHYSICS_ASSEMBLER = REGISTER.register("physics_assembler",
            () -> new PhysicsAssemblerBlock(wooden().strength(2.0F, 3.0F)));
    public static final RegistryObject<Block> SPRING = REGISTER.register("spring",
            () -> new SpringBlock(metal().strength(1.5F, 6.0F).noOcclusion()));
    public static final RegistryObject<Block> ROPE_CONNECTOR = REGISTER.register("rope_connector",
            () -> new Block(metal().strength(2.0F, 6.0F).noOcclusion()));
    public static final RegistryObject<Block> IRON_HANDLE = REGISTER.register("iron_handle",
            () -> new Block(metal().strength(1.0F, 3.0F).noOcclusion()));
    public static final RegistryObject<Block> REDSTONE_MAGNET = REGISTER.register("redstone_magnet",
            () -> new Block(metal().strength(3.0F, 8.0F).noOcclusion()));
    public static final RegistryObject<Block> WHITE_SYMMETRIC_SAIL = REGISTER.register("white_symmetric_sail",
            () -> new Block(wooden().strength(0.8F, 1.5F).noOcclusion()));

    private SimulatedBlocks() {
    }

    public static void register(final IEventBus bus) {
        REGISTER.register(bus);
    }

    public static BlockItem blockItem(final RegistryObject<Block> block) {
        return new BlockItem(block.get(), new Item.Properties());
    }

    private static BlockBehaviour.Properties wooden() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).sound(SoundType.WOOD);
    }

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.NETHERITE_BLOCK);
    }
}
