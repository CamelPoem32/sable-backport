package dev.ryanhcode.sable.api.physics.force;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.registry.SableRegistryObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All default force groups
 */
public class ForceGroups {
    private static final Map<ResourceLocation, ForceGroup> VALUES = new LinkedHashMap<>();
    public static final Map<ResourceLocation, ForceGroup> REGISTRY = Collections.unmodifiableMap(VALUES);

    public static final SableRegistryObject<ForceGroup> GRAVITY = register(Sable.sablePath("gravity"), new ForceGroup(Component.translatable("force_group.sable.gravity"), null, 0x216e55, false));
    public static final SableRegistryObject<ForceGroup> DRAG = register(Sable.sablePath("drag"), new ForceGroup(Component.translatable("force_group.sable.drag"), null, 0x834f31, false));
    public static final SableRegistryObject<ForceGroup> LEVITATION = register(Sable.sablePath("levitation"), new ForceGroup(Component.translatable("force_group.sable.levitation"), null, 0x734480, true));
    public static final SableRegistryObject<ForceGroup> BALLOON_LIFT = register(Sable.sablePath("balloon_lift"), new ForceGroup(Component.translatable("force_group.sable.balloon_lift"), null, 0xd2643e, true));
    public static final SableRegistryObject<ForceGroup> PROPULSION = register(Sable.sablePath("propulsion"), new ForceGroup(Component.translatable("force_group.sable.propulsion"), null, 0x5a7c9f, true));
    public static final SableRegistryObject<ForceGroup> LIFT = register(Sable.sablePath("lift"), new ForceGroup(Component.translatable("force_group.sable.lift"), null, 0x8cb6c6, true));
    public static final SableRegistryObject<ForceGroup> MAGNETIC_FORCE = register(Sable.sablePath("magnetic_force"), new ForceGroup(Component.translatable("force_group.sable.magnetic_force"), null, 0xe05343, false));

    public static void register() {
        // no-op
    }

    private static SableRegistryObject<ForceGroup> register(final ResourceLocation id, final ForceGroup value) {
        if (VALUES.putIfAbsent(id, value) != null) {
            throw new IllegalArgumentException("Duplicate force group: %s".formatted(id));
        }
        return new SableRegistryObject<>(id, value);
    }

    /**
     *
     * The count of registered force groups
     */
    public static int count() {
        return REGISTRY.size();
    }
}
