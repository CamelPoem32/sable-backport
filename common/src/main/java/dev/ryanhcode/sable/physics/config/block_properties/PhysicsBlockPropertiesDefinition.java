package dev.ryanhcode.sable.physics.config.block_properties;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The definition of the physics block properties for a block
 */
public record PhysicsBlockPropertiesDefinition(ExtraCodecs.TagOrElementLocation selector,
                                               int priority,
                                               Map<ResourceLocation, Object> properties,
                                               Optional<Map<BlockStateConditionSet, Map<ResourceLocation, Object>>> overrides) {

    private static final Codec<Map<ResourceLocation, Dynamic<?>>> DYNAMIC_PROPERTIES_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.PASSTHROUGH);

    public static final Codec<Map<ResourceLocation, Object>> PROPERTIES_CODEC =
            DYNAMIC_PROPERTIES_CODEC.flatXmap(
                    PhysicsBlockPropertiesDefinition::decodeProperties,
                    PhysicsBlockPropertiesDefinition::encodeProperties
            );

    public static final Codec<PhysicsBlockPropertiesDefinition> CODEC =
            RecordCodecBuilder.create(i -> i.group(
                    ExtraCodecs.TAG_OR_ELEMENT_ID.fieldOf("selector").forGetter(PhysicsBlockPropertiesDefinition::selector),
                    Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("priority", 1000).forGetter(PhysicsBlockPropertiesDefinition::priority),
                    PROPERTIES_CODEC.fieldOf("properties").forGetter(PhysicsBlockPropertiesDefinition::properties),
                    Codec.unboundedMap(BlockStateConditionSet.CODEC, PROPERTIES_CODEC)
                            .optionalFieldOf("overrides").forGetter(PhysicsBlockPropertiesDefinition::overrides)
            ).apply(i, PhysicsBlockPropertiesDefinition::new));

    @Override
    public int hashCode() {
        return Objects.hash(this.selector);
    }

    private static DataResult<Map<ResourceLocation, Object>> decodeProperties(
            final Map<ResourceLocation, Dynamic<?>> encoded) {
        DataResult<Map<ResourceLocation, Object>> result = DataResult.success(new LinkedHashMap<>());
        for (final Map.Entry<ResourceLocation, Dynamic<?>> entry : encoded.entrySet()) {
            result = result.flatMap(properties -> PhysicsBlockPropertyTypes.getPropertyCodec(entry.getKey())
                    .parse(entry.getValue())
                    .map(value -> {
                        properties.put(entry.getKey(), value);
                        return properties;
                    }));
        }
        return result;
    }

    private static DataResult<Map<ResourceLocation, Dynamic<?>>> encodeProperties(
            final Map<ResourceLocation, Object> properties) {
        DataResult<Map<ResourceLocation, Dynamic<?>>> result = DataResult.success(new LinkedHashMap<>());
        for (final Map.Entry<ResourceLocation, Object> entry : properties.entrySet()) {
            result = result.flatMap(encoded -> PhysicsBlockPropertyTypes.getPropertyCodec(entry.getKey())
                    .encodeStart(JsonOps.INSTANCE, entry.getValue())
                    .map(value -> {
                        encoded.put(entry.getKey(), new Dynamic<>(JsonOps.INSTANCE, value));
                        return encoded;
                    }));
        }
        return result;
    }

    @Override
    public String toString() {
        return "PhysicsBlockPropertiesDefinition{selector=%s, properties=%s}".formatted(this.selector, this.properties);
    }
}
