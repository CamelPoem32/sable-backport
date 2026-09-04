package dev.simulated_team.simulated.util.assembly;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.chassis.AbstractChassisBlock;
import com.simibubi.create.content.contraptions.chassis.ChassisBlockEntity;
import com.simibubi.create.content.contraptions.gantry.GantryCarriageBlock;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock.PistonState;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonHeadBlock;
import com.simibubi.create.content.contraptions.piston.PistonExtensionPoleBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.gantry.GantryShaftBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import dev.simulated_team.simulated.index.SimulatedConfig;
import dev.simulated_team.simulated.service.SimAssemblyService;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.data.UniqueLinkedList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.material.PushReaction;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock.isExtensionPole;
import static com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock.isPistonHead;

public class SimAssemblyContraption {

    private static final BlockPos[] DIRECTION_OFFSETS = new BlockPos[] {
            new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
            new BlockPos(0, 1, 0), new BlockPos(0, -1, 0),
            new BlockPos(0, 0, 1), new BlockPos(0, 0, -1),
            new BlockPos(1, 1, 0), new BlockPos(-1, -1, 0),
            new BlockPos(1, -1, 0), new BlockPos(-1, 1, 0),
            new BlockPos(1, 0, 1), new BlockPos(-1, 0, -1),
            new BlockPos(1, 0, -1), new BlockPos(-1, 0, 1),
            new BlockPos(0, 1, 1), new BlockPos(0, -1, -1),
            new BlockPos(0, -1, 1), new BlockPos(0, 1, -1)
    };

    private final BlockPos anchor;
    private final ObjectOpenHashSet<BlockPos> blocks = new ObjectOpenHashSet<>(1 << 12);
    private final ObjectOpenHashSet<SuperGlueEntity> glueCache = new ObjectOpenHashSet<>();
    private int checkedBlocks;
    private int rejectedBlocks;
    private String lastRejectedReason = "none";
    private BlockPos lastFrontierPos;
    private String lastFrontierState = "none";
    private BlockPos lastFrontierFromPos;
    private String lastFrontierFromState = "none";
    private String lastFrontierReason = "none";

    public SimAssemblyContraption(final BlockPos anchor) {
        this.anchor = anchor;
    }

    public boolean searchMovedStructure(final Level level, final BlockPos pos) throws AssemblyException {
        final Queue<BlockPos> frontier = new UniqueLinkedList<>();
        final Set<BlockPos> visited = new HashSet<>();
        final Set<BlockPos> immutableVisited = Collections.unmodifiableSet(visited);

        if (!BlockMovementChecks.isBrittle(level.getBlockState(pos))) {
            frontier.add(pos);
        }

        final int maxBlocksMoved = SimulatedConfig.M22_MAX_BLOCKS_MOVED.get();
        for (int limit = maxBlocksMoved; limit > 0; limit--) {
            if (frontier.isEmpty()) {
                return true;
            }
            if (!this.moveBlock(level, frontier, visited, immutableVisited)) {
                return false;
            }
        }

        throw SimAssemblyException.structureTooLarge(pos, visited.size(), frontier.size(), maxBlocksMoved,
                this.lastFrontierPos, this.lastFrontierState, this.lastFrontierFromPos,
                this.lastFrontierFromState, this.lastFrontierReason);
    }

    protected boolean moveBlock(final Level world, final Queue<BlockPos> frontier,
                                final Set<BlockPos> visited, final Set<BlockPos> immutableVisitedView)
            throws AssemblyException {
        final BlockPos pos = frontier.poll();
        if (pos == null) {
            return false;
        }

        this.checkedBlocks++;
        visited.add(pos);

        if (world.isOutsideBuildHeight(pos)) {
            this.reject("outsideBuildHeight");
            return true;
        }

        if (!world.isLoaded(pos)) {
            throw AssemblyException.unloadedChunk(pos);
        }

        if (this.isAnchoringBlockAt(pos)) {
            this.reject("anchor");
            return true;
        }

        final BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            this.reject("air");
            return true;
        }

        if (!this.movementAllowed(state, world, pos)) {
            throw SimAssemblyException.unmovableBlock(pos, state);
        }

        if (state.getBlock() instanceof AbstractChassisBlock
                && !this.moveChassis(world, pos, null, frontier, visited)) {
            return false;
        }

