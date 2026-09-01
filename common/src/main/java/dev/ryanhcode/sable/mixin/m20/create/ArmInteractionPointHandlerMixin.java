package dev.ryanhcode.sable.mixin.m20.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Converts Mechanical Arm selection outlines from Sable storage coordinates into visible world space. */
@Mixin(targets = "com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointHandler", remap = false)
public class ArmInteractionPointHandlerMixin {
    private static final Set<String> LOGGED_ARM_SELECTION = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @WrapOperation(
            method = "drawOutlines(Ljava/util/Collection;)V",
            at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/outliner/Outliner;showAABB(Ljava/lang/Object;Lnet/minecraft/world/phys/AABB;)Lnet/createmod/catnip/outliner/Outline$OutlineParams;"))
    private static Outline.OutlineParams sable$showArmSelectionInVisibleSpace(final Outliner outliner,
                                                                              final Object key,
                                                                              final AABB rawAabb,
                                                                              final Operation<Outline.OutlineParams> original) {
        final Vec3 rawCenter = rawAabb.getCenter();
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(rawCenter);
        if (subLevel == null) {
            return original.call(outliner, key, rawAabb);
        }

        final AABB visibleAabb = new BoundingBox3d(rawAabb).transform(subLevel.renderPose(), new BoundingBox3d())
                .toMojang();
        final String logKey = subLevel.getUniqueId() + ":" + key + ":" + rawAabb;
        if (LOGGED_ARM_SELECTION.add(logKey)) {
            Sable.LOGGER.info("SABLE_M20_ARM_SELECTION subLevel={} keyClass={} rawAABB={} visibleAABB={} "
                            + "outlineSubmitted=true hiddenPlotOutline=false normalWorldPassThrough=false",
                    subLevel.getUniqueId(), key == null ? "null" : key.getClass().getName(), rawAabb, visibleAabb);
        }
        return original.call(outliner, key, visibleAabb);
    }
}
