package dev.ryanhcode.sable.compatibility.create.contraptions;

import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Read-only M28.13 ownership trace across M22 outer assembly and disassembly. */
public final class SableM28NestedBearingPayloadTrace {
    public static final String PROPERTY = "sable.m28.traceNestedBearingPayload";
    private static final int MAX_CAPTURED_STATES = 32;
    private static final ThreadLocal<OuterSession> ACTIVE = new ThreadLocal<>();

    private SableM28NestedBearingPayloadTrace() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static void beginOuterAssembly(final ServerLevel level, final BlockPos assemblerPos,
                                          final BlockPos anchor, final Collection<BlockPos> selectedBlocks,
                                          final int candidateBlocks, final int rejectedBlocks) {
        if (!enabled()) {
            return;
        }
        final OuterSession session = new OuterSession(level, assemblerPos.immutable(), anchor.immutable(),
                List.copyOf(selectedBlocks), candidateBlocks, rejectedBlocks, "M22_ASSEMBLY");
        ACTIVE.set(session);
        session.discover();
        session.logAll("NORMAL_WORLD", "owner=PRE_EXISTING_LEVEL_STATE");
        session.logAll("BEFORE_OUTER_ASSEMBLY", "owner=M22_ASSEMBLY");
        session.logAll("OUTER_CAPTURE_DISCOVERY", "owner=M22_SELECTION payloadSelectedAsBlock=");
    }

    public static void beforeBlockTransfer(final Level sourceLevel, final Level targetLevel,
                                           final BlockPos sourcePos, final BlockPos targetPos,
                                           final BlockState state) {
        final OuterSession session = ACTIVE.get();
        if (session == null || !session.relevant(sourcePos)) {
            return;
        }
        session.logTransfer("COPY_BEGIN", sourceLevel, targetLevel, sourcePos, targetPos, state,
                "result=PENDING reason=SELECTED_LIVE_BLOCK");
    }

    public static void afterBlockTransfer(final Level sourceLevel, final Level targetLevel,
                                          final BlockPos sourcePos, final BlockPos targetPos,
                                          final BlockState sourceState) {
        final OuterSession session = ACTIVE.get();
        if (session == null || !session.relevant(sourcePos)) {
            return;
        }
        session.logTransfer("SABLE_STORAGE_INSERTION", sourceLevel, targetLevel, sourcePos, targetPos,
                sourceState, "result=" + targetLevel.getBlockState(targetPos)
                        + " reason=TARGET_CHUNK_SET_BLOCK_STATE");
    }

    public static void afterOuterAssembly(final ServerSubLevel subLevel,
                                          final SubLevelAssemblyHelper.AssemblyTransform transform) {
        final OuterSession session = ACTIVE.get();
        if (session == null) {
            return;
        }
        session.logAfterAssembly(subLevel, transform);
    }

    public static void endOuterAssembly() {
        ACTIVE.remove();
    }

    public static void beginOuterDisassembly(final ServerLevel level, final ServerSubLevel subLevel,
                                             final SubLevelAssemblyHelper.AssemblyTransform transform,
                                             final Collection<BlockPos> blocks) {
        if (!enabled()) {
            return;
        }
        final OuterSession session = new OuterSession(level, BlockPos.ZERO, BlockPos.ZERO, List.copyOf(blocks),
                blocks.size(), 0, "M22_DISASSEMBLY");
        ACTIVE.set(session);
        session.discover();
        for (final BearingChain chain : session.chains) {
            final BlockPos destination = transform.apply(chain.bearingPos);
            logPayload("BEFORE_OUTER_DISASSEMBLY", level, chain, session, subLevel, destination,
                    "owner=M22_DISASSEMBLY destinationState=" + level.getBlockState(destination));
        }
    }

