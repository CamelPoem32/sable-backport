package dev.ryanhcode.sable.forge;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Traces framebuffer ownership only after a tracked M28 CPU target reaches its draw call. */
public final class SableM28PresentationTrace {
    private static long frame;
    private static boolean targetDrawSeen;
    private static boolean targetDrawInProgress;
    private static final Set<Integer> TARGET_ENTITIES = new LinkedHashSet<>();
    private static final Set<String> LOGGED_SWITCHES = new LinkedHashSet<>();

    private SableM28PresentationTrace() {
    }

    public static void beginFrame(final long currentFrame) {
        frame = currentFrame;
        targetDrawSeen = false;
        targetDrawInProgress = false;
        TARGET_ENTITIES.clear();
        LOGGED_SWITCHES.clear();
    }

    public static void targetDraw(final int entityId, final UUID subLevelId,
                                  final float bearingAngle, final int renderedBufferIdentity) {
        if (!enabled()) {
            return;
        }
        targetDrawSeen = true;
        targetDrawInProgress = true;
        TARGET_ENTITIES.add(entityId);
        log("TARGET_DRAW", "entityId=" + entityId + " subLevel=" + subLevelId
                + " bearingAngle=" + bearingAngle + " renderedBufferIdentity=" + renderedBufferIdentity);
    }

    public static void targetDrawComplete() {
        if (enabled() && targetDrawInProgress) {
            log("TARGET_DRAW_COMPLETE", "targetEntities=" + TARGET_ENTITIES);
            targetDrawInProgress = false;
        }
    }

    public static void renderTargetEvent(final String event, final RenderTarget target) {
        if (!enabled() || !targetDrawSeen || !RenderSystem.isOnRenderThread()) {
            return;
        }
        final String key = event + ":" + System.identityHashCode(target) + ":" + currentFramebuffer();
        if (!LOGGED_SWITCHES.add(key)) {
            return;
        }
        log(event, "targetClass=" + target.getClass().getName()
                + " targetIdentity=" + System.identityHashCode(target)
                + " targetFramebuffer=" + target.frameBufferId
                + " targetColorAttachment=" + target.getColorTextureId());
    }

    public static void worldRenderEnd() {
        if (enabled() && targetDrawSeen) {
            log("WORLD_RENDER_END", "targetEntities=" + TARGET_ENTITIES);
        }
    }

    public static void finalComposite(final RenderTarget target, final int width, final int height) {
        if (enabled() && targetDrawSeen) {
            log("FINAL_COMPOSITE", "sourceTargetIdentity=" + System.identityHashCode(target)
                    + " sourceFramebuffer=" + target.frameBufferId
                    + " sourceColorAttachment=" + target.getColorTextureId()
                    + " destinationSize=" + width + "x" + height);
        }
    }

    public static void compositeComplete(final RenderTarget target) {
        if (enabled() && targetDrawSeen) {
            log("FINAL_COMPOSITE_COMPLETE", "sourceTargetIdentity=" + System.identityHashCode(target)
                    + " sourceFramebuffer=" + target.frameBufferId
                    + " sourceColorAttachment=" + target.getColorTextureId());
        }
    }

    public static void displayEvent(final String event) {
        if (enabled() && targetDrawSeen) {
            log(event, "targetEntities=" + TARGET_ENTITIES + " boundary=WINDOW_UPDATE_DISPLAY");
        }
    }

    private static boolean enabled() {
        return Boolean.getBoolean("sable.m28.visualOwnershipTrace")
                && Boolean.getBoolean(SableM28CpuTargetIdentity.PRESENTATION_PROPERTY);
    }

    private static void log(final String event, final String detail) {
        if (!RenderSystem.isOnRenderThread() || !SableM28CpuTargetIdentity.sampledFrame()) {
            return;
        }
        final RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        Sable.LOGGER.info("SABLE_M29_PRESENTATION_CHAIN frame={} event={} currentFramebuffer={} "
                        + "mainTargetClass={} mainTargetIdentity={} mainFramebuffer={} "
                        + "mainColorAttachment={} {}",
                frame, event, currentFramebuffer(), main.getClass().getName(),
                System.identityHashCode(main), main.frameBufferId, main.getColorTextureId(), detail);
    }

    private static int currentFramebuffer() {
        return GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    }
}
