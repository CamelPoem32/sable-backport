package dev.ryanhcode.sable.compatibility.create.goggles;

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/** Overlay eligibility only. Never substitutes a hit or changes the interaction target. */
public final class GoggleOverlayHitPolicy {
    private GoggleOverlayHitPolicy() { }

    public enum Decision {
        NATIVE_PARENT_BLOCK, NO_CLIENT_CONTEXT, NULL_HIT, MISS, ENTITY_NO_BLOCK_CONTEXT,
        NON_BLOCK_HIT, HIDDEN_STORAGE_NO_PHYSICAL_BLOCK_CONTEXT
    }

    public static Decision decide(@Nullable final HitResult hit, final boolean clientReady, final boolean hiddenBlock) {
        if (!clientReady) return Decision.NO_CLIENT_CONTEXT;
        if (hit == null) return Decision.NULL_HIT;
        if (hit.getType() == HitResult.Type.MISS) return Decision.MISS;
        if (hit.getType() == HitResult.Type.ENTITY) return Decision.ENTITY_NO_BLOCK_CONTEXT;
        if (!(hit instanceof BlockHitResult)) return Decision.NON_BLOCK_HIT;
        return hiddenBlock ? Decision.HIDDEN_STORAGE_NO_PHYSICAL_BLOCK_CONTEXT : Decision.NATIVE_PARENT_BLOCK;
    }
}
