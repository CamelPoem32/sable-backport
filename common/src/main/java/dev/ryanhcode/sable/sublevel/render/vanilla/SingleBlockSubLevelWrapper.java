package dev.ryanhcode.sable.sublevel.render.vanilla;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.util.SubLevelBlockStateLookup;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SingleBlockSubLevelWrapper implements BlockAndTintGetter {

    private ClientSubLevel subLevel;
    private ClientLevel level;
    private final BlockPos.MutableBlockPos globalPos;
    private final BlockPos.MutableBlockPos localPos;
    private BlockState state;

    public SingleBlockSubLevelWrapper() {
        this.globalPos = new BlockPos.MutableBlockPos();
        this.localPos = new BlockPos.MutableBlockPos();
    }

    public void setup(final ClientLevel level, final double x, final double y, final double z, final BlockPos localPos, final BlockState state) {
        this.subLevel = null;
        this.level = level;
        this.globalPos.set(x, y, z);
        this.localPos.set(localPos);
        this.state = state;
    }

    public void setup(final ClientSubLevel subLevel, final ClientLevel level, final double x, final double y,
                      final double z, final BlockPos localPos, final BlockState state) {
        this.subLevel = subLevel;
        this.level = level;
        this.globalPos.set(x, y, z);
        this.localPos.set(localPos);
        this.state = state;
    }

    public void clear() {
        this.subLevel = null;
        this.level = null;
    }

    @Override
    public float getShade(final Direction direction, final boolean bl) {
        return this.level.getShade(direction, bl);
    }

    @Override
    public @NotNull LevelLightEngine getLightEngine() {
        return this.level.getLightEngine();
    }

    @Override
    public int getBrightness(final LightLayer lightLayer, final BlockPos pos) {
        return this.getLightEngine().getLayerListener(lightLayer).getLightValue(this.globalPos);
    }

    @Override
    public int getRawBrightness(final BlockPos pos, final int i) {
        return this.getLightEngine().getRawBrightness(this.globalPos, i);
    }

    @Override
    public boolean canSeeSky(final BlockPos pos) {
        return this.getBrightness(LightLayer.SKY, this.globalPos) >= this.getMaxLightLevel();
    }

    @Override
    public int getBlockTint(final BlockPos pos, final ColorResolver colorResolver) {
        return this.level.getBlockTint(pos, colorResolver);
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(final BlockPos pos) {
        if (this.subLevel != null) {
            return SubLevelBlockStateLookup.getBlockEntity(this.subLevel, pos);
        }

        return this.level.getBlockEntity(pos);
    }

    @Override
    public @NotNull BlockState getBlockState(final BlockPos pos) {
        if (pos.equals(this.localPos)) {
            return this.state;
        }

        if (this.subLevel != null) {
            return SubLevelBlockStateLookup.getBlockStateOrAir(this.subLevel, pos);
        }

        return this.level.getBlockState(pos);
    }

    @Override
    public @NotNull FluidState getFluidState(final BlockPos pos) {
        if (pos.equals(this.localPos)) {
            return this.state.getFluidState();
        }

        if (this.subLevel != null) {
            return SubLevelBlockStateLookup.getFluidStateOrEmpty(this.subLevel, pos);
        }

        return this.level.getFluidState(pos);
    }

    @Override
    public int getHeight() {
        return this.level.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return this.level.getMinBuildHeight();
    }

    public ClientLevel getLevel() {
        return this.level;
    }
}
