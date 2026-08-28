package dev.ryanhcode.sable.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.command.SableCommandHelper;
import dev.ryanhcode.sable.api.command.SubLevelArgumentType;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelBlockEditHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.util.SubLevelBlockStateLookup;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Diagnostic/runtime acceptance command harness for M12 scale and ordinary Create compatibility. */
public final class M12TestCommands {
    private static final String CREATE_PACKAGE = "com.simibubi.create.";
    private static final ResourceLocation CREATIVE_MOTOR_ID = new ResourceLocation("create", "creative_motor");
    private static final ResourceLocation SHAFT_ID = new ResourceLocation("create", "shaft");
    private static final ResourceLocation COGWHEEL_ID = new ResourceLocation("create", "cogwheel");
    private static final ResourceLocation LARGE_COGWHEEL_ID = new ResourceLocation("create", "large_cogwheel");
    private static final ResourceLocation GEARBOX_ID = new ResourceLocation("create", "gearbox");
    private static final ResourceLocation DEPOT_ID = new ResourceLocation("create", "depot");
    private static final ResourceLocation SPEED_CONTROLLER_ID = new ResourceLocation("create", "rotation_speed_controller");
    private static final ResourceLocation SPEEDOMETER_ID = new ResourceLocation("create", "speedometer");
    private static final BlockState DEFAULT_PLATFORM_BLOCKSTATE = Blocks.STONE.defaultBlockState();
    private static final int KINETIC_SPAN_SHAFTS = 16;
    private static final int MIN_KINETIC_SPAN_ELEMENTS = 8;
    private static final DynamicCommandExceptionType ERROR_M12_FAILED = new DynamicCommandExceptionType(
            message -> Component.literal("SABLE_M12 command failed: " + message));

