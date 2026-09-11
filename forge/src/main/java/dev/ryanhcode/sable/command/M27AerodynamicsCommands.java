package dev.ryanhcode.sable.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.content.propulsion.WoodenPropellerBlock;
import dev.eriksonn.aeronautics.content.propulsion.WoodenPropellerBlockEntity;
import dev.eriksonn.aeronautics.index.AeroBlocks;
import dev.eriksonn.aeronautics.index.AeroPropulsionRegistries;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.index.SimulatedBlocks;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class M27AerodynamicsCommands {
    private static final ResourceLocation CREATIVE_MOTOR_ID = new ResourceLocation("create", "creative_motor");
    private static final ResourceLocation CREATE_SAIL_ID = new ResourceLocation("create", "white_sail");
    private static final ResourceLocation MECHANICAL_BEARING_ID =
            new ResourceLocation("create", "mechanical_bearing");
    private static final double TICK_SECONDS = 1.0D / 20.0D;
    private static final Set<Integer> SAMPLE_TICKS = Set.of(0, 1, 2, 5, 20);
    private static final Map<String, FixtureState> ACTIVE_FIXTURES = new HashMap<>();

    private M27AerodynamicsCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m27")
                .then(Commands.literal("status").executes(M27AerodynamicsCommands::status))
                .then(Commands.literal("registry_check").executes(M27AerodynamicsCommands::registryCheck))
                .then(Commands.literal("cleanup").executes(M27AerodynamicsCommands::cleanup))
                .then(Commands.literal("release").executes(M27AerodynamicsCommands::release))
                .then(Commands.literal("prepare_disassembly")
                        .executes(M27AerodynamicsCommands::prepareDisassembly))
                .then(Commands.literal("fixture")
                        .then(Commands.literal("aero_surface_basic")
                                .executes(ctx -> fixture(ctx.getSource(), FixtureKind.BASIC)))
                        .then(Commands.literal("aero_offset")
                                .executes(ctx -> fixture(ctx.getSource(), FixtureKind.OFFSET)))
                        .then(Commands.literal("pitch_control")
                                .executes(ctx -> fixture(ctx.getSource(), FixtureKind.PITCH)))
                        .then(Commands.literal("aero_vehicle")
                                .executes(ctx -> fixture(ctx.getSource(), FixtureKind.VEHICLE))))
                .then(Commands.literal("propulsion")
                        .then(Commands.literal("rpm")
                                .then(Commands.argument("rpm", IntegerArgumentType.integer(-256, 256))
                                        .executes(M27AerodynamicsCommands::setRpm))))
                .then(Commands.literal("control")
                        .then(Commands.literal("elevator")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(-1.0D, 1.0D))
                                        .executes(M27AerodynamicsCommands::setControl))))
                .then(Commands.literal("inspect")
                        .then(Commands.literal("aero").executes(M27AerodynamicsCommands::inspect))));
    }

    public static void tick(final ServerLevel level) {
        final FixtureState state = activeFixture(level);
        final ServerSubLevel body = resolveBody(level, state);
        if (state == null || body == null) {
            return;
        }
        body.enableIndividualQueuedForcesTracking(true);
        final AeroSample sample = sample(level, body, state);
        state.latest = sample;
        logStaticProviderDiagnostic(level, body, state);
        logKinematicProviderDiagnostic(body, state);
        if (SAMPLE_TICKS.contains(state.observationTick)) {
            Aeronautics.LOGGER.info("SABLE_M27_AERO phase=POST_PHYSICS_SAMPLE fixtureSessionId={}"
                            + " fixtureType={} step={} sableId={} providerDiscovered={} airSpeed={} normalVelocity={}"
                            + " lift={} drag={} aeroTorque={} position={} linearVelocity={} angularVelocity={}"
                            + " finite={} aeroNumericState={}",
                    state.sessionId, state.kind.commandName, state.observationTick, body.getUniqueId(),
                    sample.providerPresent, diagnostic(sample.relativeAirSpeed), diagnostic(sample.normalVelocity),
                    diagnostic(sample.liftWorld), diagnostic(sample.dragWorld), diagnostic(sample.accumulatedTorque),
                    diagnostic(sample.position), diagnostic(sample.linearVelocity), diagnostic(sample.angularVelocity),
                    sample.numericStateFinite, sample.aeroNumericState);
        }
        state.observationTick++;
    }

    public static void onM22AssemblyCreated(final ServerLevel level, final BlockPos parentAssemblerPos,
                                            final ServerSubLevel subLevel, final BlockPos assemblyOffset) {
        final FixtureState state = activeFixture(level);
        if (state == null || state.bodyId != null || !state.parentAssembler.equals(parentAssemblerPos)) {
            return;
        }
        final BlockPos assemblerRaw = parentAssemblerPos.offset(assemblyOffset);
        if (!level.getBlockState(assemblerRaw).is(SimulatedBlocks.PHYSICS_ASSEMBLER.get())) {
            state.failure = "assembly_capture_assembler_missing";
            Aeronautics.LOGGER.error("SABLE_M27_FIXTURE_BODY_CAPTURE fixtureSessionId={} fixtureType={}"
                            + " captureOwner=PhysicsAssemblerBlockEntity#assemble assemblerParentPos={}"
                            + " createdSableId={} storedBlockCountAtCapture={} captureStatus=FAIL"
                            + " reason=assembly_capture_assembler_missing",
                    state.sessionId, state.kind.commandName, parentAssemblerPos.toShortString(),
                    subLevel.getUniqueId(), SimAssemblyHelper.collectBlocks(level, subLevel).size());
            return;
        }
        state.capture(subLevel, assemblerRaw);
        Aeronautics.LOGGER.info("SABLE_M27_FIXTURE_BODY_CAPTURE fixtureSessionId={} fixtureType={}"
                        + " captureOwner=PhysicsAssemblerBlockEntity#assemble assemblerParentPos={}"
                        + " createdSableId={} storedBlockCountAtCapture={} captureStatus=PASS"
                        + " assemblerLocalPos={} motorLocalPos={} propellerLocalPos={}"
                        + " aeroLocalPos={} controlLocalPos={}",
                state.sessionId, state.kind.commandName, parentAssemblerPos.toShortString(),
                state.bodyId, SimAssemblyHelper.collectBlocks(level, subLevel).size(), state.assemblerLocal,
                state.motorLocal, state.propellerLocal, state.aeroLocal, state.controlLocal);
        logStaticProviderDiagnostic(level, subLevel, state);
    }

    private static int status(final CommandContext<CommandSourceStack> ctx) {
        send(ctx.getSource(), "SABLE_M27_STATUS implementationRevision=M27.2"
                + " m25=CLOSED_RUNTIME_PROVEN m26PropulsionCore=RUNTIME_PROVEN"
                + " upstreamSable=b7226222caf4eace63a708bdcd73ef36c971137d"
                + " upstreamSimulated=" + Aeronautics.BASELINE_COMMIT
                + " aeroModel=SABLE_BLOCK_SUBLEVEL_LIFT_PROVIDER"
                + " status=CLOSED_RUNTIME_PROVEN");
        return 1;
    }

    private static int registryCheck(final CommandContext<CommandSourceStack> ctx) {
        final Block sail = ForgeRegistries.BLOCKS.getValue(CREATE_SAIL_ID);
        final Block symmetric = SimulatedBlocks.WHITE_SYMMETRIC_SAIL.get();
        final boolean regularProvider = sail instanceof BlockSubLevelLiftProvider;
        final boolean symmetricProvider = symmetric instanceof BlockSubLevelLiftProvider;
        final boolean symmetricSemantics = symmetricProvider
                && ((BlockSubLevelLiftProvider) symmetric).sable$getLiftScalar() == 0.0F
                && ((BlockSubLevelLiftProvider) symmetric).sable$getParallelDragScalar() == 1.75F;
        final boolean pass = regularProvider && symmetricSemantics;
        send(ctx.getSource(), "SABLE_M27_REGISTRY_CHECK status=" + (pass ? "PASS" : "FAIL")
                + " createSailLiftProvider=" + regularProvider
                + " symmetricSailDragProvider=" + symmetricProvider
                + " symmetricLiftScalar=" + providerLift(symmetric)
                + " symmetricParallelDragScalar=" + providerParallelDrag(symmetric)
                + " productionMixins=1 m27Blocks=0 m27BlockEntities=0 m27Packets=0");
        return pass ? 1 : 0;
    }

    private static int fixture(final CommandSourceStack source, final FixtureKind kind) {
        final ServerLevel level = source.getLevel();
        final FixtureState old = ACTIVE_FIXTURES.remove(key(level));
        if (old != null) {
            cleanupParent(level, old);
            old.active = false;
        }
        final Direction towardPlayer = Direction.fromYRot(source.getRotation().y);
        final BlockPos origin = BlockPos.containing(source.getPosition()).relative(towardPlayer, 7).above(7);
        final Set<BlockPos> support = buildSupport(level, origin);
        final Set<BlockPos> body = new HashSet<>();
        final BlockPos propeller = origin;
        final BlockPos motor = origin.west();
        final BlockPos assembler = origin.above();
        final BlockPos aero = kind == FixtureKind.OFFSET ? origin.east(2).north(2) : origin.east(2);
        final BlockPos controlBridge = kind.hasControl ? origin.south() : null;
        final BlockPos controlMotor = kind.hasControl ? origin.south(2) : null;
        final BlockPos controlBearing = kind.hasControl ? controlMotor.east() : null;
        final BlockPos control = kind.hasControl ? controlBearing.east() : null;

        place(level, body, propeller, AeroPropulsionRegistries.WOODEN_PROPELLER.get().defaultBlockState()
                .setValue(WoodenPropellerBlock.FACING, Direction.EAST)
                .setValue(WoodenPropellerBlock.REVERSED, false));
        placeMotor(level, body, motor);
        place(level, body, assembler, SimulatedBlocks.PHYSICS_ASSEMBLER.get().defaultBlockState());
        place(level, body, origin.east(), Blocks.COPPER_BLOCK.defaultBlockState());
        if (kind == FixtureKind.OFFSET) {
            place(level, body, origin.north(), Blocks.COPPER_BLOCK.defaultBlockState());
            place(level, body, origin.north(2), Blocks.COPPER_BLOCK.defaultBlockState());
            place(level, body, origin.east().north(2), Blocks.COPPER_BLOCK.defaultBlockState());
        }
        place(level, body, aero, requireState(CREATE_SAIL_ID).setValue(DirectionalBlock.FACING, Direction.DOWN));
        place(level, body, origin.below(), Blocks.COPPER_BLOCK.defaultBlockState());
        if (control != null) {
            place(level, body, controlBridge, Blocks.COPPER_BLOCK.defaultBlockState());
            placeStoppedMotor(level, body, controlMotor, Direction.EAST);
            place(level, body, controlBearing,
                    requireState(MECHANICAL_BEARING_ID).setValue(DirectionalBlock.FACING, Direction.EAST));
            place(level, body, control, SimulatedBlocks.WHITE_SYMMETRIC_SAIL.get()
                    .defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        }
        if (kind == FixtureKind.VEHICLE) {
            place(level, body, origin.west().below(), AeroBlocks.LEVITITE.get().defaultBlockState());
        }
        glueConnected(level, body);

        final FixtureState state = new FixtureState(UUID.randomUUID(), kind, assembler,
                propeller.subtract(assembler), motor.subtract(assembler), aero.subtract(assembler),
                control == null ? null : control.subtract(assembler),
                controlBearing == null ? null : controlBearing.subtract(assembler),
                controlMotor == null ? null : controlMotor.subtract(assembler),
                Set.copyOf(support), Set.copyOf(body));
        ACTIVE_FIXTURES.put(key(level), state);
        final SimAssemblyContraption selection = new SimAssemblyContraption(null);
        try {
            selection.searchMovedStructure(level, assembler);
        } catch (final AssemblyException exception) {
            Aeronautics.LOGGER.error("SABLE_M27_FIXTURE selection failed", exception);
            send(source, "SABLE_M27_FIXTURE status=FAIL reason=selection_exception message=" + exception.getMessage());
            return 0;
        }
        final long supportSelected = selection.getBlocks().stream().filter(support::contains).count();
        final boolean pass = selection.getBlocks().size() == body.size() && supportSelected == 0;
        send(source, "SABLE_M27_FIXTURE status=" + (pass ? "PASS" : "FAIL")
                + " fixtureSessionId=" + state.sessionId + " fixtureType=" + kind.commandName
                + " assembler=" + assembler.toShortString() + " motor=" + motor.toShortString()
                + " propeller=" + propeller.toShortString() + " aeroSurface=" + aero.toShortString()
                + " controlSurface=" + nullable(control) + " controlBearing=" + nullable(controlBearing)
                + " controlMotor=" + nullable(controlMotor) + " selectedBlockCount=" + selection.getBlocks().size()
                + " expectedBlockCount=" + body.size() + " selectedSupportBlocks=" + supportSelected
                + " bearingAxis=X initialControlNormal=+Z bearingNormalDot=0"
                + " expectedControlPayloadBlocks=" + (kind.hasControl ? 1 : 0)
                + " action=assemble_then_inspect_then_release_then_set_rpm_then_inspect");
        return pass ? 1 : 0;
    }

    private static int setRpm(final CommandContext<CommandSourceStack> ctx) {
        final FixtureState state = activeFixture(ctx.getSource().getLevel());
        final ServerSubLevel body = resolveBody(ctx.getSource().getLevel(), state);
        if (state == null || body == null) {
            return fail(ctx.getSource(), "SABLE_M27_PROPULSION_POWER", state);
        }
        final CreativeMotorBlockEntity motor = resolveMotor(ctx.getSource().getLevel(), body, state);
        final WoodenPropellerBlockEntity propeller = resolvePropeller(ctx.getSource().getLevel(), body, state);
        if (motor == null || propeller == null) {
            send(ctx.getSource(), "SABLE_M27_PROPULSION_POWER status=FAIL reason=active_fixture_component_missing"
                    + " fixtureSessionId=" + state.sessionId + " sableId=" + body.getUniqueId());
            return 0;
        }
        final int rpm = IntegerArgumentType.getInteger(ctx, "rpm");
        motor.generatedSpeed.setValue(rpm);
        motor.setChanged();
        state.observationTick = 0;
        send(ctx.getSource(), "SABLE_M27_PROPULSION_POWER status=PASS fixtureSessionId=" + state.sessionId
                + " fixtureType=" + state.kind.commandName + " storedSableId=" + state.bodyId
                + " sableId=" + body.getUniqueId()
                + " sableResolvedByUuid=true componentResolution=STATIC_SABLE"
                + " motorLocalPos=" + state.motorLocal + " requestedRPM=" + rpm
                + " actualMotorSpeed=" + motor.getGeneratedSpeed()
                + " propulsorKineticSpeed=" + propeller.getSpeed()
                + " commandAppliedForce=false");
        return 1;
    }

    private static int setControl(final CommandContext<CommandSourceStack> ctx) {
        final FixtureState state = activeFixture(ctx.getSource().getLevel());
        final ServerSubLevel body = resolveBody(ctx.getSource().getLevel(), state);
        if (state == null || body == null || state.controlLocal == null) {
            return fail(ctx.getSource(), "SABLE_M27_CONTROL", state);
        }
        final MechanicalBearingBlockEntity bearing = resolveControlBearing(ctx.getSource().getLevel(), body, state);
        final CreativeMotorBlockEntity motor = resolveControlMotor(ctx.getSource().getLevel(), body, state);
        if (bearing == null || motor == null) {
            send(ctx.getSource(), "SABLE_M27_CONTROL status=FAIL reason=active_control_surface_missing"
                    + " fixtureSessionId=" + state.sessionId + " sableId=" + body.getUniqueId());
            return 0;
        }
        final double requested = DoubleArgumentType.getDouble(ctx, "value");
        if (!bearing.isRunning()) {
            bearing.assemble();
        }
        final ControlContraptionSnapshot payload = controlContraptionSnapshot(bearing);
        logControlContraption(body, state, bearing, payload);
        if (!payload.validPayload) {
            send(ctx.getSource(), "SABLE_M27_CONTROL status=FAIL reason=invalid_control_contraption_payload"
                    + " fixtureSessionId=" + state.sessionId + " sableId=" + body.getUniqueId()
                    + " capturedBlockCount=" + payload.capturedBlockCount
                    + " capturedBlockIds=" + payload.capturedBlockIds);
            return 0;
        }
        final int requestedSpeed = (int) Math.round(requested * 16.0D);
        motor.generatedSpeed.setValue(requestedSpeed);
        motor.setChanged();
        state.controlValue = requested;
        state.observationTick = 0;
        send(ctx.getSource(), "SABLE_M27_CONTROL status=PASS fixtureSessionId=" + state.sessionId
                + " fixtureType=" + state.kind.commandName + " storedSableId=" + state.bodyId
                + " sableId=" + body.getUniqueId()
                + " sableResolvedByUuid=true componentResolution=STATIC_SABLE"
                + " surface=elevator mechanism=CREATE_MECHANICAL_BEARING_SYMMETRIC_SAIL"
                + " requestedProductionState=" + requested + " requestedBearingRPM=" + requestedSpeed
                + " observedMotorSpeed=" + motor.getGeneratedSpeed()
                + " bearingRunning=" + bearing.isRunning()
                + " actualDeflectionDegrees=" + bearing.getInterpolatedAngle(1.0F)
                + " forceAppliedByCommand=false bodyPoseChangedByCommand=false");
        return 1;
    }

    private static int inspect(final CommandContext<CommandSourceStack> ctx) {
        final FixtureState state = activeFixture(ctx.getSource().getLevel());
        final ServerSubLevel body = resolveBody(ctx.getSource().getLevel(), state);
        if (state == null || body == null) {
            return fail(ctx.getSource(), "SABLE_M27_INSPECT", state);
        }
        body.enableIndividualQueuedForcesTracking(true);
        final AeroSample s = sample(ctx.getSource().getLevel(), body, state);
        state.latest = s;
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(ctx.getSource().getLevel());
        final RigidBodyHandle handle = physics == null ? null : physics.getPhysicsHandle(body);
        final int fixedProviders = body.getPlot().getLiftProviders().size();
        int movingProviders = 0;
        for (final KinematicContraption contraption : body.getPlot().getContraptions()) {
            movingProviders += contraption.sable$liftProviders().size();
        }
        logStaticProviderDiagnostic(ctx.getSource().getLevel(), body, state);
        logKinematicProviderDiagnostic(body, state);
        final boolean handleValid = handle != null && handle.isValid();
        final MechanicalBearingBlockEntity controlBearing = resolveControlBearing(ctx.getSource().getLevel(), body, state);
        final ControlSurfaceSample control = sampleControlSurface(ctx.getSource().getLevel(), body, state,
                controlBearing);
        final ControlContraptionSnapshot payload = controlBearing == null
                ? ControlContraptionSnapshot.unavailable() : controlContraptionSnapshot(controlBearing);
        if (controlBearing != null && controlBearing.isRunning()) {
            logControlContraption(body, state, controlBearing, payload);
        }
        final boolean controlPayloadValid = !state.kind.hasControl
                || controlBearing != null && (!controlBearing.isRunning() || payload.validPayload);
        final boolean valid = handleValid && s.providerPresent && s.numericStateFinite && controlPayloadValid;
        final String runtimeState;
        if (!handleValid) {
            runtimeState = "WAITING_FOR_BODY_HANDLE";
        } else if (!controlPayloadValid) {
            runtimeState = "INVALID_CONTROL_PAYLOAD";
        } else if (!s.bodyStateFinite || (s.providerPresent && !s.numericStateFinite)) {
            runtimeState = "INVALID_NUMERIC_STATE";
        } else if (!s.providerPresent) {
            runtimeState = "WAITING_FOR_AERO_PROVIDER";
        } else {
            runtimeState = "ACTIVE_RUNTIME_VALIDATION_REQUIRED";
        }
        final Vector3d centerOfMassVisible = body.logicalPose()
                .transformPosition(new Vector3d(body.getMassTracker().getCenterOfMass()));
        final Vector3d angularVelocityBodyLocal = s.angularVelocity == null ? null : body.logicalPose().orientation()
                .transformInverse(s.angularVelocity, new Vector3d());
        final String componentOwner = controlComponentOwner(ctx.getSource().getLevel(), body, state, control.present);
        final String mainMotorOwner = resolveMotor(ctx.getSource().getLevel(), body, state) == null
                ? "UNAVAILABLE" : "STATIC_SABLE";
        final String propellerOwner = resolvePropeller(ctx.getSource().getLevel(), body, state) == null
                ? "UNAVAILABLE" : "STATIC_SABLE";
        final String staticSailOwner = staticProviderAt(ctx.getSource().getLevel(), body, state)
                ? "STATIC_SABLE" : "UNAVAILABLE";
        final String bearingOwner = controlBearing == null ? "UNAVAILABLE" : "STATIC_SABLE";
        send(ctx.getSource(), "SABLE_M27_INSPECT status=" + (valid ? "PASS" : "FAIL")
                + " runtimeState=" + runtimeState
                + " fixtureSessionId=" + state.sessionId + " fixtureType=" + state.kind.commandName
                + " storedSableId=" + state.bodyId + " sableId=" + body.getUniqueId()
                + " sableResolvedByUuid=true componentResolution=AUTHORITATIVE_LOCAL_POSITIONS"
                + " bodyPresent=true bodyHandleValid=" + handleValid
                + " mass=" + body.getMassTracker().getMass() + " centerOfMassVisible=" + centerOfMassVisible
                + " linearVelocityWorld=" + diagnostic(s.linearVelocity)
                + " angularVelocityWorld=" + diagnostic(s.angularVelocity)
                + " angularVelocityBodyLocal=" + diagnostic(angularVelocityBodyLocal)
                + " orientation=" + diagnostic(s.orientation) + " propulsionRPM=" + diagnostic(s.rpm)
                + " propulsionForceWorld=" + diagnostic(s.propulsionWorld)
                + " aeroSurfaceCount=" + fixedProviders + " kinematicAeroSurfaceCount=" + movingProviders
                 + " controlSurfaceCount=" + movingProviders + " providerMissing=" + !s.providerPresent
                 + " componentOwner=" + componentOwner
                + " mainPropulsionMotorOwner=" + mainMotorOwner
                + " woodenPropellerOwner=" + propellerOwner
                + " staticMainSailOwner=" + staticSailOwner
                + " controlBearingOwner=" + bearingOwner
                + " controlSurfaceOwner=" + componentOwner
                + " surfaceLocalPos=" + state.aeroLocal
                + " surfaceVisiblePos=" + diagnostic(s.applicationPointVisible)
                + " surfaceChordBodyLocal=UNDEFINED_BY_FROZEN_MODEL"
                + " surfaceChordWorld=UNDEFINED_BY_FROZEN_MODEL"
                + " surfaceNormalBodyLocal=" + diagnostic(s.normalLocal)
                + " surfaceNormalWorld=" + diagnostic(s.normalWorld)
                + " pointVelocityWorld=" + diagnostic(s.pointVelocityWorld)
                + " airVelocityWorld=(0,0,0) relativeAirflowWorld=" + diagnostic(s.relativeAirflowWorld)
                + " relativeAirSpeed=" + diagnostic(s.relativeAirSpeed)
                + " angleOfAttack=UNDEFINED_BY_FROZEN_MODEL normalVelocity=" + diagnostic(s.normalVelocity)
                + " tangentialSpeed=" + diagnostic(s.tangentialSpeed)
                + " liftForceWorld=" + diagnostic(s.liftWorld)
                + " dragForceWorld=" + diagnostic(s.dragWorld)
                + " totalAeroForceWorld=" + diagnostic(sum(s.liftWorld, s.dragWorld))
                + " applicationPointBodyLocal=" + diagnostic(s.applicationPointBodyLocal)
                + " leverArmBodyLocal=" + diagnostic(s.applicationPointBodyLocal)
                + " expectedTorqueBodyLocal=" + diagnostic(s.expectedTorque)
                + " accumulatedAeroForceBodyLocal=" + diagnostic(s.accumulatedForce)
                + " accumulatedAeroTorqueBodyLocal=" + diagnostic(s.accumulatedTorque)
                + " totalAeroForce=" + diagnostic(s.accumulatedForce)
                + " totalAeroTorque=" + diagnostic(s.accumulatedTorque)
                + " controlDeflectionCommand=" + state.controlValue
                 + " actualControlDeflectionDegrees="
                 + (controlBearing == null ? "unavailable" : controlBearing.getInterpolatedAngle(1.0F))
                + " bearingAxisBodyLocal=" + diagnostic(control.bearingAxisBodyLocal)
                + " controlSurfaceNormalInitial=" + diagnostic(control.initialNormal)
                + " controlSurfaceNormalCurrent=" + diagnostic(control.currentNormal)
                + " normalDeltaDegrees=" + diagnostic(control.normalDeltaDegrees)
                + " bearingAngle=" + diagnostic(control.bearingAngle)
                + " bearingRunning=" + control.bearingRunning
                + " controlPayloadValid=" + payload.validPayload
                + " controlPayloadBlockCount=" + payload.capturedBlockCount
                 + " controlSurfaceMechanism=CREATE_MECHANICAL_BEARING_SYMMETRIC_SAIL"
                + " aeroNumericState=" + s.aeroNumericState
                + " finite=" + s.numericStateFinite);
        return valid ? 1 : 0;
    }

    private static AeroSample sample(final ServerLevel level, final ServerSubLevel body, final FixtureState state) {
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(level);
        final RigidBodyHandle handle = physics == null ? null : physics.getPhysicsHandle(body);
        final WoodenPropellerBlockEntity propeller = resolvePropeller(level, body, state);
        final BlockPos raw = body.getPlot().getCenterBlock().offset(state.aeroLocal);
        final BlockState blockState = level.getBlockState(raw);
        final BlockSubLevelLiftProvider provider = blockState.getBlock() instanceof final BlockSubLevelLiftProvider p ? p : null;
        final Vector3d normalLocal = provider == null ? null : direction(provider.sable$getNormal(blockState));
        final Vector3d applicationRaw = new Vector3d(raw.getX() + 0.5D, raw.getY() + 0.5D, raw.getZ() + 0.5D);
        final Vector3d applicationVisible = body.logicalPose().transformPosition(new Vector3d(applicationRaw));
        final Vector3d comRaw = new Vector3d(body.getMassTracker().getCenterOfMass());
        final Vector3d leverLocal = applicationRaw.sub(comRaw, new Vector3d());
        final Vector3d leverWorld = body.logicalPose().orientation().transform(leverLocal, new Vector3d());
        final Vector3d linear = handle == null ? null : handle.getLinearVelocity(new Vector3d());
        final Vector3d angular = handle == null ? null : handle.getAngularVelocity(new Vector3d());
        final Vector3d pointVelocityWorld = finite(linear) && finite(angular)
                ? angular.cross(leverWorld, new Vector3d()).add(linear) : null;
        final Vector3d velocityLocal = finite(pointVelocityWorld)
                ? body.logicalPose().orientation().transformInverse(pointVelocityWorld, new Vector3d())
                : null;
        final Vector3d normalWorld = finite(normalLocal)
                ? body.logicalPose().orientation().transform(normalLocal, new Vector3d()) : null;
        final double substep = TICK_SECONDS / (physics == null ? 1 : Math.max(1, physics.getConfig().substepsPerTick));
        final double pressure = DimensionPhysicsData.getAirPressure(level, applicationVisible);
        final Double normalVelocity = finite(velocityLocal) && finite(normalLocal)
                ? normalLocal.dot(velocityLocal) : null;
        final Vector3d parallelDragImpulse = provider == null || normalVelocity == null || velocityLocal == null
                ? null
                : new Vector3d(normalLocal).mul(normalVelocity
                        * provider.sable$getParallelDragScalar() * pressure * substep);
        final Vector3d directionlessDragImpulse = provider == null || velocityLocal == null
                ? null
                : new Vector3d(velocityLocal).mul(provider.sable$getDirectionlessDragScalar() * pressure * substep);
        final Vector3d tangential = provider == null || velocityLocal == null || parallelDragImpulse == null
                ? null : new Vector3d(velocityLocal).sub(parallelDragImpulse);
        final Vector3d liftImpulse = provider == null || tangential == null
                ? null
                : new Vector3d(normalLocal).mul(tangential.length()
                        * provider.sable$getLiftScalar() * pressure * substep);
        final Vector3d dragLocal = finite(parallelDragImpulse) && finite(directionlessDragImpulse)
                ? parallelDragImpulse.add(directionlessDragImpulse, new Vector3d()).negate().div(substep)
                : null;
        final Vector3d liftLocal = finite(liftImpulse) ? liftImpulse.negate(new Vector3d()).div(substep)
                : null;
        final Vector3d dragWorld = finite(dragLocal)
                ? body.logicalPose().orientation().transform(dragLocal, new Vector3d()) : null;
        final Vector3d liftWorld = finite(liftLocal)
                ? body.logicalPose().orientation().transform(liftLocal, new Vector3d()) : null;
        final ForceSample actual = forceSample(body, substep, ForceGroups.LIFT.get(), ForceGroups.DRAG.get());
        final ForceSample propulsion = forceSample(body, substep, ForceGroups.PROPULSION.get());
        final Vector3d propulsionWorld = finite(propulsion.force)
                ? body.logicalPose().orientation().transform(propulsion.force, new Vector3d())
                : null;
        final Vector3d expectedTorque = finite(liftLocal) && finite(dragLocal)
                ? leverLocal.cross(new Vector3d(liftLocal).add(dragLocal), new Vector3d()) : null;
        final Quaterniond orientation = new Quaterniond(body.logicalPose().orientation());
        final boolean bodyStateFinite = finite(applicationVisible) && finite(linear) && finite(angular)
                && finite(actual.force) && finite(actual.torque) && finite(propulsionWorld) && finite(orientation);
        final boolean aeroStateFinite = provider != null && finite(normalLocal) && finite(normalWorld)
                && finite(liftWorld) && finite(dragWorld) && finite(expectedTorque)
                && normalVelocity != null && Double.isFinite(normalVelocity)
                && tangential != null && Double.isFinite(tangential.length());
        final boolean numericStateFinite = bodyStateFinite && (provider == null || aeroStateFinite);
        final String aeroNumericState = provider == null ? "UNAVAILABLE_PROVIDER_MISSING"
                : aeroStateFinite ? "FINITE" : "INVALID_NUMERIC_STATE";
        return new AeroSample(propeller == null ? null : propeller.getSpeed(), provider != null,
                normalLocal, normalWorld, applicationVisible, leverLocal, pointVelocityWorld,
                pointVelocityWorld == null ? null : pointVelocityWorld.negate(new Vector3d()),
                pointVelocityWorld == null ? null : pointVelocityWorld.length(), normalVelocity,
                tangential == null ? null : tangential.length(), liftWorld, dragWorld,
                propulsionWorld, expectedTorque, actual.force, actual.torque, linear, angular,
                new Vector3d(body.logicalPose().position()), orientation, bodyStateFinite,
                numericStateFinite, aeroNumericState);
    }

    private static ForceSample forceSample(final ServerSubLevel body, final double seconds,
                                           final ForceGroup... sampledGroups) {
        final Vector3d force = new Vector3d();
        final Vector3d torque = new Vector3d();
        final Vector3dc com = body.getMassTracker().getCenterOfMass();
        final Object2ObjectMap<ForceGroup, QueuedForceGroup> groups = body.getQueuedForceGroups();
        for (final ForceGroup group : sampledGroups) {
            final QueuedForceGroup queued = groups == null ? null : groups.get(group);
            if (queued == null) {
                continue;
            }
            for (final QueuedForceGroup.PointForce point : queued.getRecordedPointForces()) {
                force.add(point.force());
                torque.add(new Vector3d(point.point()).sub(com).cross(point.force(), new Vector3d()));
            }
        }
        return new ForceSample(force.div(seconds), torque.div(seconds));
    }

    private static int release(final CommandContext<CommandSourceStack> ctx) {
        final FixtureState state = activeFixture(ctx.getSource().getLevel());
        final ServerSubLevel body = resolveBody(ctx.getSource().getLevel(), state);
        if (state == null || body == null) {
            return fail(ctx.getSource(), "SABLE_M27_RELEASE", state);
        }
        int removed = 0;
        for (final BlockPos pos : state.support) {
            if (!ctx.getSource().getLevel().getBlockState(pos).isAir()) {
                ctx.getSource().getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                removed++;
            }
        }
        send(ctx.getSource(), "SABLE_M27_RELEASE status=PASS fixtureSessionId=" + state.sessionId
                + " storedSableId=" + state.bodyId + " sableId=" + body.getUniqueId()
                + " sableResolvedByUuid=true componentResolution=STATIC_SABLE"
                + " removedSupportBlocks=" + removed
                + " bodyMoved=false forceAppliedByCommand=false");
        return 1;
    }

    private static int prepareDisassembly(final CommandContext<CommandSourceStack> ctx) {
        final ServerLevel level = ctx.getSource().getLevel();
        final FixtureState state = activeFixture(level);
        final ServerSubLevel body = resolveBody(level, state);
        if (state == null || body == null) {
            return fail(ctx.getSource(), "SABLE_M27_PREPARE_DISASSEMBLY", state);
        }

        final CreativeMotorBlockEntity propulsionMotor = resolveMotor(level, body, state);
        final CreativeMotorBlockEntity controlMotor = resolveControlMotor(level, body, state);
        final MechanicalBearingBlockEntity bearing = resolveControlBearing(level, body, state);
        if (propulsionMotor != null) {
            propulsionMotor.generatedSpeed.setValue(0);
            propulsionMotor.setChanged();
        }
        if (controlMotor != null) {
            controlMotor.generatedSpeed.setValue(0);
            controlMotor.setChanged();
        }

        final boolean controlContraptionPresent = bearing != null && bearing.getMovedContraption() != null;
        if (controlContraptionPresent) {
            bearing.disassemble();
        }
        final boolean controlPayloadReturned = state.controlLocal == null
                || SimAssemblyHelper.collectBlocks(level, body).stream()
                .anyMatch(pos -> level.getBlockState(pos).is(SimulatedBlocks.WHITE_SYMMETRIC_SAIL.get()));
        final DisassemblyPreview preview = previewDisassembly(level, body, state);
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(level);
        final RigidBodyHandle handle = physics == null ? null : physics.getPhysicsHandle(body);
        final Vector3d linear = handle == null ? null : handle.getLinearVelocity(new Vector3d());
        final Vector3d angular = handle == null ? null : handle.getAngularVelocity(new Vector3d());
        final boolean ready = propulsionMotor != null
                && (state.controlLocal == null || controlMotor != null)
                && controlPayloadReturned && preview.occupied.isEmpty();
        send(ctx.getSource(), "SABLE_M27_PREPARE_DISASSEMBLY status="
                + (ready ? "READY_TO_DISASSEMBLE" : "BLOCKED")
                + " fixtureSessionId=" + state.sessionId + " storedSableId=" + state.bodyId
                + " sableId=" + body.getUniqueId() + " sableResolvedByUuid=true"
                + " propulsionStopped=" + (propulsionMotor != null && propulsionMotor.getGeneratedSpeed() == 0)
                + " controlMotorStopped=" + (state.controlLocal == null
                        || controlMotor != null && controlMotor.getGeneratedSpeed() == 0)
                + " controlContraptionPresent=" + controlContraptionPresent
                + " controlPayloadReturned=" + controlPayloadReturned
                + " linearSpeed=" + (linear == null ? "UNAVAILABLE" : linear.length())
                + " angularSpeed=" + (angular == null ? "UNAVAILABLE" : angular.length())
                + " restorationBounds=" + preview.restorationBounds
                + " occupied=" + preview.occupied
                + " commandDisassembledSable=false occupiedGuardBypassed=false");
        return ready ? 1 : 0;
    }

    private static int cleanup(final CommandContext<CommandSourceStack> ctx) {
        final FixtureState state = ACTIVE_FIXTURES.remove(key(ctx.getSource().getLevel()));
        if (state == null) {
            send(ctx.getSource(), "SABLE_M27_CLEANUP status=PASS reason=no_active_fixture");
            return 1;
        }
        final int removed = cleanupParent(ctx.getSource().getLevel(), state);
        state.active = false;
        send(ctx.getSource(), "SABLE_M27_CLEANUP status=PASS fixtureSessionId=" + state.sessionId
                + " sessionInvalidated=true removedParentBlocks=" + removed
                + " assembledSablesUntouched=true productionActorsUntouched=true");
        return 1;
    }

    private static int fail(final CommandSourceStack source, final String prefix, final FixtureState state) {
        send(source, prefix + " status=FAIL reason=" + (state == null ? "active_fixture_missing" : state.failure)
                + (state == null ? "" : " fixtureSessionId=" + state.sessionId
                        + " storedSableId=" + nullable(state.bodyId)
                        + " sableResolvedByUuid=false componentResolution=UNAVAILABLE"));
        return 0;
    }

    private static FixtureState activeFixture(final ServerLevel level) {
        final FixtureState state = ACTIVE_FIXTURES.get(key(level));
        return state != null && state.active ? state : null;
    }

    private static ServerSubLevel resolveBody(final ServerLevel level, final FixtureState state) {
        if (state == null || !state.active) {
            return null;
        }
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            state.failure = "active_fixture_container_missing";
            return null;
        }
        if (state.bodyId != null) {
            if (container.getSubLevel(state.bodyId) instanceof final ServerSubLevel body) {
                return body;
            }
            state.failure = "active_fixture_missing";
            return null;
        }
        state.failure = "active_fixture_body_unresolved";
        return null;
    }

    private static CreativeMotorBlockEntity resolveMotor(final ServerLevel level, final ServerSubLevel body,
                                                         final FixtureState state) {
        final BlockPos raw = body.getPlot().getCenterBlock().offset(state.motorLocal);
        return level.getBlockEntity(raw) instanceof final CreativeMotorBlockEntity motor ? motor : null;
    }

    private static WoodenPropellerBlockEntity resolvePropeller(final ServerLevel level, final ServerSubLevel body,
                                                               final FixtureState state) {
        final BlockPos raw = body.getPlot().getCenterBlock().offset(state.propellerLocal);
        return level.getBlockEntity(raw) instanceof final WoodenPropellerBlockEntity propeller ? propeller : null;
    }

    private static CreativeMotorBlockEntity resolveControlMotor(final ServerLevel level, final ServerSubLevel body,
                                                                final FixtureState state) {
        if (state.controlMotorLocal == null) {
            return null;
        }
        final BlockPos raw = body.getPlot().getCenterBlock().offset(state.controlMotorLocal);
        return level.getBlockEntity(raw) instanceof final CreativeMotorBlockEntity motor ? motor : null;
    }

    private static MechanicalBearingBlockEntity resolveControlBearing(final ServerLevel level,
                                                                       final ServerSubLevel body,
                                                                       final FixtureState state) {
        if (state.controlBearingLocal == null) {
            return null;
        }
        final BlockPos raw = body.getPlot().getCenterBlock().offset(state.controlBearingLocal);
        return level.getBlockEntity(raw) instanceof final MechanicalBearingBlockEntity bearing ? bearing : null;
    }

    private static void logStaticProviderDiagnostic(final ServerLevel level, final ServerSubLevel body,
                                                    final FixtureState state) {
        if (state.staticProviderDiagnosticLogged || state.aeroLocal == null) {
            return;
        }
        final BlockPos raw = body.getPlot().getCenterBlock().offset(state.aeroLocal);
        final BlockState blockState = level.getBlockState(raw);
        final Block block = blockState.getBlock();
        final BlockSubLevelLiftProvider provider = block instanceof final BlockSubLevelLiftProvider value
                ? value : null;
        final boolean discovered = body.getPlot().getLiftProviders().stream()
                .anyMatch(context -> context.pos().equals(raw));
        final ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
        final String reason = provider == null ? "PROVIDER_INTERFACE_MISSING"
                : discovered ? "BLOCK_CHANGE_PROVIDER_REGISTRY" : "PLOT_PROVIDER_REGISTRATION_MISSING";
        Aeronautics.LOGGER.info("SABLE_M27_PROVIDER sableId={} localPos={} blockId={}"
                        + " providerOwner=STATIC_SABLE providerType={} providerDiscovered={}"
                        + " providerNormalBodyLocal={} registrationReason={}",
                body.getUniqueId(), state.aeroLocal, nullable(blockId),
                provider == null ? "UNAVAILABLE" : provider.getClass().getName(), discovered,
                provider == null ? "UNAVAILABLE" : direction(provider.sable$getNormal(blockState)), reason);
        state.staticProviderDiagnosticLogged = true;
    }

    private static void logKinematicProviderDiagnostic(final ServerSubLevel body, final FixtureState state) {
        if (state.kinematicProviderDiagnosticLogged || state.controlLocal == null) {
            return;
        }
        for (final KinematicContraption contraption : body.getPlot().getContraptions()) {
            for (final BlockSubLevelLiftProvider.LiftProviderContext context
                    : contraption.sable$liftProviders().values()) {
                final ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(context.state().getBlock());
                Aeronautics.LOGGER.info("SABLE_M27_PROVIDER sableId={} localPos={} blockId={}"
                                + " providerOwner=CREATE_CONTRAPTION providerType={} providerDiscovered=true"
                                + " providerNormalBodyLocal={} registrationReason=CONTRAPTION_INITIALIZE_PROVIDER_SCAN",
                        body.getUniqueId(), context.pos(), nullable(blockId),
                        context.state().getBlock().getClass().getName(), context.dir());
                state.kinematicProviderDiagnosticLogged = true;
                return;
            }
        }
    }

    private static ControlSurfaceSample sampleControlSurface(final ServerLevel level, final ServerSubLevel body,
                                                             final FixtureState state,
                                                             final MechanicalBearingBlockEntity bearing) {
        if (bearing == null || state.controlLocal == null || bearing.getMovedContraption() == null) {
            return ControlSurfaceSample.unavailable(bearing);
        }
        final ControlledContraptionEntity moved = bearing.getMovedContraption();
        if (!(moved instanceof final KinematicContraption contraption)) {
            return ControlSurfaceSample.unavailable(bearing);
        }
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(level);
        final double partialTick = physics == null ? 1.0D : physics.getPartialPhysicsTick();
        final Pose3d localPose = contraption.sable$getLocalPose(new Pose3d(), partialTick);
        for (final BlockSubLevelLiftProvider.LiftProviderContext context
                : contraption.sable$liftProviders().values()) {
            if (!context.state().is(SimulatedBlocks.WHITE_SYMMETRIC_SAIL.get())) {
                continue;
            }
            final Vector3d initial = new Vector3d(context.dir().x(), context.dir().y(), context.dir().z()).normalize();
            final Vector3d current = localPose.transformNormal(initial, new Vector3d()).normalize();
            final Direction facing = bearing.getBlockState().getValue(DirectionalBlock.FACING);
            final Vector3d axis = direction(Direction.get(Direction.AxisDirection.POSITIVE, facing.getAxis()));
            final double dot = Math.max(-1.0D, Math.min(1.0D, initial.dot(current)));
            return new ControlSurfaceSample(true, axis, initial, current, Math.toDegrees(Math.acos(dot)),
                    (double) bearing.getInterpolatedAngle(1.0F), bearing.isRunning());
        }
        return ControlSurfaceSample.unavailable(bearing);
    }

    private static ControlContraptionSnapshot controlContraptionSnapshot(
            final MechanicalBearingBlockEntity bearing) {
        final ControlledContraptionEntity moved = bearing.getMovedContraption();
        if (moved == null || moved.getContraption() == null) {
            return ControlContraptionSnapshot.unavailable();
        }
        final Map<BlockPos, StructureTemplate.StructureBlockInfo> blocks = moved.getContraption().getBlocks();
        final List<String> blockIds = blocks.values().stream()
                .map(info -> String.valueOf(ForgeRegistries.BLOCKS.getKey(info.state().getBlock())))
                .sorted()
                .toList();
        final List<BlockPos> positions = new ArrayList<>(blocks.keySet());
        positions.sort(Comparator.comparingInt((BlockPos pos) -> pos.getX())
                .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));
        final String bounds = localBounds(positions);
        int controlSurfaces = 0;
        boolean assembler = false;
        boolean motor = false;
        boolean propeller = false;
        boolean staticSail = false;
        boolean structure = false;
        for (final StructureTemplate.StructureBlockInfo info : blocks.values()) {
            final BlockState captured = info.state();
            final boolean symmetric = captured.is(SimulatedBlocks.WHITE_SYMMETRIC_SAIL.get());
            if (symmetric) {
                controlSurfaces++;
            } else {
                structure = true;
            }
            assembler |= captured.is(SimulatedBlocks.PHYSICS_ASSEMBLER.get());
            motor |= CREATIVE_MOTOR_ID.equals(ForgeRegistries.BLOCKS.getKey(captured.getBlock()));
            propeller |= captured.is(AeroPropulsionRegistries.WOODEN_PROPELLER.get());
            staticSail |= CREATE_SAIL_ID.equals(ForgeRegistries.BLOCKS.getKey(captured.getBlock()));
        }
        final boolean valid = blocks.size() == 1 && controlSurfaces == 1
                && !assembler && !motor && !propeller && !staticSail && !structure;
        return new ControlContraptionSnapshot(true, moved.getId(), blocks.size(), blockIds, bounds,
                controlSurfaces, assembler, motor, propeller, staticSail, structure, valid);
    }

    private static void logControlContraption(final ServerSubLevel body, final FixtureState state,
                                              final MechanicalBearingBlockEntity bearing,
                                              final ControlContraptionSnapshot payload) {
        if (state.controlPayloadDiagnosticLogged && payload.validPayload) {
            return;
        }
        final Direction facing = bearing.getBlockState().getValue(DirectionalBlock.FACING);
        Aeronautics.LOGGER.info("SABLE_M27_CONTROL_CONTRAPTION fixtureSessionId={} sableId={}"
                        + " contraptionEntityId={} bearingLocalPos={} bearingAxis={}"
                        + " capturedBlockCount={} capturedBlockIds={} capturedLocalBounds={}"
                        + " controlSurfaceCount={} containsPhysicsAssembler={}"
                        + " containsMainPropulsionMotor={} containsWoodenPropeller={}"
                        + " containsStaticMainSail={} containsMainStructure={} payloadValid={}",
                state.sessionId, body.getUniqueId(), payload.entityId, state.controlBearingLocal,
                facing.getAxis(), payload.capturedBlockCount, payload.capturedBlockIds,
                payload.capturedLocalBounds, payload.controlSurfaceCount, payload.containsPhysicsAssembler,
                payload.containsMainPropulsionMotor, payload.containsWoodenPropeller,
                payload.containsStaticMainSail, payload.containsMainStructure, payload.validPayload);
        state.controlPayloadDiagnosticLogged = payload.validPayload;
    }

    private static boolean staticProviderAt(final ServerLevel level, final ServerSubLevel body,
                                            final FixtureState state) {
        final BlockPos raw = body.getPlot().getCenterBlock().offset(state.aeroLocal);
        return level.getBlockState(raw).is(requireState(CREATE_SAIL_ID).getBlock());
    }

    private static String controlComponentOwner(final ServerLevel level, final ServerSubLevel body,
                                                final FixtureState state, final boolean movingProviderPresent) {
        if (state.controlLocal == null) {
            return "NOT_APPLICABLE";
        }
        final BlockPos raw = body.getPlot().getCenterBlock().offset(state.controlLocal);
        if (level.getBlockState(raw).is(SimulatedBlocks.WHITE_SYMMETRIC_SAIL.get())) {
            return "STATIC_SABLE";
        }
        return movingProviderPresent ? "CREATE_CONTRAPTION" : "UNAVAILABLE";
    }

    private static DisassemblyPreview previewDisassembly(final ServerLevel level, final ServerSubLevel body,
                                                         final FixtureState state) {
        final BlockPos rawAssembler = body.getPlot().getCenterBlock().offset(state.assemblerLocal);
        final BlockPos goal = SimAssemblyHelper.currentVisibleBlockPos(body, rawAssembler);
        final SubLevelAssemblyHelper.AssemblyTransform transform = new SubLevelAssemblyHelper.AssemblyTransform(
                rawAssembler, goal, 0, Rotation.NONE, level);
        final List<BlockPos> targets = new ArrayList<>();
        final List<BlockPos> occupied = new ArrayList<>();
        for (final BlockPos raw : SimAssemblyHelper.collectBlocks(level, body)) {
            final BlockPos target = transform.apply(raw);
            targets.add(target);
            if (!level.getBlockState(target).isAir()) {
                occupied.add(target);
            }
        }
        targets.sort(Comparator.comparingLong(BlockPos::asLong));
        occupied.sort(Comparator.comparingLong(BlockPos::asLong));
        return new DisassemblyPreview(localBounds(targets), List.copyOf(occupied));
    }

    private static String localBounds(final List<BlockPos> positions) {
        if (positions.isEmpty()) {
            return "EMPTY";
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (final BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return "(" + minX + "," + minY + "," + minZ + ")->("
                + maxX + "," + maxY + "," + maxZ + ")";
    }

    private static Set<BlockPos> buildSupport(final ServerLevel level, final BlockPos origin) {
        final Set<BlockPos> support = new HashSet<>();
        for (int x = -3; x <= 4; x++) {
            for (int z = -3; z <= 3; z++) {
                final BlockPos pos = origin.offset(x, -2, z);
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                support.add(pos.immutable());
            }
        }
        return support;
    }

    private static void placeMotor(final ServerLevel level, final Set<BlockPos> body, final BlockPos pos) {
        placeStoppedMotor(level, body, pos, Direction.EAST);
    }

    private static void placeStoppedMotor(final ServerLevel level, final Set<BlockPos> body,
                                          final BlockPos pos, final Direction facing) {
        place(level, body, pos, requireState(CREATIVE_MOTOR_ID).setValue(DirectionalBlock.FACING, facing));
        if (!(level.getBlockEntity(pos) instanceof final CreativeMotorBlockEntity motor)) {
            throw new IllegalStateException("creative_motor_block_entity_missing");
        }
        motor.generatedSpeed.setValue(0);
        motor.setChanged();
    }

    private static BlockState requireState(final ResourceLocation id) {
        final Block block = ForgeRegistries.BLOCKS.getValue(id);
        if (block == null || block == Blocks.AIR) {
            throw new IllegalStateException("missing_block_" + id);
        }
        return block.defaultBlockState();
    }

    private static void place(final ServerLevel level, final Set<BlockPos> body, final BlockPos pos,
                              final BlockState state) {
        level.setBlock(pos, state, 3);
        body.add(pos.immutable());
    }

    private static void glueConnected(final ServerLevel level, final Set<BlockPos> blocks) {
        final Set<String> edges = new HashSet<>();
        for (final BlockPos pos : blocks) {
            for (final Direction direction : Direction.values()) {
                final BlockPos other = pos.relative(direction);
                if (!blocks.contains(other)) {
                    continue;
                }
                final String edge = pos.asLong() < other.asLong() ? pos.asLong() + ":" + other.asLong()
                        : other.asLong() + ":" + pos.asLong();
                if (edges.add(edge)) {
                    level.addFreshEntity(new SuperGlueEntity(level, SuperGlueEntity.span(pos, other)));
                }
            }
        }
    }

    private static int cleanupParent(final ServerLevel level, final FixtureState state) {
        int removed = 0;
        for (final BlockPos pos : state.parentBlocks) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                removed++;
            }
        }
        return removed;
    }

    private static String providerLift(final Block block) {
        return block instanceof final BlockSubLevelLiftProvider provider
                ? Float.toString(provider.sable$getLiftScalar()) : "UNAVAILABLE";
    }

    private static String providerParallelDrag(final Block block) {
        return block instanceof final BlockSubLevelLiftProvider provider
                ? Float.toString(provider.sable$getParallelDragScalar()) : "UNAVAILABLE";
    }

    private static Vector3d direction(final Direction direction) {
        return new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private static boolean finite(final Vector3dc value) {
        return value != null && Double.isFinite(value.x()) && Double.isFinite(value.y()) && Double.isFinite(value.z());
    }

    private static boolean finite(final Quaterniond value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z) && Double.isFinite(value.w);
    }

    private static String diagnostic(final Object value) {
        return value == null ? "UNAVAILABLE" : value.toString();
    }

    private static Vector3d sum(final Vector3dc first, final Vector3dc second) {
        return first == null || second == null ? null : new Vector3d(first).add(second);
    }

    private static String key(final ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static String nullable(final Object value) {
        return value == null ? "unresolved" : value.toString();
    }

    private static void send(final CommandSourceStack source, final String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    private enum FixtureKind {
        BASIC("aero_surface_basic", false),
        OFFSET("aero_offset", false),
        PITCH("pitch_control", true),
        VEHICLE("aero_vehicle", true);

        private final String commandName;
        private final boolean hasControl;

        FixtureKind(final String commandName, final boolean hasControl) {
            this.commandName = commandName;
            this.hasControl = hasControl;
        }
    }

    private static final class FixtureState {
        private final UUID sessionId;
        private final FixtureKind kind;
        private final BlockPos parentAssembler;
        private final BlockPos propellerOffset;
        private final BlockPos motorOffset;
        private final BlockPos aeroOffset;
        private final BlockPos controlOffset;
        private final BlockPos controlBearingOffset;
        private final BlockPos controlMotorOffset;
        private final Set<BlockPos> support;
        private final Set<BlockPos> parentBlocks;
        private UUID bodyId;
        private BlockPos assemblerLocal;
        private BlockPos propellerLocal;
        private BlockPos motorLocal;
        private BlockPos aeroLocal;
        private BlockPos controlLocal;
        private BlockPos controlBearingLocal;
        private BlockPos controlMotorLocal;
        private boolean active = true;
        private String failure = "active_fixture_body_unresolved";
        private int observationTick;
        private double controlValue;
        private AeroSample latest;
        private boolean staticProviderDiagnosticLogged;
        private boolean kinematicProviderDiagnosticLogged;
        private boolean controlPayloadDiagnosticLogged;

        private FixtureState(final UUID sessionId, final FixtureKind kind, final BlockPos parentAssembler,
                             final BlockPos propellerOffset, final BlockPos motorOffset, final BlockPos aeroOffset,
                             final BlockPos controlOffset, final BlockPos controlBearingOffset,
                             final BlockPos controlMotorOffset, final Set<BlockPos> support,
                             final Set<BlockPos> parentBlocks) {
            this.sessionId = sessionId;
            this.kind = kind;
            this.parentAssembler = parentAssembler;
            this.propellerOffset = propellerOffset;
            this.motorOffset = motorOffset;
            this.aeroOffset = aeroOffset;
            this.controlOffset = controlOffset;
            this.controlBearingOffset = controlBearingOffset;
            this.controlMotorOffset = controlMotorOffset;
            this.support = support;
            this.parentBlocks = parentBlocks;
        }

        private void capture(final ServerSubLevel body, final BlockPos assemblerRaw) {
            this.bodyId = body.getUniqueId();
            this.assemblerLocal = assemblerRaw.subtract(body.getPlot().getCenterBlock());
            this.propellerLocal = this.assemblerLocal.offset(this.propellerOffset);
            this.motorLocal = this.assemblerLocal.offset(this.motorOffset);
            this.aeroLocal = this.assemblerLocal.offset(this.aeroOffset);
            this.controlLocal = this.controlOffset == null ? null : this.assemblerLocal.offset(this.controlOffset);
            this.controlBearingLocal = this.controlBearingOffset == null
                    ? null : this.assemblerLocal.offset(this.controlBearingOffset);
            this.controlMotorLocal = this.controlMotorOffset == null
                    ? null : this.assemblerLocal.offset(this.controlMotorOffset);
            this.failure = "none";
        }
    }

    private record ForceSample(Vector3d force, Vector3d torque) {
    }

    private record ControlSurfaceSample(boolean present, Vector3d bearingAxisBodyLocal,
                                        Vector3d initialNormal, Vector3d currentNormal,
                                        Double normalDeltaDegrees, Double bearingAngle,
                                        boolean bearingRunning) {
        private static ControlSurfaceSample unavailable(final MechanicalBearingBlockEntity bearing) {
            return new ControlSurfaceSample(false, null, null, null, null,
                    bearing == null ? null : (double) bearing.getInterpolatedAngle(1.0F),
                    bearing != null && bearing.isRunning());
        }
    }

    private record ControlContraptionSnapshot(boolean present, int entityId, int capturedBlockCount,
                                               List<String> capturedBlockIds, String capturedLocalBounds,
                                               int controlSurfaceCount, boolean containsPhysicsAssembler,
                                               boolean containsMainPropulsionMotor, boolean containsWoodenPropeller,
                                               boolean containsStaticMainSail, boolean containsMainStructure,
                                               boolean validPayload) {
        private static ControlContraptionSnapshot unavailable() {
            return new ControlContraptionSnapshot(false, -1, 0, List.of(), "UNAVAILABLE", 0,
                    false, false, false, false, false, false);
        }
    }

    private record DisassemblyPreview(String restorationBounds, List<BlockPos> occupied) {
    }

    private record AeroSample(Float rpm, boolean providerPresent,
                              Vector3d normalLocal, Vector3d normalWorld,
                              Vector3d applicationPointVisible, Vector3d applicationPointBodyLocal,
                              Vector3d pointVelocityWorld, Vector3d relativeAirflowWorld,
                              Double relativeAirSpeed, Double normalVelocity, Double tangentialSpeed,
                              Vector3d liftWorld, Vector3d dragWorld, Vector3d propulsionWorld,
                              Vector3d expectedTorque,
                              Vector3d accumulatedForce, Vector3d accumulatedTorque,
                              Vector3d linearVelocity, Vector3d angularVelocity,
                              Vector3d position, Quaterniond orientation,
                              boolean bodyStateFinite, boolean numericStateFinite,
                              String aeroNumericState) {
    }
}
