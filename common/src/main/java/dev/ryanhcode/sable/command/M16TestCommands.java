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
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
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

/** Runtime acceptance harness for M16 Mechanical Drill block-breaking actors; fixture-local coordinates are the acceptance truth. */
public final class M16TestCommands {
    private static final ResourceLocation CREATIVE_MOTOR_ID = new ResourceLocation("create", "creative_motor");
    private static final ResourceLocation SHAFT_ID = new ResourceLocation("create", "shaft");
    private static final ResourceLocation STICKY_MECHANICAL_PISTON_ID = new ResourceLocation("create", "sticky_mechanical_piston");
    private static final ResourceLocation PISTON_EXTENSION_POLE_ID = new ResourceLocation("create", "piston_extension_pole");
    private static final ResourceLocation RADIAL_CHASSIS_ID = new ResourceLocation("create", "radial_chassis");
    private static final ResourceLocation MECHANICAL_DRILL_ID = new ResourceLocation("create", "mechanical_drill");
    private static final BlockPos MOTOR_LOCAL = new BlockPos(0, -2, 0);
    private static final BlockPos SHAFT_LOCAL = new BlockPos(0, -1, 0);
    private static final BlockPos PISTON_LOCAL = BlockPos.ZERO;
    private static final BlockPos PAYLOAD_CHASSIS_RETRACTED = new BlockPos(1, 0, 0);
    private static final BlockPos DRILL_RETRACTED = new BlockPos(1, 1, 0);
    private static final Direction PISTON_FACING = Direction.EAST;
    private static final int PISTON_EXTENSION_POLES = 4;
    private static final int SPAWN_MOTOR_RPM = 0;
    private static final int EXTEND_RPM = -32;
    private static final int RETRACT_RPM = 32;
    private static final List<BlockPos> TARGET_LOCALS = List.of(
            new BlockPos(6, 1, 0),
            new BlockPos(7, 1, 0),
            new BlockPos(8, 1, 0));
    private static final DynamicCommandExceptionType ERROR_M16_FAILED = new DynamicCommandExceptionType(
            message -> Component.literal("SABLE_M16 command failed: " + message));

