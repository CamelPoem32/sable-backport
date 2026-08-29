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
import net.minecraft.world.phys.AABB;
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

/** Diagnostic/runtime acceptance command harness for M14.1 Create Mechanical Piston contraptions. */
public final class M14TestCommands {
    private static final ResourceLocation CREATIVE_MOTOR_ID = new ResourceLocation("create", "creative_motor");
    private static final ResourceLocation SHAFT_ID = new ResourceLocation("create", "shaft");
    private static final ResourceLocation MECHANICAL_PISTON_ID = new ResourceLocation("create", "mechanical_piston");
    private static final ResourceLocation STICKY_MECHANICAL_PISTON_ID = new ResourceLocation("create", "sticky_mechanical_piston");
    private static final ResourceLocation PISTON_EXTENSION_POLE_ID = new ResourceLocation("create", "piston_extension_pole");
    private static final ResourceLocation RADIAL_CHASSIS_ID = new ResourceLocation("create", "radial_chassis");
    private static final String CREATE_CONTROL_CONTRAPTION_CLASS =
            "com.simibubi.create.content.contraptions.IControlContraption";
    private static final BlockState DEFAULT_PLATFORM_BLOCKSTATE = Blocks.STONE.defaultBlockState();
    private static final BlockPos MOTOR_LOCAL = new BlockPos(0, -2, 0);
    private static final BlockPos SHAFT_LOCAL = new BlockPos(0, -1, 0);
    private static final BlockPos PISTON_LOCAL = BlockPos.ZERO;
    private static final Direction PISTON_FACING = Direction.EAST;
    private static final Direction EXPECTED_POLE_DIRECTION = PISTON_FACING.getOpposite();
    private static final int PISTON_EXTENSION_POLES = 4;
    private static final int SPAWN_MOTOR_RPM = 0;
    private static final int DEFAULT_EXTEND_RPM = -32;
    private static final int DEFAULT_RETRACT_RPM = 32;
    private static final List<BlockPos> MOVED_STRUCTURE_BLOCKS = List.of(
            new BlockPos(1, 0, 0),
            new BlockPos(1, 1, 0));
    private static final DynamicCommandExceptionType ERROR_M14_FAILED = new DynamicCommandExceptionType(
            message -> Component.literal("SABLE_M14 command failed: " + message));