    public static void afterOuterDisassembly(final ServerLevel level,
                                             final SubLevelAssemblyHelper.AssemblyTransform transform) {
        final OuterSession session = ACTIVE.get();
        if (session == null || !session.owner.equals("M22_DISASSEMBLY")) {
            return;
        }
        try {
            for (final BearingChain source : session.chains) {
                final BlockPos targetBearingPos = transform.apply(source.bearingPos);
                if (!(level.getBlockEntity(targetBearingPos) instanceof final MechanicalBearingBlockEntity bearing)) {
                    continue;
                }
                final Direction facing = bearing.getBlockState().getValue(BearingBlock.FACING);
                final BearingChain restored = new BearingChain(null, null, null, bearing, targetBearingPos,
                        facing, targetBearingPos.relative(facing), "RESTORED_M22_BLOCK_TRANSFER");
                logPayload("AFTER_OUTER_DISASSEMBLY", level, restored, session, null, targetBearingPos,
                        "owner=M22_DISASSEMBLY sourceBearingRawPos=" + source.bearingPos);
            }
        } finally {
            ACTIVE.remove();
        }
    }

    private static void logPayload(final String phase, final Level level, final BearingChain chain,
                                   final OuterSession session, final @Nullable SubLevel outer,
                                   final @Nullable BlockPos transferredBearingPos, final String detail) {
        final MechanicalBearingBlockEntity bearing = chain.bearing;
        final ControlledContraptionEntity nested = bearing.getMovedContraption();
        final BlockState payloadState = level.getBlockState(chain.payloadPos);
        final BlockEntity payloadBe = level.getBlockEntity(chain.payloadPos);
        Sable.LOGGER.info("SABLE_M33_NESTED_PAYLOAD phase={} side={} gameTime={} wheelWorldPos={} "
                        + "wheelLocalPos={} wheelState={} wheelBEIdentity={} bearingWorldPos={} bearingLocalPos={} "
                        + "bearingState={} bearingFacing={} bearingSpeed={} bearingRunning={} bearingAssembledState={} "
                        + "bearingBEIdentity={} payloadExpectedWorldPos={} payloadExpectedLocalPos={} "
                        + "payloadBlockState={} payloadBEClass={} nestedContraptionEntityId={} nestedContraptionUuid={} "
                        + "nestedContraptionLevel={} nestedContraptionAlive={} nestedCapturedBlockCount={} "
                        + "nestedCapturedStates={} outerSableId={} outerAssemblerPos={} outerCapturedBlockCount={} "
                        + "outerCapturedBECount={} transferredBearingPos={} payloadSelectedAsBlock={} association={} {}",
                phase, level.isClientSide ? "CLIENT" : "SERVER", level.getGameTime(), chain.wheelPos,
                local(outer, chain.wheelPos), chain.wheelState, identity(chain.wheel), chain.bearingPos,
                local(outer, chain.bearingPos), bearing.getBlockState(), chain.facing, bearing.getSpeed(),
                bearing.isRunning(), nested == null ? "DISASSEMBLED" : "ASSEMBLED", identity(bearing),
                chain.payloadPos, local(outer, chain.payloadPos), payloadState,
                payloadBe == null ? "none" : payloadBe.getClass().getName(),
                nested == null ? "none" : nested.getId(), nested == null ? "none" : nested.getUUID(),
                nested == null ? "none" : levelIdentity(nested.level()), nested != null && !nested.isRemoved(),
                nested == null || nested.getContraption() == null ? 0 : nested.getContraption().getBlocks().size(),
                nestedCapturedStates(nested), outer == null ? "none" : outer.getUniqueId(), session.assemblerPos,
                session.selected.size(), session.selectedBlockEntities, transferredBearingPos,
                session.selected.contains(chain.payloadPos), chain.association, detail);
    }

