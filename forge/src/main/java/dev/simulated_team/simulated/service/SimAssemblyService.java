package dev.simulated_team.simulated.service;

import net.minecraft.world.level.block.state.BlockState;

public final class SimAssemblyService {

    private SimAssemblyService() {
    }

    public static boolean canStickTo(final BlockState stateA, final BlockState stateB) {
        return stateA.canStickTo(stateB);
    }
}
