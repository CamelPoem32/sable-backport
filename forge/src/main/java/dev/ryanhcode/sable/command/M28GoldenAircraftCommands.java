package dev.ryanhcode.sable.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.IControlContraption.RotationMode;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.content.propulsion.WoodenPropellerBlock;
import dev.eriksonn.aeronautics.content.propulsion.WoodenPropellerBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.propeller.BlockEntitySubLevelPropellerActor;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixin.compatibility.create.contraptions.MechanicalBearingBlockEntityAccessor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlockEntity;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlock;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelDiagnostics;
import dev.simulated_team.simulated.index.SimulatedBlocks;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Read-only diagnostics for a manually built M28 aircraft. */
public final class M28GoldenAircraftCommands {
    private static final double TICK_SECONDS = 1.0D / 20.0D;
    private static final List<WheelExpectation> EXPECTED_WHEELS = List.of(
            new WheelExpectation("pitch", new BlockPos(-5, 1, -2), Direction.NORTH,
                    List.of(new BlockPos(-5, 0, -2)), new BlockPos(-5, 0, -1),
                    new BlockPos(-5, 0, 0), Direction.SOUTH, new BlockPos(-5, 0, 1), Direction.Axis.Y),
            new WheelExpectation("yaw", new BlockPos(-3, 1, -4), Direction.EAST,
                    List.of(new BlockPos(-3, 0, -4), new BlockPos(-5, 0, -4)), new BlockPos(-4, 0, -4),
                    new BlockPos(-5, 1, -4), Direction.UP, new BlockPos(-5, 2, -4), Direction.Axis.Z),
            new WheelExpectation("roll", new BlockPos(1, 1, 3), Direction.WEST,
                    List.of(new BlockPos(1, 0, 3)), new BlockPos(1, 0, 4),
                    new BlockPos(1, 0, 5), Direction.SOUTH, new BlockPos(1, 0, 6), Direction.Axis.Y));

