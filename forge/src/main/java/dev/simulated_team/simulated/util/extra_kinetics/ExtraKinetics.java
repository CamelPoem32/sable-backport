package dev.simulated_team.simulated.util.extra_kinetics;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import org.jetbrains.annotations.Nullable;

public interface ExtraKinetics {
    @Nullable KineticBlockEntity getExtraKinetics();

    boolean shouldConnectExtraKinetics();

    default String getExtraKineticsSaveName() {
        return "DEFAULT";
    }

    @FunctionalInterface
    interface ExtraKineticsBlock {
        IRotate getExtraKineticsRotationConfiguration();
    }

    interface ExtraKineticsBlockEntity {
        KineticBlockEntity getParentBlockEntity();
    }
}
