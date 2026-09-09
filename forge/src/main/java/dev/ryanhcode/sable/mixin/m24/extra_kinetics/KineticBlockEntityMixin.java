package dev.ryanhcode.sable.mixin.m24.extra_kinetics;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.simulated_team.simulated.mixin_interface.extra_kinetics.KineticBlockEntityExtension;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraBlockPos;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KineticBlockEntity.class)
public abstract class KineticBlockEntityMixin extends SmartBlockEntity implements KineticBlockEntityExtension {
    @Shadow private int validationCountdown;
    @Shadow public abstract boolean hasSource();
    @Unique private boolean simulated$extraKineticsConnected;

    protected KineticBlockEntityMixin(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void simulated$setConnectedToExtraKinetics(final boolean connected) {
        this.simulated$extraKineticsConnected = connected;
    }

    @Override
    public boolean simulated$getConnectedToExtraKinetics() {
        return this.simulated$extraKineticsConnected;
    }

    @Override
    public void simulated$setValidationCountdown(final int countdown) {
        this.validationCountdown = countdown;
    }

    @Redirect(method = "validateKinetics", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity simulated$resolveValidationSource(final Level level, final BlockPos pos) {
        final BlockEntity found = level.getBlockEntity(pos);
        return found instanceof final ExtraKinetics extra && this.simulated$extraKineticsConnected
                ? extra.getExtraKinetics()
                : found;
    }

    @Redirect(method = "setSource", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity simulated$resolveSetSource(final Level level, final BlockPos pos) {
        final BlockEntity found = level.getBlockEntity(pos);
        if (found instanceof final ExtraKinetics extra && pos instanceof ExtraBlockPos) {
            this.simulated$extraKineticsConnected = true;
            return extra.getExtraKinetics();
        }
        return found;
    }

    @Override
    public void setLevel(final Level level) {
        super.setLevel(level);
        if ((Object) this instanceof final ExtraKinetics extra && extra.getExtraKinetics() != null) {
            extra.getExtraKinetics().setLevel(level);
        }
    }

    @Override
    public void setBlockState(final BlockState state) {
        super.setBlockState(state);
        if ((Object) this instanceof final ExtraKinetics extra && extra.getExtraKinetics() != null) {
            extra.getExtraKinetics().setBlockState(state);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if ((Object) this instanceof final ExtraKinetics extra && extra.getExtraKinetics() != null) {
            extra.getExtraKinetics().invalidate();
        }
    }

    @Inject(method = "remove", at = @At("TAIL"), remap = false)
    private void simulated$removeExtra(final CallbackInfo ci) {
        if ((Object) this instanceof final ExtraKinetics extra && extra.getExtraKinetics() != null) {
            extra.getExtraKinetics().remove();
        }
    }

    @Inject(method = "removeSource", at = @At("TAIL"), remap = false)
    private void simulated$clearExtraSourceFlag(final CallbackInfo ci) {
        this.simulated$extraKineticsConnected = false;
    }

    @Inject(method = "write", at = @At("TAIL"), remap = false)
    private void simulated$writeExtra(final CompoundTag tag, final boolean clientPacket, final CallbackInfo ci) {
        if ((Object) this instanceof final ExtraKinetics extra && extra.getExtraKinetics() != null) {
            final CompoundTag nested = new CompoundTag();
            if (clientPacket) {
                extra.getExtraKinetics().writeClient(nested);
            } else {
                extra.getExtraKinetics().saveAdditional(nested);
            }
            tag.put(extra.getExtraKineticsSaveName(), nested);
        }
        if (this.hasSource()) {
            tag.putBoolean("ConnectedToExtraKinetics", this.simulated$extraKineticsConnected);
        }
    }

    @Inject(method = "read", at = @At("TAIL"), remap = false)
    private void simulated$readExtra(final CompoundTag tag, final boolean clientPacket, final CallbackInfo ci) {
        if ((Object) this instanceof final ExtraKinetics extra && extra.getExtraKinetics() != null) {
            final CompoundTag nested = tag.getCompound(extra.getExtraKineticsSaveName());
            if (clientPacket) {
                extra.getExtraKinetics().readClient(nested);
            } else {
                extra.getExtraKinetics().load(nested);
            }
        }
        if (tag.contains("ConnectedToExtraKinetics")) {
            this.simulated$extraKineticsConnected = tag.getBoolean("ConnectedToExtraKinetics");
        }
    }

    @Inject(method = "switchToBlockState", at = @At("TAIL"), remap = false)
    private static void simulated$detachExtraBeforeStateSwitch(final Level level, final BlockPos pos,
                                                                final BlockState state, final CallbackInfo ci) {
        if (level.getBlockEntity(pos) instanceof final ExtraKinetics extra
                && extra.getExtraKinetics() instanceof final GeneratingKineticBlockEntity output
                && output.hasNetwork()) {
            output.detachKinetics();
            output.removeSource();
            output.reActivateSource = true;
        }
    }
}
