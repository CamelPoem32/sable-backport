package dev.ryanhcode.sable.mixin.compatibility.create.behaviour_compatibility.funnel;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.logistics.funnel.FunnelMovementBehaviour;
import dev.ryanhcode.sable.command.M37FunnelFixtureCommands;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Read-only, fixture-owned observation of Create's cell-entry pickup. */
@Mixin(value = FunnelMovementBehaviour.class, remap = false)
public class FunnelFixtureTimingMixin {
    @Inject(method = "succ", at = @At("HEAD"))
    private void sable$observeNativeFixtureQuery(final MovementContext context, final BlockPos pos,
                                                 final CallbackInfo ci) {
        M37FunnelFixtureCommands.observeNativeSucc(context, pos);
    }
}
