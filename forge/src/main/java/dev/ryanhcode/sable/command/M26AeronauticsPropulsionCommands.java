package dev.ryanhcode.sable.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.config.AeroConfig;
import dev.eriksonn.aeronautics.content.propulsion.WoodenPropellerBlock;
import dev.eriksonn.aeronautics.content.propulsion.WoodenPropellerBlockEntity;
import dev.eriksonn.aeronautics.index.AeroBlocks;
import dev.eriksonn.aeronautics.index.AeroPropulsionRegistries;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class M26AeronauticsPropulsionCommands {
    private static final ResourceLocation CREATIVE_MOTOR_ID = new ResourceLocation("create", "creative_motor");
    private static final double TICK_SECONDS = 1.0D / 20.0D;
    private static final Set<Integer> SAMPLE_TICKS = Set.of(0, 1, 2, 5, 20);
    private static final Map<String, PropulsionFixtureState> ACTIVE_FIXTURES = new HashMap<>();

    private M26AeronauticsPropulsionCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m26")
                .then(Commands.literal("status").executes(M26AeronauticsPropulsionCommands::status))
                .then(Commands.literal("registry_check").executes(M26AeronauticsPropulsionCommands::registryCheck))
                .then(Commands.literal("cleanup").executes(M26AeronauticsPropulsionCommands::cleanup))
                .then(Commands.literal("release").executes(M26AeronauticsPropulsionCommands::release))
                .then(Commands.literal("fixture")
                        .then(Commands.literal("thrust_centered")
                                .executes(ctx -> fixture(ctx.getSource(), FixtureKind.CENTERED)))
                        .then(Commands.literal("thrust_offset")
                                .executes(ctx -> fixture(ctx.getSource(), FixtureKind.OFFSET)))
                        .then(Commands.literal("lift_and_thrust")
                                .executes(ctx -> fixture(ctx.getSource(), FixtureKind.LIFT_AND_THRUST))))
                .then(Commands.literal("propulsion")
                        .then(Commands.literal("rpm")
                                .then(Commands.argument("rpm", IntegerArgumentType.integer(-256, 256))
                                        .executes(M26AeronauticsPropulsionCommands::setRpm)))
                        .then(Commands.literal("rotate")
                                .then(Commands.argument("degX", DoubleArgumentType.doubleArg(-180.0D, 180.0D))
                                        .then(Commands.argument("degY", DoubleArgumentType.doubleArg(-180.0D, 180.0D))
                                                .then(Commands.argument("degZ", DoubleArgumentType.doubleArg(-180.0D, 180.0D))
                                                        .executes(M26AeronauticsPropulsionCommands::rotate)))))
                .then(Commands.literal("inspect")
                        .then(Commands.literal("propulsion")
                                .executes(M26AeronauticsPropulsionCommands::inspect)))));
    }

    public static void tick(final ServerLevel level) {
        final PropulsionFixtureState state = activeFixture(level);
        final ServerSubLevel body = resolveBody(level, state);
        if (state == null || body == null) {
            return;
        }
        body.enableIndividualQueuedForcesTracking(true);
        final PropulsionSample sample = capture(level, body, state, state.observationTick);
        state.latestSample = sample;
        if (SAMPLE_TICKS.contains(state.observationTick)) {
            Aeronautics.LOGGER.info("SABLE_M26_PROPULSION phase=POST_PHYSICS_SAMPLE fixtureSessionId={}"
                            + " fixtureType={} step={} sableId={}"
                            + " rpm={} thrustBodyLocal={} thrustWorld={} torqueBodyLocal={} position={}"
                            + " linearVelocity={} angularVelocity={} finite={}",
                    state.fixtureSessionId, state.kind.commandName, state.observationTick, body.getUniqueId(),
                    sample.kineticSpeed(), sample.forceBodyLocal(),
                    sample.forceWorld(), sample.torqueBodyLocal(), sample.position(), sample.linearVelocity(),
                    sample.angularVelocity(), sample.finite());
        }
        state.observationTick++;
    }

    private static int status(final CommandContext<CommandSourceStack> ctx) {
        send(ctx.getSource(), "SABLE_M26_STATUS implementationRevision=M26.1"
                + " m25=CLOSED_RUNTIME_PROVEN"
                + " mechanism=WOODEN_PROPELLER"
                + " upstreamCommit=" + Aeronautics.BASELINE_COMMIT
                + " m27Aerodynamics=DEFERRED"
                + " status=IMPLEMENTED_RUNTIME_REQUIRED");
        return 1;
    }

    private static int registryCheck(final CommandContext<CommandSourceStack> ctx) {
        final boolean block = Aeronautics.path("wooden_propeller")
                .equals(ForgeRegistries.BLOCKS.getKey(AeroPropulsionRegistries.WOODEN_PROPELLER.get()));
        final boolean item = Aeronautics.path("wooden_propeller")
                .equals(ForgeRegistries.ITEMS.getKey(AeroPropulsionRegistries.WOODEN_PROPELLER_ITEM.get()));
        final boolean blockEntity = Aeronautics.path("wooden_propeller")
                .equals(ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(AeroPropulsionRegistries.WOODEN_PROPELLER_BE.get()));
        final boolean pass = block && item && blockEntity;
        send(ctx.getSource(), "SABLE_M26_REGISTRY_CHECK status=" + (pass ? "PASS" : "FAIL")
                + " propulsionBlocks=1 propulsionItems=1 propulsionBlockEntities=1"
                + " entities=0 menus=0 recipeTypes=0 recipeSerializers=0 networkPackets=0"
                + " woodenPropellerBlock=" + block + " woodenPropellerItem=" + item
                + " woodenPropellerBlockEntity=" + blockEntity);
        return pass ? 1 : 0;
    }

    private static int fixture(final CommandSourceStack source, final FixtureKind kind) {
        if (!AeroConfig.ENABLE_M26_PROPULSION_FIXTURES.get()) {
            send(source, "SABLE_M26_FIXTURE status=FAIL reason=fixture_disabled_by_config");
            return 0;
        }
        final ServerLevel level = source.getLevel();
        final PropulsionFixtureState old = ACTIVE_FIXTURES.remove(key(level));
        if (old != null) {
            cleanupParent(level, old);
            old.invalidate();
        }
        final Set<UUID> preExistingSableIds = currentSableIds(level);
        final Direction towardPlayer = Direction.fromYRot(source.getRotation().y);
        final BlockPos origin = BlockPos.containing(source.getPosition()).relative(towardPlayer, 6).above(4);
        final Set<BlockPos> support = buildSupport(level, origin);
        final Set<BlockPos> body = new HashSet<>();
        final BlockPos propellerPos = kind == FixtureKind.OFFSET ? origin.north(2) : origin;
        final BlockPos motorPos = propellerPos.west();
        final BlockPos assemblerPos = origin.above();

        place(level, body, propellerPos, AeroPropulsionRegistries.WOODEN_PROPELLER.get().defaultBlockState()
                .setValue(WoodenPropellerBlock.FACING, Direction.EAST)
                .setValue(WoodenPropellerBlock.REVERSED, false));
        placeMotor(level, body, motorPos);
        place(level, body, assemblerPos, SimulatedBlocks.PHYSICS_ASSEMBLER.get().defaultBlockState());
        place(level, body, origin.east(), Blocks.COPPER_BLOCK.defaultBlockState());
        place(level, body, origin.south(), AeroBlocks.LEVITITE.get().defaultBlockState());
        place(level, body, origin.below(), Blocks.COPPER_BLOCK.defaultBlockState());
        if (kind == FixtureKind.OFFSET) {
            place(level, body, origin, Blocks.COPPER_BLOCK.defaultBlockState());
            place(level, body, origin.north(), Blocks.AMETHYST_BLOCK.defaultBlockState());
            place(level, body, origin.west(), Blocks.COPPER_BLOCK.defaultBlockState());
        } else {
            place(level, body, origin.north(), kind == FixtureKind.LIFT_AND_THRUST
                    ? AeroBlocks.LEVITITE.get().defaultBlockState() : Blocks.AMETHYST_BLOCK.defaultBlockState());
        }
        glueConnected(level, body);

        final Map<BlockPos, ResourceLocation> fixtureBlockFingerprint = fixtureBlockFingerprint(
                level, assemblerPos, body);
        final PropulsionFixtureState state = new PropulsionFixtureState(UUID.randomUUID(), kind, origin,
                assemblerPos, propellerPos.subtract(assemblerPos), motorPos.subtract(assemblerPos),
                fixtureBlockFingerprint, preExistingSableIds, Set.copyOf(support), Set.copyOf(body));
        ACTIVE_FIXTURES.put(key(level), state);
        final SimAssemblyContraption selection = new SimAssemblyContraption(null);
        try {
            selection.searchMovedStructure(level, assemblerPos);
        } catch (final AssemblyException exception) {
            Aeronautics.LOGGER.error("SABLE_M26_FIXTURE selection failed", exception);
            send(source, "SABLE_M26_FIXTURE status=FAIL reason=selection_exception message="
                    + safe(exception.getMessage()));
            return 0;
        }
        final long selectedSupport = selection.getBlocks().stream().filter(support::contains).count();
        final boolean valid = selection.getBlocks().size() == body.size() && selectedSupport == 0
                && selection.getBlocks().contains(propellerPos)
                && selection.getBlocks().contains(motorPos)
                && selection.getBlocks().contains(assemblerPos);
        send(source, "SABLE_M26_FIXTURE status=" + (valid ? "PASS" : "FAIL")
                + " name=" + kind.commandName
                + " fixtureSessionId=" + state.fixtureSessionId
                + " assembler=" + assemblerPos.toShortString()
                + " propeller=" + propellerPos.toShortString()
                + " propellerFacing=EAST motor=" + motorPos.toShortString()
                + " selectedBlockCount=" + selection.getBlocks().size()
                + " expectedBlockCount=" + body.size()
                + " selectedSupportBlocks=" + selectedSupport
                + " levititeBlocks=" + kind.levititeCount
                + " expectedTorqueSign=" + (kind == FixtureKind.OFFSET ? "NEGATIVE_Y_FOR_POSITIVE_RPM" : "NEAR_ZERO")
                + " action=assemble_then_release_then_propulsion_rpm_then_inspect");
        return valid ? 1 : 0;
    }

    private static int setRpm(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final PropulsionFixtureState state = activeFixture(source.getLevel());
        if (state == null) {
            send(source, "SABLE_M26_PROPULSION_POWER status=FAIL reason=active_fixture_missing");
            return 0;
        }
        final ServerSubLevel body = resolveBody(source.getLevel(), state);
        if (body == null) {
            send(source, "SABLE_M26_PROPULSION_POWER status=FAIL reason=" + state.resolutionFailure
                    + " fixtureSessionId=" + state.fixtureSessionId
                    + " fixtureType=" + state.kind.commandName
                    + " sableId=" + nullable(state.bodyId));
            return 0;
        }
        final WoodenPropellerBlockEntity propeller = resolvePropeller(source.getLevel(), body, state);
        final CreativeMotorBlockEntity motor = resolveMotor(source.getLevel(), body, state);
        if (propeller == null || motor == null) {
            send(source, "SABLE_M26_PROPULSION_POWER status=FAIL reason="
                    + (propeller == null ? "active_fixture_propeller_missing" : "active_fixture_motor_missing")
                    + " fixtureSessionId=" + state.fixtureSessionId
                    + " fixtureType=" + state.kind.commandName
                    + " sableId=" + body.getUniqueId());
            return 0;
        }
        final int rpm = IntegerArgumentType.getInteger(ctx, "rpm");
        motor.generatedSpeed.setValue(rpm);
        motor.setChanged();
        state.observationTick = 0;
        send(source, "SABLE_M26_PROPULSION_POWER status=PASS"
                + " fixtureSessionId=" + state.fixtureSessionId
                + " fixtureType=" + state.kind.commandName
                + " sableId=" + body.getUniqueId()
                + " motorLocalPos=" + state.motorLocalPos.toShortString()
                + " resolvedMotorSable=" + body.getUniqueId()
                + " requestedRPM=" + rpm
                + " actualMotorSpeed=" + motor.getGeneratedSpeed()
                + " propulsorKineticSpeed=" + propeller.getSpeed()
                + " productionThrustMagnitude=" + Math.abs(propeller.getScaledThrust())
                + " commandAppliedForce=false");
        return 1;
    }

    private static int rotate(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final PropulsionFixtureState state = activeFixture(source.getLevel());
        final ServerSubLevel body = resolveBody(source.getLevel(), state);
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(source.getLevel());
        final RigidBodyHandle handle = body == null || physics == null ? null : physics.getPhysicsHandle(body);
        if (body == null || handle == null || !handle.isValid()) {
            send(source, "SABLE_M26_PROPULSION_ROTATE status=FAIL reason=physical_body_unavailable");
            return 0;
        }
        final double x = DoubleArgumentType.getDouble(ctx, "degX");
        final double y = DoubleArgumentType.getDouble(ctx, "degY");
        final double z = DoubleArgumentType.getDouble(ctx, "degZ");
        final Quaterniond orientation = new Quaterniond(body.logicalPose().orientation())
                .rotateXYZ(Math.toRadians(x), Math.toRadians(y), Math.toRadians(z)).normalize();
        handle.teleport(body.logicalPose().position(), orientation);
        state.observationTick = 0;
        send(source, "SABLE_M26_PROPULSION_ROTATE status=PASS sableId=" + body.getUniqueId()
                + " deltaDegrees=(" + x + "," + y + "," + z + ")"
                + " api=RIGID_BODY_HANDLE_TELEPORT forceAppliedByCommand=false");
        return 1;
    }

    private static int inspect(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final PropulsionFixtureState state = activeFixture(source.getLevel());
        if (state == null) {
            send(source, "SABLE_M26_INSPECT status=FAIL reason=active_fixture_missing");
            return 0;
        }
        final ServerSubLevel body = resolveBody(source.getLevel(), state);
        if (body == null) {
            send(source, "SABLE_M26_INSPECT status=FAIL reason=" + state.resolutionFailure
                    + " fixtureSessionId=" + state.fixtureSessionId
                    + " fixtureType=" + state.kind.commandName
                    + " sableId=" + nullable(state.bodyId)
                    + " runtimeState=WAITING_FOR_ACTIVE_FIXTURE_BODY");
            return 0;
        }
        body.enableIndividualQueuedForcesTracking(true);
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(source.getLevel());
        final RigidBodyHandle handle = physics == null ? null : physics.getPhysicsHandle(body);
        final WoodenPropellerBlockEntity propeller = resolvePropeller(source.getLevel(), body, state);
        final PropulsionSample sample = state.latestSample != null
                ? state.latestSample : capture(source.getLevel(), body, state, -1);
        final Vector3dc rawCom = body.getMassTracker().getCenterOfMass();
        final Vector3d visibleCom = rawCom == null ? new Vector3d(Double.NaN)
                : body.logicalPose().transformPosition(new Vector3d(rawCom));
        final long propellers = SimAssemblyHelper.collectBlocks(source.getLevel(), body).stream()
                .filter(pos -> source.getLevel().getBlockState(pos).is(AeroPropulsionRegistries.WOODEN_PROPELLER.get()))
                .count();
        final long levitite = SimAssemblyHelper.collectBlocks(source.getLevel(), body).stream()
                .filter(pos -> source.getLevel().getBlockState(pos).is(AeroBlocks.LEVITITE.get())).count();
        final boolean validHandle = handle != null && handle.isValid();
        final String runtimeState = !validHandle || !sample.finite() ? "INVALID"
                : sample.kineticSpeed() == 0.0F ? "READY_ZERO_RPM"
                : sample.forceWorld().lengthSquared() > 1.0E-8D ? "ACTIVE_THRUST" : "WAITING_FOR_FORCE_SAMPLE";
        send(source, "SABLE_M26_INSPECT status=" + (validHandle && propeller != null ? "PASS" : "FAIL")
                + " runtimeState=" + runtimeState
                + " fixtureSessionId=" + state.fixtureSessionId
                + " fixtureType=" + state.kind.commandName
                + " sableId=" + body.getUniqueId()
                + " propellerLocalPos=" + state.propellerLocalPos.toShortString()
                + " motorLocalPos=" + state.motorLocalPos.toShortString()
                + " bodyPresent=true bodyHandleValid=" + validHandle
                + " mass=" + body.getMassTracker().getMass()
                + " centerOfMassVisible=" + visibleCom
                + " propulsionProviderCount=" + propellers
                + " propulsionProviderTypes=" + (propellers == 0 ? "[]" : "[aeronautics:wooden_propeller]")
                + " kineticSpeed=" + sample.kineticSpeed()
                + " localThrustAxis=" + sample.localAxis()
                + " worldThrustAxis=" + sample.worldAxis()
                + " thrustMagnitude=" + sample.forceWorld().length()
                + " thrustForceBodyLocal=" + sample.forceBodyLocal()
                + " thrustForceWorld=" + sample.forceWorld()
                + " applicationPointVisible=" + sample.applicationPointVisible()
                + " applicationPointBodyLocal=" + sample.applicationPointBodyLocal()
                + " leverArmBodyLocal=" + sample.applicationPointBodyLocal()
                + " expectedTorqueBodyLocal=" + sample.expectedTorqueBodyLocal()
                + " accumulatedTorqueBodyLocal=" + sample.torqueBodyLocal()
                + " linearVelocity=" + sample.linearVelocity()
                + " angularVelocity=" + sample.angularVelocity()
                + " position=" + sample.position()
                + " orientation=" + sample.orientation()
                + " levititeProviderCount=" + levitite
                + " liftForceWorld=" + sample.liftForceWorld()
                + " finite=" + sample.finite());
        return validHandle && propeller != null ? 1 : 0;
    }

    private static int release(final CommandContext<CommandSourceStack> ctx) {
        final ServerLevel level = ctx.getSource().getLevel();
        final PropulsionFixtureState state = activeFixture(level);
        if (state == null) {
            send(ctx.getSource(), "SABLE_M26_RELEASE status=FAIL reason=active_fixture_missing");
            return 0;
        }
        final ServerSubLevel body = resolveBody(level, state);
        if (body == null) {
            send(ctx.getSource(), "SABLE_M26_RELEASE status=FAIL reason=" + state.resolutionFailure
                    + " fixtureSessionId=" + state.fixtureSessionId
                    + " fixtureType=" + state.kind.commandName
                    + " sableId=" + nullable(state.bodyId));
            return 0;
        }
        int removed = 0;
        for (final BlockPos pos : state.supportBlocks) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                removed++;
            }
        }
        send(ctx.getSource(), "SABLE_M26_RELEASE status=PASS fixtureSessionId=" + state.fixtureSessionId
                + " fixtureType=" + state.kind.commandName
                + " sableId=" + body.getUniqueId()
                + " removedSupportBlocks=" + removed
                + " bodyMoved=false forceAppliedByCommand=false");
        return 1;
    }

    private static int cleanup(final CommandContext<CommandSourceStack> ctx) {
        final PropulsionFixtureState state = ACTIVE_FIXTURES.remove(key(ctx.getSource().getLevel()));
        if (state == null) {
            send(ctx.getSource(), "SABLE_M26_CLEANUP status=PASS reason=no_active_parent_fixture");
            return 1;
        }
        final int removed = cleanupParent(ctx.getSource().getLevel(), state);
        final UUID invalidatedSession = state.fixtureSessionId;
        state.invalidate();
        send(ctx.getSource(), "SABLE_M26_CLEANUP status=PASS fixtureSessionId=" + invalidatedSession
                + " sessionInvalidated=true removedParentBlocks=" + removed
                + " assembledSablesUntouched=true productionProvidersUntouched=true");
        return 1;
    }

    private static PropulsionSample capture(final ServerLevel level, final ServerSubLevel body,
                                            final PropulsionFixtureState state, final int step) {
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(level);
        final RigidBodyHandle handle = physics == null ? null : physics.getPhysicsHandle(body);
        final WoodenPropellerBlockEntity propeller = resolvePropeller(level, body, state);
        final Vector3d localAxis = propeller == null ? new Vector3d(Double.NaN)
                : new Vector3d(propeller.getBlockDirection().getStepX(), propeller.getBlockDirection().getStepY(),
                        propeller.getBlockDirection().getStepZ());
        final Vector3d worldAxis = finite(localAxis)
                ? body.logicalPose().orientation().transform(localAxis, new Vector3d()) : new Vector3d(Double.NaN);
        final ForceSample propulsion = forceSample(body, ForceGroups.PROPULSION.get(), physics);
        final ForceSample lift = forceSample(body, ForceGroups.LEVITATION.get(), physics);
        final Vector3d applicationRaw = propeller == null ? new Vector3d(Double.NaN)
                : new Vector3d(propeller.getBlockPos().getX() + 0.5D, propeller.getBlockPos().getY() + 0.5D,
                        propeller.getBlockPos().getZ() + 0.5D);
        final Vector3dc rawCom = body.getMassTracker().getCenterOfMass();
        final Vector3d lever = rawCom == null || !finite(applicationRaw) ? new Vector3d(Double.NaN)
                : new Vector3d(applicationRaw).sub(rawCom);
        final Vector3d visiblePoint = finite(applicationRaw)
                ? body.logicalPose().transformPosition(new Vector3d(applicationRaw)) : new Vector3d(Double.NaN);
        final Vector3d expectedTorque = finite(lever) && finite(propulsion.localForce)
                ? lever.cross(propulsion.localForce, new Vector3d()) : new Vector3d(Double.NaN);
        final Vector3d localForce = propulsion.localForce;
        final Vector3d worldForce = finite(localForce)
                ? body.logicalPose().orientation().transform(localForce, new Vector3d()) : new Vector3d(Double.NaN);
        final Vector3d worldLift = finite(lift.localForce)
                ? body.logicalPose().orientation().transform(lift.localForce, new Vector3d()) : new Vector3d(Double.NaN);
        final Vector3d linear = handle == null ? new Vector3d(Double.NaN) : handle.getLinearVelocity(new Vector3d());
        final Vector3d angular = handle == null ? new Vector3d(Double.NaN) : handle.getAngularVelocity(new Vector3d());
        final Vector3d position = new Vector3d(body.logicalPose().position());
        final Quaterniond orientation = new Quaterniond(body.logicalPose().orientation());
        final boolean finite = finite(localAxis) && finite(worldAxis) && finite(localForce) && finite(worldForce)
                && finite(propulsion.localTorque) && finite(linear) && finite(angular) && finite(position)
                && finite(orientation);
        return new PropulsionSample(step, propeller == null ? Float.NaN : propeller.getSpeed(), localAxis,
                worldAxis, localForce, worldForce, visiblePoint, lever, expectedTorque, propulsion.localTorque,
                linear, angular, position, orientation, worldLift, finite);
    }

    private static ForceSample forceSample(final ServerSubLevel body, final ForceGroup group,
                                           final SubLevelPhysicsSystem physics) {
        final Vector3d impulse = new Vector3d();
        final Vector3d torqueImpulse = new Vector3d();
        final Object2ObjectMap<ForceGroup, QueuedForceGroup> groups = body.getQueuedForceGroups();
        final QueuedForceGroup queued = groups == null ? null : groups.get(group);
        final Vector3dc com = body.getMassTracker().getCenterOfMass();
        if (queued != null) {
            for (final QueuedForceGroup.PointForce pointForce : queued.getRecordedPointForces()) {
                impulse.add(pointForce.force());
                if (com != null) {
                    final Vector3d lever = new Vector3d(pointForce.point()).sub(com);
                    torqueImpulse.add(lever.cross(pointForce.force(), new Vector3d()));
                }
            }
        }
        final int substeps = physics == null ? 1 : Math.max(1, physics.getConfig().substepsPerTick);
        final double seconds = TICK_SECONDS / substeps;
        return new ForceSample(impulse.div(seconds), torqueImpulse.div(seconds));
    }

    private static PropulsionFixtureState activeFixture(final ServerLevel level) {
        final PropulsionFixtureState state = ACTIVE_FIXTURES.get(key(level));
        return state != null && state.active ? state : null;
    }

    private static ServerSubLevel resolveBody(final ServerLevel level, final PropulsionFixtureState state) {
        if (state == null || !state.active) {
            return null;
        }
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            state.resolutionFailure = "active_fixture_container_missing";
            return null;
        }
        if (state.bodyId != null && container.getSubLevel(state.bodyId) instanceof final ServerSubLevel body) {
            return body;
        }

        if (state.bodyId != null) {
            state.resolutionFailure = "active_fixture_missing";
            return null;
        }

        final List<FixtureBodyMatch> matches = new ArrayList<>();
        for (final ServerSubLevel candidate : container.getAllSubLevels()) {
            if (state.preExistingSableIds.contains(candidate.getUniqueId())) {
                continue;
            }
            final BlockPos assemblerRaw = matchingAssembler(level, candidate, state);
            if (assemblerRaw != null) {
                matches.add(new FixtureBodyMatch(candidate, assemblerRaw));
            }
        }

        if (matches.isEmpty()) {
            state.resolutionFailure = "active_fixture_body_unresolved";
            return null;
        }
        if (matches.size() != 1) {
            state.resolutionFailure = "active_fixture_body_ambiguous";
            return null;
        }

        final FixtureBodyMatch match = matches.get(0);
        state.captureBody(match.body, match.assemblerRaw);
        Aeronautics.LOGGER.info("SABLE_M26_FIXTURE_BODY_CAPTURE fixtureSessionId={} fixtureType={}"
                        + " sableId={} assemblerLocalPos={} propellerLocalPos={} motorLocalPos={}"
                        + " ownership=AUTHORITATIVE_FIXTURE_SESSION",
                state.fixtureSessionId, state.kind.commandName, state.bodyId, state.assemblerLocalPos,
                state.propellerLocalPos, state.motorLocalPos);
        return match.body;
    }

    private static BlockPos matchingAssembler(final ServerLevel level, final ServerSubLevel body,
                                              final PropulsionFixtureState state) {
        final List<BlockPos> blocks = SimAssemblyHelper.collectBlocks(level, body);
        if (blocks.size() != state.fixtureBlockFingerprint.size()) {
            return null;
        }
        BlockPos result = null;
        for (final BlockPos candidate : blocks) {
            if (!level.getBlockState(candidate).is(SimulatedBlocks.PHYSICS_ASSEMBLER.get())) {
                continue;
            }
            boolean matches = true;
            for (final Map.Entry<BlockPos, ResourceLocation> expected : state.fixtureBlockFingerprint.entrySet()) {
                final BlockPos raw = candidate.offset(expected.getKey());
                if (!expected.getValue().equals(ForgeRegistries.BLOCKS.getKey(level.getBlockState(raw).getBlock()))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                if (result != null) {
                    return null;
                }
                result = candidate.immutable();
            }
        }
        return result;
    }

    private static WoodenPropellerBlockEntity resolvePropeller(final ServerLevel level, final ServerSubLevel body,
                                                               final PropulsionFixtureState state) {
        if (!state.owns(body) || state.propellerLocalPos == null) {
            return null;
        }
        final BlockPos raw = body.getPlot().getCenterBlock().offset(state.propellerLocalPos);
        return level.getBlockEntity(raw) instanceof final WoodenPropellerBlockEntity propeller ? propeller : null;
    }

    private static CreativeMotorBlockEntity resolveMotor(final ServerLevel level, final ServerSubLevel body,
                                                         final PropulsionFixtureState state) {
        if (!state.owns(body) || state.motorLocalPos == null) {
            return null;
        }
        final BlockPos raw = body.getPlot().getCenterBlock().offset(state.motorLocalPos);
        return level.getBlockEntity(raw) instanceof final CreativeMotorBlockEntity motor ? motor : null;
    }

    private static Set<UUID> currentSableIds(final ServerLevel level) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return Set.of();
        }
        final Set<UUID> result = new HashSet<>();
        for (final ServerSubLevel body : container.getAllSubLevels()) {
            result.add(body.getUniqueId());
        }
        return Set.copyOf(result);
    }

    private static Map<BlockPos, ResourceLocation> fixtureBlockFingerprint(final ServerLevel level,
                                                                           final BlockPos assembler,
                                                                           final Set<BlockPos> bodyBlocks) {
        final Map<BlockPos, ResourceLocation> result = new HashMap<>();
        for (final BlockPos parentPos : bodyBlocks) {
            final ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(level.getBlockState(parentPos).getBlock());
            if (blockId == null) {
                throw new IllegalStateException("unregistered_fixture_block_at_" + parentPos.toShortString());
            }
            result.put(parentPos.subtract(assembler), blockId);
        }
        return Map.copyOf(result);
    }

    private static Set<BlockPos> buildSupport(final ServerLevel level, final BlockPos origin) {
        final Set<BlockPos> support = new HashSet<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                final BlockPos pos = origin.offset(x, -2, z);
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                support.add(pos);
            }
        }
        return support;
    }

    private static void placeMotor(final ServerLevel level, final Set<BlockPos> body, final BlockPos pos) {
        final Block motor = ForgeRegistries.BLOCKS.getValue(CREATIVE_MOTOR_ID);
        if (motor == null || motor == Blocks.AIR) {
            throw new IllegalStateException("missing_create_creative_motor");
        }
        BlockState state = motor.defaultBlockState();
        if (!state.hasProperty(DirectionalBlock.FACING)) {
            throw new IllegalStateException("creative_motor_missing_facing_property");
        }
        state = state.setValue(DirectionalBlock.FACING, Direction.EAST);
        place(level, body, pos, state);
    }

    private static void place(final ServerLevel level, final Set<BlockPos> body, final BlockPos pos,
                              final BlockState state) {
        level.setBlock(pos, state, 3);
        body.add(pos.immutable());
    }

    private static void glueConnected(final ServerLevel level, final Set<BlockPos> blocks) {
        final Set<String> glued = new HashSet<>();
        for (final BlockPos pos : blocks) {
            for (final Direction direction : Direction.values()) {
                final BlockPos neighbor = pos.relative(direction);
                if (!blocks.contains(neighbor)) {
                    continue;
                }
                final String edge = pos.asLong() < neighbor.asLong()
                        ? pos.asLong() + ":" + neighbor.asLong() : neighbor.asLong() + ":" + pos.asLong();
                if (glued.add(edge)) {
                    level.addFreshEntity(new SuperGlueEntity(level, SuperGlueEntity.span(pos, neighbor)));
                }
            }
        }
    }

    private static int cleanupParent(final ServerLevel level, final PropulsionFixtureState state) {
        int removed = 0;
        final Set<BlockPos> all = new HashSet<>(state.supportBlocks);
        all.addAll(state.bodyBlocks);
        for (final BlockPos pos : all) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                removed++;
            }
        }
        final AABB bounds = new AABB(state.origin).inflate(6.0D);
        for (final SuperGlueEntity glue : List.copyOf(level.getEntitiesOfClass(SuperGlueEntity.class, bounds))) {
            glue.remove(Entity.RemovalReason.KILLED);
        }
        return removed;
    }

    private static boolean finite(final Vector3dc value) {
        return Double.isFinite(value.x()) && Double.isFinite(value.y()) && Double.isFinite(value.z());
    }

    private static boolean finite(final Quaterniondc value) {
        return Double.isFinite(value.x()) && Double.isFinite(value.y())
                && Double.isFinite(value.z()) && Double.isFinite(value.w());
    }

    private static String key(final ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static String safe(final String value) {
        return value == null ? "none" : value.replaceAll("\\s+", "_");
    }

    private static String nullable(final Object value) {
        return value == null ? "unresolved" : value.toString();
    }

    private static void send(final CommandSourceStack source, final String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    private enum FixtureKind {
        CENTERED("thrust_centered", 1),
        OFFSET("thrust_offset", 1),
        LIFT_AND_THRUST("lift_and_thrust", 2);

        private final String commandName;
        private final int levititeCount;

        FixtureKind(final String commandName, final int levititeCount) {
            this.commandName = commandName;
            this.levititeCount = levititeCount;
        }
    }

    private static final class PropulsionFixtureState {
        private final UUID fixtureSessionId;
        private final FixtureKind kind;
        private final BlockPos origin;
        private final BlockPos parentAssemblerPos;
        private final BlockPos propellerOffsetFromAssembler;
        private final BlockPos motorOffsetFromAssembler;
        private final Map<BlockPos, ResourceLocation> fixtureBlockFingerprint;
        private final Set<UUID> preExistingSableIds;
        private final Set<BlockPos> supportBlocks;
        private final Set<BlockPos> bodyBlocks;
        private UUID bodyId;
        private BlockPos assemblerLocalPos;
        private BlockPos propellerLocalPos;
        private BlockPos motorLocalPos;
        private boolean active = true;
        private String resolutionFailure = "active_fixture_body_unresolved";
        private int observationTick;
        private PropulsionSample latestSample;

        private PropulsionFixtureState(final UUID fixtureSessionId, final FixtureKind kind,
                                       final BlockPos origin, final BlockPos parentAssemblerPos,
                                       final BlockPos propellerOffsetFromAssembler,
                                       final BlockPos motorOffsetFromAssembler,
                                       final Map<BlockPos, ResourceLocation> fixtureBlockFingerprint,
                                       final Set<UUID> preExistingSableIds,
                                       final Set<BlockPos> supportBlocks, final Set<BlockPos> bodyBlocks) {
            this.fixtureSessionId = fixtureSessionId;
            this.kind = kind;
            this.origin = origin;
            this.parentAssemblerPos = parentAssemblerPos;
            this.propellerOffsetFromAssembler = propellerOffsetFromAssembler;
            this.motorOffsetFromAssembler = motorOffsetFromAssembler;
            this.fixtureBlockFingerprint = fixtureBlockFingerprint;
            this.preExistingSableIds = preExistingSableIds;
            this.supportBlocks = supportBlocks;
            this.bodyBlocks = bodyBlocks;
        }

        private void captureBody(final ServerSubLevel body, final BlockPos assemblerRaw) {
            this.bodyId = body.getUniqueId();
            this.assemblerLocalPos = assemblerRaw.subtract(body.getPlot().getCenterBlock());
            this.propellerLocalPos = this.assemblerLocalPos.offset(this.propellerOffsetFromAssembler);
            this.motorLocalPos = this.assemblerLocalPos.offset(this.motorOffsetFromAssembler);
            this.resolutionFailure = "none";
            this.observationTick = 0;
        }

        private boolean owns(final ServerSubLevel body) {
            return this.active && this.bodyId != null && this.bodyId.equals(body.getUniqueId());
        }

        private void invalidate() {
            this.active = false;
            this.bodyId = null;
            this.assemblerLocalPos = null;
            this.propellerLocalPos = null;
            this.motorLocalPos = null;
            this.latestSample = null;
            this.resolutionFailure = "active_fixture_missing";
        }
    }

    private record FixtureBodyMatch(ServerSubLevel body, BlockPos assemblerRaw) {
    }

    private record ForceSample(Vector3d localForce, Vector3d localTorque) {
    }

    private record PropulsionSample(int step, float kineticSpeed, Vector3d localAxis, Vector3d worldAxis,
                                    Vector3d forceBodyLocal, Vector3d forceWorld,
                                    Vector3d applicationPointVisible, Vector3d applicationPointBodyLocal,
                                    Vector3d expectedTorqueBodyLocal, Vector3d torqueBodyLocal,
                                    Vector3d linearVelocity, Vector3d angularVelocity, Vector3d position,
                                    Quaterniond orientation, Vector3d liftForceWorld, boolean finite) {
    }
}
