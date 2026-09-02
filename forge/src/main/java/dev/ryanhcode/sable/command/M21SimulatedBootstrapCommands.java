package dev.ryanhcode.sable.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.index.SimulatedBlockEntityTypes;
import dev.simulated_team.simulated.index.SimulatedBlocks;
import dev.simulated_team.simulated.index.SimulatedConfig;
import dev.simulated_team.simulated.index.SimulatedItems;
import dev.simulated_team.simulated.network.SimulatedNetwork;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

public final class M21SimulatedBootstrapCommands {

    private static final List<ResourceLocation> EXPECTED_BLOCKS = List.of(
            Simulated.path("physics_assembler"),
            Simulated.path("spring"),
            Simulated.path("rope_connector"),
            Simulated.path("iron_handle"),
            Simulated.path("redstone_magnet"),
            Simulated.path("white_symmetric_sail"));

    private static final List<ResourceLocation> EXPECTED_ITEMS = List.of(
            Simulated.path("physics_assembler"),
            Simulated.path("spring"),
            Simulated.path("rope_connector"),
            Simulated.path("iron_handle"),
            Simulated.path("redstone_magnet"),
            Simulated.path("white_symmetric_sail"),
            Simulated.path("contraption_diagram"),
            Simulated.path("rope_coupling"),
            Simulated.path("gyroscopic_mechanism"),
            Simulated.path("engine_assembly"),
            Simulated.path("honey_glue"));

    private static final List<ResourceLocation> EXPECTED_BLOCK_ENTITIES = List.of(
            Simulated.path("physics_assembler"),
            Simulated.path("spring"));

    private M21SimulatedBootstrapCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m21")
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("registry_check")
                        .executes(ctx -> registryCheck(ctx.getSource())))
                .then(Commands.literal("fixture")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> fixture(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"))))));
    }

    private static int status(final CommandSourceStack source) {
        List<String> missing = missingRegistrations();
        send(source, "simulatedPresent=true");
        send(source, "simulatedBaseline=" + Simulated.BASELINE_COMMIT);
        send(source, "registeredBlocks=" + SimulatedBlocks.REGISTER.getEntries().size());
        send(source, "registeredItems=" + SimulatedItems.REGISTER.getEntries().size());
        send(source, "registeredBlockEntities=" + SimulatedBlockEntityTypes.REGISTER.getEntries().size());
        send(source, "registeredEntities=0");
        send(source, "registeredMenus=0");
        send(source, "registeredRecipeTypes=0");
        send(source, "registeredRecipeSerializers=0");
        send(source, "networkReady=" + SimulatedNetwork.isReady());
        send(source, "configReady=" + SimulatedConfig.isReady());
        send(source, "clientBootstrapExpected=true");
        send(source, "missingRegistrations=" + (missing.isEmpty() ? "[]" : missing));
        send(source, "status=" + (missing.isEmpty() && SimulatedNetwork.isReady() && SimulatedConfig.isReady()
                ? "BOOTSTRAP_READY" : "BOOTSTRAP_INCOMPLETE"));
        return missing.isEmpty() ? 1 : 0;
    }

    private static int registryCheck(final CommandSourceStack source) {
        List<String> missing = missingRegistrations();
        if (missing.isEmpty()) {
            send(source, "SABLE_M21_REGISTRY_CHECK status=PASS missing=[]");
            return 1;
        }
        send(source, "SABLE_M21_REGISTRY_CHECK status=FAIL missing=" + missing);
        return 0;
    }

    private static int fixture(final CommandSourceStack source, final String name) {
        if (!"bootstrap_gallery".equals(name)) {
            send(source, "SABLE_M21_FIXTURE status=FAIL unknownFixture=" + name
                    + " expected=bootstrap_gallery");
            return 0;
        }
        if (!SimulatedConfig.ENABLE_M21_BOOTSTRAP_FIXTURE.get()) {
            send(source, "SABLE_M21_FIXTURE status=FAIL disabledByConfig=true");
            return 0;
        }

        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition()).offset(2, 0, 0);
        List<RegistryObject<Block>> blocks = List.of(
                SimulatedBlocks.PHYSICS_ASSEMBLER,
                SimulatedBlocks.SPRING,
                SimulatedBlocks.ROPE_CONNECTOR,
                SimulatedBlocks.IRON_HANDLE,
                SimulatedBlocks.REDSTONE_MAGNET,
                SimulatedBlocks.WHITE_SYMMETRIC_SAIL);

        for (int index = 0; index < blocks.size(); index++) {
            BlockPos pos = origin.offset(index, 0, 0);
            level.setBlock(pos.below(), Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
            level.setBlock(pos, blocks.get(index).get().defaultBlockState(), 3);
        }

        send(source, "SABLE_M21_FIXTURE status=PASS name=bootstrap_gallery origin=" + origin.toShortString()
                + " blocks=" + blocks.size()
                + " physicsAssemblerBE=" + (level.getBlockEntity(origin) != null)
                + " springBE=" + (level.getBlockEntity(origin.offset(1, 0, 0)) != null));
        return 1;
    }

    private static List<String> missingRegistrations() {
        List<String> missing = new ArrayList<>();
        EXPECTED_BLOCKS.stream()
                .filter(id -> !ForgeRegistries.BLOCKS.containsKey(id))
                .map(id -> "block:" + id)
                .forEach(missing::add);
        EXPECTED_ITEMS.stream()
                .filter(id -> !ForgeRegistries.ITEMS.containsKey(id))
                .map(id -> "item:" + id)
                .forEach(missing::add);
        EXPECTED_BLOCK_ENTITIES.stream()
                .filter(id -> !ForgeRegistries.BLOCK_ENTITY_TYPES.containsKey(id))
                .map(id -> "block_entity_type:" + id)
                .forEach(missing::add);
        return missing;
    }

    private static void send(final CommandSourceStack source, final String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }
}
