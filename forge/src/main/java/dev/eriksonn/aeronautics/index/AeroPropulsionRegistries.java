package dev.eriksonn.aeronautics.index;

import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.content.propulsion.WoodenPropellerBlock;
import dev.eriksonn.aeronautics.content.propulsion.WoodenPropellerBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AeroPropulsionRegistries {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Aeronautics.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Aeronautics.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Aeronautics.MOD_ID);

    public static final RegistryObject<Block> WOODEN_PROPELLER = BLOCKS.register("wooden_propeller",
            () -> new WoodenPropellerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Item> WOODEN_PROPELLER_ITEM = ITEMS.register("wooden_propeller",
            () -> new BlockItem(WOODEN_PROPELLER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<WoodenPropellerBlockEntity>> WOODEN_PROPELLER_BE =
            BLOCK_ENTITIES.register("wooden_propeller", () -> BlockEntityType.Builder
                    .of(WoodenPropellerBlockEntity::new, WOODEN_PROPELLER.get()).build(null));

    private AeroPropulsionRegistries() {
    }

    public static void register(final IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}
