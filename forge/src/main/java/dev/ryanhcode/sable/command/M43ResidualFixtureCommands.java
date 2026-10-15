package dev.ryanhcode.sable.command;

import com.mojang.brigadier.CommandDispatcher;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption.MovementMode;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlockEntity;
import com.simibubi.create.content.contraptions.piston.PistonContraption;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.compatibility.create.residuals.ResidualActorGeometry;
import dev.ryanhcode.sable.compatibility.create.residuals.SableActorEntityEvidence;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fixture-only orchestration. Never calls damageEntities/throwEntity/hurt; every witness comes from native actors. */
public final class M43ResidualFixtureCommands {
    static final String TAG = "sable_m43_origin";
    private static final String READY = "sable_m43_ready_v1";
    private static final String OWNER = "M43Owner";
    private static final Map<UUID, Task> TASKS = new HashMap<>();
    private M43ResidualFixtureCommands() { }

    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(M43ResidualFixtureCommands::tick);
        MinecraftForge.EVENT_BUS.addListener(M43ResidualFixtureCommands::postPhysics);
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> TASKS.clear());
        M43ResidualFixtureNetwork.install();
    }

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sable_m43").requires(s -> s.hasPermission(2))
                .then(Commands.literal("setup").executes(c -> setup(c.getSource())))
                .then(Commands.literal("run_all").executes(c -> run(c.getSource())))
                .then(Commands.literal("yaw90").executes(c -> yaw(c.getSource())))
                .then(Commands.literal("cleanup").executes(c -> cleanup(c.getSource()))));
    }

    private static int setup(final CommandSourceStack source) {
        final ServerLevel level = source.getLevel();
        if (lookup(level).status != Status.NONE) return refuse(source, "SETUP REFUSED: existing/ambiguous ownership; run cleanup");
        BlockPos chosen = null;
        int checked = 0;
        for (final BlockPos offset : M38DispenserFixturePlacement.candidateOffsets()) {
            final BlockPos candidate = BlockPos.containing(source.getPosition()).above(4).offset(offset);
            checked++;
            if (M43ResidualFixtureLayout.reservation().stream().allMatch(p -> safeAir(level, candidate.offset(p), null))) {
                chosen = candidate; break;
            }
        }
        if (chosen == null) return refuse(source, "SETUP REFUSED: no loaded safe nearby footprint checked=" + checked);
        final BlockPos origin = chosen;
        final ArmorStand marker = new ArmorStand(level, origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        final CompoundTag flags = new CompoundTag();
        flags.putBoolean("Marker", true); flags.putBoolean("Invisible", true); flags.putBoolean("NoGravity", true);
        marker.load(flags); marker.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        marker.addTag(TAG); marker.getPersistentData().put("M43Origin", NbtUtils.writeBlockPos(origin));
        if (!level.addFreshEntity(marker) || level.getEntity(marker.getUUID()) != marker || !marker.blockPosition().equals(origin)) {
            marker.discard(); return refuse(source, "SETUP FAILED: marker unavailable before BUILD");
        }
        tell(level, "SETUP origin=" + origin.toShortString() + " MARKER status=VALID candidatesChecked=" + checked);
        final Fixture f = new Fixture(level, marker, origin, origin, null);
        final List<BlockPos> placed = new ArrayList<>();
        SuperGlueEntity glue = null;
        tell(level, "SETUP phase=BUILD");
        try {
            for (final var cell : M43ResidualFixtureLayout.blocks().entrySet()) {
                final BlockPos p = origin.offset(cell.getKey());
                if (!safeAir(level, p, null)) throw new IllegalStateException("reservation changed " + p);
                placed.add(p); put(level, p, cell.getValue());
            }
            for (int lane = 0; lane < 4; lane++) {
                drive(f, lane, 0, true);
                mode(f, lane, MovementMode.MOVE_PLACE_RETURNED);
                final PistonContraption payload = new PistonContraption(Direction.EAST, false);
                if (!payload.assemble(level, origin.offset(M43ResidualFixtureLayout.lane(lane)))) throw new IllegalStateException("native lane assembly failed " + lane);
                final BlockPos actor = origin.offset(M43ResidualFixtureLayout.lane(lane)).offset(1, 1, 0).subtract(payload.anchor);
                if (!payload.getBlocks().containsKey(actor) || payload.getActors().size() != 1
                        || payload.getBlocks().size() != 16) throw new IllegalStateException("native attachment invalid lane=" + lane + " blocks=" + payload.getBlocks().keySet());
            }
            tell(level, "SETUP phase=VALIDATE");
            final BlockEntity assembler = level.getBlockEntity(origin.offset(M43ResidualFixtureLayout.ASSEMBLER));
            if (!(assembler instanceof PhysicsAssemblerBlockEntity)) throw new IllegalStateException("outer assembler unavailable");
            assembler.getPersistentData().putUUID(OWNER, marker.getUUID());
            glue = new SuperGlueEntity(level, M43ResidualFixtureLayout.glue(origin));
            glue.getPersistentData().putUUID(OWNER, marker.getUUID());
            if (!level.addFreshEntity(glue)) throw new IllegalStateException("outer glue unavailable");
            final SimAssemblyContraption outer = new SimAssemblyContraption(null);
            if (!outer.searchMovedStructure(level, origin.north(2))
                    || !outer.getBlocks().containsAll(M43ResidualFixtureLayout.blocks().keySet().stream().map(origin::offset).toList())) throw new IllegalStateException("outer selection incomplete");
            marker.addTag(READY);
            tell(level, "SETUP phase=READY lanes=4 nativeAttachment=true outerSelectedBlocks=" + outer.getBlocks().size()
                    + "; click Physics Assembler once, then /function sable:m43/run_all");
            return 1;
        } catch (final Exception failure) {
            Sable.LOGGER.error("SABLE_M43_SETUP_EXCEPTION origin={}", origin, failure);
            tell(level, "SETUP FAILED reason=" + failure.getMessage() + " phase=ROLLBACK");
            if (glue != null) glue.discard();
            // BUILD is synchronous, all drives are stopped before a native entity can tick.
            for (final BlockPos p : placed) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
            final boolean clear = placed.stream().allMatch(p -> level.getBlockState(p).isAir() && level.getBlockEntity(p) == null);
            if (clear) marker.discard();
            tell(level, "SETUP ROLLBACK " + (clear ? "COMPLETE" : "INCOMPLETE") + " footprintClear=" + clear);
            return 0;
        }
    }

    private static int run(final CommandSourceStack source) {
        tell(source.getLevel(), "RUN phase=PRECHECK");
        final Lookup lookup = lookup(source.getLevel());
        if (lookup.fixture == null || lookup.fixture.body == null || !lookup.fixture.marker.getTags().contains(READY)) return refuse(source, "RUN BLOCKED reason=READY_ASSEMBLED_FIXTURE_REQUIRED ownership=" + lookup.status);
        final Fixture f = lookup.fixture;
        if (TASKS.containsKey(f.marker.getUUID())) return refuse(source, "RUN BLOCKED reason=LIFECYCLE_ACTIVE");
        if (!(source.getEntity() instanceof ServerPlayer player)) return refuse(source, "RUN BLOCKED reason=PLAYER_REQUIRED_FOR_CLIENT_GAZE_WITNESS");
        for (int lane = 0; lane < 4; lane++) if (piston(f, lane) == null || inner(f, lane) != null || piston(f, lane).offset > .01) return refuse(source, "RUN BLOCKED reason=RETRACTED_NATIVE_CONTROLLER_REQUIRED lane=" + lane);
        final Task task = new Task(f.level, f.marker.getUUID(), Phase.CAPTURE);
        task.player = player.getUUID();
        TASKS.put(task.marker, task);
        for (int lane = 0; lane < 4; lane++) { mode(f, lane, MovementMode.MOVE_NEVER_PLACE); drive(f, lane, -16, false); }
        tell(f.level, "RUN phase=WAIT_FOR_NATIVE_ACTORS nonce=" + task.nonce);
        return 1;
    }

    private static int yaw(final CommandSourceStack source) {
        final Lookup lookup = lookup(source.getLevel());
        if (lookup.fixture == null || lookup.fixture.body == null || !lookup.fixture.marker.getPersistentData().getBoolean("M43BaselinePass")
                || TASKS.containsKey(lookup.fixture.marker.getUUID())) return refuse(source, "YAW90 BLOCKED reason=BASELINE_PASS_AND_RETRACTED_REQUIRED");
        final Fixture f = lookup.fixture;
        for (int lane = 0; lane < 4; lane++) if (inner(f, lane) != null) return refuse(source, "YAW90 BLOCKED reason=INNER_STILL_ACTIVE");
        f.marker.getPersistentData().putBoolean("M43Yaw90", true);
        hold(f);
        tell(f.level, "YAW90 READY yaw=90 localEast=north; /function sable:m43/run_all");
        return 1;
    }

    private static int cleanup(final CommandSourceStack source) {
        final Lookup lookup = lookup(source.getLevel());
        if (lookup.status == Status.NONE) { tell(source.getLevel(), "CLEANUP COMPLETE alreadyClean=true"); return 1; }
        if (lookup.fixture == null) return refuse(source, "CLEANUP REFUSED ownership=" + lookup.status);
        final Fixture f = lookup.fixture;
        final Task previous = TASKS.remove(f.marker.getUUID());
        if (previous != null) removeTargets(f, previous);
        removeOwnedEntities(f);
        removeRegressionBlocks(f);
        final Task task = new Task(f.level, f.marker.getUUID(), Phase.RETRACT);
        task.cleaning = true;
        TASKS.put(task.marker, task);
        tell(f.level, "CLEANUP phase=STOP ownership=" + lookup.status);
        return 1;
    }

    /** One authoritative token/controller lookup for setup, run, yaw, cleanup and tick. */
    private static Lookup lookup(final ServerLevel level) {
        final List<ArmorStand> markers = new ArrayList<>();
        for (final Entity entity : level.getAllEntities()) if (entity instanceof ArmorStand marker && marker.getTags().contains(TAG)) markers.add(marker);
        if (markers.isEmpty()) return new Lookup(Status.NONE, null);
        if (markers.size() != 1) return new Lookup(Status.AMBIGUOUS, null);
        final ArmorStand marker = markers.get(0);
        final BlockPos origin = NbtUtils.readBlockPos(marker.getPersistentData().getCompound("M43Origin"));
        final List<Fixture> bodies = new ArrayList<>();
        final var container = SubLevelContainer.getContainer(level);
        if (container != null) for (final ServerSubLevel body : container.getAllSubLevels()) {
            for (final BlockPos p : SimAssemblyHelper.collectBlocks(level, body)) {
                final BlockEntity be = M35FixtureLookup.blockEntity(level, body, p);
                if (be instanceof PhysicsAssemblerBlockEntity && be.getPersistentData().hasUUID(OWNER)
                        && marker.getUUID().equals(be.getPersistentData().getUUID(OWNER))) {
                    bodies.add(new Fixture(level, marker, origin, p.subtract(M43ResidualFixtureLayout.ASSEMBLER), body));
                }
            }
        }
        if (bodies.size() > 1) return new Lookup(Status.AMBIGUOUS, null);
        if (bodies.size() == 1) return new Lookup(Status.ASSEMBLED, bodies.get(0));
        return new Lookup(Status.STATIC_OR_PARTIAL, new Fixture(level, marker, origin, origin, null));
    }

    private static void tick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (final Task task : List.copyOf(TASKS.values())) {
            if (task.level.getServer() != event.getServer()) continue;
            try { advance(task); }
            catch (final RuntimeException failure) {
                TASKS.remove(task.marker);
                final Lookup lookup = lookup(task.level);
                if (lookup.fixture != null) for (int lane = 0; lane < 4; lane++) if (piston(lookup.fixture, lane) != null) drive(lookup.fixture, lane, 0, true);
                Sable.LOGGER.error("SABLE_M43_FIXTURE_EXCEPTION phase={} marker={}", task.phase, task.marker, failure);
                tell(task.level, (task.cleaning ? "CLEANUP" : "RUN") + " FAILED reason=" + failure.getMessage() + "; cleanup remains retryable");
            }
        }
    }

    private static void advance(final Task task) {
        final Lookup lookup = lookup(task.level);
        if (lookup.fixture == null || !lookup.fixture.marker.getUUID().equals(task.marker)) throw new IllegalStateException("ownership unavailable " + lookup.status);
        final Fixture f = lookup.fixture;
        if (++task.ticks > 2400) throw new IllegalStateException("phase timeout " + task.phase);
        if (task.phase == Phase.CAPTURE) {
            boolean ready = true;
            for (int lane = 0; lane < 4; lane++) {
                if (actor(f, lane) == null || actor(f, lane).position == null) ready = false;
                else drive(f, lane, 0, true);
            }
            if (!ready) return;
            task.phase = Phase.SETTLE; task.settle = 0;
            tell(f.level, "ACTORS_CAPTURED nativePistons=4 lanes=PLOUGH,DRILL,SAW,BLAZE");
        } else if (task.phase == Phase.SETTLE) {
            for (int lane = 0; lane < 4; lane++) if (piston(f, lane).getSpeed() != 0) return;
            if (++task.settle < 4) return;
            arm(f, task);
            task.phase = Phase.OBSERVE;
        } else if (task.phase == Phase.OBSERVE) {
            observe(f, task);
            if (task.ticks % 200 == 0) tell(f.level, "RUN PENDING plough=" + task.pass[0] + " drill=" + task.pass[1]
                    + " saw=" + task.pass[2] + " blazeClientWitness=" + task.blaze
                    + " drillBlock=" + task.blockPass[0] + " sawBlock=" + task.blockPass[1]);
            if (M43ResidualFixtureLayout.allPassed(task.pass[0], task.pass[1], task.pass[2], task.blaze,
                    task.blockPass[0], task.blockPass[1])) {
                tell(f.level, f.marker.getPersistentData().getBoolean("M43Yaw90") ? "YAW90 PASS" : "BASELINE PASS");
                f.marker.getPersistentData().putBoolean("M43BaselinePass", true);
                task.phase = Phase.RETRACT; task.ticks = 0;
                removeTargets(f, task);
            }
        } else if (task.phase == Phase.RETRACT) {
            boolean done = true;
            for (int lane = 0; lane < 4; lane++) {
                final var controller = piston(f, lane);
                if (controller == null) {
                    if (f.body != null) throw new IllegalStateException("owned controller absent lane=" + lane);
                    continue;
                }
                mode(f, lane, MovementMode.MOVE_PLACE_RETURNED);
                if (inner(f, lane) != null || controller.running) { drive(f, lane, 32, false); done = false; }
                else drive(f, lane, 0, true);
            }
            if (!done) return;
            if (!task.cleaning) {
                TASKS.remove(task.marker);
                tell(f.level, "RUN COMPLETE innerRetracted=true; yaw90 or cleanup available");
            } else { task.phase = Phase.OUTER; tell(f.level, "CLEANUP phase=INNER_DISASSEMBLED"); }
        } else if (task.phase == Phase.OUTER) {
            if (f.body != null) {
                f.marker.getPersistentData().putBoolean("M43Yaw90", false); hold(f);
                for (final BlockPos raw : SimAssemblyHelper.collectBlocks(f.level, f.body)) {
                    final BlockPos target = f.origin.offset(raw.subtract(f.raw));
                    if (!f.level.getBlockState(target).isAir()) throw new IllegalStateException("unrelated restore blocker preserved pos=" + target + " state=" + f.level.getBlockState(target));
                }
                final var assembler = M35FixtureLookup.blockEntity(f.level, f.body, f.raw.offset(M43ResidualFixtureLayout.ASSEMBLER));
                if (!(assembler instanceof PhysicsAssemblerBlockEntity nativeAssembler)) throw new IllegalStateException("native assembler absent");
                nativeAssembler.assembleOrDisassemble(null);
                if (SubLevelContainer.getContainer(f.level).getSubLevel(f.body.getUniqueId()) != null) throw new IllegalStateException("native outer disassembly refused");
                f.marker.getPersistentData().putBoolean("M43NativeOuterRemoved", true);
                return;
            }
            task.phase = Phase.REMOVE;
        } else if (task.phase == Phase.REMOVE) {
            if (f.body != null) throw new IllegalStateException("active body cannot be deleted");
            removeOwnedEntities(f);
            final var cells = M43ResidualFixtureLayout.blocks();
            for (final var cell : cells.entrySet()) {
                final BlockState actual = f.level.getBlockState(f.origin.offset(cell.getKey()));
                if (!actual.isAir() && !BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString().equals(M38DispenserFixtureLayout.blockId(cell.getValue()))) throw new IllegalStateException("unrelated footprint damage preserved " + cell.getKey());
            }
            for (final BlockPos p : cells.keySet()) f.level.setBlock(f.origin.offset(p), Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
            for (final SuperGlueEntity glue : f.level.getEntitiesOfClass(SuperGlueEntity.class, M43ResidualFixtureLayout.glue(f.origin).inflate(.01))) {
                if (owned(glue, f.marker.getUUID()) || f.marker.getPersistentData().getBoolean("M43NativeOuterRemoved") && glue.getBoundingBox().equals(M43ResidualFixtureLayout.glue(f.origin))) glue.discard();
            }
            if (cells.keySet().stream().anyMatch(p -> !f.level.getBlockState(f.origin.offset(p)).isAir())) throw new IllegalStateException("footprint clear verification failed");
            f.marker.discard(); TASKS.remove(task.marker);
            tell(f.level, "CLEANUP COMPLETE footprintClear=true");
        }
    }

    private static void arm(final Fixture f, final Task task) {
        task.armedAt = f.level.getGameTime();
        task.nonce = UUID.randomUUID();
        for (int lane = 0; lane < 4; lane++) SableActorEntityEvidence.clear(actor(f, lane));
        for (int lane = 0; lane < 3; lane++) {
            final MovementContext context = actor(f, lane);
            final Vec3 future = f.body.logicalPose().transformPosition(context.position.add(3, 0, 0));
            final BlockPos cell = BlockPos.containing(future);
            if (!safeAir(f.level, cell, f.body)) throw new IllegalStateException("required AIR entity cell blocked " + cell);
            task.airCells[lane] = cell;
            final Pig pig = EntityType.PIG.create(f.level);
            if (pig == null) throw new IllegalStateException("native pig construction unavailable");
            pig.setNoAi(true); pig.setNoGravity(true); pig.setPersistenceRequired();
            pig.moveTo(cell.getX() + .5, cell.getY() + .01, cell.getZ() + .5, 0, 0);
            pig.getPersistentData().putUUID(OWNER, task.marker);
            if (!f.level.addFreshEntity(pig)) throw new IllegalStateException("test entity registration failed");
            task.targets[lane] = pig.getUUID();
        }
        for (int lane = 1; lane <= 2; lane++) {
            final Vec3 future = f.body.logicalPose().transformPosition(actor(f, lane).position.add(6, 0, 0));
            final BlockPos cell = BlockPos.containing(future);
            if (!safeAir(f.level, cell, f.body)) throw new IllegalStateException("block regression cell occupied " + cell);
            task.blocks.put(cell, lane == 1 ? Blocks.STONE.defaultBlockState() : Blocks.OAK_LOG.defaultBlockState());
            f.level.setBlock(cell, task.blocks.get(cell), 2);
            // Persist exact owned targets for cleanup after reload, not just the asynchronous Task.
            f.marker.getPersistentData().put("M43Block" + lane, NbtUtils.writeBlockPos(cell));
        }
        M43ResidualFixtureNetwork.request(f.level.getServer().getPlayerList().getPlayer(task.player), task.marker,
                task.nonce, inner(f, 3).getUUID(), f.body.getUniqueId());
        for (int lane = 0; lane < 4; lane++) drive(f, lane, -32, false);
        tell(f.level, "RUN phase=ACTIVE gameTime=" + task.armedAt + " airCells=" + List.of(task.airCells));
    }

    private static void observe(final Fixture f, final Task task) {
        for (int lane = 0; lane < 3; lane++) {
            final int laneIndex = lane;
            final MovementContext context = actor(f, lane);
            if (context == null) throw new IllegalStateException("native actor lost lane=" + lane);
            final var events = SableActorEntityEvidence.events(context).stream().filter(e -> e.gameTime() >= task.armedAt).toList();
            if (!task.pass[lane]) {
                final UUID target = task.targets[lane];
                final boolean query = events.stream().anyMatch(e -> e.operation().equals("QUERY") && e.cell().equals(task.airCells[laneIndex]) && e.air() && e.candidates() > 0);
                if (lane == 0) {
                    final var impulse = events.stream().filter(e -> e.operation().equals("THROW") && target.equals(e.entity())).findFirst().orElse(null);
                    if (impulse != null) {
                        final Vec3 expectedMotion = ResidualActorGeometry.rotate(f.body.logicalPose(), impulse.nativeMotion());
                        final Vec3 expected = ResidualActorGeometry.nativeBoost(expectedMotion);
                        final Vec3 actual = impulse.afterVelocity().subtract(impulse.beforeVelocity());
                        final double dot = expected.normalize().dot(actual.normalize());
                        final double error = expected.distanceTo(actual);
                        final Entity entity = f.level.getEntity(target);
                        task.pass[lane] = query && entity != null && entity.level() == f.level
                                && Sable.HELPER.getContaining(entity) == null && dot > 1 - 1e-10 && error < 1e-9;
                        tell(f.level, "[PLOUGH] ENTITY_SELECTED entity=" + target + " nativeMotion=" + impulse.nativeMotion()
                                + " parentMotion=" + impulse.parentMotion() + " velocityBefore=" + impulse.beforeVelocity()
                                + " velocityAfter=" + impulse.afterVelocity() + " actualDelta=" + actual + " directionDot=" + dot
                                + " magnitudeError=" + error + " physicalEntity=true hiddenPlotEntity=false " + (task.pass[lane] ? "PASS" : "INCOMPLETE"));
                    }
                } else {
                    final var damage = events.stream().filter(e -> e.operation().equals("DAMAGE") && target.equals(e.entity()) && e.accepted() && e.air() && e.healthAfter() < e.healthBefore()).toList();
                    if (!damage.isEmpty()) {
                        final var attempts = events.stream().filter(e -> e.operation().equals("DAMAGE") && target.equals(e.entity())).toList();
                        final boolean duplicate = attempts.stream().map(SableActorEntityEvidence.Event::gameTime).distinct().count() != attempts.size();
                        task.pass[lane] = query && !duplicate && f.level.getBlockState(task.airCells[lane]).isAir();
                        final var first = damage.get(0);
                        tell(f.level, "[" + M43ResidualFixtureLayout.KINDS[lane] + "] AIR_CELL blockState=air entityQueryExecuted=" + query
                                + " entitySelected=true healthBefore=" + first.healthBefore() + " healthAfter=" + first.healthAfter()
                                + " damage=" + (first.healthBefore() - first.healthAfter()) + " hiddenPlotQuery=false duplicateDamage=" + duplicate
                                + " " + (task.pass[lane] ? "PASS" : "INCOMPLETE"));
                    }
                }
                if (task.pass[lane]) { final Entity entity = f.level.getEntity(target); if (entity != null && owned(entity, task.marker)) entity.discard(); }
            }
        }
        int index = 0;
        for (final var cell : task.blocks.entrySet()) {
            if (!task.blockPass[index] && f.level.getBlockState(cell.getKey()).isAir()) {
                task.blockPass[index] = true;
                tell(f.level, "[" + (index == 0 ? "DRILL" : "SAW") + "] BLOCK_REGRESSION nativeDestroyed=true target=" + cell.getKey() + " PASS");
            }
            index++;
        }
        // Stop before the end of the stroke; native return-mode retraction is started only by RETRACT.
        for (int lane = 0; lane < 4; lane++) if (piston(f, lane).offset >= 9) drive(f, lane, 0, true);
    }

    static void clientGaze(final ServerPlayer sender, final UUID marker, final UUID nonce, final UUID vehicle,
                           final UUID owner, final Vec3 visible, final Vec3 camera, final Vec3 localGaze,
                           final float nativeAngle, final double directionDot) {
        final Task task = TASKS.get(marker);
        if (task == null || task.phase != Phase.OBSERVE || !nonce.equals(task.nonce) || !sender.getUUID().equals(task.player)) return;
        if (!finite(visible) || !finite(camera) || !finite(localGaze) || !Float.isFinite(nativeAngle)) return;
        final Lookup lookup = lookup(sender.serverLevel());
        if (lookup.fixture == null || lookup.fixture.body == null || !owner.equals(lookup.fixture.body.getUniqueId())
                || inner(lookup.fixture, 3) == null || !vehicle.equals(inner(lookup.fixture, 3).getUUID())) return;
        final MovementContext actor = actor(lookup.fixture, 3);
        if (actor == null || actor.position == null) return;
        final Vec3 expectedVisible = lookup.fixture.body.logicalPose().transformPosition(actor.position);
        final double radians = Math.toRadians(-nativeAngle - 90);
        final Vec3 actualLocal = new Vec3(Math.cos(radians), 0, Math.sin(radians));
        final Vec3 expectedLocal = new Vec3(localGaze.x, 0, localGaze.z).normalize();
        final double dot = actualLocal.dot(expectedLocal);
        if (!Double.isFinite(dot) || !Double.isFinite(directionDot) || visible.distanceTo(expectedVisible) > 2
                || Sable.HELPER.getContaining(sender.level(), visible) != null || Math.abs(dot - directionDot) > 1e-9) return;
        if (!task.blaze) {
            task.blaze = dot > 1 - 1e-10;
            tell(sender.serverLevel(), "[BLAZE] GAZE_SAMPLE visibleActor=" + visible + " camera=" + camera + " expectedDirection=" + expectedLocal
                    + " nativeDirection=" + actualLocal + " nativeAngle=" + nativeAngle + " directionDot=" + dot
                    + " hiddenPlotInput=false " + (task.blaze ? "PASS" : "INCOMPLETE"));
        }
    }

    private static void removeTargets(final Fixture f, final Task task) {
        removeOwnedEntities(f);
        removeRegressionBlocks(f);
    }
    private static void removeOwnedEntities(final Fixture f) {
        for (final Entity entity : f.level.getAllEntities()) if (entity != f.marker && owned(entity, f.marker.getUUID())
                && !(entity instanceof AbstractContraptionEntity) && !(entity instanceof SuperGlueEntity)) entity.discard();
    }
    private static void removeRegressionBlocks(final Fixture f) {
        for (int lane = 1; lane <= 2; lane++) {
            final String key = "M43Block" + lane;
            if (!f.marker.getPersistentData().contains(key)) continue;
            final BlockPos p = NbtUtils.readBlockPos(f.marker.getPersistentData().getCompound(key));
            final BlockState actual = f.level.getBlockState(p);
            if (!actual.isAir() && (!actual.is(lane == 1 ? Blocks.STONE : Blocks.OAK_LOG) || f.level.getBlockEntity(p) != null)) throw new IllegalStateException("unrelated regression target damage preserved " + p);
            if (!actual.isAir()) f.level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
            f.marker.getPersistentData().remove(key);
        }
    }
    private static boolean owned(final Entity entity, final UUID marker) {
        return entity.getPersistentData().hasUUID(OWNER) && marker.equals(entity.getPersistentData().getUUID(OWNER));
    }
    private static boolean finite(final Vec3 value) { return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z); }
    private static MechanicalPistonBlockEntity piston(final Fixture f, final int lane) {
        return M35FixtureLookup.blockEntity(f.level, f.body, f.raw.offset(M43ResidualFixtureLayout.lane(lane))) instanceof MechanicalPistonBlockEntity p ? p : null;
    }
    private static AbstractContraptionEntity inner(final Fixture f, final int lane) {
        final var controller = piston(f, lane);
        if (controller == null || controller.movedContraption == null || controller.movedContraption.isRemoved()) return null;
        final AbstractContraptionEntity entity = controller.movedContraption;
        final BlockPos expected = f.raw.offset(M43ResidualFixtureLayout.lane(lane));
        return entity.getContraption() instanceof PistonContraption && expected.equals(SableCreateContraptionContext.getControllerPos(entity))
                && (f.body == null || SableCreateContraptionContext.getContainingSubLevel(entity) == f.body) ? entity : null;
    }
    private static MovementContext actor(final Fixture f, final int lane) {
        final var entity = inner(f, lane);
        if (entity == null || entity.getContraption().getActors().size() != 1) return null;
        return entity.getContraption().getActors().get(0).right;
    }
    private static void mode(final Fixture f, final int lane, final MovementMode mode) {
        final var behaviour = piston(f, lane).getBehaviour(ScrollValueBehaviour.TYPE);
        if (!(behaviour instanceof ScrollOptionBehaviour<?> option)) throw new IllegalStateException("native movement option absent");
        option.setValue(mode.ordinal());
    }
    private static void drive(final Fixture f, final int lane, final int speed, final boolean stopped) {
        final BlockPos origin = f.raw.offset(M43ResidualFixtureLayout.lane(lane));
        if (!(M35FixtureLookup.blockEntity(f.level, f.body, origin.below(2)) instanceof CreativeMotorBlockEntity motor)) throw new IllegalStateException("native motor absent lane=" + lane);
        motor.generatedSpeed.setValue(speed);
        M35RollerFixtureLifecycleCommands.setFixtureLever(f.level, f.body, origin, stopped);
    }
    private static void postPhysics(final ForgeSablePostPhysicsTickEvent event) {
        final Lookup lookup = lookup(event.getPhysicsSystem().getLevel());
        if (lookup.fixture != null && lookup.fixture.body != null && lookup.fixture.marker.getTags().contains(READY)) hold(lookup.fixture);
    }
    private static void hold(final Fixture f) {
        if (f.body == null) return;
        final var physics = SubLevelPhysicsSystem.get(f.level);
        final var handle = physics == null ? null : physics.getPhysicsHandle(f.body);
        if (handle == null || !handle.isValid()) return;
        final Quaterniond q = new Quaterniond().rotationY(f.marker.getPersistentData().getBoolean("M43Yaw90") ? Math.PI / 2 : 0);
        final Vec3 raw = Vec3.atCenterOf(f.raw), visible = Vec3.atCenterOf(f.origin);
        final Vector3d local = new Vector3d(raw.x, raw.y, raw.z).sub(f.body.logicalPose().rotationPoint()).mul(f.body.logicalPose().scale());
        handle.teleport(new Vector3d(visible.x, visible.y, visible.z).sub(q.transform(local)), q);
        handle.setLinearAndAngularVelocity(new Vector3d(), new Vector3d());
        physics.updatePose(f.body); f.body.updateBoundingBox(); f.body.updateLastPose();
    }
    private static boolean safeAir(final ServerLevel level, final BlockPos p, final ServerSubLevel owner) {
        if (level.isOutsideBuildHeight(p) || !level.getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4)
                || !level.getBlockState(p).isAir() || level.getBlockEntity(p) != null || Sable.HELPER.getContaining(level, p) != null) return false;
        for (final var body : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(new AABB(p)))) if (body != owner) return false;
        return true;
    }
    private static void put(final ServerLevel level, final BlockPos p, final String state) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!level.setBlock(p, BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), state, false).blockState(), 2)) throw new IllegalStateException("placement failed " + p);
    }
    private static int refuse(final CommandSourceStack source, final String message) { tell(source.getLevel(), message); source.sendFailure(Component.literal("[M43] " + message)); return 0; }
    private static void tell(final ServerLevel level, final String message) {
        Sable.LOGGER.info("[M43] {}", message);
        for (final ServerPlayer player : level.players()) player.sendSystemMessage(Component.literal("[M43] " + message));
    }
    private enum Status { NONE, STATIC_OR_PARTIAL, ASSEMBLED, AMBIGUOUS }
    private enum Phase { CAPTURE, SETTLE, OBSERVE, RETRACT, OUTER, REMOVE }
    private record Lookup(Status status, Fixture fixture) { }
    private record Fixture(ServerLevel level, ArmorStand marker, BlockPos origin, BlockPos raw, ServerSubLevel body) { }
    private static final class Task {
        private final ServerLevel level;
        private final UUID marker;
        private UUID player, nonce = UUID.randomUUID();
        private Phase phase;
        private int ticks, settle;
        private long armedAt;
        private boolean cleaning, blaze;
        private final boolean[] pass = new boolean[3], blockPass = new boolean[2];
        private final UUID[] targets = new UUID[3];
        private final BlockPos[] airCells = new BlockPos[3];
        private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        private Task(final ServerLevel level, final UUID marker, final Phase phase) { this.level = level; this.marker = marker; this.phase = phase; }
    }
}
