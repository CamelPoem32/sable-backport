package dev.ryanhcode.sable.command;

import com.mojang.brigadier.CommandDispatcher;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption.MovementMode;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.contraptions.chassis.ChassisBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlockEntity;
import com.simibubi.create.content.contraptions.piston.PistonContraption;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.compatibility.create.seat.SableSeatPassengers;
import dev.ryanhcode.sable.forge.event.ForgeSablePostPhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Op-only runtime harness. Native Create owns assembly, mounting, motion and disassembly. */
public final class M39SeatFixtureCommands {
    private static final String TAG = "sable_m39_origin";
    private static final String READY = "sable_m39_ready_v1";
    private static final String GLUE = "sable_m39_glue";
    private static final String MOB = "sable_m39_mob";
    private static final Map<UUID, Task> TASKS = new HashMap<>();

    private M39SeatFixtureCommands() {
    }

    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(M39SeatFixtureCommands::tick);
        MinecraftForge.EVENT_BUS.addListener(M39SeatFixtureCommands::postPhysics);
        MinecraftForge.EVENT_BUS.addListener(M39SeatFixtureCommands::mountAttempt);
        MinecraftForge.EVENT_BUS.addListener(M39SeatFixtureCommands::stopped);
    }

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sable_m39").requires(source -> source.hasPermission(2))
                .then(Commands.literal("player").executes(context -> setup(context.getSource(), false)))
                .then(Commands.literal("mob").executes(context -> setup(context.getSource(), true)))
                .then(Commands.literal("run").executes(context -> run(context.getSource())))
                .then(Commands.literal("yaw90").executes(context -> yaw(context.getSource())))
                .then(Commands.literal("dismount").executes(context -> dismount(context.getSource())))
                .then(Commands.literal("cleanup").executes(context -> cleanup(context.getSource()))));
    }

    private static int setup(final CommandSourceStack source, final boolean mob) {
        final ServerLevel level = source.getLevel();
        final Lookup previous = lookup(level);
        if (previous.status != Status.NONE) return reject(source, "SETUP REFUSED ownership=" + previous.status
                + "; run /function sable:m39/cleanup");
        BlockPos chosen = null;
        final BlockPos preferred = BlockPos.containing(source.getPosition()).above(6);
        for (final BlockPos offset : M38DispenserFixturePlacement.candidateOffsets()) {
            final BlockPos candidate = preferred.offset(offset);
            boolean clear = true;
            // Reserve the passenger envelope too, including the yawed path. Never generate chunks.
            for (final BlockPos cell : BlockPos.betweenClosed(candidate.offset(-14, -4, -15), candidate.offset(15, 4, 6))) {
                if (!level.getChunkSource().hasChunk(cell.getX() >> 4, cell.getZ() >> 4)
                        || Sable.HELPER.getContaining(level, cell) != null || !level.getBlockState(cell).isAir()) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                chosen = candidate;
                break;
            }
        }
        if (chosen == null) return reject(source, "SETUP REFUSED: no loaded clear nearby passenger/platform footprint");
        final BlockPos origin = chosen;
        final ArmorStand marker = new ArmorStand(level, origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        final CompoundTag nbt = new CompoundTag();
        nbt.putBoolean("Marker", true);
        nbt.putBoolean("Invisible", true);
        nbt.putBoolean("NoGravity", true);
        marker.load(nbt);
        marker.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        marker.addTag(TAG);
        marker.getPersistentData().put("M39Origin", NbtUtils.writeBlockPos(origin));
        marker.getPersistentData().putBoolean("M39Mob", mob);
        marker.getPersistentData().putString("M39Dimension", level.dimension().location().toString());
        if (!level.addFreshEntity(marker) || level.getEntity(marker.getUUID()) != marker
                || !marker.blockPosition().equals(origin)) {
            marker.discard();
            return reject(source, "SETUP FAILED: marker unavailable before BUILD");
        }
        final List<BlockPos> placed = new ArrayList<>();
        SuperGlueEntity glue = null;
        tell(level, "SETUP phase=BUILD origin=" + origin.toShortString());
        try {
            for (final var cell : M39SeatFixtureLayout.all().entrySet()) {
                final BlockPos pos = origin.offset(cell.getKey());
                if (!level.getBlockState(pos).isAir()) throw new IllegalStateException("reservation changed " + pos);
                placed.add(pos);
                final var state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), cell.getValue(), false).blockState();
                if (!level.setBlock(pos, state, 3)) throw new IllegalStateException("placement failed " + pos);
            }
            final var motor = (CreativeMotorBlockEntity) level.getBlockEntity(origin.below(2));
            motor.generatedSpeed.setValue(-64);
            tell(level, "SETUP phase=VALIDATE");
            if (!(level.getBlockEntity(origin) instanceof MechanicalPistonBlockEntity)
                    || !(level.getBlockEntity(origin.above().north(2)) instanceof PhysicsAssemblerBlockEntity)
                    || !level.getBlockState(origin.north(2)).is(Blocks.IRON_BLOCK)) {
                throw new IllegalStateException("controller/assembler starting block invalid");
            }
            final ChassisBlockEntity chassis = (ChassisBlockEntity) level.getBlockEntity(origin.east());
            final List<BlockPos> included = chassis.getIncludedBlockPositions(Direction.EAST, false);
            if (included == null || !included.contains(origin.east().above()) || !included.contains(origin.east().south())) {
                throw new IllegalStateException("native chassis inclusion invalid " + included);
            }
            glue = new SuperGlueEntity(level, M38DispenserFixtureLayout.outerGlueBounds(origin));
            glue.addTag(GLUE);
            if (!level.addFreshEntity(glue) || !glue.contains(origin.north(2)) || glue.contains(origin.east())) {
                throw new IllegalStateException("outer glue invalid");
            }
            final SimAssemblyContraption outer = new SimAssemblyContraption(null);
            if (!outer.searchMovedStructure(level, origin.north(2)) || outer.getBlocks().size() != 22
                    || M39SeatFixtureLayout.platform().keySet().stream().anyMatch(p -> outer.getBlocks().contains(origin.offset(p)))) {
                throw new IllegalStateException("outer selection includes platform or misses payload " + outer.getBlocks().size());
            }
            marker.addTag(READY);
            if (lookup(level).status != Status.STATIC) throw new IllegalStateException("post-build ownership invalid");
            tell(level, "SETUP phase=READY outerSelectedBlocks=22 seatNativeAttachment=true platformParentWorld=true");
            tell(level, "READY: click Physics Assembler, /function sable:m39/run, then right-click the visible moving Seat");
            return 1;
        } catch (final Exception exception) {
            marker.removeTag(READY);
            Sable.LOGGER.error("SABLE_M39_SETUP_EXCEPTION origin={}", origin, exception);
            tell(level, "SETUP FAILED reason=" + exception.getMessage() + " phase=ROLLBACK");
            if (glue != null) glue.discard();
            for (final BlockPos pos : placed) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            final boolean clear = placed.stream().allMatch(p -> level.getBlockState(p).isAir());
            if (clear) marker.discard();
            tell(level, clear ? "SETUP ROLLBACK COMPLETE" : "SETUP ROLLBACK INCOMPLETE; ownership retained");
            return 0;
        }
    }

    private static int run(final CommandSourceStack source) {
        final Lookup found = lookup(source.getLevel());
        if (found.status != Status.ASSEMBLED || !found.fixture.marker.getTags().contains(READY)
                || !restored(found.fixture) || TASKS.containsKey(found.fixture.marker.getUUID())) {
            return reject(source, "RUN REFUSED: unique READY assembled/retracted Seat fixture required ownership=" + found.status);
        }
        final Fixture fixture = found.fixture;
        hold(fixture);
        TASKS.put(fixture.marker.getUUID(), new Task(fixture.level, fixture.marker.getUUID(), Phase.ASSEMBLING));
        movementMode(fixture, MovementMode.MOVE_NEVER_PLACE);
        drive(fixture, -64, false);
        tell(fixture.level, "RUN phase=WAIT_FOR_NATIVE_SEAT_CONTRAPTION");
        return 1;
    }

    private static int yaw(final CommandSourceStack source) {
        final Lookup found = lookup(source.getLevel());
        if (found.status != Status.ASSEMBLED) return reject(source, "YAW90 REFUSED ownership=" + found.status);
        final Fixture fixture = found.fixture;
        fixture.marker.getPersistentData().putBoolean("M39Yaw90", true);
        hold(fixture);
        final AbstractContraptionEntity vehicle = inner(fixture);
        if (vehicle != null) for (final Entity passenger : vehicle.getPassengers()) vehicle.positionRider(passenger);
        tell(fixture.level, "YAW90 COMPLETE yaw=90 level=true nativeRidingRelationPreserved=true");
        return 1;
    }

    private static int dismount(final CommandSourceStack source) {
        final Lookup found = lookup(source.getLevel());
        if (found.fixture == null) return reject(source, "DISMOUNT REFUSED ownership=" + found.status);
        final AbstractContraptionEntity vehicle = inner(found.fixture);
        if (vehicle != null) {
            SableSeatPassengers.detach(vehicle);
            final Task task = TASKS.get(found.fixture.marker.getUUID());
            if (task != null && !task.cleaning) task.phase = Phase.RETURN;
            movementMode(found.fixture, MovementMode.MOVE_PLACE_RETURNED);
            drive(found.fixture, 64, false);
        }
        tell(found.fixture.level, "DISMOUNT complete remainingPassengers=" + (vehicle == null ? 0 : vehicle.getPassengers().size())
                + "; native parent-world safe-location search applied; piston retracting");
        return 1;
    }

    private static int cleanup(final CommandSourceStack source) {
        final Lookup found = lookup(source.getLevel());
        if (found.status == Status.NONE) {
            tell(source.getLevel(), "CLEANUP COMPLETE alreadyClean=true");
            return 1;
        }
        if (found.fixture == null || found.status == Status.AMBIGUOUS) return reject(source, "CLEANUP REFUSED ownership=" + found.status);
        final Fixture fixture = found.fixture;
        final Task existing = TASKS.get(fixture.marker.getUUID());
        if (existing != null && existing.cleaning) {
            tell(source.getLevel(), "CLEANUP already in progress");
            return 1;
        }
        final Task task = new Task(fixture.level, fixture.marker.getUUID(), Phase.STOP);
        task.cleaning = true;
        TASKS.put(task.marker, task);
        tell(fixture.level, "CLEANUP phase=STOP");
        return 1;
    }

    /** The same persistent lookup is used by setup, run, yaw, dismount and cleanup. */
    private static Lookup lookup(final ServerLevel level) {
        final List<ArmorStand> markers = new ArrayList<>();
        for (final Entity entity : level.getAllEntities()) {
            if (entity instanceof ArmorStand stand && stand.getTags().contains(TAG)) markers.add(stand);
        }
        if (markers.isEmpty()) return new Lookup(Status.NONE, null);
        if (markers.size() != 1) return new Lookup(Status.AMBIGUOUS, null);
        final ArmorStand marker = markers.get(0);
        final BlockPos visible = NbtUtils.readBlockPos(marker.getPersistentData().getCompound("M39Origin"));
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        Fixture match = null;
        boolean persistedBodyPresent = false;
        if (container != null) {
            for (final ServerSubLevel body : container.getAllSubLevels()) {
                final boolean known = marker.getPersistentData().hasUUID("M39Body");
                if (known && !marker.getPersistentData().getUUID("M39Body").equals(body.getUniqueId())) continue;
                if (known) persistedBodyPresent = true;
                for (final BlockPos pos : SimAssemblyHelper.collectBlocks(level, body)) {
                    if (!M35FixtureLookup.blockState(level, body, pos).is(AllBlocks.STICKY_MECHANICAL_PISTON.get())) continue;
                    if (!known && body.logicalPose().transformPosition(Vec3.atCenterOf(pos)).distanceToSqr(Vec3.atCenterOf(visible)) > 32 * 32) continue;
                    if (!(M35FixtureLookup.blockEntity(level, body, pos.below(2)) instanceof CreativeMotorBlockEntity)
                            || !(M35FixtureLookup.blockEntity(level, body, pos.above().north(2)) instanceof PhysicsAssemblerBlockEntity)) continue;
                    final Fixture candidate = new Fixture(level, marker, visible, pos, body);
                    final AbstractContraptionEntity moving = inner(candidate);
                    if (!(M35FixtureLookup.blockState(level, body, pos.east().above()).getBlock() instanceof SeatBlock)
                            && (moving == null || moving.getContraption().getSeats().size() != 1) && !known) continue;
                    if (match != null) return new Lookup(Status.AMBIGUOUS, null);
                    match = candidate;
                }
            }
        }
        if (match != null) {
            marker.getPersistentData().putUUID("M39Body", match.body.getUniqueId());
            return new Lookup(Status.ASSEMBLED, match);
        }
        if (persistedBodyPresent) return new Lookup(Status.AMBIGUOUS, null);
        final Fixture fixture = new Fixture(level, marker, visible, visible, null);
        final boolean empty = M39SeatFixtureLayout.all().keySet().stream().allMatch(p -> level.getBlockState(visible.offset(p)).isAir());
        return new Lookup(empty ? Status.ORPHAN : marker.getTags().contains(READY) ? Status.STATIC : Status.PARTIAL, fixture);
    }

    private static AbstractContraptionEntity inner(final Fixture fixture) {
        final Task task = TASKS.get(fixture.marker.getUUID());
        return resolveVehicle(fixture, task == null ? null : task.vehicle).vehicle;
    }

    private static VehicleLookup resolveVehicle(final Fixture fixture, final UUID expected) {
        final MechanicalPistonBlockEntity piston = piston(fixture);
        final AbstractContraptionEntity controller = piston == null ? null : piston.movedContraption;
        final Entity parent = expected == null ? null : fixture.level.getEntity(expected);
        // A ServerSubLevel is not a Minecraft Level; both getters address the parent entity manager.
        final Entity subLevel = expected == null || fixture.body == null ? null : fixture.body.getLevel().getEntity(expected);
        final Map<UUID, AbstractContraptionEntity> candidates = new LinkedHashMap<>();
        if (controller != null) candidates.put(controller.getUUID(), controller);
        if (parent instanceof AbstractContraptionEntity vehicle) candidates.put(vehicle.getUUID(), vehicle);
        if (subLevel instanceof AbstractContraptionEntity vehicle) candidates.put(vehicle.getUUID(), vehicle);
        for (final Entity candidate : fixture.level.getAllEntities()) {
            if (candidate instanceof ControlledContraptionEntity vehicle
                    && fixture.raw.equals(SableCreateContraptionContext.getControllerPos(vehicle))) {
                candidates.put(vehicle.getUUID(), vehicle);
            }
        }
        final List<M39SeatFixtureVehicleIdentity.Candidate> identities = new ArrayList<>();
        for (final AbstractContraptionEntity candidate : candidates.values()) {
            final boolean nativePiston = candidate.getContraption() instanceof PistonContraption;
            final BlockPos seat = nativePiston ? ((PistonContraption) candidate.getContraption()).toLocalPos(fixture.raw.east().above()) : null;
            final boolean expectedSeat = nativePiston && candidate.getContraption().getSeats().size() == 1
                    && candidate.getContraption().getSeats().contains(seat)
                    && candidate.getContraption().getBlocks().get(seat) != null
                    && candidate.getContraption().getBlocks().get(seat).state().is(AllBlocks.SEATS.get(net.minecraft.world.item.DyeColor.RED).get());
            boolean visiblePoseMatches = false;
            if (expectedSeat && !candidate.isRemoved() && piston != null) {
                final Vec3 rawSeat = candidate.toGlobalVector(Vec3.atCenterOf(seat), 1);
                final Vec3 expectedSeatPosition = Vec3.atCenterOf(fixture.raw.east().above()).add(piston.offset, 0, 0);
                final Vec3 visibleSeat = fixture.body == null ? rawSeat : fixture.body.logicalPose().transformPosition(rawSeat);
                final Vec3 expectedVisible = fixture.body == null ? expectedSeatPosition : fixture.body.logicalPose().transformPosition(expectedSeatPosition);
                visiblePoseMatches = visibleSeat.distanceToSqr(expectedVisible) <= 4;
            }
            identities.add(new M39SeatFixtureVehicleIdentity.Candidate(candidate.getUUID(),
                    candidate instanceof ControlledContraptionEntity && nativePiston, candidate.isAlive(), candidate.isRemoved(),
                    candidate.level() == fixture.level,
                    SableCreateContraptionContext.getContainingSubLevel(candidate) == fixture.body,
                    fixture.raw.equals(SableCreateContraptionContext.getControllerPos(candidate)), expectedSeat, visiblePoseMatches));
        }
        final var selected = M39SeatFixtureVehicleIdentity.select(expected, identities);
        if (selected.reason().equals("AMBIGUOUS_CONTROLLER")) throw new IllegalStateException("ambiguous native Seat controllers");
        final AbstractContraptionEntity vehicle = selected.uuid() == null ? null : candidates.get(selected.uuid());
        final AbstractContraptionEntity observed = expected == null ? controller : candidates.get(expected);
        return new VehicleLookup(vehicle, parent, subLevel, controller, observed, selected.reason());
    }

    private static MechanicalPistonBlockEntity piston(final Fixture fixture) {
        return M35FixtureLookup.blockEntity(fixture.level, fixture.body, fixture.raw)
                instanceof MechanicalPistonBlockEntity piston ? piston : null;
    }

    private static boolean restored(final Fixture fixture) {
        final MechanicalPistonBlockEntity piston = piston(fixture);
        return piston != null && !piston.running && piston.offset <= 0 && inner(fixture) == null;
    }

    private static void drive(final Fixture fixture, final int speed, final boolean stopped) {
        final BlockEntity entity = M35FixtureLookup.blockEntity(fixture.level, fixture.body, fixture.raw.below(2));
        if (!(entity instanceof CreativeMotorBlockEntity motor)) throw new IllegalStateException("fixture motor missing");
        motor.generatedSpeed.setValue(speed);
        M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body, fixture.raw, stopped);
    }

    private static void movementMode(final Fixture fixture, final MovementMode mode) {
        final MechanicalPistonBlockEntity piston = piston(fixture);
        if (piston == null) throw new IllegalStateException("fixture piston unavailable for native movement mode");
        final var behaviour = piston.getBehaviour(ScrollValueBehaviour.TYPE);
        if (!(behaviour instanceof ScrollOptionBehaviour<?> option) || !(option.get() instanceof MovementMode)) {
            throw new IllegalStateException("native piston movement mode unavailable");
        }
        option.setValue(mode.ordinal());
        if (option.get() != mode) throw new IllegalStateException("native piston movement mode did not change");
    }

    private static void tick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (final Task task : List.copyOf(TASKS.values())) {
            if (task.level.getServer() != event.getServer()) continue;
            try {
                advance(task);
            } catch (final RuntimeException exception) {
                TASKS.remove(task.marker);
                Sable.LOGGER.error("SABLE_M39_FIXTURE_EXCEPTION phase={} marker={}", task.phase, task.marker, exception);
                tell(task.level, (task.cleaning ? "CLEANUP" : "RUN") + " FAILED reason=" + exception.getMessage() + "; cleanup remains retryable");
            }
        }
    }

    private static void advance(final Task task) {
        final Lookup found = lookup(task.level);
        if (found.fixture == null || !found.fixture.marker.getUUID().equals(task.marker)) throw new IllegalStateException("fixture ownership unavailable " + found.status);
        final Fixture fixture = found.fixture;
        if (M39SeatFixtureVehicleIdentity.lifecycleTimedOut(++task.age, task.phase == Phase.BOARDING, task.cleaning)) {
            throw new IllegalStateException("bounded native lifecycle timeout");
        }
        final VehicleLookup resolved = resolveVehicle(fixture, task.vehicle);
        final AbstractContraptionEntity vehicle = resolved.vehicle;
        if (task.vehicle != null) {
            if (vehicle != null) {
                task.lastEntityId = vehicle.getId();
                task.lastLevel = levelIdentity(vehicle.level());
                task.lastAlive = vehicle.isAlive();
                task.lastRemoved = vehicle.isRemoved();
                task.lastRemovalReason = String.valueOf(vehicle.getRemovalReason());
            } else if (resolved.observed != null) {
                task.lastAlive = resolved.observed.isAlive();
                task.lastRemoved = resolved.observed.isRemoved();
                task.lastRemovalReason = String.valueOf(resolved.observed.getRemovalReason());
            }
        }
        if (task.cleaning) {
            switch (task.phase) {
                case STOP -> {
                    if (vehicle != null) SableSeatPassengers.detach(vehicle);
                    if (piston(fixture) != null) {
                        movementMode(fixture, MovementMode.MOVE_PLACE_RETURNED);
                        drive(fixture, 64, true);
                    }
                    task.phase = Phase.RETRACT;
                }
                case RETRACT -> {
                    if (vehicle == null && (piston(fixture) == null || restored(fixture))) {
                        if (piston(fixture) != null) drive(fixture, 64, true);
                        task.phase = Phase.OUTER;
                        tell(task.level, "CLEANUP phase=INNER_DISASSEMBLED");
                    } else if (!task.reversed) {
                        drive(fixture, 64, false);
                        task.reversed = true;
                        tell(task.level, "CLEANUP phase=RETRACT_WAIT");
                    }
                }
                case OUTER -> {
                    if (fixture.body != null) {
                        fixture.marker.getPersistentData().putBoolean("M39Yaw90", false);
                        hold(fixture);
                        final BlockPos rawAssembler = fixture.raw.above().north(2);
                        for (final BlockPos raw : SimAssemblyHelper.collectBlocks(fixture.level, fixture.body)) {
                            final BlockPos target = fixture.visible.offset(raw.subtract(fixture.raw));
                            if (!fixture.level.getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4)
                                    || !fixture.level.getBlockState(target).isAir()) throw new IllegalStateException("unrelated restore obstruction " + target + " state=" + fixture.level.getBlockState(target));
                        }
                        final var assembler = M35FixtureLookup.blockEntity(fixture.level, fixture.body, rawAssembler);
                        if (!(assembler instanceof PhysicsAssemblerBlockEntity controller)) throw new IllegalStateException("outer assembler missing");
                        tell(task.level, "CLEANUP phase=OUTER_DISASSEMBLE");
                        controller.assembleOrDisassemble(null);
                        return; // Re-resolve the body on the next tick; do not dereference a deleted plot.
                    }
                    task.phase = Phase.REMOVE;
                }
                case REMOVE -> {
                    if (fixture.body != null || vehicle != null) throw new IllegalStateException("active vehicle not removed");
                    for (final var cell : M39SeatFixtureLayout.all().entrySet()) {
                        final BlockPos target = fixture.visible.offset(cell.getKey());
                        final var state = fixture.level.getBlockState(target);
                        if (!state.isAir() && !BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(M38DispenserFixtureLayout.blockId(cell.getValue()))) {
                            throw new IllegalStateException("unrelated footprint block preserved " + target + " " + state);
                        }
                    }
                    for (final BlockPos relative : M39SeatFixtureLayout.all().keySet()) fixture.level.setBlock(fixture.visible.offset(relative), Blocks.AIR.defaultBlockState(), 3);
                    final AABB bounds = M38DispenserFixtureLayout.outerGlueBounds(fixture.visible);
                    for (final SuperGlueEntity glue : fixture.level.getEntitiesOfClass(SuperGlueEntity.class, bounds.inflate(.01))) {
                        if (glue.getBoundingBox().equals(bounds)) glue.discard();
                    }
                    for (final Entity entity : fixture.level.getAllEntities()) {
                        if (entity.getTags().contains(MOB) && entity.getPersistentData().hasUUID("M39Owner")
                                && entity.getPersistentData().getUUID("M39Owner").equals(task.marker)) {
                            if (entity.isPassenger()) throw new IllegalStateException("fixture mob still riding");
                            entity.discard();
                        }
                    }
                    final boolean clear = M39SeatFixtureLayout.all().keySet().stream().allMatch(p -> fixture.level.getBlockState(fixture.visible.offset(p)).isAir());
                    if (!clear) throw new IllegalStateException("post-cleanup footprint not clear");
                    fixture.marker.discard();
                    TASKS.remove(task.marker);
                    tell(task.level, "CLEANUP COMPLETE passengersDetached=true stabilizerRemoved=true");
                }
                default -> throw new IllegalStateException("unexpected cleanup phase " + task.phase);
            }
            return;
        }
        switch (task.phase) {
            case ASSEMBLING -> {
                if (vehicle == null) return;
                if (vehicle.getContraption().getSeats().size() != 1) throw new IllegalStateException("native Seat not captured");
                task.vehicle = vehicle.getUUID();
                task.lastEntityId = vehicle.getId();
                task.lastLevel = levelIdentity(vehicle.level());
                task.capturedAt = fixture.level.getGameTime();
                task.lastAlive = vehicle.isAlive();
                task.lastRemoved = vehicle.isRemoved();
                task.lastRemovalReason = String.valueOf(vehicle.getRemovalReason());
                tell(task.level, "VEHICLE_DISCOVERY uuid=" + task.vehicle + " entityId=" + vehicle.getId()
                        + " entityClass=" + vehicle.getClass().getName() + " entityLevel=" + task.lastLevel
                        + " parentLevelSame=" + (vehicle.level() == fixture.level)
                        + " subLevelParentLevelSame=" + (fixture.body != null && vehicle.level() == fixture.body.getLevel())
                        + " sableSubLevelIsMinecraftLevel=false isAlive=" + vehicle.isAlive()
                        + " isRemoved=" + vehicle.isRemoved() + " removalReason=" + vehicle.getRemovalReason()
                        + " subLevel=" + (fixture.body == null ? null : fixture.body.getUniqueId())
                        + " discoveryPath=" + (resolved.controller == vehicle ? "PISTON_CONTROLLER_REFERENCE" : "PARENT_MANAGER_CONTROLLER_SCAN")
                        + " parentLevelGetEntity=" + describe(fixture.level.getEntity(vehicle.getUUID()))
                        + " decision=" + resolved.reason + " gameTime=" + task.capturedAt);
                movementMode(fixture, MovementMode.MOVE_NEVER_PLACE);
                drive(fixture, -64, true);
                task.phase = Phase.BOARDING;
                tell(task.level, "SEAT_CAPTURED seats=1 vehicle=" + vehicle.getUUID() + "; right-click visible Seat to start travel");
                tell(task.level, "RUN phase=BOARDING_WAIT_FOR_PLAYER movementMode=MOVE_NEVER_PLACE");
                if (fixture.marker.getPersistentData().getBoolean("M39Mob")) {
                    final Pig pig = EntityType.PIG.create(fixture.level);
                    if (pig == null) throw new IllegalStateException("pig creation failed");
                    pig.setNoAi(true);
                    pig.setPos(Vec3.atCenterOf(fixture.visible.offset(1, 1, 3)));
                    pig.addTag(MOB);
                    pig.getPersistentData().putUUID("M39Owner", task.marker);
                    if (!fixture.level.addFreshEntity(pig)) throw new IllegalStateException("pig not added");
                    vehicle.addSittingPassenger(pig, 0);
                }
            }
            case BOARDING -> {
                if (vehicle == null || task.level.getGameTime() - task.lastLookupAt >= 100 || !task.lookupPrinted) {
                    traceVehicleLookup(task, fixture, resolved, vehicle == null ? "VEHICLE_LOST" : "VEHICLE_LOOKUP");
                    task.lastLookupAt = task.level.getGameTime();
                    task.lookupPrinted = true;
                }
                if (vehicle == null) throw new IllegalStateException("Seat vehicle lost reason=" + resolved.reason + " uuid=" + task.vehicle);
                if (vehicle.getPassengers().isEmpty()) return;
                final Entity rider = vehicle.getFirstPassenger();
                if (!SableSeatPassengers.parentPassenger(vehicle, rider)) throw new IllegalStateException("passenger is not parent-world LivingEntity");
                task.passenger = rider.getUUID();
                task.phase = Phase.EXTEND;
                task.age = 0;
                drive(fixture, -64, false);
                tell(task.level, "MOUNT_SUCCESS passenger=" + rider.getUUID() + " nativeRelation=true parentWorldEntity=true");
            }
            case EXTEND, HOLD, RETURN -> {
                final Entity rider = task.passenger == null ? null : task.level.getEntity(task.passenger);
                if (vehicle != null && !vehicle.isRemoved() && (rider == null || rider.getVehicle() != vehicle)) task.leftBeforeReturn = true;
                if (rider != null && vehicle != null && rider.getVehicle() == vehicle) {
                    final Vec3 nativePhysical = vehicle.getPassengerPosition(rider, 1);
                    if (nativePhysical == null) throw new IllegalStateException("mapped passenger position unavailable");
                    final Vec3 expected = nativePhysical.add(0, SeatEntity.getCustomEntitySeatOffset(rider) - .125, 0);
                    final double error = rider.position().distanceTo(expected);
                    task.maxError = Math.max(task.maxError, error);
                    task.samples++;
                    task.hidden |= Sable.HELPER.getContaining(rider) != null;
                    task.badVelocity |= !Double.isFinite(rider.getDeltaMovement().lengthSqr()) || rider.getDeltaMovement().lengthSqr() > 64;
                    if (task.samples % 40 == 0) tell(task.level, "PASSENGER_UPDATE positionError=" + error + " maxError=" + task.maxError + " hiddenPlotPosition=" + task.hidden);
                }
                final MechanicalPistonBlockEntity piston = piston(fixture);
                if (piston == null) throw new IllegalStateException("native piston missing");
                if (task.phase == Phase.EXTEND && piston.offset >= 11) {
                    task.maxOffset = piston.offset;
                    drive(fixture, -64, true);
                    task.phase = Phase.HOLD;
                    task.holdTicks = 0;
                    tell(task.level, "RUN phase=FULL_TEST_EXTENSION; yaw90 or ordinary sneak dismount may be tested now");
                } else if (task.phase == Phase.HOLD && ++task.holdTicks >= 160) {
                    if (!M39SeatFixtureVehicleIdentity.canBeginReturnedPlacement(piston.offset)) {
                        throw new IllegalStateException("return-mode handoff before native extension");
                    }
                    movementMode(fixture, MovementMode.MOVE_PLACE_RETURNED);
                    drive(fixture, 64, false);
                    task.phase = Phase.RETURN;
                } else if (task.phase == Phase.RETURN && restored(fixture)) {
                    drive(fixture, 64, true);
                    final boolean parent = rider != null && rider.level() == fixture.level && Sable.HELPER.getContaining(rider) == null;
                    final boolean dismounted = rider != null && !rider.isPassenger();
                    final boolean safe = rider instanceof LivingEntity living && fixture.level.noCollision(living, living.getBoundingBox())
                            && !fixture.level.getBlockState(living.blockPosition().below()).getCollisionShape(fixture.level, living.blockPosition().below()).isEmpty();
                    final boolean pass = task.samples > 10 && task.maxOffset >= 11 && task.maxError <= .25 && !task.hidden && !task.badVelocity
                            && !task.leftBeforeReturn && parent && rider.isAlive() && dismounted && safe;
                    tell(task.level, (fixture.marker.getPersistentData().getBoolean("M39Mob") ? "MOB" : "PLAYER")
                            + " seatCaptured=true playerMounted=" + (task.samples > 0) + " vehicleCorrect=" + (task.samples > 0)
                            + " parentWorldEntity=" + parent + " seatPositionError=" + task.maxError
                            + " hiddenPlotPosition=" + task.hidden + " velocityValid=" + !task.badVelocity
                            + " fullRideObserved=" + !task.leftBeforeReturn
                            + " dismountPositionSafe=" + safe + " vehicleNull=" + dismounted + " " + (pass ? "PASS" : "INCOMPLETE"));
                    TASKS.remove(task.marker);
                }
            }
            default -> throw new IllegalStateException("unexpected run phase " + task.phase);
        }
    }

    private static void traceVehicleLookup(final Task task, final Fixture fixture, final VehicleLookup resolved, final String stage) {
        tell(task.level, stage + " uuid=" + task.vehicle + " lastEntityId=" + task.lastEntityId
                + " lastLevel=" + task.lastLevel + " parentLevelGetEntity=" + describe(resolved.parent)
                + " subLevelGetEntity=" + describe(resolved.subLevel) + " inclusiveLookup=" + describe(resolved.vehicle)
                + " controllerReference=" + describe(resolved.controller)
                + " sameInstance=" + (resolved.vehicle != null && resolved.vehicle == resolved.controller)
                + " isAlive=" + (resolved.observed == null ? "unknown(last=" + task.lastAlive + ")" : resolved.observed.isAlive())
                + " isRemoved=" + (resolved.observed == null ? "unknown(last=" + task.lastRemoved + ")" : resolved.observed.isRemoved())
                + " removalReason=" + (resolved.observed == null ? "unknown(last=" + task.lastRemovalReason + ")" : resolved.observed.getRemovalReason())
                + " decision=" + resolved.reason
                + " ticksSinceCapture=" + (task.level.getGameTime() - task.capturedAt) + " gameTime=" + task.level.getGameTime()
                + " phase=" + task.phase + " pistonOffset=" + (piston(fixture) == null ? "unknown" : piston(fixture).offset));
    }

    private static String describe(final Entity entity) {
        return entity == null ? "null" : entity.getUUID() + "/" + entity.getId() + "/" + entity.getClass().getSimpleName();
    }

    private static String levelIdentity(final net.minecraft.world.level.Level level) {
        return level.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(level));
    }

    private static void hold(final Fixture fixture) {
        if (fixture.body == null) return;
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(fixture.level);
        final RigidBodyHandle handle = physics == null ? null : physics.getPhysicsHandle(fixture.body);
        if (handle == null || !handle.isValid()) throw new IllegalStateException("tagged fixture physics handle unavailable");
        final Quaterniond rotation = new Quaterniond().rotationY(fixture.marker.getPersistentData().getBoolean("M39Yaw90") ? Math.PI / 2 : 0);
        final Vec3 rawCenter = Vec3.atCenterOf(fixture.raw);
        final Vector3d local = new Vector3d(rawCenter.x, rawCenter.y, rawCenter.z)
                .sub(fixture.body.logicalPose().rotationPoint()).mul(fixture.body.logicalPose().scale());
        final Vec3 anchor = Vec3.atCenterOf(fixture.visible);
        handle.teleport(new Vector3d(anchor.x, anchor.y, anchor.z).sub(rotation.transform(local)), rotation);
        handle.setLinearAndAngularVelocity(new Vector3d(), new Vector3d());
        physics.updatePose(fixture.body);
        fixture.body.updateBoundingBox();
        fixture.body.updateLastPose();
    }

    private static void postPhysics(final ForgeSablePostPhysicsTickEvent event) {
        final Lookup found = lookup(event.getPhysicsSystem().getLevel());
        if (found.status == Status.ASSEMBLED && found.fixture.marker.getTags().contains(READY)) {
            final RigidBodyHandle handle = event.getPhysicsSystem().getPhysicsHandle(found.fixture.body);
            if (handle != null && handle.isValid()) hold(found.fixture);
        }
    }

    private static void stopped(final ServerStoppedEvent event) {
        TASKS.entrySet().removeIf(entry -> entry.getValue().level.getServer() == event.getServer());
    }

    private static void mountAttempt(final EntityMountEvent event) {
        if (!event.isMounting() || event.getLevel().isClientSide) return;
        final Entity vehicle = event.getEntityBeingMounted();
        if (vehicle == null) return;
        for (final Task task : TASKS.values()) {
            if (!task.cleaning && task.phase == Phase.BOARDING && task.level == event.getLevel()
                    && vehicle.getUUID().equals(task.vehicle)) {
                tell(task.level, "MOUNT_ATTEMPT passenger=" + event.getEntityMounting().getUUID()
                        + " vehicle=" + task.vehicle + " gameTime=" + task.level.getGameTime()
                        + " observation=NATIVE_FORGE_MOUNT_EVENT");
            }
        }
    }

    private static void tell(final ServerLevel level, final String message) {
        Sable.LOGGER.info("[M39] {}", message);
        for (final var player : level.players()) player.sendSystemMessage(Component.literal("[M39] " + message));
    }

    private static int reject(final CommandSourceStack source, final String message) {
        source.sendFailure(Component.literal("[M39] " + message));
        return 0;
    }

    private enum Status { NONE, STATIC, ASSEMBLED, PARTIAL, ORPHAN, AMBIGUOUS }
    private enum Phase { ASSEMBLING, BOARDING, EXTEND, HOLD, RETURN, STOP, RETRACT, OUTER, REMOVE }
    private record Lookup(Status status, Fixture fixture) { }
    private record Fixture(ServerLevel level, ArmorStand marker, BlockPos visible, BlockPos raw, ServerSubLevel body) { }
    private record VehicleLookup(AbstractContraptionEntity vehicle, Entity parent, Entity subLevel,
                                 AbstractContraptionEntity controller, AbstractContraptionEntity observed, String reason) { }

    private static final class Task {
        final ServerLevel level;
        final UUID marker;
        Phase phase;
        UUID passenger;
        UUID vehicle;
        int lastEntityId;
        String lastLevel, lastRemovalReason;
        long capturedAt, lastLookupAt;
        boolean lastAlive, lastRemoved, lookupPrinted;
        int age, samples, holdTicks;
        double maxError, maxOffset;
        boolean cleaning, reversed, hidden, badVelocity, leftBeforeReturn;

        Task(final ServerLevel level, final UUID marker, final Phase phase) {
            this.level = level;
            this.marker = marker;
            this.phase = phase;
        }
    }
}
