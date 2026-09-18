package dev.ryanhcode.sable.mixin.compatibility.create.contraptions;

import com.simibubi.create.content.contraptions.IControlContraption.RotationMode;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** M28 bearing placement diagnostics and steering-only sequenced-travel refresh. */
@Mixin(value = MechanicalBearingBlockEntity.class, remap = false)
public interface MechanicalBearingBlockEntityAccessor {
    @Accessor("movementMode")
    ScrollOptionBehaviour<RotationMode> sable$getMovementMode();

    @Accessor("sequencedAngleLimit")
    double sable$getSequencedAngleLimit();

    @Accessor("sequencedAngleLimit")
    void sable$setSequencedAngleLimit(double remainingDegrees);

    @Accessor("movedContraption")
    void sable$setMovedContraption(ControlledContraptionEntity contraption);

    @Accessor("running")
    void sable$setRunning(boolean running);

    @Accessor("assembleNextTick")
    void sable$setAssembleNextTick(boolean assembleNextTick);
}
