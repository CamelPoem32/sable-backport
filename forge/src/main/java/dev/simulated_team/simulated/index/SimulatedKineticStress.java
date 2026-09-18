package dev.simulated_team.simulated.index;

import com.simibubi.create.api.stress.BlockStressValues;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlockEntity;
import net.minecraft.world.level.block.Block;

public final class SimulatedKineticStress {

    private static boolean registered;

    private SimulatedKineticStress() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        final Block steeringWheel = SimulatedBlocks.STEERING_WHEEL.get();
        BlockStressValues.CAPACITIES.register(steeringWheel,
                () -> SimulatedConfig.STEERING_WHEEL_STRESS_CAPACITY.get());
        BlockStressValues.setGeneratorSpeed((int) SteeringWheelBlockEntity.RPM).accept(steeringWheel);
        registered = true;
        Simulated.LOGGER.info("Registered Steering Wheel stress capacity={} generatedRpm={}",
                SimulatedConfig.STEERING_WHEEL_STRESS_CAPACITY.get(), SteeringWheelBlockEntity.RPM);
    }
}
