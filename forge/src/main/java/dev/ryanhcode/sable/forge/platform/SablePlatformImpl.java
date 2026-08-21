package dev.ryanhcode.sable.forge.platform;

import com.simibubi.create.foundation.utility.worldWrappers.WrappedServerWorld;
import dev.ryanhcode.sable.platform.SablePlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

public final class SablePlatformImpl implements SablePlatform {
    @Override
    public boolean isWrappedLevel(@Nullable final Level level) {
        return ModList.get().isLoaded("create") && CreateWrappedLevelCheck.isWrapped(level);
    }

    @Override
    public boolean isBlockstateLadder(final BlockState state, final Level level, final BlockPos pos,
                                      final LivingEntity entity) {
        return ForgeHooks.isLivingOnLadder(state, level, pos, entity).isPresent();
    }

    private static final class CreateWrappedLevelCheck {
        private static boolean isWrapped(@Nullable final Level level) {
            return level instanceof WrappedServerWorld;
        }
    }
}
