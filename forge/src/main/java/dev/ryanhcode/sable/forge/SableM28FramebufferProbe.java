package dev.ryanhcode.sable.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded M28 screen-space and framebuffer ownership probe for proven GPU-bound vertices. */
public final class SableM28FramebufferProbe {
    private static final boolean READ_FRAMEBUFFER = Boolean.getBoolean("sable.m28.framebufferProbe");
    private static long frame;
    private static int drawOrder;
    private static PendingProbe pending;
    private static final Map<Integer, OverlayTarget> OVERLAYS = new LinkedHashMap<>();

    private SableM28FramebufferProbe() {
    }

    public static void beginFrame(final long currentFrame) {
        frame = currentFrame;
        drawOrder = 0;
        pending = null;
        OVERLAYS.clear();
    }

    public static void recordCpuTargetDraw(final int entityId, final UUID subLevelId,
                                           final float bearingAngle, final List<Point> vertices,
                                           final Matrix4f modelView, final Matrix4f projection,
                                           final SableM28CpuTargetIdentity.Rgba color) {
        if (!Boolean.getBoolean(SableM28CpuTargetIdentity.OVERLAY_PROPERTY) || vertices.isEmpty()) {
            return;
        }
        final int width = Minecraft.getInstance().getWindow().getWidth();
        final int height = Minecraft.getInstance().getWindow().getHeight();
        final ScreenBox box = projectScreenBox(vertices, modelView, projection, width, height);
        OVERLAYS.put(entityId, new OverlayTarget(frame, entityId, subLevelId, bearingAngle,
                width, height, box, color));
        if (frame <= 5 || frame % 5 == 0) {
            Sable.LOGGER.info("SABLE_M29_CPU_SCREEN_BOX frame={} entityId={} subLevel={} bearingAngle={} "
                            + "minX={} minY={} maxX={} maxY={} viewportWidth={} viewportHeight={} "
                            + "projectedVertices={} behindCameraCount={} clippedCount={} finite={}",
                    frame, entityId, subLevelId, bearingAngle, box.minX, box.minY, box.maxX, box.maxY,
                    width, height, box.projectedVertices, box.behindCameraCount, box.clippedCount, box.finite);
        }
    }

    public static void renderCpuTargetOverlay(final GuiGraphics graphics, final int guiWidth,
                                              final int guiHeight) {
        if (!Boolean.getBoolean(SableM28CpuTargetIdentity.OVERLAY_PROPERTY)) {
            return;
        }
        for (final OverlayTarget target : OVERLAYS.values()) {
            if (target.frame != frame || !target.box.finite || target.box.projectedVertices == 0) {
                continue;
            }
            if (target.box.maxX < 0.0 || target.box.minX >= target.viewportWidth
                    || target.box.maxY < 0.0 || target.box.minY >= target.viewportHeight) {
                continue;
            }
            final double scaleX = (double) guiWidth / target.viewportWidth;
            final double scaleY = (double) guiHeight / target.viewportHeight;
            final int left = clamp((int) Math.floor(target.box.minX * scaleX), 0, guiWidth - 1);
            final int right = clamp((int) Math.ceil(target.box.maxX * scaleX), 0, guiWidth - 1);
            final int top = clamp((int) Math.floor(target.box.minY * scaleY), 0, guiHeight - 1);
            final int bottom = clamp((int) Math.ceil(target.box.maxY * scaleY), 0, guiHeight - 1);
            final int color = target.color.argb();
            graphics.fill(left, top, right + 1, Math.min(top + 1, guiHeight), color);
            graphics.fill(left, Math.max(top, bottom), right + 1, Math.min(bottom + 1, guiHeight), color);
            graphics.fill(left, top, Math.min(left + 1, guiWidth), bottom + 1, color);
            graphics.fill(Math.max(left, right), top, Math.min(right + 1, guiWidth), bottom + 1, color);
            final String label = "M28 CPU entity=" + target.entityId + " angle="
                    + String.format(java.util.Locale.ROOT, "%.1f", target.bearingAngle);
            graphics.drawString(Minecraft.getInstance().font, label, left,
                    Math.max(0, top - Minecraft.getInstance().font.lineHeight - 1), color, true);
        }
    }

