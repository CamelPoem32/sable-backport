package dev.simulated_team.simulated.util;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity;
import dev.simulated_team.simulated.index.SimulatedBlocks;
import dev.simulated_team.simulated.index.SimulatedConfig;
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import dev.simulated_team.simulated.util.assembly.SimAssemblyException;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class SimAssemblyHelper {

    private SimAssemblyHelper() {
    }

    public static AssemblyResult assembleFromSingleBlock(final Level level, final BlockPos selfPos,
                                                         final BlockPos toAssemble, final boolean includeStart)
            throws AssemblyException {
        if (!(level instanceof final ServerLevel serverLevel) || level.getBlockState(toAssemble).isAir()) {
            final String stage = level.getBlockState(toAssemble).isAir() ? "STARTING_BLOCK_AIR" : "NOT_SERVER_LEVEL";
            logReassemblyFailure(level, selfPos, toAssemble, false, "not_started", 0, null,
                    stage, stage.equals("STARTING_BLOCK_AIR") ? "startingBlock" : "serverLevel");
            throw SimAssemblyException.assemblyFailed(stage,
                    stage.equals("STARTING_BLOCK_AIR") ? "startingBlock" : "serverLevel");
        }

        final SimAssemblyContraption contraption = new SimAssemblyContraption(includeStart ? null : selfPos);
        contraption.searchMovedStructure(level, toAssemble);

        final Collection<BlockPos> blocks = contraption.getBlocks();
        if (blocks.isEmpty()) {
            logReassemblyFailure(level, selfPos, toAssemble, true, "empty", 0, null,
                    "COLLECTED_ZERO_BLOCKS", "contraption.blocks");
            throw SimAssemblyException.assemblyFailed("COLLECTED_ZERO_BLOCKS", "contraption.blocks");
        }

        final BoundingBox3i bounds = BoundingBox3i.from(blocks);
        if (bounds == null) {
            logReassemblyFailure(level, selfPos, toAssemble, true, "bounds_null", blocks.size(), null,
                    "BOUNDS_NULL", "BoundingBox3i.from");
            throw SimAssemblyException.assemblyFailed("BOUNDS_NULL", "BoundingBox3i.from");
        }
        final BlockPos anchor = blocks.stream().findFirst().orElseThrow();
        final Vec3 visibleAnchorBefore = anchor.getCenter();
        logGlueRoundTrip("BEFORE_ASSEMBLY", "parentGlueCount=" + countGlues(serverLevel, blockSelectionAabb(bounds))
                + " selectedGlueCount=" + contraption.getGlues().size()
                + " selectedGlueBounds=" + describeGlueBounds(glueBoxes(contraption.getGlues()))
                + " selectedBlockCount=" + blocks.size());
        logGlueRoundTrip("REASSEMBLY_SELECTION", "gluedBlockCount=" + contraption.getGlues().size()
                + " selectedBlockCount=" + blocks.size()
                + " assemblerIncluded=" + containsPhysicsAssembler(serverLevel, blocks)
                + " selectedPlatformBlocks=not_computed"
                + " selectionSha256=" + selectionDigest(serverLevel, blocks));
        final ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(serverLevel, anchor, blocks, bounds);
        if (subLevel == null) {
            logReassemblyFailure(level, selfPos, toAssemble, true, "searched", blocks.size(), bounds,
                    "SABLE_ASSEMBLE_RETURNED_NULL", "SubLevelAssemblyHelper.assembleBlocks");
            throw SimAssemblyException.assemblyFailed("SABLE_ASSEMBLE_RETURNED_NULL",
                    "SubLevelAssemblyHelper.assembleBlocks");
        }

        final BlockPos rawAnchor = subLevel.getPlot().getCenterBlock();
        moveAssemblyGluesToSubLevel(serverLevel, contraption.getGlues(), rawAnchor.subtract(anchor));
        final AABB plotAabb = plotAabb(subLevel.getPlot().getBoundingBox());
        final List<AABB> subLevelGlueBoxes = collectGlueBoxes(serverLevel, plotAabb);
        logGlueRoundTrip("AFTER_ASSEMBLY", "parentGlueCount=" + countGlues(serverLevel, blockSelectionAabb(bounds))
                + " subLevelGlueCount=" + subLevelGlueBoxes.size()
                + " subLevelGlueBounds=" + describeGlueBounds(subLevelGlueBoxes));
        final Vector3d visibleAnchorAfter = subLevel.logicalPose().transformPosition(
                new Vector3d(rawAnchor.getX() + 0.5D, rawAnchor.getY() + 0.5D, rawAnchor.getZ() + 0.5D));
        final double visibleDelta = visibleAnchorAfter.distance(visibleAnchorBefore.x, visibleAnchorBefore.y, visibleAnchorBefore.z);
        logBodySnapshot(serverLevel, subLevel, "after_assembly", blocks.size());
        return new AssemblyResult(subLevel, rawAnchor.subtract(anchor), contraption.getCheckedBlocks(),
                blocks.size(), contraption.getRejectedBlocks(), contraption.getLastRejectedReason(),
                bounds, visibleDelta);
    }

    public static DisassemblyResult disassembleSubLevel(final ServerLevel level, final ServerSubLevel subLevel,
                                                        final BlockPos subLevelAnchor,
                                                        final BlockPos disassemblyGoal,
                                                        final Rotation rotation) throws AssemblyException {
        return disassembleSubLevel(level, subLevel, subLevelAnchor, disassemblyGoal, rotation, 0);
    }

    public static DisassemblyResult disassembleSubLevel(final ServerLevel level, final ServerSubLevel subLevel,
                                                        final BlockPos subLevelAnchor,
                                                        final BlockPos disassemblyGoal,
                                                        final Rotation rotation,
                                                        final int expectedSourceBlocks) throws AssemblyException {
        logBodySnapshot(level, subLevel, "before_disassembly", expectedSourceBlocks);
        throwDisassemblyExceptions(level, subLevel);

        final BoundingBox3i plotBounds = new BoundingBox3i(subLevel.getPlot().getBoundingBox());
        final List<BlockPos> blocks = collectBlocks(level, subLevel);
        if (expectedSourceBlocks > 0 && blocks.size() < expectedSourceBlocks) {
            Sable.LOGGER.warn("SABLE_M23_REASSEMBLY_FAILURE assemblerPos={} assemblerBEPresent={} assemblerBEClass={} assemblerState={} startingBlock={} contraptionCreated=false searchResult=not_started assembledBlockCount={} assemblyBounds={} failureStage=SOURCE_BLOCK_SET_INCOMPLETE nullOwner=sourceBlocks",
                    subLevelAnchor.toShortString(),
                    level.getBlockEntity(subLevelAnchor) != null,
                    level.getBlockEntity(subLevelAnchor) == null ? "null" : level.getBlockEntity(subLevelAnchor).getClass().getName(),
                    level.getBlockState(subLevelAnchor),
                    subLevelAnchor.toShortString(),
                    blocks.size(),
                    plotBounds);
            throw SimAssemblyException.incompleteSource(expectedSourceBlocks, blocks.size());
        }
        if (blocks.isEmpty()) {
            return new DisassemblyResult(0, 0, List.of(), false, plotBounds);
        }

        final SubLevelAssemblyHelper.AssemblyTransform transform =
                new SubLevelAssemblyHelper.AssemblyTransform(subLevelAnchor, disassemblyGoal,
                        rotation == Rotation.NONE ? 0 : 4 - rotation.ordinal(), rotation, level);
        final List<BlockPos> occupied = findOccupiedRestoreSpace(level, blocks, transform);
        if (!occupied.isEmpty()) {
            throw SimAssemblyException.occupied(occupied);
        }

        final List<AABB> rawGlueBoxes = collectGlueBoxes(level, plotAabb(plotBounds));
        logGlueRoundTrip("BEFORE_DISASSEMBLY", "subLevelGlueCount=" + rawGlueBoxes.size()
                + " subLevelGlueBounds=" + describeGlueBounds(rawGlueBoxes));
        final int blockEntityCount = countBlockEntities(level, blocks);
        ((ServerLevelPlot) subLevel.getPlot()).kickAllEntities();
        try {
            SubLevelAssemblyHelper.moveBlocks(level, transform, blocks);
        } catch (final RuntimeException exception) {
            restoreMissingGlues(level, rawGlueBoxes);
            logBodySnapshot(level, subLevel, "after_disassembly_move_failed_source_preserved", expectedSourceBlocks);
            final List<AABB> preservedGlueBoxes = collectGlueBoxes(level, plotAabb(plotBounds));
            logGlueRoundTrip("AFTER_DISASSEMBLY", "result=MOVE_FAILED_SOURCE_PRESERVED"
                    + " subLevelGlueCount=" + preservedGlueBoxes.size()
                    + " subLevelGlueBounds=" + describeGlueBounds(preservedGlueBoxes));
            throw SimAssemblyException.moveFailed(exception);
        }
        final GlueMoveResult glueMoveResult = moveSubLevelGluesToParent(level, rawGlueBoxes, transform);
        logGlueRoundTrip("AFTER_DISASSEMBLY", "parentGlueCount=" + glueMoveResult.restoredGlueCount()
                + " restoredGlueCount=" + glueMoveResult.restoredGlueCount()
                + " restoredGlueBounds=" + describeGlueBounds(glueMoveResult.restoredGlueBoxes())
                + " sourceGlueRemaining=" + glueMoveResult.sourceGlueRemaining());
        SubLevelAssemblyHelper.moveTrackingPoints(level, plotBounds, null, transform);
        logBodySnapshot(level, subLevel, "after_disassembly_restore_before_remove", expectedSourceBlocks);

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
        }

        return new DisassemblyResult(blocks.size(), blockEntityCount, List.of(), true, plotBounds);
    }

    public static List<BlockPos> collectBlocks(final ServerLevel level, final SubLevel subLevel) {
        final ObjectArrayList<BlockPos> blocks = new ObjectArrayList<>();
        final LevelPlot plot = subLevel.getPlot();
        for (final PlotChunkHolder chunk : plot.getLoadedChunks()) {
            final BoundingBox3ic localChunkBounds = chunk.getBoundingBox();
            if (localChunkBounds == null || localChunkBounds == BoundingBox3i.EMPTY) {
                continue;
            }

            for (int x = localChunkBounds.minX(); x <= localChunkBounds.maxX(); x++) {
                for (int y = localChunkBounds.minY(); y <= localChunkBounds.maxY(); y++) {
                    for (int z = localChunkBounds.minZ(); z <= localChunkBounds.maxZ(); z++) {
                        final BlockPos pos = new BlockPos(
                                x + chunk.getPos().getMinBlockX(),
                                y,
                                z + chunk.getPos().getMinBlockZ());
                        final BlockState state = level.getBlockState(pos);
                        if (!state.isAir()) {
                            blocks.add(pos);
                        }
                    }
                }
            }
        }
        return blocks;
    }

    public static BodySnapshot snapshotBody(final ServerLevel level, final ServerSubLevel subLevel,
                                            final int expectedSourceBlocks) {
        final List<BlockPos> blocks = collectBlocks(level, subLevel);
        final BoundingBox3ic rawBounds = subLevel.getPlot().getBoundingBox();
        int blockEntityCount = 0;
        int springEndpointCount = 0;
        int assemblerCount = 0;
        BlockPos assemblerRawPos = null;
        for (final BlockPos block : blocks) {
            if (level.getBlockEntity(block) != null) {
                blockEntityCount++;
            }
            final BlockState state = level.getBlockState(block);
            if (state.is(SimulatedBlocks.SPRING.get())) {
                springEndpointCount++;
            }
            if (state.is(SimulatedBlocks.PHYSICS_ASSEMBLER.get())) {
                assemblerCount++;
                if (assemblerRawPos == null) {
                    assemblerRawPos = block;
                }
            }
        }
        final int actualPayloadBlockCount = Math.max(0, blocks.size() - springEndpointCount - assemblerCount);
        final int expectedPayloadBlockCount = expectedSourceBlocks > 0
                ? Math.max(0, expectedSourceBlocks - springEndpointCount - assemblerCount)
                : -1;
        return new BodySnapshot(
                subLevel.getUniqueId().toString(),
                blocks.size(),
                blockEntityCount,
                rawBounds.toString(),
                subLevel.logicalPose().toString(),
                assemblerRawPos != null,
                assemblerRawPos == null ? "null" : assemblerRawPos.toShortString(),
                springEndpointCount,
                countBlockEntityActors(subLevel),
                countTrackingPoints(level, rawBounds),
                expectedPayloadBlockCount,
                actualPayloadBlockCount,
                blockSetDigest(level, subLevel, blocks));
    }

    public static void logBodySnapshot(final ServerLevel level, final ServerSubLevel subLevel,
                                       final String phase, final int expectedSourceBlocks) {
        final BodySnapshot snapshot = snapshotBody(level, subLevel, expectedSourceBlocks);
        Sable.LOGGER.info("SABLE_M23_BODY_SNAPSHOT sableId={} phase={} storedBlockCount={} storedBlockEntityCount={} rawBounds={} logicalPose={} assemblerPresent={} assemblerRawPos={} springEndpointCount={} trackingPointCount={} actorCount={} expectedPayloadBlockCount={} actualPayloadBlockCount={} blockSetSha256={}",
                snapshot.sableId(),
                phase,
                snapshot.storedBlockCount(),
                snapshot.storedBlockEntityCount(),
                snapshot.rawBounds(),
                snapshot.logicalPose(),
                snapshot.assemblerPresent(),
                snapshot.assemblerRawPos(),
                snapshot.springEndpointCount(),
                snapshot.trackingPointCount(),
                snapshot.actorCount(),
                snapshot.expectedPayloadBlockCount(),
                snapshot.actualPayloadBlockCount(),
                snapshot.blockSetSha256());
    }

    public static int countBlockEntityActors(final ServerSubLevel subLevel) {
        int count = 0;
        for (final Object ignored : subLevel.getPlot().getBlockEntityActors()) {
            count++;
        }
        return count;
    }

    public static int countSpringActors(final ServerSubLevel subLevel) {
        int count = 0;
        for (final Object actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof SpringBlockEntity) {
                count++;
            }
        }
        return count;
    }

    public static List<String> activeSpringConstraintIds(final ServerLevel level, final ServerSubLevel subLevel) {
        return findActiveSpringConstraints(level, subLevel);
    }

    public static int countTrackingPoints(final ServerLevel level, final BoundingBox3ic bounds) {
        int count = 0;
        for (final Pair<?, ?> ignored : SubLevelTrackingPointSavedData.getOrLoad(level).getAllTrackingPoints(bounds)) {
            count++;
        }
        return count;
    }

    public static BlockPos currentVisibleBlockPos(final SubLevel subLevel, final BlockPos rawPos) {
        final Vector3d visible = subLevel.logicalPose()
                .transformPosition(new Vector3d(rawPos.getX() + 0.5D, rawPos.getY() + 0.5D, rawPos.getZ() + 0.5D));
        return BlockPos.containing(visible.x, visible.y, visible.z);
    }

    private static void moveAssemblyGluesToSubLevel(final ServerLevel level,
                                                    final Collection<SuperGlueEntity> glues,
                                                    final BlockPos offsetBlocks) {
        for (final SuperGlueEntity glue : glues) {
            final AABB movedBox = glue.getBoundingBox().move(Vec3.atLowerCornerOf(offsetBlocks));
            if (addGlueIfMissing(level, movedBox)) {
                glue.remove(Entity.RemovalReason.KILLED);
            }
        }
    }

    private static GlueMoveResult moveSubLevelGluesToParent(final ServerLevel level,
                                                            final List<AABB> rawGlueBoxes,
                                                            final SubLevelAssemblyHelper.AssemblyTransform transform) {
        final ObjectArrayList<AABB> restored = new ObjectArrayList<>();
        for (final AABB box : rawGlueBoxes) {
            final AABB movedBox = new AABB(
                    transform.apply(new Vec3(box.minX, box.minY, box.minZ)),
                    transform.apply(new Vec3(box.maxX, box.maxY, box.maxZ)));
            if (addGlueIfMissing(level, movedBox)) {
                restored.add(movedBox);
            }
            removeGlueBox(level, box);
        }
        int sourceRemaining = 0;
        for (final AABB rawBox : rawGlueBoxes) {
            if (hasGlueBox(level, rawBox)) {
                sourceRemaining++;
            }
        }
        return new GlueMoveResult(restored.size(), sourceRemaining, restored);
    }

    private static void restoreMissingGlues(final ServerLevel level, final List<AABB> rawGlueBoxes) {
        for (final AABB rawBox : rawGlueBoxes) {
            addGlueIfMissing(level, rawBox);
        }
    }

    private static boolean addGlueIfMissing(final ServerLevel level, final AABB box) {
        if (hasGlueBox(level, box)) {
            return true;
        }
        return level.addFreshEntity(new SuperGlueEntity(level, box));
    }

    private static void removeGlueBox(final ServerLevel level, final AABB box) {
        for (final SuperGlueEntity glue : List.copyOf(level.getEntitiesOfClass(SuperGlueEntity.class, box.inflate(1.0D)))) {
            if (sameGlueBox(glue.getBoundingBox(), box)) {
                glue.remove(Entity.RemovalReason.KILLED);
            }
        }
    }

    private static boolean hasGlueBox(final ServerLevel level, final AABB box) {
        for (final SuperGlueEntity glue : level.getEntitiesOfClass(SuperGlueEntity.class, box.inflate(1.0D))) {
            if (sameGlueBox(glue.getBoundingBox(), box)) {
                return true;
            }
        }
        return false;
    }

    private static List<AABB> collectGlueBoxes(final ServerLevel level, final AABB bounds) {
        final ObjectArrayList<AABB> boxes = new ObjectArrayList<>();
        for (final SuperGlueEntity glue : level.getEntitiesOfClass(SuperGlueEntity.class, bounds.inflate(1.0D))) {
            if (bounds.intersects(glue.getBoundingBox())) {
                boxes.add(glue.getBoundingBox());
            }
        }
        return boxes;
    }

    private static List<AABB> glueBoxes(final Collection<SuperGlueEntity> glues) {
        final ObjectArrayList<AABB> boxes = new ObjectArrayList<>();
        for (final SuperGlueEntity glue : glues) {
            boxes.add(glue.getBoundingBox());
        }
        return boxes;
    }

    private static int countGlues(final ServerLevel level, final AABB bounds) {
        return collectGlueBoxes(level, bounds).size();
    }

    private static AABB blockSelectionAabb(final BoundingBox3ic bounds) {
        return new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX() + 1.0D, bounds.maxY() + 1.0D, bounds.maxZ() + 1.0D).inflate(1.0D);
    }

    private static AABB plotAabb(final BoundingBox3ic bounds) {
        return new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX() + 1.0D, bounds.maxY() + 1.0D, bounds.maxZ() + 1.0D);
    }

    private static String describeGlueBounds(final Collection<AABB> boxes) {
        if (boxes.isEmpty()) {
            return "empty";
        }
        final StringBuilder result = new StringBuilder();
        int index = 0;
        for (final AABB box : boxes.stream()
                .sorted(Comparator.comparingDouble((AABB box) -> box.minX)
                        .thenComparingDouble(box -> box.minY)
                        .thenComparingDouble(box -> box.minZ))
                .toList()) {
            if (index++ > 0) {
                result.append(';');
            }
            result.append(formatBox(box));
        }
        return result.toString();
    }

    private static String formatBox(final AABB box) {
        return box.minX + "," + box.minY + "," + box.minZ + "->"
                + box.maxX + "," + box.maxY + "," + box.maxZ;
    }

    private static boolean sameGlueBox(final AABB left, final AABB right) {
        final double epsilon = 1.0E-6D;
        return Math.abs(left.minX - right.minX) < epsilon
                && Math.abs(left.minY - right.minY) < epsilon
                && Math.abs(left.minZ - right.minZ) < epsilon
                && Math.abs(left.maxX - right.maxX) < epsilon
                && Math.abs(left.maxY - right.maxY) < epsilon
                && Math.abs(left.maxZ - right.maxZ) < epsilon;
    }

    private static boolean containsPhysicsAssembler(final ServerLevel level, final Collection<BlockPos> blocks) {
        for (final BlockPos block : blocks) {
            if (level.getBlockState(block).is(SimulatedBlocks.PHYSICS_ASSEMBLER.get())) {
                return true;
            }
        }
        return false;
    }

    private static void throwDisassemblyExceptions(final ServerLevel level, final ServerSubLevel subLevel)
            throws AssemblyException {
        final List<String> activeConstraints = findActiveSpringConstraints(level, subLevel);
        if (!activeConstraints.isEmpty()) {
            throw SimAssemblyException.activeConstraint(activeConstraints);
        }

        final RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle == null) {
            return;
        }

        final double linear = handle.getLinearVelocity(new Vector3d()).lengthSquared();
        final double angular = handle.getAngularVelocity(new Vector3d()).lengthSquared();
        if (linear > Mth.square(SimulatedConfig.M22_DISASSEMBLY_MAX_VELOCITY.get().floatValue())
                || angular > Mth.square(SimulatedConfig.M22_DISASSEMBLY_MAX_ANGULAR_VELOCITY.get().floatValue())) {
            throw SimAssemblyException.tooFast();
        }

        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
        if (physicsSystem != null && !physicsSystem.getPipeline().isBodyRegistered(subLevel)) {
            Sable.LOGGER.warn("M22 disassembling sub-level without registered physics body: {}", subLevel);
        }
    }

    private static List<String> findActiveSpringConstraints(final ServerLevel level, final ServerSubLevel subLevel) {
        final ObjectArrayList<String> active = new ObjectArrayList<>();
        for (final BlockPos block : collectBlocks(level, subLevel)) {
            if (level.getBlockEntity(block) instanceof final SpringBlockEntity spring
                    && spring.isActiveConstraint()) {
                active.add(spring.logicalConstraintId());
            }
        }
        return active;
    }

    private static List<BlockPos> findOccupiedRestoreSpace(final ServerLevel level, final Iterable<BlockPos> blocks,
                                                           final SubLevelAssemblyHelper.AssemblyTransform transform) {
        final ObjectArrayList<BlockPos> occupied = new ObjectArrayList<>();
        for (final BlockPos raw : blocks) {
            final BlockPos target = transform.apply(raw);
            if (!level.getBlockState(target).isAir()) {
                occupied.add(target);
            }
        }
        return occupied;
    }

    public static int countBlockEntities(final ServerLevel level, final Iterable<BlockPos> blocks) {
        int count = 0;
        for (final BlockPos block : blocks) {
            if (level.getBlockEntity(block) != null) {
                count++;
            }
        }
        return count;
    }

    private static String blockSetDigest(final ServerLevel level, final ServerSubLevel subLevel,
                                         final List<BlockPos> blocks) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final BlockPos rawOrigin = subLevel.getPlot().getCenterBlock();
            blocks.stream()
                    .sorted(Comparator.comparingLong(BlockPos::asLong))
                    .forEach(pos -> {
                        final BlockState state = level.getBlockState(pos);
                        final BlockPos local = pos.subtract(rawOrigin);
                        final String row = local.toShortString() + "|"
                                + ForgeRegistries.BLOCKS.getKey(state.getBlock()) + "|" + state + "\n";
                        digest.update(row.getBytes(StandardCharsets.UTF_8));
                    });
            return toHex(digest.digest());
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String selectionDigest(final ServerLevel level, final Collection<BlockPos> blocks) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            blocks.stream()
                    .sorted(Comparator.comparingLong(BlockPos::asLong))
                    .forEach(pos -> {
                        final BlockState state = level.getBlockState(pos);
                        final String row = pos.toShortString() + "|"
                                + ForgeRegistries.BLOCKS.getKey(state.getBlock()) + "|" + state + "\n";
                        digest.update(row.getBytes(StandardCharsets.UTF_8));
                    });
            return toHex(digest.digest());
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void logGlueRoundTrip(final String phase, final String fields) {
        Sable.LOGGER.info("SABLE_M22_GLUE_ROUNDTRIP phase={} {}", phase, fields);
    }

    private static String toHex(final byte[] bytes) {
        final StringBuilder result = new StringBuilder(bytes.length * 2);
        for (final byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private static void logReassemblyFailure(final Level level, final BlockPos assemblerPos,
                                             final BlockPos startingBlock, final boolean contraptionCreated,
                                             final String searchResult, final int assembledBlockCount,
                                             @Nullable final BoundingBox3i assemblyBounds,
                                             final String failureStage, final String nullOwner) {
        final BlockEntity assemblerBE = level.getBlockEntity(assemblerPos);
        Sable.LOGGER.warn("SABLE_M23_REASSEMBLY_FAILURE assemblerPos={} assemblerBEPresent={} assemblerBEClass={} assemblerState={} startingBlock={} contraptionCreated={} searchResult={} assembledBlockCount={} assemblyBounds={} failureStage={} nullOwner={}",
                assemblerPos.toShortString(),
                assemblerBE != null,
                assemblerBE == null ? "null" : assemblerBE.getClass().getName(),
                level.getBlockState(assemblerPos),
                startingBlock.toShortString(),
                contraptionCreated,
                searchResult,
                assembledBlockCount,
                assemblyBounds,
                failureStage,
                nullOwner);
    }

    public static Rotation rotationFrom90DegRots(final int rots) {
        return switch (Math.floorMod(rots, 4)) {
            case 0 -> Rotation.NONE;
            case 1 -> Rotation.COUNTERCLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.CLOCKWISE_90;
            default -> throw new AssertionError();
        };
    }

    public record AssemblyResult(ServerSubLevel subLevel, BlockPos offset, int candidateBlocks,
                                 int acceptedBlocks, int rejectedBlocks, String rejectedReason,
                                 BoundingBox3i bounds, double visibleDeltaAfterAssembly) {
    }

    public record DisassemblyResult(int restoredBlockCount, int restoredBlockEntityCount,
                                    Collection<BlockPos> missingBlocks, boolean oldSableRemoved,
                                    BoundingBox3i oldRawBounds) {
    }

    public record BodySnapshot(String sableId, int storedBlockCount, int storedBlockEntityCount,
                               String rawBounds, String logicalPose, boolean assemblerPresent,
                               String assemblerRawPos, int springEndpointCount, int actorCount,
                               int trackingPointCount, int expectedPayloadBlockCount,
                               int actualPayloadBlockCount, String blockSetSha256) {
    }

    private record GlueMoveResult(int restoredGlueCount, int sourceGlueRemaining, Collection<AABB> restoredGlueBoxes) {
    }
}
