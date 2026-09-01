package dev.ryanhcode.sable.mixin.m20.create;

import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Projects Create item-cull positions out of Sable storage space before Create's normal frustum test. */
@Mixin(targets = "com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer", remap = false)
public class SafeBlockEntityRendererMixin {
    private static final Set<String> LOGGED_ITEM_CULL = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @ModifyVariable(
            method = "shouldCullItem(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/Level;)Z",
            at = @At("HEAD"),
            argsOnly = true)
    private Vec3 sable$projectItemCullPosition(final Vec3 itemPos) {
        final Vec3 projected = Sable.HELPER.projectOutOfSubLevel(Minecraft.getInstance().level, itemPos);
        if (projected != itemPos) {
            final String key = itemPos.toString();
            if (LOGGED_ITEM_CULL.add(key)) {
                Sable.LOGGER.info("SABLE_M20_ITEM_CULL rawItemPosition={} visibleItemPosition={} "
                                + "cullingInput=visible hiddenPlotPoseTranslation=false "
                                + "rendererPath=Create_SafeBlockEntityRenderer_shouldCullItem",
                        itemPos, projected);
            }
        }
        return projected;
    }
}
