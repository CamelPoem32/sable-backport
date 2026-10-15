package dev.ryanhcode.sable.command;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.ryanhcode.sable.compatibility.create.residuals.SableBlazeGaze;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/** Reads a native client gaze sample for the requested vehicle only; cannot drive animation or PASS a server gate itself. */
final class M43ResidualFixtureClient {
    private static M43ResidualFixtureNetwork.Request pending;
    private static long requestedAt;
    private static int ticks;
    private M43ResidualFixtureClient() { }
    static void install() { MinecraftForge.EVENT_BUS.addListener(M43ResidualFixtureClient::tick); }
    static void request(final M43ResidualFixtureNetwork.Request request) {
        pending = request; ticks = 0;
        requestedAt = Minecraft.getInstance().level == null ? Long.MAX_VALUE : Minecraft.getInstance().level.getGameTime();
    }
    private static void tick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending == null) return;
        final var world = Minecraft.getInstance().level;
        if (world == null || ++ticks > 2400) { pending = null; return; }
        if (ticks % 10 != 0) return;
        for (final Entity entity : world.entitiesForRendering()) {
            if (!(entity instanceof AbstractContraptionEntity vehicle) || !vehicle.getUUID().equals(pending.vehicle()) || vehicle.getContraption() == null) continue;
            for (final var pair : vehicle.getContraption().getActors()) {
                final var sample = SableBlazeGaze.sample(pair.right);
                if (sample == null || sample.gameTime() <= requestedAt || !sample.owner().equals(pending.owner())
                        || !Float.isFinite(sample.nativeAngle()) || !Double.isFinite(sample.directionDot())) continue;
                M43ResidualFixtureNetwork.CHANNEL.sendToServer(new M43ResidualFixtureNetwork.Report(pending.marker(), pending.nonce(),
                        pending.vehicle(), pending.owner(), sample.visible(), sample.camera(), sample.localGaze(), sample.nativeAngle(), sample.directionDot()));
                // Repeat boundedly: server may still be moving from ARM to OBSERVE when the first sample arrives.
                if (ticks >= 100) pending = null;
                return;
            }
        }
    }
}
