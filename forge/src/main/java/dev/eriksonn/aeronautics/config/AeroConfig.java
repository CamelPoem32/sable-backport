package dev.eriksonn.aeronautics.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class AeroConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLE_M25_LIFT_FIXTURES;
    public static final ForgeConfigSpec.BooleanValue ENABLE_M26_PROPULSION_FIXTURES;
    public static final ForgeConfigSpec.DoubleValue WOODEN_PROPELLER_THRUST;
    public static final ForgeConfigSpec.DoubleValue WOODEN_PROPELLER_AIRFLOW;

    static {
        final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("m25");
        ENABLE_M25_LIFT_FIXTURES = builder
                .comment("Allows the test-only /sable m25 lift fixtures; it does not alter Aeronautics lift physics.")
                .define("enableLiftFixtures", true);
        builder.pop();
        builder.push("physics");
        WOODEN_PROPELLER_THRUST = builder
                .comment("Frozen Aeronautics 1.3.0 Wooden Propeller thrust scaling.")
                .defineInRange("woodenPropellerThrust", 1.0D, 0.0D, Double.MAX_VALUE);
        WOODEN_PROPELLER_AIRFLOW = builder
                .comment("Frozen Aeronautics 1.3.0 Wooden Propeller airflow scaling.")
                .defineInRange("woodenPropellerAirflow", 0.1D, 0.0D, Double.MAX_VALUE);
        builder.pop();
        builder.push("m26");
        ENABLE_M26_PROPULSION_FIXTURES = builder
                .comment("Allows test-only /sable m26 fixtures; it never applies propulsion directly.")
                .define("enablePropulsionFixtures", true);
        builder.pop();
        SPEC = builder.build();
    }

    private AeroConfig() {
    }
}
