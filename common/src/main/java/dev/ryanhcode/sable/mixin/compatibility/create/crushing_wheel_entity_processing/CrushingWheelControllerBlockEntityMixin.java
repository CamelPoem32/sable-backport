package dev.ryanhcode.sable.mixin.compatibility.create.crushing_wheel_entity_processing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Direct upstream Sable Create crusher compatibility port. Create stores the
 * processing entity in parent-world coordinates, while the controller BE lives
 * at a hidden plot position. During Create's own processing tick, project the
 * entity probes into the owning Sable so AABB and center comparisons remain in
 * the same coordinate space as worldPosition.
 */
@Mixin(value = CrushingWheelControllerBlockEntity.class, remap = false)
public abstract class CrushingWheelControllerBlockEntityMixin extends SmartBlockEntity {
    @Shadow
    public Entity processingEntity;

    @Unique
    private static final Set<Long> SABLE$LOGGED_PROCESSING =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Unique
    private SubLevel sable$parentSublevel = null;

    public CrushingWheelControllerBlockEntityMixin(final BlockEntityType<?> typeIn,
                                                   final BlockPos pos,
                                                   final BlockState state) {
        super(typeIn, pos, state);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    public void sable$initSublevel(final CallbackInfo ci) {
        this.sable$parentSublevel = Sable.HELPER.getContaining(this);
        if (this.sable$parentSublevel != null && this.processingEntity != null
                && SABLE$LOGGED_PROCESSING.add(this.worldPosition.asLong())) {
            final AABB visibleAabb = this.processingEntity.getBoundingBox();
            final BoundingBox3d rawAabb = new BoundingBox3d(visibleAabb);
            rawAabb.transformInverse(this.sable$parentSublevel.logicalPose());
            Sable.LOGGER.info("SABLE_M20_CRUSH_CONTROLLER phase=TICK_PROCESSING"
                            + " subLevel={} controllerRaw={} entityId={} entityType={} entityLevel={}"
                            + " visibleEntityPos={} rawEntityAabb={} hiddenPlotPoseTranslation=false",
                    this.sable$parentSublevel.getUniqueId(),
                    this.worldPosition,
                    this.processingEntity.getId(),
                    this.processingEntity.getType(),
                    this.processingEntity.level().getClass().getName(),
                    this.processingEntity.position(),
                    rawAabb.toMojang());
        }
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;",
            remap = true))
    public AABB sable$pushEntityLocalAABB(final Entity instance, final Operation<AABB> original) {
        final AABB boundingBox = original.call(instance);
        if (this.sable$parentSublevel != null) {
            final BoundingBox3d transformed = new BoundingBox3d(boundingBox);
            transformed.transformInverse(this.sable$parentSublevel.logicalPose());
            return transformed.toMojang();
        }

        return boundingBox;
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getX()D",
            remap = true))
    public double sable$pushEntityLocalX(final Entity instance, final Operation<Double> original) {
        double x = original.call(instance);
        if (this.sable$parentSublevel != null) {
            x = this.sable$parentSublevel.logicalPose().transformPositionInverse(instance.position()).x;
        }

        return x;
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getY()D",
            remap = true))
    public double sable$pushEntityLocalY(final Entity instance, final Operation<Double> original) {
        double y = original.call(instance);
        if (this.sable$parentSublevel != null) {
            y = this.sable$parentSublevel.logicalPose().transformPositionInverse(instance.position()).y;
        }

        return y;
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getZ()D",
            remap = true))
    public double sable$pushEntityLocalZ(final Entity instance, final Operation<Double> original) {
        double z = original.call(instance);
        if (this.sable$parentSublevel != null) {
            z = this.sable$parentSublevel.logicalPose().transformPositionInverse(instance.position()).z;
        }

        return z;
    }
}
