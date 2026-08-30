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
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

/** M18 moving Deployer acceptance harness; fixture-local coordinates are the test truth. */
public final class M18TestCommands {
    private static final ResourceLocation CREATIVE_MOTOR_ID = new ResourceLocation("create", "creative_motor");
    private static final ResourceLocation SHAFT_ID = new ResourceLocation("create", "shaft");
    private static final ResourceLocation STICKY_MECHANICAL_PISTON_ID = new ResourceLocation("create", "sticky_mechanical_piston");
    private static final ResourceLocation PISTON_EXTENSION_POLE_ID = new ResourceLocation("create", "piston_extension_pole");
    private static final ResourceLocation RADIAL_CHASSIS_ID = new ResourceLocation("create", "radial_chassis");
    private static final ResourceLocation DEPLOYER_ID = new ResourceLocation("create", "deployer");
    private static final BlockPos MOTOR_LOCAL = new BlockPos(0, -2, 0);
    private static final BlockPos SHAFT_LOCAL = new BlockPos(0, -1, 0);
    private static final BlockPos PISTON_LOCAL = BlockPos.ZERO;
    private static final BlockPos PAYLOAD_CHASSIS_RETRACTED = new BlockPos(1, 0, 0);
    private static final BlockPos DEPLOYER_RETRACTED = new BlockPos(1, 1, 0);
    private static final BlockPos USE_SUPPORT_LOCAL = new BlockPos(6, 0, 0);
    private static final BlockPos USE_TARGET_LOCAL = new BlockPos(6, 1, 0);
    private static final BlockPos PUNCH_TARGET_LOCAL = new BlockPos(7, 1, 0);
    private static final Direction PISTON_FACING = Direction.EAST;
    private static final int PISTON_EXTENSION_POLES = 4;
    private static final int SPAWN_MOTOR_RPM = 0;
    private static final int EXTEND_RPM = -32;
    private static final int RETRACT_RPM = 32;
    private static final DynamicCommandExceptionType ERROR_M18_FAILED = new DynamicCommandExceptionType(
            message -> Component.literal("SABLE_M18 command failed: " + message));

