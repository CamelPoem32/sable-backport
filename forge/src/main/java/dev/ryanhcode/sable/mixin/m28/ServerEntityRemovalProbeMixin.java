package dev.ryanhcode.sable.mixin.m28;

import dev.ryanhcode.sable.compatibility.create.contraptions.SableM28NormalWorldCceSync;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Correlates vanilla tracker removal with the remove packet emitted by ServerEntity. */
@Mixin(ServerEntity.class)
public abstract class ServerEntityRemovalProbeMixin {
    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "removePairing", at = @At("HEAD"))
    private void sable$removePairing(final ServerPlayer player, final CallbackInfo ci) {
        SableM28NormalWorldCceSync.stopTracking(player, this.entity, callerFingerprint());
    }

    private static String callerFingerprint() {
        if (!Boolean.getBoolean(SableM28NormalWorldCceSync.M29_TRACE_PROPERTY)) {
            return "trace_disabled";
        }
        return StackWalker.getInstance().walk(frames -> frames
                .filter(frame -> !frame.getClassName().equals(ServerEntityRemovalProbeMixin.class.getName()))
                .limit(10)
                .map(frame -> frame.getClassName() + '#' + frame.getMethodName())
                .reduce((left, right) -> left + " <- " + right)
                .orElse("unavailable"));
    }
}
