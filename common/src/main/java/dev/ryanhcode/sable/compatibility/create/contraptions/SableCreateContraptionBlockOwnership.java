package dev.ryanhcode.sable.compatibility.create.contraptions;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves the source plot positions currently represented by active Create contraptions. */
public final class SableCreateContraptionBlockOwnership {
    private SableCreateContraptionBlockOwnership() {
    }

    public static Map<BlockPos, Ownership> index(final ClientSubLevel subLevel) {
        final Map<BlockPos, Ownership> ownerships = new LinkedHashMap<>();

        for (final Entity candidate : subLevel.getLevel().entitiesForRendering()) {
            if (!(candidate instanceof final AbstractContraptionEntity entity)
                    || !entity.isAliveOrStale()
                    || !SableCreateContraptionContext.isRawEntityInSubLevelPlot(entity, subLevel)) {
                continue;
            }

            final Contraption contraption = entity.getContraption();
            if (contraption == null || contraption.anchor == null || contraption.getBlocks().isEmpty()) {
                continue;
            }

            final List<BlockPos> sourcePositions = new ArrayList<>(contraption.getBlocks().size());
            contraption.getBlocks().values().forEach(info ->
                    sourcePositions.add(info.pos().offset(contraption.anchor).immutable()));
            final List<BlockPos> immutableSourcePositions = List.copyOf(sourcePositions);
            final BlockPos controllerPos = SableCreateContraptionContext.getControllerPos(entity);

            contraption.getBlocks().values().forEach(info -> {
                final BlockPos sourcePlotPos = info.pos().offset(contraption.anchor).immutable();
                ownerships.putIfAbsent(sourcePlotPos, new Ownership(
                        entity.getId(), controllerPos, contraption.anchor.immutable(), info.pos().immutable(),
                        info.state(), immutableSourcePositions));
            });
        }

        return Collections.unmodifiableMap(ownerships);
    }

    public record Ownership(int entityId, BlockPos controllerPos, BlockPos contraptionAnchor,
                            BlockPos capturedLocalPos, BlockState capturedState,
                            List<BlockPos> capturedSourcePlotPositions) {
    }
}