    private M12TestCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m12")
                .then(Commands.literal("list")
                        .executes(M12TestCommands::list))
                .then(Commands.literal("spawn_chunk_grid")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(M12TestCommands::spawnChunkGrid)))
                .then(Commands.literal("spawn_scale_grid")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("size", StringArgumentType.word())
                                        .executes(M12TestCommands::spawnScaleGrid))))
                .then(Commands.literal("spawn_kinetic_span")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(M12TestCommands::spawnKineticSpan)))
                .then(Commands.literal("spawn_create_suite")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(M12TestCommands::spawnCreateSuite)))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M12TestCommands::inspect)))
                .then(Commands.literal("validate")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M12TestCommands::validate)))
                .then(Commands.literal("boundary_info")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M12TestCommands::boundaryInfo)))
                .then(Commands.literal("add_boundary_block")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .then(Commands.argument("slot", StringArgumentType.word())
                                        .then(Commands.argument("block", BlockStateArgument.block(buildContext))
                                                .executes(M12TestCommands::addBoundaryBlock)))))
                .then(Commands.literal("remove_boundary_block")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .then(Commands.argument("slot", StringArgumentType.word())
                                        .executes(M12TestCommands::removeBoundaryBlock))))
                .then(Commands.literal("validate_boundaries")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M12TestCommands::validateBoundaries)))
                .then(Commands.literal("validate_kinetic_span")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M12TestCommands::validateKineticSpan)))
                .then(Commands.literal("inspect_create_suite")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M12TestCommands::inspectCreateSuite)))
                .then(Commands.literal("validate_create_suite")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M12TestCommands::validateCreateSuite)))
                .then(Commands.literal("persistence_snapshot")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M12TestCommands::persistenceSnapshot)))
                .then(Commands.literal("scale_info")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M12TestCommands::scaleInfo)))
                .then(Commands.literal("acceptance")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M12TestCommands::acceptance))));
    }

    private static int list(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(context);
        int count = 0;
        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            final M12Stats stats = inspectStats(subLevel);
            final Vector3dc position = subLevel.logicalPose().position();
            send(context, "SABLE_M12_LIST id=" + subLevel.getUniqueId()
                    + " name=" + nameOrNone(subLevel)
                    + " position=" + formatVector(position)
                    + " blockCount=" + stats.blockCount()
                    + " occupiedChunks=" + stats.occupiedChunks().size()
                    + " blockEntities=" + stats.blockEntityCount()
                    + " kineticBlockEntities=" + stats.kineticBlockEntityCount());
            count++;
        }
        send(context, "SABLE_M12_LIST_DONE count=" + count
                + " targetByNameSelector=@e[name=<name>,limit=1]");
        return count;
    }

    private static int spawnChunkGrid(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return spawnComputedBlocks(context, StringArgumentType.getString(context, "name"), "chunk_grid", M12TestCommands::chunkGridBlocks,
                "SABLE_M12_SPAWN");
    }

    private static int spawnScaleGrid(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final String size = StringArgumentType.getString(context, "size").toLowerCase(Locale.ROOT);
        final ScaleSize scaleSize = ScaleSize.byName(size);
        if (scaleSize == null) {
            throw ERROR_M12_FAILED.create("Unknown scale size '" + size + "'; expected small, medium, or large");
        }
        return spawnComputedBlocks(context, StringArgumentType.getString(context, "name"), "scale_grid_" + size,
                subLevel -> scaleGridBlocks(subLevel, scaleSize),
                "SABLE_M12_SCALE_SPAWN");
    }

    private static int spawnKineticSpan(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(context);
        final String name = StringArgumentType.getString(context, "name");
        ServerSubLevel subLevel = null;

        try {
            subLevel = createEmptySubLevel(context, container, name);
            final Map<BlockPos, BlockState> blocks = kineticSpanBlocks(subLevel);
            final List<SubLevelBlockEditHelper.BlockChange> changes = applyBlocks(subLevel, blocks);
            SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
            subLevel.updateLastPose();

            final M12Stats stats = inspectStats(subLevel);
            final KineticSpan span = findBestKineticSpan(subLevel);
            final BlockPos motor = span == null ? null : span.motor().localPos();
            final BlockPos endpoint = span == null ? null : span.elements().get(span.elements().size() - 1).localPos();
            final String line = "SABLE_M12_KINETIC_SPAWN id=" + subLevel.getUniqueId()
                    + " name=" + name
                    + " elementCount=" + (span == null ? 0 : span.elements().size())
                    + " occupiedChunks=" + formatChunks(stats.occupiedChunks())
                    + " motorPos=" + formatBlockPos(motor)
                    + " motorState=" + getLocalBlockState(subLevel, motor)
                    + " endpointPos=" + formatBlockPos(endpoint)
                    + " expectedRatio=1:1_same_axis_shafts";
            send(context, line);
            Sable.LOGGER.info(line);
            return 1;
        } catch (final RuntimeException exception) {
            rollbackSpawnSubLevel(container, subLevel, name, exception);
            throw commandFailure(exception);
        }
    }

    private static int spawnCreateSuite(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return spawnBlocks(context, StringArgumentType.getString(context, "name"), "create_suite", createSuiteBlocks(),
                "SABLE_M12_CREATE_SUITE_SPAWN");
    }

    private static int inspect(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        sendInspect(context, subLevel, "SABLE_M12_INSPECT");
        return 1;
    }

    private static int validate(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final List<String> failures = validateGeneric(subLevel);
        final M12Stats stats = inspectStats(subLevel);
        requireInvariant(stats.occupiedChunks().size() >= 4, "occupied_chunks_less_than_4", failures);
        requireInvariant(stats.loadedChunks().containsAll(stats.occupiedChunks()), "occupied_chunk_not_loaded", failures);
        return sendValidation(context, "SABLE_M12_VALIDATE", subLevel, stats, failures, "chunk_grid_or_scale");
    }

    private static int boundaryInfo(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        for (final BoundarySlot slot : boundarySlots(subLevel)) {
            send(context, "SABLE_M12_BOUNDARY_SLOT slot=" + slot.name()
                    + " local=" + formatBlockPos(slot.localPos())
                    + " plot=" + formatBlockPos(slot.plotPos())
                    + " chunk=" + formatChunkPos(slot.chunk()));
        }
        return 1;
    }

    private static int addBoundaryBlock(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = requireMutableTarget(context);
        final BoundarySlot slot = boundarySlot(subLevel, StringArgumentType.getString(context, "slot"));
        final BlockState state = BlockStateArgument.getBlock(context, "block").getState();
        final SubLevelBlockEditHelper.BlockChange change =
                SubLevelBlockEditHelper.setLocalBlock(subLevel, slot.localPos(), state, 3, true);
        SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, List.of(change));
        logBoundaryEdit(context, "add", subLevel, slot, change, state);
        return 1;
    }

    private static int removeBoundaryBlock(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = requireMutableTarget(context);
        final BoundarySlot slot = boundarySlot(subLevel, StringArgumentType.getString(context, "slot"));
        final SubLevelBlockEditHelper.BlockChange change =
                SubLevelBlockEditHelper.setLocalBlock(subLevel, slot.localPos(), Blocks.AIR.defaultBlockState(), 3, true);
        SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, List.of(change));
        logBoundaryEdit(context, "remove", subLevel, slot, change, Blocks.AIR.defaultBlockState());
        return 1;
    }

    private static int validateBoundaries(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final List<String> failures = validateGeneric(subLevel);
        final List<BoundarySlot> slots = boundarySlots(subLevel);
        final BoundarySlot a = slots.get(0);
        final BoundarySlot b = slots.get(1);
        final BoundarySlot c = slots.get(2);
        final BoundarySlot d = slots.get(3);
        requireInvariant(!a.chunk().equals(b.chunk()), "x_boundary_slots_same_chunk", failures);
        requireInvariant(!c.chunk().equals(d.chunk()), "z_boundary_slots_same_chunk", failures);

        final Set<ChunkPos> slotChunks = new HashSet<>();
        for (final BoundarySlot slot : slots) {
            slotChunks.add(slot.chunk());
            send(context, "SABLE_M12_BOUNDARY_STATE slot=" + slot.name()
                    + " local=" + formatBlockPos(slot.localPos())
                    + " plot=" + formatBlockPos(slot.plotPos())
                    + " chunk=" + formatChunkPos(slot.chunk())
                    + " state=" + getLocalBlockState(subLevel, slot.localPos()));
        }
        requireInvariant(slotChunks.size() >= 3, "boundary_slots_do_not_cover_multiple_chunks", failures);
        return sendValidation(context, "SABLE_M12_BOUNDARY_VALIDATE", subLevel, inspectStats(subLevel), failures,
                "boundary_slots");
    }

    private static int validateKineticSpan(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final List<String> failures = validateGeneric(subLevel);
        final KineticSpan span = findBestKineticSpan(subLevel);
        if (span == null) {
            failures.add("kinetic_span_not_found");
        } else {
            requireInvariant(span.elements().size() >= MIN_KINETIC_SPAN_ELEMENTS, "kinetic_span_too_short", failures);
            requireInvariant(span.occupiedChunks().size() >= 2, "kinetic_span_single_chunk", failures);
            validateKineticElements(span, failures);
        }

        final String line = "SABLE_M12_KINETIC_VALIDATE status=" + (failures.isEmpty() ? "PASS" : "FAIL")
                + " id=" + subLevel.getUniqueId()
                + " reason=" + firstFailure(failures)
                + " elements=" + (span == null ? 0 : span.elements().size())
                + " occupiedChunks=" + (span == null ? "[]" : formatChunks(span.occupiedChunks()))
                + " motorSpeed=" + fmt(span == null ? Double.NaN : span.motor().speed())
                + " endpointSpeed=" + fmt(span == null ? Double.NaN : span.elements().get(span.elements().size() - 1).speed())
                + " failures=" + failures;
        send(context, line);
        Sable.LOGGER.info(line);
        return failures.isEmpty() ? 1 : 0;
    }

    private static int inspectCreateSuite(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        sendCreateSuiteReport(context, subLevel);
        return 1;
    }

    private static int validateCreateSuite(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final List<String> failures = validateGeneric(subLevel);
        final Map<ResourceLocation, List<BlockSample>> byBlock = blocksById(subLevel);
        for (final CreateSuiteEntry entry : createSuiteEntries()) {
            final List<BlockSample> samples = byBlock.getOrDefault(entry.blockId(), List.of());
            final Optional<BlockSample> match = samples.stream()
                    .filter(sample -> expectedPropertiesMatch(sample.state(), entry.properties()))
                    .findFirst();
            requireInvariant(match.isPresent(), "missing_suite_block_" + entry.blockId().getPath(), failures);
            if (match.isPresent() && match.get().state().hasBlockEntity()) {
                requireInvariant(SubLevelBlockStateLookup.getBlockEntity(subLevel, match.get().plotPos()) != null,
                        "missing_suite_be_" + entry.blockId().getPath(), failures);
            }
        }
        return sendValidation(context, "SABLE_M12_CREATE_SUITE_VALIDATE", subLevel, inspectStats(subLevel), failures,
                "create_suite");
    }

    private static int persistenceSnapshot(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final M12Stats stats = inspectStats(subLevel);
        final String line = "SABLE_M12_PERSISTENCE id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " blockCount=" + stats.blockCount()
                + " occupiedChunkCount=" + stats.occupiedChunks().size()
                + " occupiedChunks=" + formatChunks(stats.occupiedChunks())
                + " mass=" + fmt(stats.mass())
                + " selfMass=" + fmt(stats.selfMass())
                + " localBounds=" + formatLocalBounds(subLevel, stats.plotBounds())
                + " plotBounds=" + formatBounds(stats.plotBounds())
                + " blockEntities=" + stats.blockEntityCount()
                + " blockEntityTypes=" + stats.blockEntityTypes()
                + " kineticBlockEntities=" + stats.kineticBlockEntityCount()
                + " kineticSummary=" + kineticSummary(subLevel);
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int scaleInfo(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final M12Stats stats = inspectStats(subLevel);
        send(context, "SABLE_M12_SCALE_INFO id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " blockCount=" + stats.blockCount()
                + " occupiedChunkCount=" + stats.occupiedChunks().size()
                + " occupiedChunks=" + formatChunks(stats.occupiedChunks())
                + " localBounds=" + formatLocalBounds(subLevel, stats.plotBounds())
                + " plotBounds=" + formatBounds(stats.plotBounds())
                + " mass=" + fmt(stats.mass()));
        return 1;
    }

    private static int acceptance(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final M12Stats stats = inspectStats(subLevel);
        final List<String> failures = validateGeneric(subLevel);
        final KineticSpan kineticSpan = findBestKineticSpan(subLevel);
        final boolean createSuiteApplies = createSuiteEntries().stream()
                .anyMatch(entry -> blocksById(subLevel).containsKey(entry.blockId()));

        String kinetics = "N/A";
        if (kineticSpan != null) {
            final List<String> kineticFailures = new ObjectArrayList<>();
            validateKineticElements(kineticSpan, kineticFailures);
            if (!kineticFailures.isEmpty()) {
                failures.addAll(kineticFailures);
            }
            kinetics = kineticFailures.isEmpty() ? "PASS" : "FAIL";
        }

        String createSuite = "N/A";
        if (createSuiteApplies) {
            final List<String> suiteFailures = new ObjectArrayList<>();
            final Map<ResourceLocation, List<BlockSample>> byBlock = blocksById(subLevel);
            for (final CreateSuiteEntry entry : createSuiteEntries()) {
                if (!byBlock.containsKey(entry.blockId())) {
                    suiteFailures.add("missing_suite_block_" + entry.blockId().getPath());
                }
            }
            if (!suiteFailures.isEmpty()) {
                failures.addAll(suiteFailures);
            }
            createSuite = suiteFailures.isEmpty() ? "PASS" : "FAIL";
        }

        final String line = "M12_ACCEPTANCE status=" + (failures.isEmpty() ? "PASS" : "FAIL")
                + " id=" + subLevel.getUniqueId()
                + " blocks=" + stats.blockCount()
                + " chunks=" + stats.occupiedChunks().size()
                + " mass=" + fmt(stats.mass())
                + " physics=" + (stats.physicsBodyRegistered() ? "PASS" : "FAIL")
                + " collision=" + (stats.collisionGeometryPresent() ? "PASS" : "FAIL")
                + " blockEntities=" + stats.blockEntityCount()
                + " kinetics=" + kinetics
                + " createSuite=" + createSuite
                + " failures=" + failures
                + " visualManual=N/A"
                + " fpsManual=N/A"
                + " wrenchUiManual=N/A";
        send(context, line);
        Sable.LOGGER.info(line);
        return failures.isEmpty() ? 1 : 0;
    }

    private static int spawnComputedBlocks(final CommandContext<CommandSourceStack> context, final String name,
                                           final String fixture,
                                           final Function<ServerSubLevel, Map<BlockPos, BlockState>> blockFactory,
                                           final String marker) throws CommandSyntaxException {
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(context);
        ServerSubLevel subLevel = null;
        try {
            subLevel = createEmptySubLevel(context, container, name);
            final Map<BlockPos, BlockState> blocks = blockFactory.apply(subLevel);
            final List<SubLevelBlockEditHelper.BlockChange> changes = applyBlocks(subLevel, blocks);
            SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
            subLevel.updateLastPose();
            final M12Stats stats = inspectStats(subLevel);
            final String line = marker
                    + " fixture=" + fixture
                    + " name=" + name
                    + " uuid=" + subLevel.getUniqueId()
                    + " blockCount=" + stats.blockCount()
                    + " mass=" + fmt(stats.mass())
                    + " localBounds=" + formatLocalBounds(subLevel, stats.plotBounds())
                    + " plotBounds=" + formatBounds(stats.plotBounds())
                    + " chunkCount=" + stats.occupiedChunks().size()
                    + " occupiedChunks=" + formatChunks(stats.occupiedChunks());
            send(context, line);
            Sable.LOGGER.info(line);
            return 1;
        } catch (final RuntimeException exception) {
            rollbackSpawnSubLevel(container, subLevel, name, exception);
            throw commandFailure(exception);
        }
    }

    private static int spawnBlocks(final CommandContext<CommandSourceStack> context, final String name,
                                   final String fixture, final Map<BlockPos, BlockState> blocks,
                                   final String marker) throws CommandSyntaxException {
        return spawnComputedBlocks(context, name, fixture, ignored -> blocks, marker);
    }

    private static ServerSubLevel createEmptySubLevel(final CommandContext<CommandSourceStack> context,
                                                      final ServerSubLevelContainer container,
                                                      final String name) {
        final Vec3 spawnPos = Vec3.atCenterOf(BlockPos.containing(context.getSource().getPosition()));
        final Pose3d pose = new Pose3d();
        pose.position().set(spawnPos.x, spawnPos.y, spawnPos.z);
        final ServerSubLevel subLevel = (ServerSubLevel) container.allocateNewSubLevel(pose);
        subLevel.setName(name);
        subLevel.getPlot().newEmptyChunk(subLevel.getPlot().getCenterChunk());
        return subLevel;
    }

    private static List<SubLevelBlockEditHelper.BlockChange> applyBlocks(final ServerSubLevel subLevel,
                                                                         final Map<BlockPos, BlockState> blocks) {
        final List<SubLevelBlockEditHelper.BlockChange> changes = new ObjectArrayList<>(blocks.size());
        for (final Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            changes.add(SubLevelBlockEditHelper.setLocalBlock(subLevel, entry.getKey(), entry.getValue(), 3, false));
        }
        return changes;
    }

    private static Map<BlockPos, BlockState> chunkGridBlocks(final ServerSubLevel subLevel) {
        return boundaryCenteredPlatform(subLevel, 20, 7, 0);
    }

    private static Map<BlockPos, BlockState> scaleGridBlocks(final ServerSubLevel subLevel, final ScaleSize scaleSize) {
        return boundaryCenteredPlatform(subLevel, scaleSize.width(), scaleSize.depth(), 0);
    }

    private static Map<BlockPos, BlockState> boundaryCenteredPlatform(final ServerSubLevel subLevel,
                                                                      final int width, final int depth,
                                                                      final int y) {
        final int beforeX = boundaryBeforePositiveX(subLevel);
        final int beforeZ = boundaryBeforePositiveZ(subLevel);
        final int minX = beforeX - (width / 2);
        final int minZ = beforeZ - (depth / 2);
        return rectangularPlatform(minX, minX + width - 1, minZ, minZ + depth - 1, y);
    }

    private static Map<BlockPos, BlockState> rectangularPlatform(final int minX, final int maxX,
                                                                final int minZ, final int maxZ,
                                                                final int y) {
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                blocks.put(new BlockPos(x, y, z), DEFAULT_PLATFORM_BLOCKSTATE);
            }
        }
        return blocks;
    }

    private static Map<BlockPos, BlockState> kineticSpanBlocks(final ServerSubLevel subLevel) {
        final int beforeX = boundaryBeforePositiveX(subLevel);
        final int motorX = beforeX - 3;
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        blocks.put(new BlockPos(motorX, 0, 0), setProperty(requireBlockState(CREATIVE_MOTOR_ID), "facing", "east"));
        for (int index = 1; index <= KINETIC_SPAN_SHAFTS; index++) {
            blocks.put(new BlockPos(motorX + index, 0, 0), setProperty(requireBlockState(SHAFT_ID), "axis", "x"));
        }
        for (int index = 0; index <= KINETIC_SPAN_SHAFTS; index++) {
            blocks.put(new BlockPos(motorX + index, -1, 0), DEFAULT_PLATFORM_BLOCKSTATE);
        }
        return blocks;
    }

    private static Map<BlockPos, BlockState> createSuiteBlocks() {
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (final CreateSuiteEntry entry : createSuiteEntries()) {
            BlockState state = requireBlockState(entry.blockId());
            for (final Map.Entry<String, String> property : entry.properties().entrySet()) {
                state = setProperty(state, property.getKey(), property.getValue());
            }
            blocks.put(entry.localPos(), state);
            blocks.put(entry.localPos().below(), DEFAULT_PLATFORM_BLOCKSTATE);
        }
        return blocks;
    }

    private static List<CreateSuiteEntry> createSuiteEntries() {
        return List.of(
                new CreateSuiteEntry(SHAFT_ID, new BlockPos(0, 0, 0), Map.of("axis", "x"), "kinetic relay"),
                new CreateSuiteEntry(COGWHEEL_ID, new BlockPos(2, 0, 0), Map.of("axis", "x"), "small cogwheel kinetic visual"),
                new CreateSuiteEntry(LARGE_COGWHEEL_ID, new BlockPos(4, 0, 0), Map.of("axis", "y"), "large cogwheel kinetic visual"),
                new CreateSuiteEntry(GEARBOX_ID, new BlockPos(6, 0, 0), Map.of("axis", "x"), "directional kinetic block"),
                new CreateSuiteEntry(CREATIVE_MOTOR_ID, new BlockPos(8, 0, 0), Map.of("facing", "east"), "value settings source"),
                new CreateSuiteEntry(DEPOT_ID, new BlockPos(10, 0, 0), Map.of(), "inventory smart block"),
                new CreateSuiteEntry(SPEED_CONTROLLER_ID, new BlockPos(12, 0, 0), Map.of("axis", "x"), "value settings kinetic controller"),
                new CreateSuiteEntry(SPEEDOMETER_ID, new BlockPos(14, 0, 0), Map.of("facing", "east", "axis_along_first", "false"), "gauge smart block")
        );
    }

    private static List<BoundarySlot> boundarySlots(final ServerSubLevel subLevel) {
        final int beforeX = boundaryBeforePositiveX(subLevel);
        final int beforeZ = boundaryBeforePositiveZ(subLevel);
        return List.of(
                boundarySlot(subLevel, "A", new BlockPos(beforeX, 1, 0)),
                boundarySlot(subLevel, "B", new BlockPos(beforeX + 1, 1, 0)),
                boundarySlot(subLevel, "C", new BlockPos(0, 1, beforeZ)),
                boundarySlot(subLevel, "D", new BlockPos(0, 1, beforeZ + 1))
        );
    }

    private static BoundarySlot boundarySlot(final ServerSubLevel subLevel, final String requestedSlot) throws CommandSyntaxException {
        final String slotName = requestedSlot.toUpperCase(Locale.ROOT);
        for (final BoundarySlot slot : boundarySlots(subLevel)) {
            if (slot.name().equals(slotName)) {
                return slot;
            }
        }
        throw ERROR_M12_FAILED.create("Unknown boundary slot '" + requestedSlot + "'; expected A, B, C, or D");
    }

    private static BoundarySlot boundarySlot(final ServerSubLevel subLevel, final String name, final BlockPos localPos) {
        final BlockPos plotPos = SubLevelBlockEditHelper.localOffsetToPlotBlock(subLevel, localPos);
        return new BoundarySlot(name, localPos, plotPos, new ChunkPos(plotPos));
    }

    private static int boundaryBeforePositiveX(final ServerSubLevel subLevel) {
        return 15 - Math.floorMod(subLevel.getPlot().getCenterBlock().getX(), 16);
    }

    private static int boundaryBeforePositiveZ(final ServerSubLevel subLevel) {
        return 15 - Math.floorMod(subLevel.getPlot().getCenterBlock().getZ(), 16);
    }

    private static void logBoundaryEdit(final CommandContext<CommandSourceStack> context, final String action,
                                        final ServerSubLevel subLevel, final BoundarySlot slot,
                                        final SubLevelBlockEditHelper.BlockChange change,
                                        final BlockState state) {
        final M12Stats stats = inspectStats(subLevel);
        final String line = "SABLE_M12_BOUNDARY_EDIT action=" + action
                + " id=" + subLevel.getUniqueId()
                + " slot=" + slot.name()
                + " local=" + formatBlockPos(slot.localPos())
                + " plot=" + formatBlockPos(slot.plotPos())
                + " chunk=" + formatChunkPos(slot.chunk())
                + " old=" + change.oldState()
                + " new=" + state
                + " blockCount=" + stats.blockCount()
                + " mass=" + fmt(stats.mass())
                + " occupiedChunks=" + formatChunks(stats.occupiedChunks());
        send(context, line);
        Sable.LOGGER.info(line);
    }

    private static void sendInspect(final CommandContext<CommandSourceStack> context, final ServerSubLevel subLevel,
                                    final String marker) {
        final M12Stats stats = inspectStats(subLevel);
        final String line = marker
                + " id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " blockCount=" + stats.blockCount()
                + " occupiedChunkCount=" + stats.occupiedChunks().size()
                + " occupiedChunks=" + formatChunks(stats.occupiedChunks())
                + " loadedChunkCount=" + stats.loadedChunks().size()
                + " mass=" + fmt(stats.mass())
                + " selfMass=" + fmt(stats.selfMass())
                + " centerOfMass=" + formatVector(stats.centerOfMass())
                + " selfCenterOfMass=" + formatVector(stats.selfCenterOfMass())
                + " localBounds=" + formatLocalBounds(subLevel, stats.plotBounds())
                + " plotBounds=" + formatBounds(stats.plotBounds())
                + " physicsBodyPresent=" + stats.physicsBodyPresent()
                + " physicsBodyRegistered=" + stats.physicsBodyRegistered()
                + " collisionGeometryPresent=" + stats.collisionGeometryPresent()
                + " collisionUploadedBlocks=" + stats.collisionUploadedBlocks()
                + " collisionUploadedSections=" + stats.collisionUploadedSections()
                + " blockEntityCount=" + stats.blockEntityCount()
                + " blockEntityTypes=" + stats.blockEntityTypes()
                + " kineticBlockEntityCount=" + stats.kineticBlockEntityCount()
                + " kineticSummary=" + kineticSummary(subLevel);
        send(context, line);
        Sable.LOGGER.info(line);
    }

    private static void sendCreateSuiteReport(final CommandContext<CommandSourceStack> context,
                                              final ServerSubLevel subLevel) {
        final Map<ResourceLocation, List<BlockSample>> byBlock = blocksById(subLevel);
        for (final CreateSuiteEntry entry : createSuiteEntries()) {
            final List<BlockSample> samples = byBlock.getOrDefault(entry.blockId(), List.of());
            final Optional<BlockSample> match = samples.stream()
                    .filter(sample -> expectedPropertiesMatch(sample.state(), entry.properties()))
                    .findFirst();
            final BlockSample sample = match.orElse(samples.isEmpty() ? null : samples.get(0));
            final BlockEntity blockEntity = sample == null ? null : SubLevelBlockStateLookup.getBlockEntity(subLevel, sample.plotPos());
            send(context, "SABLE_M12_CREATE_SUITE entry=" + entry.blockId()
                    + " purpose=" + entry.purpose().replace(' ', '_')
                    + " expectedLocal=" + formatBlockPos(entry.localPos())
                    + " actualLocal=" + formatBlockPos(sample == null ? null : sample.localPos())
                    + " state=" + (sample == null ? "missing" : sample.state())
                    + " blockEntityPresent=" + (blockEntity != null)
                    + " blockEntityClass=" + (blockEntity == null ? "none" : blockEntity.getClass().getName())
                    + " create=" + (blockEntity != null && isCreateBlockEntity(blockEntity))
                    + " kinetic=" + (blockEntity != null && isKineticBlockEntity(blockEntity))
                    + " speed=" + (blockEntity == null ? "unavailable" : invokeNoArg(blockEntity, "getSpeed"))
                    + " hasNetwork=" + (blockEntity == null ? "unavailable" : invokeNoArg(blockEntity, "hasNetwork")));
        }
    }

    private static List<String> validateGeneric(final ServerSubLevel subLevel) {
        final List<String> failures = new ObjectArrayList<>();
        final M12Stats stats = inspectStats(subLevel);
        requireInvariant(!subLevel.isRemoved(), "sublevel_removed", failures);
        requireInvariant(stats.blockCount() > 0, "block_count_zero", failures);
        requireInvariant(finitePositive(stats.mass()), "mass_not_finite_positive", failures);
        requireInvariant(finitePositive(stats.selfMass()), "self_mass_not_finite_positive", failures);
        requireInvariant(finiteVector(stats.centerOfMass()), "center_of_mass_not_finite", failures);
        requireInvariant(stats.plotBounds() != BoundingBox3i.EMPTY && stats.plotBounds().volume() > 0,
                "bounds_invalid", failures);
        requireInvariant(stats.physicsBodyPresent(), "physics_body_missing", failures);
        requireInvariant(stats.physicsBodyRegistered(), "physics_body_not_registered", failures);
        requireInvariant(stats.collisionGeometryPresent(), "collision_geometry_missing", failures);
        if (stats.collisionUploadedBlocks() > 0) {
            requireInvariant(stats.collisionUploadedBlocks() == stats.blockCount(),
                    "collision_block_count_mismatch", failures);
        }
        return failures;
    }

    private static int sendValidation(final CommandContext<CommandSourceStack> context, final String marker,
                                      final ServerSubLevel subLevel, final M12Stats stats,
                                      final List<String> failures, final String fixture) {
        final String line = marker
                + " status=" + (failures.isEmpty() ? "PASS" : "FAIL")
                + " id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " fixture=" + fixture
                + " reason=" + firstFailure(failures)
                + " blockCount=" + stats.blockCount()
                + " occupiedChunkCount=" + stats.occupiedChunks().size()
                + " mass=" + fmt(stats.mass())
                + " physicsBodyRegistered=" + stats.physicsBodyRegistered()
                + " collisionGeometryPresent=" + stats.collisionGeometryPresent()
                + " blockEntities=" + stats.blockEntityCount()
                + " kineticBlockEntities=" + stats.kineticBlockEntityCount()
                + " failures=" + failures;
        send(context, line);
        Sable.LOGGER.info(line);
        return failures.isEmpty() ? 1 : 0;
    }

    private static M12Stats inspectStats(final ServerSubLevel subLevel) {
        final List<BlockSample> blocks = scanBlocks(subLevel);
        final Set<ChunkPos> occupiedChunks = new LinkedHashSet<>();
        final Set<ChunkPos> loadedChunks = new LinkedHashSet<>();
        for (final PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            loadedChunks.add(holder.getChunk().getPos());
        }
        for (final BlockSample block : blocks) {
            occupiedChunks.add(block.chunk());
        }

        final List<BlockEntity> blockEntities = collectBlockEntities(subLevel);
        final Map<String, Integer> blockEntityTypes = new LinkedHashMap<>();
        int kineticCount = 0;
        for (final BlockEntity blockEntity : blockEntities) {
            final ResourceLocation typeId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
            blockEntityTypes.merge(typeId == null ? "unknown" : typeId.toString(), 1, Integer::sum);
            if (isKineticBlockEntity(blockEntity)) {
                kineticCount++;
            }
        }

        final MassData mass = subLevel.getMassTracker();
        final MassData selfMass = subLevel.getSelfMassTracker();
        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(subLevel.getLevel());
        final RigidBodyHandle handle = physicsSystem == null ? null : physicsSystem.getPhysicsHandle(subLevel);
        return new M12Stats(
                blocks.size(),
                sortChunks(occupiedChunks),
                sortChunks(loadedChunks),
                mass == null ? Double.NaN : mass.getMass(),
                selfMass == null ? Double.NaN : selfMass.getMass(),
                mass == null ? null : mass.getCenterOfMass(),
                selfMass == null ? null : selfMass.getCenterOfMass(),
                subLevel.getPlot().getBoundingBox(),
                handle != null && handle.isValid(),
                physicsSystem != null && physicsSystem.getPipeline().isBodyRegistered(subLevel),
                physicsSystem != null && physicsSystem.hasUploadedCollisionGeometry(subLevel),
                physicsSystem == null ? 0 : physicsSystem.getUploadedCollisionBlockCount(subLevel),
                physicsSystem == null ? 0 : physicsSystem.getUploadedCollisionSectionCount(subLevel),
                blockEntities.size(),
                blockEntityTypes,
                kineticCount);
    }

    private static List<BlockSample> scanBlocks(final ServerSubLevel subLevel) {
        final List<BlockSample> blocks = new ArrayList<>();
        for (final PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            final LevelChunk chunk = holder.getChunk();
            for (int sectionIndex = 0; sectionIndex < chunk.getSectionsCount(); sectionIndex++) {
                final LevelChunkSection section = chunk.getSection(sectionIndex);
                if (section.hasOnlyAir()) {
                    continue;
                }

                final int sectionY = chunk.getSectionYFromSectionIndex(sectionIndex);
                final int minX = chunk.getPos().getMinBlockX();
                final int minY = sectionY << 4;
                final int minZ = chunk.getPos().getMinBlockZ();
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            final BlockState state = section.getBlockState(x, y, z);
                            if (state.isAir()) {
                                continue;
                            }
                            final BlockPos plotPos = new BlockPos(minX + x, minY + y, minZ + z);
                            final BlockPos localPos = plotPos.subtract(subLevel.getPlot().getCenterBlock());
                            blocks.add(new BlockSample(localPos, plotPos, chunk.getPos(), state));
                        }
                    }
                }
            }
        }
        blocks.sort(Comparator.comparing(BlockSample::localPos, M12TestCommands::compareBlockPos));
        return blocks;
    }

    private static Map<ResourceLocation, List<BlockSample>> blocksById(final ServerSubLevel subLevel) {
        final Map<ResourceLocation, List<BlockSample>> byBlock = new LinkedHashMap<>();
        for (final BlockSample sample : scanBlocks(subLevel)) {
            byBlock.computeIfAbsent(BuiltInRegistries.BLOCK.getKey(sample.state().getBlock()), ignored -> new ArrayList<>())
                    .add(sample);
        }
        return byBlock;
    }

    private static Map<BlockPos, KineticSnapshot> collectKineticSnapshots(final ServerSubLevel subLevel) {
        final Map<BlockPos, KineticSnapshot> result = new LinkedHashMap<>();
        for (final BlockEntity blockEntity : collectBlockEntities(subLevel)) {
            if (!isKineticBlockEntity(blockEntity)) {
                continue;
            }

            final BlockPos plotPos = blockEntity.getBlockPos();
            final BlockPos localPos = plotPos.subtract(subLevel.getPlot().getCenterBlock());
            result.put(localPos, new KineticSnapshot(
                    blockEntity,
                    localPos,
                    plotPos,
                    getLocalBlockState(subLevel, localPos),
                    asDouble(invokeNoArgRaw(blockEntity, "getSpeed")),
                    asDouble(invokeNoArgRaw(blockEntity, "getTheoreticalSpeed")),
                    asBoolean(invokeNoArgRaw(blockEntity, "hasNetwork")),
                    readFieldRaw(blockEntity, "network")));
        }
        return result;
    }

    private static KineticSpan findBestKineticSpan(final ServerSubLevel subLevel) {
        final Map<BlockPos, KineticSnapshot> snapshots = collectKineticSnapshots(subLevel);
        KineticSpan best = null;
        for (final KineticSnapshot candidateMotor : snapshots.values()) {
            if (!blockIdMatches(candidateMotor.state(), CREATIVE_MOTOR_ID)
                    || !propertyMatches(candidateMotor.state(), "facing", "east")) {
                continue;
            }

            final List<KineticSnapshot> elements = new ObjectArrayList<>();
            elements.add(candidateMotor);
            BlockPos cursor = candidateMotor.localPos().relative(Direction.EAST);
            while (true) {
                final KineticSnapshot shaft = snapshots.get(cursor);
                if (shaft == null || !blockIdMatches(shaft.state(), SHAFT_ID)
                        || !propertyMatches(shaft.state(), "axis", "x")) {
                    break;
                }
                elements.add(shaft);
                cursor = cursor.relative(Direction.EAST);
            }

            if (elements.size() >= MIN_KINETIC_SPAN_ELEMENTS
                    && (best == null || elements.size() > best.elements().size())) {
                best = new KineticSpan(candidateMotor, elements, occupiedChunks(elements));
            }
        }
        return best;
    }

    private static void validateKineticElements(final KineticSpan span, final List<String> failures) {
        requireInvariant(span.motor().speedIsFinite(), "motor_speed_not_finite", failures);
        requireInvariant(Math.abs(span.motor().speed()) > 0.001, "motor_speed_zero", failures);
        for (int i = 0; i < span.elements().size(); i++) {
            final KineticSnapshot element = span.elements().get(i);
            requireInvariant(!element.blockEntity().isRemoved(), "kinetic_" + i + "_removed", failures);
            requireInvariant(element.speedIsFinite(), "kinetic_" + i + "_speed_not_finite", failures);
            requireInvariant(Math.abs(element.speed()) > 0.001, "kinetic_" + i + "_speed_zero", failures);
            requireInvariant(approximatelyEqual(Math.abs(span.motor().speed()), Math.abs(element.speed())),
                    "kinetic_" + i + "_ratio_unexpected", failures);
            requireInvariant(element.hasNetwork(), "kinetic_" + i + "_missing_network", failures);
            if (span.motor().network() != null && element.network() != null) {
                requireInvariant(span.motor().network().equals(element.network()),
                        "kinetic_" + i + "_network_mismatch", failures);
            }
        }
    }

    private static List<ChunkPos> occupiedChunks(final List<KineticSnapshot> snapshots) {
        final Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (final KineticSnapshot snapshot : snapshots) {
            chunks.add(new ChunkPos(snapshot.plotPos()));
        }
        return sortChunks(chunks);
    }

    private static String kineticSummary(final ServerSubLevel subLevel) {
        final KineticSpan span = findBestKineticSpan(subLevel);
        if (span == null) {
            return "span=none kineticCount=" + collectKineticSnapshots(subLevel).size();
        }
        return "spanElements=" + span.elements().size()
                + ",chunks=" + formatChunks(span.occupiedChunks())
                + ",motorSpeed=" + fmt(span.motor().speed())
                + ",endpointSpeed=" + fmt(span.elements().get(span.elements().size() - 1).speed());
    }

    private static List<BlockEntity> collectBlockEntities(final ServerSubLevel subLevel) {
        final List<BlockEntity> result = new ArrayList<>();
        for (final PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            result.addAll(holder.getChunk().getBlockEntities().values());
        }
        return result;
    }

    private static ServerSubLevel requireMutableTarget(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        if (subLevel.isRemoved()) {
            throw ERROR_M12_FAILED.create("Target sub-level is removed: " + subLevel.getUniqueId());
        }
        final SubLevelPhysicsSystem physicsSystem = SableCommandHelper.requireSubLevelPhysicsSystem(context);
        final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        if (handle == null || !handle.isValid() || !physicsSystem.getPipeline().isBodyRegistered(subLevel)) {
            throw ERROR_M12_FAILED.create("Target sub-level physics body is not active: " + subLevel.getUniqueId());
        }
        return subLevel;
    }

    private static BlockState getLocalBlockState(final ServerSubLevel subLevel, @Nullable final BlockPos localPos) {
        if (localPos == null) {
            return Blocks.AIR.defaultBlockState();
        }
        final BlockPos plotPos = SubLevelBlockEditHelper.localOffsetToPlotBlock(subLevel, localPos);
        return SubLevelBlockStateLookup.getBlockStateOrAir(subLevel, plotPos);
    }

    private static BlockState requireBlockState(final ResourceLocation id) {
        final Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
        if (block.isEmpty() || block.get() == Blocks.AIR) {
            throw new IllegalStateException("Required block is not registered: " + id);
        }
        return block.get().defaultBlockState();
    }

    private static BlockState setProperty(final BlockState state, final String propertyName, final String valueName) {
        for (final Property<?> property : state.getProperties()) {
            if (!property.getName().equals(propertyName)) {
                continue;
            }
            final Optional<?> value = property.getValue(valueName);
            if (value.isEmpty()) {
                throw new IllegalStateException("Property " + propertyName + " on " + state
                        + " does not accept value " + valueName);
            }
            return setPropertyUnchecked(state, property, (Comparable<?>) value.get());
        }
        throw new IllegalStateException("Block state " + state + " is missing property " + propertyName);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState setPropertyUnchecked(final BlockState state, final Property property,
                                                   final Comparable value) {
        return state.setValue(property, value);
    }

    private static boolean expectedPropertiesMatch(final BlockState state, final Map<String, String> expected) {
        for (final Map.Entry<String, String> entry : expected.entrySet()) {
            if (!propertyMatches(state, entry.getKey(), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean propertyMatches(final BlockState state, final String propertyName, final String propertyValue) {
        for (final Property<?> property : state.getProperties()) {
            if (property.getName().equals(propertyName)) {
                return propertyValue.equals(propertyValueName(state, property));
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String propertyValueName(final BlockState state, final Property property) {
        return property.getName((Comparable) state.getValue(property));
    }

    private static boolean blockIdMatches(final BlockState state, final ResourceLocation blockId) {
        return blockId.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    private static boolean isCreateBlockEntity(final BlockEntity blockEntity) {
        for (Class<?> type = blockEntity.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().startsWith(CREATE_PACKAGE)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKineticBlockEntity(final BlockEntity blockEntity) {
        for (Class<?> type = blockEntity.getClass(); type != null; type = type.getSuperclass()) {
            if ("com.simibubi.create.content.kinetics.base.KineticBlockEntity".equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    private static String invokeNoArg(final BlockEntity blockEntity, final String methodName) {
        final Object value = invokeNoArgRaw(blockEntity, methodName);
        return value == null ? "unavailable" : String.valueOf(value);
    }

    private static Object invokeNoArgRaw(final BlockEntity blockEntity, final String methodName) {
        try {
            return blockEntity.getClass().getMethod(methodName).invoke(blockEntity);
        } catch (final ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object readFieldRaw(final BlockEntity blockEntity, final String fieldName) {
        for (Class<?> type = blockEntity.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(blockEntity);
            } catch (final NoSuchFieldException ignored) {
                // Create stores common kinetic state on superclasses.
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean asBoolean(@Nullable final Object value) {
        return value instanceof Boolean && (Boolean) value;
    }

    private static double asDouble(@Nullable final Object value) {
        if (value instanceof final Number number) {
            return number.doubleValue();
        }
        return Double.NaN;
    }

    private static boolean finitePositive(final double value) {
        return Double.isFinite(value) && value > 0.0;
    }

    private static boolean finiteVector(@Nullable final Vector3dc vector) {
        return vector != null
                && Double.isFinite(vector.x())
                && Double.isFinite(vector.y())
                && Double.isFinite(vector.z());
    }

    private static boolean approximatelyEqual(final double expected, final double actual) {
        return Math.abs(expected - actual) <= Math.max(0.001, Math.abs(expected) * 0.001);
    }

    private static void requireInvariant(final boolean condition, final String reason, final List<String> failures) {
        if (!condition) {
            failures.add(reason);
        }
    }

    private static String firstFailure(final List<String> failures) {
        return failures.isEmpty() ? "none" : failures.get(0);
    }

    private static List<ChunkPos> sortChunks(final Set<ChunkPos> chunks) {
        final List<ChunkPos> result = new ArrayList<>(chunks);
        result.sort(Comparator.comparingInt((ChunkPos chunk) -> chunk.x).thenComparingInt(chunk -> chunk.z));
        return result;
    }

    private static int compareBlockPos(final BlockPos first, final BlockPos second) {
        int result = Integer.compare(first.getX(), second.getX());
        if (result != 0) {
            return result;
        }
        result = Integer.compare(first.getY(), second.getY());
        if (result != 0) {
            return result;
        }
        return Integer.compare(first.getZ(), second.getZ());
    }

    private static void rollbackSpawnSubLevel(final ServerSubLevelContainer container, final @Nullable SubLevel subLevel,
                                              final String name, final Throwable failure) {
        if (subLevel == null || subLevel.isRemoved()) {
            return;
        }
        Sable.LOGGER.warn("SABLE_M12 phase=rollback_begin name={} id={} reason={}",
                name, subLevel.getUniqueId(), failure.getClass().getSimpleName());
        try {
            container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
            Sable.LOGGER.warn("SABLE_M12 phase=rollback_complete name={} id={}", name, subLevel.getUniqueId());
        } catch (final Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
            Sable.LOGGER.error("SABLE_M12 phase=rollback_failed name={} id={}", name, subLevel.getUniqueId(), cleanupFailure);
        }
    }

    private static CommandSyntaxException commandFailure(final RuntimeException exception) {
        Sable.LOGGER.error("SABLE_M12 phase=failed", exception);
        final String message = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        return ERROR_M12_FAILED.create(message);
    }

    private static void send(final CommandContext<CommandSourceStack> context, final String line) {
        context.getSource().sendSuccess(() -> Component.literal(line), false);
    }

    private static String nameOrNone(final ServerSubLevel subLevel) {
        return subLevel.getName() != null ? subLevel.getName() : "<none>";
    }

    private static String fmt(final double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String formatVector(@Nullable final Vector3dc vector) {
        if (vector == null) {
            return "(null)";
        }
        return "(" + fmt(vector.x()) + "," + fmt(vector.y()) + "," + fmt(vector.z()) + ")";
    }

    private static String formatBounds(final BoundingBox3ic bounds) {
        if (bounds == null || bounds == BoundingBox3i.EMPTY || bounds.volume() <= 0) {
            return "EMPTY";
        }
        return "(" + bounds.minX() + "," + bounds.minY() + "," + bounds.minZ() + ")->("
                + bounds.maxX() + "," + bounds.maxY() + "," + bounds.maxZ() + ")";
    }

    private static String formatLocalBounds(final ServerSubLevel subLevel, final BoundingBox3ic bounds) {
        if (bounds == null || bounds == BoundingBox3i.EMPTY || bounds.volume() <= 0) {
            return "EMPTY";
        }
        final BlockPos center = subLevel.getPlot().getCenterBlock();
        return "(" + (bounds.minX() - center.getX()) + "," + (bounds.minY() - center.getY()) + "," + (bounds.minZ() - center.getZ()) + ")->("
                + (bounds.maxX() - center.getX()) + "," + (bounds.maxY() - center.getY()) + "," + (bounds.maxZ() - center.getZ()) + ")";
    }

    private static String formatBlockPos(@Nullable final BlockPos pos) {
        if (pos == null) {
            return "missing";
        }
        return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    private static String formatChunkPos(final ChunkPos pos) {
        return "(" + pos.x + "," + pos.z + ")";
    }

    private static String formatChunks(final List<ChunkPos> chunks) {
        final List<String> formatted = new ArrayList<>(chunks.size());
        for (final ChunkPos chunk : chunks) {
            formatted.add(formatChunkPos(chunk));
        }
        return formatted.toString();
    }

    private enum ScaleSize {
        SMALL("small", 20, 6),
        MEDIUM("medium", 20, 20),
        LARGE("large", 30, 30);

        private final String name;
        private final int width;
        private final int depth;

        ScaleSize(final String name, final int width, final int depth) {
            this.name = name;
            this.width = width;
            this.depth = depth;
        }

        static @Nullable ScaleSize byName(final String name) {
            for (final ScaleSize size : values()) {
                if (size.name.equals(name)) {
                    return size;
                }
            }
            return null;
        }

        int width() {
            return this.width;
        }

        int depth() {
            return this.depth;
        }
    }

    private record BoundarySlot(String name, BlockPos localPos, BlockPos plotPos, ChunkPos chunk) {
    }

    private record BlockSample(BlockPos localPos, BlockPos plotPos, ChunkPos chunk, BlockState state) {
    }

    private record CreateSuiteEntry(ResourceLocation blockId, BlockPos localPos, Map<String, String> properties,
                                    String purpose) {
    }

    private record KineticSnapshot(BlockEntity blockEntity, BlockPos localPos, BlockPos plotPos, BlockState state,
                                   double speed, double theoreticalSpeed, boolean hasNetwork,
                                   @Nullable Object network) {
        boolean speedIsFinite() {
            return Double.isFinite(this.speed) && Double.isFinite(this.theoreticalSpeed);
        }
    }

    private record KineticSpan(KineticSnapshot motor, List<KineticSnapshot> elements, List<ChunkPos> occupiedChunks) {
    }

    private record M12Stats(int blockCount, List<ChunkPos> occupiedChunks, List<ChunkPos> loadedChunks,
                            double mass, double selfMass, @Nullable Vector3dc centerOfMass,
                            @Nullable Vector3dc selfCenterOfMass, BoundingBox3ic plotBounds,
                            boolean physicsBodyPresent, boolean physicsBodyRegistered,
                            boolean collisionGeometryPresent, int collisionUploadedBlocks,
                            int collisionUploadedSections, int blockEntityCount,
                            Map<String, Integer> blockEntityTypes, int kineticBlockEntityCount) {
    }
}
