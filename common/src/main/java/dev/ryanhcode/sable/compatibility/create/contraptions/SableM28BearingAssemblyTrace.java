package dev.ryanhcode.sable.compatibility.create.contraptions;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixin.compatibility.create.contraptions.ControlledContraptionEntityAccessor;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** M28.11 read-only trace for Create's exact bearing assembly and entity insertion lifecycle. */
public final class SableM28BearingAssemblyTrace {
    public static final String PROPERTY = "sable.m28.traceBearingAssemblyLifecycle";
    private static final int MAX_LOOKUPS = 64;
    private static final ThreadLocal<Session> ACTIVE = new ThreadLocal<>();
    private static final Map<ControlledContraptionEntity, EntityRecord> TRACKED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private SableM28BearingAssemblyTrace() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static void speedChanged(final MechanicalBearingBlockEntity bearing, final float previousSpeed,
                                    final boolean running, final boolean assembleNextTick, final String phase) {
        if (!enabled()) {
            return;
        }
        logBearing("SPEED_CHANGED_" + phase, bearing, running, assembleNextTick,
                "previousSpeed=" + previousSpeed + " currentSpeed=" + bearing.getSpeed());
    }

    public static void tickGate(final MechanicalBearingBlockEntity bearing, final boolean running,
                                final boolean assembleNextTick, final @Nullable ControlledContraptionEntity moved) {
        if (!enabled()) {
            return;
        }
        if (assembleNextTick) {
            precondition(bearing, "SERVER_SIDE", !bearing.getLevel().isClientSide,
                    "tick() lines 32-49 require server side");
            precondition(bearing, "ASSEMBLE_NEXT_TICK", true,
                    "onSpeedChanged() scheduled this tick");
            precondition(bearing, "NOT_ALREADY_RUNNING", !running,
                    "tick() dispatches assemble only from the non-running branch");
            precondition(bearing, "NONZERO_SPEED_OR_WINDMILL", bearing.getSpeed() != 0,
                    "non-windmill Mechanical Bearing requires speed != 0");
            logBearing("ASSEMBLE_PRECONDITION", bearing, running, true,
                    "movedContraption=" + entityIdentity(moved));
        }
        final EntityRecord record = moved == null ? null : TRACKED.get(moved);
        if (record != null && !record.nextTickObserved) {
            record.nextTickObserved = true;
            logInsertion("ENTITY_SURVIVED_NEXT_TICK", moved, bearing.getLevel(),
                    registrationSummary(bearing.getLevel(), moved) + " tickAvailable=true");
        }
    }

    public static void beginAssembly(final MechanicalBearingBlockEntity bearing, final boolean running,
                                     final boolean assembleNextTick) {
        if (!enabled()) {
            return;
        }
        ACTIVE.set(new Session(bearing));
        final Level level = bearing.getLevel();
        final BlockState live = level.getBlockState(bearing.getBlockPos());
        precondition(bearing, "BEARING_BLOCK_PRESENT", live.getBlock() instanceof
                com.simibubi.create.content.contraptions.bearing.BearingBlock,
                "MechanicalBearingBlockEntity.assemble() bytecode lines 0-20");
        logBearing("ASSEMBLE_ENTER", bearing, running, assembleNextTick,
                "liveState=" + live + " blockEntityLevel=" + levelIdentity(level));
    }

    public static void endAssembly(final MechanicalBearingBlockEntity bearing, final boolean running,
                                   final boolean assembleNextTick,
                                   final @Nullable ControlledContraptionEntity moved,
                                   final @Nullable AssemblyException exception) {
        if (!enabled()) {
            return;
        }
        final Session session = ACTIVE.get();
        final String outcome;
        if (exception != null) {
            outcome = "ASSEMBLY_EXCEPTION:" + exception.getClass().getSimpleName() + ":" + exception.getMessage();
        } else if (moved == null) {
            outcome = "NO_CONTROLLED_ENTITY";
        } else {
            outcome = "CONTROLLED_ENTITY_PRESENT";
        }
        logBearing("ASSEMBLE_RETURN", bearing, running, assembleNextTick,
                "outcome=" + outcome + " movedContraption=" + entityIdentity(moved)
                        + " lookupCount=" + (session == null ? 0 : session.lookups));
        ACTIVE.remove();
    }

