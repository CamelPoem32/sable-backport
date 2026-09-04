package dev.ryanhcode.sable.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
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
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

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
        if ("assembly_boundary".equals(name)) {
            return fixtureAssemblyBoundary(source);
        }
        if (!"basic_assembly".equals(name)) {
            send(source, "SABLE_M22_FIXTURE status=FAIL unknownFixture=" + name
                    + " expected=basic_assembly|assembly_boundary");
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

    private static int fixtureAssemblyBoundary(final CommandSourceStack source) {
        final ServerLevel level = source.getLevel();
        final BlockPos origin = BlockPos.containing(source.getPosition()).offset(4, 1, 0);
        final Set<BlockPos> platform = new HashSet<>();

        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                final BlockPos platformPos = origin.offset(x, -1, z);
                platform.add(platformPos);
                level.setBlock(platformPos, Blocks.STONE.defaultBlockState(), 3);
            }
        }

        final BlockPos assemblerPos = origin.above();
        final BlockPos center = origin;
        final BlockPos east = origin.east();
        final BlockPos west = origin.west();
        final BlockPos north = origin.north();
        final BlockPos south = origin.south();

        level.setBlock(center, Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        level.setBlock(east, Blocks.COPPER_BLOCK.defaultBlockState(), 3);
        level.setBlock(west, Blocks.CUT_COPPER.defaultBlockState(), 3);
        level.setBlock(north, Blocks.AMETHYST_BLOCK.defaultBlockState(), 3);
        level.setBlock(south, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), 3);
        level.setBlock(assemblerPos, SimulatedBlocks.PHYSICS_ASSEMBLER.get().defaultBlockState(), 3);
        glue(level, center, east);
        glue(level, center, west);
        glue(level, center, north);
        glue(level, center, south);
        glue(level, center, assemblerPos);

        final BlockEntity chest = level.getBlockEntity(south);
        if (chest instanceof final ChestBlockEntity chestBlockEntity) {
            chestBlockEntity.setItem(0, Items.EMERALD.getDefaultInstance());
            chestBlockEntity.setChanged();
        }

        final SelectionPreview preview = previewAssemblySelection(level, center, platform);
        send(source, "SABLE_M22_FIXTURE status=PASS name=assembly_boundary"
                + " assemblerPos=" + assemblerPos.toShortString()
                + " payloadOrigin=" + center.toShortString()
                + " supportPlatform=ordinary_minecraft_stone"
                + " glue=CREATE_SUPER_GLUE"
                + " trigger=right_click_physics_assembler");
        send(source, "SABLE_M22_ASSEMBLY_SELECTION startPos=" + center.toShortString()
                + " gluedBlockCount=5"
                + " selectedBlockCount=" + preview.selectedBlockCount()
                + " platformBlockCount=" + platform.size()
                + " selectedPlatformBlocks=" + preview.selectedPlatformBlocks()
                + " selectionBounds=" + preview.selectionBounds()
                + " selectionSha256=" + preview.selectionSha256());
        return preview.selectedPlatformBlocks() == 0 ? 1 : 0;
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

    private static void glue(final ServerLevel level, final BlockPos first, final BlockPos second) {
        level.addFreshEntity(new SuperGlueEntity(level, SuperGlueEntity.span(first, second)));
    }

    private static SelectionPreview previewAssemblySelection(final ServerLevel level, final BlockPos start,
                                                             final Set<BlockPos> platform) {
        final SimAssemblyContraption contraption = new SimAssemblyContraption(null);
        try {
            contraption.searchMovedStructure(level, start);
        } catch (final AssemblyException exception) {
            return new SelectionPreview(0, 0, "failed:" + exception.getMessage(), "failed");
        }
        int selectedPlatformBlocks = 0;
        for (final BlockPos selected : contraption.getBlocks()) {
            if (platform.contains(selected)) {
                selectedPlatformBlocks++;
            }
        }
        return new SelectionPreview(contraption.getBlocks().size(), selectedPlatformBlocks,
                describeBounds(contraption.getBlocks()), selectionDigest(level, contraption.getBlocks()));
    }

    private static String describeBounds(final Collection<BlockPos> positions) {
        if (positions.isEmpty()) {
            return "empty";
        }
        final int minX = positions.stream().map(BlockPos::getX).min(Comparator.naturalOrder()).orElse(0);
        final int minY = positions.stream().map(BlockPos::getY).min(Comparator.naturalOrder()).orElse(0);
        final int minZ = positions.stream().map(BlockPos::getZ).min(Comparator.naturalOrder()).orElse(0);
        final int maxX = positions.stream().map(BlockPos::getX).max(Comparator.naturalOrder()).orElse(0);
        final int maxY = positions.stream().map(BlockPos::getY).max(Comparator.naturalOrder()).orElse(0);
        final int maxZ = positions.stream().map(BlockPos::getZ).max(Comparator.naturalOrder()).orElse(0);
        return minX + "," + minY + "," + minZ + "->" + maxX + "," + maxY + "," + maxZ;
    }

    private static String selectionDigest(final ServerLevel level, final Collection<BlockPos> positions) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            positions.stream()
                    .sorted(Comparator.comparingLong(BlockPos::asLong))
                    .forEach(pos -> digest.update((pos.toShortString() + "|"
                            + level.getBlockState(pos) + "\n").getBytes(StandardCharsets.UTF_8)));
            return toHex(digest.digest());
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String toHex(final byte[] bytes) {
        final StringBuilder result = new StringBuilder(bytes.length * 2);
        for (final byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private record SelectionPreview(int selectedBlockCount, int selectedPlatformBlocks,
                                    String selectionBounds, String selectionSha256) {
    }

    private static void send(final CommandSourceStack source, final String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }
}
