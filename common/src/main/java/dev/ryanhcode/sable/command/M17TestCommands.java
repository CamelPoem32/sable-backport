package dev.ryanhcode.sable.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** M17 specialized kinetic visual runtime harness; visual PASS remains user-observed evidence. */
public final class M17TestCommands {
    private static final ResourceLocation CREATIVE_MOTOR_ID = new ResourceLocation("create", "creative_motor");
    private static final ResourceLocation SHAFT_ID = new ResourceLocation("create", "shaft");
    private static final ResourceLocation GEARBOX_ID = new ResourceLocation("create", "gearbox");
    private static final ResourceLocation ENCASED_SHAFT_ID = new ResourceLocation("create", "andesite_encased_shaft");
    private static final ResourceLocation CLUTCH_ID = new ResourceLocation("create", "clutch");
    private static final ResourceLocation GEARSHIFT_ID = new ResourceLocation("create", "gearshift");
    private static final ResourceLocation COGWHEEL_ID = new ResourceLocation("create", "cogwheel");
    private static final ResourceLocation REDSTONE_BLOCK_ID = new ResourceLocation("minecraft", "redstone_block");
    private static final BlockPos MOTOR_LOCAL = new BlockPos(0, 0, 0);
    private static final BlockPos INPUT_SHAFT_LOCAL = new BlockPos(1, 0, 0);
    private static final BlockPos GEARBOX_LOCAL = new BlockPos(2, 0, 0);
    private static final BlockPos X_OUTPUT_SHAFT_LOCAL = new BlockPos(3, 0, 0);
    private static final BlockPos Z_OUTPUT_SHAFT_LOCAL = new BlockPos(2, 0, 1);
    private static final BlockPos ENCASED_SHAFT_LOCAL = new BlockPos(4, 0, 0);
    private static final BlockPos CLUTCH_LOCAL = new BlockPos(5, 0, 0);
    private static final BlockPos GEARSHIFT_LOCAL = new BlockPos(6, 0, 0);
    private static final BlockPos DOWNSTREAM_SHAFT_LOCAL = new BlockPos(7, 0, 0);
    private static final BlockPos COGWHEEL_LOCAL = new BlockPos(2, 1, 0);
    private static final BlockPos CLUTCH_REDSTONE_LOCAL = new BlockPos(5, 1, 0);
    private static final BlockPos GEARSHIFT_REDSTONE_LOCAL = new BlockPos(6, 1, 0);
    private static final int DEFAULT_RPM = 32;
    private static final DynamicCommandExceptionType ERROR_M17_FAILED = new DynamicCommandExceptionType(
            message -> Component.literal("SABLE_M17 command failed: " + message));

