package dev.ryanhcode.sable.command;

import com.mojang.brigadier.CommandDispatcher;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.chassis.ChassisBlockEntity;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelBlockEditHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity;
import dev.simulated_team.simulated.index.SimulatedBlocks;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/** Native Create motion and bounded teardown for the command-generated M35 fixture only. */
public final class M35RollerFixtureLifecycleCommands {
    private static final String VERSION_TAG = M35FixtureLookup.VERSION_TAG;
    private static final int RETRACT_RPM = 64;
    private static final int EXTEND_RPM = -64;
    private static final int TIMEOUT_TICKS = 240;
    private static final Map<UUID, Task> TASKS = new HashMap<>();
    private static PendingSetup pendingSetup;

    private M35RollerFixtureLifecycleCommands() {
    }

    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(M35RollerFixtureLifecycleCommands::tick);
        MinecraftForge.EVENT_BUS.addListener(M35FixtureRunState::postPhysicsTick);
        MinecraftForge.EVENT_BUS.addListener(M35RollerFixtureLifecycleCommands::serverStopped);
    }

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sable_m35")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("baseline").executes(context -> setup(context.getSource(), "baseline")))
                .then(Commands.literal("sable").executes(context -> setup(context.getSource(), "sable")))
                .then(Commands.literal("validate").executes(context -> validate(context.getSource())))
                .then(Commands.literal("reset").executes(context -> reset(context.getSource())))
                .then(Commands.literal("run").executes(context -> M35FixtureRunState.start(context.getSource(), M35FixtureRunState.Mode.RUN)))
                .then(Commands.literal("yaw90").executes(context -> M35FixtureRunState.start(context.getSource(), M35FixtureRunState.Mode.YAW90)))
                .then(Commands.literal("translate").executes(context -> M35FixtureRunState.start(context.getSource(), M35FixtureRunState.Mode.TRANSLATE)))
                .then(Commands.literal("depletion").executes(context -> M35FixtureRunState.start(context.getSource(), M35FixtureRunState.Mode.DEPLETION)))
                .then(Commands.literal("retract").executes(context -> start(context.getSource(), false)))
                .then(Commands.literal("cleanup").executes(context -> start(context.getSource(), true))));
    }

    private static int setup(final CommandSourceStack source, final String mode) {
        if (pendingSetup != null) {
            return fail(source, "SETUP REFUSED: another M35 build is still pending validation");
        }
        final M35FixtureLookup.Result lookup = M35FixtureLookup.find(source.getLevel(), source.getPosition());
        if (!lookup.status().setupAllowed()) {
            reportLookup(source, lookup);
            final M35FixtureLookup.Candidate existing = lookup.candidates().get(0);
            return fail(source, "SETUP REFUSED: existing M35 fixture at " + existing.markerOrigin()
                    + "; run /function sable:m35/cleanup");
        }
        final BlockPos origin = BlockPos.containing(source.getPosition()).above(4);
        final ServerLevel level = source.getLevel();
        for (int x = -12; x <= 14; x++) {
            for (int y = -3; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (!level.getBlockState(origin.offset(x, y, z)).isAir()) {
                        return fail(source, "SETUP REFUSED: reserved fixture cell occupied at "
                                + origin.offset(x, y, z));
                    }
                }
            }
        }
        pendingSetup = new PendingSetup(level, origin, mode, observers(source));
        tell(pendingSetup, "[M35] SETUP phase=BUILD origin=" + origin + " mode=" + mode);
        source.getServer().getCommands().performPrefixedCommand(
                source.withPosition(Vec3.atLowerCornerOf(origin)), "function sable:m35/internal/create");
        return 1;
    }

    private static int validate(final CommandSourceStack source) {
        final PendingSetup pending = pendingSetup;
        if (pending == null || pending.level != source.getLevel()
                || !pending.origin.equals(BlockPos.containing(source.getPosition()))) {
            return fail(source, "SETUP FAILED: validation without a matching pending fixture build");
        }
        tell(pending, "[M35] SETUP phase=VALIDATE");
        final BlockPos origin = pending.origin;
        final BlockPos assemblerPos = origin.above().north(2);
        final BlockPos startingBlock = assemblerPos.below();
        tell(pending, "[M35] SETUP assembler=" + assemblerPos);
        tell(pending, "[M35] SETUP startingBlock=" + startingBlock + " state="
                + pending.level.getBlockState(startingBlock));
        final M35FixtureLookup.Result created = M35FixtureLookup.find(pending.level,
                Vec3.atLowerCornerOf(origin));
        final M35FixtureLookup.Candidate candidate = created.sole();
        final String confidence = candidate == null || candidate.match() == null ? created.status().name()
                : candidate.match().confidence(candidate.marker().getTags().contains(VERSION_TAG));
        tell(pending, "[M35] SETUP lookupStatus=" + confidence);
        final String failure = validateFreshFixture(pending, candidate);
        if (failure != null) {
            tell(pending, "[M35] SETUP FAILED reason=" + failure);
            tell(pending, "[M35] SETUP phase=ROLLBACK");
            rollbackFreshSetup(pending);
            tell(pending, "[M35] SETUP ROLLBACK COMPLETE");
            pendingSetup = null;
            return 0;
        }
        candidate.marker().addTag("sable_m35_mode_" + pending.mode);
        pendingSetup = null;
        tell(pending, "[M35] SETUP phase=READY");
        tell(pending, "[M35] READY: Roller, mounted chest, and outer hull validated. Click the Physics Assembler first.");
        return 1;
    }

    private static String validateFreshFixture(final PendingSetup pending,
                                               final M35FixtureLookup.Candidate candidate) {
        final BlockPos origin = pending.origin;
        final ServerLevel level = pending.level;
        if (candidate == null || candidate.match() == null || candidate.match().bodyId() != null
                || !candidate.marker().getTags().contains(VERSION_TAG)
                || !origin.equals(candidate.markerOrigin())
                || !"EXACT_CURRENT".equals(candidate.match().confidence(true))) {
            return "FRESH_FIXTURE_LOOKUP_NOT_EXACT_CURRENT";
        }
        if (!fullFingerprint(level, origin, null)) {
            return "CONTROLLER_OR_OUTER_HULL_INCOMPLETE";
        }
        for (int x = -12; x <= -1; x++) {
            if (!level.getBlockState(origin.offset(x, 0, 0))
                    .is(com.simibubi.create.AllBlocks.PISTON_EXTENSION_POLE.get())) {
                return "PISTON_EXTENSION_POLE_MISSING_AT_" + x;
            }
        }
        if (!level.getBlockState(origin.east()).is(com.simibubi.create.AllBlocks.RADIAL_CHASSIS.get())
                || !level.getBlockState(origin.east().above()).is(com.simibubi.create.AllBlocks.MECHANICAL_ROLLER.get())
                || !level.getBlockState(origin.east().south()).is(Blocks.CHEST)) {
            return "INNER_ROLLER_CHEST_OR_CHASSIS_MISSING";
        }
        if (!(level.getBlockEntity(origin.east()) instanceof final ChassisBlockEntity chassis)) {
            return "INNER_CHASSIS_BLOCK_ENTITY_MISSING";
        }
        final List<BlockPos> included = chassis.getIncludedBlockPositions(Direction.EAST, false);
        if (included == null || !included.contains(origin.east().above())
                || !included.contains(origin.east().south())) {
            return "INNER_CHASSIS_NATIVE_ATTACHMENT_INCOMPLETE";
        }
        if (!(level.getBlockEntity(origin.east().south()) instanceof final net.minecraft.world.Container chest)
                || !chest.getItem(0).is(Blocks.COBBLESTONE.asItem()) || chest.getItem(0).getCount() != 16) {
            return "MOUNTED_CHEST_CONTENTS_INVALID";
        }
        final BlockEntity roller = level.getBlockEntity(origin.east().above());
        if (roller == null) {
            return "ROLLER_BLOCK_ENTITY_MISSING";
        }
        final CompoundTag rollerTag = roller.saveWithFullMetadata();
        if (!rollerTag.getCompound("Filter").getString("id").equals("minecraft:cobblestone")
                || !rollerTag.contains("ScrollValue") || rollerTag.getInt("ScrollValue") != 0) {
            return "ROLLER_FILTER_OR_MODE_INVALID";
        }
        if (!level.getBlockState(origin.offset(2, 0, 0)).isAir()
                || !level.getBlockState(origin.offset(3, 0, 0)).isAir()) {
            return "TERRAIN_LANE_CONNECTED_TO_HULL";
        }
        final AABB expected = new AABB(origin.getX(), origin.getY() - 2, origin.getZ() - 2,
                origin.getX() + 1, origin.getY() + 2, origin.getZ() + 2);
        final SuperGlueEntity stationaryGlue = new SuperGlueEntity(level, expected);
        stationaryGlue.addTag("sable_m35_glue");
        if (!level.addFreshEntity(stationaryGlue)) {
            return "STATIONARY_GLUE_ENTITY_ADD_FAILED";
        }
        final List<SuperGlueEntity> glue = level.getEntitiesOfClass(SuperGlueEntity.class,
                expected.inflate(0.25D), entity -> entity.getTags().contains("sable_m35_glue"));
        tell(pending, "[M35] GLUE candidateCount=" + glue.size());
        for (int index = 0; index < Math.min(glue.size(), 4); index++) {
            final SuperGlueEntity entity = glue.get(index);
            tell(pending, "[M35] GLUE candidate[" + index + "] uuid=" + entity.getUUID()
                    + " pos=" + entity.position() + " bounds=" + entity.getBoundingBox()
                    + " expectedBounds=" + expected
                    + " intersectsChassis=" + entity.contains(origin.east())
                    + " capturesRoller=" + entity.contains(origin.east().above())
                    + " capturesChest=" + entity.contains(origin.east().south()));
        }
        if (glue.size() != 1 || !stationaryGlue.contains(origin.north(2))
                || !stationaryGlue.contains(origin.north()) || !stationaryGlue.contains(origin)
                || !stationaryGlue.contains(origin.below(2))
                || stationaryGlue.contains(origin.east())) {
            tell(pending, "[M35] GLUE status=INVALID rejectReason=STATIONARY_ATTACHMENT_BOUNDS");
            return "STATIONARY_GLUE_NATIVE_BOUNDS_INVALID";
        }
        tell(pending, "[M35] GLUE status=VALID movingPayload=CHASSIS_NATIVE_ATTACHMENT");
        final SimAssemblyContraption outerSelection = new SimAssemblyContraption(null);
        try {
            if (!outerSelection.searchMovedStructure(level, origin.north(2))) {
                return "OUTER_ASSEMBLY_TRAVERSAL_REJECTED";
            }
        } catch (final AssemblyException exception) {
            return "OUTER_ASSEMBLY_TRAVERSAL_EXCEPTION_" + exception.getClass().getSimpleName();
        }
        final java.util.Collection<BlockPos> selected = outerSelection.getBlocks();
        if (!selected.contains(origin.north(2)) || !selected.contains(origin.north())
                || !selected.contains(origin) || !selected.contains(origin.below(2))
                || !selected.contains(origin.below()) || !selected.contains(origin.below().south())
                || !selected.contains(origin.above().north(2))
                || !selected.contains(origin.east()) || !selected.contains(origin.east().above())
                || !selected.contains(origin.east().south())) {
            return "OUTER_ASSEMBLY_MISSING_OWNED_COMPONENT";
        }
        for (int x = -12; x <= -1; x++) {
            if (!selected.contains(origin.offset(x, 0, 0))) {
                return "OUTER_ASSEMBLY_MISSING_EXTENSION_POLE_AT_" + x;
            }
        }
        for (int x = 4; x <= 14; x++) {
            for (int y = -3; y <= 1; y++) {
                if (selected.contains(origin.offset(x, y, 0))) {
                    return "OUTER_ASSEMBLY_CAPTURED_PARENT_TERRAIN";
                }
            }
        }
        if (selected.size() != 22) {
            return "OUTER_ASSEMBLY_UNEXPECTED_BLOCK_COUNT_" + selected.size();
        }
        tell(pending, "[M35] SETUP outerSelectedBlocks=" + selected.size());
        return null;
    }

    private static int reset(final CommandSourceStack source) {
        final M35FixtureLookup.Result lookup = M35FixtureLookup.find(source.getLevel(), source.getPosition());
        reportLookup(source, lookup);
        final M35FixtureLookup.Candidate candidate = lookup.sole();
        if (candidate == null || candidate.match() == null || candidate.match().bodyId() != null) {
            return fail(source, "RESET REFUSED: expected one static parent-world M35 fixture");
        }
        if (TASKS.containsKey(candidate.marker().getUUID())) {
            return fail(source, "RESET REFUSED: fixture lifecycle is in progress");
        }
        final Task check = new Task(source.getLevel(), candidate.marker().getUUID(), observers(source),
                candidate.markerOrigin(), candidate.match().origin(), null, false, false);
        if (!staticRetracted(check)) {
            return fail(source, "RESET REFUSED: retract the native inner piston first");
        }
        return source.getServer().getCommands().performPrefixedCommand(
                source.withPosition(Vec3.atLowerCornerOf(candidate.match().origin())),
                "function sable:m35/internal/reset_static");
    }

    private static int start(final CommandSourceStack source, final boolean cleanup) {
        final ServerLevel level = source.getLevel();
        announce(source, "[M35] CLEANUP ENTRY version=5");
        final M35FixtureLookup.Result lookup = M35FixtureLookup.find(level, source.getPosition());
        reportLookup(source, lookup);
        final M35FixtureLookup.Candidate candidate = lookup.sole();
        if (candidate == null) {
            announce(source, "[M35] fixtureDetected=" + !lookup.candidates().isEmpty()
                    + " innerState=UNKNOWN outerState=UNKNOWN lifecycleAction=REFUSE");
            return fail(source, "fixture lookup=" + lookup.status() + " reason="
                    + (lookup.candidates().isEmpty() ? "NO_MARKER" : lookup.candidates().get(0).rejectReason()));
        }
        final net.minecraft.world.entity.decoration.ArmorStand marker = candidate.marker();
        if (candidate.orphan()) {
            if (!cleanup) {
                return fail(source, "RETRACT REFUSED: orphan marker has no inner controller");
            }
            announce(source, "[M35] fixtureDetected=true innerState=ORPHAN_MARKER outerState=PARENT lifecycleAction=REMOVE_MARKER");
            marker.discard();
            announce(source, "[M35] CLEANUP COMPLETE version=5 reason=ORPHAN_MARKER");
            return 1;
        }
        if (TASKS.containsKey(marker.getUUID())) {
            announce(source, "[M35] fixtureDetected=true innerState=IN_PROGRESS outerState=UNKNOWN lifecycleAction=REFUSE");
            return fail(source, "fixture teardown already in progress");
        }
        final M35FixtureLookup.Match match = candidate.match();
        final Task task = new Task(level, marker.getUUID(), observers(source), candidate.markerOrigin(),
                match.origin(), match.bodyId(), cleanup, marker.getTags().contains("sable_m35_v4")
                && match.rejectReason().equals("HULL_MISSING"));
        TASKS.put(marker.getUUID(), task);
        tell(task, "[M35] fixtureDetected=true innerState=" + (staticRetracted(task) ? "STATIC" : "ACTIVE")
                + " outerState=" + (task.bodyId == null ? "PARENT" : "SABLE")
                + " fixtureVersion=" + (marker.getTags().contains(VERSION_TAG) ? "5" : "PREVIOUS")
                + " lifecycleAction=" + (cleanup ? "CLEANUP" : "RETRACT"));
        tell(task, "[M35] CLEANUP phase=STOP");
        return 1;
    }

    private static boolean coreFingerprint(final ServerLevel level, final BlockPos origin,
                                           final ServerSubLevel body) {
        return blockEntity(level, body, origin) instanceof MechanicalPistonBlockEntity
                && blockEntity(level, body, origin.below(2)) instanceof CreativeMotorBlockEntity
                && blockState(level, body, origin).is(com.simibubi.create.AllBlocks.STICKY_MECHANICAL_PISTON.get())
                && blockState(level, body, origin.below(2)).is(com.simibubi.create.AllBlocks.CREATIVE_MOTOR.get())
                && blockState(level, body, origin.below()).is(com.simibubi.create.AllBlocks.CLUTCH.get())
                && blockState(level, body, origin.below().south()).is(Blocks.LEVER);
    }

    private static boolean fullFingerprint(final ServerLevel level, final BlockPos origin,
                                           final ServerSubLevel body) {
        return coreFingerprint(level, origin, body)
                && blockState(level, body, origin.north()).is(Blocks.IRON_BLOCK)
                && blockState(level, body, origin.north(2)).is(Blocks.IRON_BLOCK)
                && blockEntity(level, body, origin.above().north(2)) instanceof PhysicsAssemblerBlockEntity;
    }

    private static void tick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (pendingSetup != null && pendingSetup.level.getServer() == event.getServer()
                && ++pendingSetup.ageTicks > 5) {
            final PendingSetup timedOut = pendingSetup;
            tell(timedOut, "[M35] SETUP FAILED reason=BUILD_FUNCTION_DID_NOT_VALIDATE");
            tell(timedOut, "[M35] SETUP phase=ROLLBACK");
            rollbackFreshSetup(timedOut);
            tell(timedOut, "[M35] SETUP ROLLBACK COMPLETE");
            pendingSetup = null;
        }
        if (TASKS.isEmpty()) {
            M35FixtureRunState.tick(event.getServer());
            return;
        }
        for (final Task task : List.copyOf(TASKS.values())) {
            if (task.level.getServer() != event.getServer()) {
                continue;
            }
            try {
                advance(task);
            } catch (final RuntimeException exception) {
                fail(task, "unexpected state: " + exception.getClass().getSimpleName());
            }
        }
        M35FixtureRunState.tick(event.getServer());
    }

    private static void advance(final Task task) {
        if (++task.ticks > TIMEOUT_TICKS) {
            fail(task, "native piston did not return within " + TIMEOUT_TICKS + " ticks");
            return;
        }
        if (!fixtureStillOwned(task)) {
            fail(task, "marker or fixture controller disappeared");
            return;
        }
        switch (task.phase) {
            case STOP -> {
                setLever(task, true);
                if (staticRetracted(task)) {
                    innerComplete(task);
                    return;
                }
                task.phase = Phase.REVERSE;
            }
            case REVERSE -> {
                final CreativeMotorBlockEntity motor = motor(task);
                if (motor == null) {
                    fail(task, "creative motor missing");
                    return;
                }
                motor.generatedSpeed.setValue(RETRACT_RPM);
                tell(task, "[M35] CLEANUP phase=REVERSE motorScrollValue=" + RETRACT_RPM);
                task.phase = Phase.ENGAGE;
            }
            case ENGAGE -> {
                setLever(task, false);
                tell(task, "[M35] CLEANUP phase=RETRACT_WAIT");
                task.phase = Phase.WAIT;
            }
            case WAIT -> {
                if (staticRetracted(task)) {
                    setLever(task, true);
                    final CreativeMotorBlockEntity motor = motor(task);
                    if (motor != null) {
                        motor.generatedSpeed.setValue(EXTEND_RPM);
                    }
                    innerComplete(task);
                }
            }
            case OUTER -> disassembleOuter(task);
            case CLEAN -> cleanStatic(task);
        }
    }

    private static boolean fixtureStillOwned(final Task task) {
        return task.level.getEntity(task.markerId) instanceof final net.minecraft.world.entity.decoration.ArmorStand marker
                && marker.getTags().contains(M35FixtureLookup.ORIGIN_TAG)
                && (task.bodyId == null || body(task) != null)
                && coreFingerprint(task.level, task.origin, body(task));
    }

    private static boolean staticRetracted(final Task task) {
        final ServerSubLevel body = body(task);
        final BlockState piston = blockState(task.level, body, task.origin);
        if (!(blockEntity(task.level, body, task.origin) instanceof final MechanicalPistonBlockEntity controller)
                || controller.running || controller.movedContraption != null) {
            return false;
        }
        return piston.is(com.simibubi.create.AllBlocks.STICKY_MECHANICAL_PISTON.get())
                && piston.toString().contains("state=retracted")
                && blockState(task.level, body, task.origin.east())
                        .is(com.simibubi.create.AllBlocks.RADIAL_CHASSIS.get())
                && blockState(task.level, body, task.origin.east().above())
                        .is(com.simibubi.create.AllBlocks.MECHANICAL_ROLLER.get())
                && blockState(task.level, body, task.origin.east().south()).is(Blocks.CHEST);
    }

    private static void innerComplete(final Task task) {
        tell(task, "[M35] CLEANUP phase=INNER_DISASSEMBLED");
        if (!task.cleanup) {
            TASKS.remove(task.markerId);
            return;
        }
        task.phase = task.bodyId == null ? Phase.CLEAN : Phase.OUTER;
    }

    private static void disassembleOuter(final Task task) {
        tell(task, "[M35] CLEANUP phase=OUTER_DISASSEMBLE");
        final ServerSubLevel oldBody = body(task);
        if (oldBody == null) {
            fail(task, "outer Sable body disappeared before disassembly");
            return;
        }
        final BlockPos rawAssembler = task.origin.above().north(2);
        if (!fullFingerprint(task.level, task.origin, oldBody)) {
            fail(task, "outer hull/assembler not restored after inner retraction");
            return;
        }
        if (!M35FixtureRunState.alignForCleanup(task.markerId, oldBody)) {
            fail(task, "fixture pose could not be restored before outer disassembly");
            return;
        }
        final BlockPos visibleAssembler = SimAssemblyHelper.currentVisibleBlockPos(oldBody, rawAssembler);
        final BlockPos restoredOrigin = visibleAssembler.below().south(2);
        if (!(blockEntity(task.level, oldBody, rawAssembler) instanceof final PhysicsAssemblerBlockEntity assembler)) {
            fail(task, "outer assembler missing");
            return;
        }
        assembler.assembleOrDisassemble(null);
        if (body(task) != null || !fullFingerprint(task.level, restoredOrigin, null)) {
            fail(task, "outer Sable disassembly did not restore the static fixture");
            return;
        }
        task.origin = restoredOrigin;
        task.bodyId = null;
        tell(task, "[M35] CLEANUP phase=OUTER_DISASSEMBLED");
        task.phase = Phase.CLEAN;
    }

    private static void cleanStatic(final Task task) {
        tell(task, "[M35] CLEANUP phase=REMOVE");
        if (!staticRetracted(task) || !(task.partialV4
                ? coreFingerprint(task.level, task.origin, null)
                : fullFingerprint(task.level, task.origin, null))) {
            fail(task, "static fixture changed before cleanup");
            return;
        }
        final ServerLevel level = task.level;
        M35FixtureRunState.stop(task.markerId);
        final BlockPos origin = task.origin;
        // Only the fixture's known structure cells and reserved terrain strip are removed.
        for (int x = -12; x <= -1; x++) {
            removeIf(level, origin.offset(x, 0, 0), com.simibubi.create.AllBlocks.PISTON_EXTENSION_POLE.get());
        }
        removeIf(level, origin, com.simibubi.create.AllBlocks.STICKY_MECHANICAL_PISTON.get());
        removeIf(level, origin.east(), com.simibubi.create.AllBlocks.RADIAL_CHASSIS.get());
        removeIf(level, origin.east().above(), com.simibubi.create.AllBlocks.MECHANICAL_ROLLER.get());
        removeIf(level, origin.east().south(), Blocks.CHEST);
        removeIf(level, origin.below(2), com.simibubi.create.AllBlocks.CREATIVE_MOTOR.get());
        removeIf(level, origin.below(), com.simibubi.create.AllBlocks.CLUTCH.get());
        removeIf(level, origin.below().south(), Blocks.LEVER);
        removeIf(level, origin.north(), Blocks.IRON_BLOCK);
        removeIf(level, origin.north(2), Blocks.IRON_BLOCK);
        removeIf(level, origin.above().north(2), SimulatedBlocks.PHYSICS_ASSEMBLER.get());
        removeFixtureGlue(level, origin);
        final BlockPos lane = task.markerOrigin;
        for (int x = 4; x <= 14; x++) {
            for (int y = -3; y <= 1; y++) {
                final BlockPos cell = lane.offset(x, y, 0);
                if (!level.getBlockState(cell).isAir()) {
                    level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        final Entity marker = level.getEntity(task.markerId);
        if (marker != null) {
            marker.discard();
        }
        TASKS.remove(task.markerId);
        tell(task, "[M35] CLEANUP COMPLETE version=5");
    }

    private static void removeIf(final ServerLevel level, final BlockPos pos, final net.minecraft.world.level.block.Block block) {
        if (level.getBlockState(pos).is(block)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void rollbackFreshSetup(final PendingSetup pending) {
        final ServerLevel level = pending.level;
        final BlockPos origin = pending.origin;
        for (int x = -12; x <= -1; x++) {
            removeIf(level, origin.offset(x, 0, 0), com.simibubi.create.AllBlocks.PISTON_EXTENSION_POLE.get());
        }
        removeIf(level, origin, com.simibubi.create.AllBlocks.STICKY_MECHANICAL_PISTON.get());
        removeIf(level, origin.east(), com.simibubi.create.AllBlocks.RADIAL_CHASSIS.get());
        removeIf(level, origin.east().above(), com.simibubi.create.AllBlocks.MECHANICAL_ROLLER.get());
        removeIf(level, origin.east().south(), Blocks.CHEST);
        removeIf(level, origin.below(2), com.simibubi.create.AllBlocks.CREATIVE_MOTOR.get());
        removeIf(level, origin.below(), com.simibubi.create.AllBlocks.CLUTCH.get());
        removeIf(level, origin.below().south(), Blocks.LEVER);
        removeIf(level, origin.north(), Blocks.IRON_BLOCK);
        removeIf(level, origin.north(2), Blocks.IRON_BLOCK);
        removeIf(level, origin.above().north(2), SimulatedBlocks.PHYSICS_ASSEMBLER.get());
        for (int x = 4; x <= 14; x++) {
            for (int y = -3; y <= 1; y++) {
                removeIf(level, origin.offset(x, y, 0), Blocks.STONE);
            }
        }
        removeFixtureGlue(level, origin);
        for (final net.minecraft.world.entity.decoration.ArmorStand marker : level.getEntitiesOfClass(
                net.minecraft.world.entity.decoration.ArmorStand.class, new AABB(origin).inflate(1),
                entity -> entity.getTags().contains(M35FixtureLookup.ORIGIN_TAG)
                        && entity.blockPosition().equals(origin))) {
            marker.discard();
        }
    }

    private static void removeFixtureGlue(final ServerLevel level, final BlockPos origin) {
        for (final SuperGlueEntity glue : new ArrayList<>(level.getEntitiesOfClass(SuperGlueEntity.class,
                new AABB(origin.offset(0, -2, -2), origin.offset(2, 2, 2)).inflate(0.25D),
                entity -> entity.getTags().contains("sable_m35_glue")))) {
            glue.remove(Entity.RemovalReason.KILLED);
        }
    }

    private static void setLever(final Task task, final boolean powered) {
        setFixtureLever(task.level, body(task), task.origin, powered);
    }

    static void setFixtureLever(final ServerLevel level, final ServerSubLevel body,
                                final BlockPos origin, final boolean powered) {
        final BlockPos pos = origin.below().south();
        final BlockState state = blockState(level, body, pos);
        if (!state.is(Blocks.LEVER)) {
            throw new IllegalStateException("fixture lever missing");
        }
        if (state.getValue(LeverBlock.POWERED) == powered) {
            return;
        }
        final BlockState changed = state.setValue(LeverBlock.POWERED, powered);
        if (body == null) {
            level.setBlock(pos, changed, 3);
            return;
        }
        final BlockPos local = pos.subtract(body.getPlot().getCenterBlock());
        body.getPlot().getEmbeddedLevelAccessor().setBlock(local, changed, 3);
        SubLevelBlockEditHelper.finalizeBlockChanges(body, List.of(
                new SubLevelBlockEditHelper.BlockChange(local, pos, state, changed)));
    }

    private static CreativeMotorBlockEntity motor(final Task task) {
        return blockEntity(task.level, body(task), task.origin.below(2)) instanceof final CreativeMotorBlockEntity motor
                ? motor : null;
    }

    private static BlockState blockState(final ServerLevel level, final ServerSubLevel body, final BlockPos pos) {
        return M35FixtureLookup.blockState(level, body, pos);
    }

    private static BlockEntity blockEntity(final ServerLevel level, final ServerSubLevel body, final BlockPos pos) {
        return M35FixtureLookup.blockEntity(level, body, pos);
    }

    private static ServerSubLevel body(final Task task) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(task.level);
        return container != null && task.bodyId != null
                && container.getSubLevel(task.bodyId) instanceof final ServerSubLevel body ? body : null;
    }

    private static void fail(final Task task, final String reason) {
        TASKS.remove(task.markerId);
        tell(task, "[M35] RETRACT FAILED: " + reason);
    }

    private static int fail(final CommandSourceStack source, final String reason) {
        announce(source, "[M35] CLEANUP FAILED: " + reason);
        return 0;
    }

    private static void tell(final Task task, final String text) {
        Sable.LOGGER.info("SABLE_M35_FIXTURE {}", text);
        for (final UUID observerId : task.observers) {
            final ServerPlayer player = task.level.getServer().getPlayerList().getPlayer(observerId);
            if (player != null) {
                player.sendSystemMessage(Component.literal(text));
            }
        }
    }

    private static void tell(final PendingSetup pending, final String text) {
        Sable.LOGGER.info("SABLE_M35_FIXTURE {}", text);
        for (final UUID observerId : pending.observers) {
            final ServerPlayer player = pending.level.getServer().getPlayerList().getPlayer(observerId);
            if (player != null) {
                player.sendSystemMessage(Component.literal(text));
            }
        }
    }

    private static void announce(final CommandSourceStack source, final String text) {
        Sable.LOGGER.info("SABLE_M35_FIXTURE {}", text);
        for (final UUID observerId : observers(source)) {
            final ServerPlayer player = source.getServer().getPlayerList().getPlayer(observerId);
            if (player != null) {
                player.sendSystemMessage(Component.literal(text));
            }
        }
    }

    private static void reportLookup(final CommandSourceStack source, final M35FixtureLookup.Result result) {
        announce(source, "[M35] LOOKUP candidateCount=" + result.candidates().size()
                + " status=" + result.status());
        int index = 0;
        for (final M35FixtureLookup.Candidate candidate : result.candidates()) {
            if (index >= 8) {
                announce(source, "[M35] LOOKUP remainingCandidates=" + (result.candidates().size() - index));
                break;
            }
            final String prefix = "[M35] LOOKUP candidate[" + index + "] ";
            announce(source, prefix + "markerUuid=" + candidate.marker().getUUID()
                    + " markerVersion=" + (candidate.marker().getTags().contains(VERSION_TAG) ? "5" : "PREVIOUS")
                    + " markerPos=" + candidate.markerOrigin() + " distanceFromSource=" + candidate.distance()
                    + " matchCount=" + candidate.matchCount());
            int inspected = 0;
            for (final M35FixtureLookup.Match match : candidate.inspected()) {
                if (inspected++ >= 8) {
                    announce(source, prefix + "additionalControllerCandidates="
                            + (candidate.inspected().size() - 8));
                    break;
                }
                announce(source, prefix + "origin=" + match.origin() + " sableId=" + match.bodyId()
                        + " piston=" + match.piston() + " motor=" + match.motor()
                        + " clutch=" + match.clutch() + " lever=" + match.lever()
                        + " assembler=" + match.assembler() + " hullBelowAssembler=" + match.hull());
                announce(source, prefix + "roller=" + (match.rollerStatic() ? "STATIC"
                        : match.moving() ? "MOVING" : match.extendedPayload() ? "EXTENDED" : "MISSING")
                        + " chest=" + (match.chestStatic() ? "STATIC"
                        : match.moving() ? "MOVING" : match.extendedPayload() ? "EXTENDED" : "MISSING")
                        + " chassis=" + (match.chassisStatic() ? "STATIC"
                        : match.moving() ? "MOVING" : match.extendedPayload() ? "EXTENDED" : "MISSING")
                        + " movingEntityUuid=" + match.movingEntityId()
                        + " pistonRunning=" + match.pistonRunning()
                        + " pistonOffset=" + match.pistonOffset()
                        + " pistonMovementSpeed=" + match.pistonMovementSpeed()
                        + " fingerprint=" + (match.owned() ? "MATCH" : match.piston()
                        .is(com.simibubi.create.AllBlocks.STICKY_MECHANICAL_PISTON.get()) ? "PARTIAL" : "REJECT")
                        + " confidence=" + (match.owned() ? match.confidence(candidate.marker().getTags()
                        .contains(VERSION_TAG)) : "NONE")
                        + " rejectReason=" + match.rejectReason());
            }
            if (candidate.inspected().isEmpty()) {
                announce(source, prefix + "fingerprint=REJECT rejectReason=" + candidate.rejectReason());
            }
            index++;
        }
    }

    private static List<UUID> observers(final CommandSourceStack source) {
        final List<UUID> ids = new ArrayList<>();
        if (source.getEntity() instanceof final ServerPlayer player) {
            ids.add(player.getUUID());
        }
        for (final ServerPlayer player : source.getLevel().players()) {
            if (player.position().distanceToSqr(source.getPosition()) <= 96.0D * 96.0D
                    && !ids.contains(player.getUUID())) {
                ids.add(player.getUUID());
            }
        }
        return ids;
    }

    private static void serverStopped(final ServerStoppedEvent event) {
        TASKS.clear();
        pendingSetup = null;
        M35FixtureRunState.clear();
    }

    private enum Phase {
        STOP, REVERSE, ENGAGE, WAIT, OUTER, CLEAN
    }

    private static final class Task {
        private final ServerLevel level;
        private final UUID markerId;
        private final List<UUID> observers;
        private final BlockPos markerOrigin;
        private final boolean cleanup;
        private final boolean partialV4;
        private BlockPos origin;
        private UUID bodyId;
        private Phase phase = Phase.STOP;
        private int ticks;

        private Task(final ServerLevel level, final UUID markerId, final List<UUID> observers,
                     final BlockPos markerOrigin, final BlockPos origin, final UUID bodyId, final boolean cleanup,
                     final boolean partialV4) {
            this.level = level;
            this.markerId = markerId;
            this.observers = List.copyOf(observers);
            this.markerOrigin = markerOrigin;
            this.origin = origin;
            this.bodyId = bodyId;
            this.cleanup = cleanup;
            this.partialV4 = partialV4;
        }
    }

    private static final class PendingSetup {
        private final ServerLevel level;
        private final BlockPos origin;
        private final String mode;
        private final List<UUID> observers;
        private int ageTicks;

        private PendingSetup(final ServerLevel level, final BlockPos origin, final String mode,
                             final List<UUID> observers) {
            this.level = level;
            this.origin = origin;
            this.mode = mode;
            this.observers = List.copyOf(observers);
        }
    }
}
