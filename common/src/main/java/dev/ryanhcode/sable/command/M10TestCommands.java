package dev.ryanhcode.sable.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
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
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelBlockEditHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Locale;

/**
 * Static manual-test command harness for M10 multi-block rigid sublevel acceptance.
 */
public final class M10TestCommands {
    private static final BlockState DEFAULT_SPAWN_BLOCKSTATE = Blocks.STONE.defaultBlockState();
    private static final List<BlockPos> SPAWN_L_LOCAL_BLOCKS = List.of(
            new BlockPos(0, 0, 0),
            new BlockPos(1, 0, 0),
            new BlockPos(2, 0, 0),
            new BlockPos(0, 0, 1),
            new BlockPos(0, 0, 2)
    );
    private static final DynamicCommandExceptionType ERROR_M10_FAILED = new DynamicCommandExceptionType(
            message -> Component.literal("SABLE_M10 command failed: " + message));

    private M10TestCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m10")
                .then(Commands.literal("spawn_l")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> spawnL(ctx, DEFAULT_SPAWN_BLOCKSTATE))
                                .then(Commands.argument("block", BlockStateArgument.block(buildContext))
                                        .executes(ctx -> spawnL(ctx, BlockStateArgument.getBlock(ctx, "block").getState())))))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M10TestCommands::inspect)))
                .then(Commands.literal("set_pose")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .then(Commands.argument("yaw_deg", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("pitch_deg", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("roll_deg", DoubleArgumentType.doubleArg())
                                                        .executes(M10TestCommands::setPose))))))
                .then(Commands.literal("set_velocity")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .then(Commands.argument("vx", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("vy", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("vz", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("wx_deg_s", DoubleArgumentType.doubleArg())
                                                                .then(Commands.argument("wy_deg_s", DoubleArgumentType.doubleArg())
                                                                        .then(Commands.argument("wz_deg_s", DoubleArgumentType.doubleArg())
                                                                                .executes(M10TestCommands::setVelocity)))))))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M10TestCommands::stop)))
                .then(Commands.literal("add_block")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .then(Commands.argument("localX", IntegerArgumentType.integer())
                                        .then(Commands.argument("localY", IntegerArgumentType.integer())
                                                .then(Commands.argument("localZ", IntegerArgumentType.integer())
                                                        .then(Commands.argument("block", BlockStateArgument.block(buildContext))
                                                                .executes(M10TestCommands::addBlock)))))))
                .then(Commands.literal("remove_block")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .then(Commands.argument("localX", IntegerArgumentType.integer())
                                        .then(Commands.argument("localY", IntegerArgumentType.integer())
                                                .then(Commands.argument("localZ", IntegerArgumentType.integer())
                                                        .executes(M10TestCommands::removeBlock))))))
                .then(Commands.literal("validate")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M10TestCommands::validate))));
    }

    private static int spawnL(final CommandContext<CommandSourceStack> ctx, final BlockState material) throws CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final String name = StringArgumentType.getString(ctx, "name");
        final ServerSubLevelContainer plotContainer = SableCommandHelper.requireSubLevelContainer(ctx);
        ServerSubLevel subLevel = null;

        Sable.LOGGER.info("SABLE_M10 phase=spawn_begin command=spawn_l name={} material={} position={}",
                name, material, source.getPosition());
        try {
            final Vec3 spawnPos = Vec3.atCenterOf(BlockPos.containing(source.getPosition()));
            final Pose3d pose = new Pose3d();
            pose.position().set(spawnPos.x, spawnPos.y, spawnPos.z);

            subLevel = (ServerSubLevel) plotContainer.allocateNewSubLevel(pose);
            subLevel.setName(name);

            final ServerLevelPlot plot = subLevel.getPlot();
            plot.newEmptyChunk(plot.getCenterChunk());

            final List<SubLevelBlockEditHelper.BlockChange> changes = new ObjectArrayList<>(SPAWN_L_LOCAL_BLOCKS.size());
            for (final BlockPos localBlock : SPAWN_L_LOCAL_BLOCKS) {
                changes.add(SubLevelBlockEditHelper.setLocalBlock(subLevel, localBlock, material, 3, false));
            }

            Sable.LOGGER.info("SABLE_M10 phase=blocks_set command=spawn_l id={} name={} blockCount={} localBlocks={}",
                    subLevel.getUniqueId(), name, changes.size(), SPAWN_L_LOCAL_BLOCKS);
            SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
            subLevel.updateLastPose();

            final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(subLevel.getLevel());
            final int collisionSections = physicsSystem == null ? 0 : physicsSystem.getUploadedCollisionSectionCount(subLevel);
            final int collisionBlocks = physicsSystem == null ? 0 : physicsSystem.getUploadedCollisionBlockCount(subLevel);
            Sable.LOGGER.info("SABLE_M10_COLLISION phase=initial_build id={} name={} blocks={} uploadedSections={} collisionGeometryPresent={}",
                    subLevel.getUniqueId(), name, collisionBlocks, collisionSections, collisionSections > 0 && collisionBlocks > 0);

            final MassData mass = subLevel.getMassTracker();
            Sable.LOGGER.info("SABLE_M10 phase=physics_finalized command=spawn_l id={} mass={} selfMass={} centerOfMass={} selfCenterOfMass={} bounds={}",
                    subLevel.getUniqueId(),
                    mass.getMass(),
                    subLevel.getSelfMassTracker().getMass(),
                    mass.getCenterOfMass(),
                    subLevel.getSelfMassTracker().getCenterOfMass(),
                    plot.getBoundingBox());
            Sable.LOGGER.info("SABLE_M10 phase=registered command=spawn_l id={} name={} plot={} centerChunk={}",
                    subLevel.getUniqueId(), name, plot.plotPos, plot.getCenterChunk());

            final ServerSubLevel createdSubLevel = subLevel;
            source.sendSuccess(() -> Component.literal("SABLE_M10_SPAWN_L id=" + createdSubLevel.getUniqueId()
                    + " name=" + name
                    + " blockCount=" + changes.size()
                    + " localBlocks=" + SPAWN_L_LOCAL_BLOCKS), false);
            return 1;
        } catch (final RuntimeException exception) {
            rollbackSpawnSubLevel(plotContainer, subLevel, name, exception);
            throw commandFailure(exception);
        }
    }

    private static int inspect(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(ctx, "target");
        final String line = inspectLine(subLevel);

        Sable.LOGGER.info(line);
        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    private static int setPose(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final ServerSubLevel subLevel = requireMutableTarget(ctx);
        final double yawDeg = DoubleArgumentType.getDouble(ctx, "yaw_deg");
        final double pitchDeg = DoubleArgumentType.getDouble(ctx, "pitch_deg");
        final double rollDeg = DoubleArgumentType.getDouble(ctx, "roll_deg");
        final Quaterniond orientation = orientationFromYawPitchRoll(yawDeg, pitchDeg, rollDeg);
        final RigidBodyHandle handle = SableCommandHelper.requireSubLevelPhysicsSystem(ctx).getPhysicsHandle(subLevel);

        handle.teleport(subLevel.logicalPose().position(), orientation);
        subLevel.updateBoundingBox();
        Sable.LOGGER.info("SABLE_M10 phase=pose_set id={} yawDeg={} pitchDeg={} rollDeg={} orientation={}",
                subLevel.getUniqueId(), yawDeg, pitchDeg, rollDeg, orientation);
        ctx.getSource().sendSuccess(() -> Component.literal("SABLE_M10_POSE_SET id=" + subLevel.getUniqueId()
                + " yawDeg=" + fmt(yawDeg)
                + " pitchDeg=" + fmt(pitchDeg)
                + " rollDeg=" + fmt(rollDeg)
                + " orientation=" + formatQuaternion(orientation)
                + " convention=yawY(-deg)->pitchX(+deg)->rollZ(+deg)"), false);
        return 1;
    }

    private static int setVelocity(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final ServerSubLevel subLevel = requireMutableTarget(ctx);
        final Vector3d linearVelocity = new Vector3d(
                DoubleArgumentType.getDouble(ctx, "vx"),
                DoubleArgumentType.getDouble(ctx, "vy"),
                DoubleArgumentType.getDouble(ctx, "vz"));
        final Vector3d angularVelocity = new Vector3d(
                Math.toRadians(DoubleArgumentType.getDouble(ctx, "wx_deg_s")),
                Math.toRadians(DoubleArgumentType.getDouble(ctx, "wy_deg_s")),
                Math.toRadians(DoubleArgumentType.getDouble(ctx, "wz_deg_s")));

        final SubLevelPhysicsSystem physicsSystem = SableCommandHelper.requireSubLevelPhysicsSystem(ctx);
        final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        handle.setLinearAndAngularVelocity(linearVelocity, angularVelocity);
        final Vector3d linearAfterSet = handle.getLinearVelocity(new Vector3d());
        final Vector3d angularAfterSet = handle.getAngularVelocity(new Vector3d());
        final Boolean sleepingAfterSet = physicsSystem.getPipeline().isSleeping(subLevel);
        Sable.LOGGER.info(
                "SABLE_M10_VELOCITY id={} requestedLinear={} requestedAngularDeg={} requestedAngularRad={} "
                        + "rapierLinearAfterSet={} rapierAngularAfterSet={} sleepingAfterSet={} angularDamping={}",
                subLevel.getUniqueId(), linearVelocity, formatVectorDeg(angularVelocity), angularVelocity,
                linearAfterSet, angularAfterSet, formatNullableBoolean(sleepingAfterSet),
                fmt(physicsSystem.getPipeline().getAngularDamping(subLevel)));
        Sable.LOGGER.info("SABLE_M10 phase=velocity_set id={} linearVelocity={} angularVelocityRadS={}",
                subLevel.getUniqueId(), linearVelocity, angularVelocity);
        ctx.getSource().sendSuccess(() -> Component.literal("SABLE_M10_VELOCITY_SET id=" + subLevel.getUniqueId()
                + " linearVelocity=" + formatVector(linearVelocity)
                + " angularVelocityRadS=" + formatVector(angularVelocity)
                + " angularVelocityDegS=" + formatVectorDeg(angularVelocity)
                + " convention=global_linear_m_per_s_global_angular_deg_per_s_input"), false);
        return 1;
    }

    private static int stop(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final ServerSubLevel subLevel = requireMutableTarget(ctx);
        SableCommandHelper.requireSubLevelPhysicsSystem(ctx)
                .getPhysicsHandle(subLevel)
                .setLinearAndAngularVelocity(new Vector3d(), new Vector3d());
        Sable.LOGGER.info("SABLE_M10 phase=velocity_set command=stop id={} linearVelocity=(0,0,0) angularVelocityRadS=(0,0,0)",
                subLevel.getUniqueId());
        ctx.getSource().sendSuccess(() -> Component.literal("SABLE_M10_STOP id=" + subLevel.getUniqueId()), false);
        return 1;
    }

    private static int addBlock(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final ServerSubLevel subLevel = requireMutableTarget(ctx);
        final BlockPos localOffset = localOffsetFromArguments(ctx);
        final BlockState state = BlockStateArgument.getBlock(ctx, "block").getState();
        final SubLevelBlockEditHelper.BlockChange change = SubLevelBlockEditHelper.setLocalBlock(subLevel, localOffset, state, 3, true);
        SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, List.of(change));

        Sable.LOGGER.info("SABLE_M10 phase=edit_add id={} local={} plotBlock={} old={} new={} blockCount={} mass={} centerOfMass={} bounds={}",
                subLevel.getUniqueId(), localOffset, change.plotBlockPos(), change.oldState(), state,
                SubLevelBlockEditHelper.countNonAirBlocks(subLevel),
                subLevel.getMassTracker().getMass(),
                subLevel.getMassTracker().getCenterOfMass(),
                subLevel.getPlot().getBoundingBox());
        ctx.getSource().sendSuccess(() -> Component.literal("SABLE_M10_EDIT_ADD id=" + subLevel.getUniqueId()
                + " local=" + formatBlockPos(localOffset)
                + " plotBlock=" + formatBlockPos(change.plotBlockPos())
                + " block=" + state), false);
        return 1;
    }

    private static int removeBlock(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final ServerSubLevel subLevel = requireMutableTarget(ctx);
        final BlockPos localOffset = localOffsetFromArguments(ctx);
        final SubLevelBlockEditHelper.BlockChange change = SubLevelBlockEditHelper.setLocalBlock(subLevel, localOffset, Blocks.AIR.defaultBlockState(), 3, true);
        SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, List.of(change));

        Sable.LOGGER.info("SABLE_M10 phase=edit_remove id={} local={} plotBlock={} old={} blockCount={} mass={} centerOfMass={} bounds={}",
                subLevel.getUniqueId(), localOffset, change.plotBlockPos(), change.oldState(),
                SubLevelBlockEditHelper.countNonAirBlocks(subLevel),
                subLevel.getMassTracker().getMass(),
                subLevel.getMassTracker().getCenterOfMass(),
                subLevel.getPlot().getBoundingBox());
        ctx.getSource().sendSuccess(() -> Component.literal("SABLE_M10_EDIT_REMOVE id=" + subLevel.getUniqueId()
                + " local=" + formatBlockPos(localOffset)
                + " plotBlock=" + formatBlockPos(change.plotBlockPos())
                + " old=" + change.oldState()), false);
        return 1;
    }

    private static int validate(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(ctx, "target");
        final List<String> failures = new ObjectArrayList<>();
        final int blockCount = SubLevelBlockEditHelper.countNonAirBlocks(subLevel);
        final int massBlockCount = SubLevelBlockEditHelper.countMassContributingBlocks(subLevel);
        final MassData mass = subLevel.getMassTracker();
        final MassData selfMass = subLevel.getSelfMassTracker();
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(subLevel.getLevel());
        final RigidBodyHandle handle = physicsSystem == null ? null : physicsSystem.getPhysicsHandle(subLevel);
        final boolean collisionGeometryPresent = physicsSystem != null && physicsSystem.hasUploadedCollisionGeometry(subLevel);
        final boolean physicsBodyRegistered = physicsSystem != null && physicsSystem.getPipeline().isBodyRegistered(subLevel);
        final Vector3d linearVelocity = handle == null ? new Vector3d() : handle.getLinearVelocity(new Vector3d());
        final Vector3d angularVelocity = handle == null ? new Vector3d() : handle.getAngularVelocity(new Vector3d());
        final MassTracker recomputedSelfMass = SubLevelBlockEditHelper.recomputeSelfMassData(subLevel);

        requireInvariant(!subLevel.isRemoved(), "sublevel_not_removed", failures);
        requireInvariant(blockCount > 0, "blockCount_positive", failures);
        requireInvariant(mass != null && finitePositive(mass.getMass()), "mass_finite_positive", failures);
        requireInvariant(selfMass != null && finitePositive(selfMass.getMass()), "selfMass_finite_positive", failures);
        requireInvariant(mass != null && finiteVector(mass.getCenterOfMass()), "centerOfMass_finite", failures);
        requireInvariant(selfMass != null && finiteVector(selfMass.getCenterOfMass()), "selfCenterOfMass_finite", failures);
        requireInvariant(bounds != BoundingBox3i.EMPTY && bounds.volume() > 0, "bounds_valid", failures);
        requireInvariant(physicsSystem != null, "physicsSystem_present", failures);
        requireInvariant(handle != null && handle.isValid(), "rigidBody_present", failures);
        requireInvariant(physicsBodyRegistered, "physics_body_not_registered", failures);
        requireInvariant(collisionGeometryPresent, "no_collision_geometry", failures);
        requireInvariant(massConsistent(selfMass, recomputedSelfMass), "mass_state_mismatch", failures);
        requireInvariant(finiteVector(linearVelocity), "linearVelocity_finite", failures);
        requireInvariant(finiteVector(angularVelocity), "angularVelocity_finite", failures);
        requireInvariant(nonAirBlocksWithinBounds(subLevel), "storedBlocks_within_bounds", failures);

        final String line = "SABLE_M10_VALIDATE status=" + (failures.isEmpty() ? "PASS" : "FAIL")
                + " id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " reason=" + (failures.isEmpty() ? "none" : failures.get(0))
                + " blockCount=" + blockCount
                + " massContributingBlockCount=" + massBlockCount
                + " mass=" + fmt(mass == null ? Double.NaN : mass.getMass())
                + " recomputedSelfMass=" + fmt(recomputedSelfMass.getMass())
                + " bounds=" + formatBounds(bounds)
                + " collisionGeometryPresent=" + collisionGeometryPresent
                + " physicsBodyRegistered=" + physicsBodyRegistered
                + " failures=" + failures;
        Sable.LOGGER.info(line);
        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        return failures.isEmpty() ? 1 : 0;
    }

    private static ServerSubLevel requireMutableTarget(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(ctx, "target");
        if (subLevel.isRemoved()) {
            throw ERROR_M10_FAILED.create("Target sub-level is removed: " + subLevel.getUniqueId());
        }

        final SubLevelPhysicsSystem physicsSystem = SableCommandHelper.requireSubLevelPhysicsSystem(ctx);
        final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        if (!handle.isValid()) {
            throw ERROR_M10_FAILED.create("Target rigid body is not valid: " + subLevel.getUniqueId());
        }
        if (!physicsSystem.getPipeline().isBodyRegistered(subLevel)) {
            throw ERROR_M10_FAILED.create("Target rigid body is not registered in the physics pipeline: " + subLevel.getUniqueId());
        }

        return subLevel;
    }

    private static String inspectLine(final ServerSubLevel subLevel) {
        final MassData mass = subLevel.getMassTracker();
        final MassData selfMass = subLevel.getSelfMassTracker();
        final BoundingBox3ic plotBounds = subLevel.getPlot().getBoundingBox();
        final BoundingBox3dc worldBounds = subLevel.boundingBox();
        final Pose3d pose = subLevel.logicalPose();
        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(subLevel.getLevel());
        final boolean physicsSystemPresent = physicsSystem != null;
        final RigidBodyHandle handle = physicsSystemPresent ? physicsSystem.getPhysicsHandle(subLevel) : null;
        final boolean rigidBodyPresent = handle != null && handle.isValid();
        final boolean collisionGeometryPresent = physicsSystem != null && physicsSystem.hasUploadedCollisionGeometry(subLevel);
        final boolean physicsBodyRegistered = physicsSystem != null && physicsSystem.getPipeline().isBodyRegistered(subLevel);
        final int collisionSections = physicsSystem == null ? 0 : physicsSystem.getUploadedCollisionSectionCount(subLevel);
        final int collisionBlocks = physicsSystem == null ? 0 : physicsSystem.getUploadedCollisionBlockCount(subLevel);
        final double angularDamping = physicsSystem == null ? Double.NaN : physicsSystem.getPipeline().getAngularDamping(subLevel);
        final Boolean sleeping = physicsSystem == null ? null : physicsSystem.getPipeline().isSleeping(subLevel);
        final Boolean canSleep = physicsSystem == null ? null : physicsSystem.getPipeline().canSleep(subLevel);
        final Boolean enabled = physicsSystem == null ? null : physicsSystem.getPipeline().isEnabled(subLevel);
        final String rigidBodyType = physicsSystem == null ? "unavailable" : physicsSystem.getPipeline().getRigidBodyType(subLevel);
        final Vector3d linearVelocity = rigidBodyPresent ? handle.getLinearVelocity(new Vector3d()) : new Vector3d(Double.NaN);
        final Vector3d angularVelocity = rigidBodyPresent ? handle.getAngularVelocity(new Vector3d()) : new Vector3d(Double.NaN);
        final int blockCount = SubLevelBlockEditHelper.countNonAirBlocks(subLevel);
        final int massBlockCount = SubLevelBlockEditHelper.countMassContributingBlocks(subLevel);

        return "SABLE_M10_INSPECT"
                + " id=" + subLevel.getUniqueId()
                + " runtimeId=" + subLevel.getRuntimeId()
                + " name=" + nameOrNone(subLevel)
                + " blockCount=" + blockCount
                + " nonAirBlockCount=" + blockCount
                + " massContributingBlockCount=" + massBlockCount
                + " mass=" + fmt(mass == null ? Double.NaN : mass.getMass())
                + " selfMass=" + fmt(selfMass == null ? Double.NaN : selfMass.getMass())
                + " centerOfMass=" + formatVector(mass == null ? null : mass.getCenterOfMass())
                + " selfCenterOfMass=" + formatVector(selfMass == null ? null : selfMass.getCenterOfMass())
                + " bounds=" + formatBounds(plotBounds)
                + " worldBounds=" + formatWorldBounds(worldBounds)
                + " position=" + formatVector(pose.position())
                + " orientation=" + formatQuaternion(pose.orientation())
                + " rotationPoint=" + formatVector(pose.rotationPoint())
                + " linearVelocity=" + formatVector(linearVelocity)
                + " angularVelocityRadS=" + formatVector(angularVelocity)
                + " angularVelocityDegS=" + formatVectorDeg(angularVelocity)
                + " angularDamping=" + fmt(angularDamping)
                + " sleeping=" + formatNullableBoolean(sleeping)
                + " canSleep=" + formatNullableBoolean(canSleep)
                + " rigidBodyType=" + rigidBodyType
                + " enabled=" + formatNullableBoolean(enabled)
                + " physicsSystemPresent=" + physicsSystemPresent
                + " rigidBodyPresent=" + rigidBodyPresent
                + " physicsBodyRegistered=" + physicsBodyRegistered
                + " colliderCount=unavailable"
                + " collisionGeometryPresent=" + collisionGeometryPresent
                + " collisionGeometryUploadedSections=" + collisionSections
                + " collisionGeometryUploadedBlocks=" + collisionBlocks
                + " trackingPlayers=" + subLevel.getTrackingPlayers().size()
                + " lastNetworkedStopped=" + subLevel.getLastNetworkedStopped()
                + " plot=" + formatChunkPos(subLevel.getPlot().plotPos)
                + " centerChunk=" + formatChunkPos(subLevel.getPlot().getCenterChunk())
                + " centerBlock=" + formatBlockPos(subLevel.getPlot().getCenterBlock());
    }

    private static void rollbackSpawnSubLevel(final ServerSubLevelContainer plotContainer, final @Nullable SubLevel subLevel,
                                              final String name, final Throwable failure) {
        if (subLevel == null || subLevel.isRemoved()) {
            return;
        }

        Sable.LOGGER.warn("SABLE_M10 phase=rollback_begin command=spawn_l name={} id={} reason={}",
                name, subLevel.getUniqueId(), failure.getClass().getSimpleName());
        try {
            plotContainer.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
            Sable.LOGGER.warn("SABLE_M10 phase=rollback_complete command=spawn_l name={} id={}",
                    name, subLevel.getUniqueId());
        } catch (final Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
            Sable.LOGGER.error("SABLE_M10 phase=rollback_failed command=spawn_l name={} id={}",
                    name, subLevel.getUniqueId(), cleanupFailure);
        }
    }

    private static CommandSyntaxException commandFailure(final RuntimeException exception) {
        Sable.LOGGER.error("SABLE_M10 phase=failed", exception);
        final String message = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        return ERROR_M10_FAILED.create(message);
    }

    private static BlockPos localOffsetFromArguments(final CommandContext<CommandSourceStack> ctx) {
        return new BlockPos(
                IntegerArgumentType.getInteger(ctx, "localX"),
                IntegerArgumentType.getInteger(ctx, "localY"),
                IntegerArgumentType.getInteger(ctx, "localZ"));
    }

    private static Quaterniond orientationFromYawPitchRoll(final double yawDeg, final double pitchDeg, final double rollDeg) {
        return new Quaterniond()
                .rotateY(-Math.toRadians(yawDeg))
                .rotateX(Math.toRadians(pitchDeg))
                .rotateZ(Math.toRadians(rollDeg));
    }

    private static void requireInvariant(final boolean condition, final String name, final List<String> failures) {
        if (!condition) {
            failures.add(name);
        }
    }

    private static boolean nonAirBlocksWithinBounds(final ServerSubLevel subLevel) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        if (bounds == BoundingBox3i.EMPTY || bounds.volume() <= 0) {
            return false;
        }

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
                            if (!section.getBlockState(x, y, z).isAir()
                                    && !bounds.contains(minX + x, minY + y, minZ + z)) {
                                return false;
                            }
                        }
                    }
                }
            }
        }

        return true;
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

    private static boolean massConsistent(@Nullable final MassData actual, @Nullable final MassData recomputed) {
        if (actual == null || recomputed == null || actual.getCenterOfMass() == null || recomputed.getCenterOfMass() == null) {
            return false;
        }

        final double massTolerance = Math.max(1.0E-6, Math.abs(recomputed.getMass()) * 1.0E-6);
        return Math.abs(actual.getMass() - recomputed.getMass()) <= massTolerance
                && actual.getCenterOfMass().distanceSquared(recomputed.getCenterOfMass()) <= 1.0E-10;
    }

    private static String nameOrNone(final ServerSubLevel subLevel) {
        return subLevel.getName() != null ? subLevel.getName() : "<none>";
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

    private static String formatVectorDeg(final Vector3dc vector) {
        return "(" + fmt(Math.toDegrees(vector.x())) + "," + fmt(Math.toDegrees(vector.y())) + "," + fmt(Math.toDegrees(vector.z())) + ")";
    }

    private static String formatNullableBoolean(final Boolean value) {
        return value == null ? "unavailable" : value.toString();
    }

    private static String formatQuaternion(final Quaterniondc quaternion) {
        return "(" + fmt(quaternion.x()) + "," + fmt(quaternion.y()) + "," + fmt(quaternion.z()) + "," + fmt(quaternion.w()) + ")";
    }

    private static String formatBounds(final BoundingBox3ic bounds) {
        if (bounds == BoundingBox3i.EMPTY || bounds.volume() <= 0) {
            return "EMPTY";
        }
        return "(" + bounds.minX() + "," + bounds.minY() + "," + bounds.minZ() + ")->("
                + bounds.maxX() + "," + bounds.maxY() + "," + bounds.maxZ() + ")";
    }

    private static String formatWorldBounds(final BoundingBox3dc bounds) {
        return "(" + fmt(bounds.minX()) + "," + fmt(bounds.minY()) + "," + fmt(bounds.minZ()) + ")->("
                + fmt(bounds.maxX()) + "," + fmt(bounds.maxY()) + "," + fmt(bounds.maxZ()) + ")";
    }

    private static String formatBlockPos(final BlockPos pos) {
        return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    private static String formatChunkPos(final ChunkPos pos) {
        return "(" + pos.x + "," + pos.z + ")";
    }
}
