package dev.ryanhcode.sable.mixin.entity.entities_in_blocks;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SubLevelBlockStateLookup;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
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

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Unique
    private static final ResourceLocation SABLE$CRUSHING_WHEEL_CONTROLLER =
            new ResourceLocation("create", "crushing_wheel_controller");

    @Unique
    private static final Set<String> SABLE$LOGGED_CRUSHING_BRIDGE =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Shadow
    public abstract boolean isAlive();

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    private Level level;

    @Shadow
    protected abstract void onInsideBlock(BlockState blockState);

    @Inject(method = "checkInsideBlocks", at = @At("TAIL"))
    protected void checkInsideBlocks(final CallbackInfo ci) {
        final AABB bounds = this.getBoundingBox();

        final BoundingBox3d localBounds = new BoundingBox3d(bounds);
        for (final SubLevel intersecting : Sable.HELPER.getAllIntersecting(this.level, new BoundingBox3d(bounds))) {
            localBounds.set(bounds);
            localBounds.transformInverse(intersecting.logicalPose(), localBounds);
            final BlockPos minPos = BlockPos.containing(localBounds.minX + 1.0E-7, localBounds.minY + 1.0E-7, localBounds.minZ + 1.0E-7);
            final BlockPos maxPos = BlockPos.containing(localBounds.maxX - 1.0E-7, localBounds.maxY - 1.0E-7, localBounds.maxZ - 1.0E-7);

            final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (int i = minPos.getX(); i <= maxPos.getX(); i++) {
                for (int j = minPos.getY(); j <= maxPos.getY(); j++) {
                    for (int k = minPos.getZ(); k <= maxPos.getZ(); k++) {
                        if (!this.isAlive()) {
                            return;
                        }

                        mutableBlockPos.set(i, j, k);
                        final BlockState blockState = SubLevelBlockStateLookup.getBlockStateOrAir(intersecting, mutableBlockPos);
                        if (blockState.isAir()) {
                            continue;
                        }

                        try {
                            this.sable$logCrushingEntityBridge(intersecting, mutableBlockPos, blockState,
                                    (Entity) (Object) this);
                            blockState.entityInside(this.level, mutableBlockPos, (Entity) (Object) this);
                            this.onInsideBlock(blockState);
                        } catch (final Throwable var12) {
                            final CrashReport crashReport = CrashReport.forThrowable(var12, "Colliding entity with block");
                            final CrashReportCategory crashReportCategory = crashReport.addCategory("Block being collided with");
                            CrashReportCategory.populateBlockDetails(crashReportCategory, this.level, mutableBlockPos, blockState);
                            throw new ReportedException(crashReport);
                        }
                    }
                }
            }
        }
    }

    @Unique
    private void sable$logCrushingEntityBridge(final SubLevel subLevel,
                                               final BlockPos rawPos,
                                               final BlockState blockState,
                                               final Entity entity) {
        if (!SABLE$CRUSHING_WHEEL_CONTROLLER.equals(BuiltInRegistries.BLOCK.getKey(blockState.getBlock()))) {
            return;
        }

        final String key = entity.getId() + ":" + rawPos.asLong();
        if (!SABLE$LOGGED_CRUSHING_BRIDGE.add(key)) {
            return;
        }

        final BlockPos localPos = rawPos.subtract(subLevel.getPlot().getCenterBlock());
        Sable.LOGGER.info("SABLE_M20_CRUSH_ENTITY_BRIDGE entityId={} entityType={} entityLevel={}"
                        + " visibleEntityPos={} sableId={} controllerLocalPos={} controllerRawPos={}"
                        + " vanillaEntityInsideReached=false sableEntityInsideReached=true",
                entity.getId(),
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                this.level.getClass().getName(),
                entity.position(),
                subLevel.getUniqueId(),
                localPos,
                rawPos);
    }
}
