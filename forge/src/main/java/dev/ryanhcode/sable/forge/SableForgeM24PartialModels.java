package dev.ryanhcode.sable.forge;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.IEventBus;

final class SableForgeM24PartialModels {

    static final ResourceLocation TORSION_SPRING_LOCATION =
            new ResourceLocation("simulated", "block/torsion_spring/spring");
    static final PartialModel TORSION_SPRING = PartialModel.of(TORSION_SPRING_LOCATION);

    private static volatile boolean registeredBeforeBake;
    private static volatile boolean bakedModelPresent;
    private static volatile boolean missingModel = true;
    private static volatile ResourceLocation particleSprite;
    private static volatile ResourceLocation spriteAtlas;
    private static volatile boolean missingSprite = true;
    private static volatile int bakeGeneration;

    private SableForgeM24PartialModels() {
    }

    static void register(final IEventBus modBus) {
        modBus.<ModelEvent.RegisterAdditional>addListener(SableForgeM24PartialModels::registerAdditionalModels);
        modBus.<ModelEvent.BakingCompleted>addListener(SableForgeM24PartialModels::bakingCompleted);
    }

    private static void registerAdditionalModels(final ModelEvent.RegisterAdditional event) {
        event.register(TORSION_SPRING_LOCATION);
        registeredBeforeBake = true;
    }

    private static void bakingCompleted(final ModelEvent.BakingCompleted event) {
        final BakedModel bakedModel = event.getModels().get(TORSION_SPRING_LOCATION);
        final BakedModel missing = event.getModelManager().getMissingModel();
        bakedModelPresent = bakedModel != null;
        missingModel = bakedModel == null || bakedModel == missing;

        final TextureAtlasSprite sprite = bakedModel == null ? null : bakedModel.getParticleIcon();
        final TextureAtlasSprite missingModelSprite = missing == null ? null : missing.getParticleIcon();
        particleSprite = sprite == null ? null : sprite.contents().name();
        spriteAtlas = sprite == null ? null : sprite.atlasLocation();
        missingSprite = sprite == null
                || missingModelSprite != null && sprite.contents().name().equals(missingModelSprite.contents().name());
        bakeGeneration++;
    }

    static boolean registeredBeforeBake() {
        return registeredBeforeBake;
    }

    static boolean bakedModelPresent() {
        return bakedModelPresent;
    }

    static boolean missingModel() {
        return missingModel;
    }

    static ResourceLocation particleSprite() {
        return particleSprite;
    }

    static ResourceLocation spriteAtlas() {
        return spriteAtlas;
    }

    static boolean missingSprite() {
        return missingSprite;
    }

    static int bakeGeneration() {
        return bakeGeneration;
    }
}
