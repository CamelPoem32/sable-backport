package dev.ryanhcode.sable.mixin.plot;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableChunkEventPlatform;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Makes the chunk access methods in the client chunk cache use the plot system.
 */
@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {

    @Unique
    private static final Set<Long> SABLE$LOGGED_CHUNK_STATES = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Shadow
    @Final
    private static Logger LOGGER;
    @Shadow
    @Final
    private ClientLevel level;
    @Shadow
    @Final
    private LevelChunk emptyChunk;

    @Shadow
    private static boolean isValidChunk(@Nullable final LevelChunk levelChunk, final int i, final int j) {
        return false;
    }

    @Unique
    private @NotNull SubLevelContainer sable$getPlotContainer() {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);

        if (container == null) {
            throw new IllegalStateException("Plot container not found in level");
        }
        return container;
    }

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;", at = @At("HEAD"), cancellable = true)
    private void getChunk(final int x, final int z, final ChunkStatus status, final boolean create, final CallbackInfoReturnable<LevelChunk> cir) {
        final SubLevelContainer container = this.sable$getPlotContainer();

        if (container.inBounds(x, z)) {
            final ChunkPos chunkPos = new ChunkPos(x, z);
            final LevelChunk chunk = container.getChunk(chunkPos);

            if (chunk != null) {
                cir.setReturnValue(chunk);
            } else {
                cir.setReturnValue(this.emptyChunk);
            }
        }
    }

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void drop(final int x, final int z, final CallbackInfo ci) {
        final SubLevelContainer container = this.sable$getPlotContainer();

        if (container.inBounds(x, z)) {
            ci.cancel();
            throw new UnsupportedOperationException("Cannot drop chunks in plot");
        }
    }

    @Inject(method = "replaceBiomes", at = @At("HEAD"), cancellable = true)
    private void replaceBiomes(final int x, final int z, final FriendlyByteBuf friendlyByteBuf, final CallbackInfo ci) {
        final SubLevelContainer container = this.sable$getPlotContainer();

        if (container.inBounds(x, z)) {
            final ChunkPos chunkPos = new ChunkPos(x, z);
            final LevelChunk levelChunk = container.getChunk(chunkPos);

            if (levelChunk == null || !isValidChunk(levelChunk, x, z)) {
                LOGGER.warn("Ignoring chunk since it's not present: {}, {}", x, z);
            } else {
                levelChunk.replaceBiomes(friendlyByteBuf);
            }
        }
    }

    @Inject(method = "replaceWithPacketData", at = @At("HEAD"), cancellable = true)
    private void replaceWithPacketData(final int x, final int z, final FriendlyByteBuf friendlyByteBuf, final CompoundTag compoundTag,
                                       final Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer, final CallbackInfoReturnable<LevelChunk> cir) {
        final SubLevelContainer container = this.sable$getPlotContainer();

        if (container.inBounds(x, z)) {
            final ChunkPos chunkPos = new ChunkPos(x, z);
            LevelChunk levelChunk = container.getChunk(chunkPos);
            if (!isValidChunk(levelChunk, x, z)) {
                if (levelChunk != null) {
                    SableChunkEventPlatform.INSTANCE.onOldChunkInvalid(levelChunk);
                    this.level.unload(levelChunk);
                }
                levelChunk = new LevelChunk(this.level, chunkPos);
                levelChunk.replaceWithPacketData(friendlyByteBuf, compoundTag, consumer);
                container.newPopulatedChunk(chunkPos, levelChunk);
            } else {
                levelChunk.replaceWithPacketData(friendlyByteBuf, compoundTag, consumer);
            }

            this.level.onChunkLoaded(chunkPos);
            this.level.getLightEngine().setLightEnabled(chunkPos, true);

            SableChunkEventPlatform.INSTANCE.onClientChunkPacketReplaced(levelChunk);
            this.sable$logSubLevelChunkState(container, chunkPos, levelChunk);
            cir.setReturnValue(levelChunk);
        }
    }

    @Unique
    private void sable$logSubLevelChunkState(final SubLevelContainer container, final ChunkPos chunkPos, final LevelChunk chunk) {
        final long chunkKey = ChunkPos.asLong(chunkPos.x, chunkPos.z);
        if (!SABLE$LOGGED_CHUNK_STATES.add(chunkKey)) {
            return;
        }

        final LevelPlot plot = container.getPlot(chunkPos);
        final SubLevel subLevel = plot == null ? null : plot.getSubLevel();
        if (subLevel == null) {
            return;
        }

        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                for (int y = this.level.getMinBuildHeight(); y < this.level.getMaxBuildHeight(); y++) {
                    pos.set(x, y, z);
                    final BlockState state = chunk.getBlockState(pos);
                    if (!state.isAir()) {
                        Sable.LOGGER.info("SABLE_CLIENT phase=block_state_received id={} plotChunk={} pos={} state={}",
                                subLevel.getUniqueId(), chunkPos, pos.immutable(), state.getBlock());
                        return;
                    }
                }
            }
        }

        Sable.LOGGER.info("SABLE_CLIENT phase=block_state_received id={} plotChunk={} state=air_only",
                subLevel.getUniqueId(), chunkPos);
    }
}
