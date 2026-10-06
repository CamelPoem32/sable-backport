package dev.ryanhcode.sable.command;

import com.mojang.brigadier.CommandDispatcher;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption.RotationMode;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.chassis.ChassisBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.ClutchBlockEntity;
import com.simibubi.create.content.kinetics.transmission.ClutchBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinMovementBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelBlockEditHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.compatibility.create.basin.SableBasinOutput;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.forge.event.ForgeSablePostPhysicsTickEvent;
import dev.ryanhcode.sable.mixin.compatibility.create.contraptions.MechanicalBearingBlockEntityAccessor;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
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
import java.util.HashSet;
import java.util.Set;

/** Fixture only: native bearing turns and native Basin dumps. Never creates an output ItemEntity. */
public final class M40BasinFixtureCommands {
    private static final String TAG = "sable_m40_origin";
    private static final String READY = "sable_m40_ready_v1";
    private static final String GLUE = "sable_m40_glue";
    private static final String OUTPUT = "sable_m40_output";
    private static final Map<UUID, Task> TASKS = new HashMap<>();
    private static final Map<UUID, String> OWNERSHIP_DIAGNOSTICS = new HashMap<>();

    private M40BasinFixtureCommands() { }

    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(M40BasinFixtureCommands::tick);
        MinecraftForge.EVENT_BUS.addListener(M40BasinFixtureCommands::postPhysics);
        MinecraftForge.EVENT_BUS.addListener(M40BasinFixtureCommands::stopped);
    }

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sable_m40").requires(source -> source.hasPermission(2))
                .then(Commands.literal("basin").executes(ctx -> setup(ctx.getSource())))
                .then(Commands.literal("run").executes(ctx -> run(ctx.getSource())))
                .then(Commands.literal("yaw90").executes(ctx -> yaw(ctx.getSource())))
                .then(Commands.literal("cleanup").executes(ctx -> cleanup(ctx.getSource()))));
    }

    private static int setup(final CommandSourceStack source) {
        final ServerLevel level = source.getLevel();
        if (lookup(level).status != Status.NONE) return refuse(source, "SETUP REFUSED: existing/ambiguous M40 ownership; run cleanup");
        BlockPos origin = null;
        int checked = 0;
        BlockPos rejected = null;
        for (final BlockPos offset : M38DispenserFixturePlacement.candidateOffsets()) {
            final BlockPos candidate = BlockPos.containing(source.getPosition()).above(5).offset(offset);
            checked++;
            boolean clear = true;
            for (final BlockPos relative : M40BasinFixtureLayout.reservation()) {
                final BlockPos cell = candidate.offset(relative);
                if (!safeAir(level, cell)) {
                    if (rejected == null) rejected = cell;
                    clear = false;
                    break;
                }
            }
            if (clear) { origin = candidate; break; }
        }
        if (origin == null) return refuse(source, "SETUP REFUSED: no valid loaded nearby origin checked=" + checked + " firstReject=" + rejected);
        final ArmorStand marker = new ArmorStand(level, origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        final CompoundTag nbt = new CompoundTag();
        nbt.putBoolean("Marker", true);
        nbt.putBoolean("Invisible", true);
        nbt.putBoolean("NoGravity", true);
        marker.load(nbt);
        marker.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        marker.addTag(TAG);
        marker.getPersistentData().put("M40Origin", NbtUtils.writeBlockPos(origin));
        marker.getPersistentData().putInt("M40Version", 1);
        marker.getPersistentData().putString("M40Dimension", level.dimension().location().toString());
        if (!level.addFreshEntity(marker) || level.getEntity(marker.getUUID()) != marker || !marker.blockPosition().equals(origin)) {
            marker.discard();
            return refuse(source, "SETUP FAILED: marker unavailable before BUILD");
        }
        tell(level, "SETUP origin=" + origin.toShortString() + " candidatesChecked=" + checked + " MARKER status=VALID");
        final List<BlockPos> placed = new ArrayList<>();
        SuperGlueEntity glue = null;
        tell(level, "SETUP phase=BUILD");
        try {
            for (final var cell : M40BasinFixtureLayout.all().entrySet()) {
                final BlockPos pos = origin.offset(cell.getKey());
                if (!safeAir(level, pos)) throw new IllegalStateException("reservation changed " + pos);
                final var state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), cell.getValue(), false).blockState();
                placed.add(pos);
                if (!level.setBlock(pos, state, 3)) throw new IllegalStateException("placement failed " + pos);
            }
            final Fixture fixture = new Fixture(level, marker, origin, origin, null);
            drive(fixture, 0, true);
            seed(fixture);
            tell(level, "SETUP phase=VALIDATE");
            if (!(level.getBlockEntity(origin) instanceof MechanicalBearingBlockEntity)
                    || !(level.getBlockEntity(origin.offset(M40BasinFixtureLayout.BASIN)) instanceof BasinBlockEntity)
                    || !(level.getBlockEntity(origin.offset(M40BasinFixtureLayout.ASSEMBLER)) instanceof PhysicsAssemblerBlockEntity)
                    || !level.getBlockState(origin.north(2)).is(Blocks.IRON_BLOCK)) throw new IllegalStateException("controller/Basin/assembler invalid");
            final ChassisBlockEntity chassis = (ChassisBlockEntity) level.getBlockEntity(origin.east());
            final List<BlockPos> included = chassis.getIncludedBlockPositions(Direction.EAST, false);
            if (included == null || !included.contains(origin.offset(M40BasinFixtureLayout.BASIN))) throw new IllegalStateException("native Basin attachment invalid " + included);
            final BearingContraption payload = new BearingContraption(false, Direction.EAST);
            if (!payload.assemble(level, origin) || payload.getBlocks().size() != 2
                    || payload.getBlocks().values().stream().filter(info -> AllBlocks.BASIN.has(info.state())).count() != 1) {
                throw new IllegalStateException("native payload must contain only chassis and Basin");
            }
            glue = new SuperGlueEntity(level, M40BasinFixtureLayout.glue(origin));
            glue.addTag(GLUE);
            glue.getPersistentData().putUUID("M40Owner", marker.getUUID());
            if (!level.addFreshEntity(glue) || !glue.contains(origin.north(2)) || glue.contains(origin.east())) throw new IllegalStateException("outer glue invalid");
            final SimAssemblyContraption outer = new SimAssemblyContraption(null);
            if (!outer.searchMovedStructure(level, origin.north(2)) || !outer.getBlocks().contains(origin)
                    || !outer.getBlocks().contains(origin.offset(M40BasinFixtureLayout.MOTOR))
                    || M40BasinFixtureLayout.platform().keySet().stream().anyMatch(p -> outer.getBlocks().contains(fixture.visible.offset(p)))) {
                throw new IllegalStateException("outer selection/controller/platform invalid");
            }
            for (final BlockPos offset : List.of(M40BasinFixtureLayout.MOTOR, M40BasinFixtureLayout.CLUTCH, BlockPos.ZERO)) {
                final BlockPos pos = origin.offset(offset);
                if (!outer.getBlocks().contains(pos) || !(level.getBlockEntity(pos) instanceof KineticBlockEntity kinetic)) {
                    throw new IllegalStateException("kinetic block/BE omitted from outer selection " + pos);
                }
                final CompoundTag captured = kinetic.saveWithFullMetadata();
                if (!captured.contains("id")) throw new IllegalStateException("missing kinetic BE serialization " + pos);
                tell(level, "CAPTURE_EXPECTATION pos=" + pos.toShortString() + " state=" + kinetic.getBlockState()
                        + " blockEntity=" + kinetic.getClass().getSimpleName() + " nbtCaptured=true selected=true");
            }
            tell(level, "ATTACHMENT innerNativeAttachmentValid=true basinCaptured=true outerSelectedBlocks=" + outer.getBlocks().size());
            neverPlace(fixture);
            reconnect(fixture);
            final Task baseline = new Task(level, marker.getUUID(), Phase.BASELINE);
            TASKS.put(baseline.marker, baseline);
            tell(level, "SETUP phase=VALIDATE awaiting native kinetic tick and LIVE bearing payload; READY not yet granted");
            return 1;
        } catch (final Exception exception) {
            marker.removeTag(READY);
            Sable.LOGGER.error("SABLE_M40_SETUP_EXCEPTION", exception);
            tell(level, "SETUP FAILED reason=" + exception.getMessage() + " phase=ROLLBACK");
            if (glue != null) glue.discard();
            clearTestContents(new Fixture(level, marker, origin, origin, null));
            for (final BlockPos pos : placed) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            final boolean clear = placed.stream().allMatch(p -> level.getBlockState(p).isAir());
            if (clear) marker.discard();
            tell(level, clear ? "SETUP ROLLBACK COMPLETE footprintClear=true" : "SETUP ROLLBACK INCOMPLETE ownershipRetained=true");
            return 0;
        }
    }

    private static boolean safeAir(final ServerLevel level, final BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                && level.getBlockState(pos).isAir() && level.getBlockEntity(pos) == null
                && Sable.HELPER.getContaining(level, pos) == null
                && !Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(new AABB(pos))).iterator().hasNext();
    }

    private static Lookup lookup(final ServerLevel level) {
        final List<ArmorStand> markers = new ArrayList<>();
        for (final Entity entity : level.getAllEntities()) if (entity instanceof ArmorStand marker && marker.getTags().contains(TAG)) markers.add(marker);
        if (markers.isEmpty()) return new Lookup(Status.NONE, null);
        if (markers.size() != 1) {
            for (final ArmorStand marker : markers) ownership(level, marker, marker.blockPosition(), markers.size(), null, "AMBIGUOUS", "MULTIPLE_M40_MARKERS count=" + markers.size());
            return new Lookup(Status.AMBIGUOUS, null);
        }
        final ArmorStand marker = markers.get(0);
        final CompoundTag data = marker.getPersistentData();
        if (!data.contains("M40Origin") || data.getInt("M40Version") != 1
                || !data.getString("M40Dimension").equals(level.dimension().location().toString())) {
            ownership(level, marker, marker.blockPosition(), 0, null, "AMBIGUOUS", "INVALID_MARKER_VERSION_OR_ORIGIN_OR_DIMENSION");
            return new Lookup(Status.AMBIGUOUS, null);
        }
        final BlockPos visible = NbtUtils.readBlockPos(data.getCompound("M40Origin"));
        final Map<UUID, Fixture> matches = new LinkedHashMap<>();
        final List<M40FixturePreconditions.BodyCandidate> candidates = new ArrayList<>();
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            for (final ServerSubLevel body : container.getAllSubLevels()) {
                if (data.hasUUID("M40Body") && !data.getUUID("M40Body").equals(body.getUniqueId())) continue;
                final boolean known = data.hasUUID("M40Body");
                final Set<BlockPos> origins = new HashSet<>();
                if (known && data.contains("M40RawOrigin")) origins.add(NbtUtils.readBlockPos(data.getCompound("M40RawOrigin")));
                // BE enumeration is independent of transient aggregate block/collider bounds.
                for (final var holder : body.getPlot().getLoadedChunks()) {
                    for (final BlockEntity entity : holder.getChunk().getBlockEntities().values()) {
                        if (entity instanceof PhysicsAssemblerBlockEntity) origins.add(entity.getBlockPos().subtract(M40BasinFixtureLayout.ASSEMBLER));
                    }
                }
                for (final BlockPos raw : origins) {
                    final Fixture candidate = new Fixture(level, marker, visible, raw, body);
                    final boolean identity = known || body.logicalPose().transformPosition(Vec3.atCenterOf(raw)).distanceToSqr(Vec3.atCenterOf(visible)) <= 16;
                    final boolean signature = controllerSignature(candidate) && bearing(candidate) != null
                            && M35FixtureLookup.blockEntity(level, body, raw.offset(M40BasinFixtureLayout.ASSEMBLER)) instanceof PhysicsAssemblerBlockEntity;
                    candidates.add(new M40FixturePreconditions.BodyCandidate(body.getUniqueId(), identity, signature));
                    if (identity && signature) {
                        final Fixture previous = matches.putIfAbsent(body.getUniqueId(), candidate);
                        if (previous != null && !previous.raw.equals(raw)) {
                            ownership(level, marker, visible, candidates.size(), null, "AMBIGUOUS", "MULTIPLE_CONTROLLERS_IN_OWNED_BODY");
                            return new Lookup(Status.AMBIGUOUS, null);
                        }
                    }
                }
            }
        }
        final List<UUID> selected = M40FixturePreconditions.matchingBodies(candidates);
        if (selected.size() > 1) {
            ownership(level, marker, visible, candidates.size(), null, "AMBIGUOUS", "MULTIPLE_MATCHING_M40_BODIES");
            return new Lookup(Status.AMBIGUOUS, null);
        }
        if (selected.size() == 1) {
            final Fixture match = matches.get(selected.get(0));
            data.putUUID("M40Body", match.body.getUniqueId());
            data.put("M40RawOrigin", NbtUtils.writeBlockPos(match.raw));
            ownership(level, marker, visible, candidates.size(), match, "ASSEMBLED", "NONE");
            return new Lookup(Status.ASSEMBLED, match);
        }
        if (data.hasUUID("M40Body") && container != null && container.getAllSubLevels().stream().anyMatch(body -> body.getUniqueId().equals(data.getUUID("M40Body")))) {
            ownership(level, marker, visible, candidates.size(), null, "AMBIGUOUS", "KNOWN_BODY_CONTROLLER_SIGNATURE_MISSING");
            return new Lookup(Status.AMBIGUOUS, null);
        }
        final Fixture fixture = new Fixture(level, marker, visible, visible, null);
        final boolean clear = M40BasinFixtureLayout.all().keySet().stream().allMatch(p -> level.getBlockState(visible.offset(p)).isAir());
        final Status status = clear ? Status.ORPHAN : marker.getTags().contains(READY) && controllerSignature(fixture) ? Status.STATIC : Status.PARTIAL;
        ownership(level, marker, visible, candidates.size(), fixture, status.toString(), "NONE");
        return new Lookup(status, fixture);
    }

    private static void ownership(final ServerLevel level, final ArmorStand marker, final BlockPos origin,
                                  final int candidates, final Fixture match, final String decision, final String reason) {
        final String signature = decision + ":" + reason + ":" + (match == null || match.body == null ? "none" : match.body.getUniqueId());
        if (signature.equals(OWNERSHIP_DIAGNOSTICS.put(marker.getUUID(), signature))) return;
        int markerCount = 0;
        for (final Entity entity : level.getAllEntities()) if (entity instanceof ArmorStand && entity.getTags().contains(TAG)) markerCount++;
        tell(level, "OWNERSHIP marker=" + marker.getUUID() + " markerCount=" + markerCount + " origin=" + origin.toShortString()
                + " outerBodyCandidates=" + candidates + " matchedBody=" + (match == null || match.body == null ? "absent" : match.body.getUniqueId())
                + " subLevel=" + (match != null && match.body != null) + " staticSignature=" + (match != null && match.body == null && controllerSignature(match))
                + " assembledSignature=" + (match != null && match.body != null && controllerSignature(match))
                + " decision=" + decision + " ambiguityReason=" + reason);
    }

    private static boolean controllerSignature(final Fixture fixture) {
        for (final var entry : M40BasinFixtureLayout.mechanism().entrySet()) {
            if (entry.getKey().getX() == 1) continue;
            final var actual = M35FixtureLookup.blockState(fixture.level, fixture.body, fixture.raw.offset(entry.getKey()));
            if (!BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString().equals(M38DispenserFixtureLayout.blockId(entry.getValue()))) return false;
        }
        return true;
    }

    private static MechanicalBearingBlockEntity bearing(final Fixture fixture) {
        return M35FixtureLookup.blockEntity(fixture.level, fixture.body, fixture.raw) instanceof MechanicalBearingBlockEntity bearing ? bearing : null;
    }

    private static ControlledContraptionEntity vehicle(final Fixture fixture) {
        final MechanicalBearingBlockEntity bearing = bearing(fixture);
        final Map<UUID, ControlledContraptionEntity> candidates = new LinkedHashMap<>();
        if (bearing != null && bearing.getMovedContraption() != null) candidates.put(bearing.getMovedContraption().getUUID(), bearing.getMovedContraption());
        for (final Entity entity : fixture.level.getAllEntities()) {
            if (entity instanceof ControlledContraptionEntity cce && fixture.raw.equals(SableCreateContraptionContext.getControllerPos(cce))) candidates.put(cce.getUUID(), cce);
        }
        ControlledContraptionEntity result = null;
        for (final ControlledContraptionEntity candidate : candidates.values()) {
            if (candidate.isRemoved() || candidate.level() != fixture.level || !(candidate.getContraption() instanceof BearingContraption)
                    || SableCreateContraptionContext.getContainingSubLevel(candidate) != fixture.body
                    || !fixture.raw.equals(SableCreateContraptionContext.getControllerPos(candidate))) continue;
            if (result != null) throw new IllegalStateException("ambiguous fixture bearing vehicles");
            result = candidate;
        }
        return result;
    }

    private static MovementContext actor(final ControlledContraptionEntity vehicle) {
        if (vehicle == null) return null;
        MovementContext found = null;
        for (final var pair : vehicle.getContraption().getActors()) {
            if (!AllBlocks.BASIN.has(pair.left.state())) continue;
            if (found != null) throw new IllegalStateException("ambiguous Basin actors");
            found = pair.right;
        }
        return found;
    }

    private static void seed(final Fixture fixture) {
        final BlockEntity entity = M35FixtureLookup.blockEntity(fixture.level, fixture.body, fixture.raw.offset(M40BasinFixtureLayout.BASIN));
        if (!(entity instanceof BasinBlockEntity basin)) throw new IllegalStateException("static Basin required to refill test");
        for (final var inventory : basin.getInvs()) for (int slot = 0; slot < inventory.getSlots(); slot++) inventory.setStackInSlot(slot, ItemStack.EMPTY);
        basin.getInvs().getFirst().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 4));
        basin.getInvs().getSecond().setStackInSlot(0, new ItemStack(Items.ANDESITE, 2));
        basin.setChanged();
        basin.sendData();
    }

    private static int count(final MovementContext context) {
        int total = 0;
        for (final var inventory : new BasinMovementBehaviour().getOrReadInventory(context).values()) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) total += inventory.getStackInSlot(slot).getCount();
        }
        return total;
    }

    private static void drive(final Fixture fixture, final int speed, final boolean stop) {
        final BlockEntity entity = M35FixtureLookup.blockEntity(fixture.level, fixture.body, fixture.raw.offset(M40BasinFixtureLayout.MOTOR));
        if (!(entity instanceof CreativeMotorBlockEntity motor)) throw new IllegalStateException("fixture motor missing");
        motor.generatedSpeed.setValue(speed);
        final BlockPos pos = fixture.raw.offset(M40BasinFixtureLayout.LEVER);
        final var state = M35FixtureLookup.blockState(fixture.level, fixture.body, pos);
        if (!(state.getBlock() instanceof LeverBlock)) throw new IllegalStateException("fixture lever missing");
        if (state.getValue(LeverBlock.POWERED) != stop) {
            final var changed = state.setValue(LeverBlock.POWERED, stop);
            if (fixture.body == null) {
                fixture.level.setBlock(pos, changed, 3);
            } else {
                final BlockPos local = pos.subtract(fixture.body.getPlot().getCenterBlock());
                fixture.body.getPlot().getEmbeddedLevelAccessor().setBlock(local, changed, 3);
                SubLevelBlockEditHelper.finalizeBlockChanges(fixture.body,
                        List.of(new SubLevelBlockEditHelper.BlockChange(local, pos, state, changed)));
            }
        }
        final BlockPos clutchPos = fixture.raw.offset(M40BasinFixtureLayout.CLUTCH);
        final var clutchState = M35FixtureLookup.blockState(fixture.level, fixture.body, clutchPos);
        if (!(clutchState.getBlock() instanceof ClutchBlock)) throw new IllegalStateException("fixture clutch missing");
        if (clutchState.getValue(ClutchBlock.POWERED) != stop) {
            KineticBlockEntity.switchToBlockState(fixture.level, clutchPos, clutchState.setValue(ClutchBlock.POWERED, stop));
        }
        if (M35FixtureLookup.blockEntity(fixture.level, fixture.body, clutchPos) instanceof ClutchBlockEntity clutch) clutch.attachKinetics();
    }

    private static void reconnect(final Fixture fixture) {
        neverPlace(fixture);
        drive(fixture, 0, false);
        // Native relocation reset removes old Source/network links without editing their fields/NBT.
        for (final BlockPos offset : List.of(BlockPos.ZERO, M40BasinFixtureLayout.CLUTCH, M40BasinFixtureLayout.MOTOR)) {
            final BlockPos pos = fixture.raw.offset(offset);
            final BlockEntity be = M35FixtureLookup.blockEntity(fixture.level, fixture.body, pos);
            if (!(be instanceof KineticBlockEntity)) throw new IllegalStateException("kinetic BE absent at " + pos);
            KineticBlockEntity.switchToBlockState(fixture.level, pos, be.getBlockState());
        }
        drive(fixture, 16, false);
        tell(fixture.level, "KINETIC_RECONNECT scope=OWNED_M40_FIXTURE nativeRelocationReset=true motorTransition=0->16");
    }

    private static String field(final Object target, final Class<?> declaring, final String name) {
        if (target == null) return "absent";
        if (!declaring.isInstance(target)) return "not_" + declaring.getSimpleName();
        try {
            final var field = declaring.getDeclaredField(name);
            if (!field.trySetAccessible()) return "unavailable";
            return String.valueOf(field.get(target));
        } catch (final ReflectiveOperationException | SecurityException exception) {
            return "unavailable:" + exception.getClass().getSimpleName();
        }
    }

    private static boolean kinetics(final Fixture fixture, final String stage) {
        final KineticBlockEntity[] blocks = new KineticBlockEntity[3];
        final List<BlockPos> offsets = List.of(M40BasinFixtureLayout.MOTOR, M40BasinFixtureLayout.CLUTCH, BlockPos.ZERO);
        final String[] names = {"SOURCE", "CLUTCH", "BEARING"};
        for (int i = 0; i < offsets.size(); i++) {
            final BlockPos pos = fixture.raw.offset(offsets.get(i));
            final var state = M35FixtureLookup.blockState(fixture.level, fixture.body, pos);
            final BlockEntity be = M35FixtureLookup.blockEntity(fixture.level, fixture.body, pos);
            if (be instanceof KineticBlockEntity kinetic) blocks[i] = kinetic;
            tell(fixture.level, stage + " component=" + names[i] + " pos=" + pos.toShortString() + " state=" + state
                    + " blockEntity=" + (be == null ? "absent" : be.getClass().getSimpleName())
                    + " beLevel=" + (be == null || be.getLevel() == null ? "absent" : be.getLevel().getClass().getSimpleName())
                    + " parentLevel=" + (be != null && be.getLevel() == fixture.level)
                    + " bePos=" + (be == null ? "absent" : be.getBlockPos()) + " initialized=" + field(be, SmartBlockEntity.class, "initialized")
                    + " network=" + (blocks[i] == null ? "absent" : blocks[i].network)
                    + " speed=" + (blocks[i] == null ? "absent" : blocks[i].getSpeed())
                    + " theoreticalSpeed=" + (blocks[i] == null ? "absent" : blocks[i].getTheoreticalSpeed())
                    + " source=" + (blocks[i] == null ? "absent" : blocks[i].source)
                    + " needsSpeedUpdate=" + (blocks[i] != null && blocks[i].needsSpeedUpdate())
                    + " generatedSpeed=" + (be instanceof CreativeMotorBlockEntity motor ? motor.getGeneratedSpeed() : "n/a"));
        }
        final boolean valid = M40FixturePreconditions.bearingReady(blocks[0] instanceof CreativeMotorBlockEntity,
                blocks[1] instanceof ClutchBlockEntity, blocks[2] instanceof MechanicalBearingBlockEntity,
                blocks[0] == null ? 0 : blocks[0].getSpeed(), blocks[1] == null ? 0 : blocks[1].getSpeed(), blocks[2] == null ? 0 : blocks[2].getSpeed(),
                blocks[0] == null ? null : blocks[0].network, blocks[1] == null ? null : blocks[1].network, blocks[2] == null ? null : blocks[2].network);
        final ClutchBlockEntity clutch = blocks[1] instanceof ClutchBlockEntity found ? found : null;
        final MechanicalBearingBlockEntity bearing = blocks[2] instanceof MechanicalBearingBlockEntity found ? found : null;
        tell(fixture.level, stage + " sourcePresent=" + (blocks[0] != null) + " sourceBlockEntityPresent=" + (blocks[0] instanceof CreativeMotorBlockEntity)
                + " sourceSpeed=" + (blocks[0] == null ? "absent" : blocks[0].getSpeed())
                + " sourceNetwork=" + (blocks[0] == null ? "absent" : blocks[0].network)
                + " clutchPresent=" + (clutch != null) + " clutchNetwork=" + (clutch == null ? "absent" : clutch.network)
                + " clutchSpeed=" + (clutch == null ? "absent" : clutch.getSpeed())
                + " clutchInputModifier=" + (clutch == null ? "absent" : clutch.getRotationSpeedModifier(Direction.WEST))
                + " clutchOutputModifier=" + (clutch == null ? "absent" : clutch.getRotationSpeedModifier(Direction.EAST))
                + " bearingPresent=" + (bearing != null) + " bearingBlockEntityPresent=" + (bearing != null)
                + " bearingNetwork=" + (bearing == null ? "absent" : bearing.network)
                + " bearingSpeed=" + (bearing == null ? "absent" : bearing.getSpeed()) + " networkValid=" + valid);
        return valid;
    }

    private static void neverPlace(final Fixture fixture) {
        final MechanicalBearingBlockEntity bearing = bearing(fixture);
        if (bearing == null) throw new IllegalStateException("bearing missing");
        ((MechanicalBearingBlockEntityAccessor) bearing).sable$getMovementMode().setValue(RotationMode.ROTATE_NEVER_PLACE.ordinal());
    }

    private static int run(final CommandSourceStack source) {
        final Lookup found = lookup(source.getLevel());
        if (found.status != Status.ASSEMBLED || !found.fixture.marker.getTags().contains(READY)) return refuse(source, "RUN REFUSED: valid assembled READY fixture required");
        final Fixture fixture = found.fixture;
        if (TASKS.containsKey(fixture.marker.getUUID())) return refuse(source, "RUN REFUSED: fixture lifecycle already running");
        if (!fixture.marker.getPersistentData().hasUUID("M40NestedVerified")
                || !fixture.marker.getPersistentData().getUUID("M40NestedVerified").equals(fixture.body.getUniqueId())) {
            kinetics(fixture, "KINETIC_NESTED");
            return refuse(source, "RUN BLOCKED reason=NESTED_PREFLIGHT_REQUIRED; cleanup and rebuild previous fixture");
        }
        final MovementContext existing = actor(vehicle(fixture));
        if (existing != null && count(existing) != 6) return refuse(source, "RUN REFUSED: payload already spent; yaw90 prepares second run");
        neverPlace(fixture);
        drive(fixture, 16, false);
        final MechanicalBearingBlockEntity bearing = bearing(fixture);
        final boolean valid = kinetics(fixture, "KINETIC_NESTED");
        tell(fixture.level, "BEARING_PRECONDITION bearingPresent=" + (bearing != null) + " bearingBlockEntityPresent=" + (bearing != null)
                + " speed=" + (bearing == null ? "absent" : bearing.getSpeed()) + " running=" + (bearing != null && bearing.isRunning())
                + " assembleNextTick=" + field(bearing, MechanicalBearingBlockEntity.class, "assembleNextTick"));
        if (!valid) {
            final String reason = bearing != null && bearing.getSpeed() == 0 ? "KINETIC_SPEED_ZERO" : "KINETIC_NETWORK_INVALID";
            drive(fixture, 16, true);
            return refuse(source, "RUN BLOCKED reason=" + reason);
        }
        final Task task = new Task(fixture.level, fixture.marker.getUUID(), Phase.CAPTURE);
        TASKS.put(task.marker, task);
        tell(fixture.level, "RUN phase=WAIT_FOR_NATIVE_BASIN_CONTRAPTION");
        return 1;
    }

    private static int yaw(final CommandSourceStack source) {
        final Lookup found = lookup(source.getLevel());
        if (found.status != Status.ASSEMBLED || TASKS.containsKey(found.fixture.marker.getUUID())) return refuse(source, "YAW90 REFUSED: idle assembled fixture required");
        if (!found.fixture.marker.getPersistentData().getBoolean("M40BaselinePass")) return refuse(source, "YAW90 REFUSED: baseline M40 PASS required; do not test yaw yet");
        final Task task = new Task(source.getLevel(), found.fixture.marker.getUUID(), Phase.STOP);
        task.yaw = true;
        TASKS.put(task.marker, task);
        tell(source.getLevel(), "YAW90 phase=RETURN_NATIVE_BASIN");
        return 1;
    }

    private static int cleanup(final CommandSourceStack source) {
        final Lookup found = lookup(source.getLevel());
        if (found.status == Status.NONE) { tell(source.getLevel(), "CLEANUP already clean"); return 1; }
        if (found.fixture == null) return refuse(source, "CLEANUP REFUSED: ownership=" + found.status);
        final Task previous = TASKS.get(found.fixture.marker.getUUID());
        if (previous != null && previous.cleaning) { tell(source.getLevel(), "CLEANUP already in progress"); return 1; }
        final Task task = new Task(source.getLevel(), found.fixture.marker.getUUID(), Phase.STOP);
        task.cleaning = true;
        TASKS.put(task.marker, task);
        tell(source.getLevel(), "CLEANUP phase=STOP ownership=" + found.status);
        return 1;
    }

    private static void tick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (final Task task : List.copyOf(TASKS.values())) {
            if (task.level.getServer() != event.getServer()) continue;
            try { advance(task); } catch (final RuntimeException exception) {
                Sable.LOGGER.error("SABLE_M40_FIXTURE_EXCEPTION phase={} marker={}", task.phase, task.marker, exception);
                if (task.phase == Phase.BASELINE) {
                    task.cleaning = true;
                    task.rollback = true;
                    task.phase = Phase.STOP;
                    task.age = 0;
                    tell(task.level, "SETUP FAILED reason=" + exception.getMessage() + " phase=ROLLBACK");
                    continue;
                }
                TASKS.remove(task.marker);
                tell(task.level, (task.cleaning ? "CLEANUP" : task.yaw ? "YAW90" : "RUN") + " FAILED reason=" + exception.getMessage() + "; cleanup remains retryable");
            }
        }
    }

    private static void advance(final Task task) {
        final Lookup found = lookup(task.level);
        if (found.fixture == null || !found.fixture.marker.getUUID().equals(task.marker)) throw new IllegalStateException("fixture ownership unavailable " + found.status);
        final Fixture fixture = found.fixture;
        if (++task.age > 800) throw new IllegalStateException("bounded native lifecycle timeout");
        final MechanicalBearingBlockEntity bearing = bearing(fixture);
        final ControlledContraptionEntity vehicle = vehicle(fixture);
        final MovementContext context = actor(vehicle);
        if (context != null) {
            for (final SableBasinOutput.Spawn spawn : SableBasinOutput.take(context)) {
                if (fixture.body == null || !spawn.owner().equals(fixture.body.getUniqueId())) throw new IllegalStateException("output owner mismatch");
                task.spawns.add(spawn);
                final Entity emitted = fixture.level.getEntity(spawn.uuid());
                if (emitted instanceof ItemEntity item) {
                    item.addTag(OUTPUT);
                    item.getPersistentData().putUUID("M40Owner", task.marker);
                }
                tell(task.level, "BASIN_OUTPUT basinLocal=" + spawn.local() + " basinRaw=" + spawn.raw()
                        + " basinVisible=" + spawn.expected() + " nativeOutputOffset=0 expectedSpawn=" + spawn.expected()
                        + " actualSpawn=" + spawn.actual() + " positionError=" + spawn.actual().distanceTo(spawn.expected())
                        + " parentWorldEntity=" + spawn.parent() + " hiddenPlotSpawn=" + spawn.hidden());
                tell(task.level, "ITEM_SPAWN uuid=" + spawn.uuid() + " count=" + spawn.count() + " velocity=" + spawn.velocity());
            }
        }
        switch (task.phase) {
            case BASELINE, NESTED -> {
                if (task.age == 1 || context != null) {
                    if (!kinetics(fixture, task.phase == Phase.BASELINE ? "KINETIC_BASELINE" : "KINETIC_NESTED")) {
                        throw new IllegalStateException("KINETIC_SPEED_ZERO_OR_NETWORK_INVALID");
                    }
                }
                if (context == null) {
                    if (task.age > 20) throw new IllegalStateException("NATIVE_BEARING_PAYLOAD_NOT_CAPTURED");
                    return;
                }
                if (count(context) != 6 || vehicle.getContraption().getBlocks().size() != 2
                        || bearing == null || !bearing.isNearInitialAngle()) throw new IllegalStateException("live native Basin payload preflight invalid");
                drive(fixture, 16, true);
                if (!bearing.isRunning() || vehicle.isRemoved()) throw new IllegalStateException("native NEVER_PLACE rotor was not retained");
                if (task.phase == Phase.BASELINE) {
                    fixture.marker.getPersistentData().putBoolean("M40LivePayload", true);
                    fixture.marker.addTag(READY);
                    tell(task.level, "NORMAL_WORLD_BASELINE sourceSpeedNonZero=true bearingRotates=true liveRotorRetained=true nativeCapture=SNAPSHOT_EXISTING");
                    tell(task.level, "SETUP phase=READY input=4 cobblestone output=2 andesite; click Physics Assembler once, then /function sable:m40/run");
                } else {
                    fixture.marker.getPersistentData().putUUID("M40NestedVerified", fixture.body.getUniqueId());
                    tell(task.level, "KINETIC_NESTED preflight=PASS liveRotorRetained=true waitingForRun=true clutchPowered=true bearingPaused=true");
                }
                TASKS.remove(task.marker);
            }
            case CAPTURE -> {
                if (context == null) return;
                task.before = count(context);
                task.vehicle = vehicle.getUUID();
                if (task.before != 6 || !task.spawns.isEmpty()) throw new IllegalStateException("native capture payload count mismatch " + task.before);
                tell(task.level, "BASIN_CAPTURED vehicle=" + task.vehicle + " inputOutputBefore=" + task.before
                        + " mountedBasinStorage=" + (context.getItemStorage() == null ? "NONE_NATIVE_BE_NBT" : "unexpected"));
                if (context.getItemStorage() != null) throw new IllegalStateException("unexpected parallel mounted Basin storage");
                tell(task.level, "NATIVE_OUTPUT_ATTEMPT awaiting native inversion while bearing turns");
                task.phase = Phase.OUTPUT;
            }
            case OUTPUT -> {
                if (vehicle == null || !vehicle.getUUID().equals(task.vehicle) || context == null) throw new IllegalStateException("captured Basin vehicle lost");
                if (task.spawns.isEmpty()) return;
                task.after = count(context);
                final boolean valid = task.before == 6 && task.after == 0 && Boolean.FALSE.equals(context.temporaryData)
                        && task.spawns.size() == 2 && task.spawns.stream().mapToInt(SableBasinOutput.Spawn::count).sum() == 6
                        && task.spawns.stream().allMatch(spawn -> spawn.added() && spawn.parent() && !spawn.hidden()
                        && spawn.actual().distanceTo(spawn.expected()) <= 1e-6
                        && spawn.velocity().distanceTo(spawn.expectedVelocity()) <= 1e-9)
                        && bearing != null && Math.abs(bearing.getInterpolatedAngle(0)) > 45;
                task.pass = valid;
                tell(task.level, "OUTPUT_CONFIRMED nativeDump=" + Boolean.FALSE.equals(context.temporaryData)
                        + " before=" + task.before + " after=" + task.after + " emittedStacks=" + task.spawns.size()
                        + " innerAngle=" + (bearing == null ? "unknown" : bearing.getInterpolatedAngle(0)));
                task.phase = Phase.STOP;
            }
            case STOP -> {
                if (bearing != null && vehicle != null) {
                    neverPlace(fixture);
                    drive(fixture, 16, true);
                    final float angle = bearing.getInterpolatedAngle(0);
                    task.returnSpeed = angle > 0 && angle <= 180 || angle < -180 ? -16 : 16;
                }
                task.phase = Phase.RETURN;
                task.returning = false;
            }
            case RETURN -> {
                if (vehicle != null) {
                    if (bearing == null) throw new IllegalStateException("owned vehicle has no controller");
                    if (bearing.isNearInitialAngle()) {
                        for (final BlockPos relative : List.of(new BlockPos(1, 0, 0), M40BasinFixtureLayout.BASIN)) {
                            if (!M35FixtureLookup.blockState(fixture.level, fixture.body, fixture.raw.offset(relative)).isAir()) {
                                throw new IllegalStateException("unrelated native rotor restore obstruction " + fixture.raw.offset(relative));
                            }
                        }
                        drive(fixture, 16, true);
                        // Native bearing disassembly restores its actual current transform; allow only near-home angles.
                        bearing.disassemble();
                    } else if (!task.returning) {
                        drive(fixture, task.returnSpeed, false);
                        task.returning = true;
                    }
                    return;
                }
                if (bearing != null && bearing.isRunning()) throw new IllegalStateException("running bearing without owned vehicle");
                tell(task.level, "DISASSEMBLY innerRestored=true");
                if (task.cleaning) { task.phase = fixture.body == null ? Phase.REMOVE : Phase.OUTER; return; }
                if (task.yaw) {
                    validateYawSpace(fixture);
                    final boolean oldYaw = fixture.marker.getPersistentData().getBoolean("M40Yaw90");
                    final BlockEntity be = M35FixtureLookup.blockEntity(fixture.level, fixture.body, fixture.raw.offset(M40BasinFixtureLayout.BASIN));
                    if (!(be instanceof BasinBlockEntity basin)) throw new IllegalStateException("restored Basin unavailable for yaw refill");
                    final CompoundTag oldContents = basin.saveWithFullMetadata();
                    try {
                        fixture.marker.getPersistentData().putBoolean("M40Yaw90", !oldYaw);
                        hold(fixture);
                        seed(fixture);
                    } catch (final RuntimeException exception) {
                        fixture.marker.getPersistentData().putBoolean("M40Yaw90", oldYaw);
                        hold(fixture);
                        basin.load(oldContents);
                        basin.setChanged();
                        basin.sendData();
                        throw new IllegalStateException("yaw rolled back: " + exception.getMessage(), exception);
                    }
                    tell(task.level, "YAW90 COMPLETE source=6 native Basin refill input=4 output=2; run /function sable:m40/run");
                } else {
                    final BlockEntity restored = M35FixtureLookup.blockEntity(fixture.level, fixture.body, fixture.raw.offset(M40BasinFixtureLayout.BASIN));
                    boolean empty = restored instanceof BasinBlockEntity;
                    if (restored instanceof BasinBlockEntity basin) {
                        for (final var inv : basin.getInvs()) for (int slot = 0; slot < inv.getSlots(); slot++) if (!inv.getStackInSlot(slot).isEmpty()) empty = false;
                    }
                    fixture.marker.getPersistentData().putBoolean("M40BaselinePass", task.pass && empty);
                    tell(task.level, "nativeOutput=" + !task.spawns.isEmpty() + " parentWorldEntity=" + task.pass + " physicalSpawnValid=" + task.pass
                            + " hiddenPlotSpawn=" + task.spawns.stream().anyMatch(SableBasinOutput.Spawn::hidden)
                            + " noDuplicate=" + (task.spawns.size() == 2 && empty) + " restoredContents=" + (empty ? 0 : "unknown")
                            + " " + (task.pass && empty ? "PASS" : "INCOMPLETE"));
                }
                TASKS.remove(task.marker);
            }
            case OUTER -> {
                if (fixture.body != null) {
                    fixture.marker.getPersistentData().putBoolean("M40Yaw90", false);
                    hold(fixture);
                    for (final BlockPos raw : SimAssemblyHelper.collectBlocks(fixture.level, fixture.body)) {
                        final BlockPos target = fixture.visible.offset(raw.subtract(fixture.raw));
                        if (!safeRestoreAir(fixture.level, target)) throw new IllegalStateException("unrelated restore obstruction " + target + " " + fixture.level.getBlockState(target));
                    }
                    final BlockEntity be = M35FixtureLookup.blockEntity(fixture.level, fixture.body, fixture.raw.offset(M40BasinFixtureLayout.ASSEMBLER));
                    if (!(be instanceof PhysicsAssemblerBlockEntity assembler)) throw new IllegalStateException("outer assembler missing");
                    tell(task.level, "CLEANUP phase=OUTER_DISASSEMBLE");
                    assembler.assembleOrDisassemble(null);
                    return;
                }
                // Outer ownership transfer can recreate a normal-world native bearing CCE.
                task.phase = Phase.STOP;
            }
            case REMOVE -> {
                if (fixture.body != null || vehicle != null) throw new IllegalStateException("active native ownership not removed");
                for (final var cell : M40BasinFixtureLayout.all().entrySet()) {
                    final var state = fixture.level.getBlockState(fixture.visible.offset(cell.getKey()));
                    if (!state.isAir() && !BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(M38DispenserFixtureLayout.blockId(cell.getValue()))) {
                        throw new IllegalStateException("unrelated static block preserved " + fixture.visible.offset(cell.getKey()) + " " + state);
                    }
                }
                // Remove native Basin test contents before deleting its static BE, avoiding native break drops.
                clearTestContents(fixture);
                for (final BlockPos relative : M40BasinFixtureLayout.all().keySet()) fixture.level.setBlock(fixture.visible.offset(relative), Blocks.AIR.defaultBlockState(), 3);
                for (final Entity entity : fixture.level.getAllEntities()) {
                    if ((entity instanceof SuperGlueEntity && entity.getTags().contains(GLUE) || entity instanceof ItemEntity && entity.getTags().contains(OUTPUT))
                            && entity.getPersistentData().hasUUID("M40Owner") && entity.getPersistentData().getUUID("M40Owner").equals(task.marker)) entity.discard();
                }
                if (!M40BasinFixtureLayout.all().keySet().stream().allMatch(p -> fixture.level.getBlockState(fixture.visible.offset(p)).isAir())) throw new IllegalStateException("post-cleanup footprint not clear");
                fixture.marker.discard();
                OWNERSHIP_DIAGNOSTICS.remove(task.marker);
                TASKS.remove(task.marker);
                tell(task.level, (task.rollback ? "SETUP ROLLBACK COMPLETE" : "CLEANUP COMPLETE") + " footprintClear=true stabilizerRemoved=true");
            }
        }
    }

    private static boolean safeRestoreAir(final ServerLevel level, final BlockPos cell) {
        return level.getChunkSource().hasChunk(cell.getX() >> 4, cell.getZ() >> 4) && level.getBlockState(cell).isAir();
    }

    private static void clearTestContents(final Fixture fixture) {
        final BlockEntity entity = M35FixtureLookup.blockEntity(fixture.level, fixture.body, fixture.raw.offset(M40BasinFixtureLayout.BASIN));
        if (entity instanceof BasinBlockEntity basin) {
            for (final var inv : basin.getInvs()) for (int slot = 0; slot < inv.getSlots(); slot++) inv.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    private static void validateYawSpace(final Fixture fixture) {
        final boolean nextYaw = !fixture.marker.getPersistentData().getBoolean("M40Yaw90");
        for (final BlockPos relative : M40BasinFixtureLayout.reservation()) {
            if (M40BasinFixtureLayout.platform().containsKey(relative)) continue;
            final BlockPos target = fixture.visible.offset(nextYaw ? new BlockPos(relative.getZ(), relative.getY(), -relative.getX()) : relative);
            if (!safeRestoreAir(fixture.level, target)) throw new IllegalStateException("yaw physical cell occupied " + target);
            for (final var body : Sable.HELPER.getAllIntersecting(fixture.level, new BoundingBox3d(new AABB(target)))) {
                if (body != fixture.body) throw new IllegalStateException("yaw overlaps unrelated Sable at " + target);
            }
        }
    }

    private static void hold(final Fixture fixture) {
        if (fixture.body == null) return;
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(fixture.level);
        final RigidBodyHandle handle = physics == null ? null : physics.getPhysicsHandle(fixture.body);
        if (handle == null || !handle.isValid()) throw new IllegalStateException("tagged fixture physics handle unavailable");
        final Quaterniond rotation = new Quaterniond().rotationY(fixture.marker.getPersistentData().getBoolean("M40Yaw90") ? Math.PI / 2 : 0);
        final Vec3 rawCenter = Vec3.atCenterOf(fixture.raw);
        final Vector3d local = new Vector3d(rawCenter.x, rawCenter.y, rawCenter.z).sub(fixture.body.logicalPose().rotationPoint()).mul(fixture.body.logicalPose().scale());
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
            final Fixture fixture = found.fixture;
            final CompoundTag data = fixture.marker.getPersistentData();
            if (data.getBoolean("M40LivePayload") && !TASKS.containsKey(fixture.marker.getUUID())
                    && (!data.hasUUID("M40NestedVerified") || !data.getUUID("M40NestedVerified").equals(fixture.body.getUniqueId()))
                    && !data.getBoolean("M40NestedAttempted")) {
                data.putBoolean("M40NestedAttempted", true);
                kinetics(fixture, "KINETIC_NESTED_BEFORE_RECONNECT");
                try {
                    reconnect(fixture);
                    TASKS.put(fixture.marker.getUUID(), new Task(fixture.level, fixture.marker.getUUID(), Phase.NESTED));
                } catch (final RuntimeException exception) {
                    Sable.LOGGER.error("SABLE_M40_NESTED_PREFLIGHT_EXCEPTION", exception);
                    tell(fixture.level, "RUN BLOCKED reason=" + exception.getMessage() + "; cleanup remains usable");
                }
            }
        }
    }

    private static void stopped(final ServerStoppedEvent event) {
        TASKS.entrySet().removeIf(entry -> entry.getValue().level.getServer() == event.getServer());
        OWNERSHIP_DIAGNOSTICS.clear();
    }

    private static void tell(final ServerLevel level, final String message) {
        for (final var player : level.players()) player.sendSystemMessage(Component.literal("[M40] " + message));
    }

    private static int refuse(final CommandSourceStack source, final String message) {
        source.sendFailure(Component.literal("[M40] " + message));
        return 0;
    }

    private enum Status { NONE, STATIC, ASSEMBLED, PARTIAL, ORPHAN, AMBIGUOUS }
    private enum Phase { BASELINE, NESTED, CAPTURE, OUTPUT, STOP, RETURN, OUTER, REMOVE }
    private record Lookup(Status status, Fixture fixture) { }
    private record Fixture(ServerLevel level, ArmorStand marker, BlockPos visible, BlockPos raw, ServerSubLevel body) { }
    private static final class Task {
        final ServerLevel level;
        final UUID marker;
        final List<SableBasinOutput.Spawn> spawns = new ArrayList<>();
        Phase phase;
        UUID vehicle;
        int age, before, after, returnSpeed = 16;
        boolean pass, returning, cleaning, yaw, rollback;
        Task(final ServerLevel level, final UUID marker, final Phase phase) { this.level = level; this.marker = marker; this.phase = phase; }
    }
}
