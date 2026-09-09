package dev.ryanhcode.sable.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.config.AeroConfig;
import dev.eriksonn.aeronautics.index.AeroBlocks;
import dev.eriksonn.aeronautics.index.AeroCreativeTabs;
import dev.eriksonn.aeronautics.index.AeroItems;
import dev.eriksonn.aeronautics.network.AeroNetwork;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.config.FloatingBlockMaterialDataHandler;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockMaterial;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class M25AeronauticsCommands {
    private static final ResourceLocation LEVITITE_MATERIAL = Aeronautics.path("levitite");
    private static final int EXPECTED_BODY_BLOCKS = 6;
    private static final double TICK_SECONDS = 1.0D / 20.0D;
    private static final Set<Integer> LOG_SAMPLE_TICKS = Set.of(0, 1, 2, 5, 20);
    private static final Map<String, LiftFixtureState> LAST_FIXTURES = new HashMap<>();

    private M25AeronauticsCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m25")
                .then(Commands.literal("status").executes(M25AeronauticsCommands::status))
                .then(Commands.literal("registry_check").executes(M25AeronauticsCommands::registryCheck))
                .then(Commands.literal("cleanup").executes(M25AeronauticsCommands::cleanup))
                .then(Commands.literal("release").executes(M25AeronauticsCommands::release))
                .then(Commands.literal("fixture")
                        .then(Commands.literal("lift_basic")
                                .executes(ctx -> fixture(ctx.getSource(), true)))
                        .then(Commands.literal("lift_control")
                                .executes(ctx -> fixture(ctx.getSource(), false))))
                .then(Commands.literal("inspect")
                        .then(Commands.literal("lift").executes(M25AeronauticsCommands::inspectLift))));
    }

    public static void tick(final ServerLevel level) {
        final LiftFixtureState state = LAST_FIXTURES.get(fixtureKey(level));
        if (state == null) {
            return;
        }
        final ServerSubLevel body = resolveBody(level, state);
        if (body == null) {
            return;
        }
        body.enableIndividualQueuedForcesTracking(true);
        if (state.bodyId == null) {
            state.bodyId = body.getUniqueId();
            state.observationTick = 0;
        }
        final LiftSample sample = captureSample(level, body, state.observationTick);
        state.latestSample = sample;
        if (LOG_SAMPLE_TICKS.contains(state.observationTick)) {
            Aeronautics.LOGGER.info("SABLE_M25_LIFT phase=POST_PHYSICS_SAMPLE step={} sableId={} position={}"
                            + " velocity={} mass={} gravityForce={} liftForce={} netForce={} torque={} finite={}",
                    state.observationTick, body.getUniqueId(), sample.position(), sample.velocity(), sample.mass(),
                    sample.gravityForce(), sample.liftForce(), sample.netForce(), sample.localTorque(), sample.finite());
        }
        state.observationTick++;
    }

    private static int status(final CommandContext<CommandSourceStack> ctx) {
        send(ctx.getSource(), "SABLE_M25_STATUS implementationRevision=" + Aeronautics.IMPLEMENTATION_REVISION
                + " aeronauticsBaseline=" + Aeronautics.BASELINE_COMMIT
                + " modInitialized=" + Aeronautics.isInitialized()
                + " liftMechanism=LEVITITE_FLOATING_MATERIAL"
                + " propulsion=DEFERRED_M26"
                + " status=IMPLEMENTED_RUNTIME_REQUIRED");
        return 1;
    }

    private static int registryCheck(final CommandContext<CommandSourceStack> ctx) {
        final BlockState state = AeroBlocks.LEVITITE.get().defaultBlockState();
        final FloatingBlockMaterial material = PhysicsBlockPropertyHelper.getFloatingMaterial(state);
        final boolean blockPresent = Aeronautics.path("levitite")
                .equals(ForgeRegistries.BLOCKS.getKey(AeroBlocks.LEVITITE.get()));
        final boolean itemPresent = Aeronautics.path("levitite")
                .equals(ForgeRegistries.ITEMS.getKey(AeroItems.LEVITITE.get()));
        final boolean materialPresent = FloatingBlockMaterialDataHandler.allMaterials.containsKey(LEVITITE_MATERIAL);
        final boolean propertyResolved = material != null && material == FloatingBlockMaterialDataHandler.allMaterials.get(LEVITITE_MATERIAL);
        final boolean pass = Aeronautics.isInitialized() && blockPresent && itemPresent
                && AeroCreativeTabs.MAIN_TAB.isPresent() && AeroNetwork.isReady()
                && materialPresent && propertyResolved;
        send(ctx.getSource(), "SABLE_M25_REGISTRY_CHECK status=" + (pass ? "PASS" : "FAIL")
                + " blocks=1 items=1 blockEntities=0 entities=0 menus=0 recipeTypes=0 recipeSerializers=0"
                + " creativeTabs=1 networkPackets=" + AeroNetwork.packetCount() + " configs=1"
                + " levititeBlockPresent=" + blockPresent
                + " levititeItemPresent=" + itemPresent
                + " floatingMaterialPresent=" + materialPresent
                + " blockPropertyResolved=" + propertyResolved
                + " networkReady=" + AeroNetwork.isReady());
        return pass ? 1 : 0;
    }

    private static int fixture(final CommandSourceStack source, final boolean liftEnabled) {
        if (!AeroConfig.ENABLE_M25_LIFT_FIXTURES.get()) {
            send(source, "SABLE_M25_FIXTURE status=FAIL reason=fixture_disabled_by_config");
            return 0;
        }
        final ServerLevel level = source.getLevel();
        final LiftFixtureState previous = LAST_FIXTURES.remove(fixtureKey(level));
        if (previous != null) {
            cleanupParentFixture(level, previous);
        }
        final Direction facing = Direction.fromYRot(source.getRotation().y);
        final BlockPos origin = BlockPos.containing(source.getPosition()).relative(facing, 5).above(3);
        final Set<BlockPos> support = buildSupport(level, origin);
        final Set<BlockPos> body = buildBody(level, origin, liftEnabled);
        final LiftFixtureState state = new LiftFixtureState(origin, origin.above(), support, body, liftEnabled);
        LAST_FIXTURES.put(fixtureKey(level), state);

        final SimAssemblyContraption selection = new SimAssemblyContraption(null);
        try {
            selection.searchMovedStructure(level, origin);
        } catch (final AssemblyException exception) {
            Aeronautics.LOGGER.error("SABLE_M25_FIXTURE selection failed", exception);
            send(source, "SABLE_M25_FIXTURE status=FAIL reason=selection_exception message="
                    + safe(exception.getMessage()));
            return 0;
        }
        final long selectedSupport = selection.getBlocks().stream().filter(support::contains).count();
        final boolean pass = selection.getBlocks().size() == EXPECTED_BODY_BLOCKS
                && selectedSupport == 0 && selection.getBlocks().contains(origin.above())
                && (!liftEnabled || selection.getBlocks().stream()
                        .anyMatch(pos -> level.getBlockState(pos).is(AeroBlocks.LEVITITE.get())));
        send(source, "SABLE_M25_FIXTURE status=" + (pass ? "PASS" : "FAIL")
                + " name=" + (liftEnabled ? "lift_basic" : "lift_control")
                + " origin=" + origin.toShortString()
                + " assembler=" + origin.above().toShortString()
                + " selectedBlockCount=" + selection.getBlocks().size()
                + " selectedSupportBlocks=" + selectedSupport
                + " liftBlocks=" + (liftEnabled ? 1 : 0)
                + " expectedBehavior=" + (liftEnabled ? "EXPECTED_NEAR_HOVER" : "EXPECTED_FALL")
                + " forceOwner=SABLE_FLOATING_BLOCK_CONTROLLER"
                + " action=assemble_then_run_/sable_m25_release_then_inspect_lift");
        return pass ? 1 : 0;
    }

    private static int release(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final LiftFixtureState state = LAST_FIXTURES.get(fixtureKey(source.getLevel()));
        if (state == null) {
            send(source, "SABLE_M25_RELEASE status=FAIL reason=no_active_fixture");
            return 0;
        }
        int removed = 0;
        for (final BlockPos pos : state.supportBlocks) {
            if (!source.getLevel().getBlockState(pos).isAir()) {
                source.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                removed++;
            }
        }
        send(source, "SABLE_M25_RELEASE status=PASS removedSupportBlocks=" + removed
                + " bodyMoved=false velocityChanged=false forceAppliedByCommand=false");
        return 1;
    }

    private static int cleanup(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final LiftFixtureState state = LAST_FIXTURES.remove(fixtureKey(source.getLevel()));
        if (state == null) {
            send(source, "SABLE_M25_CLEANUP status=PASS reason=no_active_fixture");
            return 1;
        }
        final int removed = cleanupParentFixture(source.getLevel(), state);
        send(source, "SABLE_M25_CLEANUP status=PASS removedParentBlocks=" + removed
                + " assembledSablesUntouched=true");
        return 1;
    }

    private static int inspectLift(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        LiftFixtureState state = LAST_FIXTURES.get(fixtureKey(source.getLevel()));
        ServerSubLevel body = resolveBody(source.getLevel(), state);
        if (body == null) {
            body = findAnyLiftBody(source.getLevel());
            if (body != null && state == null) {
                state = LiftFixtureState.recovered(body.getUniqueId());
                LAST_FIXTURES.put(fixtureKey(source.getLevel()), state);
            }
        }
        if (body == null) {
            send(source, "SABLE_M25_INSPECT status=FAIL runtimeState=WAITING_FOR_ASSEMBLED_LIFT_BODY");
            return 0;
        }
        if (state != null && state.bodyId == null) {
            state.bodyId = body.getUniqueId();
        }
        body.enableIndividualQueuedForcesTracking(true);
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(source.getLevel());
        final RigidBodyHandle handle = physics == null ? null : physics.getPhysicsHandle(body);
        final boolean bodyHandleValid = handle != null && handle.isValid();
        final List<BlockPos> blocks = SimAssemblyHelper.collectBlocks(source.getLevel(), body);
        final long providerCount = blocks.stream()
                .filter(pos -> source.getLevel().getBlockState(pos).is(AeroBlocks.LEVITITE.get()))
                .count();
        final LiftSample sample = state != null && state.latestSample != null
                ? state.latestSample : captureSample(source.getLevel(), body, -1);
        final Vector3dc rawCom = body.getMassTracker().getCenterOfMass();
        final Vector3d visibleCom = rawCom == null ? null
                : body.logicalPose().transformPosition(new Vector3d(rawCom));
        final String runtimeState = bodyHandleValid && sample.finite()
                && (providerCount == 0 || sample.liftForce().lengthSquared() > 0.0D)
                        ? (providerCount > 0 ? "ACTIVE_POST_SOLVER_OBSERVED" : "CONTROL_GRAVITY_ONLY")
                        : "WAITING_FOR_POST_SOLVER_SAMPLE";
        final String expected = providerCount == 0 ? "EXPECTED_FALL"
                : 10.0D * providerCount >= sample.mass() ? "EXPECTED_NEAR_HOVER" : "EXPECTED_REDUCED_FALL";
        send(source, "SABLE_M25_INSPECT status=" + (bodyHandleValid ? "PASS" : "FAIL")
                + " runtimeState=" + runtimeState
                + " sableId=" + body.getUniqueId()
                + " bodyPresent=true bodyHandleValid=" + bodyHandleValid
                + " bodyRegistered=" + (physics != null && physics.getPipeline().isBodyRegistered(body))
                + " collisionGeometryPresent=" + (physics != null && physics.hasUploadedCollisionGeometry(body))
                + " storedBlockCount=" + blocks.size()
                + " mass=" + sample.mass()
                + " centerOfMassVisibleWorld=" + visibleCom
                + " liftProviderCount=" + providerCount
                + " liftProviderTypes=" + (providerCount > 0 ? "[aeronautics:levitite]" : "[]")
                + " gravityForceWorld=" + sample.gravityForce()
                + " totalLiftForceWorld=" + sample.liftForce()
                + " netVerticalForce=" + sample.netForce().y
                + " forceApplicationPointsVisibleWorld=" + sample.visibleApplicationPoints()
                + " leverArmsBodyLocal=" + sample.localLeverArms()
                + " torqueBodyLocal=" + sample.localTorque()
                + " verticalVelocity=" + sample.velocity().y
                + " altitude=" + sample.position().y
                + " observationStep=" + (state == null ? "recovered" : state.observationTick)
                + " expectedBehavior=" + expected
                + " persistence=BLOCK_STATE_PLUS_SABLE_FLOATING_CLUSTER_REBUILD"
                + " finite=" + sample.finite());
        return bodyHandleValid ? 1 : 0;
    }

    private static LiftSample captureSample(final ServerLevel level, final ServerSubLevel body, final int step) {
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(level);
        final RigidBodyHandle handle = physics == null ? null : physics.getPhysicsHandle(body);
        final Vector3d velocity = handle == null ? new Vector3d(Double.NaN) : handle.getLinearVelocity(new Vector3d());
        final double mass = body.getMassTracker().getMass();
        final Vector3d gravity = DimensionPhysicsData.getGravity(level, body.logicalPose().position(), new Vector3d());
        final Vector3d gravityForce = new Vector3d(gravity).mul(mass);
        final Vector3d localLiftImpulse = new Vector3d();
        final Vector3d localTorqueImpulse = new Vector3d();
        final List<Vector3d> visiblePoints = new ArrayList<>();
        final List<Vector3d> localLeverArms = new ArrayList<>();
        final Object2ObjectMap<ForceGroup, QueuedForceGroup> groups = body.getQueuedForceGroups();
        final QueuedForceGroup levitation = groups == null ? null : groups.get(ForceGroups.LEVITATION.get());
        final Vector3dc centerOfMass = body.getMassTracker().getCenterOfMass();
        if (levitation != null) {
            for (final QueuedForceGroup.PointForce pointForce : levitation.getRecordedPointForces()) {
                final Vector3d impulse = new Vector3d(pointForce.force());
                localLiftImpulse.add(impulse);
                if (centerOfMass != null) {
                    final Vector3d lever = new Vector3d(pointForce.point()).sub(centerOfMass);
                    localLeverArms.add(new Vector3d(lever));
                    localTorqueImpulse.add(lever.cross(impulse, new Vector3d()));
                }
                visiblePoints.add(body.logicalPose().transformPosition(new Vector3d(pointForce.point())));
            }
        }
        final int substeps = physics == null ? 1 : Math.max(1, physics.getConfig().substepsPerTick);
        final double substepSeconds = TICK_SECONDS / substeps;
        final Vector3d localLiftForce = localLiftImpulse.div(substepSeconds, new Vector3d());
        final Vector3d worldLiftForce = body.logicalPose().orientation().transform(localLiftForce, new Vector3d());
        final Vector3d localTorque = localTorqueImpulse.div(substepSeconds, new Vector3d());
        final Vector3d netForce = new Vector3d(gravityForce).add(worldLiftForce);
        final Vector3d position = new Vector3d(body.logicalPose().position());
        final boolean finite = finite(position) && finite(velocity) && finite(gravityForce)
                && finite(worldLiftForce) && finite(netForce) && finite(localTorque);
        return new LiftSample(step, position, velocity, mass, gravityForce, worldLiftForce, netForce,
                localTorque, List.copyOf(visiblePoints), List.copyOf(localLeverArms), finite);
    }

    private static ServerSubLevel resolveBody(final ServerLevel level, final LiftFixtureState state) {
        if (state == null) {
            return null;
        }
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        if (state.bodyId != null && container.getSubLevel(state.bodyId) instanceof final ServerSubLevel body) {
            return body;
        }
        for (final ServerSubLevel candidate : container.getAllSubLevels()) {
            final List<BlockPos> blocks = SimAssemblyHelper.collectBlocks(level, candidate);
            final boolean hasAssembler = blocks.stream()
                    .anyMatch(pos -> level.getBlockState(pos).is(SimulatedBlocks.PHYSICS_ASSEMBLER.get()));
            final boolean hasLift = blocks.stream()
                    .anyMatch(pos -> level.getBlockState(pos).is(AeroBlocks.LEVITITE.get()));
            if (hasAssembler && (state.liftEnabled ? hasLift : isNearFixture(candidate, blocks, state.assembler))) {
                state.bodyId = candidate.getUniqueId();
                return candidate;
            }
        }
        return null;
    }

    private static ServerSubLevel findAnyLiftBody(final ServerLevel level) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        for (final ServerSubLevel candidate : container.getAllSubLevels()) {
            if (SimAssemblyHelper.collectBlocks(level, candidate).stream()
                    .anyMatch(pos -> level.getBlockState(pos).is(AeroBlocks.LEVITITE.get()))) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isNearFixture(final ServerSubLevel candidate, final Collection<BlockPos> blocks,
                                         final BlockPos expectedAssembler) {
        for (final BlockPos raw : blocks) {
            if (candidate.getLevel().getBlockState(raw).is(SimulatedBlocks.PHYSICS_ASSEMBLER.get())) {
                final Vec3 visible = candidate.logicalPose().transformPosition(raw.getCenter());
                if (visible.distanceTo(expectedAssembler.getCenter()) <= 2.0D) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<BlockPos> buildSupport(final ServerLevel level, final BlockPos origin) {
        final Set<BlockPos> support = new HashSet<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                final BlockPos pos = origin.offset(x, -1, z);
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                support.add(pos);
            }
        }
        return support;
    }

    private static Set<BlockPos> buildBody(final ServerLevel level, final BlockPos origin,
                                           final boolean liftEnabled) {
        final Set<BlockPos> body = new HashSet<>();
        level.setBlock(origin, liftEnabled ? AeroBlocks.LEVITITE.get().defaultBlockState()
                : Blocks.PURPUR_BLOCK.defaultBlockState(), 3);
        body.add(origin);
        level.setBlock(origin.above(), SimulatedBlocks.PHYSICS_ASSEMBLER.get().defaultBlockState(), 3);
        body.add(origin.above());
        level.setBlock(origin.east(), Blocks.COPPER_BLOCK.defaultBlockState(), 3);
        level.setBlock(origin.west(), Blocks.COPPER_BLOCK.defaultBlockState(), 3);
        level.setBlock(origin.north(), Blocks.AMETHYST_BLOCK.defaultBlockState(), 3);
        level.setBlock(origin.south(), Blocks.AMETHYST_BLOCK.defaultBlockState(), 3);
        body.add(origin.east());
        body.add(origin.west());
        body.add(origin.north());
        body.add(origin.south());
        glue(level, origin, origin.above());
        glue(level, origin, origin.east());
        glue(level, origin, origin.west());
        glue(level, origin, origin.north());
        glue(level, origin, origin.south());
        return body;
    }

    private static int cleanupParentFixture(final ServerLevel level, final LiftFixtureState state) {
        int removed = 0;
        final Set<BlockPos> parentBlocks = new HashSet<>(state.supportBlocks);
        parentBlocks.addAll(state.bodyBlocks);
        for (final BlockPos pos : parentBlocks) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                removed++;
            }
        }
        final AABB bounds = new AABB(state.origin).inflate(4.0D);
        for (final SuperGlueEntity glue : List.copyOf(level.getEntitiesOfClass(SuperGlueEntity.class, bounds))) {
            glue.remove(Entity.RemovalReason.KILLED);
        }
        return removed;
    }

    private static void glue(final ServerLevel level, final BlockPos first, final BlockPos second) {
        level.addFreshEntity(new SuperGlueEntity(level, SuperGlueEntity.span(first, second)));
    }

    private static boolean finite(final Vector3dc vector) {
        return Double.isFinite(vector.x()) && Double.isFinite(vector.y()) && Double.isFinite(vector.z());
    }

    private static String fixtureKey(final ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static String safe(final String value) {
        return value == null ? "none" : value.replaceAll("\\s+", "_").toLowerCase(Locale.ROOT);
    }

    private static void send(final CommandSourceStack source, final String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    private static final class LiftFixtureState {
        private final BlockPos origin;
        private final BlockPos assembler;
        private final Set<BlockPos> supportBlocks;
        private final Set<BlockPos> bodyBlocks;
        private final boolean liftEnabled;
        private UUID bodyId;
        private int observationTick;
        private LiftSample latestSample;

        private LiftFixtureState(final BlockPos origin, final BlockPos assembler,
                                 final Set<BlockPos> supportBlocks, final Set<BlockPos> bodyBlocks,
                                 final boolean liftEnabled) {
            this.origin = origin;
            this.assembler = assembler;
            this.supportBlocks = Set.copyOf(supportBlocks);
            this.bodyBlocks = Set.copyOf(bodyBlocks);
            this.liftEnabled = liftEnabled;
        }

        private static LiftFixtureState recovered(final UUID bodyId) {
            final LiftFixtureState state = new LiftFixtureState(BlockPos.ZERO, BlockPos.ZERO,
                    Set.of(), Set.of(), true);
            state.bodyId = bodyId;
            return state;
        }
    }

    private record LiftSample(int step, Vector3d position, Vector3d velocity, double mass,
                              Vector3d gravityForce, Vector3d liftForce, Vector3d netForce,
                              Vector3d localTorque, List<Vector3d> visibleApplicationPoints,
                              List<Vector3d> localLeverArms, boolean finite) {
    }
}
