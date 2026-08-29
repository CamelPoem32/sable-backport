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
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Diagnostic/runtime acceptance command harness for M15.1 Create Gantry contraptions. */
public final class M15TestCommands {
    private static final ResourceLocation CREATIVE_MOTOR_ID = new ResourceLocation("create", "creative_motor");
    private static final ResourceLocation GANTRY_SHAFT_ID = new ResourceLocation("create", "gantry_shaft");
    private static final ResourceLocation GANTRY_CARRIAGE_ID = new ResourceLocation("create", "gantry_carriage");
    private static final ResourceLocation RADIAL_CHASSIS_ID = new ResourceLocation("create", "radial_chassis");
    private static final String GANTRY_CONTRAPTION_ENTITY_CLASS =
            "com.simibubi.create.content.contraptions.gantry.GantryContraptionEntity";
    private static final String GANTRY_CARRIAGE_BE_CLASS =
            "com.simibubi.create.content.contraptions.gantry.GantryCarriageBlockEntity";
    private static final String GANTRY_SHAFT_BE_CLASS =
            "com.simibubi.create.content.kinetics.gantry.GantryShaftBlockEntity";
    private static final BlockState DEFAULT_MARKER_BLOCKSTATE = Blocks.STONE.defaultBlockState();
    private static final BlockPos MOTOR_LOCAL = new BlockPos(-5, 0, 0);
    private static final BlockPos CARRIAGE_LOCAL = new BlockPos(0, 1, 0);
    private static final BlockPos PAYLOAD_CHASSIS_LOCAL = new BlockPos(0, 2, 0);
    private static final BlockPos PAYLOAD_MARKER_LOCAL = new BlockPos(0, 2, 1);
    private static final BlockPos PAYLOAD_CHASSIS_OFFSET = PAYLOAD_CHASSIS_LOCAL.subtract(CARRIAGE_LOCAL);
    private static final BlockPos PAYLOAD_MARKER_OFFSET = PAYLOAD_MARKER_LOCAL.subtract(CARRIAGE_LOCAL);
    private static final Direction SHAFT_FACING = Direction.EAST;
    private static final Direction CARRIAGE_FACING = Direction.UP;
    private static final int SHAFT_MIN_X = -4;
    private static final int SHAFT_MAX_X = 4;
    private static final int SPAWN_MOTOR_RPM = 0;
    private static final int DEFAULT_FORWARD_RPM = 32;
    private static final int DEFAULT_REVERSE_RPM = -32;
    private static final DynamicCommandExceptionType ERROR_M15_FAILED = new DynamicCommandExceptionType(
            message -> Component.literal("SABLE_M15 command failed: " + message));