    private static String nestedCapturedStates(final @Nullable ControlledContraptionEntity nested) {
        if (nested == null || nested.getContraption() == null) {
            return "[]";
        }
        final BlockPos anchor = nested.getContraption().anchor;
        return nested.getContraption().getBlocks().entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getKey().asLong()))
                .limit(MAX_CAPTURED_STATES)
                .map(entry -> capturedState(anchor, entry.getKey(), entry.getValue()))
                .toList().toString();
    }

    private static String capturedState(final BlockPos anchor, final BlockPos local,
                                        final StructureTemplate.StructureBlockInfo info) {
        return "local=" + local + ",source=" + anchor.offset(local) + ",state=" + info.state();
    }

    private static BlockPos local(final @Nullable SubLevel owner, final @Nullable BlockPos raw) {
        if (raw == null) {
            return null;
        }
        return owner == null ? raw : raw.subtract(owner.getPlot().getCenterBlock());
    }

    private static String identity(final @Nullable Object value) {
        return value == null ? "none" : value.getClass().getName() + "@" + System.identityHashCode(value);
    }

    private static String levelIdentity(final Level level) {
        return level.getClass().getName() + "@" + System.identityHashCode(level);
    }

    private static boolean isSteeringWheel(final BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals("simulated:steering_wheel");
    }

    private static final class OuterSession {
        private final ServerLevel level;
        private final BlockPos assemblerPos;
        private final BlockPos anchor;
        private final List<BlockPos> selected;
        private final int candidateBlocks;
        private final int rejectedBlocks;
        private final String owner;
        private final List<BearingChain> chains = new ArrayList<>();
        private int selectedBlockEntities;

        private OuterSession(final ServerLevel level, final BlockPos assemblerPos, final BlockPos anchor,
                             final List<BlockPos> selected, final int candidateBlocks, final int rejectedBlocks,
                             final String owner) {
            this.level = level;
            this.assemblerPos = assemblerPos;
            this.anchor = anchor;
            this.selected = selected;
            this.candidateBlocks = candidateBlocks;
            this.rejectedBlocks = rejectedBlocks;
            this.owner = owner;
        }

        private void discover() {
            final List<BlockEntity> wheels = new ArrayList<>();
            for (final BlockPos pos : this.selected) {
                final BlockEntity blockEntity = this.level.getBlockEntity(pos);
                if (blockEntity != null) {
                    this.selectedBlockEntities++;
                }
                if (isSteeringWheel(this.level.getBlockState(pos)) && blockEntity != null) {
                    wheels.add(blockEntity);
                }
            }
            for (final BlockPos pos : this.selected) {
                if (!(this.level.getBlockEntity(pos) instanceof final MechanicalBearingBlockEntity bearing)) {
                    continue;
                }
                final Direction facing = bearing.getBlockState().getValue(BearingBlock.FACING);
                final BlockEntity wheel = findWheel(bearing, wheels);
                final String association = wheel instanceof final KineticBlockEntity kineticWheel
                        && bearing.network != null && bearing.network.equals(kineticWheel.network)
                        ? "SAME_CREATE_KINETIC_NETWORK" : "NEAREST_SELECTED_STEERING_WHEEL_DIAGNOSTIC";
                this.chains.add(new BearingChain(wheel, wheel == null ? null : wheel.getBlockPos(),
                        wheel == null ? null : wheel.getBlockState(), bearing, pos.immutable(), facing,
                        pos.relative(facing).immutable(), association));
            }
        }

        private static @Nullable BlockEntity findWheel(final MechanicalBearingBlockEntity bearing,
                                                       final List<BlockEntity> wheels) {
            return wheels.stream()
                    .filter(wheel -> wheel instanceof final KineticBlockEntity kinetic
                            && bearing.network != null && bearing.network.equals(kinetic.network))
                    .findFirst()
                    .orElseGet(() -> wheels.stream().min(Comparator.comparingInt(
                            wheel -> wheel.getBlockPos().distManhattan(bearing.getBlockPos()))).orElse(null));
        }

        private boolean relevant(final BlockPos sourcePos) {
            return this.chains.stream().anyMatch(chain -> sourcePos.equals(chain.bearingPos)
                    || sourcePos.equals(chain.payloadPos) || sourcePos.equals(chain.wheelPos));
        }

        private void logAll(final String phase, final String detailPrefix) {
            final SubLevel owner = Sable.HELPER.getContaining(this.level, this.anchor);
            for (final BearingChain chain : this.chains) {
                logPayload(phase, this.level, chain, this, owner, null,
                        detailPrefix + (phase.equals("OUTER_CAPTURE_DISCOVERY")
                                ? this.selected.contains(chain.payloadPos) : "")
                                + " candidateBlocks=" + this.candidateBlocks
                                + " rejectedBlocks=" + this.rejectedBlocks);
            }
        }

        private void logTransfer(final String phase, final Level sourceLevel, final Level targetLevel,
                                 final BlockPos sourcePos, final BlockPos targetPos, final BlockState state,
                                 final String detail) {
            final BearingChain chain = this.chains.stream()
                    .filter(candidate -> sourcePos.equals(candidate.bearingPos)
                            || sourcePos.equals(candidate.payloadPos) || sourcePos.equals(candidate.wheelPos))
                    .findFirst().orElse(null);
            if (chain == null) {
                return;
            }
            final SubLevel targetOwner = Sable.HELPER.getContaining(targetLevel, targetPos);
            final BlockPos targetLocalPos = targetOwner == null
                    ? targetPos
                    : targetPos.subtract(targetOwner.getPlot().getCenterBlock());
            Sable.LOGGER.info("SABLE_M33_OUTER_TRANSFER phase={} owner={} sourceLevel={} "
                            + "sourceWorldPos={} sourceState={} sourceBE={} sourceNestedCCE={} targetSableId={} "
                            + "targetLocalPos={} targetRawPos={} targetState={} transferSourcePos={} "
                            + "transferTargetRawPos={} transferTargetLocalPos={} transferState={} transferOwner={} "
                            + "transferCause=BLOCK_TRANSFER resultDetail={}",
                    phase, this.owner, levelIdentity(sourceLevel), sourcePos, state,
                    identity(sourceLevel.getBlockEntity(sourcePos)),
                    identity(chain.bearing.getMovedContraption()),
                    ownerId(targetOwner), targetLocalPos, targetPos, targetLevel.getBlockState(targetPos),
                    sourcePos, targetPos, targetLocalPos, state, this.owner, detail);
        }

        private void logAfterAssembly(final ServerSubLevel subLevel,
                                      final SubLevelAssemblyHelper.AssemblyTransform transform) {
            for (final BearingChain chain : this.chains) {
                final BlockPos targetBearing = transform.apply(chain.bearingPos);
                final BlockPos targetPayload = transform.apply(chain.payloadPos);
                final BlockEntity targetBe = this.level.getBlockEntity(targetBearing);
                if (targetBe instanceof final MechanicalBearingBlockEntity targetBearingBe) {
                    final Direction facing = targetBearingBe.getBlockState().getValue(BearingBlock.FACING);
                    final BlockPos targetWheel = chain.wheelPos == null ? null : transform.apply(chain.wheelPos);
                    final BlockEntity targetWheelBe = targetWheel == null ? null : this.level.getBlockEntity(targetWheel);
                    final BearingChain transferred = new BearingChain(targetWheelBe, targetWheel,
                            targetWheel == null ? null : this.level.getBlockState(targetWheel), targetBearingBe,
                            targetBearing, facing, targetBearing.relative(facing), chain.association);
                    logPayload("AFTER_OUTER_ASSEMBLY", this.level, transferred, this, subLevel, targetBearing,
                            "owner=M22_ASSEMBLY sourceBearingWorldPos=" + chain.bearingPos
                                    + " sourcePayloadWorldPos=" + chain.payloadPos
                                    + " targetPayloadRawPos=" + targetPayload
                                    + " targetPayloadState=" + this.level.getBlockState(targetPayload));
                } else {
                    Sable.LOGGER.info("SABLE_M33_NESTED_PAYLOAD phase=AFTER_OUTER_ASSEMBLY side=SERVER "
                                    + "gameTime={} bearingWorldPos={} bearingLocalPos={} bearingState={} "
                                    + "bearingBEIdentity=none payloadExpectedWorldPos={} payloadExpectedLocalPos={} "
                                    + "payloadBlockState={} outerSableId={} result=BEARING_BLOCK_ENTITY_NOT_RESTORED",
                            this.level.getGameTime(), targetBearing,
                            targetBearing.subtract(subLevel.getPlot().getCenterBlock()),
                            this.level.getBlockState(targetBearing), targetPayload,
                            targetPayload.subtract(subLevel.getPlot().getCenterBlock()),
                            this.level.getBlockState(targetPayload), subLevel.getUniqueId());
                }
            }
        }

        private static String ownerId(final @Nullable SubLevel owner) {
            return owner == null ? "none" : owner.getUniqueId().toString();
        }
    }

    private record BearingChain(@Nullable BlockEntity wheel, @Nullable BlockPos wheelPos,
                                @Nullable BlockState wheelState, MechanicalBearingBlockEntity bearing,
                                BlockPos bearingPos, Direction facing, BlockPos payloadPos, String association) {
    }
}