    private M17TestCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m17")
                .then(Commands.literal("spawn_kinetics")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(M17TestCommands::spawnKinetics)))
                .then(Commands.literal("validate")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M17TestCommands::validate)))
                .then(Commands.literal("dump_layout")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M17TestCommands::dumpLayout)))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M17TestCommands::inspect)))
                .then(Commands.literal("set_speed")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .then(Commands.argument("rpm", IntegerArgumentType.integer(-256, 256))
                                        .executes(M17TestCommands::setSpeed))))
                .then(Commands.literal("toggle_clutch")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M17TestCommands::toggleClutch)))
                .then(Commands.literal("toggle_gearshift")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M17TestCommands::toggleGearshift)))
                .then(Commands.literal("snapshot")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M17TestCommands::snapshot)))
                .then(Commands.literal("test_translate_parent")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M17TestCommands::testTranslateParent)))
                .then(Commands.literal("test_rotate_parent")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M17TestCommands::testRotateParent)))
                .then(Commands.literal("save_reload_check")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M17TestCommands::saveReloadCheck)))
                .then(Commands.literal("visual_acceptance")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M17TestCommands::visualAcceptance))));
    }

    private static int spawnKinetics(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(context);
        final String name = StringArgumentType.getString(context, "name");
        ServerSubLevel subLevel = null;
        try {
            subLevel = createEmptySubLevel(context, container, name);
            final List<SubLevelBlockEditHelper.BlockChange> changes = applyBlocks(subLevel, kineticFixtureBlocks());
            setFixtureMotorSpeed(subLevel, DEFAULT_RPM);
            SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
            setFixtureMotorSpeed(subLevel, DEFAULT_RPM);
            subLevel.updateLastPose();
            final M17Stats stats = inspectStats(subLevel);
            final String line = "SABLE_M17_SPAWN name=" + name
                    + " uuid=" + subLevel.getUniqueId()
                    + " blockCount=" + stats.blockCount()
                    + " mass=" + fmt(stats.mass())
                    + " motorLocal=" + fmt(MOTOR_LOCAL)
                    + " motorState=" + getLocalBlockState(subLevel, MOTOR_LOCAL)
                    + " gearboxLocal=" + fmt(GEARBOX_LOCAL)
                    + " gearboxState=" + getLocalBlockState(subLevel, GEARBOX_LOCAL)
                    + " clutchLocal=" + fmt(CLUTCH_LOCAL)
                    + " gearshiftLocal=" + fmt(GEARSHIFT_LOCAL)
                    + " defaultRpm=" + DEFAULT_RPM
                    + " visualAcceptance=USER_OBSERVED_RUNTIME_REQUIRED";
            send(context, line);
            Sable.LOGGER.info(line);
            return 1;
        } catch (final RuntimeException exception) {
            rollbackSpawnSubLevel(container, subLevel, name, exception);
            throw commandFailure(exception);
        }
    }

    private static int validate(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck check = checkFixture(subLevel);
        final String line = "SABLE_M17_VALIDATE id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " status=" + pass(check.ready())
                + " FIXTURE_LAYOUT=" + pass(check.layoutValid())
                + " KINETIC_DRIVE=" + pass(check.kineticDriveReady())
                + " SPECIALIZED_RENDERERS_EXPECTED=" + pass(check.specializedRendererTargetsPresent())
                + " VISUAL_RUNTIME=UNVERIFIED_USER_OBSERVATION_REQUIRED"
                + " motorValue=" + check.motorValue()
                + " motorSpeed=" + fmt(check.motorSpeed())
                + " gearboxSpeed=" + fmt(check.gearboxSpeed())
                + " clutchSpeed=" + fmt(check.clutchSpeed())
                + " gearshiftSpeed=" + fmt(check.gearshiftSpeed())
                + " downstreamShaftSpeed=" + fmt(check.downstreamShaftSpeed())
                + " clutchPowered=" + check.clutchPowered()
                + " gearshiftPowered=" + check.gearshiftPowered()
                + " downstreamMechanicalResponse=" + splitShaftMechanicalResponse(check)
                + " failures=" + check.failures();
        send(context, line);
        Sable.LOGGER.info(line);
        return check.ready() ? 1 : 0;
    }

    private static int dumpLayout(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        int count = 0;
        for (final LayoutExpectation expectation : layoutExpectations()) {
            final BlockState state = getLocalBlockState(subLevel, expectation.localPos());
            final String line = "SABLE_M17_LAYOUT id=" + subLevel.getUniqueId()
                    + " localPos=" + fmt(expectation.localPos())
                    + " plotPos=" + fmt(toPlot(subLevel, expectation.localPos()))
                    + " role=" + expectation.role()
                    + " expected=" + expectation.expected()
                    + " blockId=" + blockId(state)
                    + " state=" + state
                    + " valid=" + expectation.valid(state);
            send(context, line);
            Sable.LOGGER.info(line);
            count++;
        }
        return count;
    }

    private static int inspect(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final M17Stats stats = inspectStats(subLevel);
        final String summary = "SABLE_M17_INSPECT id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " blockCount=" + stats.blockCount()
                + " mass=" + fmt(stats.mass())
                + " posePosition=" + formatVector(stats.position())
                + " linearVelocity=" + formatVector(stats.linearVelocity())
                + " angularVelocity=" + formatVector(stats.angularVelocity())
                + " rawPlotOrigin=" + fmt(subLevel.getPlot().getCenterBlock())
                + " hiddenPlotPoseTranslation=false";
        send(context, summary);
        Sable.LOGGER.info(summary);
        for (final KineticSample sample : kineticSamples(subLevel)) {
            final String line = "SABLE_M17_KINETIC id=" + subLevel.getUniqueId()
                    + " local=" + fmt(sample.localPos())
                    + " plot=" + fmt(sample.plotPos())
                    + " blockId=" + blockId(sample.state())
                    + " state=" + sample.state()
                    + " beClass=" + sample.blockEntityClass()
                    + " speed=" + fmt(sample.speed())
                    + " source=" + sample.source()
                    + " rendererFamily=" + rendererFamily(sample.state())
                    + " expectedPartialCount=" + expectedPartialCount(sample.state())
                    + " visualRuntime=UNVERIFIED";
            send(context, line);
            Sable.LOGGER.info(line);
        }
        return 1;
    }

    private static int setSpeed(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final int rpm = IntegerArgumentType.getInteger(context, "rpm");
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck before = checkFixture(subLevel);
        if (!before.controlReady()) {
            final String rejected = "SABLE_M17_CONTROL action=set_speed id=" + subLevel.getUniqueId()
                    + " requestedRpm=" + rpm
                    + " result=REJECTED_FIXTURE_INVALID"
                    + " failures=" + before.failures();
            send(context, rejected);
            Sable.LOGGER.info(rejected);
            return 0;
        }
        setFixtureMotorSpeed(subLevel, rpm);
        final FixtureCheck after = checkFixture(subLevel);
        final String line = "SABLE_M17_CONTROL action=set_speed id=" + subLevel.getUniqueId()
                + " previousMotorValue=" + before.motorValue()
                + " requestedRpm=" + rpm
                + " motorValue=" + after.motorValue()
                + " motorSpeed=" + fmt(after.motorSpeed())
                + " result=APPLIED"
                + " semantics=Create_ScrollValueBehaviour_setValue";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int toggleClutch(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return togglePoweredBlock(context, CLUTCH_LOCAL, CLUTCH_REDSTONE_LOCAL, "toggle_clutch");
    }

    private static int toggleGearshift(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return togglePoweredBlock(context, GEARSHIFT_LOCAL, GEARSHIFT_REDSTONE_LOCAL, "toggle_gearshift");
    }

    private static int snapshot(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck check = checkFixture(subLevel);
        final M17Stats stats = inspectStats(subLevel);
        final String line = "SABLE_M17_SNAPSHOT id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " status=" + pass(check.ready())
                + " motorValue=" + check.motorValue()
                + " motorSpeed=" + fmt(check.motorSpeed())
                + " gearboxSpeed=" + fmt(check.gearboxSpeed())
                + " clutchSpeed=" + fmt(check.clutchSpeed())
                + " gearshiftSpeed=" + fmt(check.gearshiftSpeed())
                + " downstreamShaftSpeed=" + fmt(check.downstreamShaftSpeed())
                + " clutchPowered=" + check.clutchPowered()
                + " gearshiftPowered=" + check.gearshiftPowered()
                + " clutchRedstoneSource=" + blockId(getLocalBlockState(subLevel, CLUTCH_REDSTONE_LOCAL))
                + " gearshiftRedstoneSource=" + blockId(getLocalBlockState(subLevel, GEARSHIFT_REDSTONE_LOCAL))
                + " downstreamMechanicalResponse=" + splitShaftMechanicalResponse(check)
                + " expectedGearboxPartials=4"
                + " expectedSplitShaftPartials=2"
                + " visualRuntime=UNVERIFIED_USER_OBSERVATION_REQUIRED"
                + " parentPosition=" + formatVector(stats.position())
                + " parentLinearVelocity=" + formatVector(stats.linearVelocity())
                + " parentAngularVelocity=" + formatVector(stats.angularVelocity())
                + " hiddenPlotPoseTranslation=false"
                + " failures=" + check.failures();
        send(context, line);
        Sable.LOGGER.info(line);
        return check.ready() ? 1 : 0;
    }

    private static int testTranslateParent(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setBodyVelocity(context, "translate_parent", new Vector3d(0.0, 1.0, 0.0), new Vector3d());
    }

    private static int testRotateParent(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setBodyVelocity(context, "rotate_parent", new Vector3d(),
                new Vector3d(0.0, Math.toRadians(20.0), 0.0));
    }

    private static int saveReloadCheck(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck check = checkFixture(subLevel);
        final String line = "SABLE_M17_SAVE_RELOAD_CHECK id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " status=" + pass(check.ready())
                + " note=run_before_and_after_manual_save_reload"
                + " motorValue=" + check.motorValue()
                + " motorSpeed=" + fmt(check.motorSpeed())
                + " gearboxSpeed=" + fmt(check.gearboxSpeed())
                + " clutchSpeed=" + fmt(check.clutchSpeed())
                + " gearshiftSpeed=" + fmt(check.gearshiftSpeed())
                + " downstreamShaftSpeed=" + fmt(check.downstreamShaftSpeed())
                + " clutchPowered=" + check.clutchPowered()
                + " gearshiftPowered=" + check.gearshiftPowered()
                + " downstreamMechanicalResponse=" + splitShaftMechanicalResponse(check)
                + " persistence=UNVERIFIED_UNTIL_MANUAL_RELOAD"
                + " failures=" + check.failures();
        send(context, line);
        Sable.LOGGER.info(line);
        return check.ready() ? 1 : 0;
    }

    private static int visualAcceptance(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck check = checkFixture(subLevel);
        final String line = "SABLE_M17_VISUAL_ACCEPTANCE id=" + subLevel.getUniqueId()
                + " machineState=" + pass(check.ready())
                + " visualPass=USER_OBSERVED_REQUIRED"
                + " gearboxExpected=visible_rotating_internal_shaft_halves"
                + " encasedShaftExpected=visible_rotating_shaft"
                + " clutchExpected=split_shaft_modifier_matches_powered_state"
                + " gearshiftExpected=split_shaft_reversal_matches_powered_state"
                + " downstreamMechanicalResponse=" + splitShaftMechanicalResponse(check)
                + " normalWorldPassThrough=UNCHANGED_BY_SABLE_GATE"
                + " hiddenPlotPoseTranslation=false"
                + " failures=" + check.failures();
        send(context, line);
        Sable.LOGGER.info(line);
        return check.ready() ? 1 : 0;
    }

    private static int togglePoweredBlock(final CommandContext<CommandSourceStack> context,
                                          final BlockPos local,
                                          final BlockPos redstoneLocal,
                                          final String action) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck before = checkFixture(subLevel);
        if (!before.controlReady()) {
            final String rejected = "SABLE_M17_CONTROL action=" + action
                    + " id=" + subLevel.getUniqueId()
                    + " result=REJECTED_FIXTURE_INVALID"
                    + " failures=" + before.failures();
            send(context, rejected);
            Sable.LOGGER.info(rejected);
            return 0;
        }
        final BlockState oldTargetState = getLocalBlockState(subLevel, local);
        final boolean previousPowered = "true".equals(statePropertyName(oldTargetState, "powered", "false"));
        final BlockState oldSignalState = getLocalBlockState(subLevel, redstoneLocal);
        final boolean previousSignal = isRedstoneSignalSource(oldSignalState);
        final BlockState newSignalState = previousSignal
                ? Blocks.AIR.defaultBlockState()
                : Blocks.REDSTONE_BLOCK.defaultBlockState();
        final BlockPos redstonePlotPos = toPlot(subLevel, redstoneLocal);
        final boolean changed = subLevel.getPlot().getEmbeddedLevelAccessor().setBlock(redstoneLocal, newSignalState, 3);
        SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, List.of(
                new SubLevelBlockEditHelper.BlockChange(redstoneLocal.immutable(), redstonePlotPos.immutable(),
                        oldSignalState, newSignalState)));
        final FixtureCheck after = checkFixture(subLevel);
        final String line = "SABLE_M17_CONTROL action=" + action
                + " id=" + subLevel.getUniqueId()
                + " local=" + fmt(local)
                + " redstoneLocal=" + fmt(redstoneLocal)
                + " previousPowered=" + previousPowered
                + " observedPowered=" + afterPowered(subLevel, local)
                + " previousRedstoneSource=" + blockId(oldSignalState)
                + " newRedstoneSource=" + blockId(newSignalState)
                + " blockChanged=" + changed
                + " previousDownstreamShaftSpeed=" + fmt(before.downstreamShaftSpeed())
                + " downstreamShaftSpeed=" + fmt(after.downstreamShaftSpeed())
                + " downstreamMechanicalResponse=" + splitShaftMechanicalResponse(after)
                + " result=APPLIED"
                + " semantics=Create_redstone_neighborChanged_detachKinetics_scheduledTick"
                + " ready=" + after.ready()
                + " failures=" + after.failures();
        send(context, line);
        Sable.LOGGER.info(line);
        return after.ready() ? 1 : 0;
    }

    private static int setBodyVelocity(final CommandContext<CommandSourceStack> context,
                                       final String preset,
                                       final Vector3dc linearVelocity,
                                       final Vector3dc angularVelocityRad) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final SubLevelPhysicsSystem physicsSystem = SableCommandHelper.requireSubLevelPhysicsSystem(context);
        final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        handle.setLinearAndAngularVelocity(linearVelocity, angularVelocityRad);
        final String line = "SABLE_M17_TEST_PARENT id=" + subLevel.getUniqueId()
                + " preset=" + preset
                + " result=APPLIED_M10_PHYSICS_VELOCITY"
                + " requestedLinear=" + formatVector(linearVelocity)
                + " requestedAngularRadS=" + formatVector(angularVelocityRad)
                + " hiddenStorageUnchanged=true"
                + " anchored=false";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static Map<BlockPos, BlockState> kineticFixtureBlocks() {
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        blocks.put(MOTOR_LOCAL, setProperty(requireBlockState(CREATIVE_MOTOR_ID), "facing", "east"));
        blocks.put(INPUT_SHAFT_LOCAL, setProperty(requireBlockState(SHAFT_ID), "axis", "x"));
        blocks.put(GEARBOX_LOCAL, setProperty(requireBlockState(GEARBOX_ID), "axis", "y"));
        blocks.put(X_OUTPUT_SHAFT_LOCAL, setProperty(requireBlockState(SHAFT_ID), "axis", "x"));
        blocks.put(Z_OUTPUT_SHAFT_LOCAL, setProperty(requireBlockState(SHAFT_ID), "axis", "z"));
        blocks.put(ENCASED_SHAFT_LOCAL, setProperty(requireBlockState(ENCASED_SHAFT_ID), "axis", "x"));
        blocks.put(CLUTCH_LOCAL, setProperty(setProperty(requireBlockState(CLUTCH_ID), "axis", "x"), "powered", "false"));
        blocks.put(GEARSHIFT_LOCAL, setProperty(setProperty(requireBlockState(GEARSHIFT_ID), "axis", "x"), "powered", "false"));
        blocks.put(DOWNSTREAM_SHAFT_LOCAL, setProperty(requireBlockState(SHAFT_ID), "axis", "x"));
        blocks.put(COGWHEEL_LOCAL, setProperty(requireBlockState(COGWHEEL_ID), "axis", "y"));
        return blocks;
    }

    private static FixtureCheck checkFixture(final ServerSubLevel subLevel) {
        final List<String> failures = new ArrayList<>();
        for (final LayoutExpectation expectation : layoutExpectations()) {
            final BlockState state = getLocalBlockState(subLevel, expectation.localPos());
            if (!expectation.valid(state)) {
                failures.add("layout_invalid_" + expectation.role() + "_at_local_" + fmt(expectation.localPos()));
            }
        }
        final boolean layoutValid = failures.isEmpty();
        final BlockEntity motor = getLocalBlockEntity(subLevel, MOTOR_LOCAL);
        final BlockEntity gearbox = getLocalBlockEntity(subLevel, GEARBOX_LOCAL);
        final BlockEntity clutch = getLocalBlockEntity(subLevel, CLUTCH_LOCAL);
        final BlockEntity gearshift = getLocalBlockEntity(subLevel, GEARSHIFT_LOCAL);
        final BlockEntity downstreamShaft = getLocalBlockEntity(subLevel, DOWNSTREAM_SHAFT_LOCAL);
        final boolean kineticDriveReady = motor != null
                && Double.isFinite(asDouble(invokeNoArgRaw(motor, "getSpeed")))
                && gearbox != null
                && Double.isFinite(asDouble(invokeNoArgRaw(gearbox, "getSpeed")))
                && clutch != null
                && Double.isFinite(asDouble(invokeNoArgRaw(clutch, "getSpeed")))
                && gearshift != null
                && Double.isFinite(asDouble(invokeNoArgRaw(gearshift, "getSpeed")))
                && downstreamShaft != null
                && Double.isFinite(asDouble(invokeNoArgRaw(downstreamShaft, "getSpeed")));
        if (!kineticDriveReady) {
            failures.add("kinetic_drive_not_ready");
        }
        final boolean specializedTargetsPresent = getLocalBlockEntity(subLevel, GEARBOX_LOCAL) != null
                && getLocalBlockEntity(subLevel, CLUTCH_LOCAL) != null
                && getLocalBlockEntity(subLevel, GEARSHIFT_LOCAL) != null;
        if (!specializedTargetsPresent) {
            failures.add("specialized_renderer_block_entities_missing");
        }
        final int motorValue = readFixtureMotorValue(subLevel);
        return new FixtureCheck(failures, layoutValid, kineticDriveReady, specializedTargetsPresent,
                layoutValid && kineticDriveReady && specializedTargetsPresent,
                layoutValid && motor != null,
                motorValue,
                asDouble(invokeNoArgRaw(motor, "getSpeed")),
                asDouble(invokeNoArgRaw(gearbox, "getSpeed")),
                asDouble(invokeNoArgRaw(clutch, "getSpeed")),
                asDouble(invokeNoArgRaw(gearshift, "getSpeed")),
                asDouble(invokeNoArgRaw(downstreamShaft, "getSpeed")),
                "true".equals(statePropertyName(getLocalBlockState(subLevel, CLUTCH_LOCAL), "powered", "false")),
                "true".equals(statePropertyName(getLocalBlockState(subLevel, GEARSHIFT_LOCAL), "powered", "false")));
    }

    private static List<LayoutExpectation> layoutExpectations() {
        return List.of(
                new LayoutExpectation(MOTOR_LOCAL, "creative_motor_x_axis_drive",
                        "create:creative_motor[facing=east]",
                        state -> blockIdMatches(state, CREATIVE_MOTOR_ID) && propertyMatches(state, "facing", "east")),
                new LayoutExpectation(INPUT_SHAFT_LOCAL, "input_shaft_reference",
                        "create:shaft[axis=x]",
                        state -> blockIdMatches(state, SHAFT_ID) && propertyMatches(state, "axis", "x")),
                new LayoutExpectation(GEARBOX_LOCAL, "gearbox_primary_canary",
                        "create:gearbox[axis=y]",
                        state -> blockIdMatches(state, GEARBOX_ID) && propertyMatches(state, "axis", "y")),
                new LayoutExpectation(X_OUTPUT_SHAFT_LOCAL, "gearbox_x_output_reference",
                        "create:shaft[axis=x]",
                        state -> blockIdMatches(state, SHAFT_ID) && propertyMatches(state, "axis", "x")),
                new LayoutExpectation(Z_OUTPUT_SHAFT_LOCAL, "gearbox_z_output_reference",
                        "create:shaft[axis=z]",
                        state -> blockIdMatches(state, SHAFT_ID) && propertyMatches(state, "axis", "z")),
                new LayoutExpectation(ENCASED_SHAFT_LOCAL, "encased_shaft_canary",
                        "create:andesite_encased_shaft[axis=x]",
                        state -> blockIdMatches(state, ENCASED_SHAFT_ID) && propertyMatches(state, "axis", "x")),
                new LayoutExpectation(CLUTCH_LOCAL, "clutch_split_shaft_canary",
                        "create:clutch[axis=x,powered=<toggle>]",
                        state -> blockIdMatches(state, CLUTCH_ID) && propertyMatches(state, "axis", "x")),
                new LayoutExpectation(GEARSHIFT_LOCAL, "gearshift_split_shaft_canary",
                        "create:gearshift[axis=x,powered=<toggle>]",
                        state -> blockIdMatches(state, GEARSHIFT_ID) && propertyMatches(state, "axis", "x")),
                new LayoutExpectation(DOWNSTREAM_SHAFT_LOCAL, "downstream_shaft_reference",
                        "create:shaft[axis=x]",
                        state -> blockIdMatches(state, SHAFT_ID) && propertyMatches(state, "axis", "x")),
                new LayoutExpectation(COGWHEEL_LOCAL, "cogwheel_reference",
                        "create:cogwheel[axis=y]",
                        state -> blockIdMatches(state, COGWHEEL_ID) && propertyMatches(state, "axis", "y")),
                new LayoutExpectation(CLUTCH_REDSTONE_LOCAL, "clutch_redstone_control_source",
                        "minecraft:air|minecraft:redstone_block",
                        M17TestCommands::isRedstoneControlState),
                new LayoutExpectation(GEARSHIFT_REDSTONE_LOCAL, "gearshift_redstone_control_source",
                        "minecraft:air|minecraft:redstone_block",
                        M17TestCommands::isRedstoneControlState));
    }

    private static List<KineticSample> kineticSamples(final ServerSubLevel subLevel) {
        final List<KineticSample> samples = new ArrayList<>();
        for (final LayoutExpectation expectation : layoutExpectations()) {
            final BlockEntity blockEntity = getLocalBlockEntity(subLevel, expectation.localPos());
            final BlockState state = getLocalBlockState(subLevel, expectation.localPos());
            samples.add(new KineticSample(expectation.localPos(), toPlot(subLevel, expectation.localPos()),
                    state, blockEntity == null ? "none" : blockEntity.getClass().getName(),
                    asDouble(invokeNoArgRaw(blockEntity, "getSpeed")), String.valueOf(readFieldRaw(blockEntity, "source"))));
        }
        return samples;
    }

    private static String rendererFamily(final BlockState state) {
        if (blockIdMatches(state, GEARBOX_ID)) {
            return "SPECIALIZED_GEARBOX_BER_OR_GearboxVisual";
        }
        if (blockIdMatches(state, CLUTCH_ID) || blockIdMatches(state, GEARSHIFT_ID)) {
            return "SPECIALIZED_SPLIT_SHAFT_BER_OR_SplitShaftVisual";
        }
        if (blockIdMatches(state, SHAFT_ID) || blockIdMatches(state, ENCASED_SHAFT_ID)) {
            return "GENERIC_KINETIC_BER_OR_ShaftVisual";
        }
        return "REFERENCE_OR_NON_KINETIC";
    }

    private static int expectedPartialCount(final BlockState state) {
        if (blockIdMatches(state, GEARBOX_ID)) {
            return 4;
        }
        if (blockIdMatches(state, CLUTCH_ID) || blockIdMatches(state, GEARSHIFT_ID)) {
            return 2;
        }
        if (blockIdMatches(state, SHAFT_ID) || blockIdMatches(state, ENCASED_SHAFT_ID)) {
            return 1;
        }
        return 0;
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
        final List<SubLevelBlockEditHelper.BlockChange> changes = new ArrayList<>(blocks.size());
        for (final Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            changes.add(SubLevelBlockEditHelper.setLocalBlock(subLevel, entry.getKey(), entry.getValue(), 3, false));
        }
        return changes;
    }

    private static M17Stats inspectStats(final ServerSubLevel subLevel) {
        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(subLevel.getLevel());
        final RigidBodyHandle handle = physicsSystem == null ? null : physicsSystem.getPhysicsHandle(subLevel);
        final MassData mass = subLevel.getMassTracker();
        return new M17Stats(countNonAirBlocks(subLevel),
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

    private static BlockEntity getLocalBlockEntity(final ServerSubLevel subLevel, final BlockPos localPos) {
        return SubLevelBlockStateLookup.getBlockEntity(subLevel, toPlot(subLevel, localPos));
    }

    private static BlockState getLocalBlockState(final ServerSubLevel subLevel, final BlockPos localPos) {
        return SubLevelBlockStateLookup.getBlockStateOrAir(subLevel, toPlot(subLevel, localPos));
    }

    private static BlockPos toPlot(final ServerSubLevel subLevel, final BlockPos localPos) {
        return SubLevelBlockEditHelper.localOffsetToPlotBlock(subLevel, localPos);
    }

    private static void setFixtureMotorSpeed(final ServerSubLevel subLevel, final int rpm) {
        final BlockEntity motor = getLocalBlockEntity(subLevel, MOTOR_LOCAL);
        if (motor == null) {
            throw new IllegalStateException("M17 fixture motor is missing at local " + fmt(MOTOR_LOCAL));
        }
        final Object behaviour = readFieldRaw(motor, "generatedSpeed");
        if (behaviour == null || invokeIntArgRaw(behaviour, "setValue", rpm) == null) {
            throw new IllegalStateException("M17 fixture motor does not expose Create ScrollValueBehaviour at local "
                    + fmt(MOTOR_LOCAL));
        }
    }

    private static int readFixtureMotorValue(final ServerSubLevel subLevel) {
        final BlockEntity motor = getLocalBlockEntity(subLevel, MOTOR_LOCAL);
        final Object behaviour = readFieldRaw(motor, "generatedSpeed");
        final Object value = invokeNoArgRaw(behaviour, "getValue");
        return value instanceof final Number number ? number.intValue() : Integer.MIN_VALUE;
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

    private static boolean isRedstoneControlState(final BlockState state) {
        return state.isAir() || isRedstoneSignalSource(state);
    }

    private static boolean isRedstoneSignalSource(final BlockState state) {
        return blockIdMatches(state, REDSTONE_BLOCK_ID);
    }

    private static boolean afterPowered(final ServerSubLevel subLevel, final BlockPos local) {
        return "true".equals(statePropertyName(getLocalBlockState(subLevel, local), "powered", "false"));
    }

    private static String splitShaftMechanicalResponse(final FixtureCheck check) {
        if (!Double.isFinite(check.clutchSpeed())
                || !Double.isFinite(check.gearshiftSpeed())
                || !Double.isFinite(check.downstreamShaftSpeed())) {
            return "UNKNOWN_SPEED_FIELDS";
        }
        if (check.clutchPowered()) {
            return Math.abs(check.downstreamShaftSpeed()) < 0.001
                    ? "OBSERVED_CLUTCH_DISCONNECTED"
                    : "PENDING_OR_FAIL_CLUTCH_EXPECTED_DOWNSTREAM_STOP";
        }
        if (Math.abs(check.gearshiftSpeed()) < 0.001 || Math.abs(check.downstreamShaftSpeed()) < 0.001) {
            return "PENDING_CREATE_KINETIC_UPDATE";
        }
        final boolean sameSign = Math.signum(check.gearshiftSpeed()) == Math.signum(check.downstreamShaftSpeed());
        if (check.gearshiftPowered()) {
            return sameSign
                    ? "PENDING_OR_FAIL_GEARSHIFT_EXPECTED_REVERSAL"
                    : "OBSERVED_GEARSHIFT_REVERSED";
        }
        return sameSign
                ? "OBSERVED_NORMAL_TRANSMISSION"
                : "PENDING_OR_FAIL_GEARSHIFT_EXPECTED_NORMAL_SIGN";
    }

    private static boolean blockIdMatches(final BlockState state, final ResourceLocation blockId) {
        return blockId.equals(blockId(state));
    }

    private static ResourceLocation blockId(final BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static boolean propertyMatches(final BlockState state, final String propertyName, final String valueName) {
        for (final Property<?> property : state.getProperties()) {
            if (property.getName().equals(propertyName)) {
                return String.valueOf(state.getValue(property)).equalsIgnoreCase(valueName);
            }
        }
        return false;
    }

    private static String statePropertyName(final BlockState state, final String propertyName, final String fallback) {
        for (final Property<?> property : state.getProperties()) {
            if (property.getName().equals(propertyName)) {
                return String.valueOf(state.getValue(property)).toLowerCase(Locale.ROOT);
            }
        }
        return fallback;
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
                // Create stores common kinetic state on superclasses.
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static @Nullable Object invokeIntArgRaw(@Nullable final Object target,
                                                    final String methodName,
                                                    final int value) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Method method = type.getDeclaredMethod(methodName, int.class);
                method.setAccessible(true);
                method.invoke(target, value);
                return Boolean.TRUE;
            } catch (final NoSuchMethodException ignored) {
                // Create stores common kinetic state on superclasses.
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
                // Create stores common kinetic state on superclasses.
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static double asDouble(@Nullable final Object value) {
        return value instanceof final Number number ? number.doubleValue() : Double.NaN;
    }

    private static void rollbackSpawnSubLevel(final ServerSubLevelContainer container, final @Nullable SubLevel subLevel,
                                              final String name, final Throwable failure) {
        if (subLevel == null || subLevel.isRemoved()) {
            return;
        }
        Sable.LOGGER.warn("SABLE_M17 phase=rollback_begin name={} id={} reason={}",
                name, subLevel.getUniqueId(), failure.getClass().getSimpleName());
        try {
            container.removeSubLevel(subLevel, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        } catch (final Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static CommandSyntaxException commandFailure(final RuntimeException exception) {
        Sable.LOGGER.error("SABLE_M17 phase=failed", exception);
        final String message = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        return ERROR_M17_FAILED.create(message);
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

    private record M17Stats(int blockCount, double mass, Vector3dc position, Vector3dc linearVelocity,
                            Vector3dc angularVelocity) {
    }

    private record KineticSample(BlockPos localPos, BlockPos plotPos, BlockState state,
                                 String blockEntityClass, double speed, String source) {
    }

    private record FixtureCheck(List<String> failures, boolean layoutValid, boolean kineticDriveReady,
                                boolean specializedRendererTargetsPresent, boolean ready, boolean controlReady,
                                int motorValue, double motorSpeed, double gearboxSpeed,
                                double clutchSpeed, double gearshiftSpeed, double downstreamShaftSpeed,
                                boolean clutchPowered, boolean gearshiftPowered) {
    }

    private record LayoutExpectation(BlockPos localPos, String role, String expected,
                                     java.util.function.Predicate<BlockState> validator) {
        private boolean valid(final BlockState state) {
            return this.validator.test(state);
        }
    }
}
