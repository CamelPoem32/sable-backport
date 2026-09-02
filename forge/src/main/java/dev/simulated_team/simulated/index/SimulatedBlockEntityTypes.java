package dev.simulated_team.simulated.index;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity;
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

    private SimulatedBlockEntityTypes() {
    }

    public static void register(final IEventBus bus) {
        REGISTER.register(bus);
    }
}
