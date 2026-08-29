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
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Diagnostic/runtime acceptance command harness for M13.1 Create Mechanical Bearing contraptions. */
public final class M13TestCommands {
    private static final ResourceLocation CREATIVE_MOTOR_ID = new ResourceLocation("create", "creative_motor");
    private static final ResourceLocation SHAFT_ID = new ResourceLocation("create", "shaft");
    private static final ResourceLocation MECHANICAL_BEARING_ID = new ResourceLocation("create", "mechanical_bearing");
    private static final ResourceLocation RADIAL_CHASSIS_ID = new ResourceLocation("create", "radial_chassis");
    private static final String CREATE_CONTROL_CONTRAPTION_CLASS =
            "com.simibubi.create.content.contraptions.IControlContraption";
    private static final BlockState DEFAULT_PLATFORM_BLOCKSTATE = Blocks.STONE.defaultBlockState();
    private static final List<BlockPos> MOVED_STRUCTURE_BLOCKS = List.of(
            new BlockPos(1, 0, 0),
            new BlockPos(1, 1, 0));
    private static final DynamicCommandExceptionType ERROR_M13_FAILED = new DynamicCommandExceptionType(
            message -> Component.literal("SABLE_M13 command failed: " + message));

    private M13TestCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m13")
                .then(Commands.literal("spawn_bearing")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(M13TestCommands::spawnBearing)))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M13TestCommands::inspect)))
                .then(Commands.literal("validate")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M13TestCommands::validate))));
    }

    private static int spawnBearing(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(context);
        final String name = StringArgumentType.getString(context, "name");
        ServerSubLevel subLevel = null;

        try {
            subLevel = createEmptySubLevel(context, container, name);
            final Map<BlockPos, BlockState> blocks = bearingFixtureBlocks();
            final List<SubLevelBlockEditHelper.BlockChange> changes = applyBlocks(subLevel, blocks);
            SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
            subLevel.updateLastPose();

            final M13Stats stats = inspectStats(subLevel);
            final String line = "SABLE_M13_SPAWN name=" + name
                    + " uuid=" + subLevel.getUniqueId()
                    + " blockCount=" + stats.blockCount()
                    + " mass=" + fmt(stats.mass())
                    + " bearingLocal=(0,0,0)"
                    + " bearingState=" + getLocalBlockState(subLevel, BlockPos.ZERO)
                    + " motorLocal=(-2,0,0)"
                    + " motorState=" + getLocalBlockState(subLevel, new BlockPos(-2, 0, 0))
                    + " shaftLocal=(-1,0,0)"
                    + " shaftState=" + getLocalBlockState(subLevel, new BlockPos(-1, 0, 0))
                    + " movedStructureLocal=" + MOVED_STRUCTURE_BLOCKS
                    + " assemblyPath=normal_create_mechanical_bearing";
            send(context, line);
            Sable.LOGGER.info(line);
            return 1;
        } catch (final RuntimeException exception) {
            rollbackSpawnSubLevel(container, subLevel, name, exception);
            throw commandFailure(exception);
        }
    }

    private static int inspect(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        sendInspect(context, subLevel);
        return 1;
    }

    private static int validate(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final List<String> failures = validateGeneric(subLevel);
        final List<BearingSnapshot> bearings = collectBearings(subLevel);
        if (bearings.isEmpty()) {
            failures.add("bearing_missing");
        }

        boolean anyAssembled = false;
        int capturedBlocks = 0;
        int plotContraptions = subLevel.getPlot().getContraptions().size();
        boolean hiddenPlotLeak = false;
        boolean containingSubLevelKnown = false;
        boolean serverRotationObserved = false;
        for (final BearingSnapshot bearing : bearings) {
            anyAssembled |= bearing.assembled();
            capturedBlocks = Math.max(capturedBlocks, bearing.capturedBlocks());
            hiddenPlotLeak |= bearing.rawEntityPositionLooksLikePlot() && !bearing.containingSubLevelKnown();
            containingSubLevelKnown |= bearing.containingSubLevelKnown();
            serverRotationObserved |= bearing.assembled()
                    && (Double.isFinite(bearing.entityAngle())
                    || Double.isFinite(bearing.entityAngleDelta())
                    || Double.isFinite(bearing.bearingAngle()));
        }

        if (anyAssembled) {
            requireInvariant(capturedBlocks >= MOVED_STRUCTURE_BLOCKS.size(), "contraption_blocks_incomplete", failures);
            requireInvariant(plotContraptions > 0, "contraption_not_registered_in_plot", failures);
            requireInvariant(!hiddenPlotLeak, "contraption_hidden_plot_uncontained", failures);
            requireInvariant(containingSubLevelKnown, "contraption_containing_sublevel_unknown", failures);
            for (final BlockPos localPos : MOVED_STRUCTURE_BLOCKS) {
                final BlockState state = getLocalBlockState(subLevel, localPos);
                requireInvariant(state.isAir(), "assembled_moved_block_still_static_" + formatBlockPos(localPos), failures);
            }
        }

        final String state = failures.isEmpty() ? (anyAssembled ? "ASSEMBLED_VALID" : "UNASSEMBLED_VALID") : "FAIL";
        final String serverAssembly = failures.isEmpty() ? "PASS" : "FAIL";
        final String controller = !anyAssembled ? "N/A"
                : containingSubLevelKnown && !hiddenPlotLeak ? "PASS" : "FAIL";
        final String serverRotation = !anyAssembled ? "N/A" : serverRotationObserved ? "PASS" : "FAIL";
        final String line = "SABLE_M13_VALIDATE state=" + state
                + " status=" + (failures.isEmpty() ? "PASS" : "FAIL")
                + " SERVER_ASSEMBLY=" + serverAssembly
                + " CONTROLLER=" + controller
                + " SERVER_ROTATION=" + serverRotation
                + " CLIENT_RENDER=UNVERIFIED"
                + " CLIENT_TARGETING=UNVERIFIED"
                + " CLIENT_EDIT=UNVERIFIED"
                + " id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " reason=" + firstFailure(failures)
                + " bearingCount=" + bearings.size()
                + " assembled=" + anyAssembled
                + " capturedBlocks=" + capturedBlocks
                + " plotContraptions=" + plotContraptions
                + " hiddenPlotLeak=" + hiddenPlotLeak
                + " failures=" + failures;
        send(context, line);
        Sable.LOGGER.info(line);
        return failures.isEmpty() ? 1 : 0;
    }

    private static Map<BlockPos, BlockState> bearingFixtureBlocks() {
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        blocks.put(new BlockPos(-2, 0, 0), setProperty(requireBlockState(CREATIVE_MOTOR_ID), "facing", "east"));
        blocks.put(new BlockPos(-1, 0, 0), setProperty(requireBlockState(SHAFT_ID), "axis", "x"));
        blocks.put(BlockPos.ZERO, setProperty(requireBlockState(MECHANICAL_BEARING_ID), "facing", "east"));
        blocks.put(new BlockPos(1, 0, 0), setProperty(
                setProperty(requireBlockState(RADIAL_CHASSIS_ID), "axis", "x"),
                "sticky_north", "true"));
        blocks.put(new BlockPos(1, 1, 0), DEFAULT_PLATFORM_BLOCKSTATE);
        for (int x = -2; x <= 0; x++) {
            blocks.put(new BlockPos(x, -1, 0), DEFAULT_PLATFORM_BLOCKSTATE);
        }
        return blocks;
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

    private static void sendInspect(final CommandContext<CommandSourceStack> context, final ServerSubLevel subLevel) {
        final M13Stats stats = inspectStats(subLevel);
        final String line = "SABLE_M13_INSPECT id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " blockCount=" + stats.blockCount()
                + " mass=" + fmt(stats.mass())
                + " selfMass=" + fmt(stats.selfMass())
                + " centerOfMass=" + formatVector(stats.centerOfMass())
                + " localBounds=" + formatLocalBounds(subLevel, stats.plotBounds())
                + " plotBounds=" + formatBounds(stats.plotBounds())
                + " rigidBodyRegistered=" + stats.physicsBodyRegistered()
                + " collisionGeometryPresent=" + stats.collisionGeometryPresent()
                + " posePosition=" + formatVector(subLevel.logicalPose().position())
                + " poseOrientation=" + formatQuaternion(subLevel.logicalPose().orientation())
                + " bearingCount=" + stats.bearingCount()
                + " plotContraptionCount=" + stats.plotContraptionCount();
        send(context, line);
        Sable.LOGGER.info(line);

        for (final BearingSnapshot bearing : collectBearings(subLevel)) {
            final String bearingLine = "SABLE_M13_BEARING id=" + subLevel.getUniqueId()
                    + " local=" + formatBlockPos(bearing.localPos())
                    + " plot=" + formatBlockPos(bearing.plotPos())
                    + " state=" + bearing.state()
                    + " beClass=" + bearing.blockEntity().getClass().getName()
                    + " removed=" + bearing.blockEntity().isRemoved()
                    + " speed=" + fmt(bearing.speed())
                    + " theoreticalSpeed=" + fmt(bearing.theoreticalSpeed())
                    + " running=" + bearing.running()
                    + " assembleNextTick=" + bearing.assembleNextTick()
                    + " bearingAngle=" + fmt(bearing.bearingAngle())
                    + " bearingPrevAngle=" + fmt(bearing.bearingPrevAngle())
                    + " bearingClientAngleDiff=" + fmt(bearing.bearingClientAngleDiff())
                    + " movedContraptionPresent=" + (bearing.movedContraption() != null)
                    + " movedContraptionClass=" + className(bearing.movedContraption())
                    + " movedContraptionEntityId=" + bearing.entityId()
                    + " movedContraptionLevel=" + bearing.entityLevelClass()
                    + " rawEntityPosition=" + formatVec3(bearing.rawEntityPosition())
                    + " controllerPos=" + formatBlockPos(bearing.entityControllerPos())
                    + " normalControllerLoaded=" + bearing.normalControllerLoaded()
                    + " normalControllerClass=" + className(bearing.normalController())
                    + " normalControllerIsControl=" + bearing.normalControllerIsControl()
                    + " sableControllerClass=" + className(bearing.sableController())
                    + " sableControllerIsControl=" + bearing.sableControllerIsControl()
                    + " entityRotationAxis=" + bearing.entityRotationAxis()
                    + " entityAngle=" + fmt(bearing.entityAngle())
                    + " entityPrevAngle=" + fmt(bearing.entityPrevAngle())
                    + " entityAngleDelta=" + fmt(bearing.entityAngleDelta())
                    + " rotationState=" + bearing.rotationState()
                    + " createAnchorLocal=" + formatBlockPos(bearing.createAnchorLocal())
                    + " createAnchorPlot=" + formatBlockPos(bearing.createAnchorPlot())
                    + " expectedVisibleAnchor=" + formatVector(bearing.expectedVisibleAnchor())
                    + " rawEntityPositionLooksLikePlot=" + bearing.rawEntityPositionLooksLikePlot()
                    + " containingSubLevelKnown=" + bearing.containingSubLevelKnown()
                    + " contraptionClass=" + className(bearing.contraption())
                    + " capturedBlocks=" + bearing.capturedBlocks()
                    + " assembled=" + bearing.assembled();
            send(context, bearingLine);
            Sable.LOGGER.info(bearingLine);
        }
    }

    private static List<String> validateGeneric(final ServerSubLevel subLevel) {
        final List<String> failures = new ObjectArrayList<>();
        final M13Stats stats = inspectStats(subLevel);
        requireInvariant(!subLevel.isRemoved(), "sublevel_removed", failures);
        requireInvariant(stats.blockCount() > 0, "block_count_zero", failures);
        requireInvariant(finitePositive(stats.mass()), "mass_not_finite_positive", failures);
        requireInvariant(finitePositive(stats.selfMass()), "self_mass_not_finite_positive", failures);
        requireInvariant(finiteVector(stats.centerOfMass()), "center_of_mass_not_finite", failures);
        requireInvariant(stats.plotBounds() != BoundingBox3i.EMPTY && stats.plotBounds().volume() > 0,
                "bounds_invalid", failures);
        requireInvariant(stats.physicsBodyRegistered(), "physics_body_not_registered", failures);
        requireInvariant(stats.collisionGeometryPresent(), "collision_geometry_missing", failures);
        return failures;
    }

    private static M13Stats inspectStats(final ServerSubLevel subLevel) {
        final List<BlockSample> blocks = scanBlocks(subLevel);
        final MassData mass = subLevel.getMassTracker();
        final MassData selfMass = subLevel.getSelfMassTracker();
        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(subLevel.getLevel());
        final RigidBodyHandle handle = physicsSystem == null ? null : physicsSystem.getPhysicsHandle(subLevel);
        return new M13Stats(
                blocks.size(),
                mass == null ? Double.NaN : mass.getMass(),
                selfMass == null ? Double.NaN : selfMass.getMass(),
                mass == null ? null : mass.getCenterOfMass(),
                subLevel.getPlot().getBoundingBox(),
                handle != null && handle.isValid()
                        && physicsSystem != null
                        && physicsSystem.getPipeline().isBodyRegistered(subLevel),
                physicsSystem != null && physicsSystem.hasUploadedCollisionGeometry(subLevel),
                collectBearings(subLevel).size(),
                subLevel.getPlot().getContraptions().size());
    }

    private static List<BearingSnapshot> collectBearings(final ServerSubLevel subLevel) {
        final List<BearingSnapshot> bearings = new ArrayList<>();
        for (final BlockEntity blockEntity : collectBlockEntities(subLevel)) {
            final BlockState state = SubLevelBlockStateLookup.getBlockStateOrAir(subLevel, blockEntity.getBlockPos());
            if (!blockIdMatches(state, MECHANICAL_BEARING_ID)
                    && !blockEntity.getClass().getName().contains("MechanicalBearingBlockEntity")) {
                continue;
            }

            final BlockPos plotPos = blockEntity.getBlockPos();
            final BlockPos localPos = plotPos.subtract(subLevel.getPlot().getCenterBlock());
            final Direction facing = directionProperty(state, "facing", Direction.EAST);
            final BlockPos createAnchorLocal = localPos.relative(facing);
            final BlockPos createAnchorPlot = SubLevelBlockEditHelper.localOffsetToPlotBlock(subLevel, createAnchorLocal);
            final Vector3d expectedVisibleAnchor = subLevel.logicalPose().transformPosition(
                    new Vector3d(createAnchorLocal.getX(), createAnchorLocal.getY(), createAnchorLocal.getZ()));

            final Object movedContraption = firstNonNull(readFieldRaw(blockEntity, "movedContraption"),
                    invokeNoArgRaw(blockEntity, "getMovedContraption"));
            final Entity entity = movedContraption instanceof Entity movedEntity ? movedEntity : null;
            final Object contraption = firstNonNull(readFieldRaw(movedContraption, "contraption"),
                    invokeNoArgRaw(movedContraption, "getContraption"));
            final BlockPos entityControllerPos = asBlockPos(readFieldRaw(movedContraption, "controllerPos"));
            final BlockEntity normalController = safeGetBlockEntity(entity == null ? null : entity.level(), entityControllerPos);
            final BlockEntity sableController = getSableBlockEntity(subLevel, entityControllerPos);
            final int capturedBlocks = countContraptionBlocks(contraption);
            final Vec3 rawEntityPosition = entity == null ? null : entity.position();
            final boolean rawLooksPlot = rawEntityPosition != null
                    && (Math.abs(rawEntityPosition.x) > 1_000_000.0
                    || Math.abs(rawEntityPosition.z) > 1_000_000.0
                    || rawEntityPosition.distanceTo(Vec3.atLowerCornerOf(createAnchorPlot)) < 4.0);
            final boolean containingSubLevelKnown = entity != null && Sable.HELPER.getContaining(entity) == subLevel;
            bearings.add(new BearingSnapshot(
                    blockEntity,
                    localPos,
                    plotPos,
                    state,
                    asDouble(invokeNoArgRaw(blockEntity, "getSpeed")),
                    asDouble(invokeNoArgRaw(blockEntity, "getTheoreticalSpeed")),
                    asBoolean(readFieldRaw(blockEntity, "running")),
                    asBoolean(readFieldRaw(blockEntity, "assembleNextTick")),
                    asDouble(readFieldRaw(blockEntity, "angle")),
                    asDouble(readFieldRaw(blockEntity, "prevAngle")),
                    asDouble(readFieldRaw(blockEntity, "clientAngleDiff")),
                    movedContraption,
                    entity == null ? -1 : entity.getId(),
                    entity == null ? "none" : entity.level().getClass().getName(),
                    rawEntityPosition,
                    entityControllerPos,
                    safeIsLoaded(entity == null ? null : entity.level(), entityControllerPos),
                    normalController,
                    implementsTypeName(normalController, CREATE_CONTROL_CONTRAPTION_CLASS),
                    sableController,
                    implementsTypeName(sableController, CREATE_CONTROL_CONTRAPTION_CLASS),
                    String.valueOf(readFieldRaw(movedContraption, "rotationAxis")),
                    asDouble(readFieldRaw(movedContraption, "angle")),
                    asDouble(readFieldRaw(movedContraption, "prevAngle")),
                    asDouble(readFieldRaw(movedContraption, "angleDelta")),
                    formatRotationState(invokeNoArgRaw(movedContraption, "getRotationState")),
                    createAnchorLocal,
                    createAnchorPlot,
                    expectedVisibleAnchor,
                    rawLooksPlot,
                    containingSubLevelKnown,
                    contraption,
                    capturedBlocks));
        }
        bearings.sort(Comparator.comparing(BearingSnapshot::localPos, M13TestCommands::compareBlockPos));
        return bearings;
    }

    private static List<BlockEntity> collectBlockEntities(final ServerSubLevel subLevel) {
        final List<BlockEntity> result = new ArrayList<>();
        for (final PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            result.addAll(holder.getChunk().getBlockEntities().values());
        }
        return result;
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
                            blocks.add(new BlockSample(localPos, plotPos, new ChunkPos(plotPos), state));
                        }
                    }
                }
            }
        }
        blocks.sort(Comparator.comparing(BlockSample::localPos, M13TestCommands::compareBlockPos));
        return blocks;
    }

    private static int countContraptionBlocks(@Nullable final Object contraption) {
        final Object blocks = invokeNoArgRaw(contraption, "getBlocks");
        return blocks instanceof final Map<?, ?> map ? map.size() : 0;
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

    private static Direction directionProperty(final BlockState state, final String propertyName, final Direction fallback) {
        for (final Property<?> property : state.getProperties()) {
            if (property.getName().equals(propertyName) && state.getValue(property) instanceof final Direction direction) {
                return direction;
            }
        }
        return fallback;
    }

    private static boolean blockIdMatches(final BlockState state, final ResourceLocation blockId) {
        return blockId.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    private static @Nullable Object firstNonNull(@Nullable final Object first, @Nullable final Object second) {
        return first != null ? first : second;
    }

    private static @Nullable BlockPos asBlockPos(@Nullable final Object value) {
        return value instanceof final BlockPos blockPos ? blockPos : null;
    }

    private static boolean safeIsLoaded(@Nullable final Level level, @Nullable final BlockPos blockPos) {
        if (level == null || blockPos == null) {
            return false;
        }
        try {
            return level.isLoaded(blockPos);
        } catch (final RuntimeException ignored) {
            return false;
        }
    }

    private static @Nullable BlockEntity safeGetBlockEntity(@Nullable final Level level, @Nullable final BlockPos blockPos) {
        if (level == null || blockPos == null) {
            return null;
        }
        try {
            return level.getBlockEntity(blockPos);
        } catch (final RuntimeException ignored) {
            return null;
        }
    }

    private static @Nullable BlockEntity getSableBlockEntity(final ServerSubLevel subLevel,
                                                            @Nullable final BlockPos plotBlockPos) {
        if (plotBlockPos == null || Sable.HELPER.getContaining(subLevel.getLevel(), plotBlockPos) != subLevel) {
            return null;
        }
        return SubLevelBlockStateLookup.getBlockEntity(subLevel, plotBlockPos);
    }

    private static boolean implementsTypeName(@Nullable final Object object, final String typeName) {
        return object != null && typeMatches(object.getClass(), typeName);
    }

    private static boolean typeMatches(@Nullable final Class<?> type, final String typeName) {
        if (type == null) {
            return false;
        }
        if (type.getName().equals(typeName)) {
            return true;
        }
        for (final Class<?> interfaceType : type.getInterfaces()) {
            if (typeMatches(interfaceType, typeName)) {
                return true;
            }
        }
        return typeMatches(type.getSuperclass(), typeName);
    }

    private static String formatRotationState(@Nullable final Object rotationState) {
        if (rotationState == null) {
            return "none";
        }
        return "(x=" + fmt(asDouble(readFieldRaw(rotationState, "xRotation")))
                + ",y=" + fmt(asDouble(readFieldRaw(rotationState, "yRotation")))
                + ",z=" + fmt(asDouble(readFieldRaw(rotationState, "zRotation"))) + ")";
    }

    private static @Nullable Object invokeNoArgRaw(@Nullable final Object target, final String methodName) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (final NoSuchMethodException ignored) {
                // Create stores useful diagnostics on superclasses.
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static @Nullable Object readFieldRaw(@Nullable final Object target, final String fieldName) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (final NoSuchFieldException ignored) {
                // Create stores common state on superclasses.
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

    private static void requireInvariant(final boolean condition, final String reason, final List<String> failures) {
        if (!condition) {
            failures.add(reason);
        }
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
        Sable.LOGGER.warn("SABLE_M13 phase=rollback_begin name={} id={} reason={}",
                name, subLevel.getUniqueId(), failure.getClass().getSimpleName());
        try {
            container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
            Sable.LOGGER.warn("SABLE_M13 phase=rollback_complete name={} id={}", name, subLevel.getUniqueId());
        } catch (final Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
            Sable.LOGGER.error("SABLE_M13 phase=rollback_failed name={} id={}", name, subLevel.getUniqueId(), cleanupFailure);
        }
    }

    private static CommandSyntaxException commandFailure(final RuntimeException exception) {
        Sable.LOGGER.error("SABLE_M13 phase=failed", exception);
        final String message = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        return ERROR_M13_FAILED.create(message);
    }

    private static void send(final CommandContext<CommandSourceStack> context, final String line) {
        context.getSource().sendSuccess(() -> Component.literal(line), false);
    }

    private static String nameOrNone(final ServerSubLevel subLevel) {
        return subLevel.getName() != null ? subLevel.getName() : "<none>";
    }

    private static String className(@Nullable final Object object) {
        return object == null ? "none" : object.getClass().getName();
    }

    private static String firstFailure(final List<String> failures) {
        return failures.isEmpty() ? "none" : failures.get(0);
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

    private static String formatVec3(@Nullable final Vec3 vector) {
        if (vector == null) {
            return "(null)";
        }
        return "(" + fmt(vector.x) + "," + fmt(vector.y) + "," + fmt(vector.z) + ")";
    }

    private static String formatQuaternion(@Nullable final Quaterniondc quaternion) {
        if (quaternion == null) {
            return "(null)";
        }
        return "(" + fmt(quaternion.x()) + "," + fmt(quaternion.y()) + "," + fmt(quaternion.z()) + "," + fmt(quaternion.w()) + ")";
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

    private record BlockSample(BlockPos localPos, BlockPos plotPos, ChunkPos chunk, BlockState state) {
    }

    private record M13Stats(int blockCount, double mass, double selfMass, @Nullable Vector3dc centerOfMass,
                            BoundingBox3ic plotBounds, boolean physicsBodyRegistered,
                            boolean collisionGeometryPresent, int bearingCount, int plotContraptionCount) {
    }

    private record BearingSnapshot(BlockEntity blockEntity, BlockPos localPos, BlockPos plotPos, BlockState state,
                                   double speed, double theoreticalSpeed, boolean running, boolean assembleNextTick,
                                   double bearingAngle, double bearingPrevAngle, double bearingClientAngleDiff,
                                   @Nullable Object movedContraption, int entityId, String entityLevelClass,
                                   @Nullable Vec3 rawEntityPosition, @Nullable BlockPos entityControllerPos,
                                   boolean normalControllerLoaded, @Nullable BlockEntity normalController,
                                   boolean normalControllerIsControl, @Nullable BlockEntity sableController,
                                   boolean sableControllerIsControl, String entityRotationAxis, double entityAngle,
                                   double entityPrevAngle, double entityAngleDelta, String rotationState,
                                   BlockPos createAnchorLocal, BlockPos createAnchorPlot,
                                   Vector3dc expectedVisibleAnchor, boolean rawEntityPositionLooksLikePlot,
                                   boolean containingSubLevelKnown, @Nullable Object contraption, int capturedBlocks) {
        boolean assembled() {
            return this.running || this.movedContraption != null || this.capturedBlocks > 0;
        }
    }
}
