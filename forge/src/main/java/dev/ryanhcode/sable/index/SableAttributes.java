package dev.ryanhcode.sable.index;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;

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

    public static void register(final EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, PUNCH_STRENGTH, 1.0);
        event.add(EntityType.PLAYER, PUNCH_COOLDOWN, 0.0);
    }

    public static int getPushCooldownTicks(final LivingEntity entity) {
        return Mth.ceil(Objects.requireNonNull(entity.getAttribute(PUNCH_COOLDOWN)).getValue() * 20.0);
    }
}
