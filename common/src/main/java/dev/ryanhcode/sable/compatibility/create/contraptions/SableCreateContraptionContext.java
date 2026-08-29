package dev.ryanhcode.sable.compatibility.create.contraptions;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixin.compatibility.create.contraptions.ControlledContraptionEntityAccessor;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** Shared identity helpers for Create contraptions whose controller lives inside a Sable plot. */
public final class SableCreateContraptionContext {
    private SableCreateContraptionContext() {
    }

    public static @Nullable SubLevel getContainingSubLevel(final AbstractContraptionEntity entity) {
        final SubLevel direct = Sable.HELPER.getContaining((Entity) entity);
        if (direct != null) {
            return direct;
        }

        final BlockPos controllerPos = getControllerPos(entity);
        return controllerPos == null ? null : Sable.HELPER.getContaining(entity.level(), controllerPos);
    }

    public static boolean isRawEntityInSubLevelPlot(final AbstractContraptionEntity entity, final SubLevel subLevel) {
        return Sable.HELPER.getContaining((Entity) entity) == subLevel;
    }

    public static @Nullable BlockPos getControllerPos(final AbstractContraptionEntity entity) {
        if (entity instanceof ControlledContraptionEntity) {
            return ((ControlledContraptionEntityAccessor) entity).sable$getControllerPos();
        }
        return null;
    }
}