    public static void recordDynamicDraw(final int entityId, final float bearingAngle,
                                         final List<Point> vertices, final Matrix4f modelView,
                                         final Matrix4f projection, final RenderType renderType,
                                         final int renderedBufferIdentity, final String shaderName) {
        drawOrder++;
        if (vertices.isEmpty()) {
            return;
        }
        final int width = Minecraft.getInstance().getWindow().getWidth();
        final int height = Minecraft.getInstance().getWindow().getHeight();
        final Bounds bounds = project(vertices, modelView, projection, width, height);
        final boolean sampledFrame = frame <= 5 || frame % 20 == 0;
        if (sampledFrame) {
            Sable.LOGGER.info("SABLE_M28_SCREEN_BOUNDS frame={} entityId={} bearingAngle={} "
                            + "vertexCount={} viewportWidth={} viewportHeight={} minNdcX={} maxNdcX={} "
                            + "minNdcY={} maxNdcY={} minPixelX={} maxPixelX={} minPixelY={} maxPixelY={} "
                            + "minDepth={} maxDepth={} intersectsViewport={} finite={}",
                    frame, entityId, bearingAngle, vertices.size(), width, height,
                    bounds.minNdcX, bounds.maxNdcX, bounds.minNdcY, bounds.maxNdcY,
                    bounds.minPixelX, bounds.maxPixelX, bounds.minPixelY, bounds.maxPixelY,
                    bounds.minDepth, bounds.maxDepth, bounds.intersectsViewport, bounds.finite);
            Sable.LOGGER.info("SABLE_M28_VISIBLE_OWNER frame={} source=CREATE_CONTRAPTION "
                            + "entityId={} renderedBufferIdentity={} renderType={} drawOrderIndex={} "
                            + "screenBounds={} framebufferTarget={} shader={} drawReached=true",
                    frame, entityId, renderedBufferIdentity, renderType, drawOrder, bounds,
                    currentDrawFramebuffer(), shaderName);
        }
        if (READ_FRAMEBUFFER && sampledFrame && pending == null && bounds.intersectsViewport && bounds.finite) {
            pending = new PendingProbe(frame, entityId, samplePixels(bounds, width, height),
                    currentDrawFramebuffer(), false, new ArrayList<>());
        }
    }

    public static void afterDynamicDraw() {
        final PendingProbe probe = pending;
        if (!READ_FRAMEBUFFER || probe == null || probe.frame != frame || probe.afterDynamicDraw) {
            return;
        }
        probe.afterDynamicDraw = true;
        probe.afterSamples.addAll(readSamples(probe, "AFTER_DYNAMIC_DRAW"));
    }

    public static void endWorldRender(final long currentFrame) {
        final PendingProbe probe = pending;
        if (!READ_FRAMEBUFFER || probe == null || probe.frame != currentFrame) {
            return;
        }
        final List<PixelSample> endSamples = readSamples(probe, "END_WORLD_RENDER");
        for (int i = 0; i < endSamples.size(); i++) {
            final PixelSample after = i < probe.afterSamples.size() ? probe.afterSamples.get(i) : null;
            final PixelSample end = endSamples.get(i);
            Sable.LOGGER.info("SABLE_M28_FRAMEBUFFER_PROBE frame={} phase=END_WORLD_RENDER entityId={} "
                            + "samplePixel=({}, {}) rgba={} depth={} framebufferTarget={} "
                            + "expectedDynamicCoverage=BOUNDS_SAMPLE changedAfterLaterRendering={}",
                    frame, probe.entityId, end.x, end.y, end.rgba, end.depth,
                    currentDrawFramebuffer(), after != null && !after.sameValue(end));
        }
        pending = null;
    }

