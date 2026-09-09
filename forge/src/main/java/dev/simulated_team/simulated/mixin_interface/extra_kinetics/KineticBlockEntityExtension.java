package dev.simulated_team.simulated.mixin_interface.extra_kinetics;

public interface KineticBlockEntityExtension {
    void simulated$setConnectedToExtraKinetics(boolean connected);

    boolean simulated$getConnectedToExtraKinetics();

    void simulated$setValidationCountdown(int countdown);
}
