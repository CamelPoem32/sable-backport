package dev.ryanhcode.sable.compatibility.create.deployer;

import dev.ryanhcode.sable.compatibility.create.block_breakers.CreateActorTargetGeometry;
import dev.ryanhcode.sable.compatibility.create.block_breakers.SubLevelBlockBreakingUtility;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;

/** Resolves moving Deployer interactions exclusively against the physical parent world. */
public final class SubLevelDeployerInteractionUtility {
    private SubLevelDeployerInteractionUtility() {
    }

    public static SubLevelBlockBreakingUtility.TargetResolution resolveExternalTarget(
            final SubLevel owner,
            final Level parentLevel,
            final CreateActorTargetGeometry.ActorSpace actorSpace) {
        return SubLevelBlockBreakingUtility.findExternalPointTarget(owner, parentLevel, actorSpace);
    }
}
