package dev.ryanhcode.sable.registry;

import net.minecraft.resources.ResourceLocation;

/**
 * Minimal local holder for Sable's retained Forge 1.20.1 in-memory registries.
 *
 * @param id    the Sable registry id
 * @param value the registered value
 * @param <T>   the value type
 */
public record SableRegistryObject<T>(ResourceLocation id, T value) {

    public T get() {
        return this.value;
    }
}
