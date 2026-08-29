package dev.ryanhcode.sable.mixin.compatibility.create.contraptions;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionCollider;
import com.simibubi.create.foundation.collision.Matrix3d;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = ContraptionCollider.class, remap = false)
public class ContraptionColliderMixin {
    @Unique
    private static final Set<Integer> SABLE$LOGGED_COLLISION =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Unique
    private static org.joml.Matrix3d sable$toJOML(final Matrix3d createMatrix) {
        final org.joml.Matrix3d jomlMatrix = new org.joml.Matrix3d();
        final Matrix3dAccessor accessor = (Matrix3dAccessor) createMatrix;
        jomlMatrix.set(
                accessor.sable$getM00(), accessor.sable$getM01(), accessor.sable$getM02(),
                accessor.sable$getM10(), accessor.sable$getM11(), accessor.sable$getM12(),
                accessor.sable$getM20(), accessor.sable$getM21(), accessor.sable$getM22()
        );
        return jomlMatrix;
    }

    @Unique
    private static Matrix3d sable$toCreate(final org.joml.Matrix3d jomlMatrix) {
        final Matrix3d createMatrix = new Matrix3d();
        final Matrix3dAccessor accessor = (Matrix3dAccessor) createMatrix;

        accessor.sable$setM00(jomlMatrix.m00);
        accessor.sable$setM01(jomlMatrix.m01);
        accessor.sable$setM02(jomlMatrix.m02);
        accessor.sable$setM10(jomlMatrix.m10);
        accessor.sable$setM11(jomlMatrix.m11);
        accessor.sable$setM12(jomlMatrix.m12);
        accessor.sable$setM20(jomlMatrix.m20);
        accessor.sable$setM21(jomlMatrix.m21);
        accessor.sable$setM22(jomlMatrix.m22);

        return createMatrix;
    }

    @Redirect(method = "collideEntities",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getBoundingBox()Lnet/minecraft/world/phys/AABB;",
                    remap = true))
    private static AABB sable$contraptionBounds(final AbstractContraptionEntity instance,
                                                @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = SableCreateContraptionContext.getContainingSubLevel(instance);
        contraptionSubLevel.set(subLevel);
        if (subLevel == null) {
            return instance.getBoundingBox();
        }

        final AABB rawAabb = instance.getBoundingBox();
        final BoundingBox3d visibleAabb = new BoundingBox3d(rawAabb);
        visibleAabb.transform(subLevel.logicalPose(), visibleAabb);
        sable$logCollision(instance, subLevel, rawAabb, visibleAabb.toMojang(), "bounds");
        return visibleAabb.toMojang();
    }

    @Redirect(method = "collideEntities",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/AABB;expandTowards(DDD)Lnet/minecraft/world/phys/AABB;",
                    remap = true))
    private static AABB sable$entityQueryBounds(final AABB instance,
                                                final double d,
                                                final double e,
                                                final double f,
                                                @Local(argsOnly = true) final AbstractContraptionEntity contraption,
                                                @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();
        if (subLevel == null) {
            return instance.expandTowards(d, e, f);
        }

        final BoundingBox3d visibleAabb = new BoundingBox3d(contraption.getBoundingBox().inflate(2.0).expandTowards(d, e, f));
        visibleAabb.transform(subLevel.logicalPose(), visibleAabb);
        return visibleAabb.toMojang();
    }

    @Redirect(method = "collideEntities",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;position()Lnet/minecraft/world/phys/Vec3;",
                    remap = true))
    private static Vec3 sable$contraptionPosition(final AbstractContraptionEntity instance,
                                                  @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();
        return subLevel == null ? instance.position() : subLevel.logicalPose().transformPosition(instance.position());
    }

    @Redirect(method = "collideEntities",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getPrevPositionVec()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$getPrevPositionVec(final AbstractContraptionEntity instance,
                                                 @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();
        return subLevel == null ? instance.getPrevPositionVec() : subLevel.logicalPose().transformPosition(instance.getPrevPositionVec());
    }

    @Redirect(method = "collideEntities",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getAnchorVec()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$getAnchorVec(final AbstractContraptionEntity instance,
                                           @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();
        if (subLevel == null) {
            return instance.getAnchorVec();
        }
        return subLevel.logicalPose().transformPosition(instance.getAnchorVec().add(0.5, 0.5, 0.5))
                .subtract(0.5, 0.5, 0.5);
    }

    @Redirect(method = "collideEntities",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity$ContraptionRotationState;asMatrix()Lcom/simibubi/create/foundation/collision/Matrix3d;"))
    private static Matrix3d sable$rotationMatrix(final AbstractContraptionEntity.ContraptionRotationState rotationState,
                                                 @Local(argsOnly = true) final AbstractContraptionEntity contraption,
                                                 @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();
        if (subLevel == null) {
            return rotationState.asMatrix();
        }

        final org.joml.Matrix3d jomlMatrix = sable$toJOML(rotationState.asMatrix());
        jomlMatrix.rotateLocal(new Pose3d(subLevel.logicalPose()).orientation());
        return sable$toCreate(jomlMatrix);
    }

    @Redirect(method = "collideEntities",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;toLocalVector(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$toLocalVector(final AbstractContraptionEntity instance,
                                            final Vec3 localVec,
                                            final float partialTicks,
                                            @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();
        return subLevel == null ? instance.toLocalVector(localVec, partialTicks)
                : instance.toLocalVector(subLevel.logicalPose().transformPositionInverse(localVec), partialTicks);
    }

    @Redirect(method = "collideEntities",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getContactPointMotion(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$getContactPointMotion(final AbstractContraptionEntity instance,
                                                    final Vec3 globalContactPoint,
                                                    @Share("subLevel") final LocalRef<SubLevel> contraptionSubLevel) {
        final SubLevel subLevel = contraptionSubLevel.get();
        if (subLevel == null) {
            return instance.getContactPointMotion(globalContactPoint);
        }

        final Pose3d pose = new Pose3d(subLevel.logicalPose());
        final Vec3 localContactPoint = pose.transformPositionInverse(globalContactPoint);
        return pose.transformNormal(instance.getContactPointMotion(localContactPoint))
                .add(globalContactPoint.subtract(subLevel.lastPose().transformPosition(localContactPoint)));
    }

    @Unique
    private static void sable$logCollision(final AbstractContraptionEntity entity,
                                           final SubLevel subLevel,
                                           final AABB rawAabb,
                                           final AABB visibleAabb,
                                           final String phase) {
        if (!SABLE$LOGGED_COLLISION.add(entity.getId())) {
            return;
        }
        Sable.LOGGER.info("SABLE_M13_COLLISION entityId={} containingSubLevel={} rawCollisionAabb={} "
                        + "visibleExpectedCollisionAabb={} createColliderActive={} lastCollisionCheck={}",
                entity.getId(), subLevel.getUniqueId(), rawAabb, visibleAabb, true, phase);
    }
}
