package dev.ryanhcode.sable.mixin.interaction_distance;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Extends Forge's 1.20 reach checks into Sable sub-level coordinates. */
@Mixin(ServerGamePacketListenerImpl.class)
public class PlayerMixin {
    @WrapOperation(method = "handleUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;canReach(Lnet/minecraft/core/BlockPos;D)Z", remap = false))
    private boolean sable$canReachBlock(final ServerPlayer player, final BlockPos pos, final double slop,
                                        final Operation<Boolean> original) {
        return original.call(player, pos, slop) || sable$canReachBlockInSubLevel(player, pos, slop);
    }

    @WrapOperation(method = "handleUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;canReachRaw(Lnet/minecraft/core/BlockPos;D)Z", remap = false))
    private boolean sable$canReachBlockRaw(final ServerPlayer player, final BlockPos pos, final double slop,
                                           final Operation<Boolean> original) {
        return original.call(player, pos, slop) || sable$canReachBlockInSubLevel(player, pos, slop);
    }

    @WrapOperation(method = "handleInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;canReachRaw(Lnet/minecraft/world/entity/Entity;D)Z", remap = false))
    private boolean sable$canReachEntity(final ServerPlayer player, final Entity entity, final double slop,
                                         final Operation<Boolean> original) {
        if (original.call(player, entity, slop)) {
            return true;
        }

        final AABB bounds = entity.getBoundingBox();
        final Vec3 bottomCenter = new Vec3((bounds.minX + bounds.maxX) * 0.5, bounds.minY, (bounds.minZ + bounds.maxZ) * 0.5);
        final SubLevel subLevel = Sable.HELPER.getContaining(player.level(), bottomCenter);
        if (subLevel == null) {
            return false;
        }

        final double range = player.getEntityReach() + slop;
        final Vec3 eyePos = subLevel.logicalPose().transformPositionInverse(player.getEyePosition());
        return bounds.distanceToSqr(eyePos) < range * range;
    }

    private static boolean sable$canReachBlockInSubLevel(final ServerPlayer player, final BlockPos pos, final double slop) {
        final SubLevel subLevel = Sable.HELPER.getContaining(player.level(), pos);
        if (subLevel == null) {
            return false;
        }

        final double range = player.getBlockReach() + slop;
        final Vec3 eyePos = subLevel.logicalPose().transformPositionInverse(player.getEyePosition());
        return new AABB(pos).distanceToSqr(eyePos) < range * range;
    }
}
