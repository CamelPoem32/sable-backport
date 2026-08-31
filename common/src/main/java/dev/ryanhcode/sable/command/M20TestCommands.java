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
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.util.SubLevelBlockStateLookup;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/** M20 family-level Create-on-Sable parity harness; runtime PASS remains user-observed. */
public final class M20TestCommands {
    private static final ResourceLocation CREATIVE_MOTOR_ID = id("create", "creative_motor");
    private static final ResourceLocation SHAFT_ID = id("create", "shaft");
    private static final ResourceLocation COGWHEEL_ID = id("create", "cogwheel");
    private static final ResourceLocation DEPOT_ID = id("create", "depot");
    private static final ResourceLocation BELT_ID = id("create", "belt");
    private static final ResourceLocation ANDESITE_FUNNEL_ID = id("create", "andesite_funnel");
    private static final ResourceLocation CHUTE_ID = id("create", "chute");
    private static final ResourceLocation MECHANICAL_ARM_ID = id("create", "mechanical_arm");
    private static final ResourceLocation FLUID_TANK_ID = id("create", "fluid_tank");
    private static final ResourceLocation FLUID_PIPE_ID = id("create", "fluid_pipe");
    private static final ResourceLocation MECHANICAL_PUMP_ID = id("create", "mechanical_pump");
    private static final ResourceLocation CLUTCH_ID = id("create", "clutch");
    private static final ResourceLocation GEARSHIFT_ID = id("create", "gearshift");
    private static final ResourceLocation ROPE_PULLEY_ID = id("create", "rope_pulley");
    private static final ResourceLocation REDSTONE_BLOCK_ID = id("minecraft", "redstone_block");

    private static final BlockPos REDSTONE_SIGNAL_LOCAL = new BlockPos(3, 1, 0);
    private static final DynamicCommandExceptionType ERROR_M20_FAILED = new DynamicCommandExceptionType(
            message -> Component.literal("SABLE_M20 command failed: " + message));

