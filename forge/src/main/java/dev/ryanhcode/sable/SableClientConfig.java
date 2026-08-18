package dev.ryanhcode.sable;

import dev.ryanhcode.sable.forge.config.ForgeConfigValue;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.ApiStatus;

import java.util.Arrays;

public final class SableClientConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigValue.BooleanValue ATTEMPT_UDP_NETWORKING;
    public static final ForgeConfigValue.DoubleValue INTERPOLATION_DELAY;
    public static final ForgeConfigValue.EnumValue<SubLevelRenderer.SelectedRenderer> SELECTED_RENDERER;
    public static final ForgeConfigValue.DoubleValue ZOOM_SENSITIVITY;

    static {
        final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        INTERPOLATION_DELAY = new ForgeConfigValue.DoubleValue(builder
                .comment("The distance back in game ticks used for snapshot interpolation")
                .defineInRange("sub_level_snapshot_interpolation_delay_ticks", 1.5, 0.0, 100.0));
        SELECTED_RENDERER = new ForgeConfigValue.EnumValue<>(builder
                .comment("The renderer to use for sub-levels")
                .defineEnum("sub_level_renderer", SubLevelRenderer.DEFAULT,
                        Arrays.stream(SubLevelRenderer.SelectedRenderer.values())
                                .filter(SubLevelRenderer.SelectedRenderer::isSupported)
                                .toArray(SubLevelRenderer.SelectedRenderer[]::new)));
        ZOOM_SENSITIVITY = new ForgeConfigValue.DoubleValue(builder
                .comment("The zoom sensitivity for sub-level camera types")
                .defineInRange("sub_level_zoom_sensitivity", 0.2, 0.0, 100.0));
        ATTEMPT_UDP_NETWORKING = new ForgeConfigValue.BooleanValue(builder
                .comment("If Sable should attempt to establish a UDP connection with the server")
                .define("attempt_udp_networking", true));

        SPEC = builder.build();
    }

    private SableClientConfig() {
    }

    @ApiStatus.Internal
    public static void onUpdate(final boolean notify) {
        Minecraft.getInstance().execute(() -> SubLevelRenderer.setImpl(SELECTED_RENDERER.get()));
    }
}
