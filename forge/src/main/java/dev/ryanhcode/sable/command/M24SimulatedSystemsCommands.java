package dev.ryanhcode.sable.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.command.SableCommandHelper;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.diagnostic.RotaryPipelineTraceRegistry;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.m24.M24Family;
import dev.simulated_team.simulated.content.blocks.m24.M24PhysicalBlockEntity;
import dev.simulated_team.simulated.content.blocks.m24.M24TorsionSpringBlockEntity;
import dev.simulated_team.simulated.index.SimulatedBlocks;
import dev.simulated_team.simulated.index.SimulatedConfig;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class M24SimulatedSystemsCommands {
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PHYSICAL_FAMILIES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(List.of("swivel", "rope", "winch", "docking", "torsion"), builder);
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ROTATABLE_FAMILIES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(List.of("swivel", "torsion"), builder);
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_BODY_SLOTS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(List.of("a", "b"), builder);
    private static final Map<String, FixtureState> LAST_FIXTURES = new HashMap<>();
    private static final Map<String, RotaryCanaryState> LAST_ROTARY_CANARIES = new HashMap<>();
    private static final Map<String, RotaryCanaryState> LAST_ROTARY_REALBODY_CANARIES = new HashMap<>();
    private static final Map<String, RotaryCanaryState> LAST_FIXED_REALBODY_CANARIES = new HashMap<>();
    private static final Map<String, RopeCanaryState> LAST_ROPE_CANARIES = new HashMap<>();
    private static final ResourceLocation CREATIVE_MOTOR_ID = new ResourceLocation("create", "creative_motor");
    private static final ResourceLocation SHAFT_ID = new ResourceLocation("create", "shaft");

    private M24SimulatedSystemsCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m24")
                .then(Commands.literal("status").executes(M24SimulatedSystemsCommands::status))
                .then(Commands.literal("help").executes(M24SimulatedSystemsCommands::help))
                .then(Commands.literal("cleanup").executes(M24SimulatedSystemsCommands::cleanup))
                .then(Commands.literal("bodies").executes(M24SimulatedSystemsCommands::bodies))
                .then(Commands.literal("nudge")
                        .then(Commands.argument("family", StringArgumentType.word())
                                .suggests(SUGGEST_PHYSICAL_FAMILIES)
                                .then(Commands.argument("slot", StringArgumentType.word())
                                        .suggests(SUGGEST_BODY_SLOTS)
                                        .then(Commands.argument("dx", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("dy", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("dz", DoubleArgumentType.doubleArg())
                                                                .executes(M24SimulatedSystemsCommands::nudge)))))))
                .then(Commands.literal("rotate")
                        .then(Commands.argument("family", StringArgumentType.word())
                                .suggests(SUGGEST_ROTATABLE_FAMILIES)
                                .then(Commands.argument("slot", StringArgumentType.word())
                                        .suggests(SUGGEST_BODY_SLOTS)
                                        .then(Commands.argument("degX", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("degY", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("degZ", DoubleArgumentType.doubleArg())
                                                                .executes(M24SimulatedSystemsCommands::rotate)))))))
                .then(Commands.literal("winch")
                        .then(Commands.literal("rpm")
                                .then(Commands.argument("rpm", IntegerArgumentType.integer(-256, 256))
                                        .executes(ctx -> setFixtureMotorRpm(ctx, M24Family.ROPE_WINCH)))))
                .then(Commands.literal("docking")
                        .then(Commands.literal("power")
                                .then(Commands.argument("powered", BoolArgumentType.bool())
                                        .executes(M24SimulatedSystemsCommands::setDockingFixturePower))))
                .then(Commands.literal("torsion")
                        .then(Commands.literal("rpm")
                                .then(Commands.argument("rpm", IntegerArgumentType.integer(-256, 256))
                                        .executes(ctx -> setFixtureMotorRpm(ctx, M24Family.TORSION_SPRING))))
                        .then(Commands.literal("angle_limit")
                                .then(Commands.argument("degrees", DoubleArgumentType.doubleArg(1.0D, 360.0D))
                                        .executes(M24SimulatedSystemsCommands::setTorsionAngleLimit)))
                        .then(Commands.literal("power")
                                .then(Commands.argument("powered", BoolArgumentType.bool())
                                        .executes(M24SimulatedSystemsCommands::setTorsionFixturePower))))
                .then(Commands.literal("fixture")
                        .then(Commands.literal("torsion")
                                .then(Commands.literal("basic").executes(ctx -> fixture(ctx.getSource(), M24Family.TORSION_SPRING))))
                        .then(Commands.literal("swivel")
                                .then(Commands.literal("basic").executes(ctx -> fixture(ctx.getSource(), M24Family.SWIVEL_BEARING))))
                        .then(Commands.literal("rope")
                                .then(Commands.literal("basic").executes(ctx -> fixture(ctx.getSource(), M24Family.ROPE_CONNECTOR))))
                        .then(Commands.literal("winch")
                                .then(Commands.literal("basic").executes(ctx -> fixture(ctx.getSource(), M24Family.ROPE_WINCH))))
                        .then(Commands.literal("docking")
                                .then(Commands.literal("basic").executes(ctx -> fixture(ctx.getSource(), M24Family.DOCKING_CONNECTOR)))))
                .then(Commands.literal("backend_canary")
                        .then(Commands.literal("rotary")
                                .executes(M24SimulatedSystemsCommands::backendRotaryCanary)
                                .then(Commands.literal("inspect").executes(M24SimulatedSystemsCommands::inspectBackendRotaryCanary)))
                        .then(Commands.literal("rotary_realbody")
                                .executes(M24SimulatedSystemsCommands::backendRotaryRealBodyCanary)
                                .then(Commands.literal("inspect").executes(M24SimulatedSystemsCommands::inspectBackendRotaryRealBodyCanary)))
                        .then(Commands.literal("fixed_realbody")
                                .executes(M24SimulatedSystemsCommands::backendFixedRealBodyCanary)
                                .then(Commands.literal("inspect").executes(M24SimulatedSystemsCommands::inspectBackendFixedRealBodyCanary)))
                        .then(Commands.literal("rope")
                                .executes(M24SimulatedSystemsCommands::backendRopeCanary)
                                .then(Commands.literal("inspect").executes(M24SimulatedSystemsCommands::inspectBackendRopeCanary))))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("family", StringArgumentType.word())
                                .executes(M24SimulatedSystemsCommands::inspect))));
    }

    private static int status(final CommandContext<CommandSourceStack> ctx) {
        send(ctx.getSource(), "SABLE_M24_STATUS simulatedBaseline=" + Simulated.BASELINE_COMMIT
                + " implementationRevision=M24.12"
                + " SWIVEL=RUNTIME_PROVEN"
                + " ROPE=RUNTIME_PROVEN"
                + " WINCH=RUNTIME_PROVEN"
                + " DOCKING=RUNTIME_PROVEN"
                + " TORSION=RUNTIME_PROVEN"
                + " torsion=CREATE_KINETIC_ANGLE_LIMIT_AND_RETURN_BACKPORT_NO_SABLE_JOINT"
                + " swivel=ROTARY_CONSTRAINT"
                + " rope=ROPE_ATTACHMENT"
                + " winch=ROPE_LENGTH_CONTROL"
                + " docking=FIXED_CONSTRAINT"
                + " altitudeSensor=VISIBLE_COORDINATE_SENSOR_READY"
                + " velocitySensor=VISIBLE_COORDINATE_SENSOR_READY"
                + " opticalSensor=VISIBLE_COORDINATE_SENSOR_READY"
                + " steeringWheel=ONBOARD_INPUT_ONLY_AERONAUTICS_DEFERRED"
                + " enabled=" + SimulatedConfig.ENABLE_M24_SIMULATED_SYSTEMS.get()
                + " status=CLOSED_RUNTIME_PROVEN");
        return 1;
    }

    private static int help(final CommandContext<CommandSourceStack> ctx) {
        send(ctx.getSource(), "SABLE_M24_HELP swivel=fixture_then_assemble_A_wait_then_assemble_B_wait_10s_bodies_inspect_nudge_rotate"
                + " winch=fixture_then_assemble_A_B_then_winch_rpm_0_16_0_-16"
                + " docking=fixture_then_assemble_A_B_then_docking_power_true_then_false_to_disconnect"
                + " torsion=fixture_then_assemble_A_then_angle_limit_45_then_rpm_32_0_-32_then_power_true_false_for_hold_release");
        return 1;
    }

    private static int setFixtureMotorRpm(final CommandContext<CommandSourceStack> ctx,
                                          final M24Family expectedFamily) {
        final CommandSourceStack source = ctx.getSource();
        final FixtureState state = LAST_FIXTURES.get(fixtureKey(source.getLevel()));
        if (state == null || state.family() != expectedFamily) {
            send(source, "SABLE_M24_" + (expectedFamily == M24Family.ROPE_WINCH ? "WINCH_POWER" : "TORSION_POWER")
                    + " status=FAIL reason=no_matching_active_fixture");
            return 0;
        }
        if (state.bodyASableId == null) {
            send(source, "SABLE_M24_" + (expectedFamily == M24Family.ROPE_WINCH ? "WINCH_POWER" : "TORSION_POWER")
                    + " status=FAIL reason=body_A_not_assembled");
            return 0;
        }
        final ServerSubLevel body = findOrLoadSubLevelById(source.getLevel(), state.bodyASableId);
        if (body == null) {
            sendBodyLookupFailure(source, state, true,
                    expectedFamily == M24Family.ROPE_WINCH ? "SABLE_M24_WINCH_POWER" : "SABLE_M24_TORSION_POWER");
            return 0;
        }
        final BlockPos motorRaw = findRawBlock(source.getLevel(), body,
                blockState -> CREATIVE_MOTOR_ID.equals(ForgeRegistries.BLOCKS.getKey(blockState.getBlock())));
        if (motorRaw == null
                || !(source.getLevel().getBlockEntity(motorRaw) instanceof final CreativeMotorBlockEntity motor)) {
            send(source, "SABLE_M24_" + (expectedFamily == M24Family.ROPE_WINCH ? "WINCH_POWER" : "TORSION_POWER")
                    + " status=FAIL reason=creative_motor_not_found_in_body_A");
            return 0;
        }
        final int rpm = IntegerArgumentType.getInteger(ctx, "rpm");
        motor.generatedSpeed.setValue(rpm);
        motor.setChanged();
        final BlockPos componentRaw = findRawBlock(source.getLevel(), body,
                blockState -> blockState.is(state.bodyAComponent()));
        final M24PhysicalBlockEntity component = componentRaw != null
                && source.getLevel().getBlockEntity(componentRaw) instanceof final M24PhysicalBlockEntity physical
                        ? physical
                        : null;
        final String marker = expectedFamily == M24Family.ROPE_WINCH
                ? "SABLE_M24_WINCH_POWER"
                : "SABLE_M24_TORSION_POWER";
        send(source, marker + " status=PASS"
                + " motorPos=" + motorRaw.toShortString()
                + " requestedRPM=" + rpm
                + " actualMotorSpeed=" + motor.getGeneratedSpeed()
                + " componentKineticSpeed=" + (component == null ? "unresolved" : component.simulated$getKineticSpeed())
                + (expectedFamily == M24Family.ROPE_WINCH
                        ? " ropeCurrentLength=" + (component == null ? "unresolved" : component.simulated$getRopeCurrentLength())
                                + " ropeTargetLength=" + (component == null ? "unresolved" : component.simulated$getRopeTargetLength())
                        : " angleLimit=" + (component == null ? "unresolved" : component.simulated$getTorsionAngleLimit())
                                + " targetAngle=" + (component instanceof M24TorsionSpringBlockEntity torsion
                                        ? torsion.simulated$getTorsionTargetAngle() : "unresolved")
                                + " currentAngle=" + (component == null ? "unresolved" : component.simulated$getTorsionAngle())
                                + " outputPortAngle=" + (component instanceof M24TorsionSpringBlockEntity torsion
                                        ? torsion.simulated$getTorsionOutputPortAngle() : "unresolved")
                                + " outputSpeed=" + (component instanceof M24TorsionSpringBlockEntity torsion
                                        ? torsion.simulated$getTorsionOutputSpeed() : "unresolved")));
        return 1;
    }

    private static int setTorsionAngleLimit(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final FixtureState state = LAST_FIXTURES.get(fixtureKey(source.getLevel()));
        if (state == null || state.family() != M24Family.TORSION_SPRING || state.bodyASableId == null) {
            send(source, "SABLE_M24_TORSION_CONTROL status=FAIL reason=no_assembled_torsion_fixture");
            return 0;
        }
        final ServerSubLevel body = findOrLoadSubLevelById(source.getLevel(), state.bodyASableId);
        final BlockPos componentRaw = body == null ? null : findRawBlock(source.getLevel(), body,
                blockState -> blockState.is(state.bodyAComponent()));
        if (componentRaw == null
                || !(source.getLevel().getBlockEntity(componentRaw) instanceof final M24PhysicalBlockEntity component)) {
            send(source, "SABLE_M24_TORSION_CONTROL status=FAIL reason=torsion_component_unavailable");
            return 0;
        }
        final double degrees = DoubleArgumentType.getDouble(ctx, "degrees");
        component.simulated$setTorsionAngleLimit(degrees);
        send(source, "SABLE_M24_TORSION_CONTROL status=PASS action=ANGLE_LIMIT degrees=" + degrees
                + " sableId=" + body.getUniqueId());
        return 1;
    }

    private static int setDockingFixturePower(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final FixtureState state = LAST_FIXTURES.get(fixtureKey(source.getLevel()));
        if (state == null || state.family() != M24Family.DOCKING_CONNECTOR || state.bodyASableId == null) {
            send(source, "SABLE_M24_DOCKING_POWER status=FAIL reason=no_assembled_docking_fixture");
            return 0;
        }
        final ServerLevel level = source.getLevel();
        final ServerSubLevel body = findOrLoadSubLevelById(level, state.bodyASableId);
        final BlockPos connectorRaw = body == null ? null : findRawBlock(level, body,
                blockState -> blockState.is(state.bodyAComponent()));
        if (body == null || connectorRaw == null
                || !(level.getBlockEntity(connectorRaw) instanceof final M24PhysicalBlockEntity connector)) {
            send(source, "SABLE_M24_DOCKING_POWER status=FAIL reason=docking_connector_unavailable");
            return 0;
        }
        final boolean requested = BoolArgumentType.getBool(ctx, "powered");
        final boolean poweredBefore = connector.simulated$isDockingPowered();
        final boolean activeBefore = connector.simulated$hasActiveConstraint();
        final BlockPos sourceRaw = connectorRaw.above();
        final BlockState existing = level.getBlockState(sourceRaw);
        if (!existing.isAir() && !existing.is(Blocks.REDSTONE_BLOCK)) {
            send(source, "SABLE_M24_DOCKING_POWER status=FAIL reason=redstone_source_position_occupied"
                    + " redstoneSourceLocalPos=" + sourceRaw.subtract(body.getPlot().getCenterBlock()).toShortString());
            return 0;
        }
        level.setBlock(sourceRaw, requested ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        level.updateNeighborsAt(sourceRaw, level.getBlockState(sourceRaw).getBlock());
        level.updateNeighborsAt(connectorRaw, level.getBlockState(connectorRaw).getBlock());
        connector.onNeighborSignalChanged();
        final boolean observedSignal = level.hasNeighborSignal(connectorRaw);
        final boolean poweredAfter = connector.simulated$isDockingPowered();
        final boolean activeAfter = connector.simulated$hasActiveConstraint();
        send(source, "SABLE_M24_DOCKING_POWER status=PASS"
                + " connectorSable=" + body.getUniqueId()
                + " connectorLocalPos=" + connectorRaw.subtract(body.getPlot().getCenterBlock()).toShortString()
                + " connectorFacing=" + level.getBlockState(connectorRaw).getValue(DirectionalBlock.FACING)
                + " redstoneSourceLocalPos=" + sourceRaw.subtract(body.getPlot().getCenterBlock()).toShortString()
                + " requestedPowered=" + requested
                + " observedNeighborSignal=" + observedSignal
                + " observedPoweredBefore=" + poweredBefore
                + " observedPoweredAfter=" + poweredAfter
                + " constraintActiveBefore=" + activeBefore
                + " constraintActiveAfter=" + activeAfter
                + " disconnectTriggered=" + (poweredBefore && !poweredAfter && activeBefore && !activeAfter));
        return observedSignal == requested ? 1 : 0;
    }

    private static int setTorsionFixturePower(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final FixtureState state = LAST_FIXTURES.get(fixtureKey(source.getLevel()));
        if (state == null || state.family() != M24Family.TORSION_SPRING || state.bodyASableId == null) {
            send(source, "SABLE_M24_TORSION_POWER status=FAIL action=REDSTONE reason=no_assembled_torsion_fixture");
            return 0;
        }
        final ServerLevel level = source.getLevel();
        final ServerSubLevel body = findOrLoadSubLevelById(level, state.bodyASableId);
        final BlockPos torsionRaw = body == null ? null : findRawBlock(level, body,
                blockState -> blockState.is(state.bodyAComponent()));
        if (body == null || torsionRaw == null
                || !(level.getBlockEntity(torsionRaw) instanceof final M24TorsionSpringBlockEntity torsion)) {
            send(source, "SABLE_M24_TORSION_POWER status=FAIL action=REDSTONE reason=torsion_component_unavailable");
            return 0;
        }
        final boolean requested = BoolArgumentType.getBool(ctx, "powered");
        final BlockPos sourceRaw = torsionRaw.above();
        final BlockState existing = level.getBlockState(sourceRaw);
        if (!existing.isAir() && !existing.is(Blocks.REDSTONE_BLOCK)) {
            send(source, "SABLE_M24_TORSION_POWER status=FAIL action=REDSTONE reason=redstone_source_position_occupied");
            return 0;
        }
        level.setBlock(sourceRaw, requested ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        level.updateNeighborsAt(sourceRaw, level.getBlockState(sourceRaw).getBlock());
        level.updateNeighborsAt(torsionRaw, level.getBlockState(torsionRaw).getBlock());
        torsion.onNeighborSignalChanged();
        final boolean observed = level.hasNeighborSignal(torsionRaw);
        send(source, "SABLE_M24_TORSION_POWER status=PASS action=REDSTONE"
                + " sableId=" + body.getUniqueId()
                + " torsionLocalPos=" + torsionRaw.subtract(body.getPlot().getCenterBlock()).toShortString()
                + " redstoneSourceLocalPos=" + sourceRaw.subtract(body.getPlot().getCenterBlock()).toShortString()
                + " requestedPowered=" + requested
                + " observedNeighborSignal=" + observed
                + " currentAngle=" + torsion.simulated$getTorsionAngle()
                + " targetAngle=" + torsion.simulated$getTorsionTargetAngle());
        return observed == requested ? 1 : 0;
    }

    public static void onM22AssemblyCreated(final ServerLevel level, final BlockPos parentAssemblerPos,
                                            final ServerSubLevel subLevel) {
        final FixtureState state = LAST_FIXTURES.get(fixtureKey(level));
        if (state == null) {
            return;
        }
        final String slot;
        if (parentAssemblerPos.equals(state.bodyAAssembler())) {
            state.bodyASableId = subLevel.getUniqueId();
            state.bodyALastKnownHandle = subLevel.getRuntimeId();
            slot = "A";
        } else if (parentAssemblerPos.equals(state.bodyBAssembler())) {
            state.bodyBSableId = subLevel.getUniqueId();
            state.bodyBLastKnownHandle = subLevel.getRuntimeId();
            slot = "B";
        } else {
            return;
        }
        final boolean bodiesReady = state.expectedBodies() == 1
                ? state.bodyASableId != null
                : state.bodyASableId != null && state.bodyBSableId != null
                        && !state.bodyASableId.equals(state.bodyBSableId);
        state.updateLifecycle(state.bodyASableId != null, state.bodyBSableId != null, bodiesReady);
        Sable.LOGGER.info("SABLE_M24_BODY_ID_CAPTURE slot={} family={} parentAssembler={} sableId={} lifecycle={} timing=ASSEMBLY_COMPLETION_BEFORE_AUTOPAIR",
                slot, state.family().id(), parentAssemblerPos.toShortString(), subLevel.getUniqueId(),
                state.lifecycleName());
        Sable.LOGGER.info("SABLE_M24_BODY_ID_CAPTURE slot={} sableId={} bodyHandle={} ownership=TEST_HARNESS_ONLY reloadPolicy=AUTHORITATIVE_UUID_FROM_HOLDING_STORAGE",
                slot, subLevel.getUniqueId(), subLevel.getRuntimeId());
    }

    private static int fixture(final CommandSourceStack source, final M24Family family) {
        String stage = "COMMAND_RECEIVED";
        sendFixtureTrace(source, family, stage, "");
        try {
            final ServerLevel level = source.getLevel();
            final BlockPos origin = BlockPos.containing(source.getPosition()).offset(4, 1, 0);
            stage = "ORIGIN_RESOLVED";
            sendFixtureTrace(source, family, stage, " origin=" + origin.toShortString());
            cleanupFixture(level, LAST_FIXTURES.remove(fixtureKey(level)), false);
            stage = "CLEANUP_DONE";
            sendFixtureTrace(source, family, stage, "");

            final BlockPos bodyA = origin;
            final BlockPos bodyB = origin.offset(bodySpacing(family), 0, 0);
            final Direction bodyADirection = Direction.EAST;
            final Direction bodyBDirection = Direction.WEST;
            final RegistryObject<Block> primary = blockForPrimary(family);
            final boolean singleBody = family == M24Family.TORSION_SPRING;
            final RegistryObject<Block> secondary = blockForSecondary(family);
            final Block primaryBlock = requireFixtureBlock(primary, "primary");
            final Block secondaryBlock = singleBody ? primaryBlock : requireFixtureBlock(secondary, "secondary");
            final Set<BlockPos> parentBlocks = new HashSet<>();
            final Set<BlockPos> bodyAPlatform = buildSupport(level, bodyA);
            final Set<BlockPos> bodyBPlatform = singleBody ? Set.of() : buildSupport(level, bodyB);
            parentBlocks.addAll(bodyAPlatform);
            parentBlocks.addAll(bodyBPlatform);
            stage = "PLATFORM_PLACED";
            sendFixtureTrace(source, family, stage, " platformBlocks=" + parentBlocks.size());

            parentBlocks.addAll(buildBody(level, bodyA, primary, bodyADirection));
            if (family == M24Family.DOCKING_CONNECTOR) {
                configureDockingFixturePower(level, bodyA, bodyADirection, parentBlocks);
            }
            final BlockPos motorPos = placeFixtureKineticSource(level, family, bodyA);
            if (family == M24Family.TORSION_SPRING) {
                parentBlocks.remove(bodyA.west());
                parentBlocks.add(bodyA.east(2));
            }
            stage = "BODY_A_PLACED";
            sendFixtureTrace(source, family, stage, " bodyAOrigin=" + bodyA.toShortString()
                    + " kineticMotor=" + (motorPos == null ? "not_required" : motorPos.toShortString()));
            if (!singleBody) {
                parentBlocks.addAll(buildBody(level, bodyB, secondary, bodyBDirection));
                if (family == M24Family.DOCKING_CONNECTOR) {
                    configureDockingFixturePower(level, bodyB, bodyBDirection, parentBlocks);
                }
                stage = "BODY_B_PLACED";
                sendFixtureTrace(source, family, stage, " bodyBOrigin=" + bodyB.toShortString());
            }
            stage = "GLUE_CREATED";
            sendFixtureTrace(source, family, stage, " glue=CREATE_SUPER_GLUE");

            final BlockPos bodyAEndpoint = family == M24Family.ROPE_WINCH
                    ? bodyA
                    : bodyA.relative(bodyADirection);
            final FixtureState state = new FixtureState(family, bodyA, bodyB, bodyA.above(), bodyB.above(),
                    bodyAEndpoint, bodyB.relative(bodyBDirection), primaryBlock,
                    secondaryBlock, motorPos, singleBody ? 1 : 2, 6, Set.copyOf(parentBlocks));
            LAST_FIXTURES.put(fixtureKey(level), state);
            stage = "STATE_STORED";
            sendFixtureTrace(source, family, stage, " lifecycle=" + state.lifecycleName());

            final SelectionPreview bodyAPreview = previewAssemblySelection(level, bodyA, bodyAPlatform,
                    state.bodyAAssembler(), state.bodyAEndpoint());
            final SelectionPreview bodyBPreview = singleBody ? null : previewAssemblySelection(level, bodyB, bodyBPlatform,
                    state.bodyBAssembler(), state.bodyBEndpoint());

            send(source, "SABLE_M24_FIXTURE status=PASS family=" + fixtureName(family)
                    + " name=basic"
                    + " bodyAAssembler=" + state.bodyAAssembler().toShortString()
                    + " bodyBAssembler=" + (singleBody ? "not_required" : state.bodyBAssembler().toShortString())
                    + " bodyAComponent=" + state.bodyAEndpoint().toShortString()
                    + " bodyBComponent=" + (singleBody ? "not_required" : state.bodyBEndpoint().toShortString())
                    + " expectedBodies=" + state.expectedBodies()
                    + " expectedBlocksPerBody=6"
                    + " expectedMode=" + (singleBody ? "ONBOARD_CREATE_EXTRA_KINETICS" : "SABLE_TO_SABLE")
                    + " glue=CREATE_SUPER_GLUE"
                    + " supportPlatform=ordinary_minecraft_stone"
                    + " kineticMotor=" + (motorPos == null ? "not_required" : motorPos.toShortString())
                    + " lifecycle=PARENT_FIXTURE_READY"
                    + " sequence=" + (singleBody
                            ? "assemble_A_then_inspect_bodies_then_angle_limit_then_rpm_then_inspect_torsion"
                            : "assemble_A_then_inspect_bodies_then_assemble_B_auto_pair_expected_then_inspect_bodies_then_inspect_family"));
            logSelection(source, family, "A", bodyA, bodyAPreview);
            if (!singleBody) {
                logSelection(source, family, "B", bodyB, bodyBPreview);
            }
            stage = "COMPLETE";
            sendFixtureTrace(source, family, stage, " status=PASS");
            return bodyAPreview.validForFixture() && (singleBody || bodyBPreview.validForFixture()) ? 1 : 0;
        } catch (final RuntimeException exception) {
            Simulated.LOGGER.error("SABLE_M24_FIXTURE failed family={} stage={}", family.id(), stage, exception);
            send(source, "SABLE_M24_FIXTURE status=FAIL family=" + fixtureName(family)
                    + " reason=exception"
                    + " stage=" + stage
                    + " exceptionClass=" + exception.getClass().getName()
                    + " message=" + safeToken(exception.getMessage()));
            return 0;
        }
    }

    private static int cleanup(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final ServerLevel level = source.getLevel();
        final FixtureState state = LAST_FIXTURES.remove(fixtureKey(level));
        if (state == null) {
            send(source, "SABLE_M24_CLEANUP status=PASS reason=no_active_fixture");
            return 1;
        }
        final int removedBlocks = cleanupFixture(level, state, true);
        send(source, "SABLE_M24_CLEANUP status=PASS family=" + fixtureName(state.family())
                + " removedParentBlocks=" + removedBlocks
                + " action=parent_fixture_only_no_sable_deletion");
        return 1;
    }

    private static int bodies(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final FixtureState state = LAST_FIXTURES.get(fixtureKey(source.getLevel()));
        if (state == null) {
            send(source, "SABLE_M24_BODIES status=FAIL reason=no_active_fixture"
                    + " action=run_/sable_m24_fixture_<family>_basic_first");
            return 0;
        }
        final FixtureValidation validation = validateM24FixtureBodies(source, state, true);
        return validation.status().equals("PASS") ? 1 : 0;
    }

    private static int inspect(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final M24Family family = parseFamily(StringArgumentType.getString(ctx, "family"));
        if (family == null) {
            send(source, "SABLE_M24_INSPECT status=FAIL reason=unknown_family expected=torsion|swivel|rope|winch|docking|altitude|velocity|optical|steering");
            return 0;
        }
        final FixtureState state = LAST_FIXTURES.get(fixtureKey(source.getLevel()));
        if (state != null && state.family() == family) {
            final FixtureValidation validation = validateM24FixtureBodies(source, state, false);
            if (!validation.valid()) {
                send(source, "SABLE_M24_INSPECT status=FAIL family=" + family.id()
                        + " runtimeState=WAITING_FOR_VALID_BODIES"
                        + " reason=" + validation.reason());
                return 0;
            }
            final String report = validation.bodyA().endpointBlockEntity().inspect();
            send(source, "SABLE_M24_INSPECT " + report);
            return report.contains("runtimeState=ACTIVE") ? 1 : 0;
        }
        final M24PhysicalBlockEntity nearest = findNearestComponent(source, family);
        if (nearest == null) {
            send(source, "SABLE_M24_INSPECT status=FAIL family=" + family.id() + " runtimeState=NOT_FOUND");
            return 0;
        }
        final String report = nearest.inspect();
        send(source, "SABLE_M24_INSPECT " + report);
        return report.contains("runtimeState=ACTIVE") ? 1 : 0;
    }

    private static int nudge(final CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final FixtureBodyTarget target = resolveFixtureBodyTarget(ctx, "SABLE_M24_NUDGE", false);
        if (target == null) {
            return 0;
        }
        final double dx = DoubleArgumentType.getDouble(ctx, "dx");
        final double dy = DoubleArgumentType.getDouble(ctx, "dy");
        final double dz = DoubleArgumentType.getDouble(ctx, "dz");
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)) {
            send(source, "SABLE_M24_NUDGE status=FAIL family=" + fixtureName(target.family())
                    + " slot=" + target.slot()
                    + " reason=non_finite_delta");
            return 0;
        }
        final Vector3d before = new Vector3d(target.subLevel().logicalPose().position());
        final Vector3d after = new Vector3d(before).add(dx, dy, dz);
        target.handle().teleport(after, target.subLevel().logicalPose().orientation());
        send(source, "SABLE_M24_NUDGE status=PASS family=" + fixtureName(target.family())
                + " slot=" + target.slot()
                + " sableId=" + target.subLevel().getUniqueId()
                + " delta=(" + dx + "," + dy + "," + dz + ")"
                + " before=" + before
                + " after=" + after);
        return 1;
    }

    private static int rotate(final CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final FixtureBodyTarget target = resolveFixtureBodyTarget(ctx, "SABLE_M24_ROTATE", true);
        if (target == null) {
            return 0;
        }
        final double degX = DoubleArgumentType.getDouble(ctx, "degX");
        final double degY = DoubleArgumentType.getDouble(ctx, "degY");
        final double degZ = DoubleArgumentType.getDouble(ctx, "degZ");
        if (!Double.isFinite(degX) || !Double.isFinite(degY) || !Double.isFinite(degZ)) {
            send(source, "SABLE_M24_ROTATE status=FAIL family=" + fixtureName(target.family())
                    + " slot=" + target.slot()
                    + " reason=non_finite_delta");
            return 0;
        }
        final Quaterniond orientation = new Quaterniond(target.subLevel().logicalPose().orientation())
                .rotateXYZ(Math.toRadians(degX), Math.toRadians(degY), Math.toRadians(degZ))
                .normalize();
        target.handle().teleport(target.subLevel().logicalPose().position(), orientation);
        send(source, "SABLE_M24_ROTATE status=PASS family=" + fixtureName(target.family())
                + " slot=" + target.slot()
                + " sableId=" + target.subLevel().getUniqueId()
                + " deltaDegrees=(" + degX + "," + degY + "," + degZ + ")");
        return 1;
    }

    private static FixtureBodyTarget resolveFixtureBodyTarget(final CommandContext<CommandSourceStack> ctx,
                                                              final String marker,
                                                              final boolean requireRotatable)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final M24Family family = parseFamily(StringArgumentType.getString(ctx, "family"));
        if (family == null || !isPhysicalFixtureFamily(family)) {
            send(source, marker + " status=FAIL reason=unknown_family expected=swivel|rope|winch|docking|torsion");
            return null;
        }
        if (requireRotatable && family != M24Family.SWIVEL_BEARING && family != M24Family.TORSION_SPRING) {
            send(source, marker + " status=FAIL family=" + fixtureName(family)
                    + " reason=family_not_rotatable expected=swivel|torsion");
            return null;
        }
        final String slotInput = StringArgumentType.getString(ctx, "slot").toLowerCase(Locale.ROOT);
        final boolean slotA;
        if ("a".equals(slotInput)) {
            slotA = true;
        } else if ("b".equals(slotInput)) {
            slotA = false;
        } else {
            send(source, marker + " status=FAIL family=" + fixtureName(family)
                    + " reason=unknown_slot expected=a|b");
            return null;
        }
        final FixtureState state = LAST_FIXTURES.get(fixtureKey(source.getLevel()));
        if (state == null) {
            send(source, marker + " status=FAIL family=" + fixtureName(family)
                    + " slot=" + (slotA ? "A" : "B")
                    + " reason=no_active_fixture");
            return null;
        }
        if (state.family() != family) {
            send(source, marker + " status=FAIL family=" + fixtureName(family)
                    + " slot=" + (slotA ? "A" : "B")
                    + " reason=active_fixture_family_mismatch activeFamily=" + fixtureName(state.family()));
            return null;
        }
        final UUID sableId = slotA ? state.bodyASableId : state.bodyBSableId;
        if (sableId == null) {
            send(source, marker + " status=FAIL family=" + fixtureName(family)
                    + " slot=" + (slotA ? "A" : "B")
                    + " reason=body_uuid_unresolved");
            return null;
        }
        final ServerSubLevel subLevel = findOrLoadSubLevelById(source.getLevel(), sableId);
        if (subLevel == null || subLevel.isRemoved()) {
            sendBodyLookupFailure(source, state, slotA, marker);
            return null;
        }
        final SubLevelPhysicsSystem physicsSystem = SableCommandHelper.requireSubLevelPhysicsSystem(ctx);
        final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        if (handle == null || !handle.isValid()) {
            send(source, marker + " status=FAIL family=" + fixtureName(family)
                    + " slot=" + (slotA ? "A" : "B")
                    + " reason=missingRigidBodyHandle sableId=" + sableId);
            return null;
        }
        return new FixtureBodyTarget(family, slotA ? "A" : "B", subLevel, handle);
    }

    private static void sendBodyLookupFailure(final CommandSourceStack source, final FixtureState state,
                                              final boolean slotA, final String marker) {
        final UUID sableId = slotA ? state.bodyASableId : state.bodyBSableId;
        final int lastKnownHandle = slotA ? state.bodyALastKnownHandle : state.bodyBLastKnownHandle;
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(source.getLevel());
        final ServerSubLevel active;
        if (container != null && sableId != null
                && container.getSubLevel(sableId) instanceof final ServerSubLevel subLevel) {
            active = subLevel;
        } else {
            active = null;
        }
        final boolean held = container != null && sableId != null
                && container.getHoldingChunkMap().getHoldingSubLevel(sableId) != null;
        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(source.getLevel());
        final RigidBodyHandle handle = active == null || physicsSystem == null
                ? null
                : physicsSystem.getPhysicsHandle(active);
        final int currentHandle = active == null ? -1 : active.getRuntimeId();
        final boolean rapierBodyPresent = active != null && physicsSystem != null
                && physicsSystem.getPipeline().isBodyRegistered(active);
        final String reason = held ? "body_unloaded" : "body_removed";
        send(source, marker + " status=FAIL family=" + fixtureName(state.family())
                + " slot=" + (slotA ? "A" : "B")
                + " reason=" + reason
                + " savedSableId=" + sableId
                + " sableContainerContainsUuid=" + (active != null)
                + " subLevelExists=" + (active != null)
                + " holdingStorageContainsUuid=" + held
                + " currentBodyHandle=" + (currentHandle < 0 ? "unavailable" : currentHandle)
                + " bodyHandleValid=" + (handle != null && handle.isValid())
                + " rapierBodyPresent=" + rapierBodyPresent
                + " lastKnownBodyHandle=" + (lastKnownHandle < 0 ? "unavailable" : lastKnownHandle)
                + " bodyHandleRecreated=" + (currentHandle >= 0 && lastKnownHandle >= 0 && currentHandle != lastKnownHandle)
                + " lastRemovalReason=unavailable"
                + " lastRemovalOwner=unavailable");
    }

    private static boolean isPhysicalFixtureFamily(final M24Family family) {
        return family == M24Family.TORSION_SPRING
                || family == M24Family.SWIVEL_BEARING
                || family == M24Family.ROPE_CONNECTOR
                || family == M24Family.ROPE_WINCH
                || family == M24Family.DOCKING_CONNECTOR;
    }

    private static FixtureValidation validateM24FixtureBodies(final CommandSourceStack source,
                                                              final FixtureState state,
                                                              final boolean emitLifecycle) {
        final ServerLevel level = source.getLevel();
        final BodySummary bodyA = summarizeBody(level, "A", state.bodyASableId, state.bodyAAssembler(),
                state.bodyAEndpoint(), state.bodyAComponent());
        final BodySummary bodyB = summarizeBody(level, "B", state.bodyBSableId, state.bodyBAssembler(),
                state.bodyBEndpoint(), state.bodyBComponent());
        if (bodyA.subLevel() != null) {
            state.bodyASableId = bodyA.subLevel().getUniqueId();
        }
        if (bodyB.subLevel() != null) {
            state.bodyBSableId = bodyB.subLevel().getUniqueId();
        }
        if (emitLifecycle) {
            logBodyLifecycle(source, state.family(), bodyA);
            logBodyLifecycle(source, state.family(), bodyB);
        }
        final boolean singleBody = state.expectedBodies() == 1;
        final boolean sameSable = !singleBody && bodyA.subLevel() != null && bodyA.subLevel() == bodyB.subLevel();
        final boolean valid = bodyA.valid(state.expectedBlocksPerBody())
                && (singleBody || bodyB.valid(state.expectedBlocksPerBody()))
                && !sameSable;
        final String status;
        final String reason;
        if (valid) {
            status = "PASS";
            reason = "ok";
        } else if (sameSable) {
            status = "FAIL";
            reason = "same_sable";
        } else if (singleBody && bodyA.subLevel() == null
                && bodyA.parentAssemblerPresent() && bodyA.parentEndpointPresent()) {
            status = "WAITING";
            reason = "body_A_not_assembled_yet";
        } else if (bodyA.subLevel() == null && bodyB.subLevel() == null
                && state.parentFixturePresent(level, bodyA, bodyB)) {
            status = "WAITING";
            reason = "bodies_not_assembled_yet";
        } else if (bodyA.subLevel() == null || bodyB.subLevel() == null) {
            status = "PARTIAL";
            if (bodyA.subLevel() == null && bodyA.knownSableId() != null) {
                reason = "body_A_" + bodyA.classification().toLowerCase(Locale.ROOT);
            } else if (bodyB.subLevel() == null && bodyB.knownSableId() != null) {
                reason = "body_B_" + bodyB.classification().toLowerCase(Locale.ROOT);
            } else {
                reason = bodyA.subLevel() == null ? "body_A_not_assembled_or_unresolved" : "body_B_not_assembled_or_unresolved";
            }
        } else if (!bodyA.valid(state.expectedBlocksPerBody())) {
            status = "FAIL";
            reason = "invalid_body_A";
        } else {
            status = "FAIL";
            reason = "invalid_body_B";
        }
        state.updateLifecycle(bodyA.subLevel() != null, !singleBody && bodyB.subLevel() != null, valid);
        send(source, "SABLE_M24_BODIES status=" + status
                + " family=" + state.family().id()
                + " lifecycle=" + state.lifecycleName()
                + " expectedBodies=" + state.expectedBodies()
                + " bodyASableId=" + bodyA.sableId()
                + " bodyBSableId=" + (singleBody ? "not_required" : bodyB.sableId())
                + " bodyAPresent=" + (bodyA.subLevel() != null)
                + " bodyBPresent=" + (singleBody ? "not_required" : bodyB.subLevel() != null)
                + " bodyAAssemblerParentPresent=" + bodyA.parentAssemblerPresent()
                + " bodyBAssemblerParentPresent=" + (singleBody ? "not_required" : bodyB.parentAssemblerPresent())
                + " bodyAEndpointParentPresent=" + bodyA.parentEndpointPresent()
                + " bodyBEndpointParentPresent=" + (singleBody ? "not_required" : bodyB.parentEndpointPresent())
                + " sameSable=" + sameSable
                + " bodyAStoredBlockCount=" + bodyA.storedBlockCount()
                + " bodyBStoredBlockCount=" + (singleBody ? "not_required" : bodyB.storedBlockCount())
                + " bodyAAssemblerPresent=" + bodyA.assemblerPresent()
                + " bodyBAssemblerPresent=" + (singleBody ? "not_required" : bodyB.assemblerPresent())
                + " bodyAEndpointPresent=" + bodyA.endpointPresent()
                + " bodyBEndpointPresent=" + (singleBody ? "not_required" : bodyB.endpointPresent())
                + " bodyABodyRegistered=" + bodyA.bodyRegistered()
                + " bodyBBodyRegistered=" + (singleBody ? "not_required" : bodyB.bodyRegistered())
                + " bodyACollisionGeometryPresent=" + bodyA.collisionGeometryPresent()
                + " bodyBCollisionGeometryPresent=" + (singleBody ? "not_required" : bodyB.collisionGeometryPresent())
                + " bodyACollisionDiagnosticStatus=" + bodyA.collisionDiagnosticStatus()
                + " bodyBCollisionDiagnosticStatus=" + (singleBody ? "not_required" : bodyB.collisionDiagnosticStatus())
                + " reason=" + reason);
        return new FixtureValidation(valid, status, reason, bodyA, bodyB);
    }

    private static BodySummary summarizeBody(final ServerLevel level, final String slot,
                                             final UUID knownSableId,
                                             final BlockPos assemblerVisible, final BlockPos endpointVisible,
                                             final Block endpointBlock) {
        final boolean parentAssemblerPresent = level.getBlockState(assemblerVisible)
                .is(SimulatedBlocks.PHYSICS_ASSEMBLER.get());
        final boolean parentEndpointPresent = level.getBlockState(endpointVisible).is(endpointBlock);
        ServerSubLevel subLevel = knownSableId == null ? null : findOrLoadSubLevelById(level, knownSableId);
        if (subLevel == null && knownSableId != null) {
            final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            final boolean held = container != null
                    && container.getHoldingChunkMap().getHoldingSubLevel(knownSableId) != null;
            return BodySummary.missing(slot, assemblerVisible, endpointVisible, knownSableId,
                    parentAssemblerPresent, parentEndpointPresent,
                    held ? "UNLOADED_TO_HOLDING_STORAGE" : "REMOVED_AFTER_CONSTRAINT_CREATE");
        }
        if (subLevel == null) {
            subLevel = findSubLevelWithVisibleBlock(level, assemblerVisible,
                    blockState -> blockState.is(SimulatedBlocks.PHYSICS_ASSEMBLER.get()));
        }
        if (subLevel == null) {
            return BodySummary.missing(slot, assemblerVisible, endpointVisible, knownSableId,
                    parentAssemblerPresent, parentEndpointPresent, "NO_SABLE_CREATED_OR_NOT_DISCOVERED");
        }
        final List<BlockPos> blocks = SimAssemblyHelper.collectBlocks(level, subLevel);
        BlockPos assemblerRaw = findRawVisibleBlock(level, subLevel, assemblerVisible,
                blockState -> blockState.is(SimulatedBlocks.PHYSICS_ASSEMBLER.get()));
        BlockPos endpointRaw = findRawVisibleBlock(level, subLevel, endpointVisible,
                blockState -> blockState.is(endpointBlock));
        if (assemblerRaw == null) {
            assemblerRaw = findRawBlock(level, subLevel, blockState -> blockState.is(SimulatedBlocks.PHYSICS_ASSEMBLER.get()));
        }
        if (endpointRaw == null) {
            endpointRaw = findRawBlock(level, subLevel, blockState -> blockState.is(endpointBlock));
        }
        final int blockEntityCount = SimAssemblyHelper.countBlockEntities(level, blocks);
        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
        final RigidBodyHandle handle = physicsSystem == null ? null : physicsSystem.getPhysicsHandle(subLevel);
        final boolean bodyRegistered = physicsSystem != null && physicsSystem.getPipeline().isBodyRegistered(subLevel);
        final boolean collisionGeometryPresent = physicsSystem != null && physicsSystem.hasUploadedCollisionGeometry(subLevel);
        final int collisionUploadedBlocks = physicsSystem == null ? -1 : physicsSystem.getUploadedCollisionBlockCount(subLevel);
        final M24PhysicalBlockEntity endpointBlockEntity = endpointRaw != null
                && level.getBlockEntity(endpointRaw) instanceof final M24PhysicalBlockEntity component
                        ? component
                        : null;
        final String linearVelocity = handle == null ? "unresolved" : handle.getLinearVelocity(new org.joml.Vector3d()).toString();
        final String angularVelocity = handle == null ? "unresolved" : handle.getAngularVelocity(new org.joml.Vector3d()).toString();
        final String activeConstraintIds = endpointBlockEntity != null && endpointBlockEntity.simulated$hasActiveConstraint()
                ? endpointBlockEntity.simulated$getLogicalConstraintId()
                : "[]";
        final String classification;
        if (blocks.isEmpty()) {
            classification = "SABLE_CREATED_ZERO_BLOCKS";
        } else if (endpointRaw == null || assemblerRaw == null) {
            classification = "SERVER_BODY_MISSING_EXPECTED_BLOCK";
        } else if (!bodyRegistered || handle == null || !handle.isValid()) {
            classification = "PHYSICS_BODY_MISSING";
        } else if (!collisionGeometryPresent) {
            classification = "COLLISION_UPLOAD_COUNTER_MISSING_RUNTIME_CHECK_REQUIRED";
        } else {
            classification = "SERVER_BODY_READY_CLIENT_RUNTIME_REQUIRED";
        }
        final String collisionDiagnosticStatus = collisionGeometryPresent
                ? "UPLOADED_COLLISION_GEOMETRY_PRESENT"
                : bodyRegistered && handle != null && handle.isValid()
                        ? "UPLOAD_COUNTER_FALSE_BODY_HANDLE_PRESENT"
                        : "NO_AUTHORITATIVE_BODY_HANDLE";
        return new BodySummary(slot, assemblerVisible, endpointVisible, knownSableId, subLevel, assemblerRaw, endpointRaw,
                blocks.size(), blockEntityCount, assemblerRaw != null, endpointRaw != null, bodyRegistered,
                collisionGeometryPresent, collisionUploadedBlocks, handle != null && handle.isValid(),
                endpointBlockEntity, linearVelocity, angularVelocity, activeConstraintIds, classification,
                collisionDiagnosticStatus, parentAssemblerPresent, parentEndpointPresent);
    }

    private static void logBodyLifecycle(final CommandSourceStack source, final M24Family family,
                                         final BodySummary summary) {
        send(source, "SABLE_M24_BODY_LIFECYCLE"
                + " family=" + family.id()
                + " slot=" + summary.slot()
                + " phase=AFTER_ASSEMBLY"
                + " assemblerParentPos=" + summary.assemblerVisible().toShortString()
                + " assemblyResult=" + summary.classification()
                + " sableId=" + summary.sableId()
                + " subLevelPresent=" + (summary.subLevel() != null)
                + " storedBlockCount=" + summary.storedBlockCount()
                + " nonAirBlockCount=" + summary.storedBlockCount()
                + " blockEntityCount=" + summary.blockEntityCount()
                + " assemblerPresent=" + summary.assemblerPresent()
                + " endpointBlockPresent=" + summary.endpointPresent()
                + " logicalPose=" + (summary.subLevel() == null ? "null" : summary.subLevel().logicalPose())
                + " rawBounds=" + (summary.subLevel() == null ? "null" : summary.subLevel().getPlot().getBoundingBox())
                + " bodyHandleValid=" + summary.bodyHandleValid()
                + " bodyRegistered=" + summary.bodyRegistered()
                + " visiblePosition=" + (summary.subLevel() == null ? "null" : summary.subLevel().logicalPose().position())
                + " linearVelocity=" + summary.linearVelocity()
                + " angularVelocity=" + summary.angularVelocity()
                + " activeConstraintIds=" + summary.activeConstraintIds()
                + " mass=" + (summary.subLevel() == null ? 0.0D : summary.subLevel().getMassTracker().getMass())
                + " collisionGeometryPresent=" + summary.collisionGeometryPresent()
                + " collisionUploadedBlocks=" + summary.collisionUploadedBlocks()
                + " collisionDiagnosticStatus=" + summary.collisionDiagnosticStatus()
                + " clientSubLevelKnown=runtime_required"
                + " clientStoredBlocks=runtime_required"
                + " renderBoundsValid=runtime_required");
    }

    private static M24PhysicalBlockEntity findNearestComponent(final CommandSourceStack source, final M24Family family) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(source.getLevel());
        if (container == null) {
            return null;
        }
        M24PhysicalBlockEntity nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            for (final BlockPos block : SimAssemblyHelper.collectBlocks(source.getLevel(), subLevel)) {
                if (source.getLevel().getBlockEntity(block) instanceof final M24PhysicalBlockEntity component
                        && component.family() == family) {
                    final double distance = subLevel.logicalPose().position().distanceSquared(
                            source.getPosition().x, source.getPosition().y, source.getPosition().z);
                    if (distance < bestDistance) {
                        nearest = component;
                        bestDistance = distance;
                    }
                }
            }
        }
        return nearest;
    }

    private static ServerSubLevel findSubLevelWithVisibleBlock(final ServerLevel level, final BlockPos visible,
                                                               final Predicate<BlockState> predicate) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (findRawVisibleBlock(level, subLevel, visible, predicate) != null) {
                return subLevel;
            }
        }
        return null;
    }

    private static ServerSubLevel findSubLevelById(final ServerLevel level, final UUID id) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        return container.getSubLevel(id) instanceof final ServerSubLevel subLevel ? subLevel : null;
    }

    private static ServerSubLevel findOrLoadSubLevelById(final ServerLevel level, final UUID id) {
        final ServerSubLevel active = findSubLevelById(level, id);
        if (active != null) {
            return active;
        }
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        final dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel held =
                container.getHoldingChunkMap().getHoldingSubLevel(id);
        if (held == null) {
            return null;
        }
        container.getHoldingChunkMap().loadHoldingSubLevel(held);
        return findSubLevelById(level, id);
    }

    private static BlockPos findRawVisibleBlock(final ServerLevel level, final ServerSubLevel subLevel,
                                                final BlockPos visible, final Predicate<BlockState> predicate) {
        for (final BlockPos block : SimAssemblyHelper.collectBlocks(level, subLevel)) {
            if (predicate.test(level.getBlockState(block))
                    && visibleBlockPos(subLevel, block).distManhattan(visible) <= 1) {
                return block;
            }
        }
        return null;
    }

    private static BlockPos findRawBlock(final ServerLevel level, final ServerSubLevel subLevel,
                                         final Predicate<BlockState> predicate) {
        for (final BlockPos block : SimAssemblyHelper.collectBlocks(level, subLevel)) {
            if (predicate.test(level.getBlockState(block))) {
                return block;
            }
        }
        return null;
    }

    private static Set<BlockPos> buildSupport(final ServerLevel level, final BlockPos origin) {
        final Set<BlockPos> platform = new HashSet<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                final BlockPos pos = origin.offset(x, -1, z);
                platform.add(pos);
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            }
        }
        return platform;
    }

    private static Set<BlockPos> buildBody(final ServerLevel level, final BlockPos origin,
                                           final RegistryObject<Block> component, final Direction componentDirection) {
        final Set<BlockPos> positions = new HashSet<>();
        level.setBlock(origin, Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        positions.add(origin);
        level.setBlock(origin.above(), SimulatedBlocks.PHYSICS_ASSEMBLER.get().defaultBlockState(), 3);
        positions.add(origin.above());
        level.setBlock(origin.relative(componentDirection), component.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, componentDirection), 3);
        positions.add(origin.relative(componentDirection));
        level.setBlock(origin.relative(componentDirection.getOpposite()), Blocks.COPPER_BLOCK.defaultBlockState(), 3);
        positions.add(origin.relative(componentDirection.getOpposite()));
        level.setBlock(origin.north(), Blocks.AMETHYST_BLOCK.defaultBlockState(), 3);
        positions.add(origin.north());
        level.setBlock(origin.south(), Blocks.CALCITE.defaultBlockState(), 3);
        positions.add(origin.south());
        glue(level, origin, origin.above());
        glue(level, origin, origin.east());
        glue(level, origin, origin.west());
        glue(level, origin, origin.north());
        glue(level, origin, origin.south());
        return positions;
    }

    private static BlockPos placeFixtureKineticSource(final ServerLevel level, final M24Family family,
                                                       final BlockPos bodyA) {
        if (family != M24Family.ROPE_WINCH && family != M24Family.TORSION_SPRING) {
            return null;
        }
        final Block motor = ForgeRegistries.BLOCKS.getValue(CREATIVE_MOTOR_ID);
        if (motor == null || motor == Blocks.AIR) {
            throw new IllegalStateException("missing_create_creative_motor");
        }
        final BlockPos motorPos = family == M24Family.ROPE_WINCH ? bodyA.north() : bodyA;
        final Direction facing = family == M24Family.ROPE_WINCH ? Direction.SOUTH : Direction.EAST;
        if (family == M24Family.ROPE_WINCH) {
            final BlockPos oldWinchPos = bodyA.east();
            BlockState winchState = level.getBlockState(oldWinchPos);
            if (winchState.hasProperty(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE)) {
                winchState = winchState.setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, false);
            }
            level.setBlock(bodyA, winchState, 3);
            level.setBlock(oldWinchPos, Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        }
        BlockState motorState = motor.defaultBlockState();
        if (!motorState.hasProperty(DirectionalBlock.FACING)) {
            throw new IllegalStateException("creative_motor_missing_facing_property");
        }
        motorState = motorState.setValue(DirectionalBlock.FACING, facing);
        level.setBlock(motorPos, motorState, 3);
        if (family == M24Family.TORSION_SPRING) {
            final Block shaft = ForgeRegistries.BLOCKS.getValue(SHAFT_ID);
            if (shaft == null || shaft == Blocks.AIR) {
                throw new IllegalStateException("missing_create_shaft");
            }
            level.setBlock(bodyA.west(), Blocks.AIR.defaultBlockState(), 3);
            BlockState shaftState = shaft.defaultBlockState();
            if (shaftState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)) {
                shaftState = shaftState.setValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
                        Direction.Axis.X);
            }
            level.setBlock(bodyA.east(2), shaftState, 3);
            glue(level, bodyA.east(), bodyA.east(2));
        }
        return motorPos;
    }

    private static void configureDockingFixturePower(final ServerLevel level, final BlockPos body,
                                                     final Direction componentDirection,
                                                     final Set<BlockPos> parentBlocks) {
        final BlockPos replacedDecoration = body.north();
        final BlockPos component = body.relative(componentDirection);
        final BlockPos source = component.above();
        level.setBlock(replacedDecoration, Blocks.AIR.defaultBlockState(), 3);
        parentBlocks.remove(replacedDecoration);
        level.setBlock(source, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        level.updateNeighborsAt(source, Blocks.REDSTONE_BLOCK);
        parentBlocks.add(source);
        glue(level, component, source);
    }

    private static SelectionPreview previewAssemblySelection(final ServerLevel level, final BlockPos start,
                                                             final Set<BlockPos> platform,
                                                             final BlockPos assembler,
                                                             final BlockPos endpoint) {
        final SimAssemblyContraption contraption = new SimAssemblyContraption(null);
        try {
            contraption.searchMovedStructure(level, start);
        } catch (final AssemblyException exception) {
            return new SelectionPreview(0, platform.size(), 0, false, false,
                    "failed:" + exception.getMessage(), "failed");
        }
        int selectedPlatformBlocks = 0;
        for (final BlockPos selected : contraption.getBlocks()) {
            if (platform.contains(selected)) {
                selectedPlatformBlocks++;
            }
        }
        return new SelectionPreview(contraption.getBlocks().size(), platform.size(), selectedPlatformBlocks,
                contraption.getBlocks().contains(assembler), contraption.getBlocks().contains(endpoint),
                describeBounds(contraption.getBlocks()), selectionDigest(level, contraption.getBlocks()));
    }

    private static void logSelection(final CommandSourceStack source, final M24Family family, final String slot,
                                     final BlockPos start, final SelectionPreview preview) {
        send(source, "SABLE_M24_ASSEMBLY_SELECTION"
                + " family=" + family.id()
                + " slot=" + slot
                + " startPos=" + start.toShortString()
                + " selectedBlockCount=" + preview.selectedBlockCount()
                + " platformBlockCount=" + preview.platformBlockCount()
                + " selectedPlatformBlocks=" + preview.selectedPlatformBlocks()
                + " assemblerIncluded=" + preview.assemblerIncluded()
                + " endpointIncluded=" + preview.endpointIncluded()
                + " selectionBounds=" + preview.selectionBounds()
                + " selectionSha256=" + preview.selectionSha256());
    }

    private static RegistryObject<Block> blockForPrimary(final M24Family family) {
        return switch (family) {
            case TORSION_SPRING -> SimulatedBlocks.TORSION_SPRING;
            case SWIVEL_BEARING -> SimulatedBlocks.SWIVEL_BEARING;
            case ROPE_WINCH -> SimulatedBlocks.ROPE_WINCH;
            case DOCKING_CONNECTOR -> SimulatedBlocks.DOCKING_CONNECTOR;
            default -> SimulatedBlocks.ROPE_CONNECTOR;
        };
    }

    private static int bodySpacing(final M24Family family) {
        return switch (family) {
            case SWIVEL_BEARING -> 4;
            case DOCKING_CONNECTOR -> 5;
            default -> 7;
        };
    }

    private static RegistryObject<Block> blockForSecondary(final M24Family family) {
        return switch (family) {
            case SWIVEL_BEARING -> SimulatedBlocks.SWIVEL_BEARING_LINK_BLOCK;
            case ROPE_WINCH -> SimulatedBlocks.ROPE_CONNECTOR;
            case DOCKING_CONNECTOR -> SimulatedBlocks.PAIRED_DOCKING_CONNECTOR;
            case TORSION_SPRING -> SimulatedBlocks.TORSION_SPRING;
            default -> SimulatedBlocks.ROPE_CONNECTOR;
        };
    }

    private static int backendRotaryCanary(final CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final ServerLevel level = source.getLevel();
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(ctx);
        final SubLevelPhysicsSystem physicsSystem = SableCommandHelper.requireSubLevelPhysicsSystem(container);
        final Vec3 playerPos = Vec3.atCenterOf(BlockPos.containing(source.getPosition()));
        final ServerSubLevel bodyA = createRotaryCanaryBody(container, playerPos);
        final ServerSubLevel bodyB = createRotaryCanaryBody(container, playerPos.add(4.0D, 0.0D, 0.0D));
        physicsSystem.finalizeExistingSubLevelStorage(bodyA, true, "m24_rotary_canary_A");
        physicsSystem.finalizeExistingSubLevelStorage(bodyB, true, "m24_rotary_canary_B");
        final Vector3d anchorA = JOMLConversion.atBottomCenterOf(bodyA.getPlot().getCenterBlock().east(2));
        final Vector3d anchorB = JOMLConversion.atBottomCenterOf(bodyB.getPlot().getCenterBlock().west(2));
        final Vec3 visibleA = bodyA.logicalPose().transformPosition(new Vec3(anchorA.x, anchorA.y, anchorA.z));
        final Vec3 visibleB = bodyB.logicalPose().transformPosition(new Vec3(anchorB.x, anchorB.y, anchorB.z));
        final Vector3d axisA = JOMLConversion.atLowerCornerOf(Direction.EAST.getNormal());
        final Vector3d axisB = JOMLConversion.atLowerCornerOf(Direction.WEST.getNormal());
        final RotaryConstraintConfiguration config = new RotaryConstraintConfiguration(anchorA, anchorB, axisA, axisB);
        final PhysicsConstraintHandle handle = physicsSystem.getPipeline().addConstraint(bodyA, bodyB, config);
        if (handle != null) {
            handle.setContactsEnabled(false);
        }
        LAST_ROTARY_CANARIES.put(fixtureKey(level),
                new RotaryCanaryState(bodyA.getUniqueId(), bodyB.getUniqueId(), handle));
        send(source, "SABLE_M24_ROTARY_CANARY status=" + (handle != null && handle.isValid() ? "PASS" : "FAIL")
                + " phase=POST_HANDLE_CREATE"
                + " bodyA=" + bodyA.getUniqueId()
                + " bodyB=" + bodyB.getUniqueId()
                + " bodyAPose=" + bodyA.logicalPose().position()
                + " bodyBPose=" + bodyB.logicalPose().position()
                + " rawAnchorA=" + anchorA
                + " rawAnchorB=" + anchorB
                + " rapierLocalAnchorA=" + rawMinusCenterOfMass(bodyA, anchorA)
                + " rapierLocalAnchorB=" + rawMinusCenterOfMass(bodyB, anchorB)
                + " rapierLocalAxisA=" + axisA
                + " rapierLocalAxisB=" + axisB
                + " visibleAnchorA=" + visibleA
                + " visibleAnchorB=" + visibleB
                + " visibleAnchorDistance=" + visibleA.distanceTo(visibleB)
                + " axisA=" + axisA
                + " axisB=" + axisB
                + " backendHandleValid=" + (handle != null && handle.isValid())
                + " containsSimulatedM24BlockEntity=false");
        return handle != null && handle.isValid() ? 1 : 0;
    }

    private static int inspectBackendRotaryCanary(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final RotaryCanaryState canary = LAST_ROTARY_CANARIES.get(fixtureKey(source.getLevel()));
        if (canary == null) {
            send(source, "SABLE_M24_ROTARY_CANARY status=FAIL phase=INSPECT reason=no_rotary_canary_created");
            return 0;
        }
        final ServerSubLevel bodyA = findSubLevelById(source.getLevel(), canary.bodyA());
        final ServerSubLevel bodyB = findSubLevelById(source.getLevel(), canary.bodyB());
        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(source.getLevel());
        final RigidBodyHandle handleA = bodyA == null || physicsSystem == null ? null : physicsSystem.getPhysicsHandle(bodyA);
        final RigidBodyHandle handleB = bodyB == null || physicsSystem == null ? null : physicsSystem.getPhysicsHandle(bodyB);
        final boolean valid = bodyA != null && bodyB != null
                && handleA != null && handleA.isValid()
                && handleB != null && handleB.isValid()
                && canary.handle() != null && canary.handle().isValid();
        send(source, "SABLE_M24_ROTARY_CANARY status=" + (valid ? "PASS" : "FAIL")
                + " phase=INSPECT"
                + " bodyA=" + canary.bodyA()
                + " bodyB=" + canary.bodyB()
                + " bodyAPresent=" + (bodyA != null)
                + " bodyBPresent=" + (bodyB != null)
                + " bodyAPose=" + (bodyA == null ? "missing" : bodyA.logicalPose().position())
                + " bodyBPose=" + (bodyB == null ? "missing" : bodyB.logicalPose().position())
                + " bodyAHandleValid=" + (handleA != null && handleA.isValid())
                + " bodyBHandleValid=" + (handleB != null && handleB.isValid())
                + " backendHandleValid=" + (canary.handle() != null && canary.handle().isValid())
                + " bodyALinearVelocity=" + (handleA == null ? "unresolved" : handleA.getLinearVelocity(new Vector3d()))
                + " bodyBLinearVelocity=" + (handleB == null ? "unresolved" : handleB.getLinearVelocity(new Vector3d())));
        return valid ? 1 : 0;
    }

    private static int backendRotaryRealBodyCanary(final CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final ServerLevel level = source.getLevel();
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(ctx);
        final SubLevelPhysicsSystem physicsSystem = SableCommandHelper.requireSubLevelPhysicsSystem(container);
        final Vec3 playerPos = Vec3.atCenterOf(BlockPos.containing(source.getPosition()));
        final ServerSubLevel bodyA = createRotaryRealBodyCanaryBody(container, playerPos, Direction.EAST);
        final ServerSubLevel bodyB = createRotaryRealBodyCanaryBody(container, playerPos.add(4.0D, 0.0D, 0.0D), Direction.WEST);
        physicsSystem.finalizeExistingSubLevelStorage(bodyA, true, "m24_rotary_realbody_canary_A");
        physicsSystem.finalizeExistingSubLevelStorage(bodyB, true, "m24_rotary_realbody_canary_B");
        final BlockPos centerA = bodyA.getPlot().getCenterBlock();
        final BlockPos centerB = bodyB.getPlot().getCenterBlock();
        final Vector3d anchorA = JOMLConversion.atCenterOf(centerA.east(2));
        final Vector3d anchorB = JOMLConversion.atCenterOf(centerB.west(2)).add(0.001D, 0.0D, 0.0D);
        final Vec3 visibleA = bodyA.logicalPose().transformPosition(new Vec3(anchorA.x, anchorA.y, anchorA.z));
        final Vec3 visibleB = bodyB.logicalPose().transformPosition(new Vec3(anchorB.x, anchorB.y, anchorB.z));
        final Vector3d axisA = JOMLConversion.atLowerCornerOf(Direction.EAST.getNormal());
        final Vector3d axisB = JOMLConversion.atLowerCornerOf(Direction.WEST.getNormal());
        final RigidBodyHandle handleA = physicsSystem.getPhysicsHandle(bodyA);
        final RigidBodyHandle handleB = physicsSystem.getPhysicsHandle(bodyB);
        final RotaryConstraintConfiguration config = new RotaryConstraintConfiguration(anchorA, anchorB, axisA, axisB);
        send(source, "SABLE_M24_ROTARY_REALBODY_CANARY status=PASS phase=PRE_INSERT"
                + " bodyA=" + bodyA.getUniqueId()
                + " bodyB=" + bodyB.getUniqueId()
                + " bodyAPose=" + bodyA.logicalPose().position()
                + " bodyBPose=" + bodyB.logicalPose().position()
                + " bodyAMass=" + bodyA.getMassTracker().getMass()
                + " bodyBMass=" + bodyB.getMassTracker().getMass()
                + " bodyAInertia=" + bodyA.getMassTracker().getInertiaTensor()
                + " bodyBInertia=" + bodyB.getMassTracker().getInertiaTensor()
                + " rawAnchorA=" + anchorA
                + " rawAnchorB=" + anchorB
                + " localAnchorA=" + rawMinusCenterOfMass(bodyA, anchorA)
                + " localAnchorB=" + rawMinusCenterOfMass(bodyB, anchorB)
                + " axisA=" + axisA
                + " axisB=" + axisB
                + " visibleAnchorA=" + visibleA
                + " visibleAnchorB=" + visibleB
                + " visibleAnchorDistance=" + visibleA.distanceTo(visibleB)
                + " bodyALinearVelocity=" + (handleA == null ? "unresolved" : handleA.getLinearVelocity(new Vector3d()))
                + " bodyBLinearVelocity=" + (handleB == null ? "unresolved" : handleB.getLinearVelocity(new Vector3d()))
                + " bodyAAngularVelocity=" + (handleA == null ? "unresolved" : handleA.getAngularVelocity(new Vector3d()))
                + " bodyBAngularVelocity=" + (handleB == null ? "unresolved" : handleB.getAngularVelocity(new Vector3d()))
                + " containsSimulatedM24BlockEntity=false");
        final Vector3d localAnchorA = rawMinusCenterOfMass(bodyA, anchorA);
        final Vector3d localAnchorB = rawMinusCenterOfMass(bodyB, anchorB);
        final String canarySessionId = UUID.randomUUID().toString();
        RotaryPipelineTraceRegistry.register(level, canarySessionId, bodyA.getUniqueId(), bodyB.getUniqueId(),
                bodyA.getRuntimeId(), bodyB.getRuntimeId(), -1L,
                localAnchorA, localAnchorB, axisA, axisB);
        final PhysicsConstraintHandle handle = physicsSystem.getPipeline().addConstraint(bodyA, bodyB, config);
        if (handle != null) {
            handle.setContactsEnabled(false);
        }
        LAST_ROTARY_REALBODY_CANARIES.put(fixtureKey(level),
                new RotaryCanaryState(bodyA.getUniqueId(), bodyB.getUniqueId(), handle));
        final RotaryPipelineTraceRegistry.TraceState trace = RotaryPipelineTraceRegistry.get(level);
        final RotaryPipelineTraceRegistry.LifecycleSnapshot postInsert = trace == null ? null
                : trace.lifecycleSnapshot(RotaryPipelineTraceRegistry.POST_JOINT_INSERT);
        send(source, "SABLE_M24_ROTARY_REALBODY_CANARY status=" + (handle != null && handle.isValid() ? "PASS" : "FAIL")
                + " phase=POST_INSERT_PRE_STEP"
                + " bodyA=" + bodyA.getUniqueId()
                + " bodyB=" + bodyB.getUniqueId()
                + " bodyAPose=" + bodyA.logicalPose().position()
                + " bodyBPose=" + bodyB.logicalPose().position()
                + " canarySessionId=" + canarySessionId
                + " traceCorrelation=body_handles"
                + " sableBodyAPresent=" + (bodyA != null && !bodyA.isRemoved())
                + " sableBodyBPresent=" + (bodyB != null && !bodyB.isRemoved())
                + " rapierBodyAPresent=" + (postInsert != null && postInsert.rigidBodySetContainsA())
                + " rapierBodyBPresent=" + (postInsert != null && postInsert.rigidBodySetContainsB())
                + " rigidBodySetContainsA=" + (postInsert != null && postInsert.rigidBodySetContainsA())
                + " rigidBodySetContainsB=" + (postInsert != null && postInsert.rigidBodySetContainsB())
                + " nativeBodyHandleA=" + (postInsert == null ? "unavailable" : postInsert.nativeBodyHandleA())
                + " nativeBodyHandleB=" + (postInsert == null ? "unavailable" : postInsert.nativeBodyHandleB())
                + " javaConstraintHandlePresent=" + (handle != null)
                + " rapierJointSetContainsHandle=" + (postInsert != null && postInsert.rapierJointSetContainsHandle())
                + " backendHandleValid=" + (handle != null && handle.isValid())
                + " finite=" + (finite(bodyA.logicalPose().position()) && finite(bodyB.logicalPose().position()))
                + " expectedFirstFailureBoundary=POST_STEP_1_IF_SOLVER_OR_SYNC_CORRUPTS");
        return handle != null && handle.isValid() ? 1 : 0;
    }

    private static int inspectBackendRotaryRealBodyCanary(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final RotaryCanaryState canary = LAST_ROTARY_REALBODY_CANARIES.get(fixtureKey(source.getLevel()));
        if (canary == null) {
            send(source, "SABLE_M24_ROTARY_REALBODY_CANARY status=FAIL phase=INSPECT reason=no_rotary_realbody_canary_created");
            return 0;
        }
        final ServerSubLevel bodyA = findSubLevelById(source.getLevel(), canary.bodyA());
        final ServerSubLevel bodyB = findSubLevelById(source.getLevel(), canary.bodyB());
        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(source.getLevel());
        final RigidBodyHandle handleA = bodyA == null || physicsSystem == null ? null : physicsSystem.getPhysicsHandle(bodyA);
        final RigidBodyHandle handleB = bodyB == null || physicsSystem == null ? null : physicsSystem.getPhysicsHandle(bodyB);
        final boolean finite = bodyA != null && bodyB != null
                && finite(bodyA.logicalPose().position())
                && finite(bodyB.logicalPose().position())
                && (handleA == null || (finite(handleA.getLinearVelocity(new Vector3d())) && finite(handleA.getAngularVelocity(new Vector3d()))))
                && (handleB == null || (finite(handleB.getLinearVelocity(new Vector3d())) && finite(handleB.getAngularVelocity(new Vector3d()))));
        final boolean valid = bodyA != null && bodyB != null
                && handleA != null && handleA.isValid()
                && handleB != null && handleB.isValid()
                && canary.handle() != null && canary.handle().isValid()
                && finite;
        final RotaryPipelineTraceRegistry.TraceState trace = RotaryPipelineTraceRegistry.get(source.getLevel());
        final boolean traceMatchesCanary = trace != null
                && trace.bodyAUuid().equals(canary.bodyA())
                && trace.bodyBUuid().equals(canary.bodyB());
        final boolean traceComplete = traceMatchesCanary && trace.traceComplete();
        final String capturedPhases = traceMatchesCanary ? trace.capturedPhases().toString() : "[]";
        final String missingPhases = traceMatchesCanary
                ? trace.missingPhases().toString()
                : "[PRE_SOLVER, POST_RAPIER_SOLVER, POST_SABLE_SYNC_PRE_CLEANUP]";
        final String classification = traceMatchesCanary
                ? trace.firstFailureClassification()
                : "TRACE_INCOMPLETE";
        send(source, "SABLE_M24_ROTARY_REALBODY_CANARY status=" + (valid ? "PASS" : "FAIL")
                + " phase=POST_STEP_INSPECT"
                + " bodyA=" + canary.bodyA()
                + " bodyB=" + canary.bodyB()
                + " bodyAPresent=" + (bodyA != null)
                + " bodyBPresent=" + (bodyB != null)
                + " bodyAPose=" + (bodyA == null ? "missing" : bodyA.logicalPose().position())
                + " bodyBPose=" + (bodyB == null ? "missing" : bodyB.logicalPose().position())
                + " bodyAHandleValid=" + (handleA != null && handleA.isValid())
                + " bodyBHandleValid=" + (handleB != null && handleB.isValid())
                + " backendHandleValid=" + (canary.handle() != null && canary.handle().isValid())
                + " bodyALinearVelocity=" + (handleA == null ? "unresolved" : handleA.getLinearVelocity(new Vector3d()))
                + " bodyBLinearVelocity=" + (handleB == null ? "unresolved" : handleB.getLinearVelocity(new Vector3d()))
                + " bodyAAngularVelocity=" + (handleA == null ? "unresolved" : handleA.getAngularVelocity(new Vector3d()))
                + " bodyBAngularVelocity=" + (handleB == null ? "unresolved" : handleB.getAngularVelocity(new Vector3d()))
                + " canarySessionId=" + (traceMatchesCanary ? trace.canarySessionId() : "unavailable")
                + " traceComplete=" + traceComplete
                + " capturedPhases=" + capturedPhases
                + " missingPhases=" + missingPhases
                + " preSolver=" + (traceMatchesCanary
                        ? trace.snapshotSummary(RotaryPipelineTraceRegistry.PRE_SOLVER) : "PRE_SOLVER=missing")
                + " postRapierSolver=" + (traceMatchesCanary
                        ? trace.snapshotSummary(RotaryPipelineTraceRegistry.POST_RAPIER_SOLVER) : "POST_RAPIER_SOLVER=missing")
                + " postSableSyncPreCleanup=" + (traceMatchesCanary
                        ? trace.snapshotSummary(RotaryPipelineTraceRegistry.POST_SABLE_SYNC_PRE_CLEANUP)
                        : "POST_SABLE_SYNC_PRE_CLEANUP=missing")
                + " safetyRemoval=" + (traceMatchesCanary
                        ? trace.snapshotSummary(RotaryPipelineTraceRegistry.SAFETY_REMOVAL) : "SAFETY_REMOVAL=missing")
                + " postJointInsert=" + (traceMatchesCanary
                        ? trace.lifecycleSummary(RotaryPipelineTraceRegistry.POST_JOINT_INSERT) : "POST_JOINT_INSERT=missing")
                + " prePhysicsSystemTick=" + (traceMatchesCanary
                        ? trace.lifecycleSummary(RotaryPipelineTraceRegistry.PRE_PHYSICS_SYSTEM_TICK) : "PRE_PHYSICS_SYSTEM_TICK=missing")
                + " afterBodyUpdateQueue=" + (traceMatchesCanary
                        ? trace.lifecycleSummary(RotaryPipelineTraceRegistry.AFTER_BODY_UPDATE_QUEUE) : "AFTER_BODY_UPDATE_QUEUE=missing")
                + " beforeBodyRecreate=" + (traceMatchesCanary
                        ? trace.lifecycleSummary(RotaryPipelineTraceRegistry.BEFORE_BODY_RECREATE) : "BEFORE_BODY_RECREATE=missing")
                + " afterBodyRecreate=" + (traceMatchesCanary
                        ? trace.lifecycleSummary(RotaryPipelineTraceRegistry.AFTER_BODY_RECREATE_IF_ANY) : "AFTER_BODY_RECREATE_IF_ANY=missing")
                + " afterColliderUpdate=" + (traceMatchesCanary
                        ? trace.lifecycleSummary(RotaryPipelineTraceRegistry.AFTER_COLLIDER_UPDATE) : "AFTER_COLLIDER_UPDATE=missing")
                + " preConstraintMaintenance=" + (traceMatchesCanary
                        ? trace.lifecycleSummary(RotaryPipelineTraceRegistry.PRE_CONSTRAINT_MAINTENANCE) : "PRE_CONSTRAINT_MAINTENANCE=missing")
                + " afterConstraintMaintenance=" + (traceMatchesCanary
                        ? trace.lifecycleSummary(RotaryPipelineTraceRegistry.AFTER_CONSTRAINT_MAINTENANCE) : "AFTER_CONSTRAINT_MAINTENANCE=missing")
                + " firstRemovalPhase=" + (traceMatchesCanary ? trace.firstRemovalPhase() : "unavailable")
                + " bodyARemovalReason=" + (traceMatchesCanary ? trace.bodyRemovalReason(canary.bodyA()) : "unavailable")
                + " bodyBRemovalReason=" + (traceMatchesCanary ? trace.bodyRemovalReason(canary.bodyB()) : "unavailable")
                + " jointRemovalReason=" + (traceMatchesCanary ? trace.jointRemovalReason() : "unavailable")
                + " oldBodyAHandle=" + (traceMatchesCanary ? trace.bodyAId() : -1)
                + " replacementBodyAHandle=" + (traceMatchesCanary ? trace.replacementBodyAId() : -1)
                + " oldBodyBHandle=" + (traceMatchesCanary ? trace.bodyBId() : -1)
                + " replacementBodyBHandle=" + (traceMatchesCanary ? trace.replacementBodyBId() : -1)
                + " bodyARecreationReason=" + (traceMatchesCanary ? trace.bodyARecreationReason() : "unavailable")
                + " bodyBRecreationReason=" + (traceMatchesCanary ? trace.bodyBRecreationReason() : "unavailable")
                + " removalRecords=" + (traceMatchesCanary ? trace.removalRecords() : "[]")
                + " jointLinearImpulse=" + (traceMatchesCanary ? trace.latestJointLinearImpulse() : "unavailable")
                + " jointAngularImpulse=" + (traceMatchesCanary ? trace.latestJointAngularImpulse() : "unavailable")
                + " finite=" + finite
                + " firstCorruptedQuantity=" + (bodyA == null || bodyB == null ? "POST_STEP_1_body_removed_or_safety_cleanup" : "runtime_log_required")
                + " firstFailureClassification=" + classification);
        return valid ? 1 : 0;
    }

    private static int backendFixedRealBodyCanary(final CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final ServerLevel level = source.getLevel();
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(ctx);
        final SubLevelPhysicsSystem physicsSystem = SableCommandHelper.requireSubLevelPhysicsSystem(container);
        final Vec3 playerPos = Vec3.atCenterOf(BlockPos.containing(source.getPosition()));
        final ServerSubLevel bodyA = createRotaryRealBodyCanaryBody(container, playerPos, Direction.EAST);
        final ServerSubLevel bodyB = createRotaryRealBodyCanaryBody(container,
                playerPos.add(4.0D, 0.0D, 0.0D), Direction.WEST);
        physicsSystem.finalizeExistingSubLevelStorage(bodyA, true, "m24_fixed_realbody_canary_A");
        physicsSystem.finalizeExistingSubLevelStorage(bodyB, true, "m24_fixed_realbody_canary_B");
        final Vector3d anchorA = JOMLConversion.atCenterOf(bodyA.getPlot().getCenterBlock().east(2));
        final Vector3d anchorB = JOMLConversion.atCenterOf(bodyB.getPlot().getCenterBlock().west(2));
        final String canarySessionId = UUID.randomUUID().toString();
        RotaryPipelineTraceRegistry.register(level, canarySessionId, bodyA.getUniqueId(), bodyB.getUniqueId(),
                bodyA.getRuntimeId(), bodyB.getRuntimeId(), -1L,
                rawMinusCenterOfMass(bodyA, anchorA), rawMinusCenterOfMass(bodyB, anchorB),
                new Vector3d(1.0D, 0.0D, 0.0D), new Vector3d(1.0D, 0.0D, 0.0D));
        final PhysicsConstraintHandle handle = physicsSystem.getPipeline().addConstraint(bodyA, bodyB,
                new FixedConstraintConfiguration(anchorA, anchorB, new Quaterniond()));
        if (handle != null) {
            handle.setContactsEnabled(false);
        }
        LAST_FIXED_REALBODY_CANARIES.put(fixtureKey(level),
                new RotaryCanaryState(bodyA.getUniqueId(), bodyB.getUniqueId(), handle));
        final RotaryPipelineTraceRegistry.TraceState trace = RotaryPipelineTraceRegistry.get(level);
        send(source, "SABLE_M24_FIXED_REALBODY_CANARY status="
                + (handle != null && handle.isValid() ? "PASS" : "FAIL")
                + " phase=POST_INSERT_PRE_STEP bodyA=" + bodyA.getUniqueId()
                + " bodyB=" + bodyB.getUniqueId()
                + " canarySessionId=" + canarySessionId
                + " postJointInsert=" + (trace == null ? "missing"
                        : trace.lifecycleSummary(RotaryPipelineTraceRegistry.POST_JOINT_INSERT)));
        return handle != null && handle.isValid() ? 1 : 0;
    }

    private static int inspectBackendFixedRealBodyCanary(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final RotaryCanaryState canary = LAST_FIXED_REALBODY_CANARIES.get(fixtureKey(source.getLevel()));
        if (canary == null) {
            send(source, "SABLE_M24_FIXED_REALBODY_CANARY status=FAIL phase=INSPECT reason=no_fixed_realbody_canary_created");
            return 0;
        }
        final RotaryPipelineTraceRegistry.TraceState trace = RotaryPipelineTraceRegistry.get(source.getLevel());
        final boolean matches = trace != null && trace.bodyAUuid().equals(canary.bodyA())
                && trace.bodyBUuid().equals(canary.bodyB());
        send(source, "SABLE_M24_FIXED_REALBODY_CANARY status="
                + (canary.handle() != null && canary.handle().isValid() ? "PASS" : "FAIL")
                + " phase=INSPECT bodyA=" + canary.bodyA() + " bodyB=" + canary.bodyB()
                + " postJointInsert=" + (matches
                        ? trace.lifecycleSummary(RotaryPipelineTraceRegistry.POST_JOINT_INSERT) : "missing")
                + " afterBodyUpdateQueue=" + (matches
                        ? trace.lifecycleSummary(RotaryPipelineTraceRegistry.AFTER_BODY_UPDATE_QUEUE) : "missing")
                + " preSolver=" + (matches
                        ? trace.snapshotSummary(RotaryPipelineTraceRegistry.PRE_SOLVER) : "missing")
                + " firstFailureClassification=" + (matches ? trace.firstFailureClassification() : "TRACE_INCOMPLETE"));
        return canary.handle() != null && canary.handle().isValid() ? 1 : 0;
    }

    private static int backendRopeCanary(final CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final ServerLevel level = source.getLevel();
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(ctx);
        final SubLevelPhysicsSystem physicsSystem = SableCommandHelper.requireSubLevelPhysicsSystem(container);
        final Vec3 playerPos = Vec3.atCenterOf(BlockPos.containing(source.getPosition()));
        final ServerSubLevel bodyA = createRotaryCanaryBody(container, playerPos);
        final ServerSubLevel bodyB = createRotaryCanaryBody(container, playerPos.add(6.0D, 0.0D, 0.0D));
        physicsSystem.finalizeExistingSubLevelStorage(bodyA, true, "m24_rope_canary_A");
        physicsSystem.finalizeExistingSubLevelStorage(bodyB, true, "m24_rope_canary_B");
        final Vector3d anchorA = JOMLConversion.atCenterOf(bodyA.getPlot().getCenterBlock().east());
        final Vector3d anchorB = JOMLConversion.atCenterOf(bodyB.getPlot().getCenterBlock().west());
        final Vec3 visibleA = bodyA.logicalPose().transformPosition(new Vec3(anchorA.x, anchorA.y, anchorA.z));
        final Vec3 visibleB = bodyB.logicalPose().transformPosition(new Vec3(anchorB.x, anchorB.y, anchorB.z));
        final ObjectArrayList<Vector3d> points = createRopeCanaryPoints(visibleA, visibleB);
        if (points.size() < 2) {
            send(source, "SABLE_M24_ROPE_CANARY status=FAIL phase=CREATE reason=invalid_endpoint_distance"
                    + " visibleAnchorDistance=" + visibleA.distanceTo(visibleB));
            return 0;
        }
        final RopePhysicsObject rope = new RopePhysicsObject(points, 0.125D);
        physicsSystem.addObject(rope);
        rope.setAttachment(RopeHandle.AttachmentPoint.START, anchorA, bodyA);
        rope.setAttachment(RopeHandle.AttachmentPoint.END, anchorB, bodyB);
        final double configuredLength = ropeCanaryLength(points);
        LAST_ROPE_CANARIES.put(fixtureKey(level),
                new RopeCanaryState(bodyA.getUniqueId(), bodyB.getUniqueId(), rope,
                        configuredLength, level.getGameTime()));
        send(source, "SABLE_M24_ROPE_CANARY status=" + (rope.isActive() ? "PASS" : "FAIL")
                + " phase=CREATE"
                + " bodyA=" + bodyA.getUniqueId()
                + " bodyB=" + bodyB.getUniqueId()
                + " bodyAPose=" + bodyA.logicalPose().position()
                + " bodyBPose=" + bodyB.logicalPose().position()
                + " rawAnchorA=" + anchorA
                + " rawAnchorB=" + anchorB
                + " rapierLocalAnchorA=" + rawMinusCenterOfMass(bodyA, anchorA)
                + " rapierLocalAnchorB=" + rawMinusCenterOfMass(bodyB, anchorB)
                + " visibleAnchorA=" + visibleA
                + " visibleAnchorB=" + visibleB
                + " visibleAnchorDistance=" + visibleA.distanceTo(visibleB)
                + " configuredLength=" + configuredLength
                + " backendCurrentLength=" + configuredLength
                + " initialConstraintError=0.0"
                + " backendHandleValid=" + rope.isActive()
                + " containsSimulatedM24BlockEntity=false");
        return rope.isActive() ? 1 : 0;
    }

    private static int inspectBackendRopeCanary(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final RopeCanaryState canary = LAST_ROPE_CANARIES.get(fixtureKey(source.getLevel()));
        if (canary == null) {
            send(source, "SABLE_M24_ROPE_CANARY status=FAIL phase=INSPECT reason=no_rope_canary_created");
            return 0;
        }
        final ServerSubLevel bodyA = findSubLevelById(source.getLevel(), canary.bodyA());
        final ServerSubLevel bodyB = findSubLevelById(source.getLevel(), canary.bodyB());
        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(source.getLevel());
        final RigidBodyHandle handleA = bodyA == null || physicsSystem == null ? null : physicsSystem.getPhysicsHandle(bodyA);
        final RigidBodyHandle handleB = bodyB == null || physicsSystem == null ? null : physicsSystem.getPhysicsHandle(bodyB);
        if (canary.rope().isActive()) {
            canary.rope().updatePose();
        }
        final double currentLength = ropeCanaryLength(canary.rope().getPoints());
        final boolean valid = bodyA != null && bodyB != null
                && handleA != null && handleA.isValid()
                && handleB != null && handleB.isValid()
                && canary.rope().isActive()
                && Double.isFinite(currentLength);
        send(source, "SABLE_M24_ROPE_CANARY status=" + (valid ? "PASS" : "FAIL")
                + " phase=INSPECT"
                + " ticksSinceCreate=" + Math.max(0L, source.getLevel().getGameTime() - canary.createdGameTime())
                + " bodyA=" + canary.bodyA()
                + " bodyB=" + canary.bodyB()
                + " bodyAPresent=" + (bodyA != null)
                + " bodyBPresent=" + (bodyB != null)
                + " bodyAPose=" + (bodyA == null ? "missing" : bodyA.logicalPose().position())
                + " bodyBPose=" + (bodyB == null ? "missing" : bodyB.logicalPose().position())
                + " bodyAHandleValid=" + (handleA != null && handleA.isValid())
                + " bodyBHandleValid=" + (handleB != null && handleB.isValid())
                + " backendHandleValid=" + canary.rope().isActive()
                + " storedConfiguredLength=" + canary.configuredLength()
                + " backendCurrentLength=" + currentLength
                + " currentLength=" + currentLength
                + " constraintError=" + Math.abs(currentLength - canary.configuredLength())
                + " bodyALinearVelocity=" + (handleA == null ? "unresolved" : handleA.getLinearVelocity(new Vector3d()))
                + " bodyBLinearVelocity=" + (handleB == null ? "unresolved" : handleB.getLinearVelocity(new Vector3d())));
        return valid ? 1 : 0;
    }

    private static ServerSubLevel createRotaryCanaryBody(final ServerSubLevelContainer container, final Vec3 visiblePosition) {
        final Pose3d pose = new Pose3d();
        pose.position().set(visiblePosition.x, visiblePosition.y, visiblePosition.z);
        final ServerSubLevel subLevel = (ServerSubLevel) container.allocateNewSubLevel(pose);
        final LevelPlot plot = subLevel.getPlot();
        final ChunkPos center = plot.getCenterChunk();
        plot.newEmptyChunk(center);
        plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), 3);
        subLevel.updateLastPose();
        return subLevel;
    }

    private static ServerSubLevel createRotaryRealBodyCanaryBody(final ServerSubLevelContainer container,
                                                                 final Vec3 visiblePosition,
                                                                 final Direction hingeDirection) {
        final Pose3d pose = new Pose3d();
        pose.position().set(visiblePosition.x, visiblePosition.y, visiblePosition.z);
        final ServerSubLevel subLevel = (ServerSubLevel) container.allocateNewSubLevel(pose);
        final LevelPlot plot = subLevel.getPlot();
        final ChunkPos center = plot.getCenterChunk();
        plot.newEmptyChunk(center);
        plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO.above(), Blocks.STONE.defaultBlockState(), 3);
        plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO.relative(hingeDirection), Blocks.COPPER_BLOCK.defaultBlockState(), 3);
        plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO.relative(hingeDirection.getOpposite()), Blocks.COPPER_BLOCK.defaultBlockState(), 3);
        plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO.north(), Blocks.AMETHYST_BLOCK.defaultBlockState(), 3);
        plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO.south(), Blocks.CALCITE.defaultBlockState(), 3);
        subLevel.updateLastPose();
        return subLevel;
    }

    private static ObjectArrayList<Vector3d> createRopeCanaryPoints(final Vec3 visibleA, final Vec3 visibleB) {
        final double distance = visibleA.distanceTo(visibleB);
        final ObjectArrayList<Vector3d> points = new ObjectArrayList<>();
        if (!Double.isFinite(distance) || distance <= 1.0E-6D) {
            return points;
        }
        final int oneLongSegments = (int) Math.floor(distance);
        final int pointCount = Math.max(1, oneLongSegments + 1);
        final Vec3 diff = visibleB.subtract(visibleA).normalize();
        final double shortSegmentLength = distance - oneLongSegments;
        points.add(new Vector3d(visibleA.x, visibleA.y, visibleA.z));
        for (int i = 0; i < pointCount; i++) {
            final Vec3 point = visibleA.add(diff.scale(i + shortSegmentLength));
            points.add(new Vector3d(point.x, point.y, point.z));
        }
        return points;
    }

    private static double ropeCanaryLength(final List<? extends Vector3d> points) {
        double length = 0.0D;
        for (int i = 0; i < points.size() - 1; i++) {
            length += points.get(i).distance(points.get(i + 1));
        }
        return length;
    }

    private static Vector3d rawMinusCenterOfMass(final ServerSubLevel subLevel, final Vector3d rawAnchor) {
        return rawAnchor.sub(subLevel.getMassTracker().getCenterOfMass(), new Vector3d());
    }

    private static M24Family parseFamily(final String name) {
        final String normalized = name.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "torsion", "torsion_spring" -> M24Family.TORSION_SPRING;
            case "swivel", "swivel_bearing" -> M24Family.SWIVEL_BEARING;
            case "rope", "rope_connector" -> M24Family.ROPE_CONNECTOR;
            case "winch", "rope_winch" -> M24Family.ROPE_WINCH;
            case "docking", "docking_connector" -> M24Family.DOCKING_CONNECTOR;
            case "altitude", "altitude_sensor" -> M24Family.ALTITUDE_SENSOR;
            case "velocity", "velocity_sensor" -> M24Family.VELOCITY_SENSOR;
            case "optical", "optical_sensor" -> M24Family.OPTICAL_SENSOR;
            case "steering", "steering_wheel" -> M24Family.STEERING_WHEEL;
            default -> null;
        };
    }

    private static String fixtureName(final M24Family family) {
        return switch (family) {
            case TORSION_SPRING -> "torsion";
            case SWIVEL_BEARING -> "swivel";
            case ROPE_WINCH -> "winch";
            case DOCKING_CONNECTOR -> "docking";
            default -> "rope";
        };
    }

    private static BlockPos visibleBlockPos(final ServerSubLevel subLevel, final BlockPos raw) {
        final Vec3 visible = subLevel.logicalPose().transformPosition(raw.getCenter());
        return BlockPos.containing(visible);
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
                            + ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock()) + "|"
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

    private static void glue(final ServerLevel level, final BlockPos first, final BlockPos second) {
        level.addFreshEntity(new SuperGlueEntity(level, SuperGlueEntity.span(first, second)));
    }

    private static Block requireFixtureBlock(final RegistryObject<Block> block, final String role) {
        if (!block.isPresent()) {
            throw new IllegalStateException("missing_" + role + "_fixture_block:" + block.getId());
        }
        return block.get();
    }

    private static void sendFixtureTrace(final CommandSourceStack source, final M24Family family,
                                         final String phase, final String extra) {
        send(source, "SABLE_M24_FIXTURE_TRACE family=" + fixtureName(family)
                + " phase=" + phase + extra);
    }

    private static int cleanupFixture(final ServerLevel level, final FixtureState state, final boolean removeMetadata) {
        if (state == null) {
            return 0;
        }
        int removedBlocks = 0;
        for (final BlockPos pos : state.parentBlocks()) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                removedBlocks++;
            }
        }
        final AABB bounds = state.parentBounds().inflate(1.0D);
        for (final SuperGlueEntity glue : List.copyOf(level.getEntitiesOfClass(SuperGlueEntity.class, bounds))) {
            glue.remove(Entity.RemovalReason.KILLED);
        }
        if (removeMetadata) {
            state.clearBodyIds();
        }
        return removedBlocks;
    }

    private static String safeToken(final String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        return value.replaceAll("\\s+", "_");
    }

    private static String fixtureKey(final ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static boolean finite(final Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static void send(final CommandSourceStack source, final String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    private static final class FixtureState {
        private final M24Family family;
        private final BlockPos bodyABase;
        private final BlockPos bodyBBase;
        private final BlockPos bodyAAssembler;
        private final BlockPos bodyBAssembler;
        private final BlockPos bodyAEndpoint;
        private final BlockPos bodyBEndpoint;
        private final Block bodyAComponent;
        private final Block bodyBComponent;
        private final BlockPos motorPos;
        private final int expectedBodies;
        private final int expectedBlocksPerBody;
        private final Set<BlockPos> parentBlocks;
        private FixtureLifecycle lifecycle = FixtureLifecycle.PARENT_FIXTURE_READY;
        private UUID bodyASableId;
        private UUID bodyBSableId;
        private int bodyALastKnownHandle = -1;
        private int bodyBLastKnownHandle = -1;

        private FixtureState(final M24Family family, final BlockPos bodyABase, final BlockPos bodyBBase,
                             final BlockPos bodyAAssembler, final BlockPos bodyBAssembler,
                             final BlockPos bodyAEndpoint, final BlockPos bodyBEndpoint,
                             final Block bodyAComponent, final Block bodyBComponent,
                             final BlockPos motorPos,
                             final int expectedBodies, final int expectedBlocksPerBody,
                             final Set<BlockPos> parentBlocks) {
            this.family = family;
            this.bodyABase = bodyABase;
            this.bodyBBase = bodyBBase;
            this.bodyAAssembler = bodyAAssembler;
            this.bodyBAssembler = bodyBAssembler;
            this.bodyAEndpoint = bodyAEndpoint;
            this.bodyBEndpoint = bodyBEndpoint;
            this.bodyAComponent = bodyAComponent;
            this.bodyBComponent = bodyBComponent;
            this.motorPos = motorPos;
            this.expectedBodies = expectedBodies;
            this.expectedBlocksPerBody = expectedBlocksPerBody;
            this.parentBlocks = parentBlocks;
        }

        private M24Family family() {
            return this.family;
        }

        private BlockPos bodyAAssembler() {
            return this.bodyAAssembler;
        }

        private BlockPos bodyBAssembler() {
            return this.bodyBAssembler;
        }

        private BlockPos bodyAEndpoint() {
            return this.bodyAEndpoint;
        }

        private BlockPos bodyBEndpoint() {
            return this.bodyBEndpoint;
        }

        private Block bodyAComponent() {
            return this.bodyAComponent;
        }

        private Block bodyBComponent() {
            return this.bodyBComponent;
        }

        private BlockPos motorPos() {
            return this.motorPos;
        }

        private int expectedBodies() {
            return this.expectedBodies;
        }

        private int expectedBlocksPerBody() {
            return this.expectedBlocksPerBody;
        }

        private Set<BlockPos> parentBlocks() {
            return this.parentBlocks;
        }

        private AABB parentBounds() {
            AABB bounds = new AABB(this.bodyABase);
            for (final BlockPos pos : this.parentBlocks) {
                bounds = bounds.minmax(new AABB(pos));
            }
            return bounds;
        }

        private boolean parentFixturePresent(final ServerLevel level, final BodySummary bodyA,
                                             final BodySummary bodyB) {
            return bodyA.parentAssemblerPresent()
                    && (this.expectedBodies == 1 || bodyB.parentAssemblerPresent())
                    && bodyA.parentEndpointPresent()
                    && (this.expectedBodies == 1 || bodyB.parentEndpointPresent())
                    && !level.getBlockState(this.bodyABase).isAir()
                    && (this.expectedBodies == 1 || !level.getBlockState(this.bodyBBase).isAir());
        }

        private void updateLifecycle(final boolean bodyAAssembled, final boolean bodyBAssembled,
                                     final boolean bodiesReady) {
            if (bodiesReady) {
                this.lifecycle = FixtureLifecycle.BODIES_READY;
            } else if (this.expectedBodies == 1 && bodyAAssembled) {
                this.lifecycle = FixtureLifecycle.BODY_A_ASSEMBLED;
            } else if (bodyAAssembled && bodyBAssembled) {
                this.lifecycle = FixtureLifecycle.BODY_DISCOVERY_ERROR;
            } else if (bodyAAssembled) {
                this.lifecycle = FixtureLifecycle.BODY_A_ASSEMBLED;
            } else if (bodyBAssembled) {
                this.lifecycle = FixtureLifecycle.BODY_B_ASSEMBLED;
            } else {
                this.lifecycle = FixtureLifecycle.PARENT_FIXTURE_READY;
            }
        }

        private String lifecycleName() {
            return this.lifecycle.name();
        }

        private void clearBodyIds() {
            this.bodyASableId = null;
            this.bodyBSableId = null;
            this.lifecycle = FixtureLifecycle.NO_FIXTURE;
        }
    }

    private enum FixtureLifecycle {
        NO_FIXTURE,
        PARENT_FIXTURE_READY,
        BODY_A_ASSEMBLED,
        BODY_B_ASSEMBLED,
        BODIES_READY,
        CONSTRAINT_ACTIVE,
        BODY_DISCOVERY_ERROR
    }

    private record FixtureValidation(boolean valid, String status, String reason, BodySummary bodyA, BodySummary bodyB) {
    }

    private record SelectionPreview(int selectedBlockCount, int platformBlockCount,
                                    int selectedPlatformBlocks, boolean assemblerIncluded,
                                    boolean endpointIncluded, String selectionBounds,
                                    String selectionSha256) {
        private boolean validForFixture() {
            return this.selectedBlockCount == 6
                    && this.selectedPlatformBlocks == 0
                    && this.assemblerIncluded
                    && this.endpointIncluded;
        }
    }

    private record BodySummary(String slot, BlockPos assemblerVisible, BlockPos endpointVisible,
                               UUID knownSableId, ServerSubLevel subLevel, BlockPos assemblerRaw, BlockPos endpointRaw,
                               int storedBlockCount, int blockEntityCount, boolean assemblerPresent,
                               boolean endpointPresent, boolean bodyRegistered,
                               boolean collisionGeometryPresent, int collisionUploadedBlocks,
                               boolean bodyHandleValid, M24PhysicalBlockEntity endpointBlockEntity,
                               String linearVelocity, String angularVelocity, String activeConstraintIds,
                               String classification, String collisionDiagnosticStatus,
                               boolean parentAssemblerPresent, boolean parentEndpointPresent) {
        private static BodySummary missing(final String slot, final BlockPos assemblerVisible,
                                           final BlockPos endpointVisible,
                                           final UUID knownSableId,
                                           final boolean parentAssemblerPresent,
                                           final boolean parentEndpointPresent,
                                           final String classification) {
            return new BodySummary(slot, assemblerVisible, endpointVisible, knownSableId, null, null, null,
                    0, 0, false, false, false, false, 0, false, null,
                    "unresolved", "unresolved", "[]",
                    classification, "NO_AUTHORITATIVE_BODY_HANDLE",
                    parentAssemblerPresent, parentEndpointPresent);
        }

        private boolean valid(final int expectedBlocks) {
            return this.subLevel != null
                    && this.storedBlockCount == expectedBlocks
                    && this.assemblerPresent
                    && this.endpointPresent
                    && this.bodyRegistered
                    && this.bodyHandleValid
                    && this.endpointBlockEntity != null;
        }

        private String sableId() {
            if (this.subLevel != null) {
                return this.subLevel.getUniqueId().toString();
            }
            return this.knownSableId == null ? "unresolved" : this.knownSableId.toString();
        }
    }

    private record FixtureBodyTarget(M24Family family, String slot, ServerSubLevel subLevel, RigidBodyHandle handle) {
    }

    private record RotaryCanaryState(UUID bodyA, UUID bodyB, PhysicsConstraintHandle handle) {
    }

    private record RopeCanaryState(UUID bodyA, UUID bodyB, RopePhysicsObject rope,
                                   double configuredLength, long createdGameTime) {
    }
}
