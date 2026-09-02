package dev.simulated_team.simulated.index;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SimulatedConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLE_M21_BOOTSTRAP_FIXTURE;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.BooleanValue EXPECT_STATIC_CLIENT_BOOTSTRAP;

    private static boolean initialized;

    static {
        ForgeConfigSpec.Builder common = new ForgeConfigSpec.Builder();
        common.push("m21");
        ENABLE_M21_BOOTSTRAP_FIXTURE = common.comment("Allows the /sable m21 fixture bootstrap gallery.")
                .define("enableBootstrapFixture", true);
        common.pop();
        COMMON_SPEC = common.build();

        ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();
        client.push("m21");
        EXPECT_STATIC_CLIENT_BOOTSTRAP = client.comment("Documents that only static renderer bootstrap is expected in M21.")
                .define("expectStaticClientBootstrap", true);
        client.pop();
        CLIENT_SPEC = client.build();
    }

    private SimulatedConfig() {
    }

    public static void init() {
        initialized = true;
    }

    public static boolean isReady() {
        return initialized;
    }
}