    public static boolean contraptionAssemble(final BearingContraption contraption, final Level level,
                                               final BlockPos bearingPos,
                                               final ThrowingBooleanOperation operation) throws AssemblyException {
        if (!enabled() || ACTIVE.get() == null) {
            return operation.call();
        }
        final Direction facing = contraption.getFacing();
        structure("CONTRAPTION_ASSEMBLE_ENTER", level, bearingPos, bearingPos.relative(facing), null,
                "facing=" + facing + " movementDirection=null contraption=" + identity(contraption));
        try {
            final boolean result = operation.call();
            final int captured = contraption.getBlocks().size();
            structure("CONTRAPTION_ASSEMBLE_RESULT", level, bearingPos, bearingPos.relative(facing), null,
                    "result=" + result + " capturedBlockCount=" + captured
                            + " sailBlocks=" + contraption.getSailBlocks());
            logCaptured("CAPTURE_COMPLETE", contraption);
            return result;
        } catch (final AssemblyException exception) {
            structure("CONTRAPTION_ASSEMBLE_RESULT", level, bearingPos, bearingPos.relative(facing), null,
                    "result=EXCEPTION failureReason=" + exception.getClass().getName() + ":" + exception.getMessage());
            throw exception;
        }
    }

    public static void searchEnter(final Contraption contraption, final Level level, final BlockPos start,
                                   final @Nullable Direction movementDirection) {
        final Session session = ACTIVE.get();
        if (!enabled() || session == null) {
            return;
        }
        structure("STRUCTURE_SEARCH_ENTER", level, session.bearing.getBlockPos(), start, movementDirection,
                "contraption=" + identity(contraption));
    }

    public static void searchReturn(final Contraption contraption, final Level level, final BlockPos start,
                                    final @Nullable Direction movementDirection, final boolean result) {
        final Session session = ACTIVE.get();
        if (!enabled() || session == null) {
            return;
        }
        structure("STRUCTURE_SEARCH_RESULT", level, session.bearing.getBlockPos(), start, movementDirection,
                "result=" + result + " capturedBlockCount=" + contraption.getBlocks().size());
    }