    private static List<PixelSample> readSamples(final PendingProbe probe, final String phase) {
        if (!RenderSystem.isOnRenderThread()) {
            return List.of();
        }
        final List<PixelSample> samples = new ArrayList<>(probe.pixels.size());
        for (final Pixel pixel : probe.pixels) {
            final ByteBuffer rgba = BufferUtils.createByteBuffer(4);
            final FloatBuffer depth = BufferUtils.createFloatBuffer(1);
            GL11.glReadPixels(pixel.x, pixel.y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
            GL11.glReadPixels(pixel.x, pixel.y, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
            final String rgbaValue = "(" + Byte.toUnsignedInt(rgba.get(0)) + ","
                    + Byte.toUnsignedInt(rgba.get(1)) + "," + Byte.toUnsignedInt(rgba.get(2)) + ","
                    + Byte.toUnsignedInt(rgba.get(3)) + ")";
            final PixelSample sample = new PixelSample(pixel.x, pixel.y, rgbaValue, depth.get(0));
            samples.add(sample);
            if ("AFTER_DYNAMIC_DRAW".equals(phase)) {
                Sable.LOGGER.info("SABLE_M28_FRAMEBUFFER_PROBE frame={} phase={} entityId={} "
                                + "samplePixel=({}, {}) rgba={} depth={} framebufferTarget={} "
                                + "expectedDynamicCoverage=BOUNDS_SAMPLE changedAfterLaterRendering=false",
                        frame, phase, probe.entityId, pixel.x, pixel.y, rgbaValue, depth.get(0),
                        currentDrawFramebuffer());
            }
        }
        return samples;
    }

    private static Bounds project(final List<Point> vertices, final Matrix4f modelView,
                                  final Matrix4f projection, final int width, final int height) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        boolean finite = true;
        for (final Point vertex : vertices) {
            final Vector4f clip = new Vector4f(vertex.x, vertex.y, vertex.z, 1.0F);
            modelView.transform(clip);
            projection.transform(clip);
            if (!Float.isFinite(clip.x) || !Float.isFinite(clip.y)
                    || !Float.isFinite(clip.z) || !Float.isFinite(clip.w) || clip.w == 0.0F) {
                finite = false;
                continue;
            }
            final double x = clip.x / clip.w;
            final double y = clip.y / clip.w;
            final double z = clip.z / clip.w;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        finite &= Double.isFinite(minX) && Double.isFinite(minY) && Double.isFinite(minZ)
                && Double.isFinite(maxX) && Double.isFinite(maxY) && Double.isFinite(maxZ);
        final double minPixelX = (minX * 0.5 + 0.5) * width;
        final double maxPixelX = (maxX * 0.5 + 0.5) * width;
        final double minPixelY = (minY * 0.5 + 0.5) * height;
        final double maxPixelY = (maxY * 0.5 + 0.5) * height;
        final boolean intersects = finite && maxX >= -1.0 && minX <= 1.0 && maxY >= -1.0 && minY <= 1.0;
        return new Bounds(minX, maxX, minY, maxY, minPixelX, maxPixelX,
                minPixelY, maxPixelY, minZ, maxZ, intersects, finite);
    }

    private static ScreenBox projectScreenBox(final List<Point> vertices, final Matrix4f modelView,
                                              final Matrix4f projection, final int width, final int height) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        int projected = 0;
        int behind = 0;
        int clipped = 0;
        for (final Point vertex : vertices) {
            final Vector4f clip = new Vector4f(vertex.x, vertex.y, vertex.z, 1.0F);
            modelView.transform(clip);
            projection.transform(clip);
            if (!Float.isFinite(clip.x) || !Float.isFinite(clip.y)
                    || !Float.isFinite(clip.z) || !Float.isFinite(clip.w) || clip.w <= 0.0F) {
                behind++;
                continue;
            }
            final double ndcX = clip.x / clip.w;
            final double ndcY = clip.y / clip.w;
            final double ndcZ = clip.z / clip.w;
            if (ndcX < -1.0 || ndcX > 1.0 || ndcY < -1.0 || ndcY > 1.0
                    || ndcZ < -1.0 || ndcZ > 1.0) {
                clipped++;
            }
            final double pixelX = (ndcX * 0.5 + 0.5) * width;
            final double pixelY = (1.0 - (ndcY * 0.5 + 0.5)) * height;
            minX = Math.min(minX, pixelX);
            minY = Math.min(minY, pixelY);
            maxX = Math.max(maxX, pixelX);
            maxY = Math.max(maxY, pixelY);
            projected++;
        }
        final boolean finite = projected > 0 && Double.isFinite(minX) && Double.isFinite(minY)
                && Double.isFinite(maxX) && Double.isFinite(maxY);
        return new ScreenBox(minX, minY, maxX, maxY, projected, behind, clipped, finite);
    }

    private static List<Pixel> samplePixels(final Bounds bounds, final int width, final int height) {
        final int minX = clamp((int) Math.floor(bounds.minPixelX), 0, width - 1);
        final int maxX = clamp((int) Math.ceil(bounds.maxPixelX), 0, width - 1);
        final int minY = clamp((int) Math.floor(bounds.minPixelY), 0, height - 1);
        final int maxY = clamp((int) Math.ceil(bounds.maxPixelY), 0, height - 1);
        return List.of(
                new Pixel((minX + maxX) / 2, (minY + maxY) / 2),
                new Pixel((3 * minX + maxX) / 4, (3 * minY + maxY) / 4),
                new Pixel((minX + 3 * maxX) / 4, (minY + 3 * maxY) / 4));
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int currentDrawFramebuffer() {
        return RenderSystem.isOnRenderThread() ? GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) : -1;
    }

    public record Point(float x, float y, float z) {
    }

    private record Pixel(int x, int y) {
    }

    private record Bounds(double minNdcX, double maxNdcX, double minNdcY, double maxNdcY,
                          double minPixelX, double maxPixelX, double minPixelY, double maxPixelY,
                          double minDepth, double maxDepth, boolean intersectsViewport, boolean finite) {
    }

    private record ScreenBox(double minX, double minY, double maxX, double maxY,
                             int projectedVertices, int behindCameraCount, int clippedCount,
                             boolean finite) {
    }

    private record OverlayTarget(long frame, int entityId, UUID subLevelId, float bearingAngle,
                                 int viewportWidth, int viewportHeight, ScreenBox box,
                                 SableM28CpuTargetIdentity.Rgba color) {
    }

    private record PixelSample(int x, int y, String rgba, float depth) {
        private boolean sameValue(final PixelSample other) {
            return this.rgba.equals(other.rgba) && Float.compare(this.depth, other.depth) == 0;
        }
    }

    private static final class PendingProbe {
        private final long frame;
        private final int entityId;
        private final List<Pixel> pixels;
        @SuppressWarnings("unused")
        private final int drawFramebuffer;
        private boolean afterDynamicDraw;
        private final List<PixelSample> afterSamples;

        private PendingProbe(final long frame, final int entityId, final List<Pixel> pixels,
                             final int drawFramebuffer, final boolean afterDynamicDraw,
                             final List<PixelSample> afterSamples) {
            this.frame = frame;
            this.entityId = entityId;
            this.pixels = pixels;
            this.drawFramebuffer = drawFramebuffer;
            this.afterDynamicDraw = afterDynamicDraw;
            this.afterSamples = afterSamples;
        }
    }
}