    private M28GoldenAircraftCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m28")
                .then(Commands.literal("status").executes(M28GoldenAircraftCommands::status))
                .then(Commands.literal("validate_controls").executes(M28GoldenAircraftCommands::validateControls))
                .then(Commands.literal("inspect").executes(M28GoldenAircraftCommands::inspect)));
    }

    private static int status(final CommandContext<CommandSourceStack> context) {
        send(context.getSource(), "SABLE_M28_STATUS implementationRevision=" + Aeronautics.IMPLEMENTATION_REVISION
                + " m27=CLOSED_RUNTIME_PROVEN"
                + " controlArchitecture=STEERING_WHEEL_TO_CREATE_KINETICS_TO_M27_SURFACES"
                + " commands=READ_ONLY_ONLY"
                + " status=IMPLEMENTED_RUNTIME_REQUIRED");
        return 1;
    }

    private static int inspect(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        if (source.getEntity() == null) {
            send(source, "SABLE_M28_INSPECT status=FAIL reason=player_required");
            return 0;
        }

        final AircraftResolution resolution = resolveAircraft(source);
        send(source, "SABLE_M28_AIRCRAFT_RESOLUTION"
                + " supportOwner=" + id(resolution.supportOwner())
                + " targetOwner=" + id(resolution.targetOwner())
                + " resolvedSableId=" + id(resolution.body())
                + " resolutionMethod=" + resolution.method()
                + " playerOnSable=" + resolution.playerOnSable());
        final ServerSubLevel body = resolution.body();
        if (body == null) {
            send(source, "SABLE_M28_INSPECT status=FAIL reason=player_not_on_or_targeting_sable"
                    + " fixtureSessionDependency=false");
            return 0;
        }

        body.enableIndividualQueuedForcesTracking(true);
        final List<BlockPos> blocks = SimAssemblyHelper.collectBlocks(source.getLevel(), body);
        final List<String> wheels = new ArrayList<>();
        final List<String> propellers = new ArrayList<>();
        final Set<String> unexpectedWorldBlocks = new LinkedHashSet<>();
        int chestCount = 0;
        int redstoneComponentCount = 0;
        int kineticComponentCount = 0;
        int woodenPropellerCount = 0;
        int propulsionProviderCount = 0;
        int steeringWheelBlockCount = 0;
        BlockPos assemblerRaw = null;
        WoodenPropellerBlockEntity sampledPropeller = null;
        for (final BlockPos raw : blocks) {
            final BlockState state = source.getLevel().getBlockState(raw);
            final BlockEntity blockEntity = source.getLevel().getBlockEntity(raw);
            if (state.is(SimulatedBlocks.PHYSICS_ASSEMBLER.get())) {
                assemblerRaw = raw;
            }
            if (state.is(SimulatedBlocks.STEERING_WHEEL.get())) {
                steeringWheelBlockCount++;
            }
            if (blockEntity instanceof final SteeringWheelBlockEntity wheel) {
                wheels.add("{local=" + local(body, raw)
                        + ",angle=" + wheel.getAngle()
                        + ",target=" + wheel.getTargetAngle()
                        + ",generatedRpm=" + wheel.getGeneratedSpeed()
                        + ",held=" + wheel.isHeld()
                        + ",controller=" + nullable(wheel.getController()) + "}");
            }
            if (state.getBlock() instanceof WoodenPropellerBlock) {
                woodenPropellerCount++;
            }
            if (blockEntity instanceof final WoodenPropellerBlockEntity propeller) {
                final boolean actorRegistered = body.getPlot().getBlockEntityActor(raw)
                        instanceof BlockEntitySubLevelPropellerActor;
                propulsionProviderCount += actorRegistered ? 1 : 0;
                if (sampledPropeller == null) {
                    sampledPropeller = propeller;
                }
                propellers.add("{local=" + local(body, raw)
                        + ",kineticSpeed=" + propeller.getSpeed()
                        + ",effectiveSpeed=" + effectiveSpeed(propeller)
                        + ",active=" + propeller.isActive()
                        + ",actorRegistered=" + actorRegistered + "}");
            }
            final ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            if (blockId != null) {
                chestCount += blockId.getPath().endsWith("chest") ? 1 : 0;
                redstoneComponentCount += blockId.getPath().contains("lever")
                        || blockId.getPath().contains("redstone") ? 1 : 0;
                kineticComponentCount += "create".equals(blockId.getNamespace()) ? 1 : 0;
                if (looksLikeTerrain(state)) {
                    unexpectedWorldBlocks.add("{local=" + local(body, raw) + ",block=" + blockId + "}");
                }
            }
        }

        final List<BlockPos> actualWheelLocals = blocks.stream()
                .filter(raw -> source.getLevel().getBlockState(raw).is(SimulatedBlocks.STEERING_WHEEL.get()))
                .map(raw -> local(body, raw)).toList();
        for (int index = 0; index < EXPECTED_WHEELS.size(); index++) {
            sendWheelDiagnostic(source, body, assemblerRaw, index, EXPECTED_WHEELS.get(index), actualWheelLocals);
        }

        int controlProviderCount = 0;
        for (final KinematicContraption contraption : body.getPlot().getContraptions()) {
            controlProviderCount += contraption.sable$liftProviders().size();
        }
        final int staticProviderCount = body.getPlot().getLiftProviders().size();
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(source.getLevel());
        final RigidBodyHandle handle = physics == null ? null : physics.getPhysicsHandle(body);
        final boolean handleValid = handle != null && handle.isValid();
        final boolean rapierBodyPresent = physics != null && physics.getPipeline().isBodyRegistered(body);
        final String bodyType = !rapierBodyPresent
                ? "unavailable" : physics.getPipeline().getRigidBodyType(body);
        final Boolean bodySleeping = !rapierBodyPresent ? null : physics.getPipeline().isSleeping(body);
        final Vector3d linearVelocity = handleValid ? handle.getLinearVelocity(new Vector3d()) : null;
        final Vector3d angularVelocity = handleValid ? handle.getAngularVelocity(new Vector3d()) : null;
        final Vector3dc rawCenterOfMass = body.getMassTracker().getCenterOfMass();
        final Vector3d visibleCenterOfMass = rawCenterOfMass == null ? null
                : body.logicalPose().transformPosition(new Vector3d(rawCenterOfMass));
        final PropulsionDiagnostic propulsion = propulsionDiagnostic(body, sampledPropeller, physics);
        final boolean finite = handleValid && finite(body.logicalPose().position())
                && finite(linearVelocity) && finite(angularVelocity)
                && finite(visibleCenterOfMass) && propulsion.finite();
        final String runtimeState = runtimeState(handleValid, woodenPropellerCount, propulsionProviderCount,
                sampledPropeller, propulsion);
        final boolean providerReady = woodenPropellerCount > 0 && propulsionProviderCount > 0;

        send(source, "SABLE_M28_INSPECT status=" + (finite && providerReady ? "PASS" : "FAIL")
                + " runtimeState=" + runtimeState
                + " sableId=" + body.getUniqueId()
                + " bodyPresent=true bodyHandleValid=" + handleValid
                + " rapierBodyPresent=" + rapierBodyPresent
                + " bodyStaticOrDynamic=" + bodyType
                + " bodyDynamic=" + "dynamic".equals(bodyType)
                + " bodySleeping=" + nullable(bodySleeping)
                + " contactCount=unavailable"
                + " mass=" + body.getMassTracker().getMass()
                + " centerOfMass=" + rawCenterOfMass
                + " centerOfMassVisible=" + visibleCenterOfMass
                + " position=" + body.logicalPose().position()
                + " linearVelocity=" + nullable(linearVelocity)
                + " angularVelocity=" + nullable(angularVelocity)
                + " woodenPropellerCount=" + woodenPropellerCount
                + " propulsionProviderCount=" + propulsionProviderCount
                + " propellerKineticSpeed=" + propulsion.kineticSpeed()
                + " propellerEffectiveSpeed=" + propulsion.effectiveSpeed()
                + " thrustMagnitude=" + propulsion.productionForceBodyLocal().length()
                + " thrustForceBodyLocal=" + propulsion.productionForceBodyLocal()
                + " thrustForceWorld=" + propulsion.productionForceWorld()
                + " accumulatedPropulsionForceBodyLocal=" + propulsion.accumulatedForceBodyLocal()
                + " accumulatedPropulsionTorqueBodyLocal=" + propulsion.accumulatedTorqueBodyLocal()
                + " zeroThrustReason=" + propulsion.zeroThrustReason()
                + " propulsionState=" + propellers
                + " aeroProviderCount=" + (staticProviderCount + controlProviderCount)
                + " staticAeroProviderCount=" + staticProviderCount
                + " controlSurfaceCount=" + controlProviderCount
                + " steeringWheelCount=" + steeringWheelBlockCount
                + " steeringWheelBeCount=" + wheels.size()
                + " steeringWheels=" + wheels
                + " activeControlOwner=" + (wheels.isEmpty() ? "none" : "ONBOARD_STEERING_WHEEL")
                + " controlSableResolved=" + !wheels.isEmpty()
                + " chestCount=" + chestCount
                + " redstoneComponentCount=" + redstoneComponentCount
                + " createComponentCount=" + kineticComponentCount
                + " storedBlockCount=" + blocks.size()
                + " selectedBounds=" + body.getPlot().getBoundingBox()
                + " unexpectedWorldBlocks=" + unexpectedWorldBlocks
                + " fixtureSessionDependency=false"
                + " finite=" + finite);
        return finite && providerReady ? 1 : 0;
    }

    private static int validateControls(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        if (source.getEntity() == null) {
            send(source, "SABLE_M28_CONTROL_PREFLIGHT overallStatus=FAIL reason=player_required");
            return 0;
        }
        final ServerSubLevel body = resolveAircraft(source).body();
        if (body == null) {
            send(source, "SABLE_M28_CONTROL_PREFLIGHT overallStatus=FAIL reason=player_not_on_or_targeting_sable");
            return 0;
        }
        final Map<BlockPos, KineticBlockEntity> kinetics = new HashMap<>();
        final Map<BlockPos, SteeringWheelBlockEntity> wheels = new HashMap<>();
        final Set<BlockPos> actualWheelBlocks = new LinkedHashSet<>();
        BlockPos assemblerRaw = null;
        for (final BlockPos raw : SimAssemblyHelper.collectBlocks(source.getLevel(), body)) {
            final BlockState state = source.getLevel().getBlockState(raw);
            if (state.is(SimulatedBlocks.PHYSICS_ASSEMBLER.get())) {
                assemblerRaw = raw;
            }
            final BlockEntity be = source.getLevel().getBlockEntity(raw);
            if (be instanceof final KineticBlockEntity kinetic) {
                kinetics.put(raw.immutable(), kinetic);
            }
            if (state.is(SimulatedBlocks.STEERING_WHEEL.get())) {
                actualWheelBlocks.add(raw.immutable());
                if (be instanceof final SteeringWheelBlockEntity wheel) {
                    wheels.put(raw.immutable(), wheel);
                }
            }
        }
        final List<BlockPos> actualPositions = actualWheelBlocks.stream().map(raw -> local(body, raw))
                .sorted(Comparator.comparingLong(BlockPos::asLong)).toList();
        send(source, "SABLE_M28_CONTROL_PREFLIGHT sableId=" + body.getUniqueId()
                + " actualWheelSableLocalPositions=" + actualPositions);
        for (final BlockPos raw : actualWheelBlocks.stream()
                .sorted(Comparator.comparingLong(BlockPos::asLong)).toList()) {
            final BlockState state = source.getLevel().getBlockState(raw);
            final SteeringWheelBlockEntity wheel = wheels.get(raw);
            send(source, "SABLE_M28_CONTROL_WHEEL localPos=" + local(body, raw)
                    + " facing=" + state.getValue(SteeringWheelBlock.FACING)
                    + " outputAxis=" + ((IRotate) state.getBlock()).getRotationAxis(state)
                    + " outputFace=" + (state.getValue(SteeringWheelBlock.ON_FLOOR) ? Direction.DOWN : Direction.UP)
                    + " blockPresent=true bePresent=" + (wheel != null)
                    + " generatedSpeedNow=" + (wheel == null ? "unavailable" : wheel.getGeneratedSpeed()));
        }
        if (assemblerRaw == null) {
            send(source, "SABLE_M28_CONTROL_PREFLIGHT overallStatus=FAIL reason=physics_assembler_missing");
            return 0;
        }
        final BlockPos assembler = assemblerRaw;
        send(source, "SABLE_M28_CONTROL_BLUEPRINT actualWheelAssemblerRelativePositions="
                + actualWheelBlocks.stream().map(raw -> raw.subtract(assembler))
                .sorted(Comparator.comparingLong(BlockPos::asLong)).toList()
                + " expectedWheelAssemblerRelativePositions="
                + EXPECTED_WHEELS.stream().map(WheelExpectation::wheelOffset).toList());
        boolean allPass = actualWheelBlocks.size() == EXPECTED_WHEELS.size();
        final Set<BlockPos> expectedWheelPositions = new LinkedHashSet<>();
        for (final WheelExpectation expected : EXPECTED_WHEELS) {
            expectedWheelPositions.add(assembler.offset(expected.wheelOffset()));
        }
        final List<BlockPos> unmatched = actualWheelBlocks.stream()
                .filter(raw -> !expectedWheelPositions.contains(raw))
                .map(raw -> raw.subtract(assembler)).sorted(Comparator.comparingLong(BlockPos::asLong)).toList();
        for (final WheelExpectation expected : EXPECTED_WHEELS) {
            final Set<String> failures = new LinkedHashSet<>();
            final BlockPos wheelRaw = assembler.offset(expected.wheelOffset());
            final SteeringWheelBlockEntity wheel = wheels.get(wheelRaw);
            final BlockState wheelState = source.getLevel().getBlockState(wheelRaw);
            if (wheel == null) {
                failures.add(!wheelState.is(SimulatedBlocks.STEERING_WHEEL.get())
                        ? unmatched.isEmpty() ? "MISSING_WHEEL" : "WRONG_WHEEL_POSITION"
                        : "MISSING_WHEEL_BLOCK_ENTITY");
            } else if (wheelState.getValue(SteeringWheelBlock.FACING) != expected.wheelFacing()) {
                failures.add("WRONG_WHEEL_FACING");
            }
            final List<BlockPos> route = new ArrayList<>();
            route.add(wheelRaw);
            for (final BlockPos offset : expected.gearboxOffsets()) {
                final BlockPos raw = assembler.offset(offset);
                route.add(raw);
                if (!"create:gearbox".equals(blockId(source, raw))) {
                    failures.add("MISSING_GEARBOX");
                }
            }
            final BlockPos shaftRaw = assembler.offset(expected.shaftOffset());
            route.add(expected.gearboxOffsets().size() == 2 ? 2 : route.size(), shaftRaw);
            if (!"create:shaft".equals(blockId(source, shaftRaw))) {
                failures.add("MISSING_SHAFT");
            }
            final BlockPos bearingRaw = assembler.offset(expected.bearingOffset());
            route.add(bearingRaw);
            final BlockEntity bearingBe = source.getLevel().getBlockEntity(bearingRaw);
            final MechanicalBearingBlockEntity bearing = bearingBe instanceof final MechanicalBearingBlockEntity found
                    ? found : null;
            if (bearing == null) {
                failures.add("MISSING_BEARING");
            }
            final BlockState bearingState = source.getLevel().getBlockState(bearingRaw);
            final Direction bearingFacing = bearingState.hasProperty(BlockStateProperties.FACING)
                    ? bearingState.getValue(BlockStateProperties.FACING) : null;
            if (bearing != null && bearingFacing != expected.bearingFacing()) {
                failures.add("WRONG_BEARING_FACING");
            }
            final RotationMode mode = bearing == null ? null
                    : ((MechanicalBearingBlockEntityAccessor) bearing).sable$getMovementMode().get();
            if (bearing != null && mode != RotationMode.ROTATE_NEVER_PLACE) {
                failures.add("WRONG_BEARING_MOVEMENT_MODE");
            }
            final List<String> payload = bearingPayload(source, bearingRaw, bearing);
            final long controlSurfaceCount = payload.stream()
                    .filter(id -> id.equals("simulated:white_symmetric_sail")).count();
            final boolean payloadTypeValid = payload.size() == 1
                    && payload.get(0).equals("simulated:white_symmetric_sail");
            final Direction.Axis payloadAxis = bearingPayloadAxis(source, bearingRaw, bearing);
            if (!payloadTypeValid) {
                failures.add(payload.isEmpty() ? "CONTROL_SAIL_MISSING" : "WRONG_CONTROL_PAYLOAD");
            }
            if (payloadTypeValid && payloadAxis != expected.sailAxis()) {
                failures.add("WRONG_CONTROL_PAYLOAD_AXIS");
            }
            final Set<KineticBlockEntity> reachable = wheel == null ? Set.of() : reachableKinetics(wheel, kinetics);
            final long generators = reachable.stream().filter(member -> member instanceof SteeringWheelBlockEntity
                    || member.isSource() || blockId(source, member.getBlockPos()).contains("creative_motor")).count();
            final long wheelGenerators = reachable.stream().filter(SteeringWheelBlockEntity.class::isInstance).count();
            final long bearings = reachable.stream().filter(MechanicalBearingBlockEntity.class::isInstance).count();
            final long motors = reachable.stream().filter(member -> blockId(source, member.getBlockPos())
                    .contains("creative_motor")).count();
            final boolean touchesPropulsion = reachable.stream().anyMatch(member ->
                    member.getBlockPos().equals(assembler.offset(3, 1, 0))
                    || blockId(source, member.getBlockPos()).contains("propeller"));
            final boolean touchesDrill = reachable.stream().anyMatch(member ->
                    member.getBlockPos().equals(assembler.offset(2, 0, -2))
                    || blockId(source, member.getBlockPos()).contains("drill"));
            if (wheelGenerators > 1 || bearings > 1) {
                failures.add("CONTROL_NETWORK_MERGED");
            }
            if (motors > 0 || generators > 1) {
                failures.add("EXTRA_GENERATOR");
            }
            if (touchesPropulsion) {
                failures.add("PROPULSION_NETWORK_CONNECTED");
            }
            if (touchesDrill) {
                failures.add("DRILL_NETWORK_CONNECTED");
            }
            for (int i = 1; i < route.size(); i++) {
                final KineticBlockEntity from = kinetics.get(route.get(i - 1));
                final KineticBlockEntity to = kinetics.get(route.get(i));
                if (from != null && to != null && !RotationPropagator.isConnected(from, to)) {
                    failures.add(i == 1 ? "WRONG_GEARBOX_AXIS" : "WRONG_SHAFT_AXIS");
                }
            }
            if (wheel != null && !reachable.contains(bearing)) {
                failures.add("CONTROL_NETWORK_DISCONNECTED");
            }
            allPass &= failures.isEmpty();
            send(source, "SABLE_M28_CONTROL_CHANNEL channel=" + expected.channel()
                    + " status=" + (failures.isEmpty() ? "PASS" : "FAIL")
                    + " failures=" + failures
                    + " expectedWheel=" + expected.wheelOffset()
                    + " actualWheel=" + (wheel == null ? "unavailable" : wheel.getBlockPos().subtract(assembler))
                    + " actualWheelSableLocal=" + (wheel == null ? "unavailable"
                    : local(body, wheel.getBlockPos()))
                    + " unmatchedWheels=" + unmatched
                    + " expectedRouteAssemblerRelative="
                    + route.stream().map(raw -> raw.subtract(assembler)).toList()
                    + " routeConnections=" + routeConnections(body, route, kinetics)
                    + " bearingLocalPos=" + expected.bearingOffset()
                    + " bearingFacing=" + nullable(bearingFacing)
                    + " movementMode=" + nullable(mode)
                    + " bearingHoldConfigured=" + (mode == RotationMode.ROTATE_NEVER_PLACE)
                    + " expectedMovementMode=ROTATE_NEVER_PLACE"
                    + " payloadLocalPos=" + expected.sailOffset()
                    + " expectedPayloadBlockId=simulated:white_symmetric_sail"
                    + " actualPayloadBlockId=" + payload
                    + " payloadAxis=" + nullable(payloadAxis)
                    + " expectedPayloadAxis=" + expected.sailAxis()
                    + " payloadIsolated=" + (payload.size() == 1)
                    + " controlSurfaceCount=" + controlSurfaceCount
                    + " payloadTypeValid=" + payloadTypeValid
                    + " generatorCount=" + generators + " bearingCount=" + bearings
                    + " steeringWheelGeneratorCount=" + wheelGenerators + " creativeMotorCount=" + motors
                    + " networkTouchesOtherControlChannel=" + (wheelGenerators > 1)
                    + " networkTouchesPropulsionNetwork=" + touchesPropulsion
                    + " networkTouchesDrillNetwork=" + touchesDrill
                    + " reachableKinetics=" + reachable.stream().map(member -> "{localPos="
                    + local(body, member.getBlockPos()) + ",blockId=" + blockId(source, member.getBlockPos())
                    + ",axis=" + kineticAxis(source, member) + ",isGenerator="
                    + (member instanceof SteeringWheelBlockEntity || member.isSource())
                    + ",currentSpeed=" + member.getSpeed() + ",networkId=" + nullable(member.network)
                    + "}").sorted().toList());
        }
        send(source, "SABLE_M28_CONTROL_PREFLIGHT overallStatus=" + (allPass ? "PASS" : "FAIL")
                + " unmatchedWheels=" + unmatched + " qualificationAllowed=" + allPass);
        return allPass ? 1 : 0;
    }

    private static Set<KineticBlockEntity> reachableKinetics(final KineticBlockEntity start,
                                                              final Map<BlockPos, KineticBlockEntity> kinetics) {
        final Set<KineticBlockEntity> visited = new LinkedHashSet<>();
        final ArrayDeque<KineticBlockEntity> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            final KineticBlockEntity current = queue.removeFirst();
            for (final KineticBlockEntity candidate : kinetics.values()) {
                if (!visited.contains(candidate) && RotationPropagator.isConnected(current, candidate)) {
                    visited.add(candidate);
                    queue.addLast(candidate);
                }
            }
        }
        return visited;
    }

    private static List<String> routeConnections(final ServerSubLevel body, final List<BlockPos> route,
                                                 final Map<BlockPos, KineticBlockEntity> kinetics) {
        final List<String> connections = new ArrayList<>();
        for (int index = 1; index < route.size(); index++) {
            final BlockPos from = route.get(index - 1);
            final BlockPos to = route.get(index);
            final KineticBlockEntity fromKinetic = kinetics.get(from);
            final KineticBlockEntity toKinetic = kinetics.get(to);
            connections.add(local(body, from) + "->" + local(body, to) + ":"
                    + (fromKinetic != null && toKinetic != null
                    && RotationPropagator.isConnected(fromKinetic, toKinetic)));
        }
        return connections;
    }

    private static List<String> bearingPayload(final CommandSourceStack source, final BlockPos raw,
                                               final MechanicalBearingBlockEntity bearing) {
        if (bearing != null && bearing.getMovedContraption() != null
                && bearing.getMovedContraption().getContraption() != null) {
            return bearing.getMovedContraption().getContraption().getBlocks().values().stream()
                    .map(StructureTemplate.StructureBlockInfo::state)
                    .map(state -> String.valueOf(ForgeRegistries.BLOCKS.getKey(state.getBlock())))
                    .sorted().toList();
        }
        final BlockState bearingState = source.getLevel().getBlockState(raw);
        if (!bearingState.hasProperty(BlockStateProperties.FACING)) {
            return List.of();
        }
        final BlockState state = source.getLevel().getBlockState(raw.relative(
                bearingState.getValue(BlockStateProperties.FACING)));
        return state.isAir() ? List.of() : List.of(String.valueOf(ForgeRegistries.BLOCKS.getKey(state.getBlock())));
    }

    private static Direction.Axis bearingPayloadAxis(final CommandSourceStack source, final BlockPos raw,
                                                      final MechanicalBearingBlockEntity bearing) {
        final BlockState state;
        if (bearing != null && bearing.getMovedContraption() != null
                && bearing.getMovedContraption().getContraption() != null
                && bearing.getMovedContraption().getContraption().getBlocks().size() == 1) {
            state = bearing.getMovedContraption().getContraption().getBlocks().values().iterator().next().state();
        } else {
            final BlockState bearingState = source.getLevel().getBlockState(raw);
            if (!bearingState.hasProperty(BlockStateProperties.FACING)) {
                return null;
            }
            state = source.getLevel().getBlockState(raw.relative(bearingState.getValue(BlockStateProperties.FACING)));
        }
        return state.hasProperty(BlockStateProperties.AXIS) ? state.getValue(BlockStateProperties.AXIS) : null;
    }

    private static String blockId(final CommandSourceStack source, final BlockPos raw) {
        return String.valueOf(ForgeRegistries.BLOCKS.getKey(source.getLevel().getBlockState(raw).getBlock()));
    }

    private static String kineticAxis(final CommandSourceStack source, final KineticBlockEntity member) {
        final BlockState state = source.getLevel().getBlockState(member.getBlockPos());
        return state.getBlock() instanceof final IRotate rotate
                ? nullable(rotate.getRotationAxis(state)) : "unavailable";
    }

    private static void sendWheelDiagnostic(final CommandSourceStack source, final ServerSubLevel body,
                                            final BlockPos assemblerRaw, final int index,
                                            final WheelExpectation expectation,
                                            final List<BlockPos> actualWheelLocals) {
        if (assemblerRaw == null) {
            send(source, "SABLE_M28_STEERING_WHEEL index=" + index + " channel=" + expectation.channel()
                    + " status=UNAVAILABLE reason=physics_assembler_missing");
            return;
        }
        final BlockPos wheelRaw = assemblerRaw.offset(expectation.wheelOffset());
        final BlockPos gearboxRaw = assemblerRaw.offset(expectation.gearboxOffsets().get(0));
        final BlockPos shaftRaw = assemblerRaw.offset(expectation.shaftOffset());
        final BlockPos bearingRaw = assemblerRaw.offset(expectation.bearingOffset());
        final BlockState wheelState = source.getLevel().getBlockState(wheelRaw);
        final BlockEntity foundWheel = source.getLevel().getBlockEntity(wheelRaw);
        final SteeringWheelBlockEntity wheel = foundWheel instanceof final SteeringWheelBlockEntity found ? found : null;
        final boolean blockPresent = wheelState.is(SimulatedBlocks.STEERING_WHEEL.get());
        final Direction outputFace = blockPresent && wheelState.getValue(SteeringWheelBlock.ON_FLOOR)
                ? Direction.DOWN : Direction.UP;
        final Direction.Axis outputAxis = blockPresent
                ? ((IRotate) wheelState.getBlock()).getRotationAxis(wheelState) : null;
        final BlockState gearboxState = source.getLevel().getBlockState(gearboxRaw);
        final BlockEntity gearboxEntity = source.getLevel().getBlockEntity(gearboxRaw);
        final KineticBlockEntity gearbox = gearboxEntity instanceof final KineticBlockEntity kinetic ? kinetic : null;
        final boolean gearboxAccepted = gearboxState.getBlock() instanceof final IRotate rotate
                && rotate.hasShaftTowards(source.getLevel(), gearboxRaw, gearboxState, outputFace.getOpposite());
        final BlockEntity shaftEntity = source.getLevel().getBlockEntity(shaftRaw);
        final KineticBlockEntity shaft = shaftEntity instanceof final KineticBlockEntity kinetic ? kinetic : null;
        final BlockEntity bearingEntity = source.getLevel().getBlockEntity(bearingRaw);
        final MechanicalBearingBlockEntity bearing = bearingEntity instanceof final MechanicalBearingBlockEntity found
                ? found : null;
        final SteeringWheelBlockEntity.ControlLifecycleSnapshot controlLifecycle = wheel == null
                ? null : wheel.getControlLifecycleSnapshot();
        final boolean networkConnected = wheel != null && gearbox != null && wheel.network != null
                && wheel.network.equals(gearbox.network);
        final KineticNetwork network = wheel != null && wheel.hasNetwork() ? wheel.getOrCreateNetwork() : null;
        final ResourceLocation adjacentId = ForgeRegistries.BLOCKS.getKey(gearboxState.getBlock());
        final boolean numericFinite = wheel == null || Float.isFinite(wheel.getTargetAngle())
                && Float.isFinite(wheel.getAngle()) && Float.isFinite(wheel.getGeneratedSpeed())
                && Float.isFinite(wheel.getSpeed()) && Double.isFinite(wheel.getRegisteredStressCapacity())
                && Double.isFinite(wheel.getRegisteredStressImpact())
                && Float.isFinite(wheel.getNetworkCapacitySnapshot())
                && Float.isFinite(wheel.getNetworkStressSnapshot())
                && (gearbox == null || Float.isFinite(gearbox.getSpeed()))
                && (shaft == null || Float.isFinite(shaft.getSpeed()))
                && (bearing == null || Float.isFinite(bearing.getSpeed()));
        final BlockPos wheelLocal = local(body, wheelRaw);
        send(source, "SABLE_M28_STEERING_WHEEL index=" + index
                + " channel=" + expectation.channel()
                + " expectedWheel=" + expectation.wheelOffset()
                + " actualWheel=" + (wheel == null ? "unavailable" : wheelRaw.subtract(assemblerRaw))
                + " actualWheelSableLocal=" + (wheel == null ? "unavailable" : wheelLocal)
                + " topologyMatch=" + (wheel != null && blockPresent)
                + " actualWheelInventory=" + actualWheelLocals
                + " localPos=" + wheelLocal
                + " facing=" + (blockPresent ? wheelState.getValue(SteeringWheelBlock.FACING) : "unavailable")
                + " blockPresent=" + blockPresent
                + " bePresent=" + (wheel != null)
                + " registeredKineticBE=" + (foundWheel instanceof KineticBlockEntity)
                + " heldControlSessionActive=" + (wheel != null && wheel.isHeld())
                + " capturedWheelLocalPos=" + (wheel == null ? "unavailable"
                : nullable(wheel.getControllerLocalPos()))
                + " capturedSableId=" + (wheel == null ? "unavailable"
                : nullable(wheel.getControllerSableId()))
                + " capturedHand=" + (wheel == null ? "unavailable" : nullable(wheel.getControllerHand()))
                + " controlSessionToken=" + (wheel == null || wheel.getControllerSessionToken() == 0L
                ? "unavailable" : wheel.getControllerSessionToken())
                + " targetAngle=" + (wheel == null ? "unavailable" : wheel.getTargetAngle())
                + " currentAngle=" + (wheel == null ? "unavailable" : wheel.getAngle())
                + " generatedRpm=" + (wheel == null ? "unavailable" : wheel.getGeneratedSpeed())
                + " actualWheelSpeed=" + (wheel == null ? "unavailable" : wheel.getSpeed())
                + " stressConfigKey=" + (blockPresent ? ForgeRegistries.BLOCKS.getKey(wheelState.getBlock())
                : "unavailable")
                + " stressCapacity=" + (wheel == null ? "unavailable" : wheel.getRegisteredStressCapacity())
                + " stressImpact=" + (wheel == null ? "unavailable" : wheel.getRegisteredStressImpact())
                + " networkStress=" + (wheel == null ? "unavailable" : wheel.getNetworkStressSnapshot())
                + " networkCapacity=" + (wheel == null ? "unavailable" : wheel.getNetworkCapacitySnapshot())
                + " networkOverstressed=" + (wheel == null ? "unavailable" : wheel.isOverStressed())
                + " outputAxis=" + nullable(outputAxis)
                + " expectedOutputAxis=Y"
                + " outputFace=" + (blockPresent ? outputFace : "unavailable")
                + " networkPresent=" + (wheel != null && wheel.hasNetwork())
                + " networkId=" + (wheel == null ? "unavailable" : nullable(wheel.network))
                + " networkSourceCount=" + (network == null ? "unavailable" : network.sources.size())
                + " networkMemberCount=" + (network == null ? "unavailable" : network.members.size())
                + " networkMembers=" + networkMembers(source, body, network)
                + " adjacentKineticBlock=" + nullable(adjacentId)
                + " adjacentGearboxPos=" + local(body, gearboxRaw)
                + " gearboxAcceptedConnection=" + gearboxAccepted
                + " networkConnected=" + networkConnected
                + " adjacentSpeed=" + (gearbox == null ? "unavailable" : gearbox.getSpeed())
                + " downstreamShaftPos=" + local(body, shaftRaw)
                + " downstreamShaftRpm=" + (shaft == null ? "unavailable" : shaft.getSpeed())
                + " bearingPos=" + local(body, bearingRaw)
                + " bearingRpm=" + (bearing == null ? "unavailable" : bearing.getSpeed())
                + " bearingAngle=" + (controlLifecycle == null ? "unavailable"
                : nullable(controlLifecycle.bearingAngle()))
                + " bearingMovementMode=" + (controlLifecycle == null ? "unavailable"
                : nullable(controlLifecycle.movementMode()))
                + " bearingHoldConfigured=" + (controlLifecycle != null
                && controlLifecycle.movementMode()
                == com.simibubi.create.content.contraptions.IControlContraption.RotationMode.ROTATE_NEVER_PLACE)
                + " contraptionEntityId=" + (controlLifecycle == null ? "unavailable"
                : nullable(controlLifecycle.contraptionEntityId()))
                + " contraptionPresent=" + (controlLifecycle != null && controlLifecycle.contraptionPresent())
                + " contraptionAssembled=" + (controlLifecycle != null
                && controlLifecycle.contraptionAssembled())
                + " contraptionCreateCount=" + (controlLifecycle == null ? "unavailable"
                : controlLifecycle.contraptionCreateCount())
                + " contraptionRemoveCount=" + (controlLifecycle == null ? "unavailable"
                : controlLifecycle.contraptionRemoveCount())
                + " capturedControlBlockCount=" + (controlLifecycle == null ? "unavailable"
                : controlLifecycle.capturedBlockCount())
                + " controlSailPresent=" + (controlLifecycle != null && controlLifecycle.controlSailPresent())
                + " controlLifecycleState=" + (controlLifecycle == null ? "unavailable"
                : controlLifecycle.lifecycleState())
                + " survivalValid=" + (blockPresent && wheelState.canSurvive(source.getLevel(), wheelRaw))
                + " removalReason=" + SteeringWheelDiagnostics.removalReason(body.getUniqueId(), wheelLocal)
                + " finite=" + numericFinite);
    }

    private static String networkMembers(final CommandSourceStack source, final ServerSubLevel body,
                                         final KineticNetwork network) {
        if (network == null) {
            return "unavailable";
        }
        return network.members.keySet().stream()
                .map(member -> ForgeRegistries.BLOCKS.getKey(
                        source.getLevel().getBlockState(member.getBlockPos()).getBlock())
                        + "@" + local(body, member.getBlockPos()))
                .sorted()
                .toList()
                .toString();
    }

    private static AircraftResolution resolveAircraft(final CommandSourceStack source) {
        final Entity entity = source.getEntity();
        ServerSubLevel supportOwner = asServer(Sable.HELPER.getTrackingOrVehicleSubLevel(entity));
        String supportMethod = supportOwner == null ? "NONE" : "TRACKING_OR_VEHICLE";

        if (supportOwner == null && entity instanceof final EntityMovementExtension movement) {
            final SubLevelEntityCollision.CollisionInfo collision = movement.sable$getCollisionInfo();
            if (collision != null && collision.verticalCollisionBelow) {
                supportOwner = asServer(collision.trackingSubLevel);
                if (supportOwner == null) {
                    supportOwner = asServer(collision.preTrackingSubLevel);
                }
                if (supportOwner != null) {
                    supportMethod = "COLLISION_SUPPORT";
                }
            }
            if (supportOwner == null) {
                entity.getFeetBlockState();
                supportOwner = asServer(Sable.HELPER.getContaining(source.getLevel(),
                        movement.sable$getInBlockStatePos()));
                if (supportOwner != null) {
                    supportMethod = "FEET_BLOCK_OWNER";
                }
            }
        }

        ServerSubLevel targetOwner = null;
        final HitResult hit = entity.pick(100.0D, 1.0F, true);
        if (hit instanceof final BlockHitResult blockHit) {
            targetOwner = asServer(Sable.HELPER.getContaining(source.getLevel(), blockHit.getBlockPos()));
        }

        if (supportOwner != null) {
            return new AircraftResolution(supportOwner, targetOwner, supportOwner, supportMethod, true);
        }
        if (targetOwner != null) {
            return new AircraftResolution(null, targetOwner, targetOwner, "TARGETED_SABLE_BLOCK", false);
        }
        return new AircraftResolution(null, null, null, "NONE", false);
    }

    private static PropulsionDiagnostic propulsionDiagnostic(final ServerSubLevel body,
                                                              final WoodenPropellerBlockEntity propeller,
                                                              final SubLevelPhysicsSystem physics) {
        if (propeller == null) {
            return new PropulsionDiagnostic("unavailable", "unavailable", new Vector3d(), new Vector3d(),
                    "unavailable", "unavailable", "PROPELLER_MISSING", true);
        }
        final double effectiveSpeed = effectiveSpeed(propeller);
        final Vector3d productionLocal = new Vector3d(propeller.getBlockDirection().getStepX(),
                propeller.getBlockDirection().getStepY(), propeller.getBlockDirection().getStepZ())
                .mul(propeller.getScaledThrust());
        final Vector3d productionWorld = body.logicalPose().orientation().transform(productionLocal, new Vector3d());
        final ForceSample accumulated = forceSample(body, ForceGroups.PROPULSION.get(), physics);
        final String zeroReason;
        if (propeller.getSpeed() == 0.0F) {
            zeroReason = "KINETIC_SPEED_ZERO";
        } else if (!propeller.isActive()) {
            zeroReason = "ROTATION_SPEED_SMOOTHING_NOT_ACTIVE";
        } else if (productionLocal.lengthSquared() <= 1.0E-12D) {
            zeroReason = "UPSTREAM_AIRFLOW_OR_PRESSURE_SCALING_ZERO";
        } else {
            zeroReason = "NONE";
        }
        return new PropulsionDiagnostic(Float.toString(propeller.getSpeed()), Double.toString(effectiveSpeed),
                productionLocal, productionWorld, accumulated.forceText(), accumulated.torqueText(), zeroReason,
                finite(productionLocal) && finite(productionWorld));
    }

    private static ForceSample forceSample(final ServerSubLevel body, final ForceGroup group,
                                           final SubLevelPhysicsSystem physics) {
        final Object2ObjectMap<ForceGroup, QueuedForceGroup> groups = body.getQueuedForceGroups();
        final QueuedForceGroup queued = groups == null ? null : groups.get(group);
        if (queued == null || queued.getRecordedPointForces().isEmpty()) {
            return new ForceSample("unavailable", "unavailable");
        }
        final Vector3d impulse = new Vector3d();
        final Vector3d torqueImpulse = new Vector3d();
        final Vector3dc com = body.getMassTracker().getCenterOfMass();
        for (final QueuedForceGroup.PointForce pointForce : queued.getRecordedPointForces()) {
            impulse.add(pointForce.force());
            if (com != null) {
                final Vector3d lever = new Vector3d(pointForce.point()).sub(com);
                torqueImpulse.add(lever.cross(pointForce.force(), new Vector3d()));
            }
        }
        final int substeps = physics == null ? 1 : Math.max(1, physics.getConfig().substepsPerTick);
        final double seconds = TICK_SECONDS / substeps;
        return new ForceSample(impulse.div(seconds).toString(), torqueImpulse.div(seconds).toString());
    }

    private static String runtimeState(final boolean handleValid, final int propellerCount,
                                       final int providerCount, final WoodenPropellerBlockEntity propeller,
                                       final PropulsionDiagnostic propulsion) {
        if (!handleValid || !propulsion.finite()) {
            return "INVALID_BODY_STATE";
        }
        if (propellerCount == 0 || propeller == null) {
            return "WAITING_FOR_PROPELLER";
        }
        if (providerCount == 0) {
            return "WAITING_FOR_PROPULSION_PROVIDER";
        }
        if (propeller.getSpeed() == 0.0F || !propeller.isActive()) {
            return "READY_ZERO_RPM";
        }
        if (propulsion.productionForceBodyLocal().lengthSquared() <= 1.0E-12D) {
            return "ZERO_THRUST_" + propulsion.zeroThrustReason();
        }
        return "ACTIVE_PROPULSION";
    }

    private static double effectiveSpeed(final WoodenPropellerBlockEntity propeller) {
        return propeller.getBlockDirection().getAxisDirection().getStep()
                * propeller.getRotationSpeed() * (10.0D / 3.0D)
                * (propeller.getBlockState().getValue(WoodenPropellerBlock.REVERSED) ? -1.0D : 1.0D);
    }

    private static boolean looksLikeTerrain(final BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.BEDROCK);
    }

    private static ServerSubLevel asServer(final SubLevel subLevel) {
        return subLevel instanceof final ServerSubLevel server && !server.isRemoved() ? server : null;
    }

    private static BlockPos local(final ServerSubLevel body, final BlockPos raw) {
        return raw.subtract(body.getPlot().getCenterBlock());
    }

    private static boolean finite(final Vector3dc value) {
        return value != null && Double.isFinite(value.x()) && Double.isFinite(value.y())
                && Double.isFinite(value.z());
    }

    private static String id(final ServerSubLevel body) {
        return body == null ? "none" : body.getUniqueId().toString();
    }

    private static String nullable(final Object value) {
        return value == null ? "unavailable" : value.toString();
    }

    private static void send(final CommandSourceStack source, final String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    private record AircraftResolution(ServerSubLevel supportOwner, ServerSubLevel targetOwner,
                                      ServerSubLevel body, String method, boolean playerOnSable) {
    }

    private record ForceSample(String forceText, String torqueText) {
    }

    private record PropulsionDiagnostic(String kineticSpeed, String effectiveSpeed,
                                        Vector3d productionForceBodyLocal, Vector3d productionForceWorld,
                                        String accumulatedForceBodyLocal, String accumulatedTorqueBodyLocal,
                                        String zeroThrustReason, boolean finite) {
    }

    private record WheelExpectation(String channel, BlockPos wheelOffset, Direction wheelFacing,
                                    List<BlockPos> gearboxOffsets, BlockPos shaftOffset,
                                    BlockPos bearingOffset, Direction bearingFacing, BlockPos sailOffset,
                                    Direction.Axis sailAxis) {
    }
}
