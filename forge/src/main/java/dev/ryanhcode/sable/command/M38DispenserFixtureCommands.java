package dev.ryanhcode.sable.command;

import com.mojang.brigadier.CommandDispatcher;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.dispenser.storage.DispenserMountedStorage;
import com.simibubi.create.content.contraptions.chassis.ChassisBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlockEntity;
import com.simibubi.create.content.contraptions.piston.PistonContraption;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.command.M38DispenserFixtureLayout.Status;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.compatibility.create.dispenser.SableMountedDispenserTarget;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
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
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
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
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Op-only, tagged fixture. It observes native mounted dispensing without performing it. */
public final class M38DispenserFixtureCommands {
    private static final String ORIGIN_TAG = "sable_m38_origin";
    private static final String GLUE_TAG = "sable_m38_glue";
    private static final String OUTPUT_TAG = "sable_m38_output";
    private static final String YAW_TAG = "sable_m38_yaw90";
    private static final String VERSION_TAG = "sable_m38_v2";
    private static final String ORIGIN_DATA = "M38Origin";
    private static final String BODY_DATA = "M38Body";
    private static final int TIMEOUT_TICKS = 600;
    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static final Map<UUID, Cleanup> CLEANUPS = new HashMap<>();
    private static final Map<UUID, Yaw> YAWS = new HashMap<>();