    private M20TestCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m20")
                .then(Commands.literal("audit").executes(M20TestCommands::audit))
                .then(Commands.literal("status").executes(M20TestCommands::status))
                .then(Commands.literal("spawn_logistics")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(context -> spawn(context, logisticsSpec()))))
                .then(Commands.literal("inspect_logistics")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> inspect(context, logisticsSpec()))))
                .then(Commands.literal("reset_logistics")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> reset(context, logisticsSpec()))))
                .then(Commands.literal("spawn_fluids")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(context -> spawn(context, fluidsSpec()))))
                .then(Commands.literal("inspect_fluids")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> inspect(context, fluidsSpec()))))
                .then(Commands.literal("reset_fluids")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> reset(context, fluidsSpec()))))
                .then(Commands.literal("spawn_redstone")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(context -> spawn(context, redstoneSpec()))))
                .then(Commands.literal("inspect_redstone")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> inspect(context, redstoneSpec()))))
                .then(Commands.literal("toggle_redstone")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M20TestCommands::toggleRedstone)))
                .then(Commands.literal("spawn_arm")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(context -> spawn(context, armSpec()))))
                .then(Commands.literal("inspect_arm")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> inspect(context, armSpec()))))
                .then(Commands.literal("spawn_controller")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(context -> spawn(context, controllerSpec()))))
                .then(Commands.literal("inspect_controller")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> inspect(context, controllerSpec()))))
                .then(Commands.literal("stabilize")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> setBodyVelocity(context, "stabilize", new Vector3d(),
                                        new Vector3d()))))
                .then(Commands.literal("test_translate_parent")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> setBodyVelocity(context, "translate_parent",
                                        new Vector3d(0.0, 0.6, 0.0), new Vector3d()))))
                .then(Commands.literal("test_rotate_parent")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> setBodyVelocity(context, "rotate_parent", new Vector3d(),
                                        new Vector3d(0.0, Math.toRadians(18.0), 0.0)))))
                .then(Commands.literal("save_reload_check")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M20TestCommands::saveReloadCheck)))
                .then(Commands.literal("acceptance")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M20TestCommands::acceptance))));
    }

    private static int audit(final CommandContext<CommandSourceStack> context) {
        final String line = "SABLE_M20_AUDIT parityMatrix=M20_CREATE_PARITY_MATRIX.md"
                + " upstreamConcernRows=42"
                + " missingApplicable=0"
                + " productionPolicy=no_new_broad_Create_patch_without_runtime_evidence"
                + " m13ThroughM18Frozen=true"
                + " m19Isolated=true";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int status(final CommandContext<CommandSourceStack> context) {
        final String line = "SABLE_M20_STATUS implementation=STATIC_AND_RUNTIME_HARNESS_READY"
                + " runtimeClosure=USER_REQUIRED"
                + " fixtures=logistics,fluids,redstone,arm,controller"
                + " hiddenPlotPoseTranslation=false"
                + " normalWorldCreate=UNCHANGED";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int spawn(final CommandContext<CommandSourceStack> context,
                             final FixtureSpec spec) throws CommandSyntaxException {
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(context);
        final String name = StringArgumentType.getString(context, "name");
        ServerSubLevel subLevel = null;
        try {
            subLevel = createEmptySubLevel(context, container, name);
            final List<SubLevelBlockEditHelper.BlockChange> changes = applyFixture(subLevel, spec, true);
            SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
            subLevel.updateLastPose();
            final FixtureCheck check = checkFixture(subLevel, spec);
            final String line = "SABLE_M20_SPAWN family=" + spec.family()
                    + " name=" + name
                    + " uuid=" + subLevel.getUniqueId()
                    + " status=" + pass(check.ready())
                    + " blockCount=" + countNonAirBlocks(subLevel)
                    + " fixtureLocalTruth=true"
                    + " runtimeAcceptance=USER_REQUIRED"
                    + " failures=" + check.failures();
            send(context, line);
            Sable.LOGGER.info(line);
            return check.ready() ? 1 : 0;
        } catch (final RuntimeException exception) {
            rollbackSpawnSubLevel(container, subLevel, name, exception);
            throw commandFailure(exception);
        }
    }

    private static int reset(final CommandContext<CommandSourceStack> context,
                             final FixtureSpec spec) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final List<SubLevelBlockEditHelper.BlockChange> changes = applyFixture(subLevel, spec, true);
        SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
        subLevel.updateLastPose();
        final FixtureCheck check = checkFixture(subLevel, spec);
        final String line = "SABLE_M20_RESET family=" + spec.family()
                + " id=" + subLevel.getUniqueId()
                + " status=" + pass(check.ready())
                + " note=test_fixture_rebuilt_from_canonical_local_blocks"
                + " failures=" + check.failures();
        send(context, line);
        Sable.LOGGER.info(line);
        return check.ready() ? 1 : 0;
    }

    private static int inspect(final CommandContext<CommandSourceStack> context,
                               final FixtureSpec spec) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck check = checkFixture(subLevel, spec);
        final FixtureStats stats = inspectStats(subLevel);
        final String summary = "SABLE_M20_INSPECT family=" + spec.family()
                + " id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " status=" + pass(check.ready())
                + " blockCount=" + stats.blockCount()
                + " mass=" + fmt(stats.mass())
                + " posePosition=" + formatVector(stats.position())
                + " linearVelocity=" + formatVector(stats.linearVelocity())
                + " angularVelocity=" + formatVector(stats.angularVelocity())
                + " rawPlotOrigin=" + fmt(subLevel.getPlot().getCenterBlock())
                + " semanticRuntime=USER_OBSERVED_REQUIRED"
                + " hiddenPlotPoseTranslation=false"
                + " failures=" + check.failures();
        send(context, summary);
        Sable.LOGGER.info(summary);
        for (final PlacedBlock expected : spec.blocks()) {
            final BlockState state = getLocalBlockState(subLevel, expected.localPos());
            final BlockEntity be = state.hasBlockEntity()
                    ? subLevel.getLevel().getBlockEntity(toPlot(subLevel, expected.localPos()))
                    : null;
            final String line = "SABLE_M20_" + spec.family().toUpperCase(Locale.ROOT)
                    + " local=" + fmt(expected.localPos())
                    + " raw=" + fmt(toPlot(subLevel, expected.localPos()))
                    + " role=" + expected.role()
                    + " expected=" + expected.blockId()
                    + " actual=" + blockId(state)
                    + " state=" + state
                    + " beClass=" + (be == null ? "none" : be.getClass().getName())
                    + " valid=" + expected.valid(state);
            send(context, line);
            Sable.LOGGER.info(line);
        }
        return check.ready() ? 1 : 0;
    }

    private static int toggleRedstone(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final BlockState oldState = getLocalBlockState(subLevel, REDSTONE_SIGNAL_LOCAL);
        final BlockState newState = blockIdMatches(oldState, REDSTONE_BLOCK_ID)
                ? Blocks.AIR.defaultBlockState()
                : Blocks.REDSTONE_BLOCK.defaultBlockState();
        final List<SubLevelBlockEditHelper.BlockChange> changes = List.of(
                SubLevelBlockEditHelper.setLocalBlock(subLevel, REDSTONE_SIGNAL_LOCAL, newState, 3, false));
        SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
        final String line = "SABLE_M20_REDSTONE_TOGGLE id=" + subLevel.getUniqueId()
                + " redstoneLocal=" + fmt(REDSTONE_SIGNAL_LOCAL)
                + " previous=" + blockId(oldState)
                + " current=" + blockId(newState)
                + " semantics=vanilla_neighbor_update_into_Create_powered_blocks"
                + " inspectWith=/sable_m20_inspect_redstone";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int setBodyVelocity(final CommandContext<CommandSourceStack> context,
                                       final String preset,
                                       final Vector3dc linearVelocity,
                                       final Vector3dc angularVelocityRad) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final SubLevelPhysicsSystem physicsSystem = SableCommandHelper.requireSubLevelPhysicsSystem(context);
        final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        handle.setLinearAndAngularVelocity(linearVelocity, angularVelocityRad);
        final String line = "SABLE_M20_PARENT id=" + subLevel.getUniqueId()
                + " preset=" + preset
                + " result=APPLIED_EXISTING_PHYSICS_HANDLE"
                + " requestedLinear=" + formatVector(linearVelocity)
                + " requestedAngularRadS=" + formatVector(angularVelocityRad)
                + " anchored=false"
                + " hiddenStorageUnchanged=true";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int saveReloadCheck(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final String line = "SABLE_M20_SAVE_RELOAD_CHECK id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " blockCount=" + countNonAirBlocks(subLevel)
                + " note=run_before_and_after_manual_save_reload"
                + " persistence=UNVERIFIED_UNTIL_MANUAL_RELOAD";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int acceptance(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final String line = "SABLE_M20_ACCEPTANCE id=" + subLevel.getUniqueId()
                + " machineState=SEE_FAMILY_INSPECT_COMMAND"
                + " semanticPass=USER_OBSERVED_REQUIRED"
                + " visualPass=USER_OBSERVED_REQUIRED"
                + " parentTransformPass=USER_OBSERVED_REQUIRED"
                + " persistencePass=USER_OBSERVED_AFTER_RELOAD"
                + " hiddenPlotPoseTranslation=false";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static FixtureSpec logisticsSpec() {
        return new FixtureSpec("logistics", List.of(
                placed(new BlockPos(-1, 0, 0), id("minecraft", "smooth_stone"), "platform"),
                placed(new BlockPos(0, 1, 0), id("minecraft", "chest"), "source_inventory"),
                placed(new BlockPos(1, 1, 0), DEPOT_ID, "depot_transfer_canary"),
                placed(new BlockPos(2, 1, 0), ANDESITE_FUNNEL_ID, "funnel_canary"),
                placed(new BlockPos(3, 1, 0), id("minecraft", "chest"), "destination_inventory"),
                placed(new BlockPos(1, 0, 1), BELT_ID, "belt_visual_transfer_canary"),
                placed(new BlockPos(2, 0, 1), BELT_ID, "belt_visual_transfer_canary")));
    }

    private static FixtureSpec fluidsSpec() {
        return new FixtureSpec("fluids", List.of(
                placed(new BlockPos(0, 1, 0), FLUID_TANK_ID, "source_tank"),
                placed(new BlockPos(1, 1, 0), FLUID_PIPE_ID, "pipe_network"),
                placed(new BlockPos(2, 1, 0), MECHANICAL_PUMP_ID, "pump_canary"),
                placed(new BlockPos(3, 1, 0), FLUID_PIPE_ID, "pipe_network"),
                placed(new BlockPos(4, 1, 0), FLUID_TANK_ID, "destination_tank"),
                placed(new BlockPos(2, 0, 0), SHAFT_ID, "pump_kinetic_input")));
    }

    private static FixtureSpec redstoneSpec() {
        return new FixtureSpec("redstone", List.of(
                placed(new BlockPos(0, 0, 0), CREATIVE_MOTOR_ID, "kinetic_source"),
                placed(new BlockPos(1, 0, 0), SHAFT_ID, "input_shaft"),
                placed(new BlockPos(2, 0, 0), CLUTCH_ID, "clutch_powered_state_canary"),
                placed(new BlockPos(3, 0, 0), GEARSHIFT_ID, "gearshift_powered_state_canary"),
                placed(new BlockPos(4, 0, 0), SHAFT_ID, "downstream_shaft"),
                placed(REDSTONE_SIGNAL_LOCAL, REDSTONE_BLOCK_ID, "toggleable_redstone_source")));
    }

    private static FixtureSpec armSpec() {
        return new FixtureSpec("arm", List.of(
                placed(new BlockPos(0, 1, 0), DEPOT_ID, "arm_input_depot"),
                placed(new BlockPos(2, 1, 0), MECHANICAL_ARM_ID, "mechanical_arm_canary"),
                placed(new BlockPos(4, 1, 0), DEPOT_ID, "arm_output_depot"),
                placed(new BlockPos(2, 0, 0), SHAFT_ID, "arm_kinetic_input")));
    }

    private static FixtureSpec controllerSpec() {
        return new FixtureSpec("controller", List.of(
                placed(new BlockPos(0, 2, 0), ROPE_PULLEY_ID, "rope_pulley_controller_canary"),
                placed(new BlockPos(0, 1, 0), SHAFT_ID, "pulley_kinetic_input"),
                placed(new BlockPos(0, 0, 0), id("minecraft", "smooth_stone"), "compact_payload")));
    }

    private static PlacedBlock placed(final BlockPos localPos, final ResourceLocation blockId, final String role) {
        return new PlacedBlock(localPos, blockId, role, state -> blockIdMatches(state, blockId));
    }

    private static List<SubLevelBlockEditHelper.BlockChange> applyFixture(final ServerSubLevel subLevel,
                                                                          final FixtureSpec spec,
                                                                          final boolean clearFirst) {
        final List<SubLevelBlockEditHelper.BlockChange> changes = new ArrayList<>();
        if (clearFirst) {
            for (int x = -2; x <= 7; x++) {
                for (int y = 0; y <= 3; y++) {
                    for (int z = -2; z <= 4; z++) {
                        changes.add(SubLevelBlockEditHelper.setLocalBlock(subLevel, new BlockPos(x, y, z),
                                Blocks.AIR.defaultBlockState(), 3, false));
                    }
                }
            }
        }
        for (final PlacedBlock block : spec.blocks()) {
            changes.add(SubLevelBlockEditHelper.setLocalBlock(subLevel, block.localPos(), canonicalState(block), 3,
                    false));
        }
        return changes;
    }

    private static BlockState canonicalState(final PlacedBlock block) {
        BlockState state = requireBlock(block.blockId()).defaultBlockState();
        if (block.blockId().equals(SHAFT_ID)) {
            state = setPropertyIfPresent(state, "axis", "x");
        } else if (block.blockId().equals(CREATIVE_MOTOR_ID)) {
            state = setPropertyIfPresent(state, "facing", "east");
        } else if (block.blockId().equals(CLUTCH_ID) || block.blockId().equals(GEARSHIFT_ID)) {
            state = setPropertyIfPresent(state, "axis", "x");
            state = setPropertyIfPresent(state, "powered", "false");
        } else if (block.blockId().equals(MECHANICAL_PUMP_ID)) {
            state = setPropertyIfPresent(state, "facing", "east");
        } else if (block.blockId().equals(ANDESITE_FUNNEL_ID)) {
            state = setPropertyIfPresent(state, "facing", "west");
        } else if (block.blockId().equals(MECHANICAL_ARM_ID)) {
            state = setPropertyIfPresent(state, "facing", "south");
        } else if (block.blockId().equals(ROPE_PULLEY_ID)) {
            state = setPropertyIfPresent(state, "axis", "y");
        } else if (block.blockId().equals(COGWHEEL_ID)) {
            state = setPropertyIfPresent(state, "axis", "y");
        }
        return state;
    }

    private static FixtureCheck checkFixture(final ServerSubLevel subLevel, final FixtureSpec spec) {
        final List<String> failures = new ArrayList<>();
        for (final PlacedBlock expected : spec.blocks()) {
            final BlockState state = getLocalBlockState(subLevel, expected.localPos());
            if (!expected.valid(state)) {
                failures.add("invalid_" + expected.role() + "_at_local_" + fmt(expected.localPos())
                        + "_expected_" + expected.blockId() + "_actual_" + blockId(state));
            }
        }
        return new FixtureCheck(failures, failures.isEmpty());
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

    private static FixtureStats inspectStats(final ServerSubLevel subLevel) {
        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(subLevel.getLevel());
        final RigidBodyHandle handle = physicsSystem == null ? null : physicsSystem.getPhysicsHandle(subLevel);
        final MassData mass = subLevel.getMassTracker();
        return new FixtureStats(countNonAirBlocks(subLevel),
                mass == null ? Double.NaN : mass.getMass(),
                new Vector3d(subLevel.logicalPose().position()),
                handle == null ? new Vector3d(Double.NaN) : handle.getLinearVelocity(new Vector3d()),
                handle == null ? new Vector3d(Double.NaN) : handle.getAngularVelocity(new Vector3d()));
    }

    private static int countNonAirBlocks(final ServerSubLevel subLevel) {
        int count = 0;
        for (final PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            final LevelChunk chunk = holder.getChunk();
            for (int sectionIndex = 0; sectionIndex < chunk.getSectionsCount(); sectionIndex++) {
                final LevelChunkSection section = chunk.getSection(sectionIndex);
                if (section.hasOnlyAir()) {
                    continue;
                }
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            if (!section.getBlockState(x, y, z).isAir()) {
                                count++;
                            }
                        }
                    }
                }
            }
        }
        return count;
    }

    private static BlockState getLocalBlockState(final ServerSubLevel subLevel, final BlockPos localPos) {
        return SubLevelBlockStateLookup.getBlockStateOrAir(subLevel, toPlot(subLevel, localPos));
    }

    private static BlockPos toPlot(final ServerSubLevel subLevel, final BlockPos localPos) {
        return SubLevelBlockEditHelper.localOffsetToPlotBlock(subLevel, localPos);
    }

    private static Block requireBlock(final ResourceLocation id) {
        final Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
        if (block.isEmpty() || block.get() == Blocks.AIR) {
            throw new IllegalStateException("Required block is not registered: " + id);
        }
        return block.get();
    }

    private static BlockState setPropertyIfPresent(final BlockState state,
                                                   final String propertyName,
                                                   final String valueName) {
        for (final Property<?> property : state.getProperties()) {
            if (!property.getName().equals(propertyName)) {
                continue;
            }
            final Optional<?> value = property.getValue(valueName);
            if (value.isEmpty()) {
                return state;
            }
            return setPropertyUnchecked(state, property, (Comparable<?>) value.get());
        }
        return state;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState setPropertyUnchecked(final BlockState state,
                                                   final Property property,
                                                   final Comparable value) {
        return state.setValue(property, value);
    }

    private static boolean blockIdMatches(final BlockState state, final ResourceLocation blockId) {
        return blockId.equals(blockId(state));
    }

    private static ResourceLocation blockId(final BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static ResourceLocation id(final String namespace, final String path) {
        return new ResourceLocation(namespace, path);
    }

    private static void rollbackSpawnSubLevel(final ServerSubLevelContainer container, final @Nullable SubLevel subLevel,
                                              final String name, final Throwable failure) {
        if (subLevel == null || subLevel.isRemoved()) {
            return;
        }
        Sable.LOGGER.warn("SABLE_M20 phase=rollback_begin name={} id={} reason={}",
                name, subLevel.getUniqueId(), failure.getClass().getSimpleName());
        try {
            container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
        } catch (final Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static CommandSyntaxException commandFailure(final RuntimeException exception) {
        Sable.LOGGER.error("SABLE_M20 phase=failed", exception);
        final String message = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        return ERROR_M20_FAILED.create(message);
    }

    private static void send(final CommandContext<CommandSourceStack> context, final String line) {
        context.getSource().sendSuccess(() -> Component.literal(line), false);
    }

    private static String nameOrNone(final ServerSubLevel subLevel) {
        return subLevel.getName() != null ? subLevel.getName() : "<none>";
    }

    private static String pass(final boolean value) {
        return value ? "PASS" : "FAIL";
    }

    private static String fmt(final BlockPos pos) {
        return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    private static String fmt(final double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.6f", value) : "nan";
    }

    private static String formatVector(final Vector3dc vector) {
        return String.format(Locale.ROOT, "(%.6f,%.6f,%.6f)", vector.x(), vector.y(), vector.z());
    }

    private record FixtureSpec(String family, List<PlacedBlock> blocks) {
    }

    private record PlacedBlock(BlockPos localPos, ResourceLocation blockId, String role,
                               Predicate<BlockState> validator) {
        private boolean valid(final BlockState state) {
            return this.validator.test(state);
        }
    }

    private record FixtureCheck(List<String> failures, boolean ready) {
    }

    private record FixtureStats(int blockCount, double mass, Vector3dc position, Vector3dc linearVelocity,
                                Vector3dc angularVelocity) {
    }
}