    private M15TestCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m15")
                .then(Commands.literal("list")
                        .executes(M15TestCommands::list))
                .then(Commands.literal("spawn_gantry")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(M15TestCommands::spawnGantry)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M15TestCommands::remove)))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M15TestCommands::inspect)))
                .then(Commands.literal("validate")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M15TestCommands::validate)))
                .then(Commands.literal("diagnose")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M15TestCommands::diagnose)))
                .then(Commands.literal("snapshot")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M15TestCommands::snapshot)))
                .then(Commands.literal("dump_layout")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M15TestCommands::dumpLayout)))
                .then(Commands.literal("captured")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M15TestCommands::captured)))
                .then(Commands.literal("lifecycle")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M15TestCommands::lifecycle)))
                .then(Commands.literal("forward")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M15TestCommands::forward)))
                .then(Commands.literal("reverse")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M15TestCommands::reverse)))
                .then(Commands.literal("stop")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M15TestCommands::stop)))
                .then(Commands.literal("toggle")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M15TestCommands::toggle)))
                .then(Commands.literal("set_motor_speed")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .then(Commands.argument("rpm", IntegerArgumentType.integer(-256, 256))
                                        .executes(M15TestCommands::setMotorSpeed))))
                .then(Commands.literal("reset_fixture")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M15TestCommands::resetFixture)))
                .then(Commands.literal("prepare_airborne")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M15TestCommands::prepareAirborne)))
                .then(Commands.literal("test_translate_parent")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M15TestCommands::testTranslateParent)))
                .then(Commands.literal("test_rotate_parent")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M15TestCommands::testRotateParent)))
                .then(Commands.literal("test_combined_parent")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M15TestCommands::testCombinedParent)))
                .then(Commands.literal("airborne_acceptance")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M15TestCommands::airborneAcceptance))));
    }

    private static int list(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(context);
        int count = 0;
        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.getName() == null || !subLevel.getName().startsWith("m15")) {
                continue;
            }
            final FixtureCheck fixture = checkFixture(subLevel);
            send(context, "SABLE_M15_LIST id=" + subLevel.getUniqueId()
                    + " name=" + nameOrNone(subLevel)
                    + " position=" + formatVector(subLevel.logicalPose().position())
                    + " carriageLocal=" + formatBlockPos(fixture.carriageLocal())
                    + " railIndex=" + fixture.railIndex()
                    + " state=" + fixture.state()
                    + " gantryChain=" + fixture.gantryChainValid()
                    + " speed=" + fmt(fixture.carriageSpeed())
                    + " motorValue=" + fixture.motorValue()
                    + " contraptionEntityId=" + fixture.firstContraptionEntityId()
                    + " capturedBlocks=" + fixture.capturedBlocks()
                    + " controlReady=" + fixture.controlReady()
                    + " fixtureReady=" + fixture.ready());
            count++;
        }
        send(context, "SABLE_M15_LIST_DONE count=" + count
                + " targetByNameSelector=@e[name=<name>,limit=1]");
        return count;
    }

    private static int spawnGantry(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(context);
        final String name = StringArgumentType.getString(context, "name");
        ServerSubLevel subLevel = null;

        try {
            subLevel = createEmptySubLevel(context, container, name);
            final Map<BlockPos, BlockState> blocks = gantryFixtureBlocks();
            final List<SubLevelBlockEditHelper.BlockChange> changes = applyBlocks(subLevel, blocks);
            setFixtureMotorSpeed(subLevel, SPAWN_MOTOR_RPM);
            SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
            setFixtureMotorSpeed(subLevel, SPAWN_MOTOR_RPM);
            subLevel.updateLastPose();
            final M15Stats stats = inspectStats(subLevel);
            final String line = "SABLE_M15_SPAWN name=" + name
                    + " uuid=" + subLevel.getUniqueId()
                    + " blockCount=" + stats.blockCount()
                    + " mass=" + fmt(stats.mass())
                    + " motorLocal=" + formatBlockPos(MOTOR_LOCAL)
                    + " motorState=" + getLocalBlockState(subLevel, MOTOR_LOCAL)
                    + " shaftRangeLocal=x[" + SHAFT_MIN_X + "," + SHAFT_MAX_X + "] y=0 z=0"
                    + " shaftState=create:gantry_shaft[facing=east,part=start|middle|end,powered=false]"
                    + " carriageLocal=" + formatBlockPos(CARRIAGE_LOCAL)
                    + " carriageState=" + getLocalBlockState(subLevel, CARRIAGE_LOCAL)
                    + " payloadLocal=[" + formatBlockPos(PAYLOAD_CHASSIS_LOCAL) + ","
                    + formatBlockPos(PAYLOAD_MARKER_LOCAL) + "]"
                    + " initialMotorValue=" + SPAWN_MOTOR_RPM
                    + " forwardMotorValue=" + DEFAULT_FORWARD_RPM
                    + " reverseMotorValue=" + DEFAULT_REVERSE_RPM
                    + " constructionOrdering=blocks_then_motor_stopped_then_finalize"
                    + " assemblyPath=normal_create_gantry_carriage";
            send(context, line);
            Sable.LOGGER.info(line);
            return 1;
        } catch (final RuntimeException exception) {
            rollbackSpawnSubLevel(container, subLevel, name, exception);
            throw commandFailure(exception);
        }
    }

    private static int remove(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(context);
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        if (!isM15FixtureCandidate(subLevel)) {
            throw ERROR_M15_FAILED.create("Refusing to remove non-M15-looking sub-level " + subLevel.getUniqueId());
        }
        container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
        send(context, "SABLE_M15_REMOVE id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " result=removed_sublevel_and_owned_entities");
        return 1;
    }

    private static int inspect(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        sendInspect(context, SubLevelArgumentType.getSingleSubLevel(context, "target"));
        return 1;
    }

    private static int validate(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck fixture = checkFixture(subLevel);
        final List<String> failures = new ObjectArrayList<>(validateGeneric(subLevel));
        failures.addAll(fixture.failures());
        final boolean pass = failures.isEmpty();
        final String line = "SABLE_M15_VALIDATE state=" + fixture.state()
                + " status=" + (pass ? "PASS" : "FAIL")
                + " FIXTURE_LAYOUT=" + passFail(fixture.layoutValid())
                + " KINETIC_DRIVE=" + passFail(fixture.kineticDriveReady())
                + " GANTRY_CHAIN=" + passFail(fixture.gantryChainValid())
                + " SERVER_ASSEMBLY=" + passFail(fixture.serverAssemblyReady())
                + " LIVE_CONTRAPTION=" + (fixture.liveContraptionPresent() ? "PASS" : "N/A")
                + " PAYLOAD_OWNERSHIP=" + passFail(fixture.payloadPresent() && !fixture.duplicatePayload())
                + " CONTROLLER=" + (fixture.liveContraptionPresent() ? passFail(fixture.controllerKnown()) : "N/A")
                + " SERVER_LINEAR_MOTION=" + (fixture.liveContraptionPresent() ? passFail(fixture.linearMotionObserved()) : "N/A")
                + " CLIENT_INTERPOLATION=UNVERIFIED"
                + " CLIENT_RENDER=UNVERIFIED"
                + " CLIENT_COLLISION=UNVERIFIED"
                + " CLIENT_TARGETING=UNVERIFIED"
                + " PERSISTENCE=UNVERIFIED"
                + " gantryCount=" + fixture.gantryCount()
                + " shaftCount=" + fixture.shaftCount()
                + " motorValue=" + fixture.motorValue()
                + " motorSpeed=" + fmt(fixture.motorSpeed())
                + " carriageSpeed=" + fmt(fixture.carriageSpeed())
                + " pinionMovementSpeed=" + fmt(fixture.pinionMovementSpeed())
                + " staticPayloadPresent=" + fixture.staticPayloadPresent()
                + " capturedPayloadPresent=" + fixture.capturedPayloadPresent()
                + " duplicatePayload=" + fixture.duplicatePayload()
                + " carriageLocal=" + formatBlockPos(fixture.carriageLocal())
                + " carriageRailIndex=" + fixture.railIndex()
                + " payloadChassisLocal=" + formatBlockPos(fixture.payloadChassisLocal())
                + " payloadMarkerLocal=" + formatBlockPos(fixture.payloadMarkerLocal())
                + " liveControllerLocal=" + formatBlockPos(fixture.liveControllerLocal())
                + " movementAxis=" + fixture.movementAxis()
                + " axisMotion=" + fmt(fixture.axisMotion())
                + " plotContraptions=" + subLevel.getPlot().getContraptions().size()
                + " capturedBlocks=" + fixture.capturedBlocks()
                + " hiddenPlotLeak=false"
                + " failures=" + failures;
        send(context, line);
        Sable.LOGGER.info(line);
        return pass ? 1 : 0;
    }

    private static int diagnose(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck fixture = checkFixture(subLevel);
        final String line = "SABLE_M15_DIAGNOSE id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " FIXTURE_LAYOUT=" + passFail(fixture.layoutValid())
                + " KINETIC_DRIVE=" + passFail(fixture.kineticDriveReady())
                + " GANTRY_CHAIN=" + passFail(fixture.gantryChainValid())
                + " SERVER_ASSEMBLY=" + passFail(fixture.serverAssemblyReady())
                + " LIVE_CONTRAPTION=" + (fixture.liveContraptionPresent() ? "PASS" : "N/A")
                + " PAYLOAD_OWNERSHIP=" + passFail(fixture.payloadPresent() && !fixture.duplicatePayload())
                + " CONTROLLER=" + (fixture.liveContraptionPresent() ? passFail(fixture.controllerKnown()) : "N/A")
                + " RENDER=UNVERIFIED"
                + " COLLISION=UNVERIFIED"
                + " shaftFacing=" + SHAFT_FACING
                + " carriageFacing=" + CARRIAGE_FACING
                + " carriageAxisAlongFirst=false"
                + " travelAxisLocal=" + formatVector(localTravelAxis())
                + " carriageLocal=" + formatBlockPos(fixture.carriageLocal())
                + " carriageRailIndex=" + fixture.railIndex()
                + " payloadChassisLocal=" + formatBlockPos(fixture.payloadChassisLocal())
                + " payloadMarkerLocal=" + formatBlockPos(fixture.payloadMarkerLocal())
                + " liveControllerLocal=" + formatBlockPos(fixture.liveControllerLocal())
                + " movementAxis=" + fixture.movementAxis()
                + " axisMotion=" + fmt(fixture.axisMotion())
                + " failures=" + fixture.failures();
        send(context, line);
        Sable.LOGGER.info(line);
        return fixture.ready() ? 1 : 0;
    }

    private static int snapshot(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck fixture = checkFixture(subLevel);
        final M15Stats stats = inspectStats(subLevel);
        final GantrySnapshot gantry = collectGantries(subLevel).stream().findFirst().orElse(null);
        final Entity contraptionEntity = firstGantryContraptionEntity(subLevel);
        final Vec3 rawPlotAnchor = contraptionEntity == null ? null : contraptionEntity.position();
        final Vector3d rawLocalAnchor = rawPlotAnchor == null ? null : plotVecToLocal(subLevel, rawPlotAnchor);
        final Vector3d visibleAnchor = rawLocalAnchor == null ? null : subLevel.logicalPose().transformPosition(rawLocalAnchor);
        final Vector3d visibleAxis = subLevel.logicalPose().orientation().transform(localTravelAxis(), new Vector3d());
        final String line = "SABLE_M15_SNAPSHOT id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " state=" + fixture.state()
                + " pose=" + formatVector(subLevel.logicalPose().position())
                + " orientation=" + formatQuaternion(subLevel.logicalPose().orientation())
                + " linearVelocity=" + formatVector(stats.linearVelocity())
                + " angularVelocity=" + formatVector(stats.angularVelocity())
                + " motorSpeed=" + fmt(fixture.motorSpeed())
                + " carriageSpeed=" + fmt(gantry == null ? Double.NaN : gantry.speed())
                + " pinionMovementSpeed=" + fmt(fixture.pinionMovementSpeed())
                + " live=" + fixture.liveContraptionPresent()
                + " movementAxis=" + fixture.movementAxis()
                + " axisMotion=" + fmt(fixture.axisMotion())
                + " staticPayloadPresent=" + fixture.staticPayloadPresent()
                + " capturedPayloadPresent=" + fixture.capturedPayloadPresent()
                + " duplicatePayload=" + fixture.duplicatePayload()
                + " carriageLocal=" + formatBlockPos(fixture.carriageLocal())
                + " carriageRailIndex=" + fixture.railIndex()
                + " payloadChassisLocal=" + formatBlockPos(fixture.payloadChassisLocal())
                + " payloadMarkerLocal=" + formatBlockPos(fixture.payloadMarkerLocal())
                + " liveControllerLocal=" + formatBlockPos(fixture.liveControllerLocal())
                + " localTravelAxis=" + formatVector(localTravelAxis())
                + " expectedVisibleTravelAxis=" + formatVector(visibleAxis)
                + " contraptionEntityId=" + (contraptionEntity == null ? -1 : contraptionEntity.getId())
                + " capturedBlocks=" + fixture.capturedBlocks()
                + " createAnchorRawPlot=" + formatVec3(rawPlotAnchor)
                + " createAnchorSubLevelLocal=" + formatVector(rawLocalAnchor)
                + " expectedVisibleAnchor=" + formatVector(visibleAnchor)
                + " actualVisibleAnchor=UNVERIFIED"
                + " positionError=UNVERIFIED"
                + " axisAlignmentDot=UNVERIFIED"
                + " CLIENT_RENDER=UNVERIFIED"
                + " CLIENT_COLLISION=UNVERIFIED";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int dumpLayout(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        for (final LayoutExpectation expectation : layoutExpectations()) {
            final BlockState state = getLocalBlockState(subLevel, expectation.localPos());
            final BlockPos plot = SubLevelBlockEditHelper.localOffsetToPlotBlock(subLevel, expectation.localPos());
            final String line = "SABLE_M15_LAYOUT id=" + subLevel.getUniqueId()
                    + " local=" + formatBlockPos(expectation.localPos())
                    + " plot=" + formatBlockPos(plot)
                    + " blockId=" + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                    + " state=" + state
                    + " role=" + expectation.role()
                    + " expected=" + expectation.expected()
                    + " valid=" + expectation.validator().isValid(state);
            send(context, line);
            Sable.LOGGER.info(line);
        }
        return 1;
    }

    private static int captured(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final Entity entity = firstGantryContraptionEntity(subLevel);
        final Object contraption = entity == null ? null : firstNonNull(readFieldRaw(entity, "contraption"),
                invokeNoArgRaw(entity, "getContraption"));
        final Map<?, ?> blocks = contraptionBlocks(contraption);
        if (blocks.isEmpty()) {
            send(context, "SABLE_M15_CAPTURED id=" + subLevel.getUniqueId()
                    + " entityId=" + (entity == null ? -1 : entity.getId())
                    + " result=no_live_gantry_contraption_blocks");
            return 0;
        }
        final List<String> lines = new ArrayList<>();
        for (final Map.Entry<?, ?> entry : blocks.entrySet()) {
            final BlockPos local = entry.getKey() instanceof final BlockPos pos ? pos : null;
            final Object info = entry.getValue();
            final BlockState state = capturedBlockState(info);
            lines.add("local=" + formatBlockPos(local)
                    + " state=" + state
                    + " role=" + capturedRole(local));
        }
        lines.sort(String::compareTo);
        send(context, "SABLE_M15_CAPTURED id=" + subLevel.getUniqueId()
                + " entityId=" + entity.getId()
                + " count=" + blocks.size()
                + " blocks=" + lines);
        return blocks.size();
    }

    private static int lifecycle(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck fixture = checkFixture(subLevel);
        final String line = "SABLE_M15_LIFECYCLE id=" + subLevel.getUniqueId()
                + " cycleState=" + fixture.state()
                + " liveContraptionId=" + fixture.firstContraptionEntityId()
                + " capturedBlocks=" + fixture.capturedBlocks()
                + " staticCarriagePresent=" + !getLocalBlockState(subLevel, CARRIAGE_LOCAL).isAir()
                + " carriageLocal=" + formatBlockPos(fixture.carriageLocal())
                + " carriageRailIndex=" + fixture.railIndex()
                + " staticPayloadChassisPresent=" + !getLocalBlockState(subLevel, fixture.payloadChassisLocal()).isAir()
                + " staticPayloadMarkerPresent=" + !getLocalBlockState(subLevel, fixture.payloadMarkerLocal()).isAir()
                + " capturedPayloadPresent=" + fixture.capturedPayloadPresent()
                + " duplicates=" + fixture.duplicatePayload()
                + " missingPayload=" + !fixture.payloadPresent()
                + " liveControllerLocal=" + formatBlockPos(fixture.liveControllerLocal())
                + " axisMotion=" + fmt(fixture.axisMotion())
                + " expectedNextAction=" + (fixture.liveContraptionPresent() ? "reverse_or_stop" : "forward");
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int forward(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setMotorSpeed(context, DEFAULT_FORWARD_RPM, "forward");
    }

    private static int reverse(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setMotorSpeed(context, DEFAULT_REVERSE_RPM, "reverse");
    }

    private static int stop(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setMotorSpeed(context, SPAWN_MOTOR_RPM, "stop");
    }

    private static int toggle(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final int current = readFixtureMotorValue(subLevel);
        return setMotorSpeed(context, current > 0 ? DEFAULT_REVERSE_RPM : DEFAULT_FORWARD_RPM,
                current > 0 ? "toggle_reverse" : "toggle_forward");
    }

    private static int setMotorSpeed(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setMotorSpeed(context, IntegerArgumentType.getInteger(context, "rpm"), "set_motor_speed");
    }

    private static int resetFixture(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck before = checkFixture(subLevel);
        if (before.liveContraptionPresent()) {
            final String line = "SABLE_M15_RESET id=" + subLevel.getUniqueId()
                    + " result=REJECTED_LIVE_CONTRAPTION"
                    + " contraptionEntityId=" + before.firstContraptionEntityId();
            send(context, line);
            Sable.LOGGER.info(line);
            return 0;
        }
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (final LayoutExpectation expectation : layoutExpectations()) {
            blocks.put(expectation.localPos(), Blocks.AIR.defaultBlockState());
        }
        blocks.putAll(gantryFixtureBlocks());
        final List<SubLevelBlockEditHelper.BlockChange> changes = applyBlocks(subLevel, blocks);
        setFixtureMotorSpeed(subLevel, SPAWN_MOTOR_RPM);
        SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
        setFixtureMotorSpeed(subLevel, SPAWN_MOTOR_RPM);
        final FixtureCheck after = checkFixture(subLevel);
        send(context, "SABLE_M15_RESET id=" + subLevel.getUniqueId()
                + " result=" + (after.ready() ? "APPLIED" : "APPLIED_BUT_INVALID")
                + " failures=" + after.failures());
        return after.ready() ? 1 : 0;
    }

    private static int prepareAirborne(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck fixture = checkFixture(subLevel);
        if (!fixture.safeForAirbornePrepare()) {
            final String line = "SABLE_M15_AIRBORNE id=" + subLevel.getUniqueId()
                    + " result=REJECTED_UNSAFE_ACTIVE_STATE"
                    + " state=" + fixture.state()
                    + " contraptionEntityId=" + fixture.firstContraptionEntityId()
                    + " motorSpeed=" + fmt(fixture.motorSpeed())
                    + " axisMotion=" + fmt(fixture.axisMotion())
                    + " failures=" + fixture.failures();
            send(context, line);
            Sable.LOGGER.info(line);
            return 0;
        }
        final SubLevelPhysicsSystem physicsSystem = SableCommandHelper.requireSubLevelPhysicsSystem(context);
        final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        final Vector3d before = new Vector3d(subLevel.logicalPose().position());
        final Vector3d after = before.add(0.0, 8.0, 0.0, new Vector3d());
        handle.teleport(after, subLevel.logicalPose().orientation());
        handle.setLinearAndAngularVelocity(new Vector3d(), new Vector3d());
        subLevel.updateBoundingBox();
        subLevel.updateLastPose();
        final String line = "SABLE_M15_AIRBORNE id=" + subLevel.getUniqueId()
                + " result=APPLIED_ONE_SHOT_POSE"
                + " previousPose=" + formatVector(before)
                + " newPose=" + formatVector(after)
                + " linearVelocity=(0,0,0)"
                + " angularVelocityRadS=(0,0,0)"
                + " gravityStillActive=true"
                + " anchored=false";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int testTranslateParent(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setBodyVelocity(context, "translate_parent", new Vector3d(0.0, 1.0, 0.0), new Vector3d());
    }

    private static int testRotateParent(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setBodyVelocity(context, "rotate_parent", new Vector3d(),
                new Vector3d(0.0, Math.toRadians(20.0), 0.0));
    }

    private static int testCombinedParent(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setBodyVelocity(context, "combined_parent", new Vector3d(0.0, 0.6, 0.0),
                new Vector3d(0.0, Math.toRadians(12.0), 0.0));
    }

    private static int airborneAcceptance(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (prepareAirborne(context) == 0) {
            return 0;
        }
        setBodyVelocity(context, "airborne_acceptance_combined_parent", new Vector3d(0.0, 0.6, 0.0),
                new Vector3d(0.0, Math.toRadians(12.0), 0.0));
        final int result = setMotorSpeed(context, DEFAULT_FORWARD_RPM, "airborne_acceptance_forward");
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final String line = "SABLE_M15_AIRBORNE_ACCEPTANCE id=" + subLevel.getUniqueId()
                + " result=" + (result > 0 ? "APPLIED" : "REJECTED")
                + " preparedAirborne=true"
                + " bodyVelocity=combined_parent"
                + " gantryControl=normal_create_motor_speed"
                + " gravityStillActive=true"
                + " anchored=false"
                + " manualContraptionMotion=false";
        send(context, line);
        Sable.LOGGER.info(line);
        return result;
    }

    private static int setBodyVelocity(final CommandContext<CommandSourceStack> context,
                                       final String preset,
                                       final Vector3dc linearVelocity,
                                       final Vector3dc angularVelocityRad) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final SubLevelPhysicsSystem physicsSystem = SableCommandHelper.requireSubLevelPhysicsSystem(context);
        final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        handle.setLinearAndAngularVelocity(linearVelocity, angularVelocityRad);
        final Vector3d linearAfterSet = handle.getLinearVelocity(new Vector3d());
        final Vector3d angularAfterSet = handle.getAngularVelocity(new Vector3d());
        final String line = "SABLE_M15_TEST_PRESET target=" + subLevel.getUniqueId()
                + " preset=" + preset
                + " result=APPLIED_M10_PHYSICS_VELOCITY"
                + " requestedLinear=" + formatVector(linearVelocity)
                + " requestedAngularRadS=" + formatVector(angularVelocityRad)
                + " requestedAngularDegS=" + formatVectorDeg(angularVelocityRad)
                + " actualLinear=" + formatVector(linearAfterSet)
                + " actualAngularRadS=" + formatVector(angularAfterSet)
                + " anchored=false"
                + " next=/sable m15 forward <target>";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int setMotorSpeed(final CommandContext<CommandSourceStack> context, final int rpm,
                                     final String action) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck before = checkFixture(subLevel);
        if (!before.controlReady() && rpm != SPAWN_MOTOR_RPM) {
            final String rejectedLine = "SABLE_M15_CONTROL action=" + action
                    + " id=" + subLevel.getUniqueId()
                    + " name=" + nameOrNone(subLevel)
                    + " requestedMotorValue=" + rpm
                    + " previousMotorValue=" + before.motorValue()
                    + " result=REJECTED_FIXTURE_INVALID"
                    + " fixtureValid=" + before.ready()
                    + " controlReady=" + before.controlReady()
                    + " state=" + before.state()
                    + " carriageLocal=" + formatBlockPos(before.carriageLocal())
                    + " railIndex=" + before.railIndex()
                    + " commandApplied=false"
                    + " failures=" + before.failures();
            send(context, rejectedLine);
            Sable.LOGGER.info(rejectedLine);
            return 0;
        }
        setFixtureMotorSpeed(subLevel, rpm);
        final FixtureCheck after = checkFixture(subLevel);
        final String line = "SABLE_M15_CONTROL action=" + action
                + " id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " requestedMotorValue=" + rpm
                + " previousMotorValue=" + before.motorValue()
                + " motorValue=" + after.motorValue()
                + " motorSpeed=" + fmt(after.motorSpeed())
                + " carriageSpeed=" + fmt(after.carriageSpeed())
                + " pinionMovementSpeed=" + fmt(after.pinionMovementSpeed())
                + " movementDirection=" + (rpm > 0 ? "forward_for_east_rail" : rpm < 0 ? "reverse_for_east_rail" : "stop")
                + " fixtureValid=" + after.ready()
                + " controlReady=" + after.controlReady()
                + " state=" + after.state()
                + " carriageLocal=" + formatBlockPos(after.carriageLocal())
                + " railIndex=" + after.railIndex()
                + " result=APPLIED"
                + " commandApplied=true"
                + " semantics=Create_ScrollValueBehaviour_setValue";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static Map<BlockPos, BlockState> gantryFixtureBlocks() {
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        blocks.put(MOTOR_LOCAL, setProperty(requireBlockState(CREATIVE_MOTOR_ID), "facing", SHAFT_FACING.getName()));
        for (int x = SHAFT_MIN_X; x <= SHAFT_MAX_X; x++) {
            final String part = x == SHAFT_MIN_X ? "start" : x == SHAFT_MAX_X ? "end" : "middle";
            blocks.put(new BlockPos(x, 0, 0), setProperty(
                    setProperty(
                            setProperty(requireBlockState(GANTRY_SHAFT_ID), "facing", SHAFT_FACING.getName()),
                            "part", part),
                    "powered", "false"));
        }
        blocks.put(CARRIAGE_LOCAL, setProperty(
                setProperty(requireBlockState(GANTRY_CARRIAGE_ID), "facing", CARRIAGE_FACING.getName()),
                "axis_along_first", "false"));
        blocks.put(PAYLOAD_CHASSIS_LOCAL, setProperty(
                setProperty(requireBlockState(RADIAL_CHASSIS_ID), "axis", "y"),
                "sticky_south", "true"));
        blocks.put(PAYLOAD_MARKER_LOCAL, DEFAULT_MARKER_BLOCKSTATE);
        return blocks;
    }

    private static void sendInspect(final CommandContext<CommandSourceStack> context, final ServerSubLevel subLevel) {
        final M15Stats stats = inspectStats(subLevel);
        final FixtureCheck fixture = checkFixture(subLevel);
        final String line = "SABLE_M15_INSPECT id=" + subLevel.getUniqueId()
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
                + " linearVelocity=" + formatVector(stats.linearVelocity())
                + " angularVelocity=" + formatVector(stats.angularVelocity())
                + " rawPlotOrigin=" + formatBlockPos(subLevel.getPlot().getCenterBlock())
                + " gantryCount=" + fixture.gantryCount()
                + " shaftCount=" + fixture.shaftCount()
                + " plotContraptionCount=" + stats.plotContraptionCount();
        send(context, line);
        Sable.LOGGER.info(line);

        final String fixtureLine = "SABLE_M15_FIXTURE id=" + subLevel.getUniqueId()
                + " layoutValid=" + fixture.layoutValid()
                + " gantryChainValid=" + fixture.gantryChainValid()
                + " kineticDriveReady=" + fixture.kineticDriveReady()
                + " ready=" + fixture.ready()
                + " motorLocal=" + formatBlockPos(MOTOR_LOCAL)
                + " motorFacing=east"
                + " motorValue=" + fixture.motorValue()
                + " motorSpeed=" + fmt(fixture.motorSpeed())
                + " shaftRange=x[" + SHAFT_MIN_X + "," + SHAFT_MAX_X + "]"
                + " shaftFacing=east"
                + " carriageLocal=" + formatBlockPos(fixture.carriageLocal())
                + " carriageSpawnLocal=" + formatBlockPos(CARRIAGE_LOCAL)
                + " carriageRailIndex=" + fixture.railIndex()
                + " carriageFacing=up"
                + " carriageAxisAlongFirst=false"
                + " shaftBelowCarriage=" + formatBlockPos(fixture.carriageLocal() == null
                        ? null
                        : fixture.carriageLocal().relative(CARRIAGE_FACING.getOpposite()))
                + " carriageSpeed=" + fmt(fixture.carriageSpeed())
                + " pinionMovementSpeed=" + fmt(fixture.pinionMovementSpeed())
                + " payloadPresent=" + fixture.payloadPresent()
                + " staticPayloadPresent=" + fixture.staticPayloadPresent()
                + " capturedPayloadPresent=" + fixture.capturedPayloadPresent()
                + " payloadChassisLocal=" + formatBlockPos(fixture.payloadChassisLocal())
                + " payloadMarkerLocal=" + formatBlockPos(fixture.payloadMarkerLocal())
                + " localTravelAxis=" + formatVector(localTravelAxis())
                + " visibleTravelAxis=" + formatVector(subLevel.logicalPose().orientation()
                        .transform(localTravelAxis(), new Vector3d()))
                + " failures=" + fixture.failures();
        send(context, fixtureLine);
        Sable.LOGGER.info(fixtureLine);

        for (final GantrySnapshot gantry : collectGantries(subLevel)) {
            final String gantryLine = "SABLE_M15_GANTRY id=" + subLevel.getUniqueId()
                    + " local=" + formatBlockPos(gantry.localPos())
                    + " plot=" + formatBlockPos(gantry.plotPos())
                    + " state=" + gantry.state()
                    + " beClass=" + gantry.blockEntity().getClass().getName()
                    + " facing=" + gantry.facing()
                    + " axisAlongFirst=" + gantry.axisAlongFirst()
                    + " speed=" + fmt(gantry.speed())
                    + " theoreticalSpeed=" + fmt(gantry.theoreticalSpeed())
                    + " assembleNextTick=" + gantry.assembleNextTick()
                    + " lastException=" + className(gantry.lastException())
                    + " localTravelAxis=" + formatVector(localTravelAxis())
                    + " visibleTravelAxis=" + formatVector(gantry.visibleTravelAxis())
                    + " controllerSableAware=UNKNOWN_UNTIL_RUNTIME"
                    + " controllerBridgeGapCandidate=" + !genericControllerContextSupportsGantry()
                    + " clientEntityPresent=UNVERIFIED"
                    + " rendererFound=UNVERIFIED"
                    + " renderReady=UNVERIFIED"
                    + " visibleExpectedAabb=UNVERIFIED";
            send(context, gantryLine);
            Sable.LOGGER.info(gantryLine);
        }
        final Entity entity = firstGantryContraptionEntity(subLevel);
        if (entity != null) {
            final Object contraption = firstNonNull(readFieldRaw(entity, "contraption"),
                    invokeNoArgRaw(entity, "getContraption"));
            final String entityLine = "SABLE_M15_CONTRAPTION id=" + subLevel.getUniqueId()
                    + " entityId=" + entity.getId()
                    + " entityClass=" + entity.getClass().getName()
                    + " entityLevel=" + entity.level().getClass().getName()
                    + " rawPlotPosition=" + formatVec3(entity.position())
                    + " previousRawPlotPosition=" + formatVec3(new Vec3(entity.xOld, entity.yOld, entity.zOld))
                    + " localAnchor=" + formatVector(plotVecToLocal(subLevel, entity.position()))
                    + " visibleAnchor=" + formatVector(subLevel.logicalPose()
                            .transformPosition(plotVecToLocal(subLevel, entity.position())))
                    + " capturedBlocks=" + countContraptionBlocks(contraption)
                    + " localBounds=" + formatContraptionBounds(contraption)
                    + " containingSubLevelKnown=" + (Sable.HELPER.getContaining(entity) == subLevel)
                    + " axisMotion=" + fmt(asDouble(readFieldRaw(entity, "axisMotion")))
                    + " clientOffsetDiff=" + fmt(asDouble(readFieldRaw(entity, "clientOffsetDiff")))
                    + " sequencedOffsetLimit=" + fmt(asDouble(readFieldRaw(entity, "sequencedOffsetLimit")))
                    + " movementAxis=" + readFieldRaw(entity, "movementAxis");
            send(context, entityLine);
            Sable.LOGGER.info(entityLine);
        }
    }

    private static List<String> validateGeneric(final ServerSubLevel subLevel) {
        final List<String> failures = new ObjectArrayList<>();
        final M15Stats stats = inspectStats(subLevel);
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

    private static M15Stats inspectStats(final ServerSubLevel subLevel) {
        final List<BlockSample> blocks = scanBlocks(subLevel);
        final MassData mass = subLevel.getMassTracker();
        final MassData selfMass = subLevel.getSelfMassTracker();
        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(subLevel.getLevel());
        final RigidBodyHandle handle = physicsSystem == null ? null : physicsSystem.getPhysicsHandle(subLevel);
        return new M15Stats(
                blocks.size(),
                mass == null ? Double.NaN : mass.getMass(),
                selfMass == null ? Double.NaN : selfMass.getMass(),
                mass == null ? null : mass.getCenterOfMass(),
                subLevel.getPlot().getBoundingBox(),
                handle != null && handle.isValid()
                        && physicsSystem != null
                        && physicsSystem.getPipeline().isBodyRegistered(subLevel),
                physicsSystem != null && physicsSystem.hasUploadedCollisionGeometry(subLevel),
                handle == null ? new Vector3d(Double.NaN) : handle.getLinearVelocity(new Vector3d()),
                handle == null ? new Vector3d(Double.NaN) : handle.getAngularVelocity(new Vector3d()),
                subLevel.getPlot().getContraptions().size());
    }

    private static FixtureCheck checkFixture(final ServerSubLevel subLevel) {
        final List<String> failures = new ObjectArrayList<>();
        final List<GantrySnapshot> gantries = collectGantries(subLevel);
        final List<BlockSample> shafts = collectShafts(subLevel);
        final List<StaticGantryMechanism> staticMechanisms = collectStaticGantryMechanisms(subLevel, gantries, shafts);
        final StaticGantryMechanism staticMechanism = staticMechanisms.size() == 1 ? staticMechanisms.get(0) : null;
        final Entity liveEntity = firstGantryContraptionEntity(subLevel);
        final Object contraption = liveEntity == null ? null : firstNonNull(readFieldRaw(liveEntity, "contraption"),
                invokeNoArgRaw(liveEntity, "getContraption"));
        final boolean liveContraption = liveEntity != null;
        if (liveContraption && !staticMechanisms.isEmpty()) {
            failures.add("live_gantry_static_carriage_duplicate_" + staticMechanisms.size());
        }

        final BlockState motorState = getLocalBlockState(subLevel, MOTOR_LOCAL);
        requireInvariant(blockIdMatches(motorState, CREATIVE_MOTOR_ID),
                "creative_motor_missing_at_local_" + formatBlockPos(MOTOR_LOCAL), failures);
        requireInvariant(propertyMatches(motorState, "facing", SHAFT_FACING.getName()),
                "motor_facing_not_" + SHAFT_FACING.getName(), failures);

        int validShafts = 0;
        for (int x = SHAFT_MIN_X; x <= SHAFT_MAX_X; x++) {
            final BlockPos local = new BlockPos(x, 0, 0);
            final BlockState state = getLocalBlockState(subLevel, local);
            final String expectedPart = x == SHAFT_MIN_X ? "start" : x == SHAFT_MAX_X ? "end" : "middle";
            if (isValidShaftState(state, expectedPart)) {
                validShafts++;
            } else {
                failures.add("gantry_shaft_missing_or_wrong_state_at_local_" + formatBlockPos(local)
                        + "_expected_part_" + expectedPart);
            }
        }

        final Direction liveMovementAxis = directionFromObject(readFieldRaw(liveEntity, "movementAxis"), SHAFT_FACING);
        final double axisMotion = asDouble(readFieldRaw(liveEntity, "axisMotion"));
        final BlockPos liveControllerPlot = liveEntity == null
                ? null
                : BlockPos.containing(liveEntity.position().add(0.5, 0.5, 0.5))
                        .relative(liveMovementAxis.getOpposite());
        final BlockPos liveControllerLocal = liveControllerPlot == null
                ? null
                : liveControllerPlot.subtract(subLevel.getPlot().getCenterBlock());

        final BlockState carriageState = staticMechanism == null ? Blocks.AIR.defaultBlockState()
                : staticMechanism.carriageState();
        final BlockState staticShaftBelowCarriage = staticMechanism == null ? Blocks.AIR.defaultBlockState()
                : staticMechanism.controllerShaftState();
        final BlockState activeControllerShaft = liveContraption
                ? getPlotBlockState(subLevel, liveControllerPlot)
                : staticShaftBelowCarriage;
        if (!liveContraption) {
            if (staticMechanisms.isEmpty()) {
                failures.add("gantry_carriage_missing_on_rail");
            } else if (staticMechanisms.size() > 1) {
                failures.add("multiple_static_gantry_carriages_on_rail_" + staticMechanisms.size());
            }
            requireInvariant(blockIdMatches(carriageState, GANTRY_CARRIAGE_ID),
                    "gantry_carriage_missing_at_local_" + formatBlockPos(
                            staticMechanism == null ? null : staticMechanism.carriageLocal()), failures);
            requireInvariant(propertyMatches(carriageState, "facing", CARRIAGE_FACING.getName()),
                    "gantry_carriage_facing_not_" + CARRIAGE_FACING.getName(), failures);
            requireInvariant(propertyMatches(carriageState, "axis_along_first", "false"),
                    "gantry_carriage_axis_along_first_not_false_for_x_rail", failures);

            requireInvariant(blockIdMatches(staticShaftBelowCarriage, GANTRY_SHAFT_ID),
                    "gantry_shaft_missing_under_carriage_at_local_" + formatBlockPos(
                            staticMechanism == null ? null : staticMechanism.controllerShaftLocal()), failures);
            requireInvariant(propertyMatches(staticShaftBelowCarriage, "powered", "false"),
                    "gantry_shaft_under_carriage_must_not_be_powered", failures);
        } else {
            requireInvariant(blockIdMatches(activeControllerShaft, GANTRY_SHAFT_ID),
                    "live_gantry_controller_shaft_missing_at_local_"
                            + formatBlockPos(liveControllerLocal), failures);
            requireInvariant(propertyMatches(activeControllerShaft, "powered", "false"),
                    "live_gantry_controller_shaft_must_not_be_powered", failures);
        }

        final BlockPos staticPayloadChassisLocal = staticMechanism == null
                ? PAYLOAD_CHASSIS_LOCAL
                : staticMechanism.payloadChassisLocal();
        final BlockPos staticPayloadMarkerLocal = staticMechanism == null
                ? PAYLOAD_MARKER_LOCAL
                : staticMechanism.payloadMarkerLocal();
        final BlockState chassis = getLocalBlockState(subLevel, staticPayloadChassisLocal);
        final BlockState marker = getLocalBlockState(subLevel, staticPayloadMarkerLocal);
        final boolean staticPayloadPresent = blockIdMatches(chassis, RADIAL_CHASSIS_ID) && !marker.isAir();
        final boolean capturedPayloadPresent = liveContraption
                && contraptionContainsBlockId(contraption, RADIAL_CHASSIS_ID)
                && contraptionContainsBlockId(contraption, BuiltInRegistries.BLOCK.getKey(Blocks.STONE));
        final boolean duplicatePayload = liveContraption && staticPayloadPresent && capturedPayloadPresent;
        final boolean payloadPresent = liveContraption ? capturedPayloadPresent : staticPayloadPresent;
        if (!liveContraption) {
            requireInvariant(blockIdMatches(chassis, RADIAL_CHASSIS_ID),
                    "payload_radial_chassis_missing_at_local_" + formatBlockPos(staticPayloadChassisLocal), failures);
            requireInvariant(propertyMatches(chassis, "axis", "y"),
                    "payload_radial_chassis_axis_not_y", failures);
            requireInvariant(propertyMatches(chassis, "sticky_south", "true"),
                    "payload_radial_chassis_not_sticky_south_for_marker", failures);
            requireInvariant(!marker.isAir(),
                    "payload_marker_missing_at_local_" + formatBlockPos(staticPayloadMarkerLocal), failures);
        } else {
            requireInvariant(capturedPayloadPresent,
                    "live_gantry_payload_missing_from_contraption", failures);
            requireInvariant(!duplicatePayload,
                    "live_gantry_payload_duplicated_static_and_captured", failures);
        }

        final BlockEntity motor = getLocalBlockEntity(subLevel, MOTOR_LOCAL);
        final BlockEntity carriage = staticMechanism == null ? null : staticMechanism.blockEntity();
        final BlockEntity shaftUnderCarriage = liveContraption
                ? getPlotBlockEntity(subLevel, liveControllerPlot)
                : getLocalBlockEntity(subLevel, CARRIAGE_LOCAL.relative(CARRIAGE_FACING.getOpposite()));
        final int motorValue = readFixtureMotorValue(subLevel);
        final double motorSpeed = asDouble(invokeNoArgRaw(motor, "getSpeed"));
        final double carriageSpeed = liveContraption ? axisMotion : asDouble(invokeNoArgRaw(carriage, "getSpeed"));
        final double pinionMovementSpeed = asDouble(invokeNoArgRaw(shaftUnderCarriage, "getPinionMovementSpeed"));
        final boolean kineticDriveReady = motor != null
                && shaftUnderCarriage != null
                && Double.isFinite(motorSpeed)
                && (liveContraption || carriage != null)
                && (Double.isFinite(carriageSpeed) || Double.isFinite(pinionMovementSpeed));
        if (!kineticDriveReady) {
            failures.add("kinetic_drive_not_ready");
        }

        final boolean layoutValid = failures.stream().noneMatch(reason -> reason.startsWith("creative_motor_missing")
                || reason.startsWith("gantry_shaft_missing")
                || reason.startsWith("gantry_carriage_missing")
                || reason.startsWith("multiple_static_gantry_carriages")
                || reason.startsWith("payload_")
                || reason.startsWith("live_gantry_static_carriage_")
                || reason.startsWith("live_gantry_payload_"));
        final boolean gantryChainValid = validShafts == shaftCountExpected()
                && blockIdMatches(activeControllerShaft, GANTRY_SHAFT_ID)
                && propertyMatches(activeControllerShaft, "powered", "false");
        final boolean serverAssemblyReady = layoutValid && gantryChainValid && kineticDriveReady && payloadPresent;
        final int capturedBlocks = countContraptionBlocks(contraption);
        final boolean linearMotionObserved = liveEntity != null
                && (Math.abs(axisMotion) > 0.0001
                || Math.abs(liveEntity.getDeltaMovement().length()) > 0.0001);
        return new FixtureCheck(
                failures,
                layoutValid,
                gantryChainValid,
                kineticDriveReady,
                serverAssemblyReady,
                payloadPresent,
                staticPayloadPresent,
                capturedPayloadPresent,
                duplicatePayload,
                gantries.size(),
                shafts.size(),
                motorValue,
                motorSpeed,
                carriageSpeed,
                pinionMovementSpeed,
                liveContraption,
                liveEntity == null ? -1 : liveEntity.getId(),
                capturedBlocks,
                liveEntity != null && Sable.HELPER.getContaining(liveEntity) == subLevel,
                linearMotionObserved,
                liveMovementAxis.getName(),
                axisMotion,
                liveControllerLocal,
                staticMechanism == null ? null : staticMechanism.carriageLocal(),
                staticMechanism == null ? -1 : staticMechanism.railIndex(),
                staticPayloadChassisLocal,
                staticPayloadMarkerLocal);
    }

    private static List<GantrySnapshot> collectGantries(final ServerSubLevel subLevel) {
        final List<GantrySnapshot> gantries = new ArrayList<>();
        for (final BlockEntity blockEntity : collectBlockEntities(subLevel)) {
            final BlockState state = SubLevelBlockStateLookup.getBlockStateOrAir(subLevel, blockEntity.getBlockPos());
            if (!blockIdMatches(state, GANTRY_CARRIAGE_ID)
                    && !blockEntity.getClass().getName().contains("GantryCarriageBlockEntity")) {
                continue;
            }
            final BlockPos plotPos = blockEntity.getBlockPos();
            final BlockPos localPos = plotPos.subtract(subLevel.getPlot().getCenterBlock());
            final Vector3d visibleTravelAxis = subLevel.logicalPose().orientation()
                    .transform(localTravelAxis(), new Vector3d());
            gantries.add(new GantrySnapshot(
                    blockEntity,
                    localPos,
                    plotPos,
                    state,
                    directionProperty(state, "facing", CARRIAGE_FACING),
                    statePropertyName(state, "axis_along_first", "unknown"),
                    asDouble(invokeNoArgRaw(blockEntity, "getSpeed")),
                    asDouble(invokeNoArgRaw(blockEntity, "getTheoreticalSpeed")),
                    asBoolean(readFieldRaw(blockEntity, "assembleNextTick")),
                    readFieldRaw(blockEntity, "lastException"),
                    visibleTravelAxis));
        }
        gantries.sort(Comparator.comparing(GantrySnapshot::localPos, M15TestCommands::compareBlockPos));
        return gantries;
    }

    private static List<StaticGantryMechanism> collectStaticGantryMechanisms(final ServerSubLevel subLevel,
                                                                             final List<GantrySnapshot> gantries,
                                                                             final List<BlockSample> shafts) {
        final List<BlockSample> sortedShafts = new ArrayList<>(shafts);
        sortedShafts.sort(Comparator.comparing(BlockSample::localPos, M15TestCommands::compareBlockPos));
        final List<StaticGantryMechanism> mechanisms = new ArrayList<>();
        for (final GantrySnapshot gantry : gantries) {
            final BlockPos controllerShaftLocal = gantry.localPos().relative(CARRIAGE_FACING.getOpposite());
            int railIndex = -1;
            BlockState controllerShaftState = getLocalBlockState(subLevel, controllerShaftLocal);
            for (int index = 0; index < sortedShafts.size(); index++) {
                final BlockSample shaft = sortedShafts.get(index);
                if (shaft.localPos().equals(controllerShaftLocal)) {
                    railIndex = index;
                    controllerShaftState = shaft.state();
                    break;
                }
            }
            final BlockPos payloadChassisLocal = gantry.localPos().offset(PAYLOAD_CHASSIS_OFFSET);
            final BlockPos payloadMarkerLocal = gantry.localPos().offset(PAYLOAD_MARKER_OFFSET);
            mechanisms.add(new StaticGantryMechanism(
                    gantry.blockEntity(),
                    gantry.localPos(),
                    gantry.plotPos(),
                    gantry.state(),
                    controllerShaftLocal,
                    controllerShaftState,
                    railIndex,
                    payloadChassisLocal,
                    payloadMarkerLocal));
        }
        mechanisms.sort(Comparator.comparing(StaticGantryMechanism::carriageLocal, M15TestCommands::compareBlockPos));
        return mechanisms;
    }

    private static List<BlockSample> collectShafts(final ServerSubLevel subLevel) {
        final List<BlockSample> shafts = new ArrayList<>();
        for (final BlockSample sample : scanBlocks(subLevel)) {
            if (blockIdMatches(sample.state(), GANTRY_SHAFT_ID)) {
                shafts.add(sample);
            }
        }
        return shafts;
    }

    private static Entity firstGantryContraptionEntity(final ServerSubLevel subLevel) {
        final Collection<KinematicContraption> contraptions = subLevel.getPlot().getContraptions();
        for (final KinematicContraption contraption : contraptions) {
            if (contraption instanceof final Entity entity
                    && entity.getClass().getName().contains("GantryContraptionEntity")) {
                return entity;
            }
        }
        return null;
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
                for (int sectionX = 0; sectionX < 16; sectionX++) {
                    for (int sectionYLocal = 0; sectionYLocal < 16; sectionYLocal++) {
                        for (int sectionZ = 0; sectionZ < 16; sectionZ++) {
                            final BlockState state = section.getBlockState(sectionX, sectionYLocal, sectionZ);
                            if (state.isAir()) {
                                continue;
                            }
                            final BlockPos plotPos = new BlockPos(minX + sectionX, minY + sectionYLocal, minZ + sectionZ);
                            final BlockPos localPos = plotPos.subtract(subLevel.getPlot().getCenterBlock());
                            blocks.add(new BlockSample(localPos, plotPos, new ChunkPos(plotPos), state));
                        }
                    }
                }
            }
        }
        blocks.sort(Comparator.comparing(BlockSample::localPos, M15TestCommands::compareBlockPos));
        return blocks;
    }

    private static List<LayoutExpectation> layoutExpectations() {
        final List<LayoutExpectation> expectations = new ArrayList<>();
        expectations.add(new LayoutExpectation(MOTOR_LOCAL, "creative_motor_x_axis_drive",
                "create:creative_motor[facing=east], value initially 0",
                state -> blockIdMatches(state, CREATIVE_MOTOR_ID)
                        && propertyMatches(state, "facing", SHAFT_FACING.getName())));
        for (int x = SHAFT_MIN_X; x <= SHAFT_MAX_X; x++) {
            final String part = x == SHAFT_MIN_X ? "start" : x == SHAFT_MAX_X ? "end" : "middle";
            expectations.add(new LayoutExpectation(new BlockPos(x, 0, 0),
                    "gantry_shaft_" + part,
                    "create:gantry_shaft[facing=east,part=" + part + ",powered=false]",
                    state -> isValidShaftState(state, part)));
        }
        expectations.add(new LayoutExpectation(CARRIAGE_LOCAL, "gantry_carriage_above_rail",
                "create:gantry_carriage[facing=up,axis_along_first=false]",
                state -> blockIdMatches(state, GANTRY_CARRIAGE_ID)
                        && propertyMatches(state, "facing", CARRIAGE_FACING.getName())
                        && propertyMatches(state, "axis_along_first", "false")));
        expectations.add(new LayoutExpectation(PAYLOAD_CHASSIS_LOCAL, "payload_radial_chassis",
                "create:radial_chassis[axis=y,sticky_south=true]",
                state -> blockIdMatches(state, RADIAL_CHASSIS_ID)
                        && propertyMatches(state, "axis", "y")
                        && propertyMatches(state, "sticky_south", "true")));
        expectations.add(new LayoutExpectation(PAYLOAD_MARKER_LOCAL, "off_axis_marker",
                "minecraft:stone marker at local +Z from chassis",
                state -> blockIdMatches(state, BuiltInRegistries.BLOCK.getKey(Blocks.STONE))));
        return expectations;
    }

    private static int shaftCountExpected() {
        return SHAFT_MAX_X - SHAFT_MIN_X + 1;
    }

    private static boolean isValidShaftState(final BlockState state, final String part) {
        return blockIdMatches(state, GANTRY_SHAFT_ID)
                && propertyMatches(state, "facing", SHAFT_FACING.getName())
                && propertyMatches(state, "part", part)
                && propertyMatches(state, "powered", "false");
    }

    private static boolean isM15FixtureCandidate(final ServerSubLevel subLevel) {
        return subLevel.getName() != null && subLevel.getName().startsWith("m15")
                || !collectGantries(subLevel).isEmpty()
                || collectShafts(subLevel).size() >= 3;
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

    private static void setFixtureMotorSpeed(final ServerSubLevel subLevel, final int rpm) {
        final BlockEntity motor = getLocalBlockEntity(subLevel, MOTOR_LOCAL);
        if (motor == null) {
            throw new IllegalStateException("M15 fixture motor is missing at local " + formatBlockPos(MOTOR_LOCAL));
        }
        final Object behaviour = readFieldRaw(motor, "generatedSpeed");
        if (behaviour == null || invokeIntArgRaw(behaviour, "setValue", rpm) == null) {
            throw new IllegalStateException("M15 fixture motor does not expose Create ScrollValueBehaviour at local "
                    + formatBlockPos(MOTOR_LOCAL));
        }
    }

    private static int readFixtureMotorValue(final ServerSubLevel subLevel) {
        final BlockEntity motor = getLocalBlockEntity(subLevel, MOTOR_LOCAL);
        final Object behaviour = readFieldRaw(motor, "generatedSpeed");
        final Object value = invokeNoArgRaw(behaviour, "getValue");
        return value instanceof final Number number ? number.intValue() : Integer.MIN_VALUE;
    }

    private static @Nullable BlockEntity getLocalBlockEntity(final ServerSubLevel subLevel,
                                                            @Nullable final BlockPos localPos) {
        if (localPos == null) {
            return null;
        }
        return getPlotBlockEntity(subLevel, SubLevelBlockEditHelper.localOffsetToPlotBlock(subLevel, localPos));
    }

    private static BlockState getLocalBlockState(final ServerSubLevel subLevel, @Nullable final BlockPos localPos) {
        if (localPos == null) {
            return Blocks.AIR.defaultBlockState();
        }
        return getPlotBlockState(subLevel, SubLevelBlockEditHelper.localOffsetToPlotBlock(subLevel, localPos));
    }

    private static @Nullable BlockEntity getPlotBlockEntity(final ServerSubLevel subLevel,
                                                           @Nullable final BlockPos plotPos) {
        if (plotPos == null) {
            return null;
        }
        return SubLevelBlockStateLookup.getBlockEntity(subLevel, plotPos);
    }

    private static BlockState getPlotBlockState(final ServerSubLevel subLevel, @Nullable final BlockPos plotPos) {
        if (plotPos == null) {
            return Blocks.AIR.defaultBlockState();
        }
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

    private static String statePropertyName(final BlockState state, final String propertyName, final String fallback) {
        for (final Property<?> property : state.getProperties()) {
            if (property.getName().equals(propertyName)) {
                return normalizePropertyValue(state.getValue(property));
            }
        }
        return fallback;
    }

    private static boolean blockIdMatches(final BlockState state, final ResourceLocation blockId) {
        return blockId.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    private static boolean propertyMatches(final BlockState state, final String propertyName, final String valueName) {
        for (final Property<?> property : state.getProperties()) {
            if (property.getName().equals(propertyName)) {
                return normalizePropertyValue(state.getValue(property))
                        .equals(valueName.toLowerCase(Locale.ROOT));
            }
        }
        return false;
    }

    private static String normalizePropertyValue(final Object value) {
        return String.valueOf(value).toLowerCase(Locale.ROOT);
    }

    private static Vector3d localTravelAxis() {
        return new Vector3d(SHAFT_FACING.getStepX(), SHAFT_FACING.getStepY(), SHAFT_FACING.getStepZ());
    }

    private static Direction directionFromObject(@Nullable final Object value, final Direction fallback) {
        return value instanceof final Direction direction ? direction : fallback;
    }

    private static Vector3d plotVecToLocal(final ServerSubLevel subLevel, final Vec3 plotVec) {
        final BlockPos center = subLevel.getPlot().getCenterBlock();
        return new Vector3d(plotVec.x - center.getX(), plotVec.y - center.getY(), plotVec.z - center.getZ());
    }

    private static @Nullable Object firstNonNull(@Nullable final Object first, @Nullable final Object second) {
        return first != null ? first : second;
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> contraptionBlocks(@Nullable final Object contraption) {
        final Object blocks = firstNonNull(invokeNoArgRaw(contraption, "getBlocks"),
                readFieldRaw(contraption, "blocks"));
        return blocks instanceof final Map<?, ?> map ? map : Map.of();
    }

    private static int countContraptionBlocks(@Nullable final Object contraption) {
        return contraptionBlocks(contraption).size();
    }

    private static boolean contraptionContainsBlockId(@Nullable final Object contraption,
                                                      final ResourceLocation blockId) {
        for (final Object info : contraptionBlocks(contraption).values()) {
            final BlockState state = capturedBlockState(info);
            if (state != null && blockIdMatches(state, blockId)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable BlockState capturedBlockState(@Nullable final Object structureBlockInfo) {
        final Object state = firstNonNull(
                firstNonNull(invokeNoArgRaw(structureBlockInfo, "state"),
                        readFieldRaw(structureBlockInfo, "state")),
                readFieldRaw(structureBlockInfo, "f_74676_"));
        return state instanceof final BlockState blockState ? blockState : null;
    }

    private static String formatContraptionBounds(@Nullable final Object contraption) {
        final Map<?, ?> blocks = contraptionBlocks(contraption);
        if (blocks.isEmpty()) {
            return "EMPTY";
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (final Object key : blocks.keySet()) {
            if (!(key instanceof final BlockPos pos)) {
                continue;
            }
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if (minX == Integer.MAX_VALUE) {
            return "EMPTY";
        }
        return "(" + minX + "," + minY + "," + minZ + ")->(" + maxX + "," + maxY + "," + maxZ + ")";
    }

    private static String capturedRole(@Nullable final BlockPos local) {
        if (local == null) {
            return "unknown";
        }
        if (local.equals(CARRIAGE_LOCAL)) {
            return "gantry_carriage";
        }
        if (local.equals(PAYLOAD_CHASSIS_LOCAL)) {
            return "payload_radial_chassis";
        }
        if (local.equals(PAYLOAD_MARKER_LOCAL)) {
            return "off_axis_marker";
        }
        return "create_gantry_contraption_block";
    }

    private static boolean genericControllerContextSupportsGantry() {
        try {
            Class.forName(GANTRY_CONTRAPTION_ENTITY_CLASS);
            return false;
        } catch (final ClassNotFoundException ignored) {
            return false;
        }
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
        Sable.LOGGER.warn("SABLE_M15 phase=rollback_begin name={} id={} reason={}",
                name, subLevel.getUniqueId(), failure.getClass().getSimpleName());
        try {
            container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
            Sable.LOGGER.warn("SABLE_M15 phase=rollback_complete name={} id={}", name, subLevel.getUniqueId());
        } catch (final Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
            Sable.LOGGER.error("SABLE_M15 phase=rollback_failed name={} id={}",
                    name, subLevel.getUniqueId(), cleanupFailure);
        }
    }

    private static CommandSyntaxException commandFailure(final RuntimeException exception) {
        Sable.LOGGER.error("SABLE_M15 phase=failed", exception);
        final String message = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        return ERROR_M15_FAILED.create(message);
    }

    private static void send(final CommandContext<CommandSourceStack> context, final String line) {
        context.getSource().sendSuccess(() -> Component.literal(line), false);
    }

    private static String passFail(final boolean value) {
        return value ? "PASS" : "FAIL";
    }

    private static String nameOrNone(final ServerSubLevel subLevel) {
        return subLevel.getName() != null ? subLevel.getName() : "<none>";
    }

    private static String className(@Nullable final Object object) {
        return object == null ? "none" : object.getClass().getName();
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

    private static String formatVectorDeg(@Nullable final Vector3dc vector) {
        if (vector == null) {
            return "(null)";
        }
        return "(" + fmt(Math.toDegrees(vector.x())) + "," + fmt(Math.toDegrees(vector.y()))
                + "," + fmt(Math.toDegrees(vector.z())) + ")";
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

    private interface StateValidator {
        boolean isValid(BlockState state);
    }

    private record LayoutExpectation(BlockPos localPos, String role, String expected, StateValidator validator) {
    }

    private record BlockSample(BlockPos localPos, BlockPos plotPos, ChunkPos chunk, BlockState state) {
    }

    private record M15Stats(int blockCount, double mass, double selfMass, @Nullable Vector3dc centerOfMass,
                            BoundingBox3ic plotBounds, boolean physicsBodyRegistered,
                            boolean collisionGeometryPresent, Vector3dc linearVelocity,
                            Vector3dc angularVelocity, int plotContraptionCount) {
    }

    private record GantrySnapshot(BlockEntity blockEntity, BlockPos localPos, BlockPos plotPos, BlockState state,
                                  Direction facing, String axisAlongFirst, double speed, double theoreticalSpeed,
                                  boolean assembleNextTick, @Nullable Object lastException,
                                  Vector3dc visibleTravelAxis) {
    }

    private record StaticGantryMechanism(BlockEntity blockEntity, BlockPos carriageLocal, BlockPos carriagePlot,
                                         BlockState carriageState, BlockPos controllerShaftLocal,
                                         BlockState controllerShaftState, int railIndex,
                                         BlockPos payloadChassisLocal, BlockPos payloadMarkerLocal) {
    }

    private record FixtureCheck(List<String> failures, boolean layoutValid, boolean gantryChainValid,
                                boolean kineticDriveReady, boolean serverAssemblyReady,
                                boolean payloadPresent, boolean staticPayloadPresent,
                                boolean capturedPayloadPresent, boolean duplicatePayload,
                                int gantryCount, int shaftCount,
                                int motorValue, double motorSpeed, double carriageSpeed,
                                double pinionMovementSpeed, boolean liveContraptionPresent,
                                int firstContraptionEntityId, int capturedBlocks,
                                boolean controllerKnown, boolean linearMotionObserved,
                                String movementAxis, double axisMotion,
                                @Nullable BlockPos liveControllerLocal,
                                @Nullable BlockPos carriageLocal, int railIndex,
                                BlockPos payloadChassisLocal, BlockPos payloadMarkerLocal) {
        boolean ready() {
            return this.failures.isEmpty();
        }

        boolean controlReady() {
            return this.gantryChainValid
                    && this.kineticDriveReady
                    && this.payloadPresent
                    && !this.duplicatePayload
                    && (!this.liveContraptionPresent || this.controllerKnown);
        }

        boolean safeForAirbornePrepare() {
            return !this.liveContraptionPresent
                    || (this.controlReady()
                    && Math.abs(this.axisMotion) <= 0.0001
                    && Math.abs(this.motorSpeed) <= 0.0001);
        }

        String state() {
            if (!this.layoutValid || !this.gantryChainValid || !this.kineticDriveReady) {
                return "BROKEN";
            }
            if (this.liveContraptionPresent && this.linearMotionObserved) {
                return this.motorValue >= 0 ? "MOVING_FORWARD" : "MOVING_REVERSE";
            }
            if (this.liveContraptionPresent) {
                return "LIVE_IDLE";
            }
            if (CARRIAGE_LOCAL.equals(this.carriageLocal)) {
                return "UNASSEMBLED_READY";
            }
            return "STATIC_AT_RAIL_POSITION";
        }
    }
}
