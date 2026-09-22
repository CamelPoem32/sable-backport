package dev.ryanhcode.sable.compatibility.create.block_breakers;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** State transition used when a moving Sable body changes an actor's physical parent target. */
public final class ExternalBlockBreakingTargetState {
    private ExternalBlockBreakingTargetState() {
    }

    public static Transition transition(@Nullable final BlockPos current, @Nullable final BlockPos resolved) {
        if (Objects.equals(current, resolved)) {
            return Transition.UNCHANGED;
        }
        if (current == null) {
            return Transition.START;
        }
        if (resolved == null) {
            return Transition.STOP;
        }
        return Transition.RETARGET;
    }

    public enum Transition {
        UNCHANGED,
        START,
        STOP,
        RETARGET
    }
}
