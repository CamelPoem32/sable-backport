package dev.ryanhcode.sable.compatibility.create.controllers;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SubLevelBlockStateLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M20.5 controller lifecycle bridge.
 *
 * <p>Create Rope Pulley and Elevator code stores controller/cabin positions in
 * the parent level as raw Sable plot coordinates. Vanilla parent-level lookups at
 * those coordinates miss the blocks because Sable stores plot contents in the
 * allocated plot chunks. This helper keeps Create's normal lifecycle intact while
 * resolving only Sable-owned raw positions through their owning SubLevel.</p>
 */
public final class SableCreateControllerBlockLookup {
    private static final Set<String> LOGGED = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private SableCreateControllerBlockLookup() {
    }

    public static BlockState getBlockState(final Level level,
                                           final BlockPos pos,
                                           final Operation<BlockState> original,
                                           final String phase) {
        final SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        if (subLevel == null) {
            return original.call(level, pos);
        }
        final BlockState state = SubLevelBlockStateLookup.getBlockStateOrAir(subLevel, pos);
        log(phase, subLevel, pos, "getBlockState", state, null, true);
        return state;
    }

    public static BlockState getBlockState(final LevelAccessor level,
                                           final BlockPos pos,
                                           final Operation<BlockState> original,
                                           final String phase) {
        if (!(level instanceof final Level parentLevel)) {
            return original.call(level, pos);
        }
        final SubLevel subLevel = Sable.HELPER.getContaining(parentLevel, pos);
        if (subLevel == null) {
            return original.call(level, pos);
        }
        final BlockState state = SubLevelBlockStateLookup.getBlockStateOrAir(subLevel, pos);
        log(phase, subLevel, pos, "getBlockStateAccessor", state, null, true);
        return state;
    }

    public static @Nullable BlockEntity getBlockEntity(final Level level,
                                                       final BlockPos pos,
                                                       final Operation<BlockEntity> original,
                                                       final String phase) {
        final SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        if (subLevel == null) {
            return original.call(level, pos);
        }
        final BlockEntity blockEntity = SubLevelBlockStateLookup.getBlockEntity(subLevel, pos);
        log(phase, subLevel, pos, "getBlockEntity", blockEntity == null ? Blocks.AIR.defaultBlockState()
                : blockEntity.getBlockState(), blockEntity, true);
        return blockEntity;
    }

    public static @Nullable BlockEntity getBlockEntity(final LevelAccessor level,
                                                       final BlockPos pos,
                                                       final Operation<BlockEntity> original,
                                                       final String phase) {
        if (!(level instanceof final Level parentLevel)) {
            return original.call(level, pos);
        }
        final SubLevel subLevel = Sable.HELPER.getContaining(parentLevel, pos);
        if (subLevel == null) {
            return original.call(level, pos);
        }
        final BlockEntity blockEntity = SubLevelBlockStateLookup.getBlockEntity(subLevel, pos);
        log(phase, subLevel, pos, "getBlockEntityAccessor", blockEntity == null ? Blocks.AIR.defaultBlockState()
                : blockEntity.getBlockState(), blockEntity, true);
        return blockEntity;
    }

    public static boolean isLoaded(final Level level,
                                   final BlockPos pos,
                                   final Operation<Boolean> original,
                                   final String phase) {
        final SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        if (subLevel == null) {
            return original.call(level, pos);
        }
        log(phase, subLevel, pos, "isLoaded", SubLevelBlockStateLookup.getBlockStateOrAir(subLevel, pos),
                null, true);
        return true;
    }

    public static boolean setBlock(final LevelAccessor level,
                                   final BlockPos pos,
                                   final BlockState state,
                                   final int flags,
                                   final Operation<Boolean> original,
                                   final String phase) {
        if (!(level instanceof final Level parentLevel)) {
            return original.call(level, pos, state, flags);
        }
        final SubLevel subLevel = Sable.HELPER.getContaining(parentLevel, pos);
        if (subLevel == null) {
            return original.call(level, pos, state, flags);
        }
        final BlockPos localPos = pos.subtract(subLevel.getPlot().getCenterBlock());
        final boolean changed = subLevel.getPlot().getEmbeddedLevelAccessor().setBlock(localPos, state, flags);
        log(phase, subLevel, pos, "setBlock", state, null, changed);
        return changed;
    }

    private static void log(final String phase,
                            final SubLevel subLevel,
                            final BlockPos rawPos,
                            final String operation,
                            final BlockState state,
                            @Nullable final BlockEntity blockEntity,
                            final boolean result) {
        final String key = phase + ":" + operation + ":" + rawPos.asLong() + ":" + result;
        if (!LOGGED.add(key)) {
            return;
        }
        Sable.LOGGER.info("SABLE_M20_CONTROLLER_LOOKUP phase={} operation={} subLevel={} raw={} local={} "
                        + "state={} blockEntity={} result={} candidateLevel=Sable_plot_storage "
                        + "wouldGenerateChunk=false",
                phase,
                operation,
                subLevel.getUniqueId(),
                rawPos,
                rawPos.subtract(subLevel.getPlot().getCenterBlock()),
                state,
                blockEntity == null ? "none" : blockEntity.getClass().getName(),
                result);
    }
}
