package dev.simulated_team.simulated.content.blocks.steering_wheel;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.index.SimulatedBlockEntityTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class SteeringWheelClient {

    public static final ResourceLocation WHEEL_LOCATION = Simulated.path("block/steering_wheel/wheel");
    public static final PartialModel WHEEL = PartialModel.of(WHEEL_LOCATION);

    private SteeringWheelClient() {
    }

    public static void register(final IEventBus modBus) {
        modBus.<ModelEvent.RegisterAdditional>addListener(event -> event.register(WHEEL_LOCATION));
        modBus.<EntityRenderersEvent.RegisterRenderers>addListener(event ->
                event.registerBlockEntityRenderer(SimulatedBlockEntityTypes.STEERING_WHEEL.get(),
                        SteeringWheelRenderer::new));
        SteeringWheelClientControl.register();
    }
}
