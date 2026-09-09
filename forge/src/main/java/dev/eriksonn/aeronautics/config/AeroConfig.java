package dev.eriksonn.aeronautics.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class AeroConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLE_M25_LIFT_FIXTURES;

    static {
        final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("m25");
        ENABLE_M25_LIFT_FIXTURES = builder
                .comment("Allows the test-only /sable m25 lift fixtures; it does not alter Aeronautics lift physics.")
                .define("enableLiftFixtures", true);
        builder.pop();
        SPEC = builder.build();
    }

    private AeroConfig() {
    }
}