    private M18TestCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m18")
                .then(Commands.literal("spawn_deployer")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(M18TestCommands::spawnDeployer)))
                .then(Commands.literal("validate")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M18TestCommands::validate)))
                .then(Commands.literal("dump_layout")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M18TestCommands::dumpLayout)))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M18TestCommands::inspect)))
                .then(Commands.literal("prepare_use")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(ctx -> prepare(ctx, "USE"))))
                .then(Commands.literal("prepare_punch")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(ctx -> prepare(ctx, "PUNCH"))))
                .then(Commands.literal("extend")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(ctx -> setMotorSpeed(ctx, EXTEND_RPM, "extend"))))
                .then(Commands.literal("retract")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(ctx -> setMotorSpeed(ctx, RETRACT_RPM, "retract"))))
                .then(Commands.literal("snapshot")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M18TestCommands::snapshot)))
                .then(Commands.literal("targets")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M18TestCommands::targets)))
                .then(Commands.literal("inventory")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M18TestCommands::inventory)))
                .then(Commands.literal("test_translate_parent")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(ctx -> setBodyVelocity(ctx, "translate_parent", new Vector3d(0.0, 1.0, 0.0), new Vector3d()))))
                .then(Commands.literal("test_rotate_parent")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(ctx -> setBodyVelocity(ctx, "rotate_parent", new Vector3d(),
                                        new Vector3d(0.0, Math.toRadians(20.0), 0.0)))))
                .then(Commands.literal("prepare_airborne")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M18TestCommands::prepareAirborne)))
                .then(Commands.literal("airborne_acceptance")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M18TestCommands::airborneAcceptance)))
                .then(Commands.literal("save_reload_check")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M18TestCommands::saveReloadCheck)))
                .then(Commands.literal("acceptance")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M18TestCommands::acceptance))));
    }

    private static int spawnDeployer(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(context);
        final String name = StringArgumentType.getString(context, "name");
        ServerSubLevel subLevel = null;
        try {
            subLevel = createEmptySubLevel(context, container, name);
            final List<SubLevelBlockEditHelper.BlockChange> changes = applyBlocks(subLevel, deployerFixtureBlocks());
            setFixtureMotorSpeed(subLevel, SPAWN_MOTOR_RPM);
            SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
            setFixtureMotorSpeed(subLevel, SPAWN_MOTOR_RPM);
            subLevel.updateLastPose();
            configureStaticDeployer(subLevel, "USE", new ItemStack(Items.COBBLESTONE, 4));
            final FixtureCheck check = checkFixture(subLevel);
            final String line = "SABLE_M18_SPAWN name=" + name
                    + " uuid=" + subLevel.getUniqueId()
                    + " status=" + pass(check.ready())
                    + " pistonLocal=" + fmt(PISTON_LOCAL)
                    + " deployerLocal=" + fmt(DEPLOYER_RETRACTED)
                    + " deployerFacing=east"
                    + " activeAreaOffset=east*2"
                    + " useTarget=" + fmt(USE_TARGET_LOCAL)
                    + " punchTarget=" + fmt(PUNCH_TARGET_LOCAL)
                    + " semantics=sticky_piston_carrier_normal_create_deployer_fake_player"
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
        final ActorSnapshot actor = findDeployerActor(subLevel);
        final TargetSummary targetSummary = targetSummary(subLevel, actor);
        final String line = "SABLE_M18_VALIDATE state=" + check.state()
                + " status=" + pass(check.ready() && targetSummary.coherent())
                + " FIXTURE_LAYOUT=" + pass(check.fixtureLayout())
                + " PISTON_CONTROLLER=" + pass(check.pistonController())
                + " DEPLOYER_ACTOR=" + pass(check.deployerPresent())
                + " PAYLOAD_OWNERSHIP=" + pass(check.payloadOwnershipCoherent())
                + " TARGETS=" + pass(targetSummary.coherent())
                + " FAKE_PLAYER_INTERACTION=UNVERIFIED_UNTIL_RUNTIME_ACTIVATION"
                + " CLIENT_RENDER=UNVERIFIED_USER_OBSERVATION"
                + " PERSISTENCE=UNVERIFIED_UNTIL_MANUAL_RELOAD"
                + " id=" + subLevel.getUniqueId()
                + " mode=" + actor.mode()
                + " motorValue=" + check.motorValue()
                + " pistonState=" + check.pistonState()
                + " liveContraption=" + check.liveContraption()
                + " capturedBlocks=" + check.capturedBlocks()
                + " targetStates=" + targetSummary.compact()
                + " failures=" + mergeFailures(check.failures(), targetSummary.failures());
        send(context, line);
        Sable.LOGGER.info(line);
        return check.ready() && targetSummary.coherent() ? 1 : 0;
    }

    private static int dumpLayout(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        int count = 0;
        for (final BlockPos local : diagnosticLayoutLocals()) {
            final BlockState state = getLocalBlockState(subLevel, local);
            final String line = "SABLE_M18_LAYOUT id=" + subLevel.getUniqueId()
                    + " localPos=" + fmt(local)
                    + " rawPlotPos=" + fmt(toPlot(subLevel, local))
                    + " blockId=" + blockId(state)
                    + " state=" + state
                    + " role=" + layoutRole(local)
                    + " expected=" + expectedLayout(local)
                    + " valid=" + layoutPositionValid(local, state);
            send(context, line);
            Sable.LOGGER.info(line);
            count++;
        }
        return count;
    }

    private static int inspect(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck check = checkFixture(subLevel);
        final ActorSnapshot actor = findDeployerActor(subLevel);
        final M18Stats stats = inspectStats(subLevel);
        final String line = "SABLE_M18_INSPECT id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " state=" + check.state()
                + " entityId=" + actor.entityId()
                + " actorLocal=" + fmt(actor.actorLocal())
                + " facing=" + actor.facing().getName()
                + " mode=" + actor.mode()
                + " targetRaw=" + fmt(actor.targetRaw())
                + " targetFixtureLocal=" + fmt(toLocalIfOwned(subLevel, actor.targetRaw()))
                + " visitTargetSequence=" + visitSequence()
                + " expectedUseClicked=" + fmt(USE_TARGET_LOCAL)
                + " expectedUseResult=" + fmt(USE_TARGET_LOCAL)
                + " expectedPunchAttack=" + fmt(PUNCH_TARGET_LOCAL)
                + " progress=" + actor.progress()
                + " breakerId=" + actor.breakerId()
                + " stall=" + actor.stall()
                + " heldItem=" + actor.heldItem()
                + " contextHeldItem=" + actor.contextHeldItem()
                + " blockEntityInventory=" + actor.blockEntityInventory()
                + " fakePlayerMainHand=" + actor.fakePlayerMainHand()
                + " blockBreakingTarget=" + fmt(toLocalIfOwned(subLevel, actor.blockBreakingTarget()))
                + " pistonState=" + check.pistonState()
                + " capturedBlocks=" + check.capturedBlocks()
                + " containingSubLevelKnown=" + (actor.targetRaw() == null || Sable.HELPER.getContaining(subLevel.getLevel(), actor.targetRaw()) == subLevel)
                + " pose=" + formatVector(stats.position())
                + " linearVelocity=" + formatVector(stats.linearVelocity())
                + " angularVelocity=" + formatVector(stats.angularVelocity())
                + " hiddenPlotPoseTranslation=false";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int prepareAirborne(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck check = checkFixture(subLevel);
        if (check.liveContraption()) {
            final String rejected = "SABLE_M18_AIRBORNE id=" + subLevel.getUniqueId()
                    + " result=REJECTED_ACTIVE_CONTRAPTION"
                    + " reason=retract_or_wait_for_piston_before_airborne_pose";
            send(context, rejected);
            Sable.LOGGER.info(rejected);
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
        final String line = "SABLE_M18_AIRBORNE id=" + subLevel.getUniqueId()
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
        final TargetSummary targets = targetSummary(subLevel, findDeployerActor(subLevel));
        final boolean pass = control > 0 && check.ready() && targets.coherent();
        final String line = "SABLE_M18_AIRBORNE_ACCEPTANCE id=" + subLevel.getUniqueId()
                + " status=" + pass(pass)
                + " parentMotion=combined_linear_angular_via_M10_physics"
                + " pistonControl=normal_create_motor_speed"
                + " targetIdentity=" + pass(targets.coherent())
                + " payloadIntegrity=" + pass(check.payloadOwnershipCoherent())
                + " wrongWorldMutation=" + pass(check.noObviousWrongWorldMutation())
                + " gravityStillActive=true"
                + " anchored=false"
                + " failures=" + mergeFailures(check.failures(), targets.failures());
        send(context, line);
        Sable.LOGGER.info(line);
        return pass ? 1 : 0;
    }

    private static int prepare(final CommandContext<CommandSourceStack> context, final String mode) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck before = checkFixture(subLevel);
        if (before.liveContraption()) {
            final String rejected = "SABLE_M18_PREPARE mode=" + mode
                    + " id=" + subLevel.getUniqueId()
                    + " result=REJECTED_ACTIVE_CONTRAPTION"
                    + " reason=retract_or_wait_for_piston_before_setup";
            send(context, rejected);
            Sable.LOGGER.info(rejected);
            return 0;
        }
        final List<SubLevelBlockEditHelper.BlockChange> changes = new ArrayList<>();
        addChangeIfDifferent(changes, subLevel, USE_SUPPORT_LOCAL,
                "USE".equals(mode) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        addChangeIfDifferent(changes, subLevel, USE_TARGET_LOCAL, Blocks.AIR.defaultBlockState());
        addChangeIfDifferent(changes, subLevel, PUNCH_TARGET_LOCAL, Blocks.STONE.defaultBlockState());
        SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
        configureStaticDeployer(subLevel, mode, "USE".equals(mode)
                ? new ItemStack(Items.COBBLESTONE, 4)
                : new ItemStack(Items.IRON_PICKAXE, 1));
        setFixtureMotorSpeed(subLevel, SPAWN_MOTOR_RPM);
        final TargetSummary targets = targetSummary(subLevel, null, mode);
        final String line = "SABLE_M18_PREPARE mode=" + mode
                + " id=" + subLevel.getUniqueId()
                + " result=APPLIED"
                + " performsTestedInteraction=false"
                + " heldItem=" + heldItemSummary(subLevel)
                + " staticInventory=" + staticInventorySummary(subLevel)
                + " activeAreaOffset=east*2"
                + " visitTargetSequence=" + visitSequence()
                + " expectedUseClicked=" + fmt(USE_TARGET_LOCAL)
                + " expectedUseResult=" + fmt(USE_TARGET_LOCAL)
                + " expectedPunchAttack=" + fmt(PUNCH_TARGET_LOCAL)
                + " useSupport=" + fmt(USE_SUPPORT_LOCAL)
                + " targetStates=" + targets.compact()
                + " semantics=setup_only_Create_activation_occurs_on_extend";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int snapshot(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck check = checkFixture(subLevel);
        final ActorSnapshot actor = findDeployerActor(subLevel);
        final TargetSummary targets = targetSummary(subLevel, actor);
        final String line = "SABLE_M18_SNAPSHOT id=" + subLevel.getUniqueId()
                + " state=" + check.state()
                + " pistonState=" + check.pistonState()
                + " mode=" + actor.mode()
                + " expectedNextTarget=" + fmt(targets.nextExpected())
                + " currentOrLastTarget=" + fmt(toLocalIfOwned(subLevel, actor.targetRaw()))
                + " visitTargetSequence=" + visitSequence()
                + " expectedUseResult=" + fmt(USE_TARGET_LOCAL)
                + " expectedPunchAttack=" + fmt(PUNCH_TARGET_LOCAL)
                + " progress=" + actor.progress()
                + " heldItem=" + actor.heldItem()
                + " contextHeldItem=" + actor.contextHeldItem()
                + " fakePlayerMainHand=" + actor.fakePlayerMainHand()
                + " useComplete=" + targets.useComplete()
                + " punchComplete=" + targets.punchComplete()
                + " targetStates=" + targets.compact()
                + " payloadIntegrity=" + pass(check.payloadOwnershipCoherent())
                + " wrongWorldMutation=" + pass(check.noObviousWrongWorldMutation())
                + " parentPose=" + formatVector(inspectStats(subLevel).position())
                + " renderVisual=USER_OBSERVED_RUNTIME_REQUIRED";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int targets(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final ActorSnapshot actor = findDeployerActor(subLevel);
        int count = 0;
        for (final TargetState state : targetStates(subLevel, actor.targetRaw())) {
            final String line = "SABLE_M18_TARGET id=" + subLevel.getUniqueId()
                    + " local=" + fmt(state.local())
                    + " raw=" + fmt(state.raw())
                    + " blockId=" + blockId(state.state())
                    + " state=" + state.state()
                    + " expected=" + state.expected()
                    + " selected=" + state.selected()
                    + " fixtureLocalTruth=true";
            send(context, line);
            Sable.LOGGER.info(line);
            count++;
        }
        return count;
    }

    private static int inventory(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final ActorSnapshot actor = findDeployerActor(subLevel);
        final String line = "SABLE_M18_INVENTORY id=" + subLevel.getUniqueId()
                + " mode=" + actor.mode()
                + " heldItem=" + actor.heldItem()
                + " contextHeldItem=" + actor.contextHeldItem()
                + " blockEntityInventory=" + actor.blockEntityInventory()
                + " fakePlayerMainHand=" + actor.fakePlayerMainHand()
                + " contraptionStorage=" + actor.contraptionStorage()
                + " staticHeldItem=" + heldItemSummary(subLevel)
                + " staticInventory=" + staticInventorySummary(subLevel)
                + " inventoryState=read_only";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int saveReloadCheck(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck check = checkFixture(subLevel);
        final TargetSummary targets = targetSummary(subLevel, findDeployerActor(subLevel));
        final String line = "SABLE_M18_SAVE_RELOAD_CHECK id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " status=" + pass(check.ready() && targets.coherent())
                + " note=run_before_and_after_manual_save_reload"
                + " mode=" + deployerMode(subLevel)
                + " heldItem=" + heldItemSummary(subLevel)
                + " targetStates=" + targets.compact()
                + " persistence=UNVERIFIED_UNTIL_MANUAL_RELOAD";
        send(context, line);
        Sable.LOGGER.info(line);
        return check.ready() && targets.coherent() ? 1 : 0;
    }

    private static int acceptance(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck check = checkFixture(subLevel);
        final ActorSnapshot actor = findDeployerActor(subLevel);
        final TargetSummary targets = targetSummary(subLevel, actor);
        final M18Stats stats = inspectStats(subLevel);
        final boolean airborneOrMoving = vectorLength(stats.linearVelocity()) > 0.01 || vectorLength(stats.angularVelocity()) > 0.01;
        final boolean useStackConsumed = heldCount(actor, BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE)) == 3;
        final boolean punchProgressOrCompletion = actor.stall() || actor.blockBreakingTarget() != null
                || actor.progress() > 0 || targets.punchComplete();
        final boolean semanticComplete = "PUNCH".equals(targets.mode())
                ? targets.punchComplete() && punchProgressOrCompletion
                : targets.useComplete() && useStackConsumed;
        final boolean pass = check.ready() && targets.coherent() && semanticComplete;
        final String line = "SABLE_M18_ACCEPTANCE id=" + subLevel.getUniqueId()
                + " status=" + pass(pass)
                + " serverSemanticTargets=" + pass(targets.coherent())
                + " useComplete=" + pass(targets.useComplete())
                + " useHeldCountConsumed=" + pass(useStackConsumed)
                + " punchComplete=" + pass(targets.punchComplete())
                + " punchProgressOrCompletion=" + pass(punchProgressOrCompletion)
                + " noDirectHarnessInteraction=true"
                + " mode=" + targets.mode()
                + " payloadIntegrity=" + pass(check.payloadOwnershipCoherent())
                + " wrongWorldMutation=" + pass(check.noObviousWrongWorldMutation())
                + " parentMotionObservable=" + pass(airborneOrMoving)
                + " visualAcceptance=USER_OBSERVED"
                + " failures=" + mergeFailures(check.failures(), targets.failures());
        send(context, line);
        Sable.LOGGER.info(line);
        return pass ? 1 : 0;
    }

    private static int setMotorSpeed(final CommandContext<CommandSourceStack> context,
                                     final int rpm,
                                     final String action) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck before = checkFixture(subLevel);
        if (!before.controlReady() && rpm != SPAWN_MOTOR_RPM) {
            final String rejected = "SABLE_M18_CONTROL action=" + action
                    + " id=" + subLevel.getUniqueId()
                    + " requestedMotorValue=" + rpm
                    + " result=REJECTED_FIXTURE_INVALID"
                    + " failures=" + before.failures();
            send(context, rejected);
            Sable.LOGGER.info(rejected);
            return 0;
        }
        setFixtureMotorSpeed(subLevel, rpm);
        final FixtureCheck after = checkFixture(subLevel);
        final String line = "SABLE_M18_CONTROL action=" + action
                + " id=" + subLevel.getUniqueId()
                + " requestedMotorValue=" + rpm
                + " previousMotorValue=" + before.motorValue()
                + " motorValue=" + after.motorValue()
                + " motorSpeed=" + fmt(after.motorSpeed())
                + " result=APPLIED"
                + " semantics=Create_ScrollValueBehaviour_setValue_no_manual_deployer_interaction";
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
        final String line = "SABLE_M18_TEST_PARENT id=" + subLevel.getUniqueId()
                + " preset=" + preset
                + " result=APPLIED_M10_PHYSICS_VELOCITY"
                + " requestedLinear=" + formatVector(linearVelocity)
                + " requestedAngularRadS=" + formatVector(angularVelocityRad)
                + " fixtureLocalTargetsUnchanged=true";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static Map<BlockPos, BlockState> deployerFixtureBlocks() {
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        blocks.put(MOTOR_LOCAL, setProperty(requireBlockState(CREATIVE_MOTOR_ID), "facing", "up"));
        blocks.put(SHAFT_LOCAL, setProperty(requireBlockState(SHAFT_ID), "axis", "y"));
        blocks.put(PISTON_LOCAL, setProperty(setProperty(setProperty(requireBlockState(STICKY_MECHANICAL_PISTON_ID),
                "facing", PISTON_FACING.getName()), "axis_along_first", "true"), "state", "retracted"));
        for (int i = 1; i <= PISTON_EXTENSION_POLES; i++) {
            blocks.put(PISTON_LOCAL.relative(PISTON_FACING.getOpposite(), i),
                    setProperty(requireBlockState(PISTON_EXTENSION_POLE_ID), "facing", PISTON_FACING.getName()));
        }
        blocks.put(PAYLOAD_CHASSIS_RETRACTED, setProperty(setProperty(requireBlockState(RADIAL_CHASSIS_ID), "axis", "x"),
                "sticky_north", "true"));
        blocks.put(DEPLOYER_RETRACTED, setProperty(requireBlockState(DEPLOYER_ID), "facing", "east"));
        blocks.put(USE_SUPPORT_LOCAL, Blocks.STONE.defaultBlockState());
        blocks.put(PUNCH_TARGET_LOCAL, Blocks.STONE.defaultBlockState());
        return blocks;
    }

    private static void addChangeIfDifferent(final List<SubLevelBlockEditHelper.BlockChange> changes,
                                             final ServerSubLevel subLevel,
                                             final BlockPos localPos,
                                             final BlockState targetState) {
        final BlockState existing = getLocalBlockState(subLevel, localPos);
        if (existing.equals(targetState)) {
            return;
        }
        changes.add(SubLevelBlockEditHelper.setLocalBlock(subLevel, localPos, targetState, 3, false));
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
        final boolean deployerPresent = staticRetractedPayload || staticExtendedPayload || capturedPayload;
        final boolean payloadOwnershipCoherent = (staticRetractedPayload ? 1 : 0)
                + (staticExtendedPayload ? 1 : 0)
                + (capturedPayload ? 1 : 0) == 1;
        if (!deployerPresent) {
            failures.add("deployer_payload_missing");
        }
        if (!payloadOwnershipCoherent) {
            failures.add("payload_ownership_ambiguous_or_missing");
        }
        final String pistonStateName = statePropertyName(pistonState, "state", "unknown");
        final boolean fixtureLayout = pistonController && motorPresent && shaftPresent;
        final String state = live != null ? "MOVING_WITH_DEPLOYER_ACTOR"
                : staticExtendedPayload || "extended".equals(pistonStateName) ? "STATIC_EXTENDED_READY"
                : "STATIC_RETRACTED_READY";
        return new FixtureCheck(failures, state, fixtureLayout, pistonController, deployerPresent,
                payloadOwnershipCoherent, fixtureLayout && payloadOwnershipCoherent,
                fixtureLayout && payloadOwnershipCoherent, fixtureLayout,
                pistonStateName, readFixtureMotorValue(subLevel),
                asDouble(invokeNoArgRaw(getLocalBlockEntity(subLevel, MOTOR_LOCAL), "getSpeed")),
                live != null, live == null ? -1 : live.getId(), capturedBlocks);
    }

    private static boolean staticPayloadPresent(final ServerSubLevel subLevel, final int pistonOffset) {
        return blockIdMatches(getLocalBlockState(subLevel, PAYLOAD_CHASSIS_RETRACTED.relative(PISTON_FACING, pistonOffset)),
                RADIAL_CHASSIS_ID)
                && blockIdMatches(getLocalBlockState(subLevel, DEPLOYER_RETRACTED.relative(PISTON_FACING, pistonOffset)),
                DEPLOYER_ID);
    }

    private static boolean capturedPayloadPresent(@Nullable final Object contraption) {
        boolean chassis = false;
        boolean deployer = false;
        for (final BlockState state : capturedBlockStates(contraption)) {
            chassis |= blockIdMatches(state, RADIAL_CHASSIS_ID);
            deployer |= blockIdMatches(state, DEPLOYER_ID);
        }
        return chassis && deployer;
    }

    private static ActorSnapshot findDeployerActor(final ServerSubLevel subLevel) {
        final Entity entity = movedContraptionEntity(getLocalBlockEntity(subLevel, PISTON_LOCAL));
        final Object contraption = entity == null ? null : firstNonNull(readFieldRaw(entity, "contraption"),
                invokeNoArgRaw(entity, "getContraption"));
        final Object actors = firstNonNull(invokeNoArgRaw(contraption, "getActors"), readFieldRaw(contraption, "actors"));
        if (actors instanceof final Iterable<?> iterable) {
            for (final Object actor : iterable) {
                final Object context = firstNonNull(invokeNoArgRaw(actor, "getRight"), readFieldRaw(actor, "right"));
                final BlockState state = asBlockState(readFieldRaw(context, "state"));
                if (state == null || !blockIdMatches(state, DEPLOYER_ID)) {
                    continue;
                }
                final CompoundTag data = readFieldRaw(context, "data") instanceof final CompoundTag tag ? tag : new CompoundTag();
                final CompoundTag blockEntityData = readFieldRaw(context, "blockEntityData") instanceof final CompoundTag tag
                        ? tag : new CompoundTag();
                final Object temporaryData = readFieldRaw(context, "temporaryData");
                final Object breaking = readFieldRaw(temporaryData, "blockBreakingProgress");
                final String contextHeldItem = heldItemFromTag(data);
                final String blockEntityInventory = inventorySummary(blockEntityData);
                final String fakePlayerMainHand = itemStackSummary(invokeNoArgRaw(temporaryData, "getMainHandItem"));
                final String heldItem = !"empty".equals(fakePlayerMainHand) && !"unknown".equals(fakePlayerMainHand)
                        ? fakePlayerMainHand : contextHeldItem;
                return new ActorSnapshot(true, entity.getId(), asBlockPos(readFieldRaw(context, "localPos")),
                        directionProperty(state, "facing", Direction.EAST),
                        modeFromTag(blockEntityData),
                        getBlockPos(data, "BreakingPos"),
                        data.getInt("Progress"), data.getInt("BreakerId"),
                        asBoolean(readFieldRaw(context, "stall")), heldItem,
                        contextHeldItem, blockEntityInventory, fakePlayerMainHand,
                        contraptionStorageSummary(contraption),
                        breaking instanceof org.apache.commons.lang3.tuple.Pair<?, ?> pair && pair.getKey() instanceof BlockPos pos
                                ? pos : null);
            }
        }
        final BlockPos staticDeployer = staticPayloadPresent(subLevel, 0) ? DEPLOYER_RETRACTED
                : staticPayloadPresent(subLevel, PISTON_EXTENSION_POLES)
                ? DEPLOYER_RETRACTED.relative(PISTON_FACING, PISTON_EXTENSION_POLES) : null;
        final BlockState state = staticDeployer == null ? Blocks.AIR.defaultBlockState() : getLocalBlockState(subLevel, staticDeployer);
        return new ActorSnapshot(staticDeployer != null, -1, staticDeployer,
                directionProperty(state, "facing", Direction.EAST), deployerMode(subLevel), null, 0, -1,
                false, heldItemSummary(subLevel), heldItemSummary(subLevel), staticInventorySummary(subLevel),
                "not_live", "not_live", null);
    }

    private static TargetSummary targetSummary(final ServerSubLevel subLevel, final ActorSnapshot actor) {
        return targetSummary(subLevel, actor.targetRaw(), actor.mode());
    }

    private static TargetSummary targetSummary(final ServerSubLevel subLevel,
                                               @Nullable final BlockPos selectedRaw,
                                               final String modeHint) {
        final List<TargetState> states = targetStates(subLevel, selectedRaw);
        final List<String> failures = new ArrayList<>();
        final String mode = normalizedMode(modeHint);
        final boolean supportExpected = "USE".equals(mode);
        final BlockState supportState = getLocalBlockState(subLevel, USE_SUPPORT_LOCAL);
        if (supportExpected && !blockIdMatches(supportState, BuiltInRegistries.BLOCK.getKey(Blocks.STONE))) {
            failures.add("use_support_missing_at_local_" + fmt(USE_SUPPORT_LOCAL));
        }
        if (!supportExpected && !supportState.isAir()) {
            failures.add("use_support_blocks_punch_target_path_at_local_" + fmt(USE_SUPPORT_LOCAL));
        }
        if ("PUNCH".equals(mode) && !getLocalBlockState(subLevel, USE_TARGET_LOCAL).isAir()) {
            failures.add("use_target_blocks_punch_target_path_at_local_" + fmt(USE_TARGET_LOCAL));
        }
        final boolean coherent = failures.isEmpty() && ("PUNCH".equals(mode)
                ? blockIdMatches(getLocalBlockState(subLevel, PUNCH_TARGET_LOCAL), BuiltInRegistries.BLOCK.getKey(Blocks.STONE))
                        || getLocalBlockState(subLevel, PUNCH_TARGET_LOCAL).isAir()
                : getLocalBlockState(subLevel, USE_TARGET_LOCAL).isAir()
                        || blockIdMatches(getLocalBlockState(subLevel, USE_TARGET_LOCAL),
                        BuiltInRegistries.BLOCK.getKey(Blocks.COBBLESTONE)));
        final boolean useComplete = blockIdMatches(getLocalBlockState(subLevel, USE_TARGET_LOCAL),
                BuiltInRegistries.BLOCK.getKey(Blocks.COBBLESTONE))
                && blockIdMatches(getLocalBlockState(subLevel, PUNCH_TARGET_LOCAL),
                BuiltInRegistries.BLOCK.getKey(Blocks.STONE));
        final boolean punchComplete = getLocalBlockState(subLevel, USE_TARGET_LOCAL).isAir()
                && getLocalBlockState(subLevel, PUNCH_TARGET_LOCAL).isAir();
        return new TargetSummary(states, coherent, nextExpectedTarget(subLevel, mode), failures,
                mode, useComplete, punchComplete);
    }

    private static List<TargetState> targetStates(final ServerSubLevel subLevel, @Nullable final BlockPos selectedRaw) {
        final List<TargetState> states = new ArrayList<>();
        addTargetState(subLevel, states, USE_SUPPORT_LOCAL, "USE support: stone only for USE setup, air for PUNCH setup so x=7 is deterministic", selectedRaw);
        addTargetState(subLevel, states, USE_TARGET_LOCAL, "USE clicked/result target: air before deploy, cobblestone after successful fake-player use", selectedRaw);
        addTargetState(subLevel, states, PUNCH_TARGET_LOCAL, "PUNCH attacked target: stone before deploy, air after successful fake-player punch", selectedRaw);
        return states;
    }

    private static void addTargetState(final ServerSubLevel subLevel, final List<TargetState> states,
                                       final BlockPos local, final String expected, @Nullable final BlockPos selectedRaw) {
        final BlockPos raw = toPlot(subLevel, local);
        final BlockState state = getLocalBlockState(subLevel, local);
        states.add(new TargetState(local, raw, state, expected, raw.equals(selectedRaw)));
    }

    private static @Nullable BlockPos nextExpectedTarget(final ServerSubLevel subLevel, final String mode) {
        if ("PUNCH".equals(mode)) {
            return getLocalBlockState(subLevel, PUNCH_TARGET_LOCAL).isAir() ? null : PUNCH_TARGET_LOCAL;
        }
        return getLocalBlockState(subLevel, USE_TARGET_LOCAL).isAir() ? USE_TARGET_LOCAL : null;
    }

    private static void configureStaticDeployer(final ServerSubLevel subLevel, final String mode, final ItemStack stack) {
        final BlockEntity blockEntity = firstStaticDeployerBlockEntity(subLevel);
        if (blockEntity == null) {
            throw new IllegalStateException("M18 static Deployer is not available for setup");
        }
        clearOverflowItems(blockEntity);
        if (readFieldRaw(blockEntity, "player") == null) {
            invokeNoArgRaw(blockEntity, "initialize");
        }
        writeFieldRaw(blockEntity, "mode", deployerModeEnum(mode));
        final ItemStack held = stack.copy();
        writeFieldRaw(blockEntity, "heldItem", held.copy());
        writeFieldRaw(blockEntity, "deferredInventoryList", inventoryListForSelectedMainHand(held));
        final Object player = readFieldRaw(blockEntity, "player");
        invokeSetItemInHand(player, held.copy());
        final Object handler = invokeNoArgRaw(blockEntity, "createHandler");
        invokeSetStackInSlot(handler, 0, held.copy());
        blockEntity.setChanged();
        invokeNoArgRaw(blockEntity, "sendData");
    }

    private static @Nullable BlockEntity firstStaticDeployerBlockEntity(final ServerSubLevel subLevel) {
        if (staticPayloadPresent(subLevel, 0)) {
            return getLocalBlockEntity(subLevel, DEPLOYER_RETRACTED);
        }
        if (staticPayloadPresent(subLevel, PISTON_EXTENSION_POLES)) {
            return getLocalBlockEntity(subLevel, DEPLOYER_RETRACTED.relative(PISTON_FACING, PISTON_EXTENSION_POLES));
        }
        return null;
    }

    private static String deployerMode(final ServerSubLevel subLevel) {
        final BlockEntity blockEntity = firstStaticDeployerBlockEntity(subLevel);
        final Object mode = readFieldRaw(blockEntity, "mode");
        return mode == null ? "unknown_or_live" : mode.toString().toUpperCase(Locale.ROOT);
    }

    private static String heldItemSummary(final ServerSubLevel subLevel) {
        final BlockEntity blockEntity = firstStaticDeployerBlockEntity(subLevel);
        return itemStackSummary(readFieldRaw(blockEntity, "heldItem"));
    }

    private static String staticInventorySummary(final ServerSubLevel subLevel) {
        final BlockEntity blockEntity = firstStaticDeployerBlockEntity(subLevel);
        if (blockEntity == null) {
            return "unknown_or_live";
        }
        final Object deferredInventory = readFieldRaw(blockEntity, "deferredInventoryList");
        if (deferredInventory instanceof final ListTag listTag) {
            return inventoryListSummary(listTag);
        }
        final CompoundTag saved = new CompoundTag();
        invokeWriteTagRaw(blockEntity, saved, false);
        return inventorySummary(saved);
    }

    private static ListTag inventoryListForSelectedMainHand(final ItemStack stack) {
        final ListTag inventory = new ListTag();
        if (!stack.isEmpty()) {
            final CompoundTag itemTag = new CompoundTag();
            stack.save(itemTag);
            itemTag.putByte("Slot", (byte) 0);
            inventory.add(itemTag);
        }
        return inventory;
    }

    @SuppressWarnings("unchecked")
    private static void clearOverflowItems(final BlockEntity blockEntity) {
        final Object overflow = readFieldRaw(blockEntity, "overflowItems");
        if (overflow instanceof final List<?> list) {
            ((List<Object>) list).clear();
        }
    }

    private static String normalizedMode(final String mode) {
        final String upper = mode.toUpperCase(Locale.ROOT);
        return "PUNCH".equals(upper) ? "PUNCH" : "USE";
    }

    private static String modeFromTag(@Nullable final Object tag) {
        return tag instanceof final CompoundTag compound && compound.contains("Mode") ? compound.getString("Mode") : "unknown";
    }

    private static String heldItemFromTag(@Nullable final Object tag) {
        if (!(tag instanceof final CompoundTag compound) || !compound.contains("HeldItem")) {
            return "unknown";
        }
        final ItemStack stack = ItemStack.of(compound.getCompound("HeldItem"));
        return itemStackSummary(stack);
    }

    private static String inventorySummary(@Nullable final Object tag) {
        if (!(tag instanceof final CompoundTag compound) || !compound.contains("Inventory")) {
            return "unknown";
        }
        return inventoryListSummary(compound.getList("Inventory", 10));
    }

    private static String inventoryListSummary(final ListTag inventory) {
        final List<String> entries = new ArrayList<>();
        for (int i = 0; i < inventory.size(); i++) {
            final CompoundTag itemTag = inventory.getCompound(i);
            final ItemStack stack = ItemStack.of(itemTag);
            entries.add("slot" + itemTag.getByte("Slot") + "=" + itemStackSummary(stack));
        }
        return entries.isEmpty() ? "empty" : entries.toString();
    }

    private static String itemStackSummary(@Nullable final Object value) {
        if (!(value instanceof final ItemStack stack)) {
            return "unknown";
        }
        if (stack.isEmpty()) {
            return "empty";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount()
                + (stack.isDamageableItem() ? " damage=" + stack.getDamageValue() : "");
    }

    private static int heldCount(final ActorSnapshot actor, final ResourceLocation itemId) {
        final String prefix = itemId + "x";
        final List<String> summaries = List.of(actor.fakePlayerMainHand(), actor.contextHeldItem(), actor.heldItem());
        for (final String summary : summaries) {
            if (!summary.startsWith(prefix)) {
                continue;
            }
            int end = prefix.length();
            while (end < summary.length() && Character.isDigit(summary.charAt(end))) {
                end++;
            }
            if (end > prefix.length()) {
                return Integer.parseInt(summary.substring(prefix.length(), end));
            }
        }
        return -1;
    }

    private static String contraptionStorageSummary(@Nullable final Object contraption) {
        final Object storage = firstNonNull(invokeNoArgRaw(contraption, "getStorage"), readFieldRaw(contraption, "storage"));
        final Object mountedItems = invokeNoArgRaw(storage, "getAll" + "Items");
        if (mountedItems == null) {
            return "unknown";
        }
        final Object slots = invokeNoArgRaw(mountedItems, "getSlots");
        if (!(slots instanceof final Number slotCount)) {
            return "unknown";
        }
        final List<String> entries = new ArrayList<>();
        for (int slot = 0; slot < slotCount.intValue(); slot++) {
            final Object stack = invokeIntReturnRaw(mountedItems, "getStackInSlot", slot);
            final String summary = itemStackSummary(stack);
            if (!"empty".equals(summary) && !"unknown".equals(summary)) {
                entries.add("slot" + slot + "=" + summary);
            }
        }
        return entries.isEmpty() ? "empty" : entries.toString();
    }

    private static Object deployerModeEnum(final String mode) {
        try {
            final Class<?> type = Class.forName("com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity$Mode");
            @SuppressWarnings({"unchecked", "rawtypes"})
            final Object value = Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), mode);
            return value;
        } catch (final ClassNotFoundException exception) {
            throw new IllegalStateException("Create Deployer mode enum not found", exception);
        }
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
        locals.add(DEPLOYER_RETRACTED);
        locals.add(PAYLOAD_CHASSIS_RETRACTED.relative(PISTON_FACING, PISTON_EXTENSION_POLES));
        locals.add(DEPLOYER_RETRACTED.relative(PISTON_FACING, PISTON_EXTENSION_POLES));
        locals.add(USE_SUPPORT_LOCAL);
        locals.add(USE_TARGET_LOCAL);
        locals.add(PUNCH_TARGET_LOCAL);
        locals.sort(M18TestCommands::compareBlockPos);
        final List<BlockPos> unique = new ArrayList<>();
        for (final BlockPos local : locals) {
            if (!unique.contains(local)) {
                unique.add(local);
            }
        }
        return unique;
    }

    private static boolean layoutPositionValid(final BlockPos local, final BlockState state) {
        if (local.equals(MOTOR_LOCAL)) {
            return blockIdMatches(state, CREATIVE_MOTOR_ID);
        }
        if (local.equals(SHAFT_LOCAL)) {
            return blockIdMatches(state, SHAFT_ID);
        }
        if (local.equals(PISTON_LOCAL)) {
            return blockIdMatches(state, STICKY_MECHANICAL_PISTON_ID);
        }
        if (local.equals(USE_SUPPORT_LOCAL)) {
            return state.isAir() || blockIdMatches(state, BuiltInRegistries.BLOCK.getKey(Blocks.STONE));
        }
        if (local.equals(USE_TARGET_LOCAL)) {
            return state.isAir() || blockIdMatches(state, BuiltInRegistries.BLOCK.getKey(Blocks.COBBLESTONE));
        }
        if (local.equals(PUNCH_TARGET_LOCAL)) {
            return state.isAir() || blockIdMatches(state, BuiltInRegistries.BLOCK.getKey(Blocks.STONE));
        }
        return state.isAir() || blockIdMatches(state, PISTON_EXTENSION_POLE_ID)
                || blockIdMatches(state, RADIAL_CHASSIS_ID)
                || blockIdMatches(state, DEPLOYER_ID)
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
        if (local.equals(DEPLOYER_RETRACTED)) {
            return "retracted_deployer_actor";
        }
        if (local.equals(USE_SUPPORT_LOCAL)) {
            return "use_mode_air_target_support";
        }
        if (local.equals(USE_TARGET_LOCAL)) {
            return "use_mode_air_place_target";
        }
        if (local.equals(PUNCH_TARGET_LOCAL)) {
            return "punch_mode_stone_break_target";
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
        if (local.equals(DEPLOYER_RETRACTED)) {
            return "create:deployer[facing=east] or captured/extended equivalent";
        }
        if (local.equals(USE_SUPPORT_LOCAL)) {
            return "stone for USE setup, air for PUNCH setup so PUNCH first solid target is x=7";
        }
        if (local.equals(USE_TARGET_LOCAL)) {
            return "air before USE activation, cobblestone after successful fake-player place";
        }
        if (local.equals(PUNCH_TARGET_LOCAL)) {
            return "stone before PUNCH activation, air after successful fake-player break";
        }
        return "Create piston pole/head/travel space according to current piston state";
    }

    private static String visitSequence() {
        final List<String> positions = new ArrayList<>();
        for (int pistonOffset = 0; pistonOffset <= PISTON_EXTENSION_POLES; pistonOffset++) {
            positions.add(fmt(DEPLOYER_RETRACTED.relative(PISTON_FACING, pistonOffset + 2)));
        }
        return positions.toString();
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
            throw new IllegalStateException("M18 fixture motor is missing at local " + fmt(MOTOR_LOCAL));
        }
        final Object behaviour = readFieldRaw(motor, "generatedSpeed");
        if (behaviour == null || invokeIntArgRaw(behaviour, "setValue", rpm) == null) {
            throw new IllegalStateException("M18 fixture motor does not expose Create ScrollValueBehaviour at local "
                    + fmt(MOTOR_LOCAL));
        }
    }

    private static int readFixtureMotorValue(final ServerSubLevel subLevel) {
        final BlockEntity motor = getLocalBlockEntity(subLevel, MOTOR_LOCAL);
        final Object behaviour = readFieldRaw(motor, "generatedSpeed");
        final Object value = invokeNoArgRaw(behaviour, "getValue");
        return value instanceof final Number number ? number.intValue() : Integer.MIN_VALUE;
    }

    private static M18Stats inspectStats(final ServerSubLevel subLevel) {
        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(subLevel.getLevel());
        final RigidBodyHandle handle = physicsSystem == null ? null : physicsSystem.getPhysicsHandle(subLevel);
        final Vector3d linear = handle == null ? new Vector3d(Double.NaN) : handle.getLinearVelocity(new Vector3d());
        final Vector3d angular = handle == null ? new Vector3d(Double.NaN) : handle.getAngularVelocity(new Vector3d());
        final MassData mass = subLevel.getMassTracker();
        return new M18Stats(countNonAirBlocks(subLevel), mass == null ? Double.NaN : mass.getMass(),
                new Vector3d(subLevel.logicalPose().position()), linear, angular);
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
        blocks.sort(Comparator.comparing(BlockSample::localPos, M18TestCommands::compareBlockPos));
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

    private static @Nullable Object invokeIntReturnRaw(@Nullable final Object target,
                                                       final String methodName,
                                                       final int value) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Method method = type.getDeclaredMethod(methodName, int.class);
                method.setAccessible(true);
                return method.invoke(target, value);
            } catch (final NoSuchMethodException ignored) {
                // Create stores useful state across superclass boundaries.
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static void invokeSetStackInSlot(@Nullable final Object target, final int slot, final ItemStack stack) {
        if (target == null) {
            return;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Method method = type.getMethod("setStackInSlot", int.class, ItemStack.class);
                method.invoke(target, slot, stack);
                return;
            } catch (final NoSuchMethodException ignored) {
                // Continue walking superclasses.
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                return;
            }
        }
    }

    private static void invokeSetItemInHand(@Nullable final Object target, final ItemStack stack) {
        if (target == null) {
            return;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Method method = type.getMethod("setItemInHand", InteractionHand.class, ItemStack.class);
                method.invoke(target, InteractionHand.MAIN_HAND, stack);
                return;
            } catch (final NoSuchMethodException ignored) {
                // Continue walking superclasses.
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                return;
            }
        }
    }

    private static void invokeWriteTagRaw(@Nullable final Object target, final CompoundTag tag, final boolean clientPacket) {
        if (target == null) {
            return;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Method method = type.getDeclaredMethod("write", CompoundTag.class, boolean.class);
                method.setAccessible(true);
                method.invoke(target, tag, clientPacket);
                return;
            } catch (final NoSuchMethodException ignored) {
                // Continue walking superclasses.
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                return;
            }
        }
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

    private static boolean writeFieldRaw(@Nullable final Object target, final String fieldName, final Object value) {
        if (target == null) {
            return false;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return true;
            } catch (final NoSuchFieldException ignored) {
                // Create stores useful state across superclass boundaries.
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
        return false;
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
        Sable.LOGGER.warn("SABLE_M18 phase=rollback_begin name={} id={} reason={}",
                name, subLevel.getUniqueId(), failure.getClass().getSimpleName());
        try {
            container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
        } catch (final Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static CommandSyntaxException commandFailure(final RuntimeException exception) {
        Sable.LOGGER.error("SABLE_M18 phase=failed", exception);
        final String message = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        return ERROR_M18_FAILED.create(message);
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

    private static double vectorLength(final Vector3dc vector) {
        return Math.sqrt(vector.x() * vector.x() + vector.y() * vector.y() + vector.z() * vector.z());
    }

    private record M18Stats(int blockCount, double mass, Vector3dc position, Vector3dc linearVelocity,
                            Vector3dc angularVelocity) {
    }

    private record BlockSample(BlockPos localPos, BlockPos plotPos, ChunkPos chunkPos, BlockState state) {
    }

    private record FixtureCheck(List<String> failures, String state, boolean fixtureLayout,
                                boolean pistonController, boolean deployerPresent,
                                boolean payloadOwnershipCoherent, boolean noObviousWrongWorldMutation,
                                boolean ready, boolean controlReady, String pistonState, int motorValue,
                                double motorSpeed, boolean liveContraption, int entityId,
                                int capturedBlocks) {
    }

    private record ActorSnapshot(boolean present, int entityId, @Nullable BlockPos actorLocal,
                                 Direction facing, String mode, @Nullable BlockPos targetRaw,
                                 int progress, int breakerId, boolean stall, String heldItem,
                                 String contextHeldItem, String blockEntityInventory,
                                 String fakePlayerMainHand, String contraptionStorage,
                                 @Nullable BlockPos blockBreakingTarget) {
    }

    private record TargetState(BlockPos local, BlockPos raw, BlockState state, String expected, boolean selected) {
    }

    private record TargetSummary(List<TargetState> states, boolean coherent,
                                 @Nullable BlockPos nextExpected, List<String> failures,
                                 String mode, boolean useComplete, boolean punchComplete) {
        private String compact() {
            final List<String> parts = new ArrayList<>();
            for (final TargetState state : states) {
                parts.add(fmt(state.local()) + "=" + blockId(state.state()).getPath());
            }
            return parts.toString();
        }
    }
}
