package dev.simulated_team.simulated.index;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.items.spring.SpringItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class SimulatedItems {

    public static final DeferredRegister<Item> REGISTER = DeferredRegister.create(ForgeRegistries.ITEMS, Simulated.MOD_ID);

    public static final RegistryObject<Item> PHYSICS_ASSEMBLER = REGISTER.register("physics_assembler",
            () -> SimulatedBlocks.blockItem(SimulatedBlocks.PHYSICS_ASSEMBLER));
    public static final RegistryObject<Item> SPRING = REGISTER.register("spring",
            () -> new SpringItem(new Item.Properties()));
    public static final RegistryObject<Item> ROPE_CONNECTOR = REGISTER.register("rope_connector",
            () -> SimulatedBlocks.blockItem(SimulatedBlocks.ROPE_CONNECTOR));
    public static final RegistryObject<Item> IRON_HANDLE = REGISTER.register("iron_handle",
            () -> SimulatedBlocks.blockItem(SimulatedBlocks.IRON_HANDLE));
    public static final RegistryObject<Item> REDSTONE_MAGNET = REGISTER.register("redstone_magnet",
            () -> SimulatedBlocks.blockItem(SimulatedBlocks.REDSTONE_MAGNET));
    public static final RegistryObject<Item> WHITE_SYMMETRIC_SAIL = REGISTER.register("white_symmetric_sail",
            () -> SimulatedBlocks.blockItem(SimulatedBlocks.WHITE_SYMMETRIC_SAIL));
    public static final RegistryObject<Item> TORSION_SPRING = REGISTER.register("torsion_spring",
            () -> SimulatedBlocks.blockItem(SimulatedBlocks.TORSION_SPRING));
    public static final RegistryObject<Item> SWIVEL_BEARING = REGISTER.register("swivel_bearing",
            () -> SimulatedBlocks.blockItem(SimulatedBlocks.SWIVEL_BEARING));
    public static final RegistryObject<Item> SWIVEL_BEARING_LINK_BLOCK = REGISTER.register("swivel_bearing_link_block",
            () -> SimulatedBlocks.blockItem(SimulatedBlocks.SWIVEL_BEARING_LINK_BLOCK));
    public static final RegistryObject<Item> ROPE_WINCH = REGISTER.register("rope_winch",
            () -> SimulatedBlocks.blockItem(SimulatedBlocks.ROPE_WINCH));
    public static final RegistryObject<Item> DOCKING_CONNECTOR = REGISTER.register("docking_connector",
            () -> SimulatedBlocks.blockItem(SimulatedBlocks.DOCKING_CONNECTOR));
    public static final RegistryObject<Item> PAIRED_DOCKING_CONNECTOR = REGISTER.register("paired_docking_connector",
            () -> SimulatedBlocks.blockItem(SimulatedBlocks.PAIRED_DOCKING_CONNECTOR));
    public static final RegistryObject<Item> ALTITUDE_SENSOR = REGISTER.register("altitude_sensor",
            () -> SimulatedBlocks.blockItem(SimulatedBlocks.ALTITUDE_SENSOR));
    public static final RegistryObject<Item> VELOCITY_SENSOR = REGISTER.register("velocity_sensor",
            () -> SimulatedBlocks.blockItem(SimulatedBlocks.VELOCITY_SENSOR));
    public static final RegistryObject<Item> OPTICAL_SENSOR = REGISTER.register("optical_sensor",
            () -> SimulatedBlocks.blockItem(SimulatedBlocks.OPTICAL_SENSOR));
    public static final RegistryObject<Item> STEERING_WHEEL = REGISTER.register("steering_wheel",
            () -> SimulatedBlocks.blockItem(SimulatedBlocks.STEERING_WHEEL));

    public static final RegistryObject<Item> CONTRAPTION_DIAGRAM = REGISTER.register("contraption_diagram",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ROPE_COUPLING = REGISTER.register("rope_coupling",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> GYROSCOPIC_MECHANISM = REGISTER.register("gyroscopic_mechanism",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ENGINE_ASSEMBLY = REGISTER.register("engine_assembly",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> HONEY_GLUE = REGISTER.register("honey_glue",
            () -> new Item(new Item.Properties().stacksTo(1).durability(100)));

    private SimulatedItems() {
    }

    public static void register(final IEventBus bus) {
        REGISTER.register(bus);
    }
}
