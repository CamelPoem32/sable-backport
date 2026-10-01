package dev.ryanhcode.sable.command;

import com.mojang.brigadier.CommandDispatcher;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.chassis.ChassisBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.funnel.SableMovingFunnelTarget;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.forge.event.ForgeSablePostPhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.items.IItemHandler;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Command-owned M37 fixture. Create owns all actor and stationary-Funnel transfers. */
public final class M37FunnelFixtureCommands {
    private static final String ORIGIN_TAG = "sable_m37_origin";
    private static final String STATION_TAG = "sable_m37_station";
    private static final String GLUE_TAG = "sable_m37_glue";
    private static final String ITEM_TAG = "sable_m37_item";
    private static final int TIMEOUT_TICKS = 600;
    private static Pending pending;
    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static final Map<UUID, Cleanup> CLEANUPS = new HashMap<>();
    private static final Map<UUID, Yaw> YAWS = new HashMap<>();

    private M37FunnelFixtureCommands() {
    }

    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(M37FunnelFixtureCommands::tick);
        MinecraftForge.EVENT_BUS.addListener(M37FunnelFixtureCommands::entityJoined);
        MinecraftForge.EVENT_BUS.addListener(M37FunnelFixtureCommands::postPhysicsTick);
        MinecraftForge.EVENT_BUS.addListener(M37FunnelFixtureCommands::serverStopped);
    }

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sable_m37").requires(source -> source.hasPermission(2))
                .then(Commands.literal("insert").executes(context -> setup(context.getSource(), Kind.INSERT)))
                .then(Commands.literal("extract").executes(context -> setup(context.getSource(), Kind.EXTRACT)))
                .then(Commands.literal("entity").executes(context -> setup(context.getSource(), Kind.ENTITY)))
                .then(Commands.literal("validate").executes(context -> validate(context.getSource())))
                .then(Commands.literal("run").executes(context -> run(context.getSource())))
                .then(Commands.literal("yaw90").executes(context -> yaw90(context.getSource())))
                .then(Commands.literal("disconnect").executes(context -> disconnect(context.getSource())))
                .then(Commands.literal("cleanup").executes(context -> cleanup(context.getSource()))));
    }

    private static int setup(final CommandSourceStack source, final Kind kind) {
        final ServerLevel level = source.getLevel();
        final Lookup existing = lookup(level);
        if (pending != null || existing.status != Status.NONE) {
            return refuse(source, "SETUP REFUSED: existing fixture status=" + existing.status
                    + "; run /function sable:m37/cleanup");
        }
        final BlockPos origin = BlockPos.containing(source.getPosition()).above(4);
        for (int x = -12; x <= 18; x++) {
            for (int y = -3; y <= 4; y++) {
                for (int z = -2; z <= 2; z++) {
                    final BlockPos cell = origin.offset(x, y, z);
                    if (!level.getBlockState(cell).isAir()) {
                        return refuse(source, "SETUP REFUSED: occupied reserved cell " + cell.toShortString());
                    }
                }
            }
        }
        pending = new Pending(level, origin, kind, observers(source));
        tell(pending.observers, level, "[M37] SETUP phase=BUILD kind=" + kind);
        source.getServer().getCommands().performPrefixedCommand(
                source.withPosition(Vec3.atLowerCornerOf(origin)),
                "function sable:m37/internal/create_" + kind.name().toLowerCase(java.util.Locale.ROOT));
        return 1;
    }

    private static int validate(final CommandSourceStack source) {
        final Pending build = pending;
        if (build == null || build.level != source.getLevel()
                || !build.origin.equals(BlockPos.containing(source.getPosition()))) {
            return refuse(source, "SETUP FAILED: no matching pending fixture");
        }
        tell(build.observers, build.level, "[M37] SETUP phase=VALIDATE");
        final String failure = validateFresh(build);
        if (failure != null) {
            tell(build.observers, build.level, "[M37] SETUP FAILED reason=" + failure);
            tell(build.observers, build.level, "[M37] SETUP phase=ROLLBACK");
            removeStatic(build.level, build.origin, build.kind, build.origin.offset(
                    build.kind.stationOffset().getX(), build.kind.stationOffset().getY(),
                    build.kind.stationOffset().getZ()));
            tell(build.observers, build.level, "[M37] SETUP ROLLBACK COMPLETE");
            pending = null;
            return 0;
        }
        pending = null;
        tell(build.observers, build.level, "[M37] SETUP phase=READY outerSelectedBlocks=22");
        tell(build.observers, build.level, "[M37] READY: click Physics Assembler, then run /function sable:m37/run");
        return 1;
    }

    private static String validateFresh(final Pending build) {
        final ServerLevel level = build.level;
        final BlockPos origin = build.origin;
        final List<ArmorStand> origins = markers(level, ORIGIN_TAG);
        final List<ArmorStand> stations = markers(level, STATION_TAG);
        if (origins.size() != 1 || stations.size() != 1
                || !origins.get(0).blockPosition().equals(origin)
                || !stations.get(0).blockPosition().equals(origin.offset(build.kind.stationOffset()))) {
            return "MARKER_MISSING_OR_AMBIGUOUS";
        }
        if (!(level.getBlockEntity(origin) instanceof MechanicalPistonBlockEntity)
                || !(level.getBlockEntity(origin.below(2)) instanceof CreativeMotorBlockEntity)
                || !(level.getBlockEntity(origin.above().north(2)) instanceof PhysicsAssemblerBlockEntity)
                || !level.getBlockState(origin.north(2)).is(Blocks.IRON_BLOCK)
                || !level.getBlockState(origin.north()).is(Blocks.IRON_BLOCK)) {
            return "INNER_CONTROLLER_OR_OUTER_STARTING_BLOCK_INVALID";
        }
        for (int x = -12; x <= -1; x++) {
            if (!level.getBlockState(origin.offset(x, 0, 0)).is(AllBlocks.PISTON_EXTENSION_POLE.get())) {
                return "PISTON_EXTENSION_POLE_MISSING_" + x;
            }
        }
        final BlockPos movingFunnelPos = movingFunnelPos(origin, build.kind);
        final BlockState movingFunnel = level.getBlockState(movingFunnelPos);
        if (!movingFunnel.is(AllBlocks.ANDESITE_FUNNEL.get())
                || movingFunnel.getValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.FACING)
                        != build.kind.movingFacing()
                || movingFunnel.getValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.EXTRACTING)
                        != (build.kind == Kind.INSERT)
                || !(level.getBlockEntity(origin.east()) instanceof final ChassisBlockEntity chassis)
                || !(level.getBlockEntity(origin.east().south()) instanceof final Container mounted)) {
            return "INNER_FUNNEL_OR_MOUNTED_STORAGE_MISSING";
        }
        final List<BlockPos> captured = chassis.getIncludedBlockPositions(Direction.EAST, false);
        if (build.kind == Kind.INSERT) {
            tell(build.observers, level, "[M37] INSERT_ATTACHMENT chassis=" + origin.east().toShortString()
                    + "/" + level.getBlockState(origin.east()) + " expectedFunnel="
                    + movingFunnelPos.toShortString() + " expectedChest="
                    + origin.east().south().toShortString() + " nativeIncludedBlocks=" + captured
                    + " funnelIncluded=" + (captured != null && captured.contains(movingFunnelPos))
                    + " chestIncluded=" + (captured != null && captured.contains(origin.east().south())));
        }
        if (captured == null || !captured.contains(movingFunnelPos)
                || !captured.contains(origin.east().south())) {
            return "INNER_NATIVE_CHASSIS_ATTACHMENT_INCOMPLETE";
        }
        final int expectedMounted = build.kind == Kind.INSERT ? 16 : 0;
        if (mounted.getItem(0).getCount() != expectedMounted) {
            return "MOUNTED_ITEM_COUNT_INVALID";
        }
        final BlockPos station = origin.offset(build.kind.stationOffset());
        if (build.kind == Kind.ENTITY) {
            if (!level.getBlockState(station).isAir()
                    || !level.getBlockState(station.below()).is(Blocks.STONE)) {
                return "PARENT_ENTITY_CELL_INVALID";
            }
        } else if (build.kind == Kind.INSERT) {
            final BlockPos intake = insertCollector(station, false);
            final BlockState collector = level.getBlockState(intake);
            traceStationComponent(build, "PARENT_FUNNEL", intake, AllBlocks.ANDESITE_FUNNEL.get(),
                    com.simibubi.create.content.logistics.funnel.FunnelBlockEntity.class, Direction.UP);
            traceStationComponent(build, "PARENT_CHEST", intake.below(), Blocks.CHEST,
                    net.minecraft.world.level.block.entity.ChestBlockEntity.class, null);
            traceStationConnectivity(build, intake, intake.below(), Direction.UP);
            traceStationSignal(build, intake, false);
            if (!level.getBlockState(station).isAir()
                    || !collector.is(AllBlocks.ANDESITE_FUNNEL.get())
                    || collector.getValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.FACING)
                            != Direction.UP
                    || collector.getValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.EXTRACTING)
                    || level.hasNeighborSignal(intake)
                    || !(level.getBlockEntity(intake)
                            instanceof com.simibubi.create.content.logistics.funnel.FunnelBlockEntity)
                    || !(level.getBlockEntity(intake.below()) instanceof final Container parentChest)
                    || parentChest.getItem(0).getCount() != 0) {
                return "PARENT_INSERT_COLLECTOR_OR_CHEST_INVALID";
            }
            final boolean collisionFree = M37FunnelFixtureLayout.collisionFree(origin, intake);
            final boolean trajectoryIntersectsCollector = M37FunnelFixtureLayout.fallingItemReachesIntake(
                    new Vec3(station.getX() + 0.5, origin.getY() + 1.65,
                            station.getZ() + 0.5), intake);
            tell(build.observers, level, "[M37] INSERT_LAYOUT movingContraptionSweptAABB="
                    + M37FunnelFixtureLayout.movingPayloadSweep(origin)
                    + " stationaryFunnelAABB=" + new AABB(intake)
                    + " stationaryChestAABB=" + new AABB(intake.below())
                    + " minimumBlockClearance="
                    + M37FunnelFixtureLayout.minimumBlockClearance(origin, intake)
                    + " nativeAttachmentValid=true"
                    + " itemTrajectoryIntersectsCollector=" + trajectoryIntersectsCollector
                    + " payloadSweptAabbIntersectsStation=" + !collisionFree
                    + " collisionFree=" + collisionFree);
            if (!collisionFree || !trajectoryIntersectsCollector) {
                return "PARENT_INSERT_SWEPT_COLLISION_OR_DROP_COLUMN_INVALID";
            }
        } else {
            final BlockState stationaryFunnel = level.getBlockState(station);
            traceStationComponent(build, "PARENT_FUNNEL", station, AllBlocks.ANDESITE_FUNNEL.get(),
                    com.simibubi.create.content.logistics.funnel.FunnelBlockEntity.class,
                    build.kind.stationFacing());
            traceStationComponent(build, "PARENT_CHEST", station.relative(build.kind.chestDirection()),
                    Blocks.CHEST, net.minecraft.world.level.block.entity.ChestBlockEntity.class, null);
            traceStationConnectivity(build, station, station.relative(build.kind.chestDirection()),
                    build.kind.stationFacing());
            traceStationSignal(build, station, true);
            if (!stationaryFunnel.is(AllBlocks.ANDESITE_FUNNEL.get())
                    || stationaryFunnel.getValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.FACING)
                            != build.kind.stationFacing()
                    || stationaryFunnel.getValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.EXTRACTING)
                            != (build.kind == Kind.EXTRACT)
                    || !level.hasNeighborSignal(station)
                    || !(level.getBlockEntity(station) instanceof com.simibubi.create.content.logistics.funnel.FunnelBlockEntity)
                    || !(level.getBlockEntity(station.relative(build.kind.chestDirection()))
                            instanceof final Container parentChest)
                    || parentChest.getItem(0).getCount() != (build.kind == Kind.EXTRACT ? 16 : 0)) {
                return "PARENT_STATION_OR_CHEST_INVALID";
            }
            if (build.kind == Kind.EXTRACT
                    && !level.getBlockState(station.below()).is(Blocks.STONE)) {
                return "PARENT_OUTPUT_SUPPORT_MISSING";
            }
        }
        final AABB glueBounds = new AABB(origin.getX(), origin.getY() - 2, origin.getZ() - 2,
                origin.getX() + 1, origin.getY() + 2, origin.getZ() + 2);
        final SuperGlueEntity glue = new SuperGlueEntity(level, glueBounds);
        glue.addTag(GLUE_TAG);
        if (!level.addFreshEntity(glue) || !glue.contains(origin.north(2))
                || !glue.contains(origin) || glue.contains(origin.east())) {
            return "OUTER_NATIVE_GLUE_INVALID";
        }
        final SimAssemblyContraption selection = new SimAssemblyContraption(null);
        try {
            if (!selection.searchMovedStructure(level, origin.north(2))) {
                return "OUTER_ASSEMBLY_TRAVERSAL_REJECTED";
            }
        } catch (final AssemblyException exception) {
            return "OUTER_ASSEMBLY_EXCEPTION_" + exception.getClass().getSimpleName();
        }
        final var selected = selection.getBlocks();
        if (selected.size() != 22 || !selected.contains(movingFunnelPos)
                || !selected.contains(origin.east().south()) || selected.contains(station)) {
            return "OUTER_SELECTION_INVALID_" + selected.size();
        }
        return null;
    }

    private static void traceStationComponent(final Pending build, final String component, final BlockPos pos,
                                              final Block expected, final Class<? extends BlockEntity> expectedBE,
                                              final Direction expectedFacing) {
        final BlockState actual = build.level.getBlockState(pos);
        final BlockEntity actualBE = build.level.getBlockEntity(pos);
        final Direction actualFacing = actual.hasProperty(
                com.simibubi.create.content.logistics.funnel.FunnelBlock.FACING)
                ? actual.getValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.FACING) : null;
        final boolean match = actual.is(expected) && (expectedBE == null || expectedBE.isInstance(actualBE))
                && (expectedFacing == null || expectedFacing == actualFacing);
        tell(build.observers, build.level, "[M37] VALIDATE kind=" + build.kind + " component=" + component
                + " pos=" + pos.toShortString() + " expectedState=" + expected
                + " actualState=" + actual + " expectedBE=" + (expectedBE == null ? "none" : expectedBE.getSimpleName())
                + " actualBE=" + (actualBE == null ? "none" : actualBE.getClass().getSimpleName())
                + " expectedFacing=" + expectedFacing + " actualFacing=" + actualFacing + " match=" + match);
    }

    private static void traceStationSignal(final Pending build, final BlockPos pos,
                                           final boolean expectedSignal) {
        final BlockState state = build.level.getBlockState(pos);
        final boolean powered = state.hasProperty(com.simibubi.create.content.logistics.funnel.FunnelBlock.POWERED)
                && state.getValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.POWERED);
        tell(build.observers, build.level, "[M37] VALIDATE kind=" + build.kind + " component=STATION_CONTROL pos="
                + pos.toShortString() + " neighborSignal=" + build.level.hasNeighborSignal(pos)
                + " currentPowered=" + powered + " expectedSignal=" + expectedSignal
                + " nativeScheduledUpdatePending=true match="
                + (build.level.hasNeighborSignal(pos) == expectedSignal));
    }

    private static void traceStationConnectivity(final Pending build, final BlockPos funnel,
                                                 final BlockPos chest, final Direction facing) {
        final BlockState state = build.level.getBlockState(funnel);
        final boolean match = funnel.relative(facing.getOpposite()).equals(chest)
                && build.level.getBlockState(chest).is(Blocks.CHEST);
        tell(build.observers, build.level, "[M37] VALIDATE kind=" + build.kind
                + " component=COLLECTOR_CONNECTIVITY pos=" + funnel.toShortString()
                + " chest=" + chest.toShortString() + " expectedFacing=" + facing
                + " actualFacing=" + (state.hasProperty(
                        com.simibubi.create.content.logistics.funnel.FunnelBlock.FACING)
                        ? state.getValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.FACING) : null)
                + " match=" + match);
    }

    private static int run(final CommandSourceStack source) {
        final Lookup found = lookup(source.getLevel());
        final Fixture fixture = found.fixture;
        if (found.status != Status.OUTER || fixture == null || !retracted(fixture)
                || CLEANUPS.containsKey(fixture.marker.getUUID()) || YAWS.containsKey(fixture.marker.getUUID())) {
            return refuse(source, "RUN REFUSED: one assembled, retracted M37 fixture is required");
        }
        final Run prior = RUNS.get(fixture.marker.getUUID());
        final Run run = new Run(fixture.marker.getUUID(), fixture.level, observers(source));
        if (prior != null) {
            run.yaw = prior.yaw;
            run.offset = prior.offset;
        }
        hold(fixture, run.yaw, run.offset);
        final CreativeMotorBlockEntity motor = motor(fixture);
        if (motor == null) {
            return refuse(source, "RUN REFUSED: fixture motor missing");
        }
        if (fixture.kind != Kind.ENTITY && (sourceCount(fixture) <= 0 || destinationCount(fixture) != 0)) {
            return refuse(source, "RUN REFUSED: expected finite source and empty destination");
        }
        if (fixture.kind == Kind.ENTITY && destinationCount(fixture) != 0) {
            return refuse(source, "RUN REFUSED: mounted destination is not empty");
        }
        if (fixture.kind == Kind.INSERT) {
            removeIf(fixture.level, stationControl(fixture), Blocks.REDSTONE_BLOCK);
        } else if (fixture.kind == Kind.EXTRACT) {
            removeIf(fixture.level, stationControl(fixture), Blocks.REDSTONE_BLOCK);
            run.waitingForStationItem = true;
        } else if (fixture.kind == Kind.ENTITY) {
            final BlockPos cell = fixture.station.blockPosition();
            if (!fixture.level.getEntitiesOfClass(ItemEntity.class, new AABB(cell),
                    entity -> entity.getTags().contains(ITEM_TAG)).isEmpty()) {
                return refuse(source, "RUN REFUSED: old tagged test item remains in target cell");
            }
            final ItemEntity item = new ItemEntity(fixture.level, cell.getX() + 0.5,
                    cell.getY() + 0.25, cell.getZ() + 0.5, new ItemStack(Items.COBBLESTONE, 16));
            item.addTag(ITEM_TAG);
            item.setDeltaMovement(Vec3.ZERO);
            item.setNoGravity(true);
            run.entityPresentBefore = fixture.level.addFreshEntity(item);
        }
        run.sourceBefore = sourceCount(fixture);
        run.destinationBefore = destinationCount(fixture);
        if (run.sourceBefore <= 0 || run.destinationBefore != 0) {
            return refuse(source, "RUN REFUSED: expected finite source and empty destination");
        }
        RUNS.put(run.markerId, run);
        motor.generatedSpeed.setValue(-64);
        if (fixture.kind != Kind.EXTRACT) {
            M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body, fixture.origin, false);
        }
        tell(run.observers, fixture.level, "[M37] " + fixture.kind
                + (fixture.kind == Kind.EXTRACT ? " RUN: waiting for native stationary emission sourceBefore="
                        : " RUN: native piston engaged sourceBefore=") + run.sourceBefore
                + " destinationBefore=" + run.destinationBefore + " yaw=" + Math.toDegrees(run.yaw));
        return 1;
    }

    private static int sourceCount(final Fixture fixture) {
        final boolean yawed = fixture.station.getTags().contains("sable_m37_yaw90");
        return switch (fixture.kind) {
            case INSERT -> mountedCount(fixture);
            case EXTRACT -> chestCount(fixture.level, null,
                    fixture.station.blockPosition().relative(yawed
                            ? rotateFacing(fixture.kind.chestDirection()) : fixture.kind.chestDirection()));
            case ENTITY -> fixture.level.getEntitiesOfClass(ItemEntity.class,
                    new AABB(fixture.station.blockPosition()).inflate(1),
                    entity -> entity.getTags().contains(ITEM_TAG)).stream()
                    .mapToInt(entity -> entity.getItem().getCount()).sum();
        };
    }

    private static int destinationCount(final Fixture fixture) {
        final boolean yawed = fixture.station.getTags().contains("sable_m37_yaw90");
        return fixture.kind == Kind.INSERT
                ? chestCount(fixture.level, null,
                        insertCollector(fixture.station.blockPosition(), yawed).below())
                : mountedCount(fixture);
    }

    private static int chestCount(final ServerLevel level, final ServerSubLevel body, final BlockPos pos) {
        final BlockEntity entity = M35FixtureLookup.blockEntity(level, body, pos);
        if (!(entity instanceof final Container container)) {
            return -1;
        }
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(Items.COBBLESTONE)) {
                total += container.getItem(slot).getCount();
            }
        }
        return total;
    }

    private static int mountedCount(final Fixture fixture) {
        final BlockEntity controller = M35FixtureLookup.blockEntity(fixture.level, fixture.body, fixture.origin);
        if (controller instanceof final MechanicalPistonBlockEntity piston
                && piston.movedContraption != null && piston.movedContraption.getContraption() != null) {
            final IItemHandler storage = piston.movedContraption.getContraption().getStorage().getAllItems();
            int count = 0;
            for (int slot = 0; slot < storage.getSlots(); slot++) {
                if (storage.getStackInSlot(slot).is(Items.COBBLESTONE)) {
                    count += storage.getStackInSlot(slot).getCount();
                }
            }
            return count;
        }
        final int offset = controller instanceof MechanicalPistonBlockEntity piston
                ? Math.round(piston.offset) : 0;
        return chestCount(fixture.level, fixture.body, fixture.origin.east(offset + 1).south());
    }

    private static boolean retracted(final Fixture fixture) {
        if (!(M35FixtureLookup.blockEntity(fixture.level, fixture.body, fixture.origin)
                instanceof final MechanicalPistonBlockEntity piston)
                || piston.running || piston.movedContraption != null
                || !M35FixtureLookup.blockState(fixture.level, fixture.body, fixture.origin)
                        .toString().contains("state=retracted")) {
            return false;
        }
        return M35FixtureLookup.blockState(fixture.level, fixture.body, fixture.origin.east())
                .is(AllBlocks.RADIAL_CHASSIS.get())
                && M35FixtureLookup.blockState(fixture.level, fixture.body,
                        legacyInsert(fixture) ? fixture.origin.east().above()
                                : movingFunnelPos(fixture.origin, fixture.kind))
                        .is(AllBlocks.ANDESITE_FUNNEL.get())
                && M35FixtureLookup.blockState(fixture.level, fixture.body, fixture.origin.east().south())
                        .is(Blocks.CHEST);
    }

    private static void hold(final Fixture fixture, final double yaw, final Vec3 offset) {
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(fixture.level);
        final RigidBodyHandle handle = physics == null ? null : physics.getPhysicsHandle(fixture.body);
        if (handle == null || !handle.isValid()) {
            throw new IllegalStateException("M37 fixture physics handle unavailable");
        }
        final Quaterniond rotation = new Quaterniond().rotationY(yaw);
        final Vec3 rawCenter = Vec3.atCenterOf(fixture.origin);
        final Vector3d local = new Vector3d(rawCenter.x, rawCenter.y, rawCenter.z)
                .sub(fixture.body.logicalPose().rotationPoint()).mul(fixture.body.logicalPose().scale());
        final Vec3 visibleAnchor = Vec3.atCenterOf(fixture.marker.blockPosition()).add(offset);
        final Vector3d target = new Vector3d(visibleAnchor.x, visibleAnchor.y, visibleAnchor.z)
                .sub(rotation.transform(local));
        handle.teleport(target, rotation);
        handle.setLinearAndAngularVelocity(new Vector3d(), new Vector3d());
        physics.updatePose(fixture.body);
        fixture.body.updateBoundingBox();
        fixture.body.updateLastPose();
    }

    private static Lookup lookup(final ServerLevel level) {
        final List<ArmorStand> origins = markers(level, ORIGIN_TAG);
        final List<ArmorStand> stations = markers(level, STATION_TAG);
        if (origins.isEmpty() && stations.isEmpty()) {
            return new Lookup(Status.NONE, null);
        }
        if (origins.size() != 1 || stations.size() != 1) {
            return new Lookup(Status.AMBIGUOUS, null);
        }
        final ArmorStand marker = origins.get(0);
        final Kind kind = Kind.fromMarker(marker);
        if (kind == null) {
            return new Lookup(Status.CORRUPT, null);
        }
        final BlockPos origin = marker.blockPosition();
        final ArmorStand station = stations.get(0);
        if (M35FixtureLookup.blockState(level, null, origin).is(AllBlocks.STICKY_MECHANICAL_PISTON.get())) {
            return new Lookup(Status.STATIC, new Fixture(level, marker, station, origin, null, kind));
        }
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        Fixture found = null;
        if (container != null) {
            for (final ServerSubLevel body : container.getAllSubLevels()) {
                for (final BlockPos candidate : SimAssemblyHelper.collectBlocks(level, body)) {
                    if (M35FixtureLookup.blockState(level, body, candidate)
                            .is(AllBlocks.STICKY_MECHANICAL_PISTON.get())
                            && M35FixtureLookup.blockState(level, body, candidate.north(2))
                                    .is(Blocks.IRON_BLOCK)
                            && M35FixtureLookup.blockEntity(level, body, candidate.above().north(2))
                                    instanceof PhysicsAssemblerBlockEntity) {
                        if (found != null) {
                            return new Lookup(Status.AMBIGUOUS, null);
                        }
                        found = new Fixture(level, marker, station, candidate, body, kind);
                    }
                }
            }
        }
        if (found != null) {
            return new Lookup(Status.OUTER, found);
        }
        final Fixture remainder = new Fixture(level, marker, station, origin, null, kind);
        return new Lookup(knownFootprint(remainder) ? Status.PARTIAL : Status.CORRUPT, remainder);
    }

    private static boolean knownFootprint(final Fixture fixture) {
        final ServerLevel level = fixture.level;
        final BlockPos origin = fixture.origin;
        for (int x = -12; x <= -1; x++) {
            if (!expectedOrAir(level, origin.offset(x, 0, 0), AllBlocks.PISTON_EXTENSION_POLE.get())) {
                return false;
            }
        }
        return expectedOrAir(level, origin, AllBlocks.STICKY_MECHANICAL_PISTON.get())
                && expectedOrAir(level, origin.east(), AllBlocks.RADIAL_CHASSIS.get())
                && expectedOrAir(level, legacyInsert(fixture) ? origin.east().above()
                        : movingFunnelPos(origin, fixture.kind), AllBlocks.ANDESITE_FUNNEL.get())
                && expectedOrAir(level, origin.east().south(), Blocks.CHEST)
                && expectedOrAir(level, origin.below(2), AllBlocks.CREATIVE_MOTOR.get())
                && expectedOrAir(level, origin.below(), AllBlocks.CLUTCH.get())
                && expectedOrAir(level, origin.below().south(), Blocks.LEVER)
                && expectedOrAir(level, origin.north(), Blocks.IRON_BLOCK)
                && expectedOrAir(level, origin.north(2), Blocks.IRON_BLOCK)
                && expectedOrAir(level, origin.above().north(2),
                        dev.simulated_team.simulated.index.SimulatedBlocks.PHYSICS_ASSEMBLER.get())
                && stationMatchesOrAir(fixture);
    }

    private static boolean stationMatchesOrAir(final Fixture fixture) {
        final BlockPos station = fixture.station.blockPosition();
        final boolean yawed = fixture.station.getTags().contains("sable_m37_yaw90");
        if (fixture.kind == Kind.ENTITY) {
            return expectedOrAir(fixture.level, station.below(), Blocks.STONE)
                    && fixture.level.getBlockState(station).isAir();
        }
        if (fixture.kind == Kind.INSERT) {
            if (legacyInsert(fixture)) {
                return expectedOrAir(fixture.level, station, Blocks.STONE)
                        && expectedOrAir(fixture.level, station.below(2), AllBlocks.ANDESITE_FUNNEL.get())
                        && expectedOrAir(fixture.level, station.below(3), Blocks.CHEST)
                        && expectedOrAir(fixture.level, station.below(2).south(), Blocks.REDSTONE_BLOCK);
            }
            final BlockPos collector = insertCollector(station, yawed);
            return fixture.level.getBlockState(station).isAir()
                    && expectedOrAir(fixture.level, collector, AllBlocks.ANDESITE_FUNNEL.get())
                    && expectedOrAir(fixture.level, collector.below(), Blocks.CHEST)
                    && expectedOrAir(fixture.level, stationControl(fixture), Blocks.REDSTONE_BLOCK);
        }
        final Direction behind = yawed ? rotateFacing(fixture.kind.chestDirection())
                : fixture.kind.chestDirection();
        final BlockPos chest = station.relative(behind);
        return expectedOrAir(fixture.level, station, AllBlocks.ANDESITE_FUNNEL.get())
                && expectedOrAir(fixture.level, chest, Blocks.CHEST)
                && expectedOrAir(fixture.level, yawed ? station.east() : station.south(), Blocks.REDSTONE_BLOCK)
                && (fixture.kind != Kind.EXTRACT
                        || expectedOrAir(fixture.level, station.below(), Blocks.STONE));
    }

    private static boolean expectedOrAir(final ServerLevel level, final BlockPos pos, final Block expected) {
        final BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(expected);
    }

    private static int disconnect(final CommandSourceStack source) {
        final Lookup found = lookup(source.getLevel());
        if (found.status != Status.OUTER || found.fixture == null) {
            return refuse(source, "DISCONNECT REFUSED: assembled M37 fixture required");
        }
        final Fixture fixture = found.fixture;
        final Run run = RUNS.get(fixture.marker.getUUID());
        if (run == null || CLEANUPS.containsKey(run.markerId) || YAWS.containsKey(run.markerId)) {
            return refuse(source, "DISCONNECT REFUSED: start the fixture run first");
        }
        if (!powerStation(fixture)) {
            return refuse(source, "DISCONNECT REFUSED: stationary control cell is not fixture-owned");
        }
        M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body, fixture.origin, true);
        run.offset = run.offset.add(0, 0, 8);
        hold(fixture, run.yaw, run.offset);
        tell(run.observers, fixture.level, "[M37] DISCONNECT: fixture moved beyond its former target cell");
        return 1;
    }

    private static int yaw90(final CommandSourceStack source) {
        final Lookup found = lookup(source.getLevel());
        if (found.status != Status.OUTER || found.fixture == null) {
            return refuse(source, "YAW90 REFUSED: assembled M37 fixture required");
        }
        final Fixture fixture = found.fixture;
        final Run run = RUNS.get(fixture.marker.getUUID());
        if (run == null || run.yaw != 0 || YAWS.containsKey(run.markerId)
                || CLEANUPS.containsKey(run.markerId)) {
            return refuse(source, "YAW90 REFUSED: run the original level fixture first");
        }
        final BlockPos destination = fixture.marker.blockPosition()
                .offset(rotate90(fixture.kind.stationOffset()));
        for (final BlockPos cell : stationCells(fixture.kind, destination, true)) {
            if (!fixture.level.hasChunkAt(cell) || !fixture.level.getBlockState(cell).isAir()) {
                return refuse(source, "YAW90 REFUSED: unrelated block at " + cell.toShortString());
            }
        }
        YAWS.put(run.markerId, new Yaw(run.markerId, fixture.level, observers(source)));
        RUNS.remove(run.markerId);
        tell(observers(source), fixture.level, "[M37] YAW90 phase=RETRACT target=" + destination.toShortString());
        return 1;
    }

    private static BlockPos rotate90(final BlockPos local) {
        return new BlockPos(local.getZ(), local.getY(), -local.getX());
    }

    private static List<BlockPos> stationCells(final Kind kind, final BlockPos station, final boolean yawed) {
        if (kind == Kind.ENTITY) {
            return List.of(station.below());
        }
        if (kind == Kind.INSERT) {
            final BlockPos collector = insertCollector(station, yawed);
            return List.of(collector, collector.below(),
                    yawed ? collector.east() : collector.south());
        }
        final Direction behind = yawed ? kind.chestDirection().getCounterClockWise() : kind.chestDirection();
        return kind == Kind.EXTRACT
                ? List.of(station, station.relative(behind), station.below(),
                        yawed ? station.relative(Direction.EAST) : station.south())
                : List.of(station, station.relative(behind),
                        yawed ? station.relative(Direction.EAST) : station.south());
    }

    private static void serverStopped(final ServerStoppedEvent event) {
        pending = null;
        RUNS.clear();
        CLEANUPS.clear();
        YAWS.clear();
    }

    private static void tick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (pending != null && pending.level.getServer() == event.getServer() && ++pending.age > 5) {
            tell(pending.observers, pending.level, "[M37] SETUP FAILED: validation did not run");
            removeStatic(pending.level, pending.origin, pending.kind,
                    pending.origin.offset(pending.kind.stationOffset()));
            pending = null;
        }
        for (final Run run : List.copyOf(RUNS.values())) {
            if (run.level.getServer() == event.getServer()) {
                advance(run);
            }
        }
        for (final Yaw yaw : List.copyOf(YAWS.values())) {
            if (yaw.level.getServer() == event.getServer()) {
                advance(yaw);
            }
        }
        for (final Cleanup cleanup : List.copyOf(CLEANUPS.values())) {
            if (cleanup.level.getServer() == event.getServer()) {
                advance(cleanup);
            }
        }
    }

    private static void entityJoined(final EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof final ServerLevel level)
                || !(event.getEntity() instanceof final ItemEntity item)
                || !item.getItem().is(Items.COBBLESTONE)) {
            return;
        }
        for (final Run run : RUNS.values()) {
            if (run.level != level) {
                continue;
            }
            final Lookup found = lookup(level);
            if (found.fixture == null || found.fixture.kind != Kind.EXTRACT
                    || !found.fixture.marker.getUUID().equals(run.markerId)
                    || !new AABB(found.fixture.station.blockPosition()).intersects(item.getBoundingBox())) {
                continue;
            }
            run.stationarySpawnCount++;
            if (run.stationarySpawnCount != 1) {
                tell(run.observers, level, "[M37] EXTRACT_TIMING event=UNEXPECTED_SECOND_STATIONARY_ITEM"
                        + " gameTime=" + level.getGameTime() + " itemUuid=" + item.getUUID());
                continue;
            }
            run.stationaryItemSpawnTime = level.getGameTime();
            run.emittedItemId = item.getUUID();
            run.itemObservedAt = run.age;
            item.addTag(ITEM_TAG);
            run.stationControlSecured = powerStation(found.fixture);
            tell(run.observers, level, "[M37] EXTRACT_TIMING event=STATIONARY_ITEM_SPAWN sequence="
                    + ++run.eventSequence + " gameTime=" + run.stationaryItemSpawnTime
                    + " itemUuid=" + item.getUUID() + " pos=" + item.position()
                    + " stationaryFunnelDisabled=" + run.stationControlSecured);
        }
    }

    public static void observeNativeSucc(final com.simibubi.create.content.contraptions.behaviour.MovementContext context,
                                         final BlockPos queryCell) {
        if (RUNS.isEmpty() || !(context.world instanceof final ServerLevel level)) {
            return;
        }
        for (final Run run : RUNS.values()) {
            if (run.level != level || run.nativeQueryObserved) {
                continue;
            }
            final Lookup found = lookup(level);
            if (found.fixture == null || found.fixture.kind != Kind.EXTRACT
                    || !found.fixture.marker.getUUID().equals(run.markerId)
                    || !queryCell.equals(found.fixture.station.blockPosition())) {
                continue;
            }
            final BlockEntity controller = M35FixtureLookup.blockEntity(level, found.fixture.body,
                    found.fixture.origin);
            if (!(controller instanceof final MechanicalPistonBlockEntity piston)
                    || piston.movedContraption == null
                    || piston.movedContraption.getContraption() != context.contraption) {
                continue;
            }
            final int candidates = level.getEntitiesOfClass(ItemEntity.class, new AABB(queryCell)).size();
            run.nativeQueryObserved = true;
            run.nativeQueryTime = level.getGameTime();
            run.itemExistedBeforeNativeQuery = M37FixtureGeometryObservation.stationaryItemPrecedesQuery(
                    run.stationaryItemSpawnTime, run.nativeQueryTime, candidates);
            tell(run.observers, level, "[M37] EXTRACT_TIMING event=MOVING_SUCC_QUERY sequence="
                    + ++run.eventSequence + " gameTime=" + run.nativeQueryTime
                    + " activeCell=" + queryCell.toShortString() + " queryAABB=" + new AABB(queryCell)
                    + " candidateCount=" + candidates + " itemExistedBefore="
                    + run.itemExistedBeforeNativeQuery);
        }
    }

    private static void postPhysicsTick(final ForgeSablePostPhysicsTickEvent event) {
        for (final Run run : RUNS.values()) {
            if (run.level == event.getPhysicsSystem().getLevel()) {
                final Lookup found = lookup(run.level);
                if (found.status == Status.OUTER && found.fixture.marker.getUUID().equals(run.markerId)) {
                    hold(found.fixture, run.yaw, run.offset);
                }
            }
        }
        for (final Yaw yaw : YAWS.values()) {
            if (yaw.level == event.getPhysicsSystem().getLevel()) {
                final Lookup found = lookup(yaw.level);
                if (found.status == Status.OUTER && found.fixture.marker.getUUID().equals(yaw.markerId)) {
                    hold(found.fixture, 0, Vec3.ZERO);
                }
            }
        }
        for (final Cleanup cleanup : CLEANUPS.values()) {
            if (cleanup.level == event.getPhysicsSystem().getLevel()) {
                final Lookup found = lookup(cleanup.level);
                if (found.status == Status.OUTER && found.fixture.marker.getUUID().equals(cleanup.markerId)) {
                    hold(found.fixture, 0, Vec3.ZERO);
                }
            }
        }
    }

    private static void advance(final Run run) {
        run.age++;
        final Lookup found = lookup(run.level);
        if (found.status != Status.OUTER || found.fixture == null
                || !found.fixture.marker.getUUID().equals(run.markerId)) {
            RUNS.remove(run.markerId);
            tell(run.observers, run.level, "[M37] RUN FAILED: tagged outer Sable disappeared");
            return;
        }
        final Fixture fixture = found.fixture;
        final BlockEntity controller = M35FixtureLookup.blockEntity(fixture.level, fixture.body, fixture.origin);
        if (controller instanceof final MechanicalPistonBlockEntity piston
                && piston.movedContraption != null && piston.movedContraption.getContraption() != null) {
            if (!run.payloadReported) {
                final var contraption = piston.movedContraption.getContraption();
                final long funnelCount = contraption.getBlocks().values().stream()
                        .filter(info -> info.state().is(AllBlocks.ANDESITE_FUNNEL.get())).count();
                final long chestCount = contraption.getBlocks().values().stream()
                        .filter(info -> info.state().is(Blocks.CHEST)).count();
                final long chestBECount = contraption.getBlocks().values().stream()
                        .filter(info -> info.state().is(Blocks.CHEST) && info.nbt() != null).count();
                final var mounted = contraption.getStorage().getAllItemStorages();
                tell(run.observers, fixture.level, "[M37] MOVING_PAYLOAD capturedBlockCount="
                        + contraption.getBlocks().size() + " capturedFunnelCount=" + funnelCount
                        + " capturedChestCount=" + chestCount + " capturedChestBECount=" + chestBECount
                        + " mountedStorageCount=" + mounted.size() + " mountedPositions="
                        + mounted.keySet());
                run.payloadReported = true;
            }
            for (final var actor : piston.movedContraption.getContraption().getActors()) {
                if (!actor.getLeft().state().is(AllBlocks.ANDESITE_FUNNEL.get())) {
                    continue;
                }
                run.actorObserved = true;
                final var actorPose = M37FixtureGeometryObservation.captureActorPose(
                        fixture.body.logicalPose(), actor.getRight().position,
                        fixture.kind == Kind.INSERT ? new Vec3(0, 0.65, 0)
                                : new Vec3(0.65, 0, 0));
                if (actorPose.isEmpty()) {
                    updateGeometryPhase(run, fixture, true, false, false);
                    continue;
                }
                run.lastActivePoint = actorPose.get().visibleActivePoint();
                run.lastMovingFunnelBlock = actorPose.get().visibleFunnelBlock();
                updateGeometryPhase(run, fixture, true, true, false);
                final SableMovingFunnelTarget.Result target =
                        SableMovingFunnelTarget.resolve(actor.getRight(), fixture.body);
                final BlockPos expected = fixture.station.blockPosition();
                run.targetResolved |= target.accepted() && expected.equals(target.parentBlock());
                if (fixture.kind == Kind.EXTRACT && run.targetResolved && !run.closestReported) {
                    final ItemEntity item = run.emittedItemId == null ? null
                            : fixture.level.getEntity(run.emittedItemId) instanceof ItemEntity foundItem
                                    ? foundItem : null;
                    tell(run.observers, fixture.level, "[M37] EXTRACT_GEOMETRY stationaryFunnelBlock="
                            + expected.toShortString() + " emittedItemPosition="
                            + (item == null ? "absent" : item.position()) + " emittedItemAABB="
                            + (item == null ? "absent" : item.getBoundingBox()) + " movingFunnelBlock="
                            + run.lastMovingFunnelBlock + " movingActivePosition=" + run.lastActivePoint
                            + " movingQueryAABB=" + new AABB(expected) + " intersectsQuery="
                            + (item != null && item.getBoundingBox().intersects(new AABB(expected))));
                    run.closestReported = true;
                }
                run.hiddenPlotInteraction |= target.accepted()
                        && Sable.HELPER.getContaining(fixture.level, target.parentBlock()) != null;
            }
        } else {
            updateGeometryPhase(run, fixture, false, false, false);
        }
        if (run.waitingForStationItem) {
            final BlockPos station = fixture.station.blockPosition();
            final List<ItemEntity> staged = fixture.level.getEntitiesOfClass(ItemEntity.class,
                    new AABB(station), item -> item.getTags().contains(ITEM_TAG)
                            && item.getItem().is(Items.COBBLESTONE)
                            && item.getY() <= station.getY() + 0.05);
            if (run.stationarySpawnCount > 1 || !run.stationControlSecured) {
                run.reported = true;
                run.waitingForStationItem = false;
                tell(run.observers, fixture.level, "[M37] RUN FAILED reason=STATIONARY_OUTPUT_NOT_SINGLE_ITEM"
                        + "; /function sable:m37/cleanup remains available");
            } else if (staged.size() == 1 && run.stationaryItemSpawnTime >= 0
                    && fixture.level.getBlockState(station).getValue(
                            com.simibubi.create.content.logistics.funnel.FunnelBlock.POWERED)) {
                run.waitingForStationItem = false;
                M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body,
                        fixture.origin, false);
                tell(run.observers, fixture.level,
                        "[M37] EXTRACT sourceItemStaged=true nativePistonEngaged=true item="
                                + staged.get(0).getUUID() + " gameTime=" + fixture.level.getGameTime());
            }
        }
        final int sourceAfter = sourceCount(fixture);
        final int destinationAfter = destinationCount(fixture);
        final boolean hidden = run.hiddenPlotInteraction;
        run.movingExtractObserved |= fixture.kind == Kind.INSERT && sourceAfter >= 0
                && sourceAfter < run.sourceBefore;
        run.movingInsertObserved |= fixture.kind != Kind.INSERT && destinationAfter > run.destinationBefore;
        if (fixture.kind == Kind.EXTRACT && run.movingInsertObserved && !run.nativeInsertLogged) {
            run.nativeInsertLogged = true;
            tell(run.observers, fixture.level, "[M37] EXTRACT_TIMING event=MOVING_INSERT_SUCCESS sequence="
                    + ++run.eventSequence + " gameTime=" + fixture.level.getGameTime()
                    + " destinationAfter=" + destinationAfter);
        }
        observeItemGeometry(run, fixture);
        final M37FunnelRunAcceptance.Result acceptance = M37FunnelRunAcceptance.evaluate(
                fixture.kind == Kind.ENTITY, fixture.kind == Kind.INSERT,
                run.actorObserved, run.targetResolved, run.entityPresentBefore,
                run.stationarySpawnCount == 1 && run.stationControlSecured,
                run.itemExistedBeforeNativeQuery,
                run.movingExtractObserved, run.movingInsertObserved, run.sourceBefore,
                sourceAfter, run.destinationBefore, destinationAfter, hidden);
        final boolean endToEnd = acceptance.endToEnd();
        if (!run.reported && run.age >= 400 && run.waitingForStationItem) {
            run.reported = true;
            tell(run.observers, fixture.level,
                    "[M37] RUN FAILED reason=STATIONARY_ITEM_NOT_STAGED; /function sable:m37/cleanup remains available");
            return;
        }
        if (!run.reported && M37FixtureGeometryObservation.actorPositionUnavailable(
                run.lastActivePoint, run.age)) {
            run.reported = true;
            M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body, fixture.origin, true);
            tell(run.observers, fixture.level, "[M37] RUN FAILED reason="
                    + (run.payloadReported ? "ACTOR_POSITION_UNAVAILABLE"
                            : "MOVING_CONTRAPTION_UNAVAILABLE")
                    + "; /function sable:m37/cleanup remains available");
            return;
        }
        if (!run.reported && (endToEnd || run.age >= 400)) {
            run.reported = true;
            M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body, fixture.origin, true);
            tell(run.observers, fixture.level, "[M37] " + fixture.kind + " funnelActor=" + run.actorObserved
                    + (fixture.kind == Kind.ENTITY ? " entityQueryResolved="
                            : fixture.kind == Kind.EXTRACT ? " itemQueryResolved=" : " targetResolved=")
                    + acceptance.targetResolved()
                    + " movingExtractObserved=" + run.movingExtractObserved
                    + " movingInsertObserved=" + run.movingInsertObserved
                    + (fixture.kind == Kind.EXTRACT
                            ? " itemExistedBeforeNativeQuery=" + run.itemExistedBeforeNativeQuery : "")
                    + " nativeTransfer=" + acceptance.nativeTransfer()
                    + " endToEndTransfer=" + endToEnd
                    + " sourceBefore=" + run.sourceBefore + " sourceAfter=" + sourceAfter
                    + " destinationBefore=" + run.destinationBefore + " destinationAfter=" + destinationAfter
                    + " hiddenPlotInteraction=" + hidden + " " + (endToEnd ? "PASS" : "INCOMPLETE"));
        }
    }

    private static void observeItemGeometry(final Run run, final Fixture fixture) {
        if (fixture.kind == Kind.ENTITY || run.emittedItemId != null) {
            if (run.emittedItemId != null && !run.itemFollowupReported && run.age >= run.itemObservedAt + 8) {
                final Entity entity = fixture.level.getEntity(run.emittedItemId);
                tell(run.observers, fixture.level, "[M37] " + fixture.kind + "_GEOMETRY followupItemPosition="
                        + (entity == null ? "consumed/absent" : entity.position()) + " age=" + run.age);
                run.itemFollowupReported = true;
            }
            return;
        }
        if (run.lastActivePoint == null) {
            return;
        }
        final BlockPos station = fixture.station.blockPosition();
        final AABB search = fixture.kind == Kind.INSERT
                ? new AABB(station.getX(), station.getY() - 4, station.getZ(),
                        station.getX() + 1, station.getY() + 1, station.getZ() + 1)
                : new AABB(station).inflate(3);
        final List<ItemEntity> items = fixture.level.getEntitiesOfClass(ItemEntity.class,
                search, item -> item.getItem().is(Items.COBBLESTONE));
        if (items.isEmpty()) {
            updateGeometryPhase(run, fixture, true, true, false);
            return;
        }
        final ItemEntity item = items.get(0);
        updateGeometryPhase(run, fixture, true, true, true);
        run.emittedItemId = item.getUUID();
        run.itemObservedAt = run.age;
        final BlockPos intake = fixture.kind == Kind.INSERT
                ? insertCollector(station, fixture.station.getTags().contains("sable_m37_yaw90")) : station;
        final AABB intakeBounds = new AABB(intake);
        tell(run.observers, fixture.level, fixture.kind == Kind.INSERT
                ? "[M37] INSERT_GEOMETRY movingFunnelBlock=" + run.lastMovingFunnelBlock
                        + " movingActivePosition=" + run.lastActivePoint
                        + " emittedItemPosition=" + item.position()
                        + " emittedItemAABB=" + item.getBoundingBox() + " stationaryFunnelBlock="
                        + intake.toShortString() + " stationaryIntakeAABB=" + intakeBounds
                        + " intersectsIntake=" + item.getBoundingBox().intersects(intakeBounds)
                        + " delta=" + item.position().subtract(Vec3.atCenterOf(intake))
                        + " collectorPowered=" + (fixture.level.getBlockState(intake).hasProperty(
                                com.simibubi.create.content.logistics.funnel.FunnelBlock.POWERED)
                                ? fixture.level.getBlockState(intake).getValue(
                                        com.simibubi.create.content.logistics.funnel.FunnelBlock.POWERED)
                                : "collectorMissing")
                : "[M37] EXTRACT_GEOMETRY stationaryFunnelBlock=" + station.toShortString()
                        + " emittedItemPosition=" + item.position() + " emittedItemAABB="
                        + item.getBoundingBox() + " movingFunnelBlock=" + run.lastMovingFunnelBlock
                        + " movingActivePosition=" + run.lastActivePoint
                        + " movingQueryAABB=" + new AABB(station)
                        + " intersectsQuery=" + item.getBoundingBox().intersects(new AABB(station))
                        + " delta=" + item.position().subtract(Vec3.atCenterOf(station)));
    }

    private static void updateGeometryPhase(final Run run, final Fixture fixture,
                                            final boolean movingContraptionPresent,
                                            final boolean actorPositionPresent,
                                            final boolean relevantItemPresent) {
        final M37FixtureGeometryObservation.Phase next = M37FixtureGeometryObservation.nextPhase(
                run.geometryPhase, movingContraptionPresent, actorPositionPresent, relevantItemPresent);
        if (next == run.geometryPhase) {
            return;
        }
        run.geometryPhase = next;
        if (next != M37FixtureGeometryObservation.Phase.CAPTURED) {
            tell(run.observers, fixture.level, "[M37] GEOMETRY pending reason=" + switch (next) {
                case WAIT_FOR_MOVING_CONTRAPTION -> "NO_MOVING_CONTRAPTION_YET";
                case WAIT_FOR_ACTOR_POSITION -> "NO_ACTOR_POSITION_YET";
                case WAIT_FOR_RELEVANT_ITEM_ENTITY -> "NO_EMITTED_ITEM_YET";
                case CAPTURED -> "CAPTURED";
            });
        }
    }

    private static void advance(final Yaw yaw) {
        final Lookup found = lookup(yaw.level);
        if (found.status != Status.OUTER || found.fixture == null
                || !found.fixture.marker.getUUID().equals(yaw.markerId)) {
            YAWS.remove(yaw.markerId);
            tell(yaw.observers, yaw.level, "[M37] YAW90 FAILED: tagged outer Sable disappeared");
            return;
        }
        final Fixture fixture = found.fixture;
        if (++yaw.age > TIMEOUT_TICKS) {
            YAWS.remove(yaw.markerId);
            tell(yaw.observers, yaw.level, "[M37] YAW90 FAILED: native piston retract timed out; retry cleanup");
            return;
        }
        if (!yaw.reversing) {
            if (!powerStation(fixture)) {
                YAWS.remove(yaw.markerId);
                tell(yaw.observers, yaw.level, "[M37] YAW90 FAILED: stationary control cell is not fixture-owned");
                return;
            }
            M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body, fixture.origin, true);
            final CreativeMotorBlockEntity motor = motor(fixture);
            if (motor == null) {
                YAWS.remove(yaw.markerId);
                tell(yaw.observers, yaw.level, "[M37] YAW90 FAILED: fixture motor absent");
                return;
            }
            motor.generatedSpeed.setValue(64);
            M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body, fixture.origin, false);
            yaw.reversing = true;
            return;
        }
        if (!retracted(fixture)) {
            return;
        }
        M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body, fixture.origin, true);
        final BlockPos old = fixture.station.blockPosition();
        final BlockPos next = fixture.marker.blockPosition().offset(rotate90(fixture.kind.stationOffset()));
        if (!stationStructuralMatch(fixture, old, false)) {
            YAWS.remove(yaw.markerId);
            tell(yaw.observers, yaw.level, "[M37] YAW90 FAILED: old station fingerprint changed");
            return;
        }
        for (final BlockPos cell : stationCells(fixture.kind, next, true)) {
            if (!fixture.level.hasChunkAt(cell) || !fixture.level.getBlockState(cell).isAir()) {
                YAWS.remove(yaw.markerId);
                tell(yaw.observers, yaw.level, "[M37] YAW90 FAILED: unrelated target cell " + cell.toShortString());
                return;
            }
        }
        final int oldMounted = mountedCount(fixture);
        final int oldParent = fixture.kind == Kind.ENTITY ? 0
                : chestCount(fixture.level, null, fixture.kind == Kind.INSERT
                        ? insertCollector(old, false).below()
                        : old.relative(fixture.kind.chestDirection()));
        try {
            removeStation(fixture.level, fixture.kind, old, false);
            hold(fixture, Math.PI / 2, Vec3.ZERO);
            buildStation(fixture.level, fixture.kind, next, true);
            fixture.station.setPos(next.getX(), next.getY(), next.getZ());
            fixture.station.addTag("sable_m37_yaw90");
            setChest(fixture.level, fixture.body, fixture.origin.east().south(),
                    fixture.kind == Kind.INSERT ? 16 : 0);
            if (fixture.kind != Kind.ENTITY) {
                setChest(fixture.level, null, fixture.kind == Kind.INSERT
                        ? insertCollector(next, true).below()
                        : next.relative(rotateFacing(fixture.kind.chestDirection())),
                        fixture.kind == Kind.EXTRACT ? 16 : 0);
            }
            final Run nextRun = new Run(yaw.markerId, yaw.level, yaw.observers);
            nextRun.yaw = Math.PI / 2;
            RUNS.put(nextRun.markerId, nextRun);
            YAWS.remove(yaw.markerId);
            tell(yaw.observers, yaw.level, "[M37] YAW90 COMPLETE station=" + next.toShortString()
                    + " parentFacing=" + rotateFacing(fixture.kind.movingFacing())
                    + " source=" + (fixture.kind == Kind.INSERT ? 16 : 16) + " destination=0");
        } catch (final RuntimeException exception) {
            Sable.LOGGER.error("SABLE_M37_YAW_ROLLBACK", exception);
            removeStation(fixture.level, fixture.kind, next, true);
            hold(fixture, 0, Vec3.ZERO);
            buildStation(fixture.level, fixture.kind, old, false);
            fixture.station.setPos(old.getX(), old.getY(), old.getZ());
            fixture.station.removeTag("sable_m37_yaw90");
            setChest(fixture.level, fixture.body, fixture.origin.east().south(), oldMounted);
            if (fixture.kind != Kind.ENTITY) {
                setChest(fixture.level, null, fixture.kind == Kind.INSERT
                        ? insertCollector(old, false).below()
                        : old.relative(fixture.kind.chestDirection()), oldParent);
            }
            YAWS.remove(yaw.markerId);
            tell(yaw.observers, yaw.level, "[M37] YAW90 ROLLBACK: " + exception.getMessage());
        }
    }

    private static boolean stationStructuralMatch(final Fixture fixture, final BlockPos station,
                                                  final boolean yawed) {
        if (fixture.kind == Kind.ENTITY) {
            return fixture.level.getBlockState(station.below()).is(Blocks.STONE);
        }
        if (fixture.kind == Kind.INSERT) {
            final BlockPos collector = insertCollector(station, yawed);
            return fixture.level.getBlockState(station).isAir()
                    && fixture.level.getBlockState(collector).is(AllBlocks.ANDESITE_FUNNEL.get())
                    && fixture.level.getBlockState(collector.below()).is(Blocks.CHEST);
        }
        return fixture.level.getBlockState(station).is(AllBlocks.ANDESITE_FUNNEL.get())
                && fixture.level.getBlockState(station.relative(yawed
                        ? rotateFacing(fixture.kind.chestDirection()) : fixture.kind.chestDirection()))
                        .is(Blocks.CHEST)
                && (fixture.kind != Kind.EXTRACT
                        || fixture.level.getBlockState(station.below()).is(Blocks.STONE));
    }

    private static Direction rotateFacing(final Direction facing) {
        return facing.getAxis().isVertical() ? facing : facing.getCounterClockWise();
    }

    private static void setChest(final ServerLevel level, final ServerSubLevel body,
                                 final BlockPos pos, final int count) {
        final BlockEntity entity = M35FixtureLookup.blockEntity(level, body, pos);
        if (!(entity instanceof final Container chest)) {
            throw new IllegalStateException("fixture chest missing at " + pos.toShortString());
        }
        chest.clearContent();
        if (count > 0) {
            chest.setItem(0, new ItemStack(Items.COBBLESTONE, count));
        }
        chest.setChanged();
    }

    private static void buildStation(final ServerLevel level, final Kind kind, final BlockPos station,
                                     final boolean yawed) {
        if (kind == Kind.ENTITY) {
            level.setBlock(station.below(), Blocks.STONE.defaultBlockState(), 3);
            return;
        }
        if (kind == Kind.INSERT) {
            final BlockPos collector = insertCollector(station, yawed);
            level.setBlock(collector, AllBlocks.ANDESITE_FUNNEL.get().defaultBlockState()
                    .setValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.FACING, Direction.UP)
                    .setValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.EXTRACTING, false), 3);
            level.setBlock(collector.below(), Blocks.CHEST.defaultBlockState(), 3);
            return;
        }
        final Direction facing = yawed ? rotateFacing(kind.stationFacing()) : kind.stationFacing();
        final BlockState funnel = AllBlocks.ANDESITE_FUNNEL.get().defaultBlockState()
                .setValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.FACING, facing)
                .setValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.EXTRACTING,
                        kind == Kind.EXTRACT);
        level.setBlock(station, funnel, 3);
        final Direction behind = yawed ? rotateFacing(kind.chestDirection()) : kind.chestDirection();
        level.setBlock(station.relative(behind), Blocks.CHEST.defaultBlockState(), 3);
        if (kind == Kind.EXTRACT) {
            level.setBlock(station.below(), Blocks.STONE.defaultBlockState(), 3);
        }
        level.setBlock(yawed ? station.east() : station.south(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
    }

    private static void removeStation(final ServerLevel level, final Kind kind,
                                      final BlockPos station, final boolean yawed) {
        if (kind == Kind.ENTITY) {
            removeIf(level, station.below(), Blocks.STONE);
            for (final ItemEntity item : new ArrayList<>(level.getEntitiesOfClass(ItemEntity.class,
                    new AABB(station).inflate(1), entity -> entity.getTags().contains(ITEM_TAG)))) {
                item.discard();
            }
        } else if (kind == Kind.INSERT) {
            final BlockPos collector = insertCollector(station, yawed);
            removeIf(level, collector, AllBlocks.ANDESITE_FUNNEL.get());
            removeIf(level, collector.below(), Blocks.CHEST);
            removeIf(level, yawed ? collector.east() : collector.south(),
                    Blocks.REDSTONE_BLOCK);
        } else {
            removeIf(level, station, AllBlocks.ANDESITE_FUNNEL.get());
            removeIf(level, station.relative(yawed ? rotateFacing(kind.chestDirection())
                    : kind.chestDirection()), Blocks.CHEST);
            removeIf(level, yawed ? station.east() : station.south(), Blocks.REDSTONE_BLOCK);
            if (kind == Kind.EXTRACT) {
                removeIf(level, station.below(), Blocks.STONE);
            }
        }
    }

    private static CreativeMotorBlockEntity motor(final Fixture fixture) {
        final BlockEntity entity = M35FixtureLookup.blockEntity(fixture.level, fixture.body,
                fixture.origin.below(2));
        return entity instanceof final CreativeMotorBlockEntity motor ? motor : null;
    }

    private static boolean powerStation(final Fixture fixture) {
        if (fixture.kind == Kind.ENTITY) {
            return true;
        }
        final BlockPos control = stationControl(fixture);
        final BlockState state = fixture.level.getBlockState(control);
        if (!state.isAir() && !state.is(Blocks.REDSTONE_BLOCK)) {
            return false;
        }
        if (state.isAir()) {
            fixture.level.setBlock(control, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        }
        return true;
    }

    private static BlockPos stationControl(final Fixture fixture) {
        if (legacyInsert(fixture)) {
            return fixture.station.blockPosition().below(2).south();
        }
        final BlockPos base = fixture.kind == Kind.INSERT
                ? insertCollector(fixture.station.blockPosition(),
                        fixture.station.getTags().contains("sable_m37_yaw90"))
                : fixture.station.blockPosition();
        return fixture.station.getTags().contains("sable_m37_yaw90") ? base.east() : base.south();
    }

    private static BlockPos insertCollector(final BlockPos station, final boolean yawed) {
        return station.below(4);
    }

    private static BlockPos movingFunnelPos(final BlockPos origin, final Kind kind) {
        return origin.east().above();
    }

    private static boolean legacyInsert(final Fixture fixture) {
        return fixture.kind == Kind.INSERT
                && fixture.station.blockPosition().equals(fixture.marker.blockPosition().offset(13, 2, 0));
    }

    private static int cleanup(final CommandSourceStack source) {
        final Lookup found = lookup(source.getLevel());
        if (found.status == Status.NONE) {
            tell(observers(source), source.getLevel(), "[M37] CLEANUP COMPLETE: fixture already absent");
            return 1;
        }
        if (found.fixture == null || found.status == Status.AMBIGUOUS || found.status == Status.CORRUPT) {
            return refuse(source, "CLEANUP REFUSED: fixture ownership status=" + found.status);
        }
        final Fixture fixture = found.fixture;
        if (CLEANUPS.containsKey(fixture.marker.getUUID())) {
            tell(observers(source), fixture.level, "[M37] CLEANUP: already in progress");
            return 1;
        }
        RUNS.remove(fixture.marker.getUUID());
        YAWS.remove(fixture.marker.getUUID());
        CLEANUPS.put(fixture.marker.getUUID(), new Cleanup(fixture.marker.getUUID(), fixture.level,
                observers(source)));
        tell(observers(source), fixture.level, "[M37] CLEANUP ENTRY status=" + found.status
                + " origin=" + fixture.origin.toShortString());
        return 1;
    }

    private static void advance(final Cleanup cleanup) {
        final Lookup found = lookup(cleanup.level);
        if (found.status == Status.NONE && cleanup.phase == CleanupPhase.REMOVE) {
            completeCleanup(cleanup);
            return;
        }
        if (found.fixture == null || !found.fixture.marker.getUUID().equals(cleanup.markerId)
                || found.status == Status.AMBIGUOUS || found.status == Status.CORRUPT) {
            failCleanup(cleanup, "fixture ownership changed to " + found.status);
            return;
        }
        final Fixture fixture = found.fixture;
        if (++cleanup.age > TIMEOUT_TICKS) {
            failCleanup(cleanup, "phase=" + cleanup.phase + " timed out; current fixture remains recoverable");
            return;
        }
        try {
            switch (cleanup.phase) {
                case STOP -> {
                    if (found.status != Status.PARTIAL && !powerStation(fixture)) {
                        failCleanup(cleanup, "stationary control cell is not fixture-owned");
                        return;
                    }
                    if (found.status == Status.PARTIAL) {
                        cleanup.phase = CleanupPhase.REMOVE;
                        tell(cleanup.observers, cleanup.level, "[M37] CLEANUP phase=REMOVE partial fixture");
                    } else if (retracted(fixture)) {
                        cleanup.phase = CleanupPhase.INNER_DISASSEMBLED;
                        tell(cleanup.observers, cleanup.level, "[M37] CLEANUP phase=INNER_DISASSEMBLED");
                    } else {
                        M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body,
                                fixture.origin, true);
                        cleanup.phase = CleanupPhase.REVERSE;
                        tell(cleanup.observers, cleanup.level, "[M37] CLEANUP phase=STOP");
                    }
                }
                case REVERSE -> {
                    if (retracted(fixture)) {
                        cleanup.phase = CleanupPhase.INNER_DISASSEMBLED;
                        return;
                    }
                    final CreativeMotorBlockEntity motor = motor(fixture);
                    if (motor == null) {
                        failCleanup(cleanup, "fixture motor missing");
                        return;
                    }
                    motor.generatedSpeed.setValue(64);
                    M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body,
                            fixture.origin, false);
                    cleanup.phase = CleanupPhase.RETRACT_WAIT;
                    tell(cleanup.observers, cleanup.level, "[M37] CLEANUP phase=RETRACT_WAIT");
                }
                case RETRACT_WAIT -> {
                    if (retracted(fixture)) {
                        M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body,
                                fixture.origin, true);
                        cleanup.phase = CleanupPhase.INNER_DISASSEMBLED;
                        tell(cleanup.observers, cleanup.level, "[M37] CLEANUP phase=INNER_DISASSEMBLED");
                    }
                }
                case INNER_DISASSEMBLED -> {
                    if (!retracted(fixture) && found.status != Status.PARTIAL) {
                        cleanup.phase = CleanupPhase.REVERSE;
                        return;
                    }
                    cleanup.phase = fixture.body == null ? CleanupPhase.REMOVE : CleanupPhase.OUTER_DISASSEMBLE;
                }
                case OUTER_DISASSEMBLE -> {
                    if (fixture.body == null) {
                        cleanup.phase = CleanupPhase.REMOVE;
                        return;
                    }
                    hold(fixture, 0, Vec3.ZERO);
                    final BlockPos assemblerPos = fixture.origin.above().north(2);
                    if (!(M35FixtureLookup.blockEntity(fixture.level, fixture.body, assemblerPos)
                            instanceof final PhysicsAssemblerBlockEntity assembler)) {
                        failCleanup(cleanup, "tagged outer assembler missing");
                        return;
                    }
                    tell(cleanup.observers, cleanup.level, "[M37] CLEANUP phase=OUTER_DISASSEMBLE");
                    try {
                        assembler.assembleOrDisassemble(null);
                    } catch (final IllegalStateException exception) {
                        final Lookup after = lookup(cleanup.level);
                        if (after.fixture == null || after.fixture.body != null
                                || !knownFootprint(after.fixture)) {
                            throw exception;
                        }
                        tell(cleanup.observers, cleanup.level,
                                "[M37] CLEANUP: outer sublevel already absent; static footprint verified");
                    }
                    cleanup.phase = CleanupPhase.REMOVE;
                    tell(cleanup.observers, cleanup.level, "[M37] CLEANUP phase=REMOVE");
                }
                case REMOVE -> {
                    if (fixture.body != null || !knownFootprint(fixture)
                            || M35FixtureLookup.blockEntity(fixture.level, null, fixture.origin)
                                    instanceof final MechanicalPistonBlockEntity piston
                                    && (piston.running || piston.movedContraption != null
                                            || !retracted(fixture))) {
                        failCleanup(cleanup, "static fixture is not safe to remove");
                        return;
                    }
                    removeStatic(fixture.level, fixture.origin, fixture.kind,
                            fixture.station.blockPosition());
                    completeCleanup(cleanup);
                }
            }
        } catch (final RuntimeException exception) {
            Sable.LOGGER.error("SABLE_M37_CLEANUP_EXCEPTION phase={} marker={} origin={}",
                    cleanup.phase, cleanup.markerId, fixture.origin, exception);
            failCleanup(cleanup, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private static void completeCleanup(final Cleanup cleanup) {
        CLEANUPS.remove(cleanup.markerId);
        tell(cleanup.observers, cleanup.level, "[M37] CLEANUP COMPLETE");
    }

    private static void failCleanup(final Cleanup cleanup, final String reason) {
        CLEANUPS.remove(cleanup.markerId);
        tell(cleanup.observers, cleanup.level, "[M37] CLEANUP FAILED: " + reason);
    }

    private static void removeStatic(final ServerLevel level, final BlockPos origin, final Kind kind,
                                     final BlockPos station) {
        final boolean yawed = !station.equals(origin.offset(kind.stationOffset()));
        final boolean legacyInsert = kind == Kind.INSERT && station.equals(origin.offset(13, 2, 0));
        if (legacyInsert) {
            removeIf(level, station, Blocks.STONE);
            removeIf(level, station.below(2), AllBlocks.ANDESITE_FUNNEL.get());
            removeIf(level, station.below(3), Blocks.CHEST);
            removeIf(level, station.below(2).south(), Blocks.REDSTONE_BLOCK);
        } else {
            removeStation(level, kind, station, yawed);
        }
        for (int x = -12; x <= -1; x++) {
            removeIf(level, origin.offset(x, 0, 0), AllBlocks.PISTON_EXTENSION_POLE.get());
        }
        removeIf(level, origin, AllBlocks.STICKY_MECHANICAL_PISTON.get());
        removeIf(level, origin.east(), AllBlocks.RADIAL_CHASSIS.get());
        removeIf(level, movingFunnelPos(origin, kind), AllBlocks.ANDESITE_FUNNEL.get());
        if (legacyInsert) {
            removeIf(level, origin.east().above(), AllBlocks.ANDESITE_FUNNEL.get());
        }
        removeIf(level, origin.east().south(), Blocks.CHEST);
        removeIf(level, origin.below(2), AllBlocks.CREATIVE_MOTOR.get());
        removeIf(level, origin.below(), AllBlocks.CLUTCH.get());
        removeIf(level, origin.below().south(), Blocks.LEVER);
        removeIf(level, origin.north(), Blocks.IRON_BLOCK);
        removeIf(level, origin.north(2), Blocks.IRON_BLOCK);
        removeIf(level, origin.above().north(2),
                dev.simulated_team.simulated.index.SimulatedBlocks.PHYSICS_ASSEMBLER.get());
        for (final SuperGlueEntity glue : new ArrayList<>(level.getEntitiesOfClass(SuperGlueEntity.class,
                new AABB(origin.offset(0, -2, -2), origin.offset(2, 2, 2)).inflate(0.25),
                entity -> entity.getTags().contains(GLUE_TAG)))) {
            glue.remove(Entity.RemovalReason.KILLED);
        }
        for (final ItemEntity item : new ArrayList<>(level.getEntitiesOfClass(ItemEntity.class,
                new AABB(station).inflate(2), entity -> entity.getTags().contains(ITEM_TAG)))) {
            item.discard();
        }
        for (final ArmorStand marker : markers(level, ORIGIN_TAG)) {
            if (marker.blockPosition().equals(origin)) {
                marker.discard();
            }
        }
        for (final ArmorStand marker : markers(level, STATION_TAG)) {
            if (marker.blockPosition().equals(station)) {
                marker.discard();
            }
        }
    }

    private static void removeIf(final ServerLevel level, final BlockPos pos, final Block expected) {
        if (level.getBlockState(pos).is(expected)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static List<ArmorStand> markers(final ServerLevel level, final String tag) {
        final List<ArmorStand> result = new ArrayList<>();
        for (final Entity entity : level.getAllEntities()) {
            if (entity instanceof final ArmorStand marker && marker.getTags().contains(tag)) {
                result.add(marker);
            }
        }
        return result;
    }

    private static List<UUID> observers(final CommandSourceStack source) {
        return source.getEntity() instanceof final ServerPlayer player ? List.of(player.getUUID()) : List.of();
    }

    private static void tell(final List<UUID> observers, final ServerLevel level, final String message) {
        Sable.LOGGER.info("SABLE_M37_FIXTURE {}", message);
        for (final UUID id : observers) {
            final ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                player.sendSystemMessage(Component.literal(message));
            }
        }
    }

    private static int refuse(final CommandSourceStack source, final String message) {
        tell(observers(source), source.getLevel(), "[M37] " + message);
        return 0;
    }

    private enum Kind {
        INSERT, EXTRACT, ENTITY;

        BlockPos stationOffset() {
            return this == INSERT ? new BlockPos(12, 2, 0)
                    : new BlockPos(14, 1, 0);
        }

        Direction movingFacing() {
            return this == INSERT ? Direction.UP : Direction.EAST;
        }

        Direction stationFacing() {
            return this == INSERT ? Direction.DOWN : Direction.WEST;
        }

        Direction chestDirection() {
            return this == INSERT ? Direction.UP : Direction.EAST;
        }

        static Kind fromMarker(final ArmorStand marker) {
            for (final Kind kind : values()) {
                if (marker.getTags().contains("sable_m37_"
                        + kind.name().toLowerCase(java.util.Locale.ROOT))) {
                    return kind;
                }
            }
            return null;
        }
    }

    private enum Status {
        NONE, STATIC, OUTER, PARTIAL, AMBIGUOUS, CORRUPT
    }

    private enum CleanupPhase {
        STOP, REVERSE, RETRACT_WAIT, INNER_DISASSEMBLED, OUTER_DISASSEMBLE, REMOVE
    }

    private record Fixture(ServerLevel level, ArmorStand marker, ArmorStand station,
                           BlockPos origin, ServerSubLevel body, Kind kind) {
    }

    private record Lookup(Status status, Fixture fixture) {
    }

    private static final class Pending {
        final ServerLevel level;
        final BlockPos origin;
        final Kind kind;
        final List<UUID> observers;
        int age;

        Pending(final ServerLevel level, final BlockPos origin, final Kind kind,
                final List<UUID> observers) {
            this.level = level;
            this.origin = origin;
            this.kind = kind;
            this.observers = observers;
        }
    }

    private static final class Run {
        final UUID markerId;
        final ServerLevel level;
        final List<UUID> observers;
        double yaw;
        Vec3 offset = Vec3.ZERO;
        int sourceBefore;
        int destinationBefore;
        int age;
        boolean actorObserved;
        boolean targetResolved;
        boolean hiddenPlotInteraction;
        boolean payloadReported;
        boolean reported;
        boolean movingExtractObserved;
        boolean movingInsertObserved;
        boolean entityPresentBefore;
        boolean closestReported;
        boolean itemFollowupReported;
        boolean stationReleased;
        boolean waitingForStationItem;
        boolean nativeQueryObserved;
        boolean nativeInsertLogged;
        boolean itemExistedBeforeNativeQuery;
        boolean stationControlSecured;
        int stationarySpawnCount;
        long stationaryItemSpawnTime = -1;
        long nativeQueryTime = -1;
        int eventSequence;
        UUID emittedItemId;
        int itemObservedAt;
        Vec3 lastActivePoint;
        BlockPos lastMovingFunnelBlock;
        M37FixtureGeometryObservation.Phase geometryPhase;

        Run(final UUID markerId, final ServerLevel level, final List<UUID> observers) {
            this.markerId = markerId;
            this.level = level;
            this.observers = observers;
        }
    }

    private static final class Yaw {
        final UUID markerId;
        final ServerLevel level;
        final List<UUID> observers;
        int age;
        boolean reversing;

        Yaw(final UUID markerId, final ServerLevel level, final List<UUID> observers) {
            this.markerId = markerId;
            this.level = level;
            this.observers = observers;
        }
    }

    private static final class Cleanup {
        final UUID markerId;
        final ServerLevel level;
        final List<UUID> observers;
        CleanupPhase phase = CleanupPhase.STOP;
        int age;

        Cleanup(final UUID markerId, final ServerLevel level, final List<UUID> observers) {
            this.markerId = markerId;
            this.level = level;
            this.observers = observers;
        }
    }
}