        if (state.hasProperty(ChestBlock.TYPE) && state.hasProperty(ChestBlock.FACING)
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            final Direction offset = ChestBlock.getConnectedDirection(state);
            final BlockPos attached = pos.relative(offset);
            if (!visited.contains(attached)) {
                frontier.add(attached);
            }
        }

        if (state.getBlock() instanceof final AbstractBogeyBlock<?> bogey) {
            for (final Direction direction : bogey.getStickySurfaces(world, pos, state)) {
                if (!visited.contains(pos.relative(direction))) {
                    frontier.add(pos.relative(direction));
                }
            }
        }

        final BlockPos posDown = pos.below();
        final BlockState stateBelow = world.getBlockState(posDown);
        if (!visited.contains(posDown) && AllBlocks.CART_ASSEMBLER.has(stateBelow)) {
            frontier.add(posDown);
        }

        if (isPistonHead(state)) {
            this.movePistonHead(world, pos, frontier, visited, state);
        } else if (isExtensionPole(state)) {
            this.movePistonPole(world, pos, frontier, visited, state);
        } else if (state.getBlock() instanceof MechanicalPistonBlock) {
            this.moveMechanicalPiston(world, pos, frontier, visited, state);
        } else if (AllBlocks.GANTRY_CARRIAGE.has(state)) {
            this.moveGantryPinion(world, pos, frontier, visited, state);
        } else if (AllBlocks.GANTRY_SHAFT.has(state)) {
            this.moveGantryShaft(world, pos, frontier, visited, state);
        }

        for (final BlockPos offsetDirection : DIRECTION_OFFSETS) {
            final int absTotal = Math.abs(offsetDirection.getX())
                    + Math.abs(offsetDirection.getY())
                    + Math.abs(offsetDirection.getZ());
            final Direction offsetDirectionNullable = absTotal == 1
                    ? Direction.fromDelta(offsetDirection.getX(), offsetDirection.getY(), offsetDirection.getZ())
                    : null;

            final BlockPos offsetPos = pos.offset(offsetDirection);
            final BlockState blockState = world.getBlockState(offsetPos);
            if (this.isAnchoringBlockAt(offsetPos) || !this.movementAllowed(blockState, world, offsetPos)) {
                continue;
            }

            final boolean wasVisited = visited.contains(offsetPos);
            final boolean faceHasGlue = this.checkAndCacheGlue(world, pos, offsetDirection);
            boolean blockAttachedTowardsFace = offsetDirectionNullable != null
                    && BlockMovementChecks.isBlockAttachedTowards(blockState, world, offsetPos,
                    offsetDirectionNullable.getOpposite());
            final boolean brittle = BlockMovementChecks.isBrittle(blockState);

            boolean canStick = !brittle
                    && SimAssemblyService.canStickTo(state, blockState)
                    && SimAssemblyService.canStickTo(blockState, state);

            if (canStick) {
                if (state.getPistonPushReaction() == PushReaction.PUSH_ONLY
                        || blockState.getPistonPushReaction() == PushReaction.PUSH_ONLY) {
                    canStick = false;
                }

                if (offsetDirectionNullable != null) {
                    if (BlockMovementChecks.isNotSupportive(state, offsetDirectionNullable)
                            || BlockMovementChecks.isNotSupportive(blockState, offsetDirectionNullable.getOpposite())) {
                        canStick = false;
                    }
                }
            }

            if (!wasVisited && (canStick || blockAttachedTowardsFace || faceHasGlue)) {
                this.recordFrontierAddition(offsetPos, blockState, pos, state,
                        faceHasGlue ? "GLUE" : blockAttachedTowardsFace ? "ATTACHED_TOWARDS_FACE" : "BLOCK_STICKINESS");
                frontier.add(offsetPos);
            }
        }

