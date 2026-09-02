package dev.ryanhcode.sable.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.simibubi.create.AllBlocks;
import dev.ryanhcode.sable.api.command.SableCommandHelper;
import dev.ryanhcode.sable.api.command.SubLevelArgumentType;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity;
import dev.simulated_team.simulated.index.SimulatedBlocks;
import dev.simulated_team.simulated.index.SimulatedConfig;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.joml.Vector3d;

import java.util.Collection;

public final class M22SimulatedAssemblyCommands {

    private M22SimulatedAssemblyCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m22")
                .then(Commands.literal("status").executes(M22SimulatedAssemblyCommands::status))
                .then(Commands.literal("fixture")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> fixture(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("selector", SubLevelArgumentType.subLevelsOrLevel())
                                .executes(M22SimulatedAssemblyCommands::inspect)))
                .then(Commands.literal("nudge")
                        .then(Commands.argument("selector", SubLevelArgumentType.singleSubLevel())
                                .then(Commands.argument("dx", DoubleArgumentType.doubleArg(-16.0D, 16.0D))
                                        .then(Commands.argument("dy", DoubleArgumentType.doubleArg(-16.0D, 16.0D))
                                                .then(Commands.argument("dz", DoubleArgumentType.doubleArg(-16.0D, 16.0D))
                                                        .executes(M22SimulatedAssemblyCommands::nudge)))))));
    }

    private static int status(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        send(source, "SABLE_M22_STATUS simulatedAssemblyPresent=true"
                + " simulatedBaseline=" + Simulated.BASELINE_COMMIT
                + " physicsAssemblerBlock=" + Simulated.path("physics_assembler")
                + " physicsAssemblerBE=" + Simulated.path("physics_assembler")
                + " registeredBlocks=6"
                + " registeredItems=11"
                + " basicAssemblyEnabled=" + SimulatedConfig.ENABLE_M22_BASIC_ASSEMBLY.get()
                + " maxBlocksMoved=" + SimulatedConfig.M22_MAX_BLOCKS_MOVED.get()
                + " status=BASIC_LIFECYCLE_READY");
        return 1;
    }

    private static int fixture(final CommandSourceStack source, final String name) {
        if (!"basic_assembly".equals(name)) {
            send(source, "SABLE_M22_FIXTURE status=FAIL unknownFixture=" + name + " expected=basic_assembly");
            return 0;
        }

        final ServerLevel level = source.getLevel();
        final BlockPos origin = BlockPos.containing(source.getPosition()).offset(2, 1, 0);

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(origin.offset(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            }
        }

        final BlockPos assemblerPos = origin.above();
        final BlockPos chestPos = origin.offset(1, 1, 0);
        final BlockPos shaftPos = origin.offset(-1, 1, 0);
        final BlockPos payloadPos = origin.offset(0, 1, 1);

        level.setBlock(assemblerPos, SimulatedBlocks.PHYSICS_ASSEMBLER.get().defaultBlockState(), 3);
        level.setBlock(payloadPos, Blocks.CUT_COPPER.defaultBlockState(), 3);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,
                source.getRotation().y > 0.0F ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.SOUTH), 3);
        level.setBlock(shaftPos, AllBlocks.SHAFT.get().defaultBlockState(), 3);

        final BlockEntity chest = level.getBlockEntity(chestPos);
        if (chest instanceof final ChestBlockEntity chestBlockEntity) {
            chestBlockEntity.setItem(0, Items.DIAMOND.getDefaultInstance());
            chestBlockEntity.setChanged();
        }

        send(source, "SABLE_M22_FIXTURE status=PASS name=basic_assembly"
                + " assemblerPos=" + assemblerPos.toShortString()
                + " payloadOrigin=" + origin.toShortString()
                + " chestPos=" + chestPos.toShortString()
                + " shaftPos=" + shaftPos.toShortString()
                + " trigger=right_click_physics_assembler");
        return 1;
    }

    private static int inspect(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final Collection<ServerSubLevel> subLevels = SubLevelArgumentType.getSubLevels(ctx, "selector");
        final CommandSourceStack source = ctx.getSource();

        if (subLevels.isEmpty()) {
            final PhysicsAssemblerBlockEntity assembler = findNearestParentAssembler(source.getLevel(),
                    BlockPos.containing(source.getPosition()), 16);
            if (assembler == null) {
                send(source, "SABLE_M22_INSPECT state=PARENT_WORLD assemblerPos=not_found candidateBlockCount=0");
                return 0;
            }
            send(source, "SABLE_M22_INSPECT " + assembler.inspect());
            return 1;
        }

        int count = 0;
        for (final ServerSubLevel subLevel : subLevels) {
            count++;
            final int blockCount = SimAssemblyHelper.collectBlocks(subLevel.getLevel(), subLevel).size();
            final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(subLevel.getLevel());
            final RigidBodyHandle handle = physicsSystem == null ? null : physicsSystem.getPhysicsHandle(subLevel);
            send(source, "SABLE_M22_INSPECT state=ASSEMBLED"
                    + " sableId=" + subLevel.getUniqueId()
                    + " blockCount=" + blockCount
                    + " blockEntityCount=" + countBlockEntities(subLevel.getLevel(), subLevel)
                    + " visibleOrigin=" + subLevel.logicalPose().position()
                    + " rawBounds=" + subLevel.getPlot().getBoundingBox()
                    + " mass=" + subLevel.getMassTracker().getMass()
                    + " centerOfMass=" + subLevel.getMassTracker().getCenterOfMass()
                    + " bodyRegistered=" + (physicsSystem != null && physicsSystem.getPipeline().isBodyRegistered(subLevel))
                    + " collisionGeometryPresent=" + (physicsSystem != null && physicsSystem.hasUploadedCollisionGeometry(subLevel))
                    + " serializationReady=" + (subLevel.getLastSerializationPointer() != null)
                    + " handleValid=" + (handle != null && handle.isValid()));
        }
        return count;
    }

    private static int nudge(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(ctx, "selector");
        final SubLevelPhysicsSystem physicsSystem = SableCommandHelper.requireSubLevelPhysicsSystem(ctx);
        final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        if (handle == null || !handle.isValid()) {
            send(ctx.getSource(), "SABLE_M22_NUDGE status=FAIL reason=missingRigidBodyHandle");
            return 0;
        }

        final Vector3d before = new Vector3d(subLevel.logicalPose().position());
        final Vector3d after = new Vector3d(before)
                .add(DoubleArgumentType.getDouble(ctx, "dx"),
                        DoubleArgumentType.getDouble(ctx, "dy"),
                        DoubleArgumentType.getDouble(ctx, "dz"));
        handle.teleport(after, subLevel.logicalPose().orientation());
        send(ctx.getSource(), "SABLE_M22_NUDGE status=PASS before=" + before + " after=" + after);
        return 1;
    }

    private static PhysicsAssemblerBlockEntity findNearestParentAssembler(final ServerLevel level,
                                                                          final BlockPos center,
                                                                          final int radius) {
        PhysicsAssemblerBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (final BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (level.getBlockEntity(pos) instanceof final PhysicsAssemblerBlockEntity assembler) {
                final double distance = pos.distSqr(center);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = assembler;
                }
            }
        }
        return best;
    }

    private static int countBlockEntities(final ServerLevel level, final ServerSubLevel subLevel) {
        int count = 0;
        for (final BlockPos block : SimAssemblyHelper.collectBlocks(level, subLevel)) {
            if (level.getBlockEntity(block) != null) {
                count++;
            }
        }
        return count;
    }

    private static void send(final CommandSourceStack source, final String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }
}