    public static void captureEnter(final Level level, final BlockPos queriedPos) {
        final Session session = ACTIVE.get();
        if (!enabled() || session == null || session.lookups++ >= MAX_LOOKUPS) {
            return;
        }
        final BlockState state = level.getBlockState(queriedPos);
        structure("CAPTURE_LOOKUP", level, session.bearing.getBlockPos(), queriedPos, null,
                "blockState=" + state + " blockId=" + BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    public static void captureReturn(final Level level, final BlockPos queriedPos,
                                     final @Nullable Pair<StructureTemplate.StructureBlockInfo, ?> captured) {
        final Session session = ACTIVE.get();
        if (!enabled() || session == null || session.lookups > MAX_LOOKUPS) {
            return;
        }
        final BlockState state = captured == null ? null : captured.getLeft().state();
        structure("CAPTURE_RESULT", level, session.bearing.getBlockPos(), queriedPos, null,
                "captured=" + (captured != null) + " capturedState=" + state);
    }

    public static void blocksRemoved(final BearingContraption contraption, final Level level, final BlockPos offset) {
        if (!enabled() || ACTIVE.get() == null) {
            return;
        }
        logCaptured("BLOCKS_REMOVED_FROM_WORLD", contraption);
        final Session session = ACTIVE.get();
        structure("BLOCKS_REMOVED_FROM_WORLD", level, session.bearing.getBlockPos(), offset, null,
                "capturedBlockCount=" + contraption.getBlocks().size());
    }

    public static ControlledContraptionEntity entityCreated(final Level level, final Object controller,
                                                             final Contraption contraption,
                                                             final ControlledContraptionEntity entity) {
        if (!enabled() || ACTIVE.get() == null) {
            return entity;
        }
        final Session session = ACTIVE.get();
        final BlockPos controllerPos = ((ControlledContraptionEntityAccessor) entity).sable$getControllerPos();
        TRACKED.put(entity, new EntityRecord(session.bearing));
        logBearing("ENTITY_CONSTRUCTED", session.bearing, false, false,
                "entity=" + entityIdentity(entity) + " contraption=" + identity(contraption)
                        + " capturedBlockCount=" + contraption.getBlocks().size()
                        + " entityLevel=" + levelIdentity(entity.level()));
        logBearing("CONTROLLER_ASSIGNED", session.bearing, false, false,
                "controllerClass=" + controller.getClass().getName() + " controllerPos=" + controllerPos);
        return entity;
    }

    public static boolean addEntity(final Level level, final Entity entity, final BooleanOperation operation) {
        if (!enabled() || !(entity instanceof final ControlledContraptionEntity controlled)
                || !TRACKED.containsKey(controlled)) {
            return operation.call();
        }
        logInsertion("ENTITY_ADD_REQUEST", controlled, level,
                "entityLevelBefore=" + levelIdentity(controlled.level()) + " removedBefore=" + controlled.isRemoved());
        final boolean result = operation.call();
        logInsertion("ENTITY_ADD_RESULT", controlled, level,
                "returnValue=" + result + " removedAfter=" + controlled.isRemoved() + " "
                        + registrationSummary(level, controlled));
        logInsertion("ENTITY_REGISTERED", controlled, level,
                "registered=" + result + " " + registrationSummary(level, controlled));
        return result;
    }

    public static void entityRemoved(final AbstractContraptionEntity entity, final Entity.RemovalReason reason) {
        if (enabled() && entity instanceof final ControlledContraptionEntity controlled && TRACKED.containsKey(controlled)) {
            logInsertion("ENTITY_REMOVED", controlled, controlled.level(), "removalReason=" + reason);
        }
    }

    public static void disassemble(final MechanicalBearingBlockEntity bearing, final boolean running,
                                   final @Nullable ControlledContraptionEntity moved, final String phase) {
        if (enabled()) {
            logBearing("DISASSEMBLE_" + phase, bearing, running, false,
                    "movedContraption=" + entityIdentity(moved));
        }
    }

    private static void precondition(final MechanicalBearingBlockEntity bearing, final String name,
                                     final boolean value, final String source) {
        final Level level = bearing.getLevel();
        final SubLevel owner = owner(level, bearing.getBlockPos());
        Sable.LOGGER.info("SABLE_M31_ASSEMBLY_PRECONDITION side={} gameTime={} sableId={} bearingLocalPos={} "
                        + "bearingPlotPos={} condition={} evaluatedValue={} decision={} exactSource={}",
                side(level), level.getGameTime(), ownerId(owner), local(owner, bearing.getBlockPos()),
                bearing.getBlockPos(), name, value, value ? "PASS" : "BLOCK", source);
    }

    private static void logBearing(final String event, final MechanicalBearingBlockEntity bearing,
                                   final boolean running, final boolean assembleNextTick, final String detail) {
        final Level level = bearing.getLevel();
        final SubLevel owner = owner(level, bearing.getBlockPos());
        final BlockState state = bearing.getBlockState();
        final Direction facing = state.hasProperty(com.simibubi.create.content.contraptions.bearing.BearingBlock.FACING)
                ? state.getValue(com.simibubi.create.content.contraptions.bearing.BearingBlock.FACING) : null;
        Sable.LOGGER.info("SABLE_M31_BEARING_ASSEMBLY event={} side={} gameTime={} sableId={} bearingLocalPos={} "
                        + "bearingPlotPos={} facing={} axis={} kineticSpeed={} running={} assembleNextTick={} "
                        + "levelClass={} levelIdentity={} {}",
                event, side(level), level.getGameTime(), ownerId(owner), local(owner, bearing.getBlockPos()),
                bearing.getBlockPos(), facing, facing == null ? "none" : facing.getAxis(), bearing.getSpeed(), running,
                assembleNextTick, level.getClass().getName(), System.identityHashCode(level), detail);
    }

    private static void structure(final String event, final Level level, final BlockPos bearingPos,
                                  final BlockPos queriedPos, final @Nullable Direction movementDirection,
                                  final String detail) {
        final SubLevel owner = owner(level, bearingPos);
        Sable.LOGGER.info("SABLE_M31_STRUCTURE_LOOKUP event={} side={} gameTime={} sableId={} bearingLocalPos={} "
                        + "bearingPlotPos={} queriedLocalPos={} queriedPlotPos={} coordinateSpace={} movementDirection={} "
                        + "levelClass={} levelIdentity={} {}",
                event, side(level), level.getGameTime(), ownerId(owner), local(owner, bearingPos), bearingPos,
                local(owner, queriedPos), queriedPos, owner == null ? "NORMAL_WORLD" : "SABLE_PLOT_RAW",
                movementDirection, level.getClass().getName(), System.identityHashCode(level), detail);
    }

    private static void logCaptured(final String event, final BearingContraption contraption) {
        final Session session = ACTIVE.get();
        if (session == null) {
            return;
        }
        final String blocks = contraption.getBlocks().entrySet().stream().limit(24)
                .map(entry -> "local=" + entry.getKey() + ",source=" + contraption.anchor.offset(entry.getKey())
                        + ",state=" + entry.getValue().state())
                .toList().toString();
        logBearing(event, session.bearing, false, false,
                "contraption=" + identity(contraption) + " capturedBlockCount=" + contraption.getBlocks().size()
                        + " contraptionAnchor=" + contraption.anchor
                        + " capturedLocalSourcePositionsAndStates=" + blocks);
    }

    private static void logInsertion(final String event, final ControlledContraptionEntity entity,
                                     final Level target, final String detail) {
        final EntityRecord record = TRACKED.get(entity);
        final MechanicalBearingBlockEntity bearing = record == null ? null : record.bearing;
        final BlockPos bearingPos = bearing == null ? BlockPos.ZERO : bearing.getBlockPos();
        final SubLevel owner = owner(target, bearingPos);
        Sable.LOGGER.info("SABLE_M31_ENTITY_INSERTION event={} side={} gameTime={} sableId={} bearingLocalPos={} "
                        + "bearingPlotPos={} entityClass={} entityIdentity={} entityId={} entityUuid={} entityLevel={} "
                        + "insertionTarget={} rawEntityPos={} controllerPos={} {}",
                event, side(target), target.getGameTime(), ownerId(owner), local(owner, bearingPos), bearingPos,
                entity.getClass().getName(), System.identityHashCode(entity), entity.getId(), entity.getUUID(),
                levelIdentity(entity.level()), levelIdentity(target), entity.position(),
                ((ControlledContraptionEntityAccessor) entity).sable$getControllerPos(), detail);
    }

    private static String registrationSummary(final Level level, final ControlledContraptionEntity entity) {
        if (level instanceof final ServerLevel server) {
            return "lookupById=" + (server.getEntity(entity.getId()) == entity)
                    + " lookupByUuid=" + (server.getEntity(entity.getUUID()) == entity);
        }
        return "lookupById=UNAVAILABLE lookupByUuid=UNAVAILABLE";
    }

    private static @Nullable SubLevel owner(final Level level, final BlockPos pos) {
        return level == null ? null : Sable.HELPER.getContaining(level, pos);
    }

    private static String ownerId(final @Nullable SubLevel owner) {
        return owner == null ? "none" : owner.getUniqueId().toString();
    }

    private static BlockPos local(final @Nullable SubLevel owner, final BlockPos raw) {
        return owner == null ? raw : raw.subtract(owner.getPlot().getCenterBlock());
    }

    private static String side(final Level level) {
        return level.isClientSide ? "CLIENT" : "SERVER";
    }

    private static String levelIdentity(final Level level) {
        return level.getClass().getName() + "@" + System.identityHashCode(level);
    }

    private static String entityIdentity(final @Nullable Entity entity) {
        return entity == null ? "none" : entity.getClass().getSimpleName() + "@" + System.identityHashCode(entity)
                + "#" + entity.getId() + "/" + entity.getUUID();
    }

    private static String identity(final Object value) {
        return value.getClass().getSimpleName() + "@" + System.identityHashCode(value);
    }

    public interface ThrowingBooleanOperation {
        boolean call() throws AssemblyException;
    }

    public interface BooleanOperation {
        boolean call();
    }

    private static final class Session {
        private final MechanicalBearingBlockEntity bearing;
        private int lookups;

        private Session(final MechanicalBearingBlockEntity bearing) {
            this.bearing = bearing;
        }
    }

    private static final class EntityRecord {
        private final MechanicalBearingBlockEntity bearing;
        private boolean nextTickObserved;

        private EntityRecord(final MechanicalBearingBlockEntity bearing) {
            this.bearing = bearing;
        }
    }
}