    private M38DispenserFixtureCommands() {
    }

    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(M38DispenserFixtureCommands::tick);
        MinecraftForge.EVENT_BUS.addListener(M38DispenserFixtureCommands::entityJoined);
        MinecraftForge.EVENT_BUS.addListener(M38DispenserFixtureCommands::postPhysicsTick);
        MinecraftForge.EVENT_BUS.addListener(M38DispenserFixtureCommands::serverStopped);
    }

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sable_m38").requires(source -> source.hasPermission(2))
                .then(Commands.literal("dropper").executes(context -> setup(context.getSource(), Kind.DROPPER)))
                .then(Commands.literal("dispenser_item")
                        .executes(context -> setup(context.getSource(), Kind.DISPENSER_ITEM)))
                .then(Commands.literal("projectile").executes(context -> setup(context.getSource(), Kind.PROJECTILE)))
                .then(Commands.literal("block").executes(context -> setup(context.getSource(), Kind.BLOCK)))
                .then(Commands.literal("run").executes(context -> run(context.getSource())))
                .then(Commands.literal("yaw90").executes(context -> yaw90(context.getSource())))
                .then(Commands.literal("cleanup").executes(context -> cleanup(context.getSource()))));
    }

    private static int setup(final CommandSourceStack source, final Kind kind) {
        final ServerLevel level = source.getLevel();
        if (lookup(level).status != Status.NONE) {
            return refuse(source, "SETUP REFUSED: existing M38 fixture; run /function sable:m38/cleanup");
        }
        final List<UUID> observers = observers(source);
        final BlockPos preferred = BlockPos.containing(source.getPosition()).above(4);
        final BlockPos legacyCell = preferred.offset(-12, -3, -16);
        final M38DispenserFixturePlacement.Category legacyCategory = M38DispenserFixturePlacement.classify(
                new BlockPos(-12, -3, -16), kind == Kind.BLOCK, kind == Kind.PROJECTILE);
        final M38DispenserFixturePlacement.Rejection legacy = inspectReservation(level, legacyCell,
                legacyCategory);
        tell(observers, level, "[M38] LEGACY_CELL pos=" + legacyCell.toShortString()
                + " category=" + legacyCategory + " chunkLoaded=" + (legacy == null || legacy.chunkLoaded())
                + " state=" + (legacy == null ? level.getBlockState(legacyCell) : legacy.state())
                + " fluid=" + (legacy == null ? level.getFluidState(legacyCell) : legacy.fluid())
                + " blockEntity=" + (legacy == null ? "none" : legacy.blockEntity())
                + " replaceable=" + (legacy == null || legacy.replaceable())
                + " reason=" + (legacy == null ? "UNRESERVED_AIR" : legacy.reason()));
        final M38DispenserFixturePlacement.SearchResult placement = M38DispenserFixturePlacement.find(
                preferred, kind == Kind.BLOCK, kind == Kind.PROJECTILE,
                (pos, category) -> inspectReservation(level, pos, category));
        for (final M38DispenserFixturePlacement.Rejection rejection : placement.rejected()) {
            tell(observers, level, "[M38] RESERVATION_REJECT pos=" + rejection.pos().toShortString()
                    + " category=" + rejection.category() + " chunkLoaded=" + rejection.chunkLoaded()
                    + " state=" + rejection.state() + " fluid=" + rejection.fluid()
                    + " blockEntity=" + rejection.blockEntity() + " replaceable=" + rejection.replaceable()
                    + " reason=" + rejection.reason());
        }
        if (placement.origin() == null) {
            final M38DispenserFixturePlacement.Rejection first = placement.rejected().get(0);
            return refuse(source, "SETUP REFUSED: no valid nearby fixture origin checked=" + placement.checked()
                    + " firstRejectPos=" + first.pos().toShortString() + " firstRejectState=" + first.state());
        }
        final BlockPos origin = placement.origin();
        tell(observers, level, "[M38] SETUP origin=" + origin.toShortString()
                + " candidatesChecked=" + placement.checked());
        final ArmorStand marker = createMarker(level, origin, kind);
        if (marker == null) {
            return refuse(source, "SETUP FAILED reason=ORIGIN_MARKER_MISSING before BUILD; no blocks placed");
        }
        tell(observers, level, "[M38] MARKER status=VALID uuid=" + marker.getUUID()
                + " origin=" + origin.toShortString());
        tell(observers, level, "[M38] SETUP phase=BUILD kind=" + kind);
        try {
            // A nested /function is queued by ServerFunctionManager, not executed synchronously.
            for (final var cell : M38DispenserFixtureLayout.blocks(kind == Kind.DROPPER, kind == Kind.BLOCK).entrySet()) {
                final BlockPos pos = origin.offset(cell.getKey());
                if (!level.getBlockState(pos).isAir()) {
                    throw new IllegalStateException("BUILD cell changed since reservation " + pos);
                }
                final BlockState state = BlockStateParser.parseForBlock(
                        BuiltInRegistries.BLOCK.asLookup(), cell.getValue(), false).blockState();
                level.setBlock(pos, state, 3);
                if (!level.getBlockState(pos).is(state.getBlock())) {
                    throw new IllegalStateException("BUILD placement failed " + pos);
                }
            }
        } catch (final com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException exception) {
            tell(observers, level, "[M38] SETUP FAILED reason=" + exception.getMessage());
            rollback(level, origin, kind, marker, observers);
            return 0;
        }
        final BlockPos actor = actorPos(origin);
        final CreativeMotorBlockEntity motor = level.getBlockEntity(origin.below(2))
                instanceof CreativeMotorBlockEntity controller ? controller : null;
        if (motor != null) motor.generatedSpeed.setValue(-64);
        if (level.getBlockEntity(actor) instanceof final Container inventory) {
            inventory.setItem(0, new ItemStack(kind.item(), kind.count()));
            inventory.setChanged();
        }
        if (kind == Kind.BLOCK) {
            level.setBlock(ignitionSupport(origin), Blocks.STONE.defaultBlockState(), 3);
        }
        tell(observers, level, "[M38] SETUP phase=VALIDATE");
        final String failure;
        try {
            failure = validate(level, origin, kind, observers);
        } catch (final RuntimeException exception) {
            Sable.LOGGER.error("SABLE_M38_SETUP_EXCEPTION origin={}", origin, exception);
            tell(observers, level, "[M38] SETUP FAILED reason=" + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            rollback(level, origin, kind, marker, observers);
            return 0;
        }
        if (failure != null) {
            tell(observers, level, "[M38] SETUP FAILED reason=" + failure);
            rollback(level, origin, kind, marker, observers);
            return 0;
        }
        marker.addTag(M38DispenserFixtureLayout.READY_TAG);
        final Lookup complete = lookup(level);
        if (complete.status != Status.STATIC_VALID || complete.fixture == null || complete.fixture.marker != marker) {
            marker.removeTag(M38DispenserFixtureLayout.READY_TAG);
            tell(observers, level, "[M38] SETUP FAILED reason=POST_BUILD_OWNERSHIP_" + complete.status + ": " + complete.reason);
            rollback(level, origin, kind, marker, observers);
            return 0;
        }
        tell(observers, level, "[M38] SETUP phase=READY outerSelectedBlocks=22 mounted="
                + kind.count() + " item=" + kind.item());
        tell(observers, level, "[M38] READY: click Physics Assembler, then /function sable:m38/run");
        return 1;
    }

    private static ArmorStand createMarker(final ServerLevel level, final BlockPos origin, final Kind kind) {
        final ArmorStand marker = new ArmorStand(level, origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        final CompoundTag data = new CompoundTag();
        data.putBoolean("Marker", true);
        data.putBoolean("Invisible", true);
        data.putBoolean("NoGravity", true);
        marker.load(data);
        marker.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        marker.addTag(ORIGIN_TAG);
        marker.addTag(VERSION_TAG);
        marker.addTag(kind.tag());
        marker.getPersistentData().put(ORIGIN_DATA, NbtUtils.writeBlockPos(origin));
        if (!level.addFreshEntity(marker) || level.getEntity(marker.getUUID()) != marker
                || !marker.blockPosition().equals(origin) || marker(level) != marker) {
            marker.discard();
            return null;
        }
        return marker;
    }

    private static void rollback(final ServerLevel level, final BlockPos origin, final Kind kind,
                                 final ArmorStand marker, final List<UUID> observers) {
        tell(observers, level, "[M38] SETUP phase=ROLLBACK");
        removeStatic(level, origin, kind, marker);
        final boolean clear = footprintClear(level, origin, kind);
        tell(observers, level, clear && marker(level) == null
                ? "[M38] SETUP ROLLBACK COMPLETE footprintClear=true markerCount=0"
                : "[M38] SETUP ROLLBACK INCOMPLETE: retained ownership; run /function sable:m38/cleanup");
    }

    private static M38DispenserFixturePlacement.Rejection inspectReservation(final ServerLevel level,
            final BlockPos pos, final M38DispenserFixturePlacement.Category category) {
        if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            return new M38DispenserFixturePlacement.Rejection(pos, category, false, "<unloaded>",
                    "<unloaded>", "none", false, M38DispenserFixturePlacement.Reason.UNLOADED);
        }
        final BlockState state = level.getBlockState(pos);
        final BlockEntity entity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        final String entityName = entity == null ? "none" : entity.getClass().getName();
        final M38DispenserFixturePlacement.Reason reason;
        if (entity != null || state.hasBlockEntity()) {
            reason = M38DispenserFixturePlacement.Reason.BLOCK_ENTITY;
        } else if (!state.isAir()) {
            reason = M38DispenserFixturePlacement.Reason.OCCUPIED;
        } else if (Sable.HELPER.getContaining(level, pos) != null
                || Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(new AABB(pos))).iterator().hasNext()) {
            reason = M38DispenserFixturePlacement.Reason.OTHER;
        } else {
            return null;
        }
        return new M38DispenserFixturePlacement.Rejection(pos, category, true, state.toString(),
                state.getFluidState().toString(), entityName, state.canBeReplaced(), reason);
    }

    private static String validate(final ServerLevel level, final BlockPos origin, final Kind kind,
                                   final List<UUID> observers) {
        if (!level.getBlockState(origin).is(AllBlocks.STICKY_MECHANICAL_PISTON.get())
                || !(level.getBlockEntity(origin) instanceof MechanicalPistonBlockEntity)
                || !(level.getBlockEntity(origin.below(2)) instanceof CreativeMotorBlockEntity)
                || !(level.getBlockEntity(origin.above().north(2)) instanceof PhysicsAssemblerBlockEntity)
                || !level.getBlockState(origin.north(2)).is(Blocks.IRON_BLOCK)) {
            return "CONTROLLER_OR_ASSEMBLER_STARTING_BLOCK_INVALID";
        }
        for (int x = -12; x <= -1; x++) {
            if (!level.getBlockState(origin.offset(x, 0, 0)).is(AllBlocks.PISTON_EXTENSION_POLE.get())) {
                return "PISTON_POLE_MISSING_" + x;
            }
        }
        if (!level.getBlockState(actorPos(origin)).is(kind.block())
                || !(level.getBlockEntity(actorPos(origin)) instanceof final Container inventory)
                || !inventory.getItem(0).is(kind.item()) || inventory.getItem(0).getCount() != kind.count()
                || !(level.getBlockEntity(origin.east()) instanceof final ChassisBlockEntity chassis)
                || !(level.getBlockEntity(origin.east().south()) instanceof Container)) {
            return "ACTOR_OR_MOUNTED_INVENTORY_INVALID";
        }
        final List<BlockPos> included = chassis.getIncludedBlockPositions(Direction.EAST, false);
        tell(observers, level, "[M38] ATTACHMENT innerNativeAttachmentValid="
                + (included != null && included.contains(actorPos(origin)) && included.contains(origin.east().south()))
                + " actorCaptured=" + (included != null && included.contains(actorPos(origin)))
                + " mountedChestCaptured=" + (included != null && included.contains(origin.east().south()))
                + " nativeIncludedBlocks=" + included);
        if (included == null || !included.contains(actorPos(origin))
                || !included.contains(origin.east().south())) {
            return "NATIVE_CHASSIS_ATTACHMENT_INCOMPLETE_" + included;
        }
        if (kind == Kind.BLOCK && !level.getBlockState(ignitionSupport(origin)).is(Blocks.STONE)) {
            return "PARENT_IGNITION_SUPPORT_MISSING";
        }
        final SuperGlueEntity glue = new SuperGlueEntity(level, outerGlueBounds(origin));
        glue.addTag(GLUE_TAG);
        if (!level.addFreshEntity(glue) || !glue.contains(origin.north(2))
                || !glue.contains(origin) || glue.contains(origin.east())) {
            return "OUTER_NATIVE_GLUE_INVALID";
        }
        tell(observers, level, "[M38] ATTACHMENT outerGlueValid=true bounds=" + glue.getBoundingBox()
                + " innerGlue=NOT_REQUIRED");
        final SimAssemblyContraption selection = new SimAssemblyContraption(null);
        try {
            if (!selection.searchMovedStructure(level, origin.north(2))) {
                return "OUTER_SELECTION_REJECTED";
            }
        } catch (final AssemblyException exception) {
            return "OUTER_SELECTION_EXCEPTION_" + exception.getClass().getSimpleName();
        }
        final var blocks = selection.getBlocks();
        if (blocks.size() != 22 || !blocks.contains(actorPos(origin))
                || !blocks.contains(origin.east().south())
                || blocks.contains(ignitionSupport(origin))) {
            return "OUTER_SELECTION_INVALID_" + blocks.size();
        }
        return null;
    }

    private static int run(final CommandSourceStack source) {
        final Lookup found = lookup(source.getLevel());
        if (found.status != Status.ASSEMBLED_VALID || found.fixture == null || !retracted(found.fixture)
                || CLEANUPS.containsKey(found.fixture.marker.getUUID())
                || YAWS.containsKey(found.fixture.marker.getUUID())) {
            return refuse(source, "RUN REFUSED: valid READY, assembled, retracted fixture required; ownership="
                    + found.status + " reason=" + found.reason);
        }
        final Fixture fixture = found.fixture;
        final int before = sourceCount(fixture);
        if (before != fixture.kind.count()) {
            return refuse(source, "RUN REFUSED: expected " + fixture.kind.count()
                    + " mounted items, found " + before);
        }
        final CreativeMotorBlockEntity motor = motor(fixture);
        if (motor == null) {
            return refuse(source, "RUN REFUSED: native motor missing");
        }
        hold(fixture, fixture.yaw(), Vec3.ZERO);
        final Run run = new Run(fixture.marker.getUUID(), fixture.level, observers(source), fixture.kind,
                before, damage(fixture), fixture.yaw());
        RUNS.put(run.markerId, run);
        motor.generatedSpeed.setValue(-64);
        M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body, fixture.origin, false);
        tell(run.observers, run.level, "[M38] RUN native piston engaged kind=" + run.kind
                + " sourceBefore=" + before + " yaw=" + Math.toDegrees(run.yaw));
        return 1;
    }

    private static int yaw90(final CommandSourceStack source) {
        final Lookup found = lookup(source.getLevel());
        if (found.status != Status.ASSEMBLED_VALID || found.fixture == null || found.fixture.kind != Kind.PROJECTILE
                || found.fixture.yaw() != 0 || CLEANUPS.containsKey(found.fixture.marker.getUUID())
                || YAWS.containsKey(found.fixture.marker.getUUID())) {
            return refuse(source, "YAW90 REFUSED: original assembled projectile fixture required");
        }
        RUNS.remove(found.fixture.marker.getUUID());
        YAWS.put(found.fixture.marker.getUUID(), new Yaw(found.fixture.marker.getUUID(), source.getLevel(),
                observers(source)));
        tell(observers(source), source.getLevel(), "[M38] YAW90 phase=RETRACT");
        return 1;
    }

    private static int cleanup(final CommandSourceStack source) {
        Lookup found = lookup(source.getLevel());
        ownership(source, found);
        if (found.status == Status.NONE) {
            tell(observers(source), source.getLevel(), "[M38] CLEANUP COMPLETE: fixture already absent");
            return 1;
        }
        if (found.fixture == null || found.status == Status.CORRUPT_AMBIGUOUS) {
            return refuse(source, "CLEANUP REFUSED: ownership status=" + found.status + " reason=" + found.reason);
        }
        if (found.fixture.marker == null) {
            final ArmorStand recovered = createMarker(source.getLevel(), found.fixture.visibleOrigin, found.fixture.kind);
            if (recovered == null) return refuse(source, "CLEANUP REFUSED: recovery marker creation failed");
            found = lookup(source.getLevel());
            if (found.fixture == null || found.status == Status.CORRUPT_AMBIGUOUS) {
                return refuse(source, "CLEANUP REFUSED: recovery identity changed " + found.reason);
            }
        }
        if (found.status == Status.ASSEMBLED_RECOVERABLE || found.status == Status.STATIC_PARTIAL) {
            tell(observers(source), source.getLevel(), "[M38] CLEANUP recovered previous partial/manual fixture");
        }
        final UUID id = found.fixture.marker.getUUID();
        if (CLEANUPS.containsKey(id)) {
            tell(observers(source), source.getLevel(), "[M38] CLEANUP: already in progress");
            return 1;
        }
        RUNS.remove(id);
        YAWS.remove(id);
        final Cleanup cleanup = new Cleanup(id, source.getLevel(), observers(source));
        if (found.fixture.body != null) inspectRestoreSpace(cleanup, found.fixture, false);
        if (found.fixture.visibleOrigin.equals(M38DispenserFixtureLayout.LEGACY_ORIGIN)) {
            for (final BlockPos pos : List.of(new BlockPos(-32, 96, 67), new BlockPos(-32, 96, 68))) {
                final boolean loaded = source.getLevel().getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
                tell(cleanup.observers, cleanup.level, "[M38] PREVIOUS_BLOCKER pos=" + pos.toShortString()
                        + " chunkLoaded=" + loaded + " state=" + (loaded ? cleanup.level.getBlockState(pos) : "<unloaded>")
                        + " blockEntity=" + (loaded ? cleanup.level.getBlockEntity(pos) : "<unloaded>")
                        + " ownership=UNPROVEN_PRESERVE action=NO_DELETE");
            }
        }
        CLEANUPS.put(id, cleanup);
        tell(observers(source), source.getLevel(), "[M38] CLEANUP phase=STOP status=" + found.status);
        return 1;
    }

    private static Lookup lookup(final ServerLevel level) {
        final List<ArmorStand> markers = new ArrayList<>();
        for (final Entity entity : level.getAllEntities()) {
            if (entity instanceof final ArmorStand stand && stand.getTags().contains(ORIGIN_TAG)) {
                markers.add(stand);
            }
        }
        if (markers.size() > 1) return new Lookup(Status.CORRUPT_AMBIGUOUS, null, "MULTIPLE_M38_MARKERS count=" + markers.size());
        final ArmorStand marker = markers.isEmpty() ? null : markers.get(0);
        final BlockPos origin = marker == null ? M38DispenserFixtureLayout.LEGACY_ORIGIN
                : marker.getPersistentData().contains(ORIGIN_DATA)
                ? NbtUtils.readBlockPos(marker.getPersistentData().getCompound(ORIGIN_DATA)) : marker.blockPosition();
        final Kind taggedKind = marker == null ? null : Kind.fromMarker(marker);
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        Fixture match = null;
        boolean nearbyIncompleteBody = false;
        if (container != null) {
            for (final ServerSubLevel body : container.getAllSubLevels()) {
                if (marker != null && marker.getPersistentData().hasUUID(BODY_DATA)
                        && !marker.getPersistentData().getUUID(BODY_DATA).equals(body.getUniqueId())) continue;
                for (final BlockPos candidate : SimAssemblyHelper.collectBlocks(level, body)) {
                    if (!M35FixtureLookup.blockState(level, body, candidate).is(AllBlocks.STICKY_MECHANICAL_PISTON.get())) continue;
                    final boolean persistedBody = marker != null && marker.getPersistentData().hasUUID(BODY_DATA);
                    if (!persistedBody && body.logicalPose().transformPosition(Vec3.atCenterOf(candidate))
                            .distanceToSqr(Vec3.atCenterOf(origin)) > 32 * 32) continue;
                    final boolean fullSignature = controllerSignature(level, body, candidate);
                    final boolean previousPartial = !ready(marker) && M38DispenserFixtureLayout.legacyPartialSignature(relative ->
                            BuiltInRegistries.BLOCK.getKey(M35FixtureLookup.blockState(level, body, candidate.offset(relative)).getBlock()).toString());
                    if ((fullSignature || previousPartial) && payloadCompatible(level, body, candidate, !ready(marker))) {
                        if (match != null) {
                            return new Lookup(Status.CORRUPT_AMBIGUOUS, null, "MULTIPLE_MATCHING_M38_BODIES");
                        }
                        final Kind kind = inferKind(level, body, candidate, taggedKind);
                        match = new Fixture(level, marker, candidate, body, kind, origin);
                    } else if (M35FixtureLookup.blockEntity(level, body, candidate.above().north(2)) instanceof PhysicsAssemblerBlockEntity
                            && payloadCompatible(level, body, candidate, !ready(marker))) {
                        nearbyIncompleteBody = true;
                    }
                }
            }
        }
        if (nearbyIncompleteBody) return new Lookup(Status.CORRUPT_AMBIGUOUS, match, "NEARBY_INCOMPLETE_CONTROLLER_SIGNATURE; body not deleted");
        if (match != null) {
            if (marker != null) marker.getPersistentData().putUUID(BODY_DATA, match.body.getUniqueId());
            return new Lookup(ready(marker) ? Status.ASSEMBLED_VALID : Status.ASSEMBLED_RECOVERABLE,
                    match, ready(marker) ? "READY_SIGNATURE_MATCH" : "LEGACY_CONTROLLER_SIGNATURE_MATCH_NO_READY");
        }
        if (!level.getChunkSource().hasChunk(origin.getX() >> 4, origin.getZ() >> 4)) {
            return new Lookup(marker == null ? Status.NONE : Status.CORRUPT_AMBIGUOUS, null, "ORIGIN_CHUNK_UNLOADED");
        }
        final Kind kind = inferKind(level, null, origin, taggedKind);
        final Fixture partial = new Fixture(level, marker, origin, null, kind, origin);
        final boolean signature = controllerSignature(level, null, origin) || M38DispenserFixtureLayout.legacyPartialSignature(relative -> {
            final BlockPos pos = origin.offset(relative);
            return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                    ? BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString() : "<unloaded>";
        });
        if (signature && !payloadCompatible(level, null, origin, !ready(marker))) {
            return new Lookup(marker == null ? Status.NONE : Status.CORRUPT_AMBIGUOUS, partial, "NON_M38_PAYLOAD_PRESERVED");
        }
        if (marker == null && !signature) return new Lookup(Status.NONE, null, "NO_MARKER_OR_LEGACY_SIGNATURE");
        if (!knownFootprint(partial)) return new Lookup(Status.CORRUPT_AMBIGUOUS, partial, "UNEXPECTED_STATIC_FOOTPRINT_BLOCK");
        final boolean clear = footprintClear(level, origin, kind);
        return new Lookup(M38DispenserFixtureLayout.classify(marker != null,
                signature, false, ready(marker), clear, false),
                partial, clear ? "MARKER_ONLY" : "KNOWN_STATIC_FOOTPRINT");
    }

    private static boolean ready(final ArmorStand marker) {
        return marker != null && marker.getTags().contains(M38DispenserFixtureLayout.READY_TAG);
    }

    private static boolean controllerSignature(final ServerLevel level, final ServerSubLevel body, final BlockPos origin) {
        final BlockEntity controller = M35FixtureLookup.blockEntity(level, body, origin);
        final AbstractContraptionEntity moving = innerContraption(level, body, origin);
        int movingPoles = 0;
        if (moving != null) {
            for (final var block : moving.getContraption().getBlocks().values()) {
                if (block.state().is(AllBlocks.PISTON_EXTENSION_POLE.get())) movingPoles++;
            }
        }
        int extendedPoles = 0;
        if (controller instanceof final MechanicalPistonBlockEntity piston && piston.offset > 0) {
            for (int x = 1; x <= 12; x++) {
                if (M35FixtureLookup.blockState(level, body, origin.east(x)).is(AllBlocks.PISTON_EXTENSION_POLE.get())) extendedPoles++;
            }
        }
        final boolean polesDisplaced = movingPoles >= 11 || extendedPoles >= 11;
        return M38DispenserFixtureLayout.controllerSignature(relative -> {
            final BlockPos pos = origin.offset(relative);
            if (body == null && !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return "<unloaded>";
            if (polesDisplaced && relative.getX() < 0 && relative.getY() == 0 && relative.getZ() == 0) {
                return "create:piston_extension_pole";
            }
            return BuiltInRegistries.BLOCK.getKey(M35FixtureLookup.blockState(level, body, pos).getBlock()).toString();
        }) && M35FixtureLookup.blockEntity(level, body, origin) instanceof MechanicalPistonBlockEntity
                && M35FixtureLookup.blockEntity(level, body, origin.below(2)) instanceof CreativeMotorBlockEntity
                && M35FixtureLookup.blockEntity(level, body, origin.above().north(2)) instanceof PhysicsAssemblerBlockEntity;
    }

    private static AbstractContraptionEntity innerContraption(final ServerLevel level, final ServerSubLevel body, final BlockPos origin) {
        final BlockEntity entity = M35FixtureLookup.blockEntity(level, body, origin);
        if (entity instanceof final MechanicalPistonBlockEntity piston && piston.movedContraption != null
                && !piston.movedContraption.isRemoved() && piston.movedContraption.getContraption() instanceof PistonContraption) {
            return piston.movedContraption;
        }
        final List<AbstractContraptionEntity> matches = level.getEntitiesOfClass(AbstractContraptionEntity.class,
                new AABB(origin).inflate(32), candidate -> !candidate.isRemoved()
                        && candidate.getContraption() instanceof PistonContraption
                        && origin.equals(SableCreateContraptionContext.getControllerPos(candidate))
                        && SableCreateContraptionContext.getContainingSubLevel(candidate) == body);
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static boolean payloadCompatible(final ServerLevel level, final ServerSubLevel body,
                                             final BlockPos origin, final boolean allowMissingActor) {
        final AbstractContraptionEntity moving = innerContraption(level, body, origin);
        if (moving != null) {
            boolean actor = false;
            boolean chest = false;
            boolean chassis = false;
            for (final var block : moving.getContraption().getBlocks().values()) {
                final BlockState state = block.state();
                actor |= state.is(Blocks.DROPPER) || state.is(Blocks.DISPENSER);
                chest |= state.is(Blocks.CHEST);
                chassis |= state.is(AllBlocks.RADIAL_CHASSIS.get());
                if (!state.is(Blocks.DROPPER) && !state.is(Blocks.DISPENSER) && !state.is(Blocks.CHEST)
                        && !state.is(AllBlocks.RADIAL_CHASSIS.get()) && !state.is(AllBlocks.PISTON_EXTENSION_POLE.get())
                        && !state.is(AllBlocks.MECHANICAL_PISTON_HEAD.get())) return false;
            }
            return chest && chassis && (actor || allowMissingActor);
        }
        final BlockEntity controller = M35FixtureLookup.blockEntity(level, body, origin);
        final int offset = controller instanceof MechanicalPistonBlockEntity piston ? Math.round(piston.offset) : 0;
        final BlockState actor = M35FixtureLookup.blockState(level, body, actorPos(origin).east(offset));
        if (!actor.is(Blocks.DROPPER) && !actor.is(Blocks.DISPENSER) && !(allowMissingActor && actor.isAir())) return false;
        // A Roller/Funnel fixture with a similar controller is not a legacy M38 candidate.
        if (!M35FixtureLookup.blockState(level, body, origin.east(offset + 1).below()).isAir()) return false;
        return M35FixtureLookup.blockState(level, body, origin.east(offset + 1)).is(AllBlocks.RADIAL_CHASSIS.get())
                && M35FixtureLookup.blockState(level, body, origin.east(offset + 1).south()).is(Blocks.CHEST);
    }

    private static Kind inferKind(final ServerLevel level, final ServerSubLevel body, final BlockPos origin, final Kind tagged) {
        if (tagged != null) return tagged;
        final BlockEntity controller = M35FixtureLookup.blockEntity(level, body, origin);
        if (controller instanceof final MechanicalPistonBlockEntity piston && piston.movedContraption != null
                && piston.movedContraption.getContraption() != null) {
            for (final var actor : piston.movedContraption.getContraption().getActors()) {
                if (actor.getLeft().state().is(Blocks.DISPENSER)) return Kind.DISPENSER_ITEM;
            }
        }
        return M35FixtureLookup.blockState(level, body, actorPos(origin)).is(Blocks.DISPENSER)
                ? Kind.DISPENSER_ITEM : Kind.DROPPER;
    }

    private static void ownership(final CommandSourceStack source, final Lookup found) {
        final Fixture fixture = found.fixture;
        int markerCount = 0;
        for (final Entity entity : source.getLevel().getAllEntities()) {
            if (entity instanceof ArmorStand && entity.getTags().contains(ORIGIN_TAG)) markerCount++;
        }
        tell(observers(source), source.getLevel(), "[M38] OWNERSHIP status=" + found.status
                + " origin=" + (fixture == null ? "unknown" : fixture.visibleOrigin.toShortString())
                + " markerCount=" + markerCount
                + " assembler=" + (fixture == null ? "unknown" : fixture.origin.above().north(2).toShortString()
                + "/" + M35FixtureLookup.blockState(fixture.level, fixture.body, fixture.origin.above().north(2)))
                + " outerSable=" + (fixture == null || fixture.body == null ? "absent" : fixture.body.getUniqueId())
                + " innerPiston=" + (fixture == null ? "unknown" : M35FixtureLookup.blockEntity(fixture.level, fixture.body, fixture.origin))
                + " actor=" + (fixture == null ? "unknown" : M35FixtureLookup.blockState(fixture.level, fixture.body, actorPos(fixture.origin)))
                + " mountedChest=" + (fixture == null ? "unknown" : M35FixtureLookup.blockState(fixture.level, fixture.body, fixture.origin.east().south()))
                + " signatureMatches=" + (fixture != null && controllerSignature(fixture.level, fixture.body, fixture.origin))
                + " rejectReason=" + found.reason);
    }

    private static boolean knownFootprint(final Fixture fixture) {
        final ServerLevel level = fixture.level;
        final BlockPos origin = fixture.origin;
        for (int x = -12; x <= -1; x++) {
            if (!expectedOrAir(level, origin.offset(x, 0, 0), AllBlocks.PISTON_EXTENSION_POLE.get())) return false;
        }
        return expectedOrAir(level, origin, AllBlocks.STICKY_MECHANICAL_PISTON.get())
                && expectedOrAir(level, origin.east(), AllBlocks.RADIAL_CHASSIS.get())
                && expectedOrAir(level, actorPos(origin), fixture.kind.block())
                && expectedOrAir(level, origin.east().south(), Blocks.CHEST)
                && expectedOrAir(level, origin.below(2), AllBlocks.CREATIVE_MOTOR.get())
                && expectedOrAir(level, origin.below(), AllBlocks.CLUTCH.get())
                && expectedOrAir(level, origin.below().south(), Blocks.LEVER)
                && expectedOrAir(level, origin.north(), Blocks.IRON_BLOCK)
                && expectedOrAir(level, origin.north(2), Blocks.IRON_BLOCK)
                && expectedOrAir(level, origin.above().north(2),
                        dev.simulated_team.simulated.index.SimulatedBlocks.PHYSICS_ASSEMBLER.get())
                && (fixture.kind != Kind.BLOCK
                        || expectedOrAir(level, ignitionSupport(origin), Blocks.STONE));
    }

    private static boolean expectedOrAir(final ServerLevel level, final BlockPos pos, final Block expected) {
        return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                && (level.getBlockState(pos).isAir() || level.getBlockState(pos).is(expected));
    }

    private static BlockPos actorPos(final BlockPos origin) {
        return origin.east().above();
    }

    private static BlockPos ignitionSupport(final BlockPos origin) {
        return origin.offset(14, 0, 0);
    }

    private static boolean retracted(final Fixture fixture) {
        if (!(M35FixtureLookup.blockEntity(fixture.level, fixture.body, fixture.origin)
                instanceof final MechanicalPistonBlockEntity piston)
                || piston.running || piston.movedContraption != null
                || !M35FixtureLookup.blockState(fixture.level, fixture.body, fixture.origin)
                        .toString().contains("state=retracted")) {
            return false;
        }
        return M35FixtureLookup.blockState(fixture.level, fixture.body, actorPos(fixture.origin))
                .is(fixture.kind.block());
    }

    private static CreativeMotorBlockEntity motor(final Fixture fixture) {
        final BlockEntity entity = M35FixtureLookup.blockEntity(fixture.level, fixture.body,
                fixture.origin.below(2));
        return entity instanceof final CreativeMotorBlockEntity motor ? motor : null;
    }

    private static int sourceCount(final Fixture fixture) {
        final MechanicalPistonBlockEntity piston = M35FixtureLookup.blockEntity(fixture.level, fixture.body,
                fixture.origin) instanceof MechanicalPistonBlockEntity controller ? controller : null;
        if (piston != null && piston.movedContraption != null && piston.movedContraption.getContraption() != null) {
            int result = 0;
            for (final var storage : piston.movedContraption.getContraption().getStorage()
                    .getAllItemStorages().values()) {
                if (storage instanceof DispenserMountedStorage) {
                    for (int slot = 0; slot < storage.getSlots(); slot++) {
                        if (storage.getStackInSlot(slot).is(fixture.kind.item())) {
                            result += storage.getStackInSlot(slot).getCount();
                        }
                    }
                }
            }
            return result;
        }
        final int offset = piston == null ? 0 : Math.round(piston.offset);
        final BlockEntity entity = M35FixtureLookup.blockEntity(fixture.level, fixture.body,
                actorPos(fixture.origin).east(offset));
        if (!(entity instanceof final Container container)) {
            return -1;
        }
        int result = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(fixture.kind.item())) {
                result += container.getItem(slot).getCount();
            }
        }
        return result;
    }

    private static int damage(final Fixture fixture) {
        final MechanicalPistonBlockEntity piston = M35FixtureLookup.blockEntity(fixture.level, fixture.body,
                fixture.origin) instanceof MechanicalPistonBlockEntity controller ? controller : null;
        if (piston != null && piston.movedContraption != null && piston.movedContraption.getContraption() != null) {
            for (final var storage : piston.movedContraption.getContraption().getStorage()
                    .getAllItemStorages().values()) {
                if (storage instanceof DispenserMountedStorage) {
                    for (int slot = 0; slot < storage.getSlots(); slot++) {
                        if (storage.getStackInSlot(slot).is(Items.FLINT_AND_STEEL)) {
                            return storage.getStackInSlot(slot).getDamageValue();
                        }
                    }
                }
            }
        }
        final BlockEntity entity = M35FixtureLookup.blockEntity(fixture.level, fixture.body,
                actorPos(fixture.origin));
        return entity instanceof Container container ? container.getItem(0).getDamageValue() : -1;
    }

    private static SableMountedDispenserTarget.Result actorTarget(final Fixture fixture) {
        final BlockEntity controller = M35FixtureLookup.blockEntity(fixture.level, fixture.body, fixture.origin);
        if (!(controller instanceof final MechanicalPistonBlockEntity piston)
                || piston.movedContraption == null || piston.movedContraption.getContraption() == null) {
            return null;
        }
        for (final var actor : piston.movedContraption.getContraption().getActors()) {
            if (actor.getLeft().state().is(fixture.kind.block())) {
                return SableMountedDispenserTarget.resolve(actor.getRight(), fixture.body);
            }
        }
        return null;
    }

    private static void entityJoined(final EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof final ServerLevel level)) {
            return;
        }
        for (final Run run : RUNS.values()) {
            if (run.level != level || run.outputId != null || run.kind == Kind.BLOCK
                    || run.kind == Kind.PROJECTILE && !(event.getEntity() instanceof AbstractArrow)
                    || run.kind != Kind.PROJECTILE && !(event.getEntity() instanceof ItemEntity item
                            && item.getItem().is(run.kind.item()))) {
                continue;
            }
            final Lookup found = lookup(level);
            if (found.status != Status.ASSEMBLED_VALID || found.fixture == null
                    || !found.fixture.marker.getUUID().equals(run.markerId)) {
                continue;
            }
            final SableMountedDispenserTarget.Result target = actorTarget(found.fixture);
            if (target == null || !target.accepted()
                    || event.getEntity().position().distanceToSqr(Vec3.atCenterOf(target.source())) > 9) {
                continue;
            }
            final Entity output = event.getEntity();
            output.addTag(OUTPUT_TAG);
            run.outputId = output.getUUID();
            run.spawnPosition = output.position();
            run.spawnVelocity = output.getDeltaMovement();
            run.spawnSource = target.source();
            run.parentFacing = target.facing();
            run.hiddenPlotSpawn = Sable.HELPER.getContaining(level, output.position()) != null;
            tell(run.observers, level, "[M38] " + run.kind + " event="
                    + (run.kind == Kind.PROJECTILE ? "PROJECTILE_SPAWN" : "ITEM_SPAWN")
                    + " entity=" + output.getUUID() + " spawnPosition=" + output.position()
                    + " velocity=" + output.getDeltaMovement() + " source=" + target.source()
                    + " parentFacing=" + target.facing() + " hiddenPlotSpawn=" + run.hiddenPlotSpawn);
        }
    }

    private static void tick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
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

    private static void advance(final Run run) {
        final Lookup found = lookup(run.level);
        if (found.status != Status.ASSEMBLED_VALID || found.fixture == null
                || !found.fixture.marker.getUUID().equals(run.markerId)) {
            RUNS.remove(run.markerId);
            tell(run.observers, run.level, "[M38] RUN FAILED: tagged Sable fixture disappeared");
            return;
        }
        final Fixture fixture = found.fixture;
        run.age++;
        final SableMountedDispenserTarget.Result target = actorTarget(fixture);
        if (target != null && target.accepted()) {
            run.actorObserved = true;
            run.lastSource = target.source();
            run.lastFacing = target.facing();
        }
        final int after = sourceCount(fixture);
        final boolean blockMutation = run.kind == Kind.BLOCK && fixture.level.getBlockState(
                fixture.origin.offset(14, 1, 0)).is(Blocks.FIRE);
        final boolean parentSpawned = run.outputId != null && !run.hiddenPlotSpawn;
        final boolean positionValid = run.spawnSource != null && run.spawnPosition != null
                && run.spawnPosition.distanceToSqr(Vec3.atCenterOf(run.spawnSource)) < 9;
        final boolean facingValid = run.parentFacing != null && run.spawnVelocity != null
                && run.spawnVelocity.dot(Vec3.atLowerCornerOf(run.parentFacing.getNormal())) > 0.1;
        final boolean pass = run.actorObserved && (run.kind == Kind.BLOCK
                ? blockMutation && damage(fixture) > run.damageBefore
                : after == run.sourceBefore - 1 && parentSpawned && positionValid && facingValid)
                && (run.kind != Kind.PROJECTILE || run.parentFacing == run.lastFacing);
        if ((pass || run.age >= 400) && !run.reported) {
            run.reported = true;
            M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body, fixture.origin, true);
            tell(run.observers, fixture.level, "[M38] " + run.kind + " actor=" + run.actorObserved
                    + " nativeDispense=" + (after < run.sourceBefore || blockMutation)
                    + " sourceBefore=" + run.sourceBefore + " sourceAfter=" + after
                    + " parentItemSpawned=" + (run.kind != Kind.PROJECTILE && parentSpawned)
                    + " parentProjectileSpawned=" + (run.kind == Kind.PROJECTILE && parentSpawned)
                    + " spawnPositionValid=" + positionValid + " facingValid=" + facingValid
                    + " parentBlockMutation=" + blockMutation + " hiddenPlotSpawn=" + run.hiddenPlotSpawn
                    + " " + (pass ? "PASS" : "INCOMPLETE"));
        }
    }

    private static void advance(final Yaw yaw) {
        final Lookup found = lookup(yaw.level);
        if (found.status != Status.ASSEMBLED_VALID || found.fixture == null
                || !found.fixture.marker.getUUID().equals(yaw.markerId) || ++yaw.age > TIMEOUT_TICKS) {
            YAWS.remove(yaw.markerId);
            tell(yaw.observers, yaw.level, "[M38] YAW90 FAILED: fixture unavailable or native retract timed out");
            return;
        }
        final Fixture fixture = found.fixture;
        if (!retracted(fixture)) {
            final CreativeMotorBlockEntity motor = motor(fixture);
            if (motor == null) {
                YAWS.remove(yaw.markerId);
                tell(yaw.observers, yaw.level, "[M38] YAW90 FAILED: native motor missing");
                return;
            }
            motor.generatedSpeed.setValue(64);
            M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body, fixture.origin, false);
            return;
        }
        M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body, fixture.origin, true);
        final BlockEntity entity = M35FixtureLookup.blockEntity(fixture.level, fixture.body,
                actorPos(fixture.origin));
        if (!(entity instanceof final Container inventory)) {
            YAWS.remove(yaw.markerId);
            tell(yaw.observers, yaw.level, "[M38] YAW90 FAILED: restored native inventory missing");
            return;
        }
        inventory.setItem(0, new ItemStack(Items.ARROW, Kind.PROJECTILE.count()));
        inventory.setChanged();
        fixture.marker.addTag(YAW_TAG);
        hold(fixture, Math.PI / 2, Vec3.ZERO);
        YAWS.remove(yaw.markerId);
        tell(yaw.observers, yaw.level, "[M38] YAW90 COMPLETE parentFacing=north arrows=4");
    }

    private static void advance(final Cleanup cleanup) {
        final Lookup found = lookup(cleanup.level);
        if (found.status == Status.NONE && cleanup.phase == CleanupPhase.REMOVE) {
            CLEANUPS.remove(cleanup.markerId);
            tell(cleanup.observers, cleanup.level, "[M38] CLEANUP COMPLETE");
            return;
        }
        if (found.fixture == null || !found.fixture.marker.getUUID().equals(cleanup.markerId)
                || found.status == Status.CORRUPT_AMBIGUOUS
                || ++cleanup.age > TIMEOUT_TICKS) {
            CLEANUPS.remove(cleanup.markerId);
            tell(cleanup.observers, cleanup.level, "[M38] CLEANUP FAILED: ownership changed or native retract timed out; retry cleanup");
            return;
        }
        final Fixture fixture = found.fixture;
        try {
            switch (cleanup.phase) {
                case STOP -> {
                    if ((found.status == Status.ORPHAN_MARKER || fixture.body == null
                            && !(M35FixtureLookup.blockEntity(fixture.level, null, fixture.origin) instanceof MechanicalPistonBlockEntity))
                            && innerContraption(fixture.level, fixture.body, fixture.origin) == null) {
                        cleanup.phase = CleanupPhase.REMOVE;
                    } else if (innerRestored(fixture)) {
                        cleanup.phase = fixture.body == null ? CleanupPhase.REMOVE : CleanupPhase.OUTER;
                    } else {
                        M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body,
                                fixture.origin, true);
                        cleanup.phase = CleanupPhase.REVERSE;
                    }
                }
                case REVERSE -> {
                    if (innerRestored(fixture)) {
                        cleanup.phase = fixture.body == null ? CleanupPhase.REMOVE : CleanupPhase.OUTER;
                        return;
                    }
                    final CreativeMotorBlockEntity motor = motor(fixture);
                    if (motor == null) {
                        throw new IllegalStateException("native motor missing");
                    }
                    motor.generatedSpeed.setValue(64);
                    M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body,
                            fixture.origin, false);
                    cleanup.phase = CleanupPhase.RETRACT_WAIT;
                    tell(cleanup.observers, cleanup.level, "[M38] CLEANUP phase=RETRACT_WAIT");
                }
                case RETRACT_WAIT -> {
                    if (innerRestored(fixture)) {
                        M35RollerFixtureLifecycleCommands.setFixtureLever(fixture.level, fixture.body,
                                fixture.origin, true);
                        cleanup.phase = fixture.body == null ? CleanupPhase.REMOVE : CleanupPhase.OUTER;
                        tell(cleanup.observers, cleanup.level, "[M38] CLEANUP phase=INNER_DISASSEMBLED");
                    }
                }
                case OUTER -> {
                    if (fixture.body == null) {
                        cleanup.phase = CleanupPhase.REMOVE;
                        return;
                    }
                    fixture.marker.removeTag(YAW_TAG);
                    inspectRestoreSpace(cleanup, fixture, false);
                    // Recover at the reserved original origin, never clear the floor at the fallen pose.
                    if (!inspectRestoreSpace(cleanup, fixture, true)) {
                        throw new IllegalStateException("original fixture restore space occupied/unloaded; unrelated blocks preserved");
                    }
                    final AABB rawFootprint = new AABB(fixture.origin.offset(-12, -2, -2), fixture.origin.offset(2, 2, 2));
                    for (final SuperGlueEntity glue : fixture.level.getEntitiesOfClass(SuperGlueEntity.class, rawFootprint)) {
                        final AABB box = glue.getBoundingBox();
                        if (box.minX >= rawFootprint.minX && box.minY >= rawFootprint.minY && box.minZ >= rawFootprint.minZ
                                && box.maxX <= rawFootprint.maxX && box.maxY <= rawFootprint.maxY && box.maxZ <= rawFootprint.maxZ) {
                            cleanup.restoredGlues.add(box.move(Vec3.atLowerCornerOf(fixture.visibleOrigin.subtract(fixture.origin))));
                        }
                    }
                    for (final var residue : cleanup.restoreResidue.entrySet()) {
                        if (!fixture.level.getBlockState(residue.getKey()).equals(residue.getValue().state)) {
                            throw new IllegalStateException("fixture-owned restore residue changed during validation");
                        }
                        fixture.level.setBlock(residue.getKey(), Blocks.AIR.defaultBlockState(), 3);
                    }
                    hold(fixture, 0, Vec3.ZERO);
                    final BlockEntity entity = M35FixtureLookup.blockEntity(fixture.level, fixture.body,
                            fixture.origin.above().north(2));
                    if (!(entity instanceof final PhysicsAssemblerBlockEntity assembler)) {
                        throw new IllegalStateException("tagged outer assembler missing");
                    }
                    tell(cleanup.observers, cleanup.level, "[M38] CLEANUP phase=OUTER_DISASSEMBLE");
                    try {
                        assembler.assembleOrDisassemble(null);
                    } catch (final IllegalStateException exception) {
                        final Lookup after = lookup(cleanup.level);
                        if (after.fixture == null || after.fixture.body != null
                                || !knownFootprint(after.fixture)) {
                            throw exception;
                        }
                    }
                    if (lookup(cleanup.level).fixture != null && lookup(cleanup.level).fixture.body != null) {
                        throw new IllegalStateException("native outer disassembly did not complete; no static cleanup performed");
                    }
                    cleanup.phase = CleanupPhase.REMOVE;
                }
                case REMOVE -> {
                    if (fixture.body != null || !knownFootprint(fixture)
                            || M35FixtureLookup.blockEntity(fixture.level, null, fixture.origin) instanceof MechanicalPistonBlockEntity
                            && !innerRestored(fixture)) {
                        throw new IllegalStateException("static fixture not safe to remove");
                    }
                    for (final AABB bounds : cleanup.restoredGlues) {
                        for (final SuperGlueEntity glue : fixture.level.getEntitiesOfClass(SuperGlueEntity.class, bounds.inflate(0.01),
                                candidate -> candidate.getBoundingBox().equals(bounds))) {
                            glue.discard();
                        }
                    }
                    removeStatic(fixture.level, fixture.origin, fixture.kind, fixture.marker);
                    if (!footprintClear(fixture.level, fixture.origin, fixture.kind) || marker(fixture.level) != null) {
                        throw new IllegalStateException("post-cleanup footprint scan found residue; ownership retained");
                    }
                    CLEANUPS.remove(cleanup.markerId);
                    tell(cleanup.observers, cleanup.level, "[M38] CLEANUP COMPLETE");
                }
            }
        } catch (final RuntimeException exception) {
            final Lookup current = lookup(cleanup.level);
            if (current.fixture != null && current.fixture.body != null) {
                for (final var residue : cleanup.restoreResidue.entrySet()) {
                    if (!cleanup.level.getBlockState(residue.getKey()).isAir()) continue;
                    cleanup.level.setBlock(residue.getKey(), residue.getValue().state, 3);
                    final BlockEntity restored = cleanup.level.getBlockEntity(residue.getKey());
                    if (restored != null && residue.getValue().data != null) {
                        restored.load(residue.getValue().data);
                        restored.setChanged();
                    }
                }
            }
            Sable.LOGGER.error("SABLE_M38_CLEANUP_EXCEPTION phase={} marker={}", cleanup.phase,
                    cleanup.markerId, exception);
            CLEANUPS.remove(cleanup.markerId);
            tell(cleanup.observers, cleanup.level, "[M38] CLEANUP FAILED: "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private static boolean innerRestored(final Fixture fixture) {
        final BlockEntity entity = M35FixtureLookup.blockEntity(fixture.level, fixture.body, fixture.origin);
        return entity instanceof final MechanicalPistonBlockEntity piston && !piston.running
                && innerContraption(fixture.level, fixture.body, fixture.origin) == null && piston.offset <= 0
                && M35FixtureLookup.blockState(fixture.level, fixture.body, fixture.origin).toString().contains("state=retracted");
    }

    private static boolean inspectRestoreSpace(final Cleanup cleanup, final Fixture fixture, final boolean original) {
        final BlockPos rawAssembler = fixture.origin.above().north(2);
        final BlockPos goal = original ? fixture.visibleOrigin.above().north(2)
                : SimAssemblyHelper.currentVisibleBlockPos(fixture.body, rawAssembler);
        boolean clear = true;
        for (final BlockPos raw : SimAssemblyHelper.collectBlocks(fixture.level, fixture.body)) {
            final BlockPos target = goal.offset(raw.subtract(rawAssembler));
            final boolean loaded = fixture.level.getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4);
            if (!loaded || !fixture.level.getBlockState(target).isAir()) {
                final BlockState occupant = loaded ? fixture.level.getBlockState(target) : Blocks.AIR.defaultBlockState();
                final boolean owned = original && loaded && M38DispenserFixtureLayout.ownedRestoreResidue(
                        target.subtract(fixture.visibleOrigin), BuiltInRegistries.BLOCK.getKey(occupant.getBlock()).toString(),
                        BuiltInRegistries.BLOCK.getKey(M35FixtureLookup.blockState(fixture.level, fixture.body, raw).getBlock()).toString(),
                        fixture.kind == Kind.DROPPER, fixture.kind == Kind.BLOCK);
                if (owned) {
                    final BlockEntity entity = fixture.level.getBlockEntity(target);
                    cleanup.restoreResidue.put(target, new RestoreResidue(occupant,
                            entity == null ? null : entity.saveWithFullMetadata()));
                } else {
                    clear = false;
                }
                tell(cleanup.observers, fixture.level, "[M38] RESTORE_BLOCKER pos=" + target.toShortString()
                        + " state=" + (loaded ? fixture.level.getBlockState(target) : "<unloaded>")
                        + " blockEntity=" + (loaded ? fixture.level.getBlockEntity(target) : "<unloaded>")
                        + " restoreState=" + M35FixtureLookup.blockState(fixture.level, fixture.body, raw)
                        + " ownership=" + (owned ? "DETERMINISTIC_M38_RESTORE_RESIDUE" : "UNPROVEN_PRESERVE")
                        + " pose=" + (original ? "ORIGINAL" : "CURRENT")
                        + " action=" + (owned ? "TRANSACTIONAL_REMOVE" : original ? "REFUSE" : "RELOCATE_TO_ORIGINAL_ORIGIN"));
            }
        }
        return clear;
    }

    private static void hold(final Fixture fixture, final double yaw, final Vec3 offset) {
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(fixture.level);
        final RigidBodyHandle handle = physics == null ? null : physics.getPhysicsHandle(fixture.body);
        if (handle == null || !handle.isValid()) {
            throw new IllegalStateException("M38 tagged physics handle unavailable");
        }
        final Quaterniond rotation = new Quaterniond().rotationY(yaw);
        final Vec3 rawCenter = Vec3.atCenterOf(fixture.origin);
        final Vector3d local = new Vector3d(rawCenter.x, rawCenter.y, rawCenter.z)
                .sub(fixture.body.logicalPose().rotationPoint()).mul(fixture.body.logicalPose().scale());
        final Vec3 visibleAnchor = Vec3.atCenterOf(fixture.visibleOrigin).add(offset);
        final Vector3d target = new Vector3d(visibleAnchor.x, visibleAnchor.y, visibleAnchor.z)
                .sub(rotation.transform(local));
        handle.teleport(target, rotation);
        handle.setLinearAndAngularVelocity(new Vector3d(), new Vector3d());
        physics.updatePose(fixture.body);
        fixture.body.updateBoundingBox();
        fixture.body.updateLastPose();
    }

    private static void postPhysicsTick(final ForgeSablePostPhysicsTickEvent event) {
        final ServerLevel level = event.getPhysicsSystem().getLevel();
        final Lookup found = lookup(level);
        if (found.fixture != null && found.fixture.body != null && found.fixture.marker != null
                && (found.status == Status.ASSEMBLED_VALID || CLEANUPS.containsKey(found.fixture.marker.getUUID()))) {
            final RigidBodyHandle handle = event.getPhysicsSystem().getPhysicsHandle(found.fixture.body);
            if (handle == null || !handle.isValid()) return;
            final double yaw = CLEANUPS.containsKey(found.fixture.marker.getUUID()) ? 0 : found.fixture.yaw();
            hold(found.fixture, yaw, Vec3.ZERO);
        }
    }

    private static void removeStatic(final ServerLevel level, final BlockPos origin,
                                     final Kind kind, final ArmorStand marker) {
        for (int x = -12; x <= -1; x++) {
            removeIf(level, origin.offset(x, 0, 0), AllBlocks.PISTON_EXTENSION_POLE.get());
        }
        removeIf(level, origin, AllBlocks.STICKY_MECHANICAL_PISTON.get());
        removeIf(level, origin.east(), AllBlocks.RADIAL_CHASSIS.get());
        removeIf(level, actorPos(origin), kind.block());
        removeIf(level, origin.east().south(), Blocks.CHEST);
        removeIf(level, origin.below(2), AllBlocks.CREATIVE_MOTOR.get());
        removeIf(level, origin.below(), AllBlocks.CLUTCH.get());
        removeIf(level, origin.below().south(), Blocks.LEVER);
        removeIf(level, origin.north(), Blocks.IRON_BLOCK);
        removeIf(level, origin.north(2), Blocks.IRON_BLOCK);
        removeIf(level, origin.above().north(2),
                dev.simulated_team.simulated.index.SimulatedBlocks.PHYSICS_ASSEMBLER.get());
        if (kind == Kind.BLOCK) {
            removeIf(level, ignitionSupport(origin), Blocks.STONE);
            removeIf(level, ignitionSupport(origin).above(), Blocks.FIRE);
        }
        for (final SuperGlueEntity glue : new ArrayList<>(level.getEntitiesOfClass(SuperGlueEntity.class,
                new AABB(origin.offset(0, -2, -2), origin.offset(2, 2, 2)).inflate(0.25),
                entity -> entity.getTags().contains(GLUE_TAG) || entity.getBoundingBox().equals(outerGlueBounds(origin))))) {
            glue.discard();
        }
        for (final Entity output : new ArrayList<>(level.getEntities((Entity) null,
                new AABB(origin.offset(-1, -3, -16), origin.offset(18, 4, 17)),
                entity -> entity.getTags().contains(OUTPUT_TAG)))) {
            output.discard();
        }
        if (marker != null && footprintClear(level, origin, kind)) {
            marker.discard();
        }
    }

    private static boolean footprintClear(final ServerLevel level, final BlockPos origin, final Kind kind) {
        return M38DispenserFixtureLayout.footprintClear(kind == Kind.DROPPER, kind == Kind.BLOCK,
                relative -> {
                    final BlockPos pos = origin.offset(relative);
                    return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                            && level.getBlockState(pos).isAir();
                }) && level.getEntitiesOfClass(SuperGlueEntity.class, outerGlueBounds(origin).inflate(0.25),
                        glue -> glue.getTags().contains(GLUE_TAG) || glue.getBoundingBox().equals(outerGlueBounds(origin))).isEmpty();
    }

    private static AABB outerGlueBounds(final BlockPos origin) {
        return M38DispenserFixtureLayout.outerGlueBounds(origin);
    }

    private static void removeIf(final ServerLevel level, final BlockPos pos, final Block expected) {
        if (level.getBlockState(pos).is(expected)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static ArmorStand marker(final ServerLevel level) {
        ArmorStand result = null;
        for (final Entity entity : level.getAllEntities()) {
            if (entity instanceof final ArmorStand stand && stand.getTags().contains(ORIGIN_TAG)) {
                if (result != null) {
                    return null;
                }
                result = stand;
            }
        }
        return result;
    }

    private static List<UUID> observers(final CommandSourceStack source) {
        return source.getEntity() instanceof final ServerPlayer player ? List.of(player.getUUID()) : List.of();
    }

    private static void tell(final List<UUID> observers, final ServerLevel level, final String message) {
        Sable.LOGGER.info("SABLE_M38_FIXTURE {}", message);
        for (final UUID id : observers) {
            final ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                player.sendSystemMessage(Component.literal(message));
            }
        }
    }

    private static int refuse(final CommandSourceStack source, final String message) {
        tell(observers(source), source.getLevel(), "[M38] " + message);
        return 0;
    }

    private static void serverStopped(final ServerStoppedEvent event) {
        RUNS.clear();
        CLEANUPS.clear();
        YAWS.clear();
    }

    private enum Kind {
        DROPPER, DISPENSER_ITEM, PROJECTILE, BLOCK;

        Block block() {
            return this == DROPPER ? Blocks.DROPPER : Blocks.DISPENSER;
        }

        net.minecraft.world.item.Item item() {
            return switch (this) {
                case DROPPER, DISPENSER_ITEM -> Items.COBBLESTONE;
                case PROJECTILE -> Items.ARROW;
                case BLOCK -> Items.FLINT_AND_STEEL;
            };
        }

        int count() {
            return this == BLOCK ? 1 : 4;
        }

        String tag() {
            return "sable_m38_" + name().toLowerCase(java.util.Locale.ROOT);
        }

        static Kind fromMarker(final ArmorStand marker) {
            for (final Kind kind : values()) {
                if (marker.getTags().contains(kind.tag())) {
                    return kind;
                }
            }
            return null;
        }
    }

    private enum CleanupPhase {
        STOP, REVERSE, RETRACT_WAIT, OUTER, REMOVE
    }

    private record Fixture(ServerLevel level, ArmorStand marker, BlockPos origin,
                           ServerSubLevel body, Kind kind, BlockPos visibleOrigin) {
        double yaw() {
            return this.marker.getTags().contains(YAW_TAG) ? Math.PI / 2 : 0;
        }
    }

    private record Lookup(Status status, Fixture fixture, String reason) {
    }

    private record RestoreResidue(BlockState state, CompoundTag data) {
    }

    private static final class Run {
        final UUID markerId;
        final ServerLevel level;
        final List<UUID> observers;
        final Kind kind;
        final int sourceBefore;
        final int damageBefore;
        final double yaw;
        int age;
        boolean actorObserved;
        boolean hiddenPlotSpawn;
        boolean reported;
        UUID outputId;
        Vec3 spawnPosition;
        Vec3 spawnVelocity;
        BlockPos spawnSource;
        Direction parentFacing;
        BlockPos lastSource;
        Direction lastFacing;

        Run(final UUID markerId, final ServerLevel level, final List<UUID> observers,
            final Kind kind, final int sourceBefore, final int damageBefore, final double yaw) {
            this.markerId = markerId;
            this.level = level;
            this.observers = observers;
            this.kind = kind;
            this.sourceBefore = sourceBefore;
            this.damageBefore = damageBefore;
            this.yaw = yaw;
        }
    }

    private static final class Yaw {
        final UUID markerId;
        final ServerLevel level;
        final List<UUID> observers;
        int age;

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
        final Map<BlockPos, RestoreResidue> restoreResidue = new HashMap<>();
        final List<AABB> restoredGlues = new ArrayList<>();
        int age;

        Cleanup(final UUID markerId, final ServerLevel level, final List<UUID> observers) {
            this.markerId = markerId;
            this.level = level;
            this.observers = observers;
        }
    }
}