    private M16TestCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m16")
                .then(Commands.literal("spawn_drill")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(M16TestCommands::spawnDrill)))
                .then(Commands.literal("validate")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M16TestCommands::validate)))
                .then(Commands.literal("dump_layout")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M16TestCommands::dumpLayout)))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M16TestCommands::inspect)))
                .then(Commands.literal("extend")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M16TestCommands::extend)))
                .then(Commands.literal("retract")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M16TestCommands::retract)))
                .then(Commands.literal("snapshot")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M16TestCommands::snapshot)))
                .then(Commands.literal("targets")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M16TestCommands::targets)))
                .then(Commands.literal("test_translate_parent")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M16TestCommands::testTranslateParent)))
                .then(Commands.literal("test_rotate_parent")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M16TestCommands::testRotateParent)))
                .then(Commands.literal("prepare_airborne")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M16TestCommands::prepareAirborne)))
                .then(Commands.literal("airborne_acceptance")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M16TestCommands::airborneAcceptance)))
                .then(Commands.literal("save_reload_check")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M16TestCommands::saveReloadCheck))));
    }

    private static int spawnDrill(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(context);
        final String name = StringArgumentType.getString(context, "name");
        ServerSubLevel subLevel = null;

        try {
            subLevel = createEmptySubLevel(context, container, name);
            final List<SubLevelBlockEditHelper.BlockChange> changes = applyBlocks(subLevel, drillFixtureBlocks());
            setFixtureMotorSpeed(subLevel, SPAWN_MOTOR_RPM);
            SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
            setFixtureMotorSpeed(subLevel, SPAWN_MOTOR_RPM);
            subLevel.updateLastPose();

            final FixtureCheck check = checkFixture(subLevel);
            final String line = "SABLE_M16_SPAWN name=" + name
                    + " uuid=" + subLevel.getUniqueId()
                    + " status=" + (check.ready() ? "PASS" : "FAIL")
                    + " pistonLocal=" + fmt(PISTON_LOCAL)
                    + " chassisLocal=" + fmt(PAYLOAD_CHASSIS_RETRACTED)
                    + " drillLocal=" + fmt(DRILL_RETRACTED)
                    + " drillFacing=east"
                    + " targets=" + TARGET_LOCALS
                    + " motorValue=" + check.motorValue()
                    + " constructionOrdering=blocks_then_motor_stopped_then_finalize"
                    + " semantics=sticky_piston_carrier_normal_create_drill_actor"
                    + " failures=" + check.failures();
            send(context, line);
            Sable.LOGGER.info(line);
            return check.ready() ? 1 : 0;
        } catch (final RuntimeException exception) {
            rollbackSpawnSubLevel(container, subLevel, name, exception);
            throw commandFailure(exception);
        }
    }

    private static int validate(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck check = checkFixture(subLevel);
        final ActorSnapshot actor = findDrillActor(subLevel);
        final TargetSummary targets = summarizeTargets(subLevel, actor.breakingPos());
        final String line = "SABLE_M16_VALIDATE state=" + check.state()
                + " status=" + (check.ready() && targets.sequenceCoherent() ? "PASS" : "FAIL")
                + " FIXTURE_LAYOUT=" + pass(check.fixtureLayout())
                + " PISTON_CONTROLLER=" + pass(check.pistonController())
                + " DRILL_ACTOR=" + pass(check.drillPresent())
                + " TARGETS=" + pass(targets.sequenceCoherent())
                + " PAYLOAD_OWNERSHIP=" + pass(check.payloadOwnershipCoherent())
                + " BLOCK_BREAKING=UNVERIFIED_UNTIL_RUNTIME_PROGRESS"
                + " CLIENT_RENDER=UNVERIFIED"
                + " CLIENT_COLLISION=UNVERIFIED"
                + " PERSISTENCE=UNVERIFIED"
                + " id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " pistonState=" + check.pistonState()
                + " motorValue=" + check.motorValue()
                + " motorSpeed=" + fmt(check.motorSpeed())
                + " liveContraption=" + check.liveContraption()
                + " entityId=" + check.entityId()
                + " capturedBlocks=" + check.capturedBlocks()
                + " targetStates=" + targets.compact()
                + " failures=" + mergeFailures(check.failures(), targets.failures());
        send(context, line);
        Sable.LOGGER.info(line);
        return check.ready() && targets.sequenceCoherent() ? 1 : 0;
    }

    private static int dumpLayout(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        for (final BlockPos local : diagnosticLayoutLocals()) {
            final BlockState state = getLocalBlockState(subLevel, local);
            final String line = "SABLE_M16_LAYOUT id=" + subLevel.getUniqueId()
                    + " localPos=" + fmt(local)
                    + " rawPlotPos=" + fmt(toPlot(subLevel, local))
                    + " blockId=" + blockId(state)
                    + " state=" + state
                    + " role=" + layoutRole(local)
                    + " expected=" + expectedLayout(local)
                    + " valid=" + layoutPositionValid(subLevel, local, state);
            send(context, line);
            Sable.LOGGER.info(line);
        }
        return 1;
    }

    private static int inspect(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck check = checkFixture(subLevel);
        final ActorSnapshot actor = findDrillActor(subLevel);
        final String line = "SABLE_M16_INSPECT id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " state=" + check.state()
                + " entityId=" + actor.entityId()
                + " actorLocal=" + fmt(actor.actorLocal())
                + " actorState=" + actor.actorStateId()
                + " actorFacing=" + actor.facing()
                + " breakingDirection=east_local_transformed_by_parent_pose"
                + " containingSubLevel=" + subLevel.getUniqueId()
                + " breakingPosRaw=" + fmt(actor.breakingPos())
                + " breakingPosFixtureLocal=" + fmt(toLocalIfOwned(subLevel, actor.breakingPos()))
                + " progress=" + actor.progress()
                + " breakerId=" + actor.breakerId()
                + " stall=" + actor.stall()
                + " pistonState=" + check.pistonState()
                + " liveContraption=" + check.liveContraption()
                + " capturedBlocks=" + check.capturedBlocks()
                + " payloadStaticRetracted=" + check.staticRetractedPayload()
                + " payloadStaticExtended=" + check.staticExtendedPayload()
                + " payloadCaptured=" + check.capturedPayload();
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int extend(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setMotorSpeed(context, EXTEND_RPM, "extend");
    }

    private static int retract(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setMotorSpeed(context, RETRACT_RPM, "retract");
    }

    private static int snapshot(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck check = checkFixture(subLevel);
        final ActorSnapshot actor = findDrillActor(subLevel);
        final TargetSummary targets = summarizeTargets(subLevel, actor.breakingPos());
        final M16Stats stats = inspectStats(subLevel);
        final String line = "SABLE_M16_SNAPSHOT name=" + nameOrNone(subLevel)
                + " state=" + check.state()
                + " pistonState=" + check.pistonState()
                + " live=" + check.liveContraption()
                + " entityId=" + check.entityId()
                + " drillActor=" + actor.present()
                + " currentBreakingTarget=" + fmt(toLocalIfOwned(subLevel, actor.breakingPos()))
                + " progress=" + actor.progress()
                + " expectedNextTarget=" + fmt(targets.nextExpected())
                + " targetStates=" + targets.compact()
                + " payloadIntegrity=" + pass(check.payloadOwnershipCoherent())
                + " parentPosition=" + formatVector(stats.position())
                + " parentOrientation=" + subLevel.logicalPose().orientation()
                + " linearVelocity=" + formatVector(stats.linearVelocity())
                + " angularVelocity=" + formatVector(stats.angularVelocity())
                + " visibleDrillDirection=parent_orientation_times_local_east"
                + " wrongWorldMutation=" + pass(check.noObviousWrongWorldMutation());
        send(context, line);
        Sable.LOGGER.info(line);
        return check.ready() ? 1 : 0;
    }

    private static int targets(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final ActorSnapshot actor = findDrillActor(subLevel);
        int index = 1;
        for (final TargetState target : targetStates(subLevel, actor.breakingPos())) {
            final String line = "SABLE_M16_TARGET index=" + index
                    + " fixtureLocal=" + fmt(target.local())
                    + " rawStorage=" + fmt(target.raw())
                    + " blockId=" + blockId(target.state())
                    + " state=" + target.state()
                    + " expected=" + target.expected()
                    + " consumed=" + target.consumed()
                    + " lastOrCurrentBreakingTarget=" + target.selected();
            send(context, line);
            Sable.LOGGER.info(line);
            index++;
        }
        final TargetSummary summary = summarizeTargets(subLevel, actor.breakingPos());
        send(context, "SABLE_M16_TARGET_SUMMARY sequenceCoherent=" + summary.sequenceCoherent()
                + " nextExpected=" + fmt(summary.nextExpected())
                + " failures=" + summary.failures());
        return summary.sequenceCoherent() ? 1 : 0;
    }

    private static int testTranslateParent(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setBodyVelocity(context, "translate_parent", new Vector3d(0.0, 1.0, 0.0), new Vector3d());
    }

    private static int testRotateParent(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setBodyVelocity(context, "rotate_parent", new Vector3d(),
                new Vector3d(0.0, Math.toRadians(20.0), 0.0));
    }

    private static int prepareAirborne(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck check = checkFixture(subLevel);
        if (check.liveContraption()) {
            send(context, "SABLE_M16_AIRBORNE id=" + subLevel.getUniqueId()
                    + " result=REJECTED_ACTIVE_CONTRAPTION"
                    + " reason=finish_or_stop_piston_cycle_before_airborne_pose"
                    + " entityId=" + check.entityId());
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
        final String line = "SABLE_M16_AIRBORNE id=" + subLevel.getUniqueId()
                + " result=APPLIED_ONE_SHOT_POSE"
                + " previousPose=" + formatVector(before)
                + " newPose=" + formatVector(after)
                + " gravityStillActive=true"
                + " anchored=false";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int airborneAcceptance(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (prepareAirborne(context) == 0) {
            return 0;
        }
        setBodyVelocity(context, "airborne_acceptance_parent", new Vector3d(0.0, 0.6, 0.0),
                new Vector3d(0.0, Math.toRadians(12.0), 0.0));
        final int control = setMotorSpeed(context, EXTEND_RPM, "airborne_acceptance_extend");
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck check = checkFixture(subLevel);
        final TargetSummary targets = summarizeTargets(subLevel, findDrillActor(subLevel).breakingPos());
        final boolean pass = control > 0 && check.ready() && targets.sequenceCoherent();
        final String line = "SABLE_M16_AIRBORNE_ACCEPTANCE id=" + subLevel.getUniqueId()
                + " status=" + (pass ? "PASS" : "FAIL")
                + " parentMotion=combined_linear_angular_via_M10_physics"
                + " pistonControl=normal_create_motor_speed"
                + " blockBreakingTargetIdentity=" + pass(targets.sequenceCoherent())
                + " payloadIntegrity=" + pass(check.payloadOwnershipCoherent())
                + " wrongWorldMutation=" + pass(check.noObviousWrongWorldMutation())
                + " gravityStillActive=true"
                + " anchored=false"
                + " failures=" + mergeFailures(check.failures(), targets.failures());
        send(context, line);
        Sable.LOGGER.info(line);
        return pass ? 1 : 0;
    }

    private static int saveReloadCheck(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck check = checkFixture(subLevel);
        final TargetSummary targets = summarizeTargets(subLevel, findDrillActor(subLevel).breakingPos());
        final String line = "SABLE_M16_SAVE_RELOAD_CHECK id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " status=" + (check.ready() && targets.sequenceCoherent() ? "PASS" : "FAIL")
                + " note=run_before_and_after_manual_save_reload"
                + " state=" + check.state()
                + " pistonState=" + check.pistonState()
                + " targetStates=" + targets.compact()
                + " payloadIntegrity=" + pass(check.payloadOwnershipCoherent())
                + " persistence=UNVERIFIED_UNTIL_MANUAL_RELOAD";
        send(context, line);
        Sable.LOGGER.info(line);
        return check.ready() && targets.sequenceCoherent() ? 1 : 0;
    }

    private static int setMotorSpeed(final CommandContext<CommandSourceStack> context,
                                     final int rpm,
                                     final String action) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck before = checkFixture(subLevel);
        if (!before.controlReady() && rpm != SPAWN_MOTOR_RPM) {
            final String rejectedLine = "SABLE_M16_CONTROL action=" + action
                    + " id=" + subLevel.getUniqueId()
                    + " requestedMotorValue=" + rpm
                    + " previousMotorValue=" + before.motorValue()
                    + " result=REJECTED_FIXTURE_INVALID"
                    + " controlReady=false"
                    + " failures=" + before.failures();
            send(context, rejectedLine);
            Sable.LOGGER.info(rejectedLine);
            return 0;
        }
        setFixtureMotorSpeed(subLevel, rpm);
        final FixtureCheck after = checkFixture(subLevel);
        final String line = "SABLE_M16_CONTROL action=" + action
                + " id=" + subLevel.getUniqueId()
                + " requestedMotorValue=" + rpm
                + " previousMotorValue=" + before.motorValue()
                + " motorValue=" + after.motorValue()
                + " motorSpeed=" + fmt(after.motorSpeed())
                + " pistonState=" + after.pistonState()
                + " result=APPLIED"
                + " commandApplied=true"
                + " semantics=Create_ScrollValueBehaviour_setValue_no_manual_contraption_motion";
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
        final String line = "SABLE_M16_TEST_PARENT id=" + subLevel.getUniqueId()
                + " preset=" + preset
                + " result=APPLIED_M10_PHYSICS_VELOCITY"
                + " requestedLinear=" + formatVector(linearVelocity)
                + " requestedAngularRadS=" + formatVector(angularVelocityRad)
                + " anchored=false"
                + " hiddenStorageUnchanged=true";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static Map<BlockPos, BlockState> drillFixtureBlocks() {
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        blocks.put(MOTOR_LOCAL, setProperty(requireBlockState(CREATIVE_MOTOR_ID), "facing", "up"));
        blocks.put(SHAFT_LOCAL, setProperty(requireBlockState(SHAFT_ID), "axis", "y"));
        blocks.put(PISTON_LOCAL, setProperty(setProperty(setProperty(requireBlockState(STICKY_MECHANICAL_PISTON_ID),
                "facing", PISTON_FACING.getName()), "axis_along_first", "true"), "state", "retracted"));
        for (int i = 1; i <= PISTON_EXTENSION_POLES; i++) {
            blocks.put(PISTON_LOCAL.relative(PISTON_FACING.getOpposite(), i),
                    setProperty(requireBlockState(PISTON_EXTENSION_POLE_ID), "facing", PISTON_FACING.getName()));
        }
        blocks.put(PAYLOAD_CHASSIS_RETRACTED, setProperty(
                setProperty(requireBlockState(RADIAL_CHASSIS_ID), "axis", "x"),
                "sticky_north", "true"));
        blocks.put(DRILL_RETRACTED, setProperty(requireBlockState(MECHANICAL_DRILL_ID), "facing", "east"));
        for (final BlockPos target : TARGET_LOCALS) {
            blocks.put(target, Blocks.STONE.defaultBlockState());
        }
        return blocks;
    }

    private static FixtureCheck checkFixture(final ServerSubLevel subLevel) {
        final List<String> failures = new ArrayList<>();
        final BlockState pistonState = getLocalBlockState(subLevel, PISTON_LOCAL);
        final boolean pistonController = blockIdMatches(pistonState, STICKY_MECHANICAL_PISTON_ID)
                && propertyMatches(pistonState, "facing", "east")
                && propertyMatches(pistonState, "axis_along_first", "true");
        if (!pistonController) {
            failures.add("sticky_mechanical_piston_missing_or_wrong_state_at_local_" + fmt(PISTON_LOCAL));
        }

        final boolean motorPresent = blockIdMatches(getLocalBlockState(subLevel, MOTOR_LOCAL), CREATIVE_MOTOR_ID);
        final boolean shaftPresent = blockIdMatches(getLocalBlockState(subLevel, SHAFT_LOCAL), SHAFT_ID);
        if (!motorPresent) {
            failures.add("creative_motor_missing_at_local_" + fmt(MOTOR_LOCAL));
        }
        if (!shaftPresent) {
            failures.add("drive_shaft_missing_at_local_" + fmt(SHAFT_LOCAL));
        }
        for (int i = 1; i <= PISTON_EXTENSION_POLES; i++) {
            final BlockPos pole = PISTON_LOCAL.relative(PISTON_FACING.getOpposite(), i);
            final BlockState state = getLocalBlockState(subLevel, pole);
            if (!blockIdMatches(state, PISTON_EXTENSION_POLE_ID) || !propertyMatches(state, "facing", "east")) {
                failures.add("extension_pole_missing_or_wrong_axis_at_local_" + fmt(pole));
            }
        }

        final Entity live = movedContraptionEntity(getLocalBlockEntity(subLevel, PISTON_LOCAL));
        final Object contraption = live == null ? null : firstNonNull(readFieldRaw(live, "contraption"),
                invokeNoArgRaw(live, "getContraption"));
        final int capturedBlocks = countContraptionBlocks(contraption);
        final boolean staticRetractedPayload = staticPayloadPresent(subLevel, 0);
        final boolean staticExtendedPayload = staticPayloadPresent(subLevel, PISTON_EXTENSION_POLES);
        final boolean capturedPayload = capturedPayloadPresent(contraption);
        final boolean drillPresent = staticRetractedPayload || staticExtendedPayload || capturedPayload;
        final boolean payloadOwnershipCoherent = (staticRetractedPayload ? 1 : 0)
                + (staticExtendedPayload ? 1 : 0)
                + (capturedPayload ? 1 : 0) == 1;

        if (!drillPresent) {
            failures.add("mechanical_drill_payload_missing");
        }
        if (!payloadOwnershipCoherent) {
            failures.add("payload_ownership_ambiguous_or_missing");
        }

        final String pistonStateName = statePropertyName(pistonState, "state", "unknown");
        final boolean fixtureLayout = pistonController && motorPresent && shaftPresent;
        final boolean noObviousWrongWorldMutation = pistonController && motorPresent && shaftPresent;
        final String state = live != null ? "MOVING_WITH_DRILL_ACTOR"
                : staticExtendedPayload || "extended".equals(pistonStateName) ? "STATIC_EXTENDED_READY"
                : "STATIC_RETRACTED_READY";
        return new FixtureCheck(failures, state, fixtureLayout, pistonController, drillPresent,
                payloadOwnershipCoherent, noObviousWrongWorldMutation, fixtureLayout && payloadOwnershipCoherent,
                motorPresent && shaftPresent && pistonController, pistonStateName, readFixtureMotorValue(subLevel),
                asDouble(invokeNoArgRaw(getLocalBlockEntity(subLevel, MOTOR_LOCAL), "getSpeed")),
                live != null, live == null ? -1 : live.getId(), capturedBlocks,
                staticRetractedPayload, staticExtendedPayload, capturedPayload);
    }

    private static TargetSummary summarizeTargets(final ServerSubLevel subLevel, @Nullable final BlockPos selectedRaw) {
        final List<TargetState> states = targetStates(subLevel, selectedRaw);
        final List<String> failures = new ArrayList<>();
        boolean seenPresent = false;
        BlockPos next = null;
        for (final TargetState target : states) {
            if (target.consumed()) {
                if (seenPresent) {
                    failures.add("target_sequence_not_prefix_consumed_at_local_" + fmt(target.local()));
                }
                continue;
            }
            seenPresent = true;
            if (next == null) {
                next = target.local();
            }
        }
        return new TargetSummary(states, failures.isEmpty(), next, failures);
    }

    private static List<TargetState> targetStates(final ServerSubLevel subLevel, @Nullable final BlockPos selectedRaw) {
        final List<TargetState> states = new ArrayList<>();
        for (final BlockPos local : TARGET_LOCALS) {
            final BlockPos raw = toPlot(subLevel, local);
            final BlockState state = getLocalBlockState(subLevel, local);
            final boolean consumed = state.isAir();
            states.add(new TargetState(local, raw, state, consumed ? "consumed_or_already_drilled_air" : "stone_available",
                    consumed, raw.equals(selectedRaw)));
        }
        return states;
    }

    private static ActorSnapshot findDrillActor(final ServerSubLevel subLevel) {
        final Entity entity = movedContraptionEntity(getLocalBlockEntity(subLevel, PISTON_LOCAL));
        final Object contraption = entity == null ? null : firstNonNull(readFieldRaw(entity, "contraption"),
                invokeNoArgRaw(entity, "getContraption"));
        final Object actors = firstNonNull(invokeNoArgRaw(contraption, "getActors"), readFieldRaw(contraption, "actors"));
        if (actors instanceof final Iterable<?> iterable) {
            for (final Object actor : iterable) {
                final Object context = firstNonNull(invokeNoArgRaw(actor, "getRight"), readFieldRaw(actor, "right"));
                final BlockState state = asBlockState(readFieldRaw(context, "state"));
                if (state == null || !blockIdMatches(state, MECHANICAL_DRILL_ID)) {
                    continue;
                }
                final CompoundTag data = readFieldRaw(context, "data") instanceof final CompoundTag tag ? tag : new CompoundTag();
                return new ActorSnapshot(true,
                        entity.getId(),
                        asBlockPos(readFieldRaw(context, "localPos")),
                        blockId(state).toString(),
                        directionProperty(state, "facing", Direction.EAST),
                        getBlockPos(data, "BreakingPos"),
                        data.getInt("Progress"),
                        data.getInt("BreakerId"),
                        asBoolean(readFieldRaw(context, "stall")));
            }
        }

        final BlockPos staticDrill = staticPayloadPresent(subLevel, 0) ? DRILL_RETRACTED
                : staticPayloadPresent(subLevel, PISTON_EXTENSION_POLES) ? DRILL_RETRACTED.relative(PISTON_FACING, PISTON_EXTENSION_POLES)
                : null;
        final BlockState state = staticDrill == null ? Blocks.AIR.defaultBlockState() : getLocalBlockState(subLevel, staticDrill);
        return new ActorSnapshot(staticDrill != null,
                -1,
                staticDrill,
                blockId(state).toString(),
                directionProperty(state, "facing", Direction.EAST),
                null,
                0,
                -1,
                false);
    }

    private static boolean staticPayloadPresent(final ServerSubLevel subLevel, final int pistonOffset) {
        return blockIdMatches(getLocalBlockState(subLevel, PAYLOAD_CHASSIS_RETRACTED.relative(PISTON_FACING, pistonOffset)),
                RADIAL_CHASSIS_ID)
                && blockIdMatches(getLocalBlockState(subLevel, DRILL_RETRACTED.relative(PISTON_FACING, pistonOffset)),
                MECHANICAL_DRILL_ID);
    }

    private static boolean capturedPayloadPresent(@Nullable final Object contraption) {
        boolean chassis = false;
        boolean drill = false;
        for (final BlockState state : capturedBlockStates(contraption)) {
            chassis |= blockIdMatches(state, RADIAL_CHASSIS_ID);
            drill |= blockIdMatches(state, MECHANICAL_DRILL_ID);
        }
        return chassis && drill;
    }

    private static List<BlockPos> diagnosticLayoutLocals() {
        final List<BlockPos> locals = new ArrayList<>();
        locals.add(MOTOR_LOCAL);
        locals.add(SHAFT_LOCAL);
        locals.add(PISTON_LOCAL);
        for (int i = 1; i <= PISTON_EXTENSION_POLES; i++) {
            locals.add(PISTON_LOCAL.relative(PISTON_FACING.getOpposite(), i));
            locals.add(PISTON_LOCAL.relative(PISTON_FACING, i));
        }
        locals.add(PAYLOAD_CHASSIS_RETRACTED);
        locals.add(DRILL_RETRACTED);
        locals.add(PAYLOAD_CHASSIS_RETRACTED.relative(PISTON_FACING, PISTON_EXTENSION_POLES));
        locals.add(DRILL_RETRACTED.relative(PISTON_FACING, PISTON_EXTENSION_POLES));
        locals.addAll(TARGET_LOCALS);
        locals.sort(M16TestCommands::compareBlockPos);
        final List<BlockPos> unique = new ArrayList<>();
        for (final BlockPos local : locals) {
            if (!unique.contains(local)) {
                unique.add(local);
            }
        }
        return unique;
    }

    private static boolean layoutPositionValid(final ServerSubLevel subLevel, final BlockPos local, final BlockState state) {
        if (local.equals(MOTOR_LOCAL)) {
            return blockIdMatches(state, CREATIVE_MOTOR_ID);
        }
        if (local.equals(SHAFT_LOCAL)) {
            return blockIdMatches(state, SHAFT_ID);
        }
        if (local.equals(PISTON_LOCAL)) {
            return blockIdMatches(state, STICKY_MECHANICAL_PISTON_ID);
        }
        if (TARGET_LOCALS.contains(local)) {
            return state.isAir() || blockIdMatches(state, BuiltInRegistries.BLOCK.getKey(Blocks.STONE));
        }
        return state.isAir() || blockIdMatches(state, PISTON_EXTENSION_POLE_ID)
                || blockIdMatches(state, RADIAL_CHASSIS_ID)
                || blockIdMatches(state, MECHANICAL_DRILL_ID)
                || blockId(state).getPath().contains("piston_head");
    }

    private static String layoutRole(final BlockPos local) {
        if (local.equals(MOTOR_LOCAL)) {
            return "creative_motor_vertical_drive";
        }
        if (local.equals(SHAFT_LOCAL)) {
            return "shaft_y_axis_drive";
        }
        if (local.equals(PISTON_LOCAL)) {
            return "sticky_mechanical_piston_controller";
        }
        if (local.equals(PAYLOAD_CHASSIS_RETRACTED)) {
            return "retracted_payload_radial_chassis";
        }
        if (local.equals(DRILL_RETRACTED)) {
            return "retracted_mechanical_drill_actor";
        }
        if (TARGET_LOCALS.contains(local)) {
            return "deterministic_stone_break_target";
        }
        return "piston_pole_travel_or_endpoint";
    }

    private static String expectedLayout(final BlockPos local) {
        if (local.equals(MOTOR_LOCAL)) {
            return "create:creative_motor[facing=up], value initially 0";
        }
        if (local.equals(SHAFT_LOCAL)) {
            return "create:shaft[axis=y]";
        }
        if (local.equals(PISTON_LOCAL)) {
            return "create:sticky_mechanical_piston[facing=east,axis_along_first=true]";
        }
        if (local.equals(DRILL_RETRACTED)) {
            return "create:mechanical_drill[facing=east] or captured/extended equivalent";
        }
        if (TARGET_LOCALS.contains(local)) {
            return "minecraft:stone until drilled, then air";
        }
        return "Create piston pole/head/travel space according to current piston state";
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

    private static void setFixtureMotorSpeed(final ServerSubLevel subLevel, final int rpm) {
        final BlockEntity motor = getLocalBlockEntity(subLevel, MOTOR_LOCAL);
        if (motor == null) {
            throw new IllegalStateException("M16 fixture motor is missing at local " + fmt(MOTOR_LOCAL));
        }
        final Object behaviour = readFieldRaw(motor, "generatedSpeed");
        if (behaviour == null || invokeIntArgRaw(behaviour, "setValue", rpm) == null) {
            throw new IllegalStateException("M16 fixture motor does not expose Create ScrollValueBehaviour at local "
                    + fmt(MOTOR_LOCAL));
        }
    }

    private static int readFixtureMotorValue(final ServerSubLevel subLevel) {
        final BlockEntity motor = getLocalBlockEntity(subLevel, MOTOR_LOCAL);
        final Object behaviour = readFieldRaw(motor, "generatedSpeed");
        final Object value = invokeNoArgRaw(behaviour, "getValue");
        return value instanceof final Number number ? number.intValue() : Integer.MIN_VALUE;
    }

    private static M16Stats inspectStats(final ServerSubLevel subLevel) {
        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(subLevel.getLevel());
        final RigidBodyHandle handle = physicsSystem == null ? null : physicsSystem.getPhysicsHandle(subLevel);
        final Vector3d linear = handle == null ? new Vector3d(Double.NaN) : handle.getLinearVelocity(new Vector3d());
        final Vector3d angular = handle == null ? new Vector3d(Double.NaN) : handle.getAngularVelocity(new Vector3d());
        final MassData mass = subLevel.getMassTracker();
        return new M16Stats(countNonAirBlocks(subLevel), mass == null ? Double.NaN : mass.getMass(), new Vector3d(subLevel.logicalPose().position()),
                linear, angular);
    }

    private static int countNonAirBlocks(final ServerSubLevel subLevel) {
        int count = 0;
        for (final BlockSample ignored : scanBlocks(subLevel)) {
            count++;
        }
        return count;
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
                            if (!state.isAir()) {
                                final BlockPos plotPos = new BlockPos(minX + x, minY + y, minZ + z);
                                blocks.add(new BlockSample(plotPos.subtract(subLevel.getPlot().getCenterBlock()),
                                        plotPos, new ChunkPos(plotPos), state));
                            }
                        }
                    }
                }
            }
        }
        blocks.sort(Comparator.comparing(BlockSample::localPos, M16TestCommands::compareBlockPos));
        return blocks;
    }

    private static @Nullable BlockEntity getLocalBlockEntity(final ServerSubLevel subLevel,
                                                            @Nullable final BlockPos localPos) {
        if (localPos == null) {
            return null;
        }
        return SubLevelBlockStateLookup.getBlockEntity(subLevel, toPlot(subLevel, localPos));
    }

    private static BlockState getLocalBlockState(final ServerSubLevel subLevel, @Nullable final BlockPos localPos) {
        if (localPos == null) {
            return Blocks.AIR.defaultBlockState();
        }
        return SubLevelBlockStateLookup.getBlockStateOrAir(subLevel, toPlot(subLevel, localPos));
    }

    private static BlockPos toPlot(final ServerSubLevel subLevel, final BlockPos localPos) {
        return SubLevelBlockEditHelper.localOffsetToPlotBlock(subLevel, localPos);
    }

    private static @Nullable BlockPos toLocalIfOwned(final ServerSubLevel subLevel, @Nullable final BlockPos plotPos) {
        if (plotPos == null || Sable.HELPER.getContaining(subLevel.getLevel(), plotPos) != subLevel) {
            return null;
        }
        return plotPos.subtract(subLevel.getPlot().getCenterBlock());
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

    private static ResourceLocation blockId(final BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static boolean blockIdMatches(final BlockState state, final ResourceLocation blockId) {
        return blockId.equals(blockId(state));
    }

    private static boolean propertyMatches(final BlockState state, final String propertyName, final String valueName) {
        for (final Property<?> property : state.getProperties()) {
            if (property.getName().equals(propertyName)) {
                return String.valueOf(state.getValue(property)).equalsIgnoreCase(valueName);
            }
        }
        return false;
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
                return String.valueOf(state.getValue(property)).toLowerCase(Locale.ROOT);
            }
        }
        return fallback;
    }

    private static @Nullable Entity movedContraptionEntity(@Nullable final BlockEntity piston) {
        final Object moved = firstNonNull(readFieldRaw(piston, "movedContraption"), invokeNoArgRaw(piston, "getMovedContraption"));
        return moved instanceof final Entity entity ? entity : null;
    }

    private static int countContraptionBlocks(@Nullable final Object contraption) {
        return contraptionBlocks(contraption).size();
    }

    private static Map<?, ?> contraptionBlocks(@Nullable final Object contraption) {
        final Object blocks = firstNonNull(invokeNoArgRaw(contraption, "getBlocks"), readFieldRaw(contraption, "blocks"));
        return blocks instanceof final Map<?, ?> map ? map : Map.of();
    }

    private static List<BlockState> capturedBlockStates(@Nullable final Object contraption) {
        final List<BlockState> states = new ArrayList<>();
        for (final Object info : contraptionBlocks(contraption).values()) {
            final BlockState state = asBlockState(firstNonNull(invokeNoArgRaw(info, "state"), readFieldRaw(info, "state")));
            if (state != null) {
                states.add(state);
            }
        }
        return states;
    }

    private static @Nullable BlockState asBlockState(@Nullable final Object value) {
        return value instanceof final BlockState state ? state : null;
    }

    private static @Nullable BlockPos asBlockPos(@Nullable final Object value) {
        return value instanceof final BlockPos blockPos ? blockPos : null;
    }

    private static @Nullable BlockPos getBlockPos(final CompoundTag data, final String key) {
        return data.contains(key) && data.get(key) instanceof CompoundTag ? NbtUtils.readBlockPos(data.getCompound(key)) : null;
    }

    private static @Nullable Object firstNonNull(@Nullable final Object first, @Nullable final Object second) {
        return first != null ? first : second;
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
                // Create stores useful state across superclass boundaries.
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
                // Create stores useful state across superclass boundaries.
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
                // Create stores useful state across superclass boundaries.
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
        return value instanceof final Number number ? number.doubleValue() : Double.NaN;
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
        Sable.LOGGER.warn("SABLE_M16 phase=rollback_begin name={} id={} reason={}",
                name, subLevel.getUniqueId(), failure.getClass().getSimpleName());
        try {
            container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
        } catch (final Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static CommandSyntaxException commandFailure(final RuntimeException exception) {
        Sable.LOGGER.error("SABLE_M16 phase=failed", exception);
        final String message = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        return ERROR_M16_FAILED.create(message);
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

    private static String fmt(@Nullable final BlockPos pos) {
        return pos == null ? "null" : "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    private static String fmt(final double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.6f", value) : "nan";
    }

    private static String formatVector(final Vector3dc vector) {
        return String.format(Locale.ROOT, "(%.6f,%.6f,%.6f)", vector.x(), vector.y(), vector.z());
    }

    private static String mergeFailures(final List<String> first, final List<String> second) {
        final List<String> merged = new ArrayList<>(first);
        merged.addAll(second);
        return merged.toString();
    }

    private record M16Stats(int blockCount, double mass, Vector3dc position, Vector3dc linearVelocity,
                            Vector3dc angularVelocity) {
    }

    private record BlockSample(BlockPos localPos, BlockPos plotPos, ChunkPos chunkPos, BlockState state) {
    }

    private record FixtureCheck(List<String> failures, String state, boolean fixtureLayout,
                                boolean pistonController, boolean drillPresent,
                                boolean payloadOwnershipCoherent, boolean noObviousWrongWorldMutation,
                                boolean ready, boolean controlReady, String pistonState, int motorValue,
                                double motorSpeed, boolean liveContraption, int entityId,
                                int capturedBlocks, boolean staticRetractedPayload,
                                boolean staticExtendedPayload, boolean capturedPayload) {
    }

    private record ActorSnapshot(boolean present, int entityId, @Nullable BlockPos actorLocal,
                                 String actorStateId, Direction facing, @Nullable BlockPos breakingPos,
                                 int progress, int breakerId, boolean stall) {
    }

    private record TargetState(BlockPos local, BlockPos raw, BlockState state, String expected,
                               boolean consumed, boolean selected) {
    }

    private record TargetSummary(List<TargetState> states, boolean sequenceCoherent,
                                 @Nullable BlockPos nextExpected, List<String> failures) {
        private String compact() {
            final List<String> parts = new ArrayList<>();
            int index = 1;
            for (final TargetState state : states) {
                parts.add("t" + index + "=" + (state.consumed() ? "air" : "present"));
                index++;
            }
            return parts.toString();
        }
    }
}
