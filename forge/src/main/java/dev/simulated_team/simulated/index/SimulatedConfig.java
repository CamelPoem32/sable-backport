package dev.simulated_team.simulated.index;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SimulatedConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLE_M21_BOOTSTRAP_FIXTURE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_M22_BASIC_ASSEMBLY;
    public static final ForgeConfigSpec.IntValue M22_MAX_BLOCKS_MOVED;
    public static final ForgeConfigSpec.DoubleValue M22_DISASSEMBLY_MAX_VELOCITY;
    public static final ForgeConfigSpec.DoubleValue M22_DISASSEMBLY_MAX_ANGULAR_VELOCITY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_M23_SPRING_CONSTRAINTS;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.BooleanValue EXPECT_STATIC_CLIENT_BOOTSTRAP;

    private static boolean initialized;

    static {
        ForgeConfigSpec.Builder common = new ForgeConfigSpec.Builder();
        common.push("m21");
        ENABLE_M21_BOOTSTRAP_FIXTURE = common.comment("Allows the /sable m21 fixture bootstrap gallery.")
                .define("enableBootstrapFixture", true);
        common.pop();
        common.push("m22");
        ENABLE_M22_BASIC_ASSEMBLY = common.comment("Allows the M22 Physics Assembler assembly/disassembly lifecycle.")
                .define("enableBasicAssemblyLifecycle", true);
        M22_MAX_BLOCKS_MOVED = common.comment("Maximum block count for the M22 basic assembly lifecycle.")
                .defineInRange("maxBlocksMoved", 2048, 1, 32768);
        M22_DISASSEMBLY_MAX_VELOCITY = common.comment("Maximum linear velocity allowed for guarded M22 disassembly.")
                .defineInRange("disassemblyMaxVelocity", 1.0D, 0.0D, 128.0D);
        M22_DISASSEMBLY_MAX_ANGULAR_VELOCITY = common.comment("Maximum angular velocity allowed for guarded M22 disassembly.")
                .defineInRange("disassemblyMaxAngularVelocity", 0.25D, 0.0D, 128.0D);
        common.pop();
        common.push("m23");
        ENABLE_M23_SPRING_CONSTRAINTS = common.comment("Allows the M23 Simulated Spring physical constraint canary.")
                .define("enableSpringConstraints", true);
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
