package dev.ryanhcode.sable.physics.config.block_properties;

import com.mojang.serialization.Codec;
import foundry.veil.platform.registry.RegistrationProvider;
import foundry.veil.platform.registry.RegistryObject;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * All default physics block properties
 */
public class PhysicsBlockPropertyTypes {
    private static final String MOD_ID = "sable";
    public static final ResourceKey<Registry<PhysicsBlockPropertyType<?>>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(sablePath("physics_block_properties"));
    private static final RegistrationProvider<PhysicsBlockPropertyType<?>> VANILLA_PROVIDER;
    private static final Registry<PhysicsBlockPropertyType<?>> REGISTRY;
    private static final Set<ResourceLocation> REGISTERED_IDS = new LinkedHashSet<>();

    static {
        VANILLA_PROVIDER = RegistrationProvider.get(REGISTRY_KEY, MOD_ID);
        REGISTRY = VANILLA_PROVIDER.asVanillaRegistry();
    }

    /**
     * The mass of a block in [kpg]
     */
    public static final RegistryObject<PhysicsBlockPropertyType<Double>> MASS = register(sablePath("mass"), Codec.DOUBLE, 1.0);
    /**
     * The optional 3d vector representing the principal inertia of the block
     */
    public static final RegistryObject<PhysicsBlockPropertyType<Vec3>> INERTIA = register(sablePath("inertia"), Vec3.CODEC, null);
    /**
     * The volume of a block, used for buoyancy
     */
    public static final RegistryObject<PhysicsBlockPropertyType<Double>> VOLUME = register(sablePath("volume"), Codec.DOUBLE, 1.0);
    /**
     * The restitution of a block
     */
    public static final RegistryObject<PhysicsBlockPropertyType<Double>> RESTITUTION = register(sablePath("restitution"), Codec.DOUBLE, 0.0);
    /**
     * The friction multiplier of a block
     */
    public static final RegistryObject<PhysicsBlockPropertyType<Double>> FRICTION = register(sablePath("friction"), Codec.DOUBLE, 1.0);
    /**
     * If this block is fragile
     */
    public static final RegistryObject<PhysicsBlockPropertyType<Boolean>> FRAGILE = register(sablePath("fragile"), Codec.BOOL, false);
    /**
     * The floating material {@link ResourceLocation} this block should have
     */
    public static final RegistryObject<PhysicsBlockPropertyType<ResourceLocation>> FLOATING_MATERIAL = register(sablePath("floating_material"), ResourceLocation.CODEC, null);
    /**
     * The scale / multiplier of the effects caused by the floating material for this block
     */
    public static final RegistryObject<PhysicsBlockPropertyType<Double>> FLOATING_SCALE = register(sablePath("floating_scale"), Codec.DOUBLE, 1.0);

    private static ResourceLocation sablePath(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void register() {
        // no-op
    }
    /**
     * Registers a physics block property.
     *
     * @param id    The id of the property
     * @param codec The codec defining serialization/deserialization for the property
     * @return The registered property
     */
    private static <T> RegistryObject<PhysicsBlockPropertyType<T>> register(final ResourceLocation id, final Codec<T> codec, final T defaultValue) {
        if (!REGISTERED_IDS.add(id)) {
            throw new IllegalArgumentException("Duplicate physics block property: %s".formatted(id));
        }

        final int propertyId = REGISTERED_IDS.size() - 1;
        return VANILLA_PROVIDER.register(id, () -> new PhysicsBlockPropertyType<>(propertyId, codec, defaultValue));
    }

    /**
     * The count of registered properties
     */
    public static int count() {
        return REGISTERED_IDS.size();
    }

    /**
     * Gets the codec for a property.
     *
     * @param id The id of the property
     * @return The codec for the property
     */
    public static Codec<Object> getPropertyCodec(final ResourceLocation id) {
        final PhysicsBlockPropertyType<?> property = REGISTRY.get(id);

        if (property != null) {
            //noinspection unchecked
            return (Codec<Object>) property.codec;
        }

        throw new IllegalArgumentException("Unknown physics block property: %s".formatted(id));
    }

    /**
     * Gets a property type
     *
     * @param id The id of the property
     * @return The property type
     */
    public static PhysicsBlockPropertyType<?> getPropertyType(final ResourceLocation id) {
        final PhysicsBlockPropertyType<?> property = REGISTRY.get(id);

        if (property != null) {
            return property;
        }

        throw new IllegalArgumentException("Unknown physics block property: %s".formatted(id));
    }

    public record PhysicsBlockPropertyType<T>(int id, Codec<T> codec, T defaultValue) {
    }

}
