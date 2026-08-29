package dev.ryanhcode.sable.compatibility.create.contraptions;

import com.simibubi.create.content.contraptions.IControlContraption;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SubLevelBlockStateLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Resolves Create contraption controllers stored in Sable's hidden plot chunks. */
public final class SableCreateContraptionControllerLookup {
    private SableCreateContraptionControllerLookup() {
    }

    public static boolean isControllerPositionLoaded(final Level level, final BlockPos controllerPos) {
        return Sable.HELPER.getContaining(level, controllerPos) != null || level.isLoaded(controllerPos);
    }

    public static @Nullable BlockEntity getControllerBlockEntity(final Level level, final BlockPos controllerPos) {
        final SubLevel subLevel = Sable.HELPER.getContaining(level, controllerPos);
        if (subLevel != null) {
            return SubLevelBlockStateLookup.getBlockEntity(subLevel, controllerPos);
        }
        return level.getBlockEntity(controllerPos);
    }

    public static boolean isSableController(final Level level, final BlockPos controllerPos) {
        final BlockEntity blockEntity = getControllerBlockEntity(level, controllerPos);
        return blockEntity instanceof IControlContraption;
    }
}
