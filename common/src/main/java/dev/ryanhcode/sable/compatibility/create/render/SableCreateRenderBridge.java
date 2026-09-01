package dev.ryanhcode.sable.compatibility.create.render;

import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SableCreateRenderBridge {
    private static final Set<String> LOGGED_BER_FALLBACK = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<String> LOGGED_DISTANCE_BRIDGE = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private SableCreateRenderBridge() {
    }

    public static boolean visualizationSupportedForSableBer(final LevelAccessor level,
                                                            final boolean originalVisualizationSupported,
                                                            final BlockEntity blockEntity,
                                                            final String renderer,
                                                            final String renderPath) {
        final boolean sableSubLevel = Sable.HELPER.getContainingClient(blockEntity) != null;
        final boolean returnedVisualizationSupported = sableSubLevel ? false : originalVisualizationSupported;
        if (blockEntity != null) {
            final String key = renderer + ":" + blockEntity.getBlockPos().asLong() + ":" + sableSubLevel
                    + ":" + originalVisualizationSupported + ":" + returnedVisualizationSupported;
            if (LOGGED_BER_FALLBACK.add(key)) {
                Sable.LOGGER.info("SABLE_M20_SPECIALIZED_BER renderer={} blockEntityClass={} blockId={} pos={} "
                                + "sableSubLevel={} originalVisualizationSupported={} "
                                + "returnedVisualizationSupported={} hiddenPlotPoseTranslation=false "
                                + "renderPath={}",
                        renderer,
                        blockEntity.getClass().getName(),
                        BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()),
                        blockEntity.getBlockPos(),
                        sableSubLevel,
                        originalVisualizationSupported,
                        returnedVisualizationSupported,
                        renderPath);
            }
        }
        return returnedVisualizationSupported;
    }

    public static double distanceToSqrWithSubLevels(final String owner, final Vec3 first, final Vec3 second) {
        final double distance = Sable.HELPER.distanceSquaredWithSubLevels(Minecraft.getInstance().level, first, second);
        final String key = owner + ":" + first + ":" + second;
        if (LOGGED_DISTANCE_BRIDGE.add(key)) {
            Sable.LOGGER.info("SABLE_M20_FILTER_RENDER owner={} rawA={} rawB={} "
                            + "distancePath=Sable_distanceSquaredWithSubLevels distanceSqr={} "
                            + "hiddenPlotPoseTranslation=false",
                    owner, first, second, distance);
        }
        return distance;
    }
}
