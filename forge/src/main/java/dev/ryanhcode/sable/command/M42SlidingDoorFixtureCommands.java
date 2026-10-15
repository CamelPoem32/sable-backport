package dev.ryanhcode.sable.command;

import com.mojang.brigadier.CommandDispatcher;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption.MovementMode;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
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
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.compatibility.create.sliding_door.SableSlidingDoorTarget;
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
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Test only. Seeds an open captured door; Create's next tick decides and executes the close handoff. */
public final class M42SlidingDoorFixtureCommands {
    private static final String TAG = "sable_m42_origin";
    private static final String READY = "sable_m42_ready_v1";
    private static final String GLUE = "sable_m42_glue";
    private static final Map<UUID, Task> TASKS = new HashMap<>();
    private M42SlidingDoorFixtureCommands() { }

    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(M42SlidingDoorFixtureCommands::tick);
        MinecraftForge.EVENT_BUS.addListener(M42SlidingDoorFixtureCommands::postPhysics);
        MinecraftForge.EVENT_BUS.addListener(M42SlidingDoorFixtureCommands::stopped);
    }

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sable_m42").requires(s -> s.hasPermission(2))
                .then(Commands.literal("door").executes(c -> setup(c.getSource())))
                .then(Commands.literal("run").executes(c -> run(c.getSource())))
                .then(Commands.literal("yaw90").executes(c -> yaw(c.getSource())))
                .then(Commands.literal("cleanup").executes(c -> cleanup(c.getSource()))));
    }

    private static int setup(final CommandSourceStack source) {
        final ServerLevel level = source.getLevel();
        if (lookup(level).status != Status.NONE) return refuse(source, "SETUP REFUSED: existing/ambiguous M42 ownership; run cleanup");
        BlockPos chosen = null;
        int checked = 0;
        for (final BlockPos offset : M38DispenserFixturePlacement.candidateOffsets()) {
            final BlockPos candidate = BlockPos.containing(source.getPosition()).above(4).offset(offset);
            checked++;
            if (M42SlidingDoorFixtureLayout.reservation().stream().allMatch(p -> safeAir(level, candidate.offset(p)))) {
                chosen = candidate;
                break;
            }
        }
        if (chosen == null) return refuse(source, "SETUP REFUSED: no loaded safe nearby footprint checked=" + checked);
        final BlockPos origin = chosen;
        final ArmorStand marker = new ArmorStand(level, origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        final CompoundTag flags = new CompoundTag();
        flags.putBoolean("Marker", true); flags.putBoolean("Invisible", true); flags.putBoolean("NoGravity", true);
        marker.load(flags);
        marker.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        marker.addTag(TAG);
        marker.getPersistentData().put("M42Origin", NbtUtils.writeBlockPos(origin));
        marker.getPersistentData().putString("M42Dimension", level.dimension().location().toString());
        if (!level.addFreshEntity(marker) || level.getEntity(marker.getUUID()) != marker || !marker.blockPosition().equals(origin)) {
            marker.discard();
            return refuse(source, "SETUP FAILED: marker unavailable before BUILD");
        }
        final Fixture fixture = new Fixture(level, marker, origin, origin, null);
        final List<BlockPos> placed = new ArrayList<>();
        SuperGlueEntity glue = null;
        tell(level, "SETUP origin=" + origin.toShortString() + " MARKER status=VALID candidatesChecked=" + checked);
        tell(level, "SETUP phase=BUILD");
        try {
            for (final var cell : M42SlidingDoorFixtureLayout.mechanism().entrySet()) {
                final BlockPos pos = origin.offset(cell.getKey());
                if (!safeAir(level, pos)) throw new IllegalStateException("reservation changed " + pos);
                placed.add(pos);
                put(level, pos, cell.getValue());
            }
            drive(fixture, -32, true);
            if (!(piston(fixture) instanceof MechanicalPistonBlockEntity)
                    || !(M35FixtureLookup.blockEntity(level, null, origin.offset(M42SlidingDoorFixtureLayout.ASSEMBLER)) instanceof PhysicsAssemblerBlockEntity)) {
                throw new IllegalStateException("native controller/assembler absent");
            }
            validateNativeAttachment(level, origin);
            tell(level, "SETUP phase=VALIDATE");
            glue = new SuperGlueEntity(level, M42SlidingDoorFixtureLayout.glue(origin));
            glue.addTag(GLUE);
            glue.getPersistentData().putUUID("M42Owner", marker.getUUID());
            if (!level.addFreshEntity(glue) || glue.contains(origin.east())) throw new IllegalStateException("outer glue invalid");
            final SimAssemblyContraption outer = new SimAssemblyContraption(null);
            if (!outer.searchMovedStructure(level, origin.north(2))
                    || !outer.getBlocks().contains(origin.offset(M42SlidingDoorFixtureLayout.LOWER.above()))) {
                throw new IllegalStateException("outer selection misses door upper half");
            }
            relocate(fixture, origin.offset(M42SlidingDoorFixtureLayout.PARKED), Direction.NORTH);
            if (stationCells(fixture).keySet().stream().anyMatch(outer.getBlocks()::contains)) throw new IllegalStateException("station captured by outer hull");
            final BlockEntity assembler = level.getBlockEntity(origin.offset(M42SlidingDoorFixtureLayout.ASSEMBLER));
            assembler.getPersistentData().putUUID("M42FixtureOwner", marker.getUUID());
            tell(level, "ATTACHMENT innerNativeAttachmentValid=true doorHalves=2 outerSelectedBlocks=" + outer.getBlocks().size());
            mode(fixture, MovementMode.MOVE_NEVER_PLACE);
            drive(fixture, -32, false);
            final Task preparation = new Task(level, marker.getUUID(), Phase.PREPARE);
            TASKS.put(marker.getUUID(), preparation);
            tell(level, "SETUP phase=VALIDATE waitingForLiveNativePiston=true; do not assemble yet");
            return 1;
        } catch (final Exception failure) {
            marker.removeTag(READY);
            Sable.LOGGER.error("SABLE_M42_SETUP_EXCEPTION", failure);
            tell(level, "SETUP FAILED reason=" + failure.getMessage() + " phase=ROLLBACK");
            if (glue != null) glue.discard();
            removeStation(fixture);
            for (final BlockPos pos : placed) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
            final boolean clear = placed.stream().allMatch(p -> level.getBlockState(p).isAir()) && stationCells(fixture).keySet().stream().allMatch(p -> level.getBlockState(p).isAir());
            if (clear) marker.discard();
            tell(level, clear ? "SETUP ROLLBACK COMPLETE footprintClear=true" : "SETUP ROLLBACK INCOMPLETE ownershipRetained=true");
            return 0;
        }
    }

    private static void validateNativeAttachment(final ServerLevel level, final BlockPos origin) throws com.simibubi.create.content.contraptions.AssemblyException {
        final BlockPos chassisPos = origin.offset(M42SlidingDoorFixtureLayout.CHASSIS);
        final BlockState chassisState = level.getBlockState(chassisPos);
        final BlockEntity be = level.getBlockEntity(chassisPos);
        final List<BlockPos> included = be instanceof ChassisBlockEntity chassis
                ? chassis.getIncludedBlockPositions(Direction.EAST, false) : null;
        final Set<BlockPos> rangeExpected = new LinkedHashSet<>();
        M42SlidingDoorFixtureLayout.chassisRangeExpected().forEach(pos -> rangeExpected.add(origin.offset(pos)));
        final Set<BlockPos> rangeActual = included == null ? Set.of() : new LinkedHashSet<>(included);
        final Set<BlockPos> expected = new LinkedHashSet<>();
        M42SlidingDoorFixtureLayout.capturedBlockIds().keySet().forEach(pos -> expected.add(origin.offset(pos)));
        final PistonContraption payload = new PistonContraption(Direction.EAST, false);
        final boolean assembled = payload.assemble(level, origin);
        final Map<BlockPos, String> actualIds = new LinkedHashMap<>();
        payload.getBlocks().values().forEach(info -> actualIds.put(info.pos().offset(payload.anchor), BuiltInRegistries.BLOCK.getKey(info.state().getBlock()).toString()));
        final Set<BlockPos> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actualIds.keySet());
        final Set<BlockPos> extra = new LinkedHashSet<>(actualIds.keySet());
        extra.removeAll(expected);
        final List<String> wrongBlocks = new ArrayList<>();
        M42SlidingDoorFixtureLayout.capturedBlockIds().forEach((pos, id) -> {
            if (!id.equals(actualIds.get(origin.offset(pos)))) wrongBlocks.add(origin.offset(pos) + " expected=" + id + " actual=" + actualIds.get(origin.offset(pos)));
        });
        final List<String> states = new ArrayList<>();
        final List<BlockPos> wrongStates = new ArrayList<>();
        M42SlidingDoorFixtureLayout.movingPayload().forEach((pos, state) -> {
            final BlockPos absolute = origin.offset(pos);
            final BlockState actual = level.getBlockState(absolute);
            final BlockEntity blockEntity = level.getBlockEntity(absolute);
            states.add(absolute + " -> " + actual + " brittle=" + BlockMovementChecks.isBrittle(actual)
                    + " attachedDown=" + BlockMovementChecks.isBlockAttachedTowards(actual, level, absolute, Direction.DOWN)
                    + " blockEntity=" + (blockEntity == null ? "none" : blockEntity.getClass().getSimpleName()));
            try {
                if (!actual.equals(BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), state, false).blockState())) wrongStates.add(absolute);
            } catch (final com.mojang.brigadier.exceptions.CommandSyntaxException failure) { throw new IllegalStateException("invalid canonical payload state " + state, failure); }
        });
        final long halves = payload.getActors().stream().filter(p -> p.left.state().getBlock() instanceof DoorBlock).count();
        final boolean valid = assembled && included != null && rangeActual.equals(rangeExpected)
                && missing.isEmpty() && extra.isEmpty() && wrongBlocks.isEmpty() && wrongStates.isEmpty() && halves == 2;
        tell(level, "ATTACHMENT origin=" + origin.toShortString() + " chassisPos=" + chassisPos.toShortString()
                + " chassisState=" + chassisState + " range=" + (be instanceof ChassisBlockEntity chassis ? chassis.getRange() : "absent")
                + " expected=" + expected + " actual=" + actualIds.keySet() + " missing=" + missing + " extra=" + extra
                + " missingStates=" + missing.stream().map(pos -> pos + " -> " + level.getBlockState(pos)).toList()
                + " payloadStates=" + states + " wrongStates=" + wrongStates + " wrongBlocks=" + wrongBlocks
                + " chassisExpected=" + rangeExpected + " chassisActual=" + rangeActual
                + " stickyFaces=UP:sticky_north,SOUTH:sticky_east glue=NONE_DURING_NATIVE_CAPTURE"
                + " doorHalves=" + halves + " decision=" + (valid ? "NATIVE_CAPTURE_VALID" : "REJECT"));
        if (!valid) throw new IllegalStateException("native chassis/full-payload attachment incomplete missing=" + missing
                + " extra=" + extra + " wrongStates=" + wrongStates + " wrongBlocks=" + wrongBlocks
                + " chassisExpected=" + rangeExpected + " chassisActual=" + rangeActual + " doorHalves=" + halves);
    }

    private static int run(final CommandSourceStack source) {
        tell(source.getLevel(), "RUN phase=PRECHECK");
        final Lookup found = lookup(source.getLevel());
        if (found.fixture == null) return refuse(source, "RUN BLOCKED reason=OWNERSHIP_" + found.status);
        final Fixture f = found.fixture;
        final AbstractContraptionEntity vehicle = inner(f);
        tell(f.level, "RUN PRECHECK ownership=" + found.status + " outerAssembled=" + (f.body != null)
                + " controllerState=" + M35FixtureLookup.blockState(f.level, f.body, f.raw)
                + " controllerPresent=" + (piston(f) != null) + " controllerBE=" + (piston(f) == null ? "none" : piston(f).getClass().getSimpleName())
                + " innerContraptionPresent=" + (vehicle != null) + " doorActorPresent=" + (lowerActor(vehicle) != null)
                + " doorHalvesPresent=" + (lowerActor(vehicle) != null)
                + " kineticSpeed=" + (piston(f) == null ? "absent" : piston(f).getSpeed()) + " payload=" + payloadState(vehicle));
        if (f.body == null || !f.marker.getTags().contains(READY)) return refuse(source, "RUN BLOCKED reason=READY_ASSEMBLED_FIXTURE_REQUIRED");
        if (vehicle == null) return refuse(source, "RUN BLOCKED reason=INNER_CONTRAPTION_MISSING");
        if (lowerActor(vehicle) == null) return refuse(source, "RUN BLOCKED reason=DOOR_ACTOR_MISSING");
        if (!f.marker.getPersistentData().hasUUID("M42NestedVerified")
                || !f.marker.getPersistentData().getUUID("M42NestedVerified").equals(f.body.getUniqueId())) return refuse(source, "RUN BLOCKED reason=PRESERVATION_PREFLIGHT_PENDING");
        if (TASKS.containsKey(f.marker.getUUID())) return refuse(source, "RUN BLOCKED reason=LIFECYCLE_ALREADY_ACTIVE");
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(f.level);
        final RigidBodyHandle handle = physics == null ? null : physics.getPhysicsHandle(f.body);
        if (handle == null || !handle.isValid()) return refuse(source, "RUN REFUSED: fixture physics registration pending");
        hold(f);
        final Task task = new Task(f.level, f.marker.getUUID(), Phase.CAPTURE);
        TASKS.put(task.marker, task);
        drive(f, 0, true);
        seedNativeOpen(vehicle);
        tell(f.level, "RUN phase=WAIT_FOR_ACTOR");
        return 1;
    }

    private static int yaw(final CommandSourceStack source) {
        final Lookup found = lookup(source.getLevel());
        if (found.status != Status.ASSEMBLED || !found.fixture.marker.getPersistentData().getBoolean("M42Pass")
                || TASKS.containsKey(found.fixture.marker.getUUID())) return refuse(source, "YAW90 REFUSED: baseline PASS and idle assembled fixture required");
        final Fixture f = found.fixture;
        final var data = f.marker.getPersistentData();
        if (data.getBoolean("M42Yaw90")) return refuse(source, "YAW90 already applied; run or cleanup");
        final AbstractContraptionEntity vehicle = inner(f);
        final MovementContext actor = handoffActor(vehicle);
        if (actor == null || actor.position == null) return refuse(source, "YAW90 REFUSED: captured native actor unavailable");
        final Vec3 rawCenter = Vec3.atCenterOf(f.raw);
        final Vec3 visibleCenter = Vec3.atCenterOf(f.visible);
        final Vec3 before = f.body.logicalPose().transformPosition(actor.position);
        final Vec3 after = visibleCenter.add(before.z - visibleCenter.z, before.y - visibleCenter.y, -(before.x - visibleCenter.x));
        // +90 JOML yaw: (x,z) -> (z,-x), anchored at the fixture piston center.
        final var previous = SableSlidingDoorTarget.evidence(actor);
        if (previous == null) return refuse(source, "YAW90 REFUSED: native outward facing evidence unavailable");
        final Vec3 normal = new Vec3(previous.innerFacing().getStepX(), 0, previous.innerFacing().getStepZ());
        final Direction facing = Direction.getNearest(normal.z, 0, -normal.x);
        final BlockPos target = BlockPos.containing(after).relative(facing);
        try {
            validateStationDestination(f, target, facing);
            final boolean oldYaw = data.getBoolean("M42Yaw90");
            final BlockPos oldTarget = stationPos(f);
            final Direction oldFacing = stationFacing(f);
            try {
                data.putBoolean("M42Yaw90", true);
                hold(f);
                relocate(f, actor.state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? target.below() : target, facing);
                data.putBoolean("M42Pass", false);
            } catch (final RuntimeException failure) {
                data.putBoolean("M42Yaw90", oldYaw);
                hold(f);
                if (!stationPos(f).equals(oldTarget)) relocate(f, oldTarget, oldFacing);
                throw failure;
            }
            tell(f.level, "YAW90 COMPLETE parentFacing=" + facing + " target=" + target.toShortString() + "; /function sable:m42/run");
            return 1;
        } catch (final RuntimeException failure) {
            return refuse(source, "YAW90 REFUSED/ROLLED_BACK reason=" + failure.getMessage());
        }
    }

    private static int cleanup(final CommandSourceStack source) {
        final Lookup found = lookup(source.getLevel());
        if (found.status == Status.NONE) { tell(source.getLevel(), "CLEANUP COMPLETE alreadyClean=true"); return 1; }
        if (found.fixture == null) return refuse(source, "CLEANUP REFUSED: genuinely ambiguous ownership");
        final Task existing = TASKS.get(found.fixture.marker.getUUID());
        if (existing != null && existing.cleaning) { tell(source.getLevel(), "CLEANUP already in progress"); return 1; }
        final Task task = new Task(source.getLevel(), found.fixture.marker.getUUID(), Phase.RETRACT);
        task.cleaning = true;
        TASKS.put(task.marker, task);
        tell(task.level, "CLEANUP phase=RETRACT");
        return 1;
    }

    private static Lookup lookup(final ServerLevel level) {
        final List<ArmorStand> markers = new ArrayList<>();
        for (final Entity e : level.getAllEntities()) if (e instanceof ArmorStand m && m.getTags().contains(TAG)) markers.add(m);
        if (markers.isEmpty()) return new Lookup(Status.NONE, null);
        if (markers.size() != 1) return new Lookup(Status.AMBIGUOUS, null);
        final ArmorStand marker = markers.get(0);
        final var data = marker.getPersistentData();
        if (!data.contains("M42Origin") || !data.getString("M42Dimension").equals(level.dimension().location().toString())) return new Lookup(Status.AMBIGUOUS, null);
        final BlockPos visible = NbtUtils.readBlockPos(data.getCompound("M42Origin"));
        final Map<UUID, Fixture> matches = new LinkedHashMap<>();
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        boolean knownBodyPresent = false;
        if (container != null) for (final ServerSubLevel body : container.getAllSubLevels()) {
            boolean ownedElsewhere = false;
            for (final Entity entity : level.getAllEntities()) if (entity != marker && entity instanceof ArmorStand other
                    && other.getTags().stream().anyMatch(tag -> tag.startsWith("sable_m"))) {
                final CompoundTag otherData = other.getPersistentData();
                if (otherData.getAllKeys().stream().anyMatch(key -> otherData.hasUUID(key) && otherData.getUUID(key).equals(body.getUniqueId()))) ownedElsewhere = true;
            }
            if (ownedElsewhere) continue;
            final boolean known = data.hasUUID("M42Body");
            if (known && !data.getUUID("M42Body").equals(body.getUniqueId())) continue;
            if (known) knownBodyPresent = true;
            for (final var holder : body.getPlot().getLoadedChunks()) for (final BlockEntity be : holder.getChunk().getBlockEntities().values()) {
                if (!(be instanceof PhysicsAssemblerBlockEntity)) continue;
                final BlockPos raw = be.getBlockPos().subtract(M42SlidingDoorFixtureLayout.ASSEMBLER);
                if (!(M35FixtureLookup.blockEntity(level, body, raw) instanceof MechanicalPistonBlockEntity)
                        || !(M35FixtureLookup.blockEntity(level, body, raw.below(2)) instanceof CreativeMotorBlockEntity)) continue;
                final boolean ownerToken = be.getPersistentData().hasUUID("M42FixtureOwner")
                        && be.getPersistentData().getUUID("M42FixtureOwner").equals(marker.getUUID());
                if (!known && !ownerToken && body.logicalPose().transformPosition(Vec3.atCenterOf(raw)).distanceToSqr(Vec3.atCenterOf(visible)) > 4096) continue;
                final Fixture f = new Fixture(level, marker, visible, raw, body);
                final boolean door = M35FixtureLookup.blockState(level, body, raw.offset(M42SlidingDoorFixtureLayout.LOWER)).is(AllBlocks.ANDESITE_DOOR.get())
                        || lowerActor(inner(f)) != null;
                final boolean legacySignature = marker.getTags().contains(READY) && controllerSignature(f)
                        && SimAssemblyHelper.collectBlocks(level, body).stream().allMatch(p -> {
                            final String expected = M42SlidingDoorFixtureLayout.mechanism().get(p.subtract(raw));
                            return expected != null && BuiltInRegistries.BLOCK.getKey(M35FixtureLookup.blockState(level, body, p).getBlock()).toString().equals(M38DispenserFixtureLayout.blockId(expected));
                        });
                if (known || ownerToken || door || legacySignature) {
                    final Fixture previous = matches.putIfAbsent(body.getUniqueId(), f);
                    if (previous != null && !previous.raw.equals(raw)) return new Lookup(Status.AMBIGUOUS, null);
                }
            }
        }
        if (matches.size() > 1) return new Lookup(Status.AMBIGUOUS, null);
        if (matches.size() == 1) {
            final Fixture f = matches.values().iterator().next();
            data.putUUID("M42Body", f.body.getUniqueId());
            data.put("M42RawOrigin", NbtUtils.writeBlockPos(f.raw));
            return new Lookup(inner(f) != null || M35FixtureLookup.blockState(level, f.body, f.raw.offset(M42SlidingDoorFixtureLayout.LOWER)).is(AllBlocks.ANDESITE_DOOR.get())
                    ? Status.ASSEMBLED : Status.ASSEMBLED_RECOVERABLE, f);
        }
        if (knownBodyPresent) return new Lookup(Status.AMBIGUOUS, null);
        return new Lookup(Status.STATIC_PARTIAL, new Fixture(level, marker, visible, visible, null));
    }

    private static boolean controllerSignature(final Fixture f) {
        return M42SlidingDoorFixtureLayout.staticOuter().entrySet().stream()
                .allMatch(e -> BuiltInRegistries.BLOCK.getKey(M35FixtureLookup.blockState(f.level, f.body, f.raw.offset(e.getKey())).getBlock()).toString().equals(M38DispenserFixtureLayout.blockId(e.getValue())));
    }

    private static String payloadState(final AbstractContraptionEntity vehicle) {
        if (vehicle == null || vehicle.getContraption() == null) return "ABSENT";
        return "blocks=" + vehicle.getContraption().getBlocks().size() + ",doors=" + vehicle.getContraption().getBlocks().values().stream().filter(i -> i.state().is(AllBlocks.ANDESITE_DOOR.get())).count()
                + ",chests=" + vehicle.getContraption().getBlocks().values().stream().filter(i -> i.state().is(Blocks.CHEST)).count()
                + ",chassis=" + vehicle.getContraption().getBlocks().values().stream().filter(i -> i.state().is(AllBlocks.RADIAL_CHASSIS.get())).count();
    }

    private static void preservation(final Fixture f, final boolean after) {
        final var p = piston(f);
        final var vehicle = inner(f);
        final var data = f.marker.getPersistentData();
        final boolean valid = validPayload(vehicle) && p != null && p.running;
        final boolean original = data.hasUUID("M42LiveUuid");
        final boolean sameInstance = original && vehicle != null && vehicle.getUUID().equals(data.getUUID("M42LiveUuid")) && vehicle.getId() == data.getInt("M42LiveEntityId");
        tell(f.level, (after ? "INNER_POST_OUTER_ASSEMBLY" : "INNER_PRE_OUTER_ASSEMBLY")
                + " controllerPos=" + f.raw.toShortString() + " controllerState=" + M35FixtureLookup.blockState(f.level, f.body, f.raw)
                + " controllerPresent=" + (p != null) + " controllerBE=" + (p == null ? "none" : p.getClass().getName())
                + " controllerRunning=" + (p != null && p.running) + " controllerSpeed=" + (p == null ? "absent" : p.getSpeed())
                + " contraptionEntityUuid=" + (vehicle == null ? "none" : vehicle.getUUID()) + " contraptionEntityId=" + (vehicle == null ? "none" : vehicle.getId())
                + " contraptionClass=" + (vehicle == null ? "none" : vehicle.getContraption().getClass().getName())
                + " contraptionBlockCount=" + (vehicle == null ? 0 : vehicle.getContraption().getBlocks().size()) + " payload=" + payloadState(vehicle)
                + " containsChassis=" + contains(vehicle, "create:radial_chassis") + " containsChest=" + contains(vehicle, "minecraft:chest")
                + " containsDoorLower=" + (lowerActor(vehicle) != null) + " containsDoorUpper=" + (lowerActor(vehicle) != null)
                + " doorActorPresent=" + (lowerActor(vehicle) != null) + " innerContraptionPresent=" + (vehicle != null)
                + " originalUuid=" + (original ? data.getUUID("M42LiveUuid") : "UNKNOWN_PREVIOUS_VERSION")
                + " resolvedUuid=" + (vehicle == null ? "none" : vehicle.getUUID())
                + " sameInstance=" + sameInstance + " identityDecision=" + (vehicle == null ? "ENTITY_ABSENT" : after && !sameInstance ? "ENTITY_REPLACED" : "SOURCE_NATIVE_INSTANCE")
                + " entityClass=" + (vehicle == null ? "none" : vehicle.getClass().getName()) + " subLevel=" + (f.body == null ? "none" : f.body.getUniqueId())
                + " entityLevel=" + (vehicle == null ? "none" : vehicle.level().getClass().getName()) + " removed=" + (vehicle != null && vehicle.isRemoved())
                + " payloadPreservedCandidate=" + valid + " payloadPreserved=" + (after && valid && original)
                + " decision=" + (valid && (!after || original) ? "PASS" : "RECOVERABLE_MISSING_OR_UNPROVEN_PAYLOAD"));
        for (final var cell : M42SlidingDoorFixtureLayout.movingPayload().entrySet()) {
            final BlockPos raw = f.raw.offset(cell.getKey());
            final BlockEntity blockEntity = M35FixtureLookup.blockEntity(f.level, f.body, raw);
            tell(f.level, "INNER_BLOCK stage=" + (after ? "POST" : "PRE") + " relative=" + cell.getKey() + " raw=" + raw
                    + " state=" + M35FixtureLookup.blockState(f.level, f.body, raw) + " blockEntity=" + (blockEntity == null ? "none" : blockEntity.getClass().getName())
                    + " capturedInEntity=" + (vehicle != null && vehicle.getContraption().getBlocks().containsKey(raw.subtract(vehicle.getContraption().anchor))));
        }
        if (after && valid && original) data.putUUID("M42NestedVerified", f.body.getUniqueId());
    }

    private static boolean contains(final AbstractContraptionEntity vehicle, final String id) {
        return vehicle != null && vehicle.getContraption().getBlocks().values().stream().anyMatch(i -> BuiltInRegistries.BLOCK.getKey(i.state().getBlock()).toString().equals(id));
    }

    private static boolean validPayload(final AbstractContraptionEntity vehicle) {
        if (lowerActor(vehicle) == null) return false;
        final BlockPos controller = SableCreateContraptionContext.getControllerPos(vehicle);
        if (controller == null) return false;
        final Map<BlockPos, String> actual = new LinkedHashMap<>();
        vehicle.getContraption().getBlocks().values().forEach(i -> actual.put(i.pos().offset(vehicle.getContraption().anchor).subtract(controller), BuiltInRegistries.BLOCK.getKey(i.state().getBlock()).toString()));
        return actual.equals(M42SlidingDoorFixtureLayout.capturedBlockIds());
    }

    private static AbstractContraptionEntity inner(final Fixture f) {
        final MechanicalPistonBlockEntity piston = piston(f);
        final AbstractContraptionEntity vehicle = piston == null ? null : piston.movedContraption;
        if (vehicle instanceof ControlledContraptionEntity && !vehicle.isRemoved()
                && f.raw.equals(SableCreateContraptionContext.getControllerPos(vehicle))
                && SableCreateContraptionContext.getContainingSubLevel(vehicle) == f.body) return vehicle;
        return null;
    }

    private static MovementContext lowerActor(final AbstractContraptionEntity vehicle) {
        if (vehicle == null || !(vehicle.getContraption() instanceof PistonContraption)) return null;
        final List<MovementContext> actors = vehicle.getContraption().getActors().stream()
                .filter(p -> p.left.state().is(AllBlocks.ANDESITE_DOOR.get()) && p.left.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
                .map(p -> p.right).toList();
        if (actors.size() != 1) return null;
        final MovementContext lower = actors.get(0);
        final var upper = vehicle.getContraption().getBlocks().get(lower.localPos.above());
        return upper != null && upper.state().is(AllBlocks.ANDESITE_DOOR.get())
                && upper.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? lower : null;
    }

    private static MovementContext handoffActor(final AbstractContraptionEntity vehicle) {
        final MovementContext lower = lowerActor(vehicle);
        if (lower == null) return null;
        // The first native half toggles both. Do not assume actor-list iteration order.
        for (final var pair : vehicle.getContraption().getActors()) {
            if (pair.left.state().is(AllBlocks.ANDESITE_DOOR.get()) && pair.right != null
                    && SableSlidingDoorTarget.evidence(pair.right) != null) return pair.right;
        }
        return lower;
    }

    private static void arm(final Fixture f, final AbstractContraptionEntity vehicle, final MovementContext actor) {
        final var evidence = SableSlidingDoorTarget.evidence(actor);
        if (evidence == null || evidence.visible() == null || evidence.parentFacing() == null) throw new IllegalStateException("native outward geometry not yet observed");
        final var geometry = SableSlidingDoorTarget.resolve(actor, f.body, evidence.innerFacing());
        if (!geometry.accepted()) throw new IllegalStateException("unsupported fixture pose " + geometry.decision());
        final BlockPos target = BlockPos.containing(geometry.visible()).relative(geometry.facing());
        relocate(f, actor.state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? target.below() : target, geometry.facing());
        final Task task = TASKS.get(f.marker.getUUID());
        task.target = target;
        task.phase = Phase.DWELL;
        tell(f.level, "TARGET_RESOLVED parentFacing=" + geometry.facing() + " expectedTargetBlock=" + target.toShortString()
                + " beforeState=" + f.level.getBlockState(target) + " hiddenPlotTarget=false nativeCloseIn=40ticks");
    }

    private static void seedNativeClose(final Fixture f, final AbstractContraptionEntity vehicle) {
        seedNativeOpen(vehicle);
        final Task task = TASKS.get(f.marker.getUUID());
        task.armedAt = f.level.getGameTime();
        task.phase = Phase.OBSERVE;
    }

    private static void seedNativeOpen(final AbstractContraptionEntity vehicle) {
        for (final var pair : vehicle.getContraption().getActors()) {
            if (!pair.left.state().is(AllBlocks.ANDESITE_DOOR.get())) continue;
            final var info = vehicle.getContraption().getBlocks().get(pair.right.localPos);
            vehicle.setBlock(pair.right.localPos, new StructureBlockInfo(info.pos(), info.state().setValue(DoorBlock.OPEN, true), info.nbt()));
            pair.right.data.remove("Open"); // Seed the native initial-close test, not a production door controller.
        }
    }

    private static void advance(final Task task) {
        final Lookup lookup = lookup(task.level);
        if (lookup.fixture == null || !lookup.fixture.marker.getUUID().equals(task.marker)) throw new IllegalStateException("ownership unavailable " + lookup.status);
        final Fixture f = lookup.fixture;
        if (task.phase == Phase.PREPARE && !task.cleaning) {
            if (f.body != null) throw new IllegalStateException("outer assembled before live payload READY");
            if (++task.age > 200) throw new IllegalStateException("live piston preparation timeout; cleanup remains usable");
            final var live = inner(f);
            if (live == null) return;
            if (!validPayload(live)) throw new IllegalStateException("live native piston payload differs from validated topology");
            if (!task.paused) { drive(f, 0, true); task.paused = true; return; }
            if (piston(f).getSpeed() != 0 || ++task.settleTicks < 3) return;
            final SimAssemblyContraption outer = new SimAssemblyContraption(null);
            try {
                if (!outer.searchMovedStructure(f.level, f.visible.north(2)) || !outer.getBlocks().contains(f.visible)) throw new IllegalStateException("live controller omitted from outer selection");
            } catch (final com.simibubi.create.content.contraptions.AssemblyException failure) { throw new IllegalStateException("live outer selection failed", failure); }
            final Set<BlockPos> expectedStatic = new LinkedHashSet<>();
            M42SlidingDoorFixtureLayout.staticOuter().keySet().forEach(pos -> expectedStatic.add(f.visible.offset(pos)));
            if (!outer.getBlocks().equals(expectedStatic)) throw new IllegalStateException("live outer static topology differs expected=" + expectedStatic + " actual=" + outer.getBlocks());
            for (final BlockPos pos : outer.getBlocks()) {
                if (!pos.equals(f.visible) && M42SlidingDoorFixtureLayout.capturedBlockIds().containsKey(pos.subtract(f.visible))) throw new IllegalStateException("moving payload also selected as outer static block " + pos);
            }
            f.marker.getPersistentData().putUUID("M42LiveUuid", live.getUUID());
            f.marker.getPersistentData().putInt("M42LiveEntityId", live.getId());
            f.marker.addTag(READY);
            tell(f.level, "SETUP phase=READY staticOuterBlocks=" + outer.getBlocks().size() + " livePayloadBlocks=" + live.getContraption().getBlocks().size() + "; click Physics Assembler once, then WAIT for preservation PASS");
            preservation(f, false);
            TASKS.remove(task.marker);
            return;
        }
        if (!task.cleaning && lookup.status != Status.ASSEMBLED) throw new IllegalStateException("outer fixture removed during run " + lookup.status);
        if (++task.age > 1200) throw new IllegalStateException("native lifecycle timeout; cleanup remains retryable");
        final AbstractContraptionEntity vehicle = inner(f);
        if (task.cleaning) {
            switch (task.phase) {
                case RETRACT -> {
                    removeStation(f);
                    if (vehicle == null && piston(f) != null && piston(f).running) {
                        // Fixture-only repair of the old missing-vehicle state, never a payload reconstruction.
                        drive(f, 0, true);
                        piston(f).running = false; piston(f).assembleNextTick = false; piston(f).setChanged();
                        tell(f.level, "CLEANUP INNER_ALREADY_ABSENT danglingControllerReset=true payloadReconstructed=false");
                    }
                    if (vehicle == null && (piston(f) == null || !piston(f).running)) {
                        if (piston(f) != null) drive(f, 0, true);
                        task.phase = Phase.OUTER;
                        tell(f.level, "CLEANUP phase=INNER_DISASSEMBLED");
                    } else if (!task.reversed) {
                        mode(f, MovementMode.MOVE_PLACE_RETURNED);
                        drive(f, 32, false);
                        task.reversed = true;
                    }
                }
                case OUTER -> {
                    if (f.body != null) {
                        logCurrentRestoreObstructions(f);
                        f.marker.getPersistentData().putBoolean("M42Yaw90", false);
                        hold(f);
                        final Map<BlockPos, Residue> residue = new LinkedHashMap<>();
                        for (final BlockPos raw : SimAssemblyHelper.collectBlocks(f.level, f.body)) {
                            final BlockPos target = f.visible.offset(raw.subtract(f.raw));
                            final BlockState actual = f.level.getBlockState(target);
                            final BlockEntity occupantBE = f.level.getBlockEntity(target);
                            final boolean exactPayloadResidue = M42SlidingDoorFixtureLayout.movingPayload().keySet().stream().allMatch(p ->
                                    f.level.getBlockState(f.visible.offset(p)).equals(M35FixtureLookup.blockState(f.level, f.body, f.raw.offset(p))));
                            final boolean ownedPayloadBE = exactPayloadResidue && (occupantBE instanceof net.minecraft.world.Container inventory && inventory.isEmpty() && actual.is(Blocks.CHEST)
                                    || occupantBE instanceof ChassisBlockEntity && raw.subtract(f.raw).equals(M42SlidingDoorFixtureLayout.CHASSIS)
                                    || occupantBE instanceof com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlockEntity && raw.subtract(f.raw).equals(M42SlidingDoorFixtureLayout.LOWER));
                            if (!f.level.getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4)) throw new IllegalStateException("restore chunk unloaded " + target);
                            if (!actual.isAir() && actual.equals(M35FixtureLookup.blockState(f.level, f.body, raw))
                                    && (occupantBE == null || ownedPayloadBE)
                                    && M42SlidingDoorFixtureLayout.mechanism().containsKey(raw.subtract(f.raw))) {
                                residue.put(target, new Residue(actual, occupantBE == null ? null : occupantBE.saveWithFullMetadata()));
                            } else if (!actual.isAir()) {
                                tell(f.level, "CLEANUP RESTORE_BLOCKED pos=" + target + " state=" + actual + " blockEntity=" + f.level.getBlockEntity(target) + " ownership=UNPROVEN_PRESERVED");
                                throw new IllegalStateException("unrelated restore obstruction " + target + " " + f.level.getBlockState(target));
                            }
                        }
                        final var assembler = M35FixtureLookup.blockEntity(f.level, f.body, f.raw.offset(M42SlidingDoorFixtureLayout.ASSEMBLER));
                        if (!(assembler instanceof PhysicsAssemblerBlockEntity controller)) throw new IllegalStateException("outer assembler absent");
                        residue.keySet().forEach(p -> f.level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16 | 32));
                        try {
                            controller.assembleOrDisassemble(null);
                            if (SubLevelContainer.getContainer(f.level).getSubLevel(f.body.getUniqueId()) != null) throw new IllegalStateException("native outer disassembly did not complete; blockers restored, cleanup retryable");
                            f.marker.getPersistentData().putBoolean("M42OuterNativeCleanup", true);
                        } finally {
                            if (SubLevelContainer.getContainer(f.level).getSubLevel(f.body.getUniqueId()) != null) residue.forEach((p, s) -> {
                                f.level.setBlock(p, s.state, 2 | 16 | 32);
                                if (s.tag != null && f.level.getBlockEntity(p) != null) f.level.getBlockEntity(p).load(s.tag);
                            });
                        }
                        return; // Re-resolve ownership after the plot may have been removed.
                    }
                    task.phase = Phase.REMOVE;
                }
                case REMOVE -> {
                    if (vehicle != null || f.body != null) throw new IllegalStateException("active fixture cannot be deleted");
                    for (final var cell : M42SlidingDoorFixtureLayout.mechanism().entrySet()) {
                        final BlockState actual = f.level.getBlockState(f.visible.offset(cell.getKey()));
                        if (!actual.isAir() && !BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString().equals(M38DispenserFixtureLayout.blockId(cell.getValue()))) {
                            throw new IllegalStateException("unrelated footprint block preserved " + f.visible.offset(cell.getKey()));
                        }
                    }
                    for (final BlockPos p : M42SlidingDoorFixtureLayout.mechanism().keySet()) f.level.setBlock(f.visible.offset(p), Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
                    for (final SuperGlueEntity glue : f.level.getEntitiesOfClass(SuperGlueEntity.class, M42SlidingDoorFixtureLayout.glue(f.visible).inflate(.01))) {
                        final boolean tagged = glue.getTags().contains(GLUE) && glue.getPersistentData().hasUUID("M42Owner")
                                && glue.getPersistentData().getUUID("M42Owner").equals(task.marker);
                        // Native M22 round-trip recreates glue from bounds, not entity tags.
                        final boolean roundTrip = f.marker.getPersistentData().getBoolean("M42OuterNativeCleanup")
                                && glue.getBoundingBox().equals(M42SlidingDoorFixtureLayout.glue(f.visible));
                        if (tagged || roundTrip) glue.discard();
                    }
                    if (M42SlidingDoorFixtureLayout.mechanism().keySet().stream().anyMatch(p -> !f.level.getBlockState(f.visible.offset(p)).isAir())) throw new IllegalStateException("cleanup footprint not clear");
                    f.marker.discard(); TASKS.remove(task.marker);
                    tell(task.level, task.rollingBack ? "SETUP ROLLBACK COMPLETE footprintClear=true" : "CLEANUP COMPLETE");
                }
                default -> throw new IllegalStateException("invalid cleanup phase " + task.phase);
            }
            return;
        }
        final MovementContext actor = handoffActor(vehicle);
        if (task.phase == Phase.CAPTURE) {
            if (actor == null) return;
            drive(f, -32, true);
            if (actor.position == null || SableSlidingDoorTarget.evidence(actor) == null) return;
            tell(task.level, "ACTOR_CAPTURED vehicle=" + vehicle.getUUID() + " doorHalves=2 subLevel=" + f.body.getUniqueId());
            task.phase = Phase.SETTLE;
        } else if (task.phase == Phase.SETTLE) {
            if (actor == null || actor.position == null) throw new IllegalStateException("captured actor lost before settling");
            if (piston(f).getSpeed() != 0) return;
            if (++task.settleTicks >= 3) arm(f, vehicle, actor);
        } else if (task.phase == Phase.DWELL) {
            if (actor == null) throw new IllegalStateException("captured actor lost during visible target dwell");
            if (++task.dwellTicks >= 40) seedNativeClose(f, vehicle);
        } else if (task.phase == Phase.OBSERVE) {
            if (actor == null) throw new IllegalStateException("captured native door vehicle lost");
            final var e = SableSlidingDoorTarget.evidence(actor);
            if (e != null && e.gameTime() > task.armedAt && e.before() != null && e.after() != null) {
                final boolean hidden = Sable.HELPER.getContaining(f.level, e.target()) != null;
                final boolean physical = e.target().equals(task.target) && !hidden;
                final boolean pass = e.owner().equals(f.body.getUniqueId()) && physical
                        && e.before().getOptionalValue(DoorBlock.OPEN).orElse(false)
                        && !e.after().getOptionalValue(DoorBlock.OPEN).orElse(true)
                        && !f.level.getBlockState(task.target).getOptionalValue(DoorBlock.OPEN).orElse(true);
                tell(task.level, (!e.before().equals(e.after()) ? "MUTATION_COMMITTED" : "MUTATION_UNCHANGED")
                        + " target=" + e.target().toShortString() + " beforeState=" + e.before() + " afterState=" + e.after());
                tell(task.level, "actorCaptured=true nativeMutation=" + !e.before().equals(e.after()) + " parentFacing=" + e.parentFacing()
                        + " physicalTarget=" + physical + " hiddenPlotTarget=" + hidden + " " + (pass ? "PASS" : "INCOMPLETE"));
                f.marker.getPersistentData().putBoolean("M42Pass", pass);
                TASKS.remove(task.marker);
            }
        }
    }

    private static BlockPos stationPos(final Fixture f) {
        return f.marker.getPersistentData().contains("M42Station") ? NbtUtils.readBlockPos(f.marker.getPersistentData().getCompound("M42Station")) : f.visible.offset(M42SlidingDoorFixtureLayout.PARKED);
    }
    private static Direction stationFacing(final Fixture f) { return Direction.from3DDataValue(f.marker.getPersistentData().getInt("M42StationFacing")); }
    private static Map<BlockPos, String> stationCells(final Fixture f) { return f.marker.getPersistentData().contains("M42Station") ? M42SlidingDoorFixtureLayout.station(stationPos(f), stationFacing(f)) : Map.of(); }
    private static void validateStationDestination(final Fixture f, final BlockPos target, final Direction facing) {
        for (final BlockPos p : M42SlidingDoorFixtureLayout.station(target, facing).keySet()) {
            if (stationCells(f).containsKey(p)) continue;
            if (f.level.isOutsideBuildHeight(p) || !f.level.getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4)
                    || !f.level.getBlockState(p).isAir() || f.level.getBlockEntity(p) != null
                    || Sable.HELPER.getContaining(f.level, p) != null) throw new IllegalStateException("unrelated/hidden/unloaded station destination " + p + " " + f.level.getBlockState(p));
            for (final var body : Sable.HELPER.getAllIntersecting(f.level, new BoundingBox3d(new AABB(p)))) {
                if (body != f.body) throw new IllegalStateException("station overlaps another Sable " + p);
            }
            final AbstractContraptionEntity vehicle = inner(f);
            if (vehicle != null) for (final var info : vehicle.getContraption().getBlocks().values()) {
                final AABB payload = physicalCell(f, vehicle, info.pos());
                if (payload.deflate(1e-6).intersects(new AABB(p).deflate(1e-6))) throw new IllegalStateException("station intersects captured payload " + p);
            }
        }
    }
    private static AABB physicalCell(final Fixture f, final AbstractContraptionEntity vehicle, final BlockPos local) {
        Vec3 min = new Vec3(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        Vec3 max = new Vec3(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        for (int x = 0; x <= 1; x++) for (int y = 0; y <= 1; y++) for (int z = 0; z <= 1; z++) {
            final Vec3 visible = f.body.logicalPose().transformPosition(vehicle.toGlobalVector(new Vec3(local.getX() + x, local.getY() + y, local.getZ() + z), 1));
            min = new Vec3(Math.min(min.x, visible.x), Math.min(min.y, visible.y), Math.min(min.z, visible.z));
            max = new Vec3(Math.max(max.x, visible.x), Math.max(max.y, visible.y), Math.max(max.z, visible.z));
        }
        return new AABB(min, max);
    }
    private static void removeStation(final Fixture f) {
        final Map<BlockPos, String> cells = stationCells(f);
        for (final var cell : cells.entrySet()) {
            final BlockState actual = f.level.getBlockState(cell.getKey());
            if (!actual.isAir() && (!BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString().equals(M38DispenserFixtureLayout.blockId(cell.getValue()))
                    || f.level.getBlockEntity(cell.getKey()) != null)) throw new IllegalStateException("unrelated station damage preserved " + cell.getKey());
            if (actual.is(Blocks.IRON_DOOR) && (actual.getValue(DoorBlock.FACING) != stationFacing(f)
                    || actual.getValue(DoorBlock.HALF) != (cell.getKey().equals(stationPos(f)) ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER))) {
                throw new IllegalStateException("unrelated station facing/half change preserved " + cell.getKey());
            }
        }
        for (final BlockPos p : cells.keySet()) f.level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
    }
    private static void relocate(final Fixture f, final BlockPos target, final Direction facing) {
        validateStationDestination(f, target, facing);
        final Map<BlockPos, BlockState> snapshot = new LinkedHashMap<>();
        for (final BlockPos p : stationCells(f).keySet()) snapshot.put(p, f.level.getBlockState(p));
        final CompoundTag old = f.marker.getPersistentData().copy();
        final Map<BlockPos, String> next = M42SlidingDoorFixtureLayout.station(target, facing);
        removeStation(f);
        final List<BlockPos> placed = new ArrayList<>();
        try {
            for (final var cell : next.entrySet()) { placed.add(cell.getKey()); put(f.level, cell.getKey(), cell.getValue()); }
            if (!f.level.getBlockState(target).is(Blocks.IRON_DOOR) || !f.level.getBlockState(target.above()).is(Blocks.IRON_DOOR)) throw new IllegalStateException("station validation failed");
            f.marker.getPersistentData().put("M42Station", NbtUtils.writeBlockPos(target));
            f.marker.getPersistentData().putInt("M42StationFacing", facing.get3DDataValue());
        } catch (final RuntimeException failure) {
            for (final BlockPos p : placed) f.level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
            snapshot.forEach((p, s) -> f.level.setBlock(p, s, 2));
            f.marker.getPersistentData().merge(old);
            throw failure;
        }
    }
    private static void put(final ServerLevel level, final BlockPos pos, final String state) {
        try {
            if (!level.setBlock(pos, BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), state, false).blockState(), 2)) throw new IllegalStateException("placement failed " + pos);
        } catch (final com.mojang.brigadier.exceptions.CommandSyntaxException exception) { throw new IllegalStateException("invalid fixture state " + state, exception); }
    }
    private static boolean safeAir(final ServerLevel level, final BlockPos p) {
        return !level.isOutsideBuildHeight(p) && level.getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4)
                && level.getBlockState(p).isAir() && level.getBlockEntity(p) == null && Sable.HELPER.getContaining(level, p) == null
                && !Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(new AABB(p))).iterator().hasNext();
    }
    private static MechanicalPistonBlockEntity piston(final Fixture f) { return M35FixtureLookup.blockEntity(f.level, f.body, f.raw) instanceof MechanicalPistonBlockEntity p ? p : null; }
    private static void mode(final Fixture f, final MovementMode mode) {
        final var b = piston(f).getBehaviour(ScrollValueBehaviour.TYPE);
        if (!(b instanceof ScrollOptionBehaviour<?> option)) throw new IllegalStateException("native piston mode unavailable");
        option.setValue(mode.ordinal());
    }
    private static void drive(final Fixture f, final int speed, final boolean stopped) {
        if (!(M35FixtureLookup.blockEntity(f.level, f.body, f.raw.below(2)) instanceof CreativeMotorBlockEntity motor)) throw new IllegalStateException("fixture motor absent");
        motor.generatedSpeed.setValue(speed);
        M35RollerFixtureLifecycleCommands.setFixtureLever(f.level, f.body, f.raw, stopped);
    }
    private static void hold(final Fixture f) {
        if (f.body == null) return;
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(f.level);
        final RigidBodyHandle handle = physics == null ? null : physics.getPhysicsHandle(f.body);
        if (handle == null || !handle.isValid()) throw new IllegalStateException("fixture physics handle absent");
        final Quaterniond q = new Quaterniond().rotationY(f.marker.getPersistentData().getBoolean("M42Yaw90") ? Math.PI / 2 : 0);
        final Vec3 center = Vec3.atCenterOf(f.raw);
        final Vector3d local = new Vector3d(center.x, center.y, center.z).sub(f.body.logicalPose().rotationPoint()).mul(f.body.logicalPose().scale());
        final Vec3 visible = Vec3.atCenterOf(f.visible);
        handle.teleport(new Vector3d(visible.x, visible.y, visible.z).sub(q.transform(local)), q);
        handle.setLinearAndAngularVelocity(new Vector3d(), new Vector3d());
        physics.updatePose(f.body); f.body.updateBoundingBox(); f.body.updateLastPose();
    }
    private static void logCurrentRestoreObstructions(final Fixture f) {
        for (final BlockPos raw : SimAssemblyHelper.collectBlocks(f.level, f.body)) {
            final BlockPos currentTarget = BlockPos.containing(f.body.logicalPose().transformPosition(Vec3.atCenterOf(raw)));
            final BlockState occupant = f.level.getBlockState(currentTarget);
            if (!occupant.isAir()) tell(f.level, "CLEANUP CURRENT_RESTORE_OCCUPIED pos=" + currentTarget + " state=" + occupant
                    + " blockEntity=" + f.level.getBlockEntity(currentTarget) + " restoring=" + M35FixtureLookup.blockState(f.level, f.body, raw)
                    + " decision=PRESERVE_REANCHOR_TO_ORIGINAL_RESERVED_ORIGIN");
        }
    }

    private static void postPhysics(final ForgeSablePostPhysicsTickEvent event) {
        final Lookup f = lookup(event.getPhysicsSystem().getLevel());
        if (f.fixture != null && f.fixture.body != null && f.fixture.marker.getTags().contains(READY)) {
            final RigidBodyHandle handle = event.getPhysicsSystem().getPhysicsHandle(f.fixture.body);
            if (handle != null && handle.isValid()) {
                if (!f.fixture.marker.getPersistentData().getBoolean("M42RestorePoseObserved")) {
                    logCurrentRestoreObstructions(f.fixture);
                    f.fixture.marker.getPersistentData().putBoolean("M42RestorePoseObserved", true);
                }
                hold(f.fixture);
            }
        }
    }
    private static void tick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (final ServerLevel level : event.getServer().getAllLevels()) {
            final Lookup found = lookup(level);
            if (found.fixture != null && found.fixture.body != null && found.fixture.marker.getTags().contains(READY)) {
                final Fixture f = found.fixture;
                final CompoundTag data = f.marker.getPersistentData();
                if (!data.hasUUID("M42PostObservedBody") || !data.getUUID("M42PostObservedBody").equals(f.body.getUniqueId())) {
                    preservation(f, true);
                    data.putUUID("M42PostObservedBody", f.body.getUniqueId());
                }
            }
        }
        for (final Task task : List.copyOf(TASKS.values())) {
            if (task.level.getServer() != event.getServer()) continue;
            try { advance(task); }
            catch (final RuntimeException failure) {
                TASKS.remove(task.marker);
                final Lookup found = lookup(task.level);
                if (found.fixture != null && piston(found.fixture) != null) drive(found.fixture, 0, true);
                Sable.LOGGER.error("SABLE_M42_FIXTURE_EXCEPTION phase={} marker={}", task.phase, task.marker, failure);
                tell(task.level, (task.phase == Phase.PREPARE ? "SETUP" : task.cleaning ? "CLEANUP" : "RUN") + " FAILED reason=" + failure.getMessage() + "; cleanup remains retryable");
                if (task.phase == Phase.PREPARE && found.fixture != null) {
                    found.fixture.marker.removeTag(READY);
                    final Task rollback = new Task(task.level, task.marker, Phase.RETRACT);
                    rollback.cleaning = true; rollback.rollingBack = true;
                    TASKS.put(task.marker, rollback);
                    tell(task.level, "SETUP phase=ROLLBACK nativeRetractionRequired=true");
                }
            }
        }
    }
    private static void stopped(final ServerStoppedEvent event) { TASKS.entrySet().removeIf(e -> e.getValue().level.getServer() == event.getServer()); }
    private static void tell(final ServerLevel level, final String message) {
        Sable.LOGGER.info("[M42] {}", message);
        for (final var player : level.players()) player.sendSystemMessage(Component.literal("[M42] " + message));
    }
    private static int refuse(final CommandSourceStack source, final String message) { tell(source.getLevel(), message); source.sendFailure(Component.literal("[M42] " + message)); return 0; }
    private enum Status { NONE, STATIC_PARTIAL, ASSEMBLED, ASSEMBLED_RECOVERABLE, AMBIGUOUS }
    private enum Phase { PREPARE, CAPTURE, SETTLE, DWELL, OBSERVE, RETRACT, OUTER, REMOVE }
    private record Lookup(Status status, Fixture fixture) { }
    private record Fixture(ServerLevel level, ArmorStand marker, BlockPos visible, BlockPos raw, ServerSubLevel body) { }
    private record Residue(BlockState state, CompoundTag tag) { }
    private static final class Task {
        final ServerLevel level; final UUID marker; Phase phase;
        int age, settleTicks, dwellTicks; boolean cleaning, reversed, paused, rollingBack; long armedAt; BlockPos target;
        Task(final ServerLevel level, final UUID marker, final Phase phase) { this.level = level; this.marker = marker; this.phase = phase; }
    }
}
