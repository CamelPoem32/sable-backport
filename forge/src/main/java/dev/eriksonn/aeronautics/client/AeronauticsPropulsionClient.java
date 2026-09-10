package dev.eriksonn.aeronautics.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.content.propulsion.WoodenPropellerRenderer;
import dev.eriksonn.aeronautics.index.AeroPropulsionRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Aeronautics.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AeronauticsPropulsionClient {
    public static final ResourceLocation WOODEN_PROPELLER_MODEL =
            Aeronautics.path("block/wooden_propeller/propeller");
    public static final ResourceLocation WOODEN_PROPELLER_REVERSED_MODEL =
            Aeronautics.path("block/wooden_propeller/propeller_reversed");
    public static final PartialModel WOODEN_PROPELLER = PartialModel.of(WOODEN_PROPELLER_MODEL);
    public static final PartialModel WOODEN_PROPELLER_REVERSED =
            PartialModel.of(WOODEN_PROPELLER_REVERSED_MODEL);

    private AeronauticsPropulsionClient() {
    }

    @SubscribeEvent
    public static void registerAdditionalModels(final ModelEvent.RegisterAdditional event) {
        event.register(WOODEN_PROPELLER_MODEL);
        event.register(WOODEN_PROPELLER_REVERSED_MODEL);
    }

    @SubscribeEvent
    public static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AeroPropulsionRegistries.WOODEN_PROPELLER_BE.get(),
                WoodenPropellerRenderer::new);
    }
}
