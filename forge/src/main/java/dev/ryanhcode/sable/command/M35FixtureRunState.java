package dev.ryanhcode.sable.command;

import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableM31CreateActorTrace;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SableRollerTerrain;
import dev.ryanhcode.sable.forge.event.ForgeSablePostPhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.util.SableDiagnosticFlags;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Command-owned pose and terrain fixture. Never participates in normal Sable physics. */
final class M35FixtureRunState {
    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static final int OBSERVATION_TICKS = 400;

    enum Mode {
        RUN, YAW90, TRANSLATE, DEPLETION
    }

    private M35FixtureRunState() {
    }

    static int start(final CommandSourceStack source, final Mode mode) {
        final ServerLevel level = source.getLevel();
        final M35FixtureLookup.Candidate candidate = M35FixtureLookup.find(level, source.getPosition()).sole();
        if (candidate == null || candidate.match() == null || candidate.match().bodyId() == null
                || !candidate.marker().getTags().contains("sable_m35_mode_sable")) {
            return refuse(source, "one assembled, tagged M35 Sable fixture is required");
        }
        final M35FixtureLookup.Match match = candidate.match();
        final ServerSubLevel body = body(level, match.bodyId());
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(level);
        if (body == null || physics == null) {
            return refuse(source, "fixture body or physics handle is missing");
        }
        final MechanicalPistonBlockEntity piston = M35FixtureLookup.blockEntity(level, body, match.origin())
                instanceof final MechanicalPistonBlockEntity value ? value : null;
        if (piston == null || piston.running || piston.movedContraption != null
                || !match.rollerStatic() || !match.chestStatic() || !match.chassisStatic()) {
            return refuse(source, "inner piston must be natively retracted; run /function sable:m35/retract first");
        }
        final Run previous = RUNS.get(candidate.marker().getUUID());
        final double yaw = yaw(body) + (mode == Mode.YAW90 ? Math.PI / 2 : 0);
        final Vec3 anchor = Vec3.atCenterOf(candidate.markerOrigin())
                .add(mode == Mode.TRANSLATE ? new Vec3(0, 0, 18) : Vec3.ZERO);
        final int count = mode == Mode.DEPLETION ? 2 : 16;
        if (!(M35FixtureLookup.blockEntity(level, body, match.origin().east().south())
                instanceof final Container chest)) {
            return refuse(source, "mounted chest is not available while retracted");
        }
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, count));
        chest.setChanged();
        if (previous != null) {
            stop(candidate.marker().getUUID());
        }
        final Run run = new Run(level, candidate.marker().getUUID(), match.bodyId(),
                match.origin(), candidate.markerOrigin(), anchor, yaw, mode, source.getEntity() instanceof ServerPlayer player
                ? player.getUUID() : null);
        hold(run, body, physics);
        if (!SableRollerTerrain.supportsWorldDown(body)) {
            return refuse(source, "level pose does not satisfy production Roller world-up predicate");
        }
        if (!prepareLane(run, body)) {
            restoreLane(run);
            return refuse(source, "physical terrain lane is occupied, unloaded, or intersects another Sable body");
        }
        snapshotRawTerrain(run, body);
        run.startingMaterial = materialCount(run, body);
        if (run.startingMaterial != count) {
            restoreLane(run);
            return refuse(source, "mounted material count does not match depletion fixture");
        }
        RUNS.put(run.markerId, run);
        SableM31CreateActorTrace.beginRollerEvidence(run.bodyId);
        announce(run, "[M35] ORIENTATION roll=" + degreesRoll(body) + " pitch=" + degreesPitch(body)
                + " yaw=" + Math.toDegrees(yaw) + " upDot=" + upDot(body) + " supported=true");
        announce(run, "[M35] RUN mode=" + mode + " laneCells=" + run.laneExpected.size()
                + " materialBefore=" + run.startingMaterial + " traceEnabled="
                + SableDiagnosticFlags.TRACE_CREATE_ACTORS);
        return 1;
    }

    static void tick(final MinecraftServer server) {
        for (final Run run : java.util.List.copyOf(RUNS.values())) {
            if (run.level.getServer() != server) {
                continue;
            }
            final ServerSubLevel body = body(run.level, run.bodyId);
            if (body == null || run.level.getEntity(run.markerId) == null) {
                stop(run.markerId);
                continue;
            }
            final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(run.level);
            if (physics == null) {
                stop(run.markerId);
                continue;
            }
            hold(run, body, physics);
            if (!run.started) {
                if (!SableRollerTerrain.supportsWorldDown(body)) {
                    announce(run, "[M35] RUN REFUSED: production Roller orientation is unsupported");
                    stop(run.markerId);
                    continue;
                }
                M35RollerFixtureLifecycleCommands.setFixtureLever(run.level, body, run.rawOrigin, false);
                run.started = true;
                announce(run, "[M35] RUN: native Create clutch engaged");
            }
            if (run.reported) {
                continue;
            }
            run.age++;
            final SableM31CreateActorTrace.RollerEvidence evidence =
                    SableM31CreateActorTrace.rollerEvidence(run.bodyId);
            final int now = materialCount(run, body);
            final boolean rawUntouched = rawTerrainUnchanged(run, body);
            final boolean terrainChanged = terrainChanged(run);
            final boolean consumed = now >= 0 && run.startingMaterial > now;
            final boolean complete = evidence.footprints() > 0 && terrainChanged && consumed && rawUntouched
                    && (run.mode == Mode.DEPLETION ? now == 0 && evidence.paves() > 0
                            : evidence.clears() > 0 && evidence.paves() > 0);
            if (complete || run.age >= OBSERVATION_TICKS) {
                run.reported = true;
                announce(run, "[M35] RUNTIME rollerActor=" + (evidence.footprints() > 0)
                        + " orientationSupported=" + SableRollerTerrain.supportsWorldDown(body)
                        + " footprintResolved=" + (evidence.footprints() > 0)
                        + " visibleTerrainMutation=" + terrainChanged
                        + " nativeClearCount=" + evidence.clears() + " nativePaveCount=" + evidence.paves()
                        + " materialBefore=" + run.startingMaterial + " materialAfter=" + now
                        + " materialConsumed=" + consumed + " hiddenPlotMutation=" + !rawUntouched);
                announce(run, "[M35] RUNTIME " + run.mode + (complete ? " PASS" : " INCOMPLETE"));
            }
        }
    }

    static void postPhysicsTick(final ForgeSablePostPhysicsTickEvent event) {
        if (RUNS.isEmpty()) {
            return;
        }
        final SubLevelPhysicsSystem physics = event.getPhysicsSystem();
        for (final Run run : RUNS.values()) {
            if (run.level != physics.getLevel()) {
                continue;
            }
            final ServerSubLevel body = body(run.level, run.bodyId);
            if (body != null) {
                hold(run, body, physics);
            }
        }
    }

    static void stop(final UUID markerId) {
        final Run run = RUNS.remove(markerId);
        if (run != null) {
            restoreLane(run);
            SableM31CreateActorTrace.clearRollerEvidence(run.bodyId);
        }
    }

    static boolean alignForCleanup(final UUID markerId, final ServerSubLevel body) {
        final Run run = RUNS.get(markerId);
        if (run == null) {
            return true;
        }
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(run.level);
        if (physics == null || !body.getUniqueId().equals(run.bodyId)) {
            return false;
        }
        final Run assemblyPose = new Run(run.level, run.markerId, run.bodyId, run.rawOrigin,
                run.markerOrigin, Vec3.atCenterOf(run.markerOrigin), 0, Mode.RUN, run.observer);
        hold(assemblyPose, body, physics);
        if (!SableRollerTerrain.supportsWorldDown(body)) {
            return false;
        }
        stop(markerId);
        return true;
    }

    static void clear() {
        for (final UUID markerId : java.util.List.copyOf(RUNS.keySet())) {
            stop(markerId);
        }
    }

    private static void hold(final Run run, final ServerSubLevel body, final SubLevelPhysicsSystem physics) {
        final RigidBodyHandle handle = physics.getPhysicsHandle(body);
        if (handle == null || !handle.isValid()) {
            throw new IllegalStateException("M35 physics body is unavailable");
        }
        final Quaterniond levelYaw = new Quaterniond().rotationY(run.yaw);
        final Vec3 rawCenter = Vec3.atCenterOf(run.rawOrigin);
        final Vector3d local = new Vector3d(rawCenter.x, rawCenter.y, rawCenter.z)
                .sub(body.logicalPose().rotationPoint()).mul(body.logicalPose().scale());
        final Vector3d target = new Vector3d(run.visibleAnchor.x, run.visibleAnchor.y, run.visibleAnchor.z)
                .sub(levelYaw.transform(local));
        handle.teleport(target, levelYaw);
        handle.setLinearAndAngularVelocity(new Vector3d(), new Vector3d());
        physics.updatePose(body);
        body.updateBoundingBox();
        body.updateLastPose();
    }

    private static boolean prepareLane(final Run run, final ServerSubLevel body) {
        final Map<BlockPos, BlockState> desired = new LinkedHashMap<>();
        for (int x = 4; x <= 14; x++) {
            for (int y = -3; y <= 1; y++) {
                final BlockPos raw = run.rawOrigin.offset(x, y, 0);
                final BlockPos visible = BlockPos.containing(body.logicalPose().transformPosition(Vec3.atCenterOf(raw)));
                final BlockState state = y == -2 && (x < 10 || x > 11) || y == -3 && x >= 10 && x <= 11
                        || y == 0 && x >= 6 && x <= 8
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                final BlockState existing = desired.putIfAbsent(visible, state);
                if (existing != null && !existing.equals(state)) {
                    return false;
                }
            }
        }
        for (final BlockPos visible : desired.keySet()) {
            if (!run.level.hasChunkAt(visible) || Sable.HELPER.getContaining(run.level, visible) != null) {
                return false;
            }
            final BlockState before = run.level.getBlockState(visible);
            if (!before.isAir() && !(isOriginalLaneCell(run.markerOrigin, visible) && before.is(Blocks.STONE))) {
                return false;
            }
        }
        for (final Map.Entry<BlockPos, BlockState> entry : desired.entrySet()) {
            run.laneBefore.put(entry.getKey(), run.level.getBlockState(entry.getKey()));
            run.level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
        run.laneExpected.putAll(desired);
        return true;
    }

    private static boolean isOriginalLaneCell(final BlockPos origin, final BlockPos cell) {
        return cell.getX() >= origin.getX() + 4 && cell.getX() <= origin.getX() + 14
                && cell.getY() >= origin.getY() - 3 && cell.getY() <= origin.getY() + 1
                && cell.getZ() == origin.getZ();
    }

    private static void snapshotRawTerrain(final Run run, final ServerSubLevel body) {
        for (int x = 4; x <= 14; x++) {
            for (int y = -3; y <= -2; y++) {
                final BlockPos raw = run.rawOrigin.offset(x, y, 0);
                run.rawTerrain.put(raw, M35FixtureLookup.blockState(run.level, body, raw));
            }
        }
    }

    private static boolean rawTerrainUnchanged(final Run run, final ServerSubLevel body) {
        return run.rawTerrain.entrySet().stream().allMatch(entry ->
                entry.getValue().equals(M35FixtureLookup.blockState(run.level, body, entry.getKey())));
    }

    private static boolean terrainChanged(final Run run) {
        return run.laneExpected.entrySet().stream().anyMatch(entry ->
                !entry.getValue().equals(run.level.getBlockState(entry.getKey())));
    }

    private static void restoreLane(final Run run) {
        for (final Map.Entry<BlockPos, BlockState> entry : run.laneBefore.entrySet()) {
            run.level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
    }

    private static int materialCount(final Run run, final ServerSubLevel body) {
        final MechanicalPistonBlockEntity piston = M35FixtureLookup.blockEntity(run.level, body, run.rawOrigin)
                instanceof final MechanicalPistonBlockEntity value ? value : null;
        if (piston != null && piston.movedContraption != null && piston.movedContraption.getContraption() != null) {
            final IItemHandler items = piston.movedContraption.getContraption().getStorage().getAllItems();
            int total = 0;
            for (int slot = 0; slot < items.getSlots(); slot++) {
                final ItemStack stack = items.getStackInSlot(slot);
                if (stack.is(Items.COBBLESTONE)) {
                    total += stack.getCount();
                }
            }
            return total;
        }
        if (M35FixtureLookup.blockEntity(run.level, body, run.rawOrigin.east().south())
                instanceof final Container chest) {
            int total = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                if (chest.getItem(slot).is(Items.COBBLESTONE)) {
                    total += chest.getItem(slot).getCount();
                }
            }
            return total;
        }
        return -1;
    }

    private static ServerSubLevel body(final ServerLevel level, final UUID id) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && container.getSubLevel(id) instanceof final ServerSubLevel found ? found : null;
    }

    private static double yaw(final ServerSubLevel body) {
        final Vector3d forward = body.logicalPose().orientation().transform(new Vector3d(1, 0, 0));
        return Math.atan2(-forward.z, forward.x);
    }

    private static double upDot(final ServerSubLevel body) {
        return body.logicalPose().orientation().transform(new Vector3d(0, 1, 0)).y;
    }

    private static double degreesPitch(final ServerSubLevel body) {
        final Vector3d forward = body.logicalPose().orientation().transform(new Vector3d(1, 0, 0));
        return Math.toDegrees(Math.atan2(-forward.y, Math.hypot(forward.x, forward.z)));
    }

    private static double degreesRoll(final ServerSubLevel body) {
        final Vector3d up = body.logicalPose().orientation().transform(new Vector3d(0, 1, 0));
        final Vector3d lateral = new Quaterniond().rotationY(yaw(body)).transform(new Vector3d(0, 0, 1));
        return Math.toDegrees(Math.atan2(up.dot(lateral), up.y));
    }

    private static int refuse(final CommandSourceStack source, final String reason) {
        source.sendFailure(Component.literal("[M35] RUN REFUSED: " + reason));
        return 0;
    }

    private static void announce(final Run run, final String message) {
        Sable.LOGGER.info("SABLE_M35_FIXTURE {}", message);
        final ServerPlayer player = run.observer == null ? null
                : run.level.getServer().getPlayerList().getPlayer(run.observer);
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    private static final class Run {
        private final ServerLevel level;
        private final UUID markerId;
        private final UUID bodyId;
        private final BlockPos rawOrigin;
        private final BlockPos markerOrigin;
        private final Vec3 visibleAnchor;
        private final double yaw;
        private final Mode mode;
        private final UUID observer;
        private final Map<BlockPos, BlockState> laneBefore = new LinkedHashMap<>();
        private final Map<BlockPos, BlockState> laneExpected = new LinkedHashMap<>();
        private final Map<BlockPos, BlockState> rawTerrain = new LinkedHashMap<>();
        private int startingMaterial;
        private int age;
        private boolean started;
        private boolean reported;

        private Run(final ServerLevel level, final UUID markerId, final UUID bodyId, final BlockPos rawOrigin,
                    final BlockPos markerOrigin, final Vec3 visibleAnchor, final double yaw, final Mode mode,
                    final UUID observer) {
            this.level = level;
            this.markerId = markerId;
            this.bodyId = bodyId;
            this.rawOrigin = rawOrigin;
            this.markerOrigin = markerOrigin;
            this.visibleAnchor = visibleAnchor;
            this.yaw = yaw;
            this.mode = mode;
            this.observer = observer;
        }
    }
}
