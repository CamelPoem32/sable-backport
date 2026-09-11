package dev.simulated_team.simulated.index;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.m24.M24Family;
import dev.simulated_team.simulated.content.blocks.m24.M24PhysicalComponentBlock;
import dev.simulated_team.simulated.content.blocks.m24.M24TorsionSpringBlock;
import dev.simulated_team.simulated.content.blocks.m24.M24WinchBlock;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlock;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlock;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlock;
import dev.simulated_team.simulated.content.blocks.symmetric_sail.SymmetricSailBlock;
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
            () -> new M24PhysicalComponentBlock(metal().strength(2.0F, 6.0F).noOcclusion(),
                    M24Family.ROPE_CONNECTOR));
    public static final RegistryObject<Block> IRON_HANDLE = REGISTER.register("iron_handle",
            () -> new Block(metal().strength(1.0F, 3.0F).noOcclusion()));
    public static final RegistryObject<Block> REDSTONE_MAGNET = REGISTER.register("redstone_magnet",
            () -> new Block(metal().strength(3.0F, 8.0F).noOcclusion()));
    public static final RegistryObject<Block> WHITE_SYMMETRIC_SAIL = REGISTER.register("white_symmetric_sail",
            () -> new SymmetricSailBlock(wooden().strength(0.8F, 1.5F).noOcclusion()));
    public static final RegistryObject<Block> TORSION_SPRING = REGISTER.register("torsion_spring",
            () -> new M24TorsionSpringBlock(metal().strength(2.0F, 8.0F).noOcclusion()));
    public static final RegistryObject<Block> SWIVEL_BEARING = REGISTER.register("swivel_bearing",
            () -> new M24PhysicalComponentBlock(metal().strength(2.5F, 8.0F).noOcclusion(),
                    M24Family.SWIVEL_BEARING));
    public static final RegistryObject<Block> SWIVEL_BEARING_LINK_BLOCK = REGISTER.register("swivel_bearing_link_block",
            () -> new M24PhysicalComponentBlock(metal().strength(2.5F, 8.0F).noOcclusion(),
                    M24Family.SWIVEL_BEARING_LINK_BLOCK));
    public static final RegistryObject<Block> ROPE_WINCH = REGISTER.register("rope_winch",
            () -> new M24WinchBlock(metal().strength(2.5F, 8.0F).noOcclusion()));
    public static final RegistryObject<Block> DOCKING_CONNECTOR = REGISTER.register("docking_connector",
            () -> new M24PhysicalComponentBlock(metal().strength(2.5F, 8.0F).noOcclusion(),
                    M24Family.DOCKING_CONNECTOR));
    public static final RegistryObject<Block> PAIRED_DOCKING_CONNECTOR = REGISTER.register("paired_docking_connector",
            () -> new M24PhysicalComponentBlock(metal().strength(2.5F, 8.0F).noOcclusion(),
                    M24Family.PAIRED_DOCKING_CONNECTOR));
    public static final RegistryObject<Block> ALTITUDE_SENSOR = REGISTER.register("altitude_sensor",
            () -> new M24PhysicalComponentBlock(metal().strength(1.5F, 6.0F).noOcclusion(),
                    M24Family.ALTITUDE_SENSOR));
    public static final RegistryObject<Block> VELOCITY_SENSOR = REGISTER.register("velocity_sensor",
            () -> new M24PhysicalComponentBlock(metal().strength(1.5F, 6.0F).noOcclusion(),
                    M24Family.VELOCITY_SENSOR));
    public static final RegistryObject<Block> OPTICAL_SENSOR = REGISTER.register("optical_sensor",
            () -> new M24PhysicalComponentBlock(metal().strength(1.5F, 6.0F).noOcclusion(),
                    M24Family.OPTICAL_SENSOR));
    public static final RegistryObject<Block> STEERING_WHEEL = REGISTER.register("steering_wheel",
            () -> new SteeringWheelBlock(wooden().strength(1.5F, 4.0F).noOcclusion()));

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