        this.blocks.add(pos);
        if (this.blocks.size() <= SimulatedConfig.M22_MAX_BLOCKS_MOVED.get()) {
            return true;
        }
        throw SimAssemblyException.structureTooLarge(pos, visited.size(), frontier.size(),
                SimulatedConfig.M22_MAX_BLOCKS_MOVED.get(), this.lastFrontierPos, this.lastFrontierState,
                this.lastFrontierFromPos, this.lastFrontierFromState, this.lastFrontierReason);
    }

    public boolean checkAndCacheGlue(final LevelAccessor level, final BlockPos blockPos, final BlockPos offsetDir) {
        final BlockPos targetPos = blockPos.offset(offsetDir);
        for (final SuperGlueEntity glueEntity : this.glueCache) {
            if (glueEntity.contains(blockPos) && glueEntity.contains(targetPos)) {
                return true;
            }
        }

        for (final SuperGlueEntity glueEntity : level.getEntitiesOfClass(SuperGlueEntity.class,
                SuperGlueEntity.span(blockPos, targetPos).inflate(16))) {
            if (!glueEntity.contains(blockPos) || !glueEntity.contains(targetPos)) {
                continue;
            }
            this.glueCache.add(glueEntity);
            return true;
        }

        return false;
    }

    protected void movePistonHead(final Level world, final BlockPos pos, final Queue<BlockPos> frontier,
                                  final Set<BlockPos> visited, final BlockState state) {
        final Direction direction = state.getValue(MechanicalPistonHeadBlock.FACING);
        final BlockPos offset = pos.relative(direction.getOpposite());
        if (!visited.contains(offset)) {
            final BlockState blockState = world.getBlockState(offset);
            if (isExtensionPole(blockState)
                    && blockState.getValue(PistonExtensionPoleBlock.FACING).getAxis() == direction.getAxis()) {
                frontier.add(offset);
            }
            if (blockState.getBlock() instanceof MechanicalPistonBlock) {
                final Direction pistonFacing = blockState.getValue(MechanicalPistonBlock.FACING);
                if (pistonFacing == direction && blockState.getValue(MechanicalPistonBlock.STATE) == PistonState.EXTENDED) {
                    frontier.add(offset);
                }
            }
        }
        if (state.getValue(MechanicalPistonHeadBlock.TYPE) == PistonType.STICKY) {
            final BlockPos attached = pos.relative(direction);
            if (!visited.contains(attached)) {
                frontier.add(attached);
            }
        }
    }

    protected void movePistonPole(final Level world, final BlockPos pos, final Queue<BlockPos> frontier,
                                  final Set<BlockPos> visited, final BlockState state) {
        for (final Direction direction : Iterate.directionsInAxis(state.getValue(PistonExtensionPoleBlock.FACING).getAxis())) {
            final BlockPos offset = pos.relative(direction);
            if (visited.contains(offset)) {
                continue;
            }
            final BlockState blockState = world.getBlockState(offset);
            if (isExtensionPole(blockState)
                    && blockState.getValue(PistonExtensionPoleBlock.FACING).getAxis() == direction.getAxis()) {
                frontier.add(offset);
            }
            if (isPistonHead(blockState)
                    && blockState.getValue(MechanicalPistonHeadBlock.FACING).getAxis() == direction.getAxis()) {
                frontier.add(offset);
            }
            if (blockState.getBlock() instanceof MechanicalPistonBlock) {
                final Direction pistonFacing = blockState.getValue(MechanicalPistonBlock.FACING);
                if (pistonFacing == direction || pistonFacing == direction.getOpposite()
                        && blockState.getValue(MechanicalPistonBlock.STATE) == PistonState.EXTENDED) {
                    frontier.add(offset);
                }
            }
        }
    }

    protected void moveGantryPinion(final Level world, final BlockPos pos, final Queue<BlockPos> frontier,
                                    final Set<BlockPos> visited, final BlockState state) {
        BlockPos offset = pos.relative(state.getValue(GantryCarriageBlock.FACING));
        if (!visited.contains(offset)) {
            frontier.add(offset);
        }
        final Axis rotationAxis = ((IRotate) state.getBlock()).getRotationAxis(state);
        for (final Direction direction : Iterate.directionsInAxis(rotationAxis)) {
            offset = pos.relative(direction);
            final BlockState offsetState = world.getBlockState(offset);
            if (AllBlocks.GANTRY_SHAFT.has(offsetState)
                    && offsetState.getValue(GantryShaftBlock.FACING).getAxis() == direction.getAxis()
                    && !visited.contains(offset)) {
                frontier.add(offset);
            }
        }
    }

    protected void moveGantryShaft(final Level world, final BlockPos pos, final Queue<BlockPos> frontier,
                                   final Set<BlockPos> visited, final BlockState state) {
        for (final Direction direction : Iterate.directions) {
            final BlockPos offset = pos.relative(direction);
            if (visited.contains(offset)) {
                continue;
            }
            final BlockState offsetState = world.getBlockState(offset);
            final Direction facing = state.getValue(GantryShaftBlock.FACING);
            if (direction.getAxis() == facing.getAxis()
                    && AllBlocks.GANTRY_SHAFT.has(offsetState)
                    && offsetState.getValue(GantryShaftBlock.FACING) == facing) {
                frontier.add(offset);
            } else if (AllBlocks.GANTRY_CARRIAGE.has(offsetState)
                    && offsetState.getValue(GantryCarriageBlock.FACING) == direction) {
                frontier.add(offset);
            }
        }
    }

    private boolean moveMechanicalPiston(final Level world, final BlockPos pos, final Queue<BlockPos> frontier,
                                         final Set<BlockPos> visited, final BlockState state) {
        final Direction direction = state.getValue(MechanicalPistonBlock.FACING);
        final PistonState pistonState = state.getValue(MechanicalPistonBlock.STATE);
        if (pistonState == PistonState.MOVING) {
            return false;
        }

        BlockPos offset = pos.relative(direction.getOpposite());
        if (!visited.contains(offset)) {
            final BlockState poleState = world.getBlockState(offset);
            if (AllBlocks.PISTON_EXTENSION_POLE.has(poleState)
                    && poleState.getValue(PistonExtensionPoleBlock.FACING).getAxis() == direction.getAxis()) {
                frontier.add(offset);
            }
        }

        if (pistonState == PistonState.EXTENDED || MechanicalPistonBlock.isStickyPiston(state)) {
            offset = pos.relative(direction);
            if (!visited.contains(offset)) {
                frontier.add(offset);
            }
        }

        return true;
    }

    private boolean moveChassis(final Level world, final BlockPos pos, final Direction movementDirection,
                                final Queue<BlockPos> frontier, final Set<BlockPos> visited) {
        final BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof final ChassisBlockEntity chassis)) {
            return false;
        }
        chassis.addAttachedChasses(frontier, visited);
        final List<BlockPos> includedBlockPositions = chassis.getIncludedBlockPositions(movementDirection, false);
        if (includedBlockPositions == null) {
            return false;
        }
        for (final BlockPos blockPos : includedBlockPositions) {
            if (!visited.contains(blockPos)) {
                frontier.add(blockPos);
            }
        }
        return true;
    }

    protected boolean movementAllowed(final BlockState state, final Level world, final BlockPos pos) {
        return state.isAir() || state.getDestroySpeed(world, pos) != -1;
    }

    protected boolean isAnchoringBlockAt(final BlockPos pos) {
        return pos.equals(this.anchor);
    }

    private void reject(final String reason) {
        this.rejectedBlocks++;
        this.lastRejectedReason = reason;
    }

    private void recordFrontierAddition(final BlockPos targetPos, final BlockState targetState,
                                        final BlockPos fromPos, final BlockState fromState,
                                        final String attachmentReason) {
        this.lastFrontierPos = targetPos.immutable();
        this.lastFrontierState = targetState.toString();
        this.lastFrontierFromPos = fromPos.immutable();
        this.lastFrontierFromState = fromState.toString();
        this.lastFrontierReason = attachmentReason;
    }

    public Collection<BlockPos> getBlocks() {
        return this.blocks;
    }

    public Collection<SuperGlueEntity> getGlues() {
        return this.glueCache;
    }

    public int getCheckedBlocks() {
        return this.checkedBlocks;
    }

    public int getRejectedBlocks() {
        return this.rejectedBlocks;
    }

    public String getLastRejectedReason() {
        return this.lastRejectedReason;
    }

    public BlockPos getLastFrontierPos() {
        return this.lastFrontierPos;
    }

    public String getLastFrontierState() {
        return this.lastFrontierState;
    }

    public BlockPos getLastFrontierFromPos() {
        return this.lastFrontierFromPos;
    }

    public String getLastFrontierFromState() {
        return this.lastFrontierFromState;
    }

    public String getLastFrontierReason() {
        return this.lastFrontierReason;
    }
}
