package dev.ryanhcode.sable.mixin.compatibility.create.contraptions;

import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionControllerLookup;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ControlledContraptionEntity.class, remap = false)
public abstract class ControlledContraptionEntityMixin extends Entity {
    @Unique
    private boolean sable$loggedControllerBridge;

    public ControlledContraptionEntityMixin(final EntityType<?> type, final Level level) {
        super(type, level);
    }

    @Redirect(method = "tickContraption",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;isLoaded(Lnet/minecraft/core/BlockPos;)Z",
                    remap = true))
    private boolean sable$isControllerPositionLoadedDuringTick(final Level level, final BlockPos controllerPos) {
        return SableCreateContraptionControllerLookup.isControllerPositionLoaded(level, controllerPos);
    }

    @Redirect(method = "getController",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;isLoaded(Lnet/minecraft/core/BlockPos;)Z",
                    remap = true))
    private boolean sable$isControllerPositionLoadedDuringLookup(final Level level, final BlockPos controllerPos) {
        return SableCreateContraptionControllerLookup.isControllerPositionLoaded(level, controllerPos);
    }

    @Redirect(method = "getController",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
                    remap = true))
    private BlockEntity sable$getControllerBlockEntity(final Level level, final BlockPos controllerPos) {
        final BlockEntity blockEntity =
                SableCreateContraptionControllerLookup.getControllerBlockEntity(level, controllerPos);
        if (!this.sable$loggedControllerBridge && Sable.HELPER.getContaining(level, controllerPos) != null) {
            this.sable$loggedControllerBridge = true;
            final SubLevel containing = Sable.HELPER.getContaining(this);
            Sable.LOGGER.info("SABLE_M13_CONTROLLER_LOOKUP entityId={} controllerPos={} controllerBE={} "
                            + "controllerIsSable={} containingSubLevel={}",
                    this.getId(),
                    controllerPos,
                    blockEntity == null ? "none" : blockEntity.getClass().getName(),
                    blockEntity != null,
                    containing == null ? "none" : containing.getUniqueId());
        }
        return blockEntity;
    }
}
