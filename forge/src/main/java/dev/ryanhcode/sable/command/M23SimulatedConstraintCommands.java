package dev.ryanhcode.sable.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.simibubi.create.AllBlocks;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity;
import dev.simulated_team.simulated.index.SimulatedBlocks;
import dev.simulated_team.simulated.index.SimulatedConfig;
import dev.simulated_team.simulated.index.SimulatedItems;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class M23SimulatedConstraintCommands {

    private M23SimulatedConstraintCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m23")
                .then(Commands.literal("status").executes(M23SimulatedConstraintCommands::status))
                .then(Commands.literal("fixture")
                        .then(Commands.literal("spring")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> fixtureSpring(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("torsion")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> deferredFixture(ctx.getSource(), "torsion_spring"))))
                        .then(Commands.literal("swivel")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> deferredFixture(ctx.getSource(), "swivel_bearing"))))
                        .then(Commands.literal("rope")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> deferredFixture(ctx.getSource(), "rope_connector/rope_winch"))))
                        .then(Commands.literal("docking")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> deferredFixture(ctx.getSource(), "docking_connector")))))
                .then(Commands.literal("inspect")
                        .then(Commands.literal("spring").executes(M23SimulatedConstraintCommands::inspectSpring))));
    }

    private static int status(final CommandContext<CommandSourceStack> ctx) {
        send(ctx.getSource(), "SABLE_M23_STATUS simulatedBaseline=" + Simulated.BASELINE_COMMIT
                + " spring=ADAPT_NOW"
                + " torsion_spring=DEFER_M24"
                + " swivel_bearing=DEFER_M24"
                + " rope_connector=STRUCTURAL_ONLY"
                + " rope_winch=DEFER_M24"
                + " docking_connector=DEFER_M24"
                + " springConstraintsEnabled=" + SimulatedConfig.ENABLE_M23_SPRING_CONSTRAINTS.get()
                + " status=SPRING_GATE_READY");
        return 1;
    }

    private static int fixtureSpring(final CommandSourceStack source, final String name) {
        if (!"basic".equals(name)) {
            send(source, "SABLE_M23_FIXTURE status=FAIL family=spring unknownFixture=" + name + " expected=basic");
            return 0;
        }

        final ServerLevel level = source.getLevel();
        final BlockPos origin = BlockPos.containing(source.getPosition()).offset(4, 1, 0);
        final BlockPos bodyA = origin;
        final BlockPos bodyB = origin.offset(7, 0, 0);

        buildSpringBody(level, bodyA, Blocks.COPPER_BLOCK.defaultBlockState());
        buildSpringBody(level, bodyB, Blocks.CUT_COPPER.defaultBlockState());
        level.setBlock(bodyA.offset(-1, 1, 0), AllBlocks.SHAFT.get().defaultBlockState(), 3);
        level.setBlock(bodyB.offset(1, 1, 0), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), 3);
        final BlockEntity chest = level.getBlockEntity(bodyB.offset(1, 1, 0));
        if (chest instanceof final net.minecraft.world.level.block.entity.ChestBlockEntity chestBlockEntity) {
            chestBlockEntity.setItem(0, SimulatedItems.SPRING.get().getDefaultInstance());
            chestBlockEntity.setChanged();
        }

        if (source.getEntity() instanceof final net.minecraft.server.level.ServerPlayer player) {
            player.getInventory().add(new ItemStack(SimulatedItems.SPRING.get()));
        }

        send(source, "SABLE_M23_FIXTURE status=PASS family=spring name=basic"
                + " bodyAAssembler=" + bodyA.above().toShortString()
                + " bodyBAssembler=" + bodyB.above().toShortString()
                + " bodyASpringSupport=" + bodyA.offset(1, 1, 0).toShortString()
                + " bodyBSpringSupport=" + bodyB.offset(-1, 1, 0).toShortString()
                + " sequence=right_click_each_assembler_then_use_spring_item_on_facing_support_faces");
        return 1;
    }

    private static void buildSpringBody(final ServerLevel level, final BlockPos origin, final net.minecraft.world.level.block.state.BlockState payload) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                level.setBlock(origin.offset(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            }
        }
        level.setBlock(origin.above(), SimulatedBlocks.PHYSICS_ASSEMBLER.get().defaultBlockState(), 3);
        level.setBlock(origin.offset(1, 1, 0), payload, 3);
        level.setBlock(origin.offset(-1, 1, 0), payload, 3);
        level.setBlock(origin.offset(0, 1, 1), Blocks.AMETHYST_BLOCK.defaultBlockState(), 3);
    }

    private static int deferredFixture(final CommandSourceStack source, final String family) {
        send(source, "SABLE_M23_FIXTURE status=DEFERRED family=" + family
                + " reason=source_documented_not_runtime_ported_in_spring_gate");
        return 1;
    }

    private static int inspectSpring(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final SpringBlockEntity spring = findNearestSpring(source.getLevel(), BlockPos.containing(source.getPosition()), 32);
        if (spring == null) {
            send(source, "SABLE_M23_INSPECT family=spring runtimeState=NOT_FOUND");
            return 0;
        }
        send(source, "SABLE_M23_INSPECT " + spring.inspect());
        return 1;
    }

    private static SpringBlockEntity findNearestSpring(final ServerLevel level, final BlockPos center, final int radius) {
        SpringBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (final BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (level.getBlockEntity(pos) instanceof final SpringBlockEntity spring) {
                final double distance = pos.distSqr(center);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = spring;
                }
            }
        }
        return best;
    }

    private static void send(final CommandSourceStack source, final String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }
}
