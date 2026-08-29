package dev.ryanhcode.sable.mixin.interaction_distance;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Extends Forge's block-breaking reach checks into visible Sable sub-level coordinates. */
@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {
    @Shadow
    @Final
    protected ServerPlayer player;

    @WrapOperation(method = "handleBlockBreakAction",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayerGameMode;testPlayerCanReach(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean sable$testPlayerCanReachBlockBreak(final ServerPlayerGameMode gameMode,
                                                       final BlockPos pos,
                                                       final Operation<Boolean> original) {
        return original.call(gameMode, pos) || sable$canReachBlockInSubLevel(this.player, pos, 1.5);
    }

    @WrapOperation(method = "handleBlockBreakAction",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;canReach(Lnet/minecraft/core/BlockPos;D)Z"))
    private boolean sable$canReachBlockBreak(final ServerPlayer player,
                                             final BlockPos pos,
                                             final double slop,
                                             final Operation<Boolean> original) {
        return original.call(player, pos, slop) || sable$canReachBlockInSubLevel(player, pos, slop);
    }

    @WrapOperation(method = "handleBlockBreakAction",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;canReachRaw(Lnet/minecraft/core/BlockPos;D)Z"))
    private boolean sable$canReachBlockBreakRaw(final ServerPlayer player,
                                                final BlockPos pos,
                                                final double slop,
                                                final Operation<Boolean> original) {
        return original.call(player, pos, slop) || sable$canReachBlockInSubLevel(player, pos, slop);
    }

    private static boolean sable$canReachBlockInSubLevel(final ServerPlayer player,
                                                         final BlockPos pos,
                                                         final double slop) {
        final SubLevel subLevel = Sable.HELPER.getContaining(player.level(), pos);
        if (subLevel == null) {
            return false;
        }

        final double range = player.getBlockReach() + slop;
        final Vec3 eyePos = subLevel.logicalPose().transformPositionInverse(player.getEyePosition());
        return new AABB(pos).distanceToSqr(eyePos) < range * range;
    }
}