    private M14TestCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m14")
                .then(Commands.literal("list")
                        .executes(M14TestCommands::list))
                .then(Commands.literal("spawn_piston")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(M14TestCommands::spawnPiston)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::remove)))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::inspect)))
                .then(Commands.literal("validate")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::validate)))
                .then(Commands.literal("dump_layout")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::dumpLayout)))
                .then(Commands.literal("diagnose")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::diagnose)))
                .then(Commands.literal("pole_check")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::poleCheck)))
                .then(Commands.literal("attachment_check")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::attachmentCheck)))
                .then(Commands.literal("reset_fixture")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::resetFixture)))
                .then(Commands.literal("snapshot")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::snapshot)))
                .then(Commands.literal("captured")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::captured)))
                .then(Commands.literal("lifecycle")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::lifecycle)))
                .then(Commands.literal("cycle_check")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::cycleCheck)))
                .then(Commands.literal("endpoints")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::endpoints)))
                .then(Commands.literal("persistence")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::persistence)))
                .then(Commands.literal("extend")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::extend)))
                .then(Commands.literal("retract")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::retract)))
                .then(Commands.literal("toggle")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::toggle)))
                .then(Commands.literal("stop_piston")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::stopPiston)))
                .then(Commands.literal("set_motor_speed")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .then(Commands.argument("rpm", IntegerArgumentType.integer(-256, 256))
                                        .executes(M14TestCommands::setMotorSpeed))))
                .then(Commands.literal("reverse_motor")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::reverseMotor)))
                .then(Commands.literal("test_stationary")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::testStationary)))
                .then(Commands.literal("prepare_airborne")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::prepareAirborne)))
                .then(Commands.literal("test_translate_parent")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::testTranslateParent)))
                .then(Commands.literal("test_rotate_parent")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::testRotateParent)))
                .then(Commands.literal("test_combined_parent")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::testCombinedParent)))
                .then(Commands.literal("airborne_acceptance")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M14TestCommands::airborneAcceptance))));
    }

    private static int list(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(context);
        int count = 0;
        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            final List<PistonSnapshot> pistons = collectPistons(subLevel);
            if (pistons.isEmpty() && (subLevel.getName() == null || !subLevel.getName().startsWith("m14"))) {
                continue;
            }
            final PistonSnapshot piston = pistons.isEmpty() ? null : pistons.get(0);
            final FixtureCheck fixture = checkFixture(subLevel);
            send(context, "SABLE_M14_LIST id=" + subLevel.getUniqueId()
                    + " name=" + nameOrNone(subLevel)
                    + " position=" + formatVector(subLevel.logicalPose().position())
                    + " pistonLocal=" + formatBlockPos(piston == null ? null : piston.localPos())
                    + " state=" + (piston == null ? "missing" : piston.pistonStateName())
                    + " running=" + (piston != null && piston.running())
                    + " offset=" + fmt(piston == null ? Double.NaN : piston.offset())
                    + " speed=" + fmt(piston == null ? Double.NaN : piston.speed())
                    + " motorValue=" + fixture.motorValue()
                    + " contraptionEntityId=" + (piston == null ? -1 : piston.entityId())
                    + " capturedBlocks=" + (piston == null ? 0 : piston.capturedBlocks())
                    + " fixtureReady=" + fixture.ready());
            count++;
        }
        send(context, "SABLE_M14_LIST_DONE count=" + count
                + " targetByNameSelector=@e[name=<name>,limit=1]");
        return count;
    }

    private static int spawnPiston(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(context);
        final String name = StringArgumentType.getString(context, "name");
        ServerSubLevel subLevel = null;

        try {
            subLevel = createEmptySubLevel(context, container, name);
            final Map<BlockPos, BlockState> blocks = pistonFixtureBlocks();
            final List<SubLevelBlockEditHelper.BlockChange> changes = applyBlocks(subLevel, blocks);
            setFixtureMotorSpeed(subLevel, SPAWN_MOTOR_RPM);
            SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
            setFixtureMotorSpeed(subLevel, SPAWN_MOTOR_RPM);
            subLevel.updateLastPose();

            final M14Stats stats = inspectStats(subLevel);
            final String line = "SABLE_M14_SPAWN name=" + name
                    + " uuid=" + subLevel.getUniqueId()
                    + " blockCount=" + stats.blockCount()
                    + " mass=" + fmt(stats.mass())
                    + " motorLocal=" + formatBlockPos(MOTOR_LOCAL)
                    + " motorState=" + getLocalBlockState(subLevel, MOTOR_LOCAL)
                    + " shaftLocal=" + formatBlockPos(SHAFT_LOCAL)
                    + " shaftState=" + getLocalBlockState(subLevel, SHAFT_LOCAL)
                    + " pistonLocal=" + formatBlockPos(PISTON_LOCAL)
                    + " pistonState=" + getLocalBlockState(subLevel, PISTON_LOCAL)
                    + " pistonBlock=create:sticky_mechanical_piston"
                    + " extensionPolesLocal=" + formatBlockPosList(requiredPoleLocals())
                    + " expectedPoleSide=behind_opposite_facing"
                    + " staticHeadLocal=none_retracted_create_generates_head"
                    + " movedStructureLocal=" + MOVED_STRUCTURE_BLOCKS
                    + " initialMotorValue=" + SPAWN_MOTOR_RPM
                    + " extendMotorValue=" + DEFAULT_EXTEND_RPM
                    + " constructionOrdering=blocks_then_motor_stopped_then_finalize"
                    + " assemblyPath=normal_create_mechanical_piston";
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
        if (!isM14FixtureCandidate(subLevel)) {
            throw ERROR_M14_FAILED.create("Refusing to remove non-M14-looking sub-level " + subLevel.getUniqueId());
        }
        container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
        send(context, "SABLE_M14_REMOVE id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " result=removed_sublevel_and_owned_entities");
        return 1;
    }

    private static int inspect(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        sendInspect(context, subLevel);
        return 1;
    }

    private static int validate(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final List<String> failures = validateGeneric(subLevel);
        final FixtureCheck fixture = checkFixture(subLevel);
        failures.addAll(fixture.failures());
        final List<PistonSnapshot> pistons = collectPistons(subLevel);
        if (pistons.isEmpty()) {
            failures.add("piston_missing");
        }

        boolean anyAssembled = false;
        boolean containingSubLevelKnown = false;
        boolean hiddenPlotLeak = false;
        boolean finiteMotionState = false;
        String pistonState = "missing";
        int capturedBlocks = 0;
        final int plotContraptions = subLevel.getPlot().getContraptions().size();
        for (final PistonSnapshot piston : pistons) {
            anyAssembled |= piston.assembled();
            containingSubLevelKnown |= piston.containingSubLevelKnown();
            hiddenPlotLeak |= piston.rawEntityPositionLooksLikePlot() && !piston.containingSubLevelKnown();
            finiteMotionState |= Double.isFinite(piston.offset())
                    || Double.isFinite(piston.movementSpeed())
                    || finiteVec3(piston.motionVector())
                    || finiteVec3(piston.rawEntityPosition());
            capturedBlocks = Math.max(capturedBlocks, piston.capturedBlocks());
            pistonState = piston.pistonStateName();
        }

        if (anyAssembled) {
            requireInvariant(capturedBlocks > 0, "contraption_blocks_missing", failures);
            requireInvariant(plotContraptions > 0, "contraption_not_registered_in_plot", failures);
            requireInvariant(!hiddenPlotLeak, "contraption_hidden_plot_uncontained", failures);
            requireInvariant(containingSubLevelKnown, "contraption_containing_sublevel_unknown", failures);
            requireInvariant(finiteMotionState, "linear_motion_state_not_observable", failures);
        }

        final String validationState = failures.isEmpty()
                ? classifyState(anyAssembled, pistonState, pistons)
                : "FAIL";
        final String truthfulState = !fixture.ready() ? "FIXTURE_INVALID" : validationState;
        final String controller = !anyAssembled ? "N/A"
                : containingSubLevelKnown && !hiddenPlotLeak ? "PASS" : "FAIL";
        final String serverLinearMotion = !anyAssembled ? "N/A" : finiteMotionState ? "PASS" : "FAIL";
        final String line = "SABLE_M14_VALIDATE state=" + truthfulState
                + " status=" + (failures.isEmpty() ? "PASS" : "FAIL")
                + " FIXTURE_LAYOUT=" + (fixture.layoutValid() ? "PASS" : "FAIL")
                + " KINETIC_DRIVE=" + (fixture.kineticDriveReady() ? "PASS" : "FAIL")
                + " EXTENSION_CHAIN=" + (fixture.extensionChainValid() ? "PASS" : "FAIL")
                + " SERVER_ASSEMBLY=" + (failures.isEmpty() ? "PASS" : "FAIL")
                + " CONTROLLER=" + controller
                + " SERVER_LINEAR_MOTION=" + serverLinearMotion
                + " CLIENT_RENDER=UNVERIFIED"
                + " CLIENT_COLLISION=UNVERIFIED"
                + " CLIENT_TARGETING=UNVERIFIED"
                + " PERSISTENCE=UNVERIFIED"
                + " id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " pistonCount=" + pistons.size()
                + " pistonState=" + pistonState
                + " assembled=" + anyAssembled
                + " poleCount=" + fixture.poleCount()
                + " expectedPoleDirection=" + EXPECTED_POLE_DIRECTION
                + " motorValue=" + fixture.motorValue()
                + " motorSpeed=" + fmt(fixture.motorSpeed())
                + " pistonKineticSpeed=" + fmt(fixture.pistonSpeed())
                + " capturedBlocks=" + capturedBlocks
                + " plotContraptions=" + plotContraptions
                + " hiddenPlotLeak=" + hiddenPlotLeak
                + " failures=" + failures;
        send(context, line);
        Sable.LOGGER.info(line);
        return failures.isEmpty() ? 1 : 0;
    }

    private static int dumpLayout(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        for (final BlockPos local : diagnosticLayoutLocals()) {
            final BlockState state = getLocalBlockState(subLevel, local);
            final BlockPos plot = SubLevelBlockEditHelper.localOffsetToPlotBlock(subLevel, local);
            final String role = layoutRole(local);
            final String line = "SABLE_M14_LAYOUT id=" + subLevel.getUniqueId()
                    + " localPos=" + formatBlockPos(local)
                    + " plotPos=" + formatBlockPos(plot)
                    + " blockId=" + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                    + " state=" + state
                    + " role=" + role
                    + " expected=" + expectedLayoutDescription(local)
                    + " valid=" + layoutPositionValid(subLevel, local, state);
            send(context, line);
            Sable.LOGGER.info(line);
        }
        return 1;
    }

    private static int diagnose(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck fixture = checkFixture(subLevel);
        final PistonSnapshot piston = collectPistons(subLevel).stream().findFirst().orElse(null);
        final String line = "SABLE_M14_DIAGNOSE id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " FIXTURE_LAYOUT=" + (fixture.layoutValid() ? "PASS" : "FAIL")
                + " PISTON_STATE=" + fixture.pistonStateName()
                + " EXTENSION_CHAIN=" + (fixture.extensionChainValid() ? "PASS" : "FAIL")
                + " KINETICS=" + (fixture.kineticDriveReady() ? "PASS" : "FAIL")
                + " ATTACHED_STRUCTURE=" + (fixture.attachedStructurePresent() ? "PASS" : "FAIL")
                + " TRAVEL_PATH=" + (fixture.travelPathClear() ? "PASS" : "FAIL_OR_OCCUPIED_BY_EXTENDED_STATE")
                + " CONTRAPTION=" + (piston != null && piston.movedContraption() != null ? "PRESENT" : "NONE")
                + " CONTROLLER=" + (piston == null || piston.movedContraption() == null ? "N/A"
                : piston.sableControllerIsControl() ? "PASS" : "FAIL")
                + " PISTON_STATIC_MODEL=UNVERIFIED"
                + " PISTON_BER=UNVERIFIED"
                + " PISTON_VISUALIZATION=UNVERIFIED"
                + " RENDER=UNVERIFIED"
                + " COLLISION=UNVERIFIED"
                + " failures=" + fixture.failures();
        send(context, line);
        Sable.LOGGER.info(line);
        return fixture.ready() ? 1 : 0;
    }

    private static int poleCheck(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        for (final BlockPos pole : poleDiagnosticLocals()) {
            final BlockState state = getLocalBlockState(subLevel, pole);
            final String line = "SABLE_M14_POLE id=" + subLevel.getUniqueId()
                    + " localPos=" + formatBlockPos(pole)
                    + " plotPos=" + formatBlockPos(SubLevelBlockEditHelper.localOffsetToPlotBlock(subLevel, pole))
                    + " blockId=" + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                    + " state=" + state
                    + " facing=" + statePropertyName(state, "facing", "missing")
                    + " actualAxis=" + poleAxis(state)
                    + " expectedFacing=" + PISTON_FACING.getName()
                    + " expectedAxis=x"
                    + " createRecognized=" + isCreatePoleForFixture(state);
            send(context, line);
            Sable.LOGGER.info(line);
        }
        final FixtureCheck fixture = checkFixture(subLevel);
        send(context, "SABLE_M14_POLE_SUMMARY id=" + subLevel.getUniqueId()
                + " pistonState=" + fixture.pistonStateName()
                + " retractedPoleCount=" + fixture.retractedPoleCount()
                + " extendedPoleCount=" + fixture.extendedPoleCount()
                + " selectedPoleCount=" + fixture.poleCount()
                + " extensionChainValid=" + fixture.extensionChainValid());
        return fixture.extensionChainValid() ? 1 : 0;
    }

    private static int attachmentCheck(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck fixture = checkFixture(subLevel);
        for (final BlockPos local : attachmentDiagnosticLocals()) {
            final BlockState state = getLocalBlockState(subLevel, local);
            final String line = "SABLE_M14_ATTACHMENT id=" + subLevel.getUniqueId()
                    + " localPos=" + formatBlockPos(local)
                    + " plotPos=" + formatBlockPos(SubLevelBlockEditHelper.localOffsetToPlotBlock(subLevel, local))
                    + " blockId=" + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                    + " state=" + state
                    + " role=" + layoutRole(local)
                    + " chassisStickyForMarker=" + chassisCanStickToUpMarker(state)
                    + " present=" + !state.isAir();
            send(context, line);
            Sable.LOGGER.info(line);
        }
        final PistonSnapshot piston = collectPistons(subLevel).stream().findFirst().orElse(null);
        send(context, "SABLE_M14_ATTACHMENT_SUMMARY id=" + subLevel.getUniqueId()
                + " attachedStructurePresent=" + fixture.attachedStructurePresent()
                + " expectedStaticPositions=" + fixture.expectedAttachedPositions()
                + " capturedBlocks=" + (piston == null ? 0 : piston.capturedBlocks())
                + " capturedPositions=" + (piston == null ? "[]" : capturedBlockPositions(piston.contraption())));
        return fixture.attachedStructurePresent() ? 1 : 0;
    }

    private static int resetFixture(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final PistonSnapshot piston = collectPistons(subLevel).stream().findFirst().orElse(null);
        if (piston != null && (piston.movedContraption() != null || piston.running() || piston.assembleNextTick())) {
            final String line = "SABLE_M14_RESET id=" + subLevel.getUniqueId()
                    + " result=REJECTED_LIVE_CONTRAPTION"
                    + " running=" + piston.running()
                    + " assembleNextTick=" + piston.assembleNextTick()
                    + " movedContraptionPresent=" + (piston.movedContraption() != null);
            send(context, line);
            Sable.LOGGER.info(line);
            return 0;
        }

        trySetFixtureMotorSpeed(subLevel, SPAWN_MOTOR_RPM);
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (final BlockPos local : resetClearLocals()) {
            blocks.put(local, Blocks.AIR.defaultBlockState());
        }
        blocks.putAll(pistonFixtureBlocks());
        final List<SubLevelBlockEditHelper.BlockChange> changes = applyBlocks(subLevel, blocks);
        setFixtureMotorSpeed(subLevel, SPAWN_MOTOR_RPM);
        SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
        setFixtureMotorSpeed(subLevel, SPAWN_MOTOR_RPM);
        final FixtureCheck fixture = checkFixture(subLevel);
        final String line = "SABLE_M14_RESET id=" + subLevel.getUniqueId()
                + " result=APPLIED_STATIC_CANONICAL_FIXTURE"
                + " pistonState=" + fixture.pistonStateName()
                + " motorValue=" + fixture.motorValue()
                + " ready=" + fixture.ready()
                + " failures=" + fixture.failures();
        send(context, line);
        Sable.LOGGER.info(line);
        return fixture.ready() ? 1 : 0;
    }

    private static int snapshot(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck fixture = checkFixture(subLevel);
        final PistonSnapshot piston = collectPistons(subLevel).stream().findFirst().orElse(null);
        final String line = "SABLE_M14_SNAPSHOT id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " pistonState=" + (piston == null ? fixture.pistonStateName() : piston.pistonStateName())
                + " offset=" + fmt(piston == null ? Double.NaN : piston.offset())
                + " running=" + (piston != null && piston.running())
                + " motorSpeed=" + fmt(fixture.motorSpeed())
                + " motorValue=" + fixture.motorValue()
                + " poleCount=" + fixture.poleCount()
                + " contraptionId=" + (piston == null ? -1 : piston.entityId())
                + " capturedBlocks=" + (piston == null ? 0 : piston.capturedBlocks())
                + " controllerResolved=" + (piston != null && piston.sableControllerIsControl())
                + " createAnchorRawPlot=" + formatBlockPos(piston == null ? null : piston.createAnchorPlot())
                + " createAnchorSubLevelLocal=" + formatBlockPos(piston == null ? null : piston.createAnchorLocal())
                + " createAnchorVisibleWorld=" + formatVector(piston == null ? null : piston.expectedVisibleAnchor())
                + " expectedVisibleAnchor=" + formatVector(piston == null ? null : piston.expectedVisibleAnchor())
                + " expectedVisibleMovementAxis=" + formatVector(piston == null ? null : piston.visibleMotionAxis())
                + " actualVisibleMovementAxis=UNVERIFIED"
                + " axisAlignmentDot=UNVERIFIED"
                + " actualVisibleAnchor=UNVERIFIED"
                + " positionError=UNVERIFIED"
                + " pose=" + formatVector(subLevel.logicalPose().position())
                + " orientation=" + formatQuaternion(subLevel.logicalPose().orientation())
                + " linearVelocity=" + formatVector(inspectStats(subLevel).linearVelocity())
                + " angularVelocity=" + formatVector(inspectStats(subLevel).angularVelocity());
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int captured(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final PistonSnapshot piston = collectPistons(subLevel).stream().findFirst().orElse(null);
        if (piston == null || piston.contraption() == null) {
            send(context, "SABLE_M14_CAPTURED id=" + subLevel.getUniqueId() + " result=NO_CONTRAPTION");
            return 0;
        }
        for (final String entry : capturedBlockEntries(piston.contraption())) {
            final String line = "SABLE_M14_CAPTURED id=" + subLevel.getUniqueId()
                    + " entityId=" + piston.entityId() + " " + entry;
            send(context, line);
            Sable.LOGGER.info(line);
        }
        return 1;
    }

    private static int lifecycle(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck fixture = checkFixture(subLevel);
        final PistonSnapshot piston = collectPistons(subLevel).stream().findFirst().orElse(null);
        final boolean movedPresent = piston != null && piston.movedContraption() != null;
        final boolean staticFrontPresent = attachmentLocalsForState(fixture.pistonStateName()).stream()
                .anyMatch(local -> !getLocalBlockState(subLevel, local).isAir());
        final String duplicateState = movedPresent && staticFrontPresent ? "POSSIBLE_DUPLICATE" : "NONE_OBSERVED";
        final String line = "SABLE_M14_LIFECYCLE id=" + subLevel.getUniqueId()
                + " pistonState=" + fixture.pistonStateName()
                + " assembledOrMoving=" + (piston != null && piston.assembled())
                + " running=" + (piston != null && piston.running())
                + " contraptionId=" + (piston == null ? -1 : piston.entityId())
                + " capturedCount=" + (piston == null ? 0 : piston.capturedBlocks())
                + " staticFrontBlocksPresent=" + staticFrontPresent
                + " duplicateDetection=" + duplicateState
                + " missingStaticAttachment=" + !fixture.attachedStructurePresent()
                + " failures=" + fixture.failures();
        send(context, line);
        Sable.LOGGER.info(line);
        return duplicateState.equals("NONE_OBSERVED") ? 1 : 0;
    }

    private static int cycleCheck(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck fixture = checkFixture(subLevel);
        final PistonSnapshot piston = collectPistons(subLevel).stream().findFirst().orElse(null);
        final boolean live = piston != null && piston.movedContraption() != null;
        final boolean retractedAttachmentPresent = hasAllBlocks(subLevel, MOVED_STRUCTURE_BLOCKS);
        final boolean extendedAttachmentPresent = hasAllBlocks(subLevel, attachmentLocalsForState("extended"));
        final boolean duplicates = live && (retractedAttachmentPresent || extendedAttachmentPresent);
        final int captured = piston == null ? 0 : piston.capturedBlocks();
        final boolean missing = !live && !(retractedAttachmentPresent || extendedAttachmentPresent);
        final String cycleState;
        if (live && (piston.running() || "moving".equals(piston.pistonStateName()))) {
            cycleState = piston.movementSpeed() < 0.0 ? "MOVING_EXTEND" : "MOVING_RETRACT";
        } else if (live && "extended".equals(fixture.pistonStateName())) {
            cycleState = "EXTENDED_READY";
        } else if (live && "retracted".equals(fixture.pistonStateName())) {
            cycleState = "RETRACTED_READY";
        } else if ("retracted".equals(fixture.pistonStateName()) && retractedAttachmentPresent) {
            cycleState = "RETRACTED_READY";
        } else if ("extended".equals(fixture.pistonStateName()) && extendedAttachmentPresent) {
            cycleState = "EXTENDED_READY";
        } else {
            cycleState = "FAIL";
        }
        final String line = "SABLE_M14_CYCLE id=" + subLevel.getUniqueId()
                + " cycleState=" + cycleState
                + " pistonState=" + fixture.pistonStateName()
                + " offset=" + fmt(piston == null ? Double.NaN : piston.offset())
                + " motorSpeed=" + fmt(fixture.motorSpeed())
                + " liveContraptionId=" + (piston == null ? -1 : piston.entityId())
                + " capturedBlocks=" + captured
                + " retractedAttachmentPresent=" + retractedAttachmentPresent
                + " extendedAttachmentPresent=" + extendedAttachmentPresent
                + " duplicates=" + duplicates
                + " missingAttachment=" + missing
                + " expectedNextAction=" + expectedNextCycleAction(cycleState)
                + " failures=" + fixture.failures();
        send(context, line);
        Sable.LOGGER.info(line);
        return "FAIL".equals(cycleState) || duplicates || missing ? 0 : 1;
    }

    private static int endpoints(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck fixture = checkFixture(subLevel);
        final List<BlockPos> positions = List.of(
                new BlockPos(1, 0, 0), new BlockPos(1, 1, 0),
                new BlockPos(5, 0, 0), new BlockPos(5, 1, 0));
        final boolean retracted = "retracted".equals(fixture.pistonStateName());
        for (final BlockPos local : positions) {
            final boolean expected = retracted ? MOVED_STRUCTURE_BLOCKS.contains(local)
                    : "extended".equals(fixture.pistonStateName())
                    && attachmentLocalsForState("extended").contains(local);
            final BlockState state = getLocalBlockState(subLevel, local);
            final String line = "SABLE_M14_ENDPOINT id=" + subLevel.getUniqueId()
                    + " localPos=" + formatBlockPos(local)
                    + " plotPos=" + formatBlockPos(SubLevelBlockEditHelper.localOffsetToPlotBlock(subLevel, local))
                    + " blockId=" + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                    + " state=" + state
                    + " present=" + !state.isAir()
                    + " expectedForState=" + expected;
            send(context, line);
            Sable.LOGGER.info(line);
        }
        return 1;
    }

    private static int persistence(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final M14Stats stats = inspectStats(subLevel);
        final FixtureCheck fixture = checkFixture(subLevel);
        final PistonSnapshot piston = collectPistons(subLevel).stream().findFirst().orElse(null);
        final String line = "SABLE_M14_PERSISTENCE id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " blockCount=" + stats.blockCount()
                + " mass=" + fmt(stats.mass())
                + " localBounds=" + formatLocalBounds(subLevel, stats.plotBounds())
                + " plotBounds=" + formatBounds(stats.plotBounds())
                + " pistonState=" + (piston == null ? "missing" : piston.pistonStateName())
                + " running=" + (piston != null && piston.running())
                + " offset=" + fmt(piston == null ? Double.NaN : piston.offset())
                + " movedContraptionPresent=" + (piston != null && piston.movedContraption() != null)
                + " capturedBlocks=" + (piston == null ? 0 : piston.capturedBlocks())
                + " motorValue=" + fixture.motorValue()
                + " motorSpeed=" + fmt(fixture.motorSpeed())
                + " posePosition=" + formatVector(subLevel.logicalPose().position())
                + " poseOrientation=" + formatQuaternion(subLevel.logicalPose().orientation())
                + " persistenceRuntimeAfterReload=UNVERIFIED";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int extend(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setMotorSpeed(context, -Math.abs(DEFAULT_EXTEND_RPM), "extend");
    }

    private static int retract(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setMotorSpeed(context, Math.abs(DEFAULT_RETRACT_RPM), "retract");
    }

    private static int toggle(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final PistonSnapshot piston = collectPistons(subLevel).stream().findFirst().orElse(null);
        final double offset = piston == null ? 0.0 : piston.offset();
        final boolean retract = piston != null && ("extended".equals(piston.pistonStateName())
                || offset > Math.max(1.0, piston.extensionRange() / 2.0));
        return setMotorSpeed(context, retract ? Math.abs(DEFAULT_EXTEND_RPM) : -Math.abs(DEFAULT_EXTEND_RPM),
                retract ? "toggle_retract" : "toggle_extend");
    }

    private static int stopPiston(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setMotorSpeed(context, 0, "stop");
    }

    private static int setMotorSpeed(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setMotorSpeed(context, IntegerArgumentType.getInteger(context, "rpm"), "set_motor_speed");
    }

    private static int reverseMotor(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final int current = readFixtureMotorValue(subLevel);
        final int next = current == Integer.MIN_VALUE ? DEFAULT_EXTEND_RPM : -current;
        return setMotorSpeed(context, next, "reverse_motor");
    }

    private static int testStationary(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setMotorSpeed(context, -Math.abs(DEFAULT_EXTEND_RPM), "test_stationary_extend");
    }

    private static int prepareAirborne(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final PistonSnapshot piston = collectPistons(subLevel).stream().findFirst().orElse(null);
        if (piston != null && (piston.movedContraption() != null || piston.running() || piston.assembleNextTick())) {
            final String line = "SABLE_M14_AIRBORNE id=" + subLevel.getUniqueId()
                    + " result=REJECTED_LIVE_CONTRAPTION"
                    + " running=" + piston.running()
                    + " assembleNextTick=" + piston.assembleNextTick()
                    + " movedContraptionPresent=" + (piston.movedContraption() != null);
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
        final String line = "SABLE_M14_AIRBORNE id=" + subLevel.getUniqueId()
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
        final int prepared = prepareAirborne(context);
        if (prepared == 0) {
            return 0;
        }
        setBodyVelocity(context, "airborne_acceptance_combined_parent", new Vector3d(0.0, 0.6, 0.0),
                new Vector3d(0.0, Math.toRadians(12.0), 0.0));
        final int extended = setMotorSpeed(context, -Math.abs(DEFAULT_EXTEND_RPM), "airborne_acceptance_extend");
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final String line = "SABLE_M14_AIRBORNE_ACCEPTANCE id=" + subLevel.getUniqueId()
                + " result=" + (extended > 0 ? "APPLIED" : "REJECTED")
                + " preparedAirborne=true"
                + " bodyVelocity=combined_parent"
                + " pistonControl=normal_create_motor_speed"
                + " gravityStillActive=true"
                + " anchored=false"
                + " manualContraptionMotion=false";
        send(context, line);
        Sable.LOGGER.info(line);
        return extended > 0 ? 1 : 0;
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
        final String line = "SABLE_M14_TEST_PRESET target=" + subLevel.getUniqueId()
                + " preset=" + preset
                + " result=APPLIED_M10_PHYSICS_VELOCITY"
                + " requestedLinear=" + formatVector(linearVelocity)
                + " requestedAngularRadS=" + formatVector(angularVelocityRad)
                + " requestedAngularDegS=" + formatVectorDeg(angularVelocityRad)
                + " actualLinear=" + formatVector(linearAfterSet)
                + " actualAngularRadS=" + formatVector(angularAfterSet)
                + " anchored=false"
                + " next=/sable m14 extend <target>";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int setMotorSpeed(final CommandContext<CommandSourceStack> context, final int rpm,
                                     final String action) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck before = checkFixture(subLevel);
        final String previousPistonState = before.pistonStateName();
        if (isEndpointNoOp(action, rpm, previousPistonState)) {
            final String noopLine = "SABLE_M14_CONTROL action=" + action
                    + " id=" + subLevel.getUniqueId()
                    + " name=" + nameOrNone(subLevel)
                    + " requestedMotorValue=" + rpm
                    + " previousPistonState=" + previousPistonState
                    + " previousMotorValue=" + before.motorValue()
                    + " result=" + (rpm < 0 ? "NOOP_ALREADY_EXTENDED" : "NOOP_ALREADY_RETRACTED")
                    + " fixtureValid=" + before.ready()
                    + " commandApplied=false"
                    + " failures=" + before.failures();
            send(context, noopLine);
            Sable.LOGGER.info(noopLine);
            return 0;
        }
        final boolean recoveryRetract = rpm > 0 && "extended".equals(previousPistonState)
                && before.basicControlReady();
        if (!before.controlReady() && rpm != SPAWN_MOTOR_RPM && !recoveryRetract) {
            final String rejectedLine = "SABLE_M14_CONTROL action=" + action
                    + " id=" + subLevel.getUniqueId()
                    + " name=" + nameOrNone(subLevel)
                    + " requestedMotorValue=" + rpm
                    + " previousPistonState=" + previousPistonState
                    + " previousMotorValue=" + before.motorValue()
                    + " result=REJECTED_FIXTURE_INVALID"
                    + " fixtureValid=" + before.ready()
                    + " commandApplied=false"
                    + " failures=" + before.failures();
            send(context, rejectedLine);
            Sable.LOGGER.info(rejectedLine);
            return 0;
        }
        setFixtureMotorSpeed(subLevel, rpm);
        final FixtureCheck fixture = checkFixture(subLevel);
        final String line = "SABLE_M14_CONTROL action=" + action
                + " id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " requestedMotorValue=" + rpm
                + " previousPistonState=" + previousPistonState
                + " previousMotorValue=" + before.motorValue()
                + " motorValue=" + fixture.motorValue()
                + " motorSpeed=" + fmt(fixture.motorSpeed())
                + " pistonKineticSpeed=" + fmt(fixture.pistonSpeed())
                + " movementDirection=" + (rpm < 0 ? "extend_for_east_fixture" : rpm > 0 ? "retract_for_east_fixture" : "stop")
                + " fixtureValid=" + fixture.ready()
                + " result=APPLIED"
                + " commandApplied=true"
                + " semantics=Create_ScrollValueBehaviour_setValue";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static Map<BlockPos, BlockState> pistonFixtureBlocks() {
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        blocks.put(MOTOR_LOCAL, setProperty(requireBlockState(CREATIVE_MOTOR_ID), "facing", "up"));
        blocks.put(SHAFT_LOCAL, setProperty(requireBlockState(SHAFT_ID), "axis", "y"));
        blocks.put(PISTON_LOCAL, setProperty(
                setProperty(
                        setProperty(requireBlockState(STICKY_MECHANICAL_PISTON_ID), "facing", PISTON_FACING.getName()),
                        "axis_along_first", "true"),
                "state", "retracted"));
        for (final BlockPos pole : requiredPoleLocals()) {
            blocks.put(pole, setProperty(requireBlockState(PISTON_EXTENSION_POLE_ID), "facing", PISTON_FACING.getName()));
        }
        blocks.put(new BlockPos(1, 0, 0), setProperty(
                setProperty(requireBlockState(RADIAL_CHASSIS_ID), "axis", "x"),
                "sticky_north", "true"));
        blocks.put(new BlockPos(1, 1, 0), DEFAULT_PLATFORM_BLOCKSTATE);
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
        final M14Stats stats = inspectStats(subLevel);
        final String line = "SABLE_M14_INSPECT id=" + subLevel.getUniqueId()
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
                + " pistonCount=" + stats.pistonCount()
                + " plotContraptionCount=" + stats.plotContraptionCount();
        send(context, line);
        Sable.LOGGER.info(line);

        final FixtureCheck fixture = checkFixture(subLevel);
        final String fixtureLine = "SABLE_M14_FIXTURE id=" + subLevel.getUniqueId()
                + " layoutValid=" + fixture.layoutValid()
                + " extensionChainValid=" + fixture.extensionChainValid()
                + " kineticDriveReady=" + fixture.kineticDriveReady()
                + " ready=" + fixture.ready()
                    + " pistonLocal=" + formatBlockPos(PISTON_LOCAL)
                    + " pistonBlock=create:sticky_mechanical_piston"
                + " pistonFacing=" + PISTON_FACING
                + " pistonAxis=y_from_axis_along_first_true"
                + " pistonState=" + fixture.pistonStateName()
                + " expectedPoleDirection=" + EXPECTED_POLE_DIRECTION
                + " poleCount=" + fixture.poleCount()
                + " retractedPoleCount=" + fixture.retractedPoleCount()
                + " extendedPoleCount=" + fixture.extendedPoleCount()
                + " requiredPoles=" + formatBlockPosList(requiredPoleLocals())
                + " extendedPoleLocals=" + formatBlockPosList(extendedPoleLocals())
                + " frontHeadPresent=" + fixture.frontHeadPresent()
                + " retractedHeadSemantics=Create_generates_head_during_assembly"
                + " motorLocal=" + formatBlockPos(MOTOR_LOCAL)
                + " motorFacing=up"
                + " motorValue=" + fixture.motorValue()
                + " motorSpeed=" + fmt(fixture.motorSpeed())
                + " shaftLocal=" + formatBlockPos(SHAFT_LOCAL)
                + " shaftAxis=y"
                + " pistonKineticSpeed=" + fmt(fixture.pistonSpeed())
                + " pistonMovementSpeed=" + fmt(fixture.pistonMovementSpeed())
                + " kineticNetworkPresent=" + fixture.kineticNetworkPresent()
                + " attachedStructurePresent=" + fixture.attachedStructurePresent()
                + " expectedAttachedPositions=" + fixture.expectedAttachedPositions()
                + " travelPathClear=" + fixture.travelPathClear()
                + " failures=" + fixture.failures();
        send(context, fixtureLine);
        Sable.LOGGER.info(fixtureLine);

        for (final PistonSnapshot piston : collectPistons(subLevel)) {
            final String pistonLine = "SABLE_M14_PISTON id=" + subLevel.getUniqueId()
                    + " local=" + formatBlockPos(piston.localPos())
                    + " plot=" + formatBlockPos(piston.plotPos())
                    + " state=" + piston.state()
                    + " beClass=" + piston.blockEntity().getClass().getName()
                    + " removed=" + piston.blockEntity().isRemoved()
                    + " facing=" + piston.facing()
                    + " localMotionAxis=" + formatVector(piston.localMotionAxis())
                    + " visibleMotionAxis=" + formatVector(piston.visibleMotionAxis())
                    + " createLocalMovementAxis=" + formatVector(piston.localMotionAxis())
                    + " sableRotatedVisibleMovementAxis=" + formatVector(piston.visibleMotionAxis())
                    + " pistonState=" + piston.pistonStateName()
                    + " speed=" + fmt(piston.speed())
                    + " theoreticalSpeed=" + fmt(piston.theoreticalSpeed())
                    + " movementSpeed=" + fmt(piston.movementSpeed())
                    + " extensionLength=" + piston.extensionLength()
                    + " extensionRange=" + piston.extensionRange()
                    + " initialOffset=" + piston.initialOffset()
                    + " offset=" + fmt(piston.offset())
                    + " interpolatedOffset0=" + fmt(piston.interpolatedOffset0())
                    + " running=" + piston.running()
                    + " assembleNextTick=" + piston.assembleNextTick()
                    + " needsContraption=" + piston.needsContraption()
                    + " waitingForSpeedChange=" + piston.waitingForSpeedChange()
                    + " clientOffsetDiff=" + fmt(piston.clientOffsetDiff())
                    + " motionVector=" + formatVec3(piston.motionVector())
                    + " movedContraptionPresent=" + (piston.movedContraption() != null)
                    + " movedContraptionClass=" + className(piston.movedContraption())
                    + " movedContraptionEntityId=" + piston.entityId()
                    + " movedContraptionLevel=" + piston.entityLevelClass()
                    + " rawEntityPosition=" + formatVec3(piston.rawEntityPosition())
                    + " previousRawEntityPosition=" + formatVec3(piston.previousRawEntityPosition())
                    + " contraptionAnchorLocal=" + formatBlockPos(piston.createAnchorLocal())
                    + " contraptionAnchorPlot=" + formatBlockPos(piston.createAnchorPlot())
                    + " expectedVisibleAnchor=" + formatVector(piston.expectedVisibleAnchor())
                    + " actualVisibleAnchor=UNVERIFIED"
                    + " anchorError=UNVERIFIED"
                    + " axisAlignmentDot=UNVERIFIED"
                    + " entityControllerPos=" + formatBlockPos(piston.entityControllerPos())
                    + " normalControllerLoaded=" + piston.normalControllerLoaded()
                    + " normalControllerClass=" + className(piston.normalController())
                    + " normalControllerIsControl=" + piston.normalControllerIsControl()
                    + " sableControllerClass=" + className(piston.sableController())
                    + " sableControllerIsControl=" + piston.sableControllerIsControl()
                    + " rawEntityPositionLooksLikePlot=" + piston.rawEntityPositionLooksLikePlot()
                    + " containingSubLevelKnown=" + piston.containingSubLevelKnown()
                    + " contraptionClass=" + className(piston.contraption())
                    + " capturedBlocks=" + piston.capturedBlocks()
                    + " contraptionLocalBounds=" + piston.contraptionLocalBounds()
                    + " clientEntityPresent=UNVERIFIED"
                    + " rendererFound=UNVERIFIED"
                    + " renderReady=UNVERIFIED"
                    + " visibleExpectedAabb=UNVERIFIED"
                    + " assembled=" + piston.assembled();
            send(context, pistonLine);
            Sable.LOGGER.info(pistonLine);
        }
    }

    private static List<String> validateGeneric(final ServerSubLevel subLevel) {
        final List<String> failures = new ObjectArrayList<>();
        final M14Stats stats = inspectStats(subLevel);
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

    private static M14Stats inspectStats(final ServerSubLevel subLevel) {
        final List<BlockSample> blocks = scanBlocks(subLevel);
        final MassData mass = subLevel.getMassTracker();
        final MassData selfMass = subLevel.getSelfMassTracker();
        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(subLevel.getLevel());
        final RigidBodyHandle handle = physicsSystem == null ? null : physicsSystem.getPhysicsHandle(subLevel);
        return new M14Stats(
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
                collectPistons(subLevel).size(),
                subLevel.getPlot().getContraptions().size());
    }

    private static FixtureCheck checkFixture(final ServerSubLevel subLevel) {
        final List<String> failures = new ObjectArrayList<>();
        final BlockState pistonState = getLocalBlockState(subLevel, PISTON_LOCAL);
        final BlockState motorState = getLocalBlockState(subLevel, MOTOR_LOCAL);
        final BlockState shaftState = getLocalBlockState(subLevel, SHAFT_LOCAL);
        final String pistonStateName = statePropertyName(pistonState, "state", "missing");
        final boolean retracted = "retracted".equals(pistonStateName);
        final boolean moving = "moving".equals(pistonStateName);
        final boolean extended = "extended".equals(pistonStateName);

        requireInvariant(isMechanicalPiston(pistonState), "mechanical_piston_missing_at_local_"
                + formatBlockPos(PISTON_LOCAL), failures);
        requireInvariant(blockIdMatches(pistonState, STICKY_MECHANICAL_PISTON_ID),
                "sticky_mechanical_piston_required_for_reverse_cycle", failures);
        requireInvariant(propertyMatches(pistonState, "facing", PISTON_FACING.getName()),
                "piston_facing_not_" + PISTON_FACING.getName(), failures);
        requireInvariant(propertyMatches(pistonState, "axis_along_first", "true"),
                "piston_axis_along_first_not_true_for_y_axis_drive", failures);
        requireInvariant(retracted || moving || extended,
                "piston_state_unreadable", failures);

        requireInvariant(blockIdMatches(motorState, CREATIVE_MOTOR_ID), "creative_motor_missing_at_local_"
                + formatBlockPos(MOTOR_LOCAL), failures);
        requireInvariant(propertyMatches(motorState, "facing", "up"), "motor_facing_not_up", failures);
        requireInvariant(blockIdMatches(shaftState, SHAFT_ID), "shaft_missing_at_local_"
                + formatBlockPos(SHAFT_LOCAL), failures);
        requireInvariant(propertyMatches(shaftState, "axis", "y"), "shaft_axis_not_y", failures);

        int retractedPoleCount = 0;
        for (final BlockPos pole : requiredPoleLocals()) {
            final BlockState poleState = getLocalBlockState(subLevel, pole);
            if (isCreatePoleForFixture(poleState)) {
                retractedPoleCount++;
            } else if (retracted) {
                failures.add("extension_pole_missing_or_wrong_axis_at_local_" + formatBlockPos(pole));
            }
        }

        int extendedPoleCount = 0;
        for (final BlockPos pole : extendedPoleLocals()) {
            if (isCreatePoleForFixture(getLocalBlockState(subLevel, pole))) {
                extendedPoleCount++;
            }
        }

        final BlockState frontState = getLocalBlockState(subLevel, PISTON_LOCAL.relative(PISTON_FACING));
        final boolean frontHeadPresent = isPistonHead(frontState);
        if (retracted) {
            requireInvariant(!frontHeadPresent, "static_piston_head_present_in_retracted_front", failures);
        }

        final PistonSnapshot livePiston = collectPistons(subLevel).stream().findFirst().orElse(null);
        final boolean liveContraption = livePiston != null && livePiston.movedContraption() != null;
        boolean attachedStructurePresent = liveContraption
                ? livePiston.capturedBlocks() >= MOVED_STRUCTURE_BLOCKS.size()
                : true;
        final List<BlockPos> expectedAttachedPositions = attachmentLocalsForState(pistonStateName);
        if (!liveContraption && !moving) {
            for (final BlockPos local : expectedAttachedPositions) {
                if (getLocalBlockState(subLevel, local).isAir()) {
                    attachedStructurePresent = false;
                    failures.add("attached_structure_missing_at_local_" + formatBlockPos(local));
                }
            }
        }
        final BlockPos chassisLocal = expectedAttachedPositions.isEmpty() ? PISTON_LOCAL.relative(PISTON_FACING)
                : expectedAttachedPositions.get(0);
        final BlockState chassisState = getLocalBlockState(subLevel, chassisLocal);
        if (!moving && !liveContraption) {
            requireInvariant(chassisCanStickToUpMarker(chassisState),
                    "front_radial_chassis_not_configured_for_off_axis_marker", failures);
        }

        boolean travelPathClear = true;
        if (retracted) {
            for (int i = 2; i <= PISTON_EXTENSION_POLES + 1; i++) {
                final BlockPos local = PISTON_LOCAL.relative(PISTON_FACING, i);
                final BlockState state = getLocalBlockState(subLevel, local);
                if (!state.isAir()) {
                    travelPathClear = false;
                    failures.add("travel_path_blocked_at_local_" + formatBlockPos(local));
                }
            }
        }

        final BlockEntity motor = getLocalBlockEntity(subLevel, MOTOR_LOCAL);
        final BlockEntity piston = getLocalBlockEntity(subLevel, PISTON_LOCAL);
        final int motorValue = readFixtureMotorValue(subLevel);
        final double motorSpeed = asDouble(invokeNoArgRaw(motor, "getSpeed"));
        final double pistonSpeed = asDouble(invokeNoArgRaw(piston, "getSpeed"));
        final double pistonMovementSpeed = asDouble(invokeNoArgRaw(piston, "getMovementSpeed"));
        final boolean kineticNetworkPresent = asBoolean(invokeNoArgRaw(piston, "hasNetwork"))
                || readFieldRaw(piston, "network") != null;
        final boolean kineticDriveReady = motor != null
                && piston != null
                && Double.isFinite(motorSpeed)
                && Double.isFinite(pistonSpeed);
        if (!kineticDriveReady) {
            failures.add("kinetic_drive_not_ready");
        }
        final boolean extensionChainValid = liveContraption ? livePiston.capturedBlocks() >= MOVED_STRUCTURE_BLOCKS.size()
                : retracted ? retractedPoleCount == PISTON_EXTENSION_POLES
                : extended ? extendedPoleCount > 0 || frontHeadPresent
                : retractedPoleCount == PISTON_EXTENSION_POLES || extendedPoleCount > 0 || frontHeadPresent;
        if (!extensionChainValid && !retracted) {
            failures.add("extension_chain_not_recognizable_for_state_" + pistonStateName);
        }

        return new FixtureCheck(
                failures,
                failures.stream().noneMatch(reason -> reason.startsWith("mechanical_piston_missing")
                        || reason.startsWith("sticky_mechanical_piston_required")
                        || reason.startsWith("creative_motor_missing")
                        || reason.startsWith("shaft_missing")
                        || reason.startsWith("attached_structure_missing")
                        || reason.startsWith("front_radial_chassis")),
                extensionChainValid,
                kineticDriveReady,
                retracted ? retractedPoleCount : extended ? extendedPoleCount : Math.max(retractedPoleCount, extendedPoleCount),
                retractedPoleCount,
                extendedPoleCount,
                frontHeadPresent,
                attachedStructurePresent,
                travelPathClear,
                pistonStateName,
                expectedAttachedPositions,
                motorValue,
                motorSpeed,
                pistonSpeed,
                pistonMovementSpeed,
                kineticNetworkPresent);
    }

    private static List<PistonSnapshot> collectPistons(final ServerSubLevel subLevel) {
        final List<PistonSnapshot> pistons = new ArrayList<>();
        for (final BlockEntity blockEntity : collectBlockEntities(subLevel)) {
            final BlockState state = SubLevelBlockStateLookup.getBlockStateOrAir(subLevel, blockEntity.getBlockPos());
            if (!blockIdMatches(state, MECHANICAL_PISTON_ID)
                    && !blockEntity.getClass().getName().contains("MechanicalPistonBlockEntity")) {
                continue;
            }

            final BlockPos plotPos = blockEntity.getBlockPos();
            final BlockPos localPos = plotPos.subtract(subLevel.getPlot().getCenterBlock());
            final Direction facing = directionProperty(state, "facing", Direction.EAST);
            final Vector3d localMotionAxis = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
            final Vector3d visibleMotionAxis = subLevel.logicalPose().orientation()
                    .transform(localMotionAxis, new Vector3d());
            final BlockPos createAnchorLocal = localPos.relative(facing);
            final BlockPos createAnchorPlot = SubLevelBlockEditHelper.localOffsetToPlotBlock(subLevel, createAnchorLocal);
            final Vector3d expectedVisibleAnchor = subLevel.logicalPose().transformPosition(new Vector3d(
                    createAnchorPlot.getX(), createAnchorPlot.getY(), createAnchorPlot.getZ()));

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
            final Vec3 previousRawEntityPosition = entity == null ? null : new Vec3(entity.xOld, entity.yOld, entity.zOld);
            final boolean rawLooksPlot = rawEntityPosition != null
                    && (Math.abs(rawEntityPosition.x) > 1_000_000.0
                    || Math.abs(rawEntityPosition.z) > 1_000_000.0
                    || rawEntityPosition.distanceTo(Vec3.atLowerCornerOf(createAnchorPlot)) < 8.0);
            final boolean containingSubLevelKnown = entity != null && Sable.HELPER.getContaining(entity) == subLevel;
            pistons.add(new PistonSnapshot(
                    blockEntity,
                    localPos,
                    plotPos,
                    state,
                    facing,
                    localMotionAxis,
                    visibleMotionAxis,
                    statePropertyName(state, "state", "unknown"),
                    asDouble(invokeNoArgRaw(blockEntity, "getSpeed")),
                    asDouble(invokeNoArgRaw(blockEntity, "getTheoreticalSpeed")),
                    asDouble(invokeNoArgRaw(blockEntity, "getMovementSpeed")),
                    asInt(readFieldRaw(blockEntity, "extensionLength")),
                    asInt(invokeNoArgRaw(blockEntity, "getExtensionRange")),
                    asInt(invokeNoArgRaw(blockEntity, "getInitialOffset")),
                    asDouble(readFieldRaw(blockEntity, "offset")),
                    asDouble(invokeFloatArgRaw(blockEntity, "getInterpolatedOffset", 0.0F)),
                    asBoolean(readFieldRaw(blockEntity, "running")),
                    asBoolean(readFieldRaw(blockEntity, "assembleNextTick")),
                    asBoolean(readFieldRaw(blockEntity, "needsContraption")),
                    asBoolean(readFieldRaw(blockEntity, "waitingForSpeedChange")),
                    asDouble(readFieldRaw(blockEntity, "clientOffsetDiff")),
                    asVec3(invokeNoArgRaw(blockEntity, "getMotionVector")),
                    movedContraption,
                    entity == null ? -1 : entity.getId(),
                    entity == null ? "none" : entity.level().getClass().getName(),
                    rawEntityPosition,
                    previousRawEntityPosition,
                    entityControllerPos,
                    safeIsLoaded(entity == null ? null : entity.level(), entityControllerPos),
                    normalController,
                    implementsTypeName(normalController, CREATE_CONTROL_CONTRAPTION_CLASS),
                    sableController,
                    implementsTypeName(sableController, CREATE_CONTROL_CONTRAPTION_CLASS),
                    createAnchorLocal,
                    createAnchorPlot,
                    expectedVisibleAnchor,
                    rawLooksPlot,
                    containingSubLevelKnown,
                    contraption,
                    capturedBlocks,
                    formatContraptionBounds(contraption)));
        }
        pistons.sort(Comparator.comparing(PistonSnapshot::localPos, M14TestCommands::compareBlockPos));
        return pistons;
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
        blocks.sort(Comparator.comparing(BlockSample::localPos, M14TestCommands::compareBlockPos));
        return blocks;
    }

    private static String classifyState(final boolean assembled, final String pistonState,
                                        final List<PistonSnapshot> pistons) {
        if (!assembled) {
            if ("extended".equals(pistonState)) {
                return "DISASSEMBLED_EXTENDED_VALID";
            }
            if ("moving".equals(pistonState)) {
                return "ASSEMBLING";
            }
            return "UNASSEMBLED_READY";
        }
        final boolean running = pistons.stream().anyMatch(PistonSnapshot::running);
        if (running || "moving".equals(pistonState)) {
            return "ASSEMBLED_MOVING_VALID";
        }
        if ("extended".equals(pistonState)) {
            return "ASSEMBLED_EXTENDED_VALID";
        }
        return "ASSEMBLED_RETRACTED_VALID";
    }

    private static int countContraptionBlocks(@Nullable final Object contraption) {
        final Object blocks = invokeNoArgRaw(contraption, "getBlocks");
        return blocks instanceof final Map<?, ?> map ? map.size() : 0;
    }

    private static List<String> capturedBlockEntries(@Nullable final Object contraption) {
        final Object blocks = invokeNoArgRaw(contraption, "getBlocks");
        if (!(blocks instanceof final Map<?, ?> map)) {
            return List.of("capturedBlocks=unavailable");
        }
        final List<String> entries = new ArrayList<>(map.size());
        for (final Map.Entry<?, ?> entry : map.entrySet()) {
            final Object value = entry.getValue();
            final Object state = firstNonNull(readFieldRaw(value, "state"),
                    invokeNoArgRaw(value, "state"));
            entries.add("localPos=" + entry.getKey()
                    + " blockState=" + state
                    + " sourcePlot=UNAVAILABLE_AFTER_CREATE_REMOVAL"
                    + " role=" + capturedRole(entry.getKey())
                    + " rawValueClass=" + className(value));
        }
        entries.sort(String::compareTo);
        return entries;
    }

    private static String capturedRole(@Nullable final Object localPos) {
        if (!(localPos instanceof BlockPos position)) {
            return "unknown";
        }
        if (position.getY() == 1) {
            return "off_axis_marker";
        }
        return position.getX() < -1 ? "extension_or_head" : "front_structure_or_anchor";
    }

    private static String capturedBlockPositions(@Nullable final Object contraption) {
        final Object blocks = invokeNoArgRaw(contraption, "getBlocks");
        return blocks instanceof final Map<?, ?> map ? map.keySet().toString() : "unavailable";
    }

    private static String formatContraptionBounds(@Nullable final Object contraption) {
        final Object bounds = readFieldRaw(contraption, "bounds");
        return bounds instanceof AABB ? bounds.toString() : String.valueOf(bounds);
    }

    private static boolean isM14FixtureCandidate(final ServerSubLevel subLevel) {
        return (subLevel.getName() != null && subLevel.getName().startsWith("m14"))
                || !collectPistons(subLevel).isEmpty();
    }

    private static boolean isEndpointNoOp(final String action, final int rpm, final String previousPistonState) {
        if (action.startsWith("set_motor") || action.startsWith("reverse") || rpm == SPAWN_MOTOR_RPM) {
            return false;
        }
        return rpm < 0 && "extended".equals(previousPistonState)
                || rpm > 0 && "retracted".equals(previousPistonState) && action.contains("retract");
    }

    private static List<BlockPos> requiredPoleLocals() {
        final List<BlockPos> poles = new ArrayList<>(PISTON_EXTENSION_POLES);
        for (int i = 1; i <= PISTON_EXTENSION_POLES; i++) {
            poles.add(PISTON_LOCAL.relative(EXPECTED_POLE_DIRECTION, i));
        }
        return poles;
    }

    private static List<BlockPos> extendedPoleLocals() {
        final List<BlockPos> poles = new ArrayList<>(PISTON_EXTENSION_POLES);
        for (int i = 1; i <= PISTON_EXTENSION_POLES; i++) {
            poles.add(PISTON_LOCAL.relative(PISTON_FACING, i));
        }
        return poles;
    }

    private static List<BlockPos> attachmentLocalsForState(final String pistonStateName) {
        if ("extended".equals(pistonStateName)) {
            final List<BlockPos> shifted = new ArrayList<>(MOVED_STRUCTURE_BLOCKS.size());
            for (final BlockPos local : MOVED_STRUCTURE_BLOCKS) {
                shifted.add(local.relative(PISTON_FACING, PISTON_EXTENSION_POLES));
            }
            return shifted;
        }
        return MOVED_STRUCTURE_BLOCKS;
    }

    private static List<BlockPos> poleDiagnosticLocals() {
        final List<BlockPos> locals = new ArrayList<>();
        locals.addAll(requiredPoleLocals());
        locals.addAll(extendedPoleLocals());
        return locals;
    }

    private static List<BlockPos> attachmentDiagnosticLocals() {
        final List<BlockPos> locals = new ArrayList<>();
        locals.addAll(MOVED_STRUCTURE_BLOCKS);
        locals.addAll(attachmentLocalsForState("extended"));
        return locals;
    }

    private static List<BlockPos> diagnosticLayoutLocals() {
        final List<BlockPos> locals = new ArrayList<>();
        locals.add(MOTOR_LOCAL);
        locals.add(SHAFT_LOCAL);
        locals.add(PISTON_LOCAL);
        locals.addAll(poleDiagnosticLocals());
        locals.addAll(attachmentDiagnosticLocals());
        for (int i = 2; i <= PISTON_EXTENSION_POLES + 1; i++) {
            locals.add(PISTON_LOCAL.relative(PISTON_FACING, i));
        }
        locals.sort(M14TestCommands::compareBlockPos);
        final List<BlockPos> unique = new ArrayList<>();
        for (final BlockPos local : locals) {
            if (!unique.contains(local)) {
                unique.add(local);
            }
        }
        return unique;
    }

    private static List<BlockPos> resetClearLocals() {
        final List<BlockPos> locals = new ArrayList<>();
        for (int x = -PISTON_EXTENSION_POLES - 1; x <= PISTON_EXTENSION_POLES + 3; x++) {
            locals.add(new BlockPos(x, 0, 0));
            locals.add(new BlockPos(x, 1, 0));
        }
        locals.add(MOTOR_LOCAL);
        locals.add(SHAFT_LOCAL);
        return locals;
    }

    private static boolean isCreatePoleForFixture(final BlockState state) {
        return blockIdMatches(state, PISTON_EXTENSION_POLE_ID)
                && propertyMatches(state, "facing", PISTON_FACING.getName());
    }

    private static boolean isMechanicalPiston(final BlockState state) {
        return blockIdMatches(state, MECHANICAL_PISTON_ID)
                || blockIdMatches(state, STICKY_MECHANICAL_PISTON_ID);
    }

    private static boolean hasAllBlocks(final ServerSubLevel subLevel, final List<BlockPos> locals) {
        for (final BlockPos local : locals) {
            if (getLocalBlockState(subLevel, local).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static String expectedNextCycleAction(final String cycleState) {
        return switch (cycleState) {
            case "RETRACTED_READY" -> "extend";
            case "EXTENDED_READY" -> "retract";
            case "MOVING_EXTEND", "MOVING_RETRACT" -> "inspect_then_stop_or_reverse";
            default -> "reset_fixture_after_motion_stops";
        };
    }

    private static String poleAxis(final BlockState state) {
        final Direction facing = directionProperty(state, "facing", null);
        return facing == null ? "missing" : facing.getAxis().getName();
    }

    private static boolean isPistonHead(final BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("piston_head");
    }

    private static boolean chassisCanStickToUpMarker(final BlockState state) {
        return blockIdMatches(state, RADIAL_CHASSIS_ID)
                && propertyMatches(state, "axis", "x")
                && propertyMatches(state, "sticky_north", "true");
    }

    private static boolean layoutPositionValid(final ServerSubLevel subLevel, final BlockPos local,
                                               final BlockState state) {
        if (local.equals(MOTOR_LOCAL)) {
            return blockIdMatches(state, CREATIVE_MOTOR_ID) && propertyMatches(state, "facing", "up");
        }
        if (local.equals(SHAFT_LOCAL)) {
            return blockIdMatches(state, SHAFT_ID) && propertyMatches(state, "axis", "y");
        }
        if (local.equals(PISTON_LOCAL)) {
            return isMechanicalPiston(state)
                    && propertyMatches(state, "facing", PISTON_FACING.getName())
                    && propertyMatches(state, "axis_along_first", "true");
        }
        if (requiredPoleLocals().contains(local) || extendedPoleLocals().contains(local)) {
            return state.isAir() || isCreatePoleForFixture(state) || isPistonHead(state)
                    || attachmentLocalsForState("extended").contains(local);
        }
        if (attachmentDiagnosticLocals().contains(local)) {
            return state.isAir() || chassisCanStickToUpMarker(state) || blockIdMatches(state, BuiltInRegistries.BLOCK.getKey(Blocks.STONE));
        }
        return !state.isAir() || getLocalBlockState(subLevel, local).isAir();
    }

    private static String layoutRole(final BlockPos local) {
        if (local.equals(MOTOR_LOCAL)) {
            return "creative_motor_vertical_drive";
        }
        if (local.equals(SHAFT_LOCAL)) {
            return "shaft_y_axis_drive";
        }
        if (local.equals(PISTON_LOCAL)) {
            return "mechanical_piston_controller";
        }
        if (requiredPoleLocals().contains(local)) {
            return "retracted_extension_pole_behind_piston";
        }
        if (extendedPoleLocals().contains(local)) {
            return "extended_front_pole_or_travel_cell";
        }
        if (MOVED_STRUCTURE_BLOCKS.contains(local)) {
            return local.getY() == 0 ? "retracted_front_radial_chassis" : "retracted_off_axis_marker";
        }
        if (attachmentLocalsForState("extended").contains(local)) {
            return local.getY() == 0 ? "extended_front_radial_chassis" : "extended_off_axis_marker";
        }
        return "travel_clearance";
    }

    private static String expectedLayoutDescription(final BlockPos local) {
        if (local.equals(MOTOR_LOCAL)) {
            return "create:creative_motor[facing=up], value initially 0";
        }
        if (local.equals(SHAFT_LOCAL)) {
            return "create:shaft[axis=y]";
        }
        if (local.equals(PISTON_LOCAL)) {
            return "create:sticky_mechanical_piston[facing=east,axis_along_first=true,state=retracted|moving|extended]";
        }
        if (requiredPoleLocals().contains(local)) {
            return "create:piston_extension_pole[facing=east] while retracted";
        }
        if (extendedPoleLocals().contains(local)) {
            return "air while retracted, generated/moved piston pole while extended";
        }
        if (MOVED_STRUCTURE_BLOCKS.contains(local)) {
            return local.getY() == 0 ? "create:radial_chassis[axis=x,sticky_north=true]" : "minecraft:stone marker";
        }
        if (attachmentLocalsForState("extended").contains(local)) {
            return "moved attached structure when piston is extended";
        }
        return "air travel clearance";
    }

    private static void setFixtureMotorSpeed(final ServerSubLevel subLevel, final int rpm) {
        final BlockEntity motor = getLocalBlockEntity(subLevel, MOTOR_LOCAL);
        if (motor == null) {
            throw new IllegalStateException("M14 fixture motor is missing at local " + formatBlockPos(MOTOR_LOCAL));
        }
        final Object behaviour = readFieldRaw(motor, "generatedSpeed");
        if (behaviour == null || invokeIntArgRaw(behaviour, "setValue", rpm) == null) {
            throw new IllegalStateException("M14 fixture motor does not expose Create ScrollValueBehaviour at local "
                    + formatBlockPos(MOTOR_LOCAL));
        }
    }

    private static boolean trySetFixtureMotorSpeed(final ServerSubLevel subLevel, final int rpm) {
        try {
            setFixtureMotorSpeed(subLevel, rpm);
            return true;
        } catch (final RuntimeException ignored) {
            return false;
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
        return SubLevelBlockStateLookup.getBlockEntity(subLevel,
                SubLevelBlockEditHelper.localOffsetToPlotBlock(subLevel, localPos));
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

    private static @Nullable Object firstNonNull(@Nullable final Object first, @Nullable final Object second) {
        return first != null ? first : second;
    }

    private static @Nullable BlockPos asBlockPos(@Nullable final Object value) {
        return value instanceof final BlockPos blockPos ? blockPos : null;
    }

    private static @Nullable Vec3 asVec3(@Nullable final Object value) {
        return value instanceof final Vec3 vec3 ? vec3 : null;
    }

    private static int asInt(@Nullable final Object value) {
        return value instanceof final Number number ? number.intValue() : Integer.MIN_VALUE;
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

    private static @Nullable Object invokeFloatArgRaw(@Nullable final Object target,
                                                      final String methodName,
                                                      final float value) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Method method = type.getDeclaredMethod(methodName, float.class);
                method.setAccessible(true);
                return method.invoke(target, value);
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

    private static boolean finiteVec3(@Nullable final Vec3 vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
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
        Sable.LOGGER.warn("SABLE_M14 phase=rollback_begin name={} id={} reason={}",
                name, subLevel.getUniqueId(), failure.getClass().getSimpleName());
        try {
            container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
            Sable.LOGGER.warn("SABLE_M14 phase=rollback_complete name={} id={}", name, subLevel.getUniqueId());
        } catch (final Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
            Sable.LOGGER.error("SABLE_M14 phase=rollback_failed name={} id={}", name, subLevel.getUniqueId(), cleanupFailure);
        }
    }

    private static CommandSyntaxException commandFailure(final RuntimeException exception) {
        Sable.LOGGER.error("SABLE_M14 phase=failed", exception);
        final String message = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        return ERROR_M14_FAILED.create(message);
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

    private static String formatBlockPosList(final List<BlockPos> positions) {
        final List<String> formatted = new ArrayList<>(positions.size());
        for (final BlockPos position : positions) {
            formatted.add(formatBlockPos(position));
        }
        return formatted.toString();
    }

    private record BlockSample(BlockPos localPos, BlockPos plotPos, ChunkPos chunk, BlockState state) {
    }

    private record M14Stats(int blockCount, double mass, double selfMass, @Nullable Vector3dc centerOfMass,
                            BoundingBox3ic plotBounds, boolean physicsBodyRegistered,
                            boolean collisionGeometryPresent, Vector3dc linearVelocity,
                            Vector3dc angularVelocity, int pistonCount, int plotContraptionCount) {
    }

    private record FixtureCheck(List<String> failures, boolean layoutValid, boolean extensionChainValid,
                                boolean kineticDriveReady, int poleCount, int retractedPoleCount,
                                int extendedPoleCount, boolean frontHeadPresent,
                                boolean attachedStructurePresent, boolean travelPathClear,
                                String pistonStateName, List<BlockPos> expectedAttachedPositions,
                                int motorValue, double motorSpeed, double pistonSpeed,
                                double pistonMovementSpeed, boolean kineticNetworkPresent) {
        boolean ready() {
            return this.failures.isEmpty();
        }

        boolean controlReady() {
            return this.layoutValid && this.extensionChainValid && this.attachedStructurePresent
                    && this.kineticDriveReady;
        }

        boolean basicControlReady() {
            return this.layoutValid && this.kineticDriveReady;
        }
    }

    private record PistonSnapshot(BlockEntity blockEntity, BlockPos localPos, BlockPos plotPos, BlockState state,
                                  Direction facing, Vector3dc localMotionAxis, Vector3dc visibleMotionAxis,
                                  String pistonStateName, double speed, double theoreticalSpeed,
                                  double movementSpeed, int extensionLength, int extensionRange,
                                  int initialOffset, double offset, double interpolatedOffset0,
                                  boolean running, boolean assembleNextTick, boolean needsContraption,
                                  boolean waitingForSpeedChange, double clientOffsetDiff,
                                  @Nullable Vec3 motionVector, @Nullable Object movedContraption,
                                  int entityId, String entityLevelClass, @Nullable Vec3 rawEntityPosition,
                                  @Nullable Vec3 previousRawEntityPosition, @Nullable BlockPos entityControllerPos,
                                  boolean normalControllerLoaded, @Nullable BlockEntity normalController,
                                  boolean normalControllerIsControl, @Nullable BlockEntity sableController,
                                  boolean sableControllerIsControl, BlockPos createAnchorLocal,
                                  BlockPos createAnchorPlot, Vector3dc expectedVisibleAnchor,
                                  boolean rawEntityPositionLooksLikePlot, boolean containingSubLevelKnown,
                                  @Nullable Object contraption, int capturedBlocks, String contraptionLocalBounds) {
        boolean assembled() {
            return this.running || this.movedContraption != null || this.capturedBlocks > 0
                    || "moving".equals(this.pistonStateName);
        }
    }
}
