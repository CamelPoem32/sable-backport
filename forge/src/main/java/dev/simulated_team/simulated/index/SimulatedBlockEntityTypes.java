package dev.simulated_team.simulated.index;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.m24.M24Family;
import dev.simulated_team.simulated.content.blocks.m24.M24PhysicalBlockEntity;
import dev.simulated_team.simulated.content.blocks.m24.M24WinchBlockEntity;
import dev.simulated_team.simulated.content.blocks.m24.M24TorsionSpringBlockEntity;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class SimulatedBlockEntityTypes {

    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Simulated.MOD_ID);

    public static final RegistryObject<BlockEntityType<PhysicsAssemblerBlockEntity>> PHYSICS_ASSEMBLER =
            REGISTER.register("physics_assembler", () -> BlockEntityType.Builder
                    .of(PhysicsAssemblerBlockEntity::new, SimulatedBlocks.PHYSICS_ASSEMBLER.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<SpringBlockEntity>> SPRING =
            REGISTER.register("spring", () -> BlockEntityType.Builder
                    .of(SpringBlockEntity::new, SimulatedBlocks.SPRING.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<M24TorsionSpringBlockEntity>> TORSION_SPRING =
            REGISTER.register("torsion_spring", () -> BlockEntityType.Builder
                    .of(M24TorsionSpringBlockEntity::new,
                            SimulatedBlocks.TORSION_SPRING.get())
                    .build(null));
    public static final RegistryObject<BlockEntityType<M24PhysicalBlockEntity>> SWIVEL_BEARING =
            REGISTER.register("swivel_bearing", () -> BlockEntityType.Builder
                    .of((pos, state) -> new M24PhysicalBlockEntity(M24Family.SWIVEL_BEARING, pos, state),
                            SimulatedBlocks.SWIVEL_BEARING.get())
                    .build(null));
    public static final RegistryObject<BlockEntityType<M24PhysicalBlockEntity>> SWIVEL_BEARING_LINK_BLOCK =
            REGISTER.register("swivel_bearing_link_block", () -> BlockEntityType.Builder
                    .of((pos, state) -> new M24PhysicalBlockEntity(M24Family.SWIVEL_BEARING_LINK_BLOCK, pos, state),
                            SimulatedBlocks.SWIVEL_BEARING_LINK_BLOCK.get())
                    .build(null));
    public static final RegistryObject<BlockEntityType<M24PhysicalBlockEntity>> ROPE_CONNECTOR =
            REGISTER.register("rope_connector", () -> BlockEntityType.Builder
                    .of((pos, state) -> new M24PhysicalBlockEntity(M24Family.ROPE_CONNECTOR, pos, state),
                            SimulatedBlocks.ROPE_CONNECTOR.get())
                    .build(null));
    public static final RegistryObject<BlockEntityType<M24WinchBlockEntity>> ROPE_WINCH =
            REGISTER.register("rope_winch", () -> BlockEntityType.Builder
                    .of(M24WinchBlockEntity::new,
                            SimulatedBlocks.ROPE_WINCH.get())
                    .build(null));
    public static final RegistryObject<BlockEntityType<M24PhysicalBlockEntity>> DOCKING_CONNECTOR =
            REGISTER.register("docking_connector", () -> BlockEntityType.Builder
                    .of((pos, state) -> new M24PhysicalBlockEntity(M24Family.DOCKING_CONNECTOR, pos, state),
                            SimulatedBlocks.DOCKING_CONNECTOR.get())
                    .build(null));
    public static final RegistryObject<BlockEntityType<M24PhysicalBlockEntity>> PAIRED_DOCKING_CONNECTOR =
            REGISTER.register("paired_docking_connector", () -> BlockEntityType.Builder
                    .of((pos, state) -> new M24PhysicalBlockEntity(M24Family.PAIRED_DOCKING_CONNECTOR, pos, state),
                            SimulatedBlocks.PAIRED_DOCKING_CONNECTOR.get())
                    .build(null));
    public static final RegistryObject<BlockEntityType<M24PhysicalBlockEntity>> ALTITUDE_SENSOR =
            REGISTER.register("altitude_sensor", () -> BlockEntityType.Builder
                    .of((pos, state) -> new M24PhysicalBlockEntity(M24Family.ALTITUDE_SENSOR, pos, state),
                            SimulatedBlocks.ALTITUDE_SENSOR.get())
                    .build(null));
    public static final RegistryObject<BlockEntityType<M24PhysicalBlockEntity>> VELOCITY_SENSOR =
            REGISTER.register("velocity_sensor", () -> BlockEntityType.Builder
                    .of((pos, state) -> new M24PhysicalBlockEntity(M24Family.VELOCITY_SENSOR, pos, state),
                            SimulatedBlocks.VELOCITY_SENSOR.get())
                    .build(null));
    public static final RegistryObject<BlockEntityType<M24PhysicalBlockEntity>> OPTICAL_SENSOR =
            REGISTER.register("optical_sensor", () -> BlockEntityType.Builder
                    .of((pos, state) -> new M24PhysicalBlockEntity(M24Family.OPTICAL_SENSOR, pos, state),
                            SimulatedBlocks.OPTICAL_SENSOR.get())
                    .build(null));
    public static final RegistryObject<BlockEntityType<SteeringWheelBlockEntity>> STEERING_WHEEL =
            REGISTER.register("steering_wheel", () -> BlockEntityType.Builder
                    .of(SteeringWheelBlockEntity::new, SimulatedBlocks.STEERING_WHEEL.get())
                    .build(null));

    private SimulatedBlockEntityTypes() {
    }

    public static void register(final IEventBus bus) {
        REGISTER.register(bus);
    }
}
