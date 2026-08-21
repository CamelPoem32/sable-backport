package dev.ryanhcode.sable.index;

import com.google.common.collect.ImmutableMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import java.util.Map;
import java.util.Objects;

public final class SableAttributes {

    public static final String PUNCH_STRENGTH_NAME = "player.sub_level_punch_strength";
    public static final Attribute PUNCH_STRENGTH = new RangedAttribute(
            "attribute.name." + PUNCH_STRENGTH_NAME, 1.0, -100.0, 100.0).setSyncable(true);

    public static final String PUNCH_COOLDOWN_NAME = "player.sub_level_punch_cooldown";
    public static final Attribute PUNCH_COOLDOWN = new RangedAttribute(
            "attribute.name." + PUNCH_COOLDOWN_NAME, 0.0, 0.0, 10.0).setSyncable(true);

    private SableAttributes() {
    }

    public static void register() {
        final AttributeSupplier supplier = DefaultAttributes.getSupplier(EntityType.PLAYER);
        final Map<Attribute, AttributeInstance> additionalInstances = AttributeSupplier.builder()
                .add(PUNCH_STRENGTH)
                .add(PUNCH_COOLDOWN)
                .build()
                .instances;

        supplier.instances = ImmutableMap.<Attribute, AttributeInstance>builder()
                .putAll(supplier.instances)
                .putAll(additionalInstances)
                .buildKeepingLast();
    }

    public static int getPushCooldownTicks(final LivingEntity entity) {
        return Mth.ceil(Objects.requireNonNull(entity.getAttribute(PUNCH_COOLDOWN)).getValue() * 20.0);
    }
}
