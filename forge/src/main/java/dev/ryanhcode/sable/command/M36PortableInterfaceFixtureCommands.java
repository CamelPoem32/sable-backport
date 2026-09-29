package dev.ryanhcode.sable.command;

import com.mojang.brigadier.CommandDispatcher;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.chassis.ChassisBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlockEntity;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlockEntity;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.forge.event.ForgeSablePostPhysicsTickEvent;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Command-owned M36 fixture. Native Create owns motion, connection and transfer. */
public final class M36PortableInterfaceFixtureCommands {
    private static final String ORIGIN_TAG = "sable_m36_origin";
    private static final String STATION_TAG = "sable_m36_station";
    private static final int TIMEOUT_TICKS = 240;
    private static Pending pending;
    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static final Map<UUID, Cleanup> CLEANUPS = new HashMap<>();

    private M36PortableInterfaceFixtureCommands() {
    }

    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(M36PortableInterfaceFixtureCommands::tick);
        MinecraftForge.EVENT_BUS.addListener(M36PortableInterfaceFixtureCommands::postPhysicsTick);
        MinecraftForge.EVENT_BUS.addListener(M36PortableInterfaceFixtureCommands::serverStopped);
    }

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sable_m36").requires(source -> source.hasPermission(2))
                .then(Commands.literal("item").executes(context -> setup(context.getSource(), Kind.ITEM)))
                .then(Commands.literal("fluid").executes(context -> setup(context.getSource(), Kind.FLUID)))
                .then(Commands.literal("validate").executes(context -> validate(context.getSource())))
                .then(Commands.literal("run_item").executes(context -> run(context.getSource(), Kind.ITEM, false)))
                .then(Commands.literal("run_fluid").executes(context -> run(context.getSource(), Kind.FLUID, false)))
                .then(Commands.literal("yaw90").executes(context -> run(context.getSource(), null, true)))
                .then(Commands.literal("disconnect").executes(context -> disconnect(context.getSource())))
                .then(Commands.literal("reset").executes(context -> reset(context.getSource())))
                .then(Commands.literal("cleanup").executes(context -> cleanup(context.getSource()))));
    }

    private static int setup(final CommandSourceStack source, final Kind kind) {
        final Lookup existing = lookup(source.getLevel());
        if (pending != null || !existing.status.allowsSetup()) {
            return refuse(source, "SETUP REFUSED: M36 fixture status=" + existing.status
                    + "; run /function sable:m36/cleanup");
        }
        final BlockPos origin = BlockPos.containing(source.getPosition()).above(4);
        final ServerLevel level = source.getLevel();
        for (int x = -12; x <= 18; x++) {
            for (int y = -3; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (!level.getBlockState(origin.offset(x, y, z)).isAir()) {
                        return refuse(source, "SETUP REFUSED: occupied reserved cell " + origin.offset(x, y, z));
                    }
                }
            }
        }
        pending = new Pending(level, origin, kind, observers(source));
        tell(pending.observers, level, "[M36] SETUP phase=BUILD kind=" + kind);
        source.getServer().getCommands().performPrefixedCommand(
                source.withPosition(Vec3.atLowerCornerOf(origin)),
                "function sable:m36/internal/create_" + kind.name().toLowerCase(java.util.Locale.ROOT));
        return 1;
    }

    private static int validate(final CommandSourceStack source) {
        final Pending build = pending;
        if (build == null || build.level != source.getLevel()
                || !build.origin.equals(BlockPos.containing(source.getPosition()))) {
            return refuse(source, "SETUP FAILED: no matching pending build");
        }
        tell(build.observers, build.level, "[M36] SETUP phase=VALIDATE");
        final String failure = validateFresh(build);
        if (failure != null) {
            tell(build.observers, build.level, "[M36] SETUP FAILED reason=" + failure);
            tell(build.observers, build.level, "[M36] SETUP phase=ROLLBACK");
            removeStatic(build.level, build.origin, build.kind, build.origin.offset(15, 1, 0), true);
            tell(build.observers, build.level, "[M36] SETUP ROLLBACK COMPLETE");
            pending = null;
            return 0;
        }
        pending = null;
        tell(build.observers, build.level, "[M36] SETUP phase=READY outerSelectedBlocks=22");
        tell(build.observers, build.level, "[M36] READY: click the Physics Assembler, then run /function sable:m36/run_"
                + build.kind.name().toLowerCase(java.util.Locale.ROOT));
        return 1;
    }

    private static String validateFresh(final Pending build) {
        final ServerLevel level = build.level;
        final BlockPos origin = build.origin;
        final List<ArmorStand> fixtureMarkers = markers(level, ORIGIN_TAG);
        final List<ArmorStand> stationMarkers = markers(level, STATION_TAG);
        if (fixtureMarkers.size() != 1 || stationMarkers.size() != 1
                || !fixtureMarkers.get(0).blockPosition().equals(origin)
                || !stationMarkers.get(0).blockPosition().equals(origin.offset(15, 1, 0))) {
            return "FIXTURE_MARKERS_MISSING_OR_AMBIGUOUS";
        }
        if (!(level.getBlockEntity(origin) instanceof MechanicalPistonBlockEntity)
                || !(level.getBlockEntity(origin.below(2)) instanceof CreativeMotorBlockEntity)
                || !(level.getBlockEntity(origin.above().north(2)) instanceof PhysicsAssemblerBlockEntity)
                || !level.getBlockState(origin.north(2)).is(Blocks.IRON_BLOCK)
                || !level.getBlockState(origin.north()).is(Blocks.IRON_BLOCK)) {
            return "CONTROLLER_OR_ASSEMBLER_STARTING_BLOCK_INVALID";
        }
        for (int x = -12; x <= -1; x++) {
            if (!level.getBlockState(origin.offset(x, 0, 0)).is(AllBlocks.PISTON_EXTENSION_POLE.get())) {
                return "PISTON_EXTENSION_POLE_MISSING_" + x;
            }
        }
        final Block actor = build.kind.actor();
        final Block storage = build.kind.storage();
        if (!level.getBlockState(origin.east().above()).is(actor)
                || !level.getBlockState(origin.east().south()).is(storage)
                || !(level.getBlockEntity(origin.east()) instanceof final ChassisBlockEntity chassis)) {
            return "INNER_ACTOR_OR_STORAGE_MISSING";
        }
        final List<BlockPos> included = chassis.getIncludedBlockPositions(Direction.EAST, false);
        if (included == null || !included.contains(origin.east().above())
                || !included.contains(origin.east().south())) {
            return "INNER_NATIVE_CHASSIS_ATTACHMENT_INCOMPLETE";
        }
        if (build.kind == Kind.ITEM) {
            if (!(level.getBlockEntity(origin.east().south()) instanceof final net.minecraft.world.Container chest)
                    || !chest.getItem(0).is(Blocks.COBBLESTONE.asItem())
                    || chest.getItem(0).getCount() != 16) {
                return "MOUNTED_ITEM_SOURCE_INVALID";
            }
        } else {
            final BlockEntity tank = level.getBlockEntity(origin.east().south());
            final var handler = tank == null ? null : tank.getCapability(ForgeCapabilities.FLUID_HANDLER).resolve()
                    .orElse(null);
            if (handler == null || handler.fill(new FluidStack(Fluids.WATER, 4000),
                    net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) != 4000) {
                return "MOUNTED_FLUID_SOURCE_FILL_FAILED";
            }
        }
        final BlockPos station = origin.offset(15, 1, 0);
        if (!level.getBlockState(station).is(actor)
                || level.getBlockEntity(station) == null
                || !level.getBlockState(origin.east(2)).isAir()) {
            return "PARENT_INTERFACE_MISSING_OR_HULL_CONNECTED";
        }
        final AABB glueBounds = new AABB(origin.getX(), origin.getY() - 2, origin.getZ() - 2,
                origin.getX() + 1, origin.getY() + 2, origin.getZ() + 2);
        final SuperGlueEntity glue = new SuperGlueEntity(level, glueBounds);
        glue.addTag("sable_m36_glue");
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
        if (selected.size() != 22 || !selected.contains(origin.east().above())
                || !selected.contains(origin.east().south()) || selected.contains(station)) {
            return "OUTER_SELECTION_INVALID_" + selected.size();
        }
        return null;
    }

    private static int run(final CommandSourceStack source, final Kind requested, final boolean yaw90) {
        final Fixture fixture = fixture(source.getLevel());
        if (fixture == null || fixture.station == null || fixture.body == null
                || requested != null && requested != fixture.kind) {
            return refuse(source, "RUN REFUSED: exactly one assembled tagged M36 fixture of the requested type is required");
        }
        if (yaw90) {
            final Run active = RUNS.get(fixture.marker.getUUID());
            if (active == null) {
                return refuse(source, "YAW90 REFUSED: start /function sable:m36/run_"
                        + fixture.kind.name().toLowerCase(java.util.Locale.ROOT) + " first");
            }
            final double previousYaw = active.yaw;
            active.yaw = Math.PI / 2;
            active.offset = Vec3.ZERO;
            hold(active);
            if (!prepareStation(active)) {
                active.yaw = previousYaw;
                hold(active);
                return refuse(source, "YAW90 REFUSED: new physical station cells are occupied");
            }
            active.sourceBefore = sourceCount(fixture);
            active.destinationBefore = destinationCount(fixture);
            active.age = 0;
            active.connected = false;
            active.actorObserved = false;
            active.reported = false;
            active.restarting = !retracted(fixture);
            final CreativeMotorBlockEntity motor = fixtureMotor(fixture);
            if (motor == null) {
                return refuse(source, "YAW90 REFUSED: native piston motor missing");
            }
            motor.generatedSpeed.setValue(active.restarting ? 64 : -64);
            M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body,
                    fixture.origin, false);
            tell(active.observers, fixture.level, "[M36] YAW90: level pose, physical station and native piston direction updated");
            return 1;
        }
        if (!retracted(fixture)) {
            return refuse(source, "RUN REFUSED: inner native piston must be retracted first");
        }
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(fixture.level);
        if (physics == null || physics.getPhysicsHandle(fixture.body) == null) {
            return refuse(source, "RUN REFUSED: fixture physics handle missing");
        }
        final Run prior = RUNS.remove(fixture.marker.getUUID());
        final double yaw = yaw90 ? Math.PI / 2 : 0;
        final Run run = new Run(fixture, yaw, observers(source));
        hold(run);
        if (!prepareStation(run)) {
            if (prior != null) {
                RUNS.put(fixture.marker.getUUID(), prior);
            }
            return refuse(source, "RUN REFUSED: physical station cells occupied or orientation unsupported");
        }
        run.sourceBefore = sourceCount(fixture);
        run.destinationBefore = destinationCount(fixture);
        if (run.sourceBefore <= 0 || run.destinationBefore != 0) {
            return refuse(source, "RUN REFUSED: expected filled mounted source and empty parent destination");
        }
        RUNS.put(fixture.marker.getUUID(), run);
        M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body, fixture.origin, false);
        tell(run.observers, fixture.level, "[M36] " + fixture.kind + " RUN: native piston engaged; sourceBefore="
                + run.sourceBefore + " destinationBefore=" + run.destinationBefore
                + " yaw=" + Math.toDegrees(yaw));
        return 1;
    }

    private static Fixture fixture(final ServerLevel level) {
        final Lookup found = lookup(level);
        return found.status == LookupStatus.ACTIVE_STATIC || found.status == LookupStatus.ACTIVE_OUTER
                ? found.fixture : null;
    }

    private static Lookup lookup(final ServerLevel level) {
        final List<ArmorStand> originMarkers = markers(level, ORIGIN_TAG);
        final List<ArmorStand> stations = markers(level, STATION_TAG);
        if (originMarkers.isEmpty() && stations.isEmpty()) {
            return new Lookup(LookupStatus.NONE, null);
        }
        if (originMarkers.size() != 1 || stations.size() > 1) {
            return new Lookup(LookupStatus.AMBIGUOUS, null);
        }
        final ArmorStand marker = originMarkers.get(0);
        final Kind kind = marker.getTags().contains("sable_m36_item") ? Kind.ITEM
                : marker.getTags().contains("sable_m36_fluid") ? Kind.FLUID : null;
        if (kind == null) {
            return new Lookup(LookupStatus.CORRUPT, null);
        }
        final BlockPos staticOrigin = marker.blockPosition();
        final ArmorStand station = stations.isEmpty() ? null : stations.get(0);
        if (M35FixtureLookup.blockState(level, null, staticOrigin).is(AllBlocks.STICKY_MECHANICAL_PISTON.get())) {
            return new Lookup(LookupStatus.ACTIVE_STATIC,
                    new Fixture(level, marker, station, staticOrigin, null, kind));
        }
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        Fixture found = null;
        if (container != null) {
            for (final ServerSubLevel body : container.getAllSubLevels()) {
                for (final BlockPos candidate : SimAssemblyHelper.collectBlocks(level, body)) {
                    if (M35FixtureLookup.blockState(level, body, candidate)
                            .is(AllBlocks.STICKY_MECHANICAL_PISTON.get())
                            && M35FixtureLookup.blockState(level, body, candidate.north(2)).is(Blocks.IRON_BLOCK)
                            && M35FixtureLookup.blockEntity(level, body, candidate.above().north(2))
                            instanceof PhysicsAssemblerBlockEntity) {
                        if (found != null) {
                            return new Lookup(LookupStatus.AMBIGUOUS, null);
                        }
                        found = new Fixture(level, marker, station, candidate, body, kind);
                    }
                }
            }
        }
        if (found != null) {
            return new Lookup(LookupStatus.ACTIVE_OUTER, found);
        }
        final Fixture remainder = new Fixture(level, marker, station, staticOrigin, null, kind);
        if (!knownFootprint(remainder)) {
            return new Lookup(LookupStatus.CORRUPT, remainder);
        }
        return new Lookup(hasStaticRemainder(remainder) ? LookupStatus.PARTIALLY_CLEANED
                : LookupStatus.ORPHAN_MARKER, remainder);
    }

    private static boolean knownFootprint(final Fixture fixture) {
        final ServerLevel level = fixture.level;
        final BlockPos origin = fixture.origin;
        for (int x = -12; x <= -1; x++) {
            if (!expectedOrAir(level, origin.offset(x, 0, 0), AllBlocks.PISTON_EXTENSION_POLE.get())) {
                return false;
            }
        }
        if (!expectedOrAir(level, origin.east(), AllBlocks.RADIAL_CHASSIS.get())
                || !expectedOrAir(level, origin, AllBlocks.STICKY_MECHANICAL_PISTON.get())
                || !expectedOrAir(level, origin.east().above(), fixture.kind.actor())
                || !expectedOrAir(level, origin.east().south(), fixture.kind.storage())
                || !expectedOrAir(level, origin.below(2), AllBlocks.CREATIVE_MOTOR.get())
                || !expectedOrAir(level, origin.below(), AllBlocks.CLUTCH.get())
                || !expectedOrAir(level, origin.below().south(), Blocks.LEVER)
                || !expectedOrAir(level, origin.north(), Blocks.IRON_BLOCK)
                || !expectedOrAir(level, origin.north(2), Blocks.IRON_BLOCK)
                || !expectedOrAir(level, origin.above().north(2),
                        dev.simulated_team.simulated.index.SimulatedBlocks.PHYSICS_ASSEMBLER.get())) {
            return false;
        }
        if (fixture.station != null) {
            final BlockPos station = fixture.station.blockPosition();
            final Direction outward = stationOutward(fixture);
            if (outward == null) {
                return false;
            }
            final List<Block> expected = fixture.kind == Kind.ITEM
                    ? List.of(fixture.kind.actor(), Blocks.HOPPER, Blocks.CHEST)
                    : List.of(fixture.kind.actor(), AllBlocks.MECHANICAL_PUMP.get(),
                            AllBlocks.FLUID_TANK.get(), AllBlocks.COGWHEEL.get(),
                            AllBlocks.CREATIVE_MOTOR.get());
            final List<BlockPos> cells = stationCells(station, outward, fixture.kind);
            for (int i = 0; i < cells.size(); i++) {
                if (!expectedOrAir(level, cells.get(i), expected.get(i))) {
                    return false;
                }
            }
            if (!expectedOrAir(level, station.above(), Blocks.REDSTONE_BLOCK)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasStaticRemainder(final Fixture fixture) {
        final ServerLevel level = fixture.level;
        final BlockPos origin = fixture.origin;
        if (!level.getBlockState(origin.east()).isAir()
                || !level.getBlockState(origin.east().above()).isAir()
                || !level.getBlockState(origin.east().south()).isAir()
                || !level.getBlockState(origin.below(2)).isAir()
                || !level.getBlockState(origin.north(2)).isAir()
                || !level.getBlockState(origin.above().north(2)).isAir()) {
            return true;
        }
        for (int x = -12; x <= -1; x++) {
            if (!level.getBlockState(origin.offset(x, 0, 0)).isAir()) {
                return true;
            }
        }
        if (fixture.station == null) {
            return false;
        }
        final BlockPos station = fixture.station.blockPosition();
        final Direction outward = stationOutward(fixture);
        if (outward == null) {
            return true;
        }
        for (final BlockPos cell : stationCells(station, outward, fixture.kind)) {
            if (!level.getBlockState(cell).isAir()) {
                return true;
            }
        }
        return !level.getBlockState(station.above()).isAir();
    }

    private static boolean expectedOrAir(final ServerLevel level, final BlockPos pos, final Block expected) {
        final BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(expected);
    }

    private static Direction stationOutward(final Fixture fixture) {
        if (fixture.station == null || fixture.kind == Kind.ITEM) {
            return Direction.EAST;
        }
        final BlockPos station = fixture.station.blockPosition();
        final BlockState state = fixture.level.getBlockState(station);
        if (state.is(fixture.kind.actor()) && state.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING).getOpposite();
        }
        final List<Block> expected = List.of(fixture.kind.actor(), AllBlocks.MECHANICAL_PUMP.get(),
                AllBlocks.FLUID_TANK.get(), AllBlocks.COGWHEEL.get(), AllBlocks.CREATIVE_MOTOR.get());
        Direction best = null;
        int bestCount = 0;
        boolean tie = false;
        for (final Direction outward : Direction.Plane.HORIZONTAL) {
            final List<BlockPos> cells = stationCells(station, outward, fixture.kind);
            int matches = 0;
            boolean valid = true;
            for (int i = 0; i < cells.size(); i++) {
                final BlockState candidate = fixture.level.getBlockState(cells.get(i));
                if (candidate.is(expected.get(i))) {
                    matches++;
                } else if (!candidate.isAir()) {
                    valid = false;
                    break;
                }
            }
            if (valid && matches > bestCount) {
                best = outward;
                bestCount = matches;
                tie = false;
            } else if (valid && matches == bestCount && matches > 0) {
                tie = true;
            }
        }
        return tie ? null : best == null ? Direction.EAST : best;
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
                && M35FixtureLookup.blockState(fixture.level, fixture.body, fixture.origin.east().above())
                        .is(fixture.kind.actor())
                && M35FixtureLookup.blockState(fixture.level, fixture.body, fixture.origin.east().south())
                        .is(fixture.kind.storage());
    }

    private static void hold(final Run run) {
        final Fixture fixture = run.fixture;
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(fixture.level);
        final RigidBodyHandle handle = physics == null ? null : physics.getPhysicsHandle(fixture.body);
        if (handle == null || !handle.isValid()) {
            throw new IllegalStateException("M36 fixture physics handle unavailable");
        }
        final Quaterniond rotation = new Quaterniond().rotationY(run.yaw);
        final Vec3 rawCenter = Vec3.atCenterOf(fixture.origin);
        final Vector3d local = new Vector3d(rawCenter.x, rawCenter.y, rawCenter.z)
                .sub(fixture.body.logicalPose().rotationPoint()).mul(fixture.body.logicalPose().scale());
        final Vec3 visibleAnchor = Vec3.atCenterOf(fixture.marker.blockPosition()).add(run.offset);
        final Vector3d target = new Vector3d(visibleAnchor.x, visibleAnchor.y, visibleAnchor.z)
                .sub(rotation.transform(local));
        handle.teleport(target, rotation);
        handle.setLinearAndAngularVelocity(new Vector3d(), new Vector3d());
        physics.updatePose(fixture.body);
        fixture.body.updateBoundingBox();
        fixture.body.updateLastPose();
    }

    private static boolean prepareStation(final Run run) {
        final Fixture fixture = run.fixture;
        final Vec3 up = fixture.body.logicalPose().transformNormal(new Vec3(0, 1, 0));
        if (up.y <= 0.999) {
            return false;
        }
        final Vec3 facingVector = fixture.body.logicalPose().transformNormal(new Vec3(1, 0, 0));
        final Direction outward = Direction.getNearest(facingVector.x, facingVector.y, facingVector.z);
        if (facingVector.distanceToSqr(Vec3.atLowerCornerOf(outward.getNormal())) > 0.5) {
            return false;
        }
        final BlockPos desired = BlockPos.containing(fixture.body.logicalPose().transformPosition(
                Vec3.atCenterOf(fixture.origin.offset(15, 1, 0))));
        final BlockPos old = fixture.station.blockPosition();
        if (old.equals(desired) && fixture.level.getBlockState(old).is(fixture.kind.actor())) {
            return true;
        }
        if (!stationCellsClear(fixture.level, desired, outward, fixture.kind)) {
            return false;
        }
        if (!removeStation(fixture, old)) {
            return false;
        }
        buildStation(fixture.level, desired, outward, fixture.kind);
        fixture.station.setPos(desired.getX(), desired.getY(), desired.getZ());
        return true;
    }

    private static int sourceCount(final Fixture fixture) {
        final BlockEntity controller = M35FixtureLookup.blockEntity(fixture.level, fixture.body, fixture.origin);
        if (controller instanceof final MechanicalPistonBlockEntity piston
                && piston.movedContraption != null
                && piston.movedContraption.getContraption() != null) {
            final AbstractContraptionEntity moving = piston.movedContraption;
            if (fixture.kind == Kind.ITEM) {
                final IItemHandler items = moving.getContraption().getStorage().getAllItems();
                int count = 0;
                for (int slot = 0; slot < items.getSlots(); slot++) {
                    if (items.getStackInSlot(slot).is(Items.COBBLESTONE)) {
                        count += items.getStackInSlot(slot).getCount();
                    }
                }
                return count;
            }
            final IFluidHandler fluids = moving.getContraption().getStorage().getFluids();
            int amount = 0;
            for (int tank = 0; tank < fluids.getTanks(); tank++) {
                if (fluids.getFluidInTank(tank).getFluid() == Fluids.WATER) {
                    amount += fluids.getFluidInTank(tank).getAmount();
                }
            }
            return amount;
        }
        final int offset = controller instanceof final MechanicalPistonBlockEntity piston
                ? Math.round(piston.offset) : 0;
        final BlockEntity storage = M35FixtureLookup.blockEntity(fixture.level, fixture.body,
                fixture.origin.east(offset + 1).south());
        if (fixture.kind == Kind.ITEM && storage instanceof final net.minecraft.world.Container chest) {
            int count = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                if (chest.getItem(slot).is(Items.COBBLESTONE)) {
                    count += chest.getItem(slot).getCount();
                }
            }
            return count;
        }
        if (fixture.kind == Kind.FLUID && storage != null) {
            final IFluidHandler fluids = storage.getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElse(null);
            return fluids == null ? -1 : fluids.getFluidInTank(0).getAmount();
        }
        return -1;
    }

    private static int destinationCount(final Fixture fixture) {
        final BlockPos station = fixture.station.blockPosition();
        if (fixture.kind == Kind.ITEM) {
            final BlockEntity chest = fixture.level.getBlockEntity(station.below().south());
            if (!(chest instanceof final net.minecraft.world.Container container)) {
                return -1;
            }
            int count = 0;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (container.getItem(slot).is(Items.COBBLESTONE)) {
                    count += container.getItem(slot).getCount();
                }
            }
            return count;
        }
        final BlockState state = fixture.level.getBlockState(station);
        if (!state.hasProperty(BlockStateProperties.FACING)) {
            return -1;
        }
        final BlockEntity tank = fixture.level.getBlockEntity(station.relative(
                state.getValue(BlockStateProperties.FACING).getOpposite(), 2));
        final IFluidHandler fluids = tank == null ? null
                : tank.getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElse(null);
        return fluids == null ? -1 : fluids.getFluidInTank(0).getAmount();
    }

    private static boolean stationCellsClear(final ServerLevel level, final BlockPos station,
                                             final Direction outward, final Kind kind) {
        for (final BlockPos cell : stationCells(station, outward, kind)) {
            if (!level.hasChunkAt(cell) || !level.getBlockState(cell).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static List<BlockPos> stationCells(final BlockPos station, final Direction outward, final Kind kind) {
        if (kind == Kind.ITEM) {
            return List.of(station, station.below(), station.below().south());
        }
        final Direction back = outward.getOpposite();
        final BlockPos pump = station.relative(back);
        final BlockPos cog = pump.relative(back.getCounterClockWise());
        return List.of(station, pump, station.relative(back, 2), cog, cog.relative(back.getOpposite()));
    }

    private static boolean removeStation(final Fixture fixture, final BlockPos station) {
        final BlockState state = fixture.level.getBlockState(station);
        if (!state.is(fixture.kind.actor()) || !state.hasProperty(BlockStateProperties.FACING)) {
            return false;
        }
        final Direction outward = state.getValue(BlockStateProperties.FACING).getOpposite();
        final List<BlockPos> cells = stationCells(station, outward, fixture.kind);
        final List<Block> expected = fixture.kind == Kind.ITEM
                ? List.of(fixture.kind.actor(), Blocks.HOPPER, Blocks.CHEST)
                : List.of(fixture.kind.actor(), AllBlocks.MECHANICAL_PUMP.get(),
                        AllBlocks.FLUID_TANK.get(), AllBlocks.COGWHEEL.get(), AllBlocks.CREATIVE_MOTOR.get());
        for (int i = 0; i < cells.size(); i++) {
            if (!fixture.level.getBlockState(cells.get(i)).is(expected.get(i))) {
                return false;
            }
        }
        for (final BlockPos cell : cells) {
            fixture.level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
        }
        return true;
    }

    private static void buildStation(final ServerLevel level, final BlockPos station,
                                     final Direction outward, final Kind kind) {
        level.setBlock(station, kind.actor().defaultBlockState()
                .setValue(BlockStateProperties.FACING, outward.getOpposite()), 3);
        if (kind == Kind.ITEM) {
            level.setBlock(station.below(), Blocks.HOPPER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.HopperBlock.FACING, Direction.SOUTH), 3);
            level.setBlock(station.below().south(), Blocks.CHEST.defaultBlockState(), 3);
            return;
        }
        final Direction back = outward.getOpposite();
        final BlockPos pump = station.relative(back);
        final BlockPos cog = pump.relative(back.getCounterClockWise());
        level.setBlock(pump, AllBlocks.MECHANICAL_PUMP.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, back), 3);
        level.setBlock(station.relative(back, 2), AllBlocks.FLUID_TANK.get().defaultBlockState(), 3);
        level.setBlock(cog, AllBlocks.COGWHEEL.get().defaultBlockState()
                .setValue(BlockStateProperties.AXIS, back.getAxis()), 3);
        level.setBlock(cog.relative(back.getOpposite()), AllBlocks.CREATIVE_MOTOR.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, back), 3);
        if (level.getBlockEntity(cog.relative(back.getOpposite()))
                instanceof final CreativeMotorBlockEntity motor) {
            motor.generatedSpeed.setValue(64);
        }
    }

    private static int disconnect(final CommandSourceStack source) {
        final Fixture fixture = fixture(source.getLevel());
        final Run run = fixture == null ? null : RUNS.get(fixture.marker.getUUID());
        if (run == null) {
            return refuse(source, "DISCONNECT REFUSED: no active M36 run");
        }
        run.offset = new Vec3(0, 0, 18);
        hold(run);
        tell(run.observers, fixture.level, "[M36] DISCONNECT: fixture moved beyond native connection range");
        return 1;
    }

    private static int reset(final CommandSourceStack source) {
        final Fixture fixture = fixture(source.getLevel());
        if (fixture == null || fixture.station == null || fixture.body != null || !retracted(fixture)
                || RUNS.containsKey(fixture.marker.getUUID()) || CLEANUPS.containsKey(fixture.marker.getUUID())) {
            return refuse(source, "RESET REFUSED: disassemble/retract the fixture first");
        }
        final BlockEntity sourceStorage = fixture.level.getBlockEntity(fixture.origin.east().south());
        if (fixture.kind == Kind.ITEM && sourceStorage instanceof final net.minecraft.world.Container chest) {
            chest.clearContent();
            chest.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
            chest.setChanged();
        } else if (fixture.kind == Kind.FLUID && sourceStorage != null) {
            final IFluidHandler fluids = sourceStorage.getCapability(ForgeCapabilities.FLUID_HANDLER)
                    .resolve().orElse(null);
            if (fluids == null) {
                return refuse(source, "RESET REFUSED: mounted tank capability missing");
            }
            fluids.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
            fluids.fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE);
        }
        if (!removeStation(fixture, fixture.station.blockPosition())) {
            return refuse(source, "RESET REFUSED: stationary transfer hardware changed");
        }
        removeIf(fixture.level, fixture.station.blockPosition().above(), Blocks.REDSTONE_BLOCK);
        final BlockPos station = fixture.origin.offset(15, 1, 0);
        if (!stationCellsClear(fixture.level, station, Direction.EAST, fixture.kind)) {
            return refuse(source, "RESET REFUSED: original station cells occupied");
        }
        buildStation(fixture.level, station, Direction.EAST, fixture.kind);
        fixture.station.setPos(station.getX(), station.getY(), station.getZ());
        tell(observers(source), fixture.level, "[M36] RESET COMPLETE");
        return 1;
    }

    private static int cleanup(final CommandSourceStack source) {
        final Lookup found = lookup(source.getLevel());
        if (found.status == LookupStatus.NONE) {
            tell(observers(source), source.getLevel(), "[M36] CLEANUP COMPLETE: fixture already absent");
            return 1;
        }
        if (!found.status.allowsCleanup()) {
            return refuse(source, "CLEANUP REFUSED: fixture status=" + found.status);
        }
        final Fixture fixture = found.fixture;
        if (CLEANUPS.containsKey(fixture.marker.getUUID())) {
            tell(observers(source), fixture.level, "[M36] CLEANUP: already in progress");
            return 1;
        }
        RUNS.remove(fixture.marker.getUUID());
        CLEANUPS.put(fixture.marker.getUUID(), new Cleanup(fixture.marker.getUUID(),
                fixture.level, observers(source)));
        tell(observers(source), fixture.level, "[M36] CLEANUP ENTRY status=" + found.status
                + " mode=" + fixture.kind + " marker=" + fixture.marker.getUUID()
                + " origin=" + fixture.origin.toShortString() + " " + describeInner(fixture));
        return 1;
    }

    private static void tick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (pending != null && pending.level.getServer() == event.getServer() && ++pending.age > 5) {
            tell(pending.observers, pending.level, "[M36] SETUP FAILED: validation was not reached");
            removeStatic(pending.level, pending.origin, pending.kind,
                    pending.origin.offset(15, 1, 0), true);
            pending = null;
        }
        for (final Cleanup cleanup : List.copyOf(CLEANUPS.values())) {
            if (cleanup.level.getServer() == event.getServer()) {
                advance(cleanup);
            }
        }
        for (final Run run : List.copyOf(RUNS.values())) {
            if (run.fixture.level.getServer() == event.getServer()) {
                advance(run);
            }
        }
    }

    private static void postPhysicsTick(final ForgeSablePostPhysicsTickEvent event) {
        for (final Run run : RUNS.values()) {
            if (run.fixture.level == event.getPhysicsSystem().getLevel()) {
                hold(run);
            }
        }
        for (final Cleanup cleanup : List.copyOf(CLEANUPS.values())) {
            if (cleanup.level == event.getPhysicsSystem().getLevel()) {
                final Lookup found = lookup(cleanup.level);
                if (found.fixture != null && found.fixture.body != null
                        && cleanup.markerId.equals(found.fixture.marker.getUUID())) {
                    try {
                        hold(new Run(found.fixture, 0, cleanup.observers));
                    } catch (final RuntimeException exception) {
                        logCleanupException(cleanup, found.fixture, exception);
                        failCleanup(cleanup, "fixture pose hold failed; retry after inspecting world state");
                    }
                }
            }
        }
    }

    private static void advance(final Run run) {
        final Fixture fixture = fixture(run.fixture.level);
        if (fixture == null || fixture.body == null || !fixture.marker.getUUID().equals(run.fixture.marker.getUUID())) {
            RUNS.remove(run.fixture.marker.getUUID());
            return;
        }
        hold(run);
        if (M35FixtureLookup.blockEntity(fixture.level, fixture.body, fixture.origin)
                instanceof final MechanicalPistonBlockEntity piston
                && piston.movedContraption != null
                && piston.movedContraption.getContraption() != null) {
            run.actorObserved |= piston.movedContraption.getContraption().getActors().stream()
                    .anyMatch(actor -> actor.getLeft().state().is(fixture.kind.actor()));
        }
        if (run.restarting && retracted(fixture)) {
            final CreativeMotorBlockEntity motor = fixtureMotor(fixture);
            if (motor != null) {
                motor.generatedSpeed.setValue(-64);
                M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body,
                        fixture.origin, false);
                run.restarting = false;
                tell(run.observers, fixture.level, "[M36] YAW90: native piston re-extended toward new station");
            }
        }
        final BlockEntity stationary = fixture.level.getBlockEntity(fixture.station.blockPosition());
        run.connected |= stationary instanceof final PortableStorageInterfaceBlockEntity interfaceEntity
                && interfaceEntity.canTransfer();
        final int sourceNow = sourceCount(fixture);
        final int destinationNow = destinationCount(fixture);
        final boolean hiddenHandshake = Sable.HELPER.getContaining(fixture.level,
                fixture.station.blockPosition()) != null;
        if (!run.reported && (run.connected && sourceNow >= 0 && sourceNow < run.sourceBefore
                && destinationNow > run.destinationBefore || ++run.age >= 400)) {
            run.reported = true;
            final boolean pass = run.actorObserved && run.connected && sourceNow >= 0 && sourceNow < run.sourceBefore
                    && destinationNow > run.destinationBefore && !hiddenHandshake;
            tell(run.observers, fixture.level, "[M36] " + fixture.kind + " movingInterface="
                    + run.actorObserved + " candidateResolved=" + run.connected + " connected="
                    + run.connected + " sourceBefore=" + run.sourceBefore
                    + " sourceAfter=" + sourceNow + " destinationBefore=" + run.destinationBefore
                    + " destinationAfter=" + destinationNow + " transferred=" + pass
                    + " hiddenPlotHandshake=" + hiddenHandshake + " " + (pass ? "PASS" : "INCOMPLETE"));
        }
    }

    private static void advance(final Cleanup cleanup) {
        if (++cleanup.age > TIMEOUT_TICKS) {
            final Lookup current = lookup(cleanup.level);
            failCleanup(cleanup, "phase=" + cleanup.phase + " exceeded " + TIMEOUT_TICKS
                    + " ticks; current world state remains recoverable; "
                    + (current.fixture == null ? "lookup=" + current.status : describeInner(current.fixture)));
            return;
        }
        final Lookup found = lookup(cleanup.level);
        if (found.status == LookupStatus.NONE && cleanup.phase == CleanupPhase.REMOVE) {
            completeCleanup(cleanup);
            return;
        }
        final Fixture fixture = found.fixture;
        if (fixture == null || !fixture.marker.getUUID().equals(cleanup.markerId)
                || found.status == LookupStatus.CORRUPT) {
            failCleanup(cleanup, "fixture status=" + found.status + " during " + cleanup.phase);
            return;
        }
        try {
            if (cleanup.phase == CleanupPhase.STOP) {
                cleanup.phase = found.status.startsAt(retracted(fixture));
                if (cleanup.phase == CleanupPhase.REMOVE) {
                    tell(cleanup.observers, cleanup.level, "[M36] CLEANUP phase=REMOVE status=" + found.status);
                    return;
                }
                if (cleanup.phase == CleanupPhase.INNER_DISASSEMBLED) {
                    tell(cleanup.observers, cleanup.level, "[M36] CLEANUP phase=INNER_DISASSEMBLED");
                    return;
                }
                M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body,
                        fixture.origin, true);
                tell(cleanup.observers, cleanup.level, "[M36] CLEANUP phase=STOP");
                tell(cleanup.observers, cleanup.level, "[M36] CLEANUP phase=DISCONNECT_WAIT");
            } else if (cleanup.phase == CleanupPhase.DISCONNECT_WAIT) {
                if (!powerFixtureStation(fixture)) {
                    failCleanup(cleanup, "station disconnect cell occupied by an unknown block");
                    return;
                }
                if (++cleanup.disconnectAge >= 4 && !stationConnected(fixture)
                        && !actorHandshakeActive(fixture)) {
                    cleanup.phase = CleanupPhase.REVERSE;
                    tell(cleanup.observers, cleanup.level, "[M36] CLEANUP phase=REVERSE native interface disconnected");
                }
            } else if (cleanup.phase == CleanupPhase.REVERSE) {
                if (retracted(fixture)) {
                    cleanup.phase = CleanupPhase.INNER_DISASSEMBLED;
                    return;
                }
                final CreativeMotorBlockEntity motor = fixtureMotor(fixture);
                if (motor == null) {
                    failCleanup(cleanup, "fixture motor missing at " + fixture.origin.below(2).toShortString());
                    return;
                }
                motor.generatedSpeed.setValue(64);
                M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body,
                        fixture.origin, false);
                cleanup.phase = CleanupPhase.RETRACT_WAIT;
                tell(cleanup.observers, cleanup.level, "[M36] CLEANUP phase=RETRACT_WAIT");
            } else if (cleanup.phase == CleanupPhase.RETRACT_WAIT && retracted(fixture)) {
                M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body,
                        fixture.origin, true);
                cleanup.phase = CleanupPhase.INNER_DISASSEMBLED;
                tell(cleanup.observers, cleanup.level, "[M36] CLEANUP phase=INNER_DISASSEMBLED");
            } else if (cleanup.phase == CleanupPhase.INNER_DISASSEMBLED) {
                if (!retracted(fixture)) {
                    cleanup.phase = CleanupPhase.REVERSE;
                    return;
                }
                cleanup.phase = fixture.body == null ? CleanupPhase.REMOVE : CleanupPhase.OUTER_DISASSEMBLE;
                if (fixture.body == null) {
                    tell(cleanup.observers, cleanup.level,
                            "[M36] CLEANUP phase=REMOVE outer already disassembled");
                }
            } else if (cleanup.phase == CleanupPhase.OUTER_DISASSEMBLE) {
                if (fixture.body == null) {
                    cleanup.phase = CleanupPhase.REMOVE;
                    return;
                }
                hold(new Run(fixture, 0, cleanup.observers));
                final BlockPos assemblerPos = fixture.origin.above().north(2);
                if (!(M35FixtureLookup.blockEntity(fixture.level, fixture.body, assemblerPos)
                        instanceof final PhysicsAssemblerBlockEntity assembler)) {
                    failCleanup(cleanup, "outer assembler missing at " + assemblerPos.toShortString());
                    return;
                }
                tell(cleanup.observers, cleanup.level, "[M36] CLEANUP phase=OUTER_DISASSEMBLE");
                try {
                    assembler.assembleOrDisassemble(null);
                } catch (final RuntimeException exception) {
                    logCleanupException(cleanup, fixture, exception);
                    final Lookup afterFailure = lookup(cleanup.level);
                    if (bodyRegistered(fixture.level, fixture.body)
                            || afterFailure.fixture == null || afterFailure.fixture.body != null
                            || !knownFootprint(afterFailure.fixture)) {
                        failCleanup(cleanup, "outer disassembly failed; fixture remains recoverable");
                        return;
                    }
                    tell(cleanup.observers, cleanup.level,
                            "[M36] CLEANUP: outer body already absent; restored fixture blocks verified");
                }
                cleanup.phase = CleanupPhase.REMOVE;
                tell(cleanup.observers, cleanup.level, "[M36] CLEANUP phase=REMOVE");
            } else if (cleanup.phase == CleanupPhase.REMOVE) {
                if (fixture.body != null || !knownFootprint(fixture)
                        || M35FixtureLookup.blockEntity(fixture.level, null, fixture.origin)
                        instanceof final MechanicalPistonBlockEntity piston
                                && (piston.running || piston.movedContraption != null || !retracted(fixture))) {
                    failCleanup(cleanup, "static fixture not safe to remove; status=" + found.status);
                    return;
                }
                removeKnownFixture(fixture);
                completeCleanup(cleanup);
            }
        } catch (final RuntimeException exception) {
            logCleanupException(cleanup, fixture, exception);
            failCleanup(cleanup, exception.getClass().getSimpleName() + " during " + cleanup.phase);
        }
    }

    private static void failCleanup(final Cleanup cleanup, final String reason) {
        CLEANUPS.remove(cleanup.markerId);
        tell(cleanup.observers, cleanup.level, "[M36] CLEANUP FAILED: " + reason);
    }

    private static boolean bodyRegistered(final ServerLevel level, final ServerSubLevel body) {
        if (body == null) {
            return false;
        }
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && container.getAllSubLevels().stream()
                .anyMatch(candidate -> candidate.getUniqueId().equals(body.getUniqueId()));
    }

    private static void completeCleanup(final Cleanup cleanup) {
        CLEANUPS.remove(cleanup.markerId);
        tell(cleanup.observers, cleanup.level, "[M36] CLEANUP COMPLETE");
    }

    private static boolean powerFixtureStation(final Fixture fixture) {
        if (fixture.station == null) {
            return true;
        }
        final BlockPos disconnectCell = fixture.station.blockPosition().above();
        if (!fixture.level.hasChunkAt(disconnectCell)
                || !expectedOrAir(fixture.level, disconnectCell, Blocks.REDSTONE_BLOCK)) {
            return false;
        }
        fixture.level.setBlock(disconnectCell, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        if (fixture.level.getBlockEntity(fixture.station.blockPosition())
                instanceof final PortableStorageInterfaceBlockEntity interfaceEntity) {
            interfaceEntity.neighbourChanged();
        }
        return true;
    }

    private static boolean stationConnected(final Fixture fixture) {
        return fixture.station != null
                && fixture.level.getBlockEntity(fixture.station.blockPosition())
                        instanceof final PortableStorageInterfaceBlockEntity interfaceEntity
                && interfaceEntity.canTransfer();
    }

    private static boolean actorHandshakeActive(final Fixture fixture) {
        final BlockEntity controller = M35FixtureLookup.blockEntity(fixture.level, fixture.body, fixture.origin);
        if (!(controller instanceof final MechanicalPistonBlockEntity piston)
                || piston.movedContraption == null || piston.movedContraption.getContraption() == null) {
            return false;
        }
        return piston.movedContraption.getContraption().getActors().stream()
                .anyMatch(actor -> actor.getLeft().state().is(fixture.kind.actor())
                        && (actor.getRight().stall || actor.getRight().data.contains("WorkingPos")));
    }

    private static String describeInner(final Fixture fixture) {
        final BlockEntity controller = M35FixtureLookup.blockEntity(fixture.level, fixture.body, fixture.origin);
        if (!(controller instanceof final MechanicalPistonBlockEntity piston)) {
            return "innerPiston=absent stationConnected=" + stationConnected(fixture);
        }
        boolean actorStalled = false;
        boolean workingPos = false;
        if (piston.movedContraption != null && piston.movedContraption.getContraption() != null) {
            for (final var actor : piston.movedContraption.getContraption().getActors()) {
                if (actor.getLeft().state().is(fixture.kind.actor())) {
                    actorStalled |= actor.getRight().stall;
                    workingPos |= actor.getRight().data.contains("WorkingPos");
                }
            }
        }
        return "pistonOffset=" + piston.offset + " movementSpeed=" + piston.getMovementSpeed()
                + " running=" + piston.running + " movingEntity=" + (piston.movedContraption != null)
                + " actorStalled=" + actorStalled + " workingPos=" + workingPos
                + " stationConnected=" + stationConnected(fixture);
    }

    private static void logCleanupException(final Cleanup cleanup, final Fixture fixture,
                                            final RuntimeException exception) {
        try {
            final Lookup current = lookup(cleanup.level);
            final Fixture resolved = current.fixture == null ? fixture : current.fixture;
            final BlockEntity controller = M35FixtureLookup.blockEntity(resolved.level, resolved.body,
                    resolved.origin);
            final String details = "phase=" + cleanup.phase + " exception=" + exception.getClass().getName()
                    + " message=" + exception.getMessage() + " fixtureMode=" + fixture.kind
                    + " marker=" + cleanup.markerId + " origin=" + fixture.origin.toShortString()
                    + " storedSableId=" + (fixture.body == null ? "null" : fixture.body.getUniqueId())
                    + " resolvedSable=" + (current.fixture != null && current.fixture.body != null)
                    + " subLevel=" + (resolved.body != null) + " lookup=" + current.status
                    + " assembler=" + M35FixtureLookup.blockState(resolved.level, resolved.body,
                            resolved.origin.above().north(2))
                    + " innerContraption=" + (controller instanceof MechanicalPistonBlockEntity piston
                            && piston.movedContraption != null)
                    + " pistonOffset=" + (controller instanceof MechanicalPistonBlockEntity piston
                            ? piston.offset : "unknown");
            tell(cleanup.observers, cleanup.level, "[M36] CLEANUP EXCEPTION " + details);
            Sable.LOGGER.error("SABLE_M36_CLEANUP_EXCEPTION {}", details, exception);
        } catch (final RuntimeException diagnosticFailure) {
            Sable.LOGGER.error("SABLE_M36_CLEANUP_EXCEPTION phase={} marker={} origin={}"
                    + " (state capture also failed)", cleanup.phase, cleanup.markerId,
                    fixture.origin.toShortString(), exception);
            Sable.LOGGER.error("SABLE_M36_CLEANUP_DIAGNOSTIC_FAILURE", diagnosticFailure);
            tell(cleanup.observers, cleanup.level, "[M36] CLEANUP EXCEPTION phase=" + cleanup.phase
                    + " exception=" + exception.getClass().getName() + " (see latest.log)");
        }
    }

    private static void removeKnownFixture(final Fixture fixture) {
        if (fixture.station != null) {
            final BlockPos station = fixture.station.blockPosition();
            final Direction outward = stationOutward(fixture);
            if (outward == null) {
                throw new IllegalStateException("M36 station orientation ambiguous");
            }
            final List<Block> expected = fixture.kind == Kind.ITEM
                    ? List.of(fixture.kind.actor(), Blocks.HOPPER, Blocks.CHEST)
                    : List.of(fixture.kind.actor(), AllBlocks.MECHANICAL_PUMP.get(),
                            AllBlocks.FLUID_TANK.get(), AllBlocks.COGWHEEL.get(),
                            AllBlocks.CREATIVE_MOTOR.get());
            final List<BlockPos> cells = stationCells(station, outward, fixture.kind);
            for (int i = 0; i < cells.size(); i++) {
                removeIf(fixture.level, cells.get(i), expected.get(i));
            }
            removeIf(fixture.level, station.above(), Blocks.REDSTONE_BLOCK);
            fixture.station.discard();
        }
        for (int x = -12; x <= -1; x++) {
            removeIf(fixture.level, fixture.origin.offset(x, 0, 0), AllBlocks.PISTON_EXTENSION_POLE.get());
        }
        removeIf(fixture.level, fixture.origin, AllBlocks.STICKY_MECHANICAL_PISTON.get());
        removeIf(fixture.level, fixture.origin.east(), AllBlocks.RADIAL_CHASSIS.get());
        removeIf(fixture.level, fixture.origin.east().above(), fixture.kind.actor());
        removeIf(fixture.level, fixture.origin.east().south(), fixture.kind.storage());
        removeIf(fixture.level, fixture.origin.below(2), AllBlocks.CREATIVE_MOTOR.get());
        removeIf(fixture.level, fixture.origin.below(), AllBlocks.CLUTCH.get());
        removeIf(fixture.level, fixture.origin.below().south(), Blocks.LEVER);
        removeIf(fixture.level, fixture.origin.north(), Blocks.IRON_BLOCK);
        removeIf(fixture.level, fixture.origin.north(2), Blocks.IRON_BLOCK);
        removeIf(fixture.level, fixture.origin.above().north(2),
                dev.simulated_team.simulated.index.SimulatedBlocks.PHYSICS_ASSEMBLER.get());
        for (final SuperGlueEntity glue : new ArrayList<>(fixture.level.getEntitiesOfClass(SuperGlueEntity.class,
                new AABB(fixture.origin.offset(0, -2, -2), fixture.origin.offset(2, 2, 2)).inflate(0.25),
                entity -> entity.getTags().contains("sable_m36_glue")))) {
            glue.remove(Entity.RemovalReason.KILLED);
        }
        fixture.marker.discard();
    }

    private static CreativeMotorBlockEntity fixtureMotor(final Fixture fixture) {
        final BlockEntity entity = M35FixtureLookup.blockEntity(fixture.level, fixture.body,
                fixture.origin.below(2));
        return entity instanceof final CreativeMotorBlockEntity motor ? motor : null;
    }

    private static void serverStopped(final ServerStoppedEvent event) {
        pending = null;
        RUNS.clear();
        CLEANUPS.clear();
    }

    private static void removeStatic(final ServerLevel level, final BlockPos origin, final Kind kind,
                                     final BlockPos station, final boolean rollback) {
        final Fixture current = fixture(level);
        if (!rollback && (current == null || current.body != null || !retracted(current)
                || !removeStation(current, station))) {
            throw new IllegalStateException("M36 static cleanup fingerprint failed");
        }
        if (rollback) {
            for (final BlockPos cell : stationCells(station, Direction.EAST, kind)) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        for (int x = -12; x <= -1; x++) {
            removeIf(level, origin.offset(x, 0, 0), AllBlocks.PISTON_EXTENSION_POLE.get());
        }
        removeIf(level, origin, AllBlocks.STICKY_MECHANICAL_PISTON.get());
        removeIf(level, origin.east(), AllBlocks.RADIAL_CHASSIS.get());
        removeIf(level, origin.east().above(), kind.actor());
        removeIf(level, origin.east().south(), kind.storage());
        removeIf(level, origin.below(2), AllBlocks.CREATIVE_MOTOR.get());
        removeIf(level, origin.below(), AllBlocks.CLUTCH.get());
        removeIf(level, origin.below().south(), Blocks.LEVER);
        removeIf(level, origin.north(), Blocks.IRON_BLOCK);
        removeIf(level, origin.north(2), Blocks.IRON_BLOCK);
        removeIf(level, origin.above().north(2),
                dev.simulated_team.simulated.index.SimulatedBlocks.PHYSICS_ASSEMBLER.get());
        for (final SuperGlueEntity glue : new ArrayList<>(level.getEntitiesOfClass(SuperGlueEntity.class,
                new AABB(origin.offset(0, -2, -2), origin.offset(2, 2, 2)).inflate(0.25),
                entity -> entity.getTags().contains("sable_m36_glue")))) {
            glue.remove(Entity.RemovalReason.KILLED);
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

    private record Fixture(ServerLevel level, ArmorStand marker, ArmorStand station, BlockPos origin,
                           ServerSubLevel body, Kind kind) {
    }

    enum LookupStatus {
        NONE, ACTIVE_STATIC, ACTIVE_OUTER, PARTIALLY_CLEANED, ORPHAN_MARKER, AMBIGUOUS, CORRUPT;

        boolean allowsSetup() {
            return this == NONE;
        }

        boolean allowsCleanup() {
            return this == ACTIVE_STATIC || this == ACTIVE_OUTER
                    || this == PARTIALLY_CLEANED || this == ORPHAN_MARKER;
        }

        CleanupPhase startsAt(final boolean retracted) {
            if (this == PARTIALLY_CLEANED || this == ORPHAN_MARKER) {
                return CleanupPhase.REMOVE;
            }
            return retracted ? CleanupPhase.INNER_DISASSEMBLED : CleanupPhase.DISCONNECT_WAIT;
        }
    }

    private record Lookup(LookupStatus status, Fixture fixture) {
    }

    enum CleanupPhase {
        STOP, DISCONNECT_WAIT, REVERSE, RETRACT_WAIT, INNER_DISASSEMBLED, OUTER_DISASSEMBLE, REMOVE
    }

    private static final class Run {
        final Fixture fixture;
        final List<UUID> observers;
        double yaw;
        Vec3 offset = Vec3.ZERO;
        int sourceBefore;
        int destinationBefore;
        int age;
        boolean connected;
        boolean actorObserved;
        boolean reported;
        boolean restarting;

        Run(final Fixture fixture, final double yaw, final List<UUID> observers) {
            this.fixture = fixture;
            this.yaw = yaw;
            this.observers = observers;
        }
    }

    private static final class Cleanup {
        final UUID markerId;
        final ServerLevel level;
        final List<UUID> observers;
        CleanupPhase phase = CleanupPhase.STOP;
        int age;
        int disconnectAge;

        Cleanup(final UUID markerId, final ServerLevel level, final List<UUID> observers) {
            this.markerId = markerId;
            this.level = level;
            this.observers = observers;
        }
    }

    enum Kind {
        ITEM, FLUID;

        Block actor() {
            return this == ITEM ? AllBlocks.PORTABLE_STORAGE_INTERFACE.get()
                    : AllBlocks.PORTABLE_FLUID_INTERFACE.get();
        }

        Block storage() {
            return this == ITEM ? Blocks.CHEST : AllBlocks.FLUID_TANK.get();
        }
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

    static List<ArmorStand> markers(final ServerLevel level, final String tag) {
        final List<ArmorStand> result = new ArrayList<>();
        for (final Entity entity : level.getAllEntities()) {
            if (entity instanceof final ArmorStand marker && marker.getTags().contains(tag)) {
                result.add(marker);
            }
        }
        return result;
    }

    static List<UUID> observers(final CommandSourceStack source) {
        return source.getEntity() instanceof final ServerPlayer player ? List.of(player.getUUID()) : List.of();
    }

    static void tell(final List<UUID> observers, final ServerLevel level, final String message) {
        Sable.LOGGER.info("SABLE_M36_FIXTURE {}", message);
        for (final UUID id : observers) {
            final ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                player.sendSystemMessage(Component.literal(message));
            }
        }
    }

    private static int refuse(final CommandSourceStack source, final String message) {
        tell(observers(source), source.getLevel(), "[M36] " + message);
        return 0;
    }
}
