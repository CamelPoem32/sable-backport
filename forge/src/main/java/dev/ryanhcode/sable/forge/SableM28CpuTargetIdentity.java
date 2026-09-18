package dev.ryanhcode.sable.forge;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.mixin.m28.BufferBuilderProbeAccessor;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.RenderType;

import java.nio.ByteBuffer;
import java.util.UUID;

/** Diagnostic-only identity coloring for the exact CPU contraption vertex range. */
public final class SableM28CpuTargetIdentity {
    public static final String TINT_PROPERTY = "sable.m28.tintCpuTargetContraption";
    public static final String OVERLAY_PROPERTY = "sable.m28.showCpuTargetOverlay";
    public static final String PRESENTATION_PROPERTY = "sable.m28.traceFinalPresentation";

    private static final ThreadLocal<Capture> ACTIVE = new ThreadLocal<>();

    private SableM28CpuTargetIdentity() {
    }

    public static void begin(final AbstractContraptionEntity entity, final VertexConsumer consumer,
                             final RenderType layer) {
        ACTIVE.remove();
        if (!enabled() || SableM28VisualOwnershipTrace.currentOwner()
                != SableM28VisualOwnershipTrace.Owner.SABLE_FORGE_STAGE_BRIDGE
                || !(entity instanceof final ControlledContraptionEntity controlled)
                || !(SableCreateContraptionContext.getContainingSubLevel(entity)
                instanceof final ClientSubLevel subLevel)) {
            return;
        }
        final BufferBuilder builder = SableM28ContraptionBufferProbe.inspectBackingBuilder(consumer);
        if (builder == null) {
            return;
        }
        final BufferBuilderProbeAccessor access = (BufferBuilderProbeAccessor) builder;
        ACTIVE.set(new Capture(entity.getId(), entity.getUUID(), subLevel.getUniqueId(),
                controlled.getAngle(AnimationTickHolder.getPartialTicks()), layer, builder,
                access.sable$getVertices(), access.sable$getNextElementByte()));
    }

    public static boolean end() {
        final Capture capture = ACTIVE.get();
        ACTIVE.remove();
        if (capture == null) {
            return false;
        }
        final BufferBuilderProbeAccessor access = (BufferBuilderProbeAccessor) capture.builder;
        final int endVertex = access.sable$getVertices();
        final int endByte = access.sable$getNextElementByte();
        if (endVertex <= capture.startVertex || endByte <= capture.startByte || capture.layer == null) {
            return false;
        }

        final VertexFormat format = access.sable$getFormat();
        final int vertexCount = endVertex - capture.startVertex;
        if ((long) vertexCount * format.getVertexSize() != (long) endByte - capture.startByte
                || capture.startByte < 0 || endByte > access.sable$getBuffer().limit()) {
            Sable.LOGGER.info("SABLE_M29_CPU_TINT phase=RANGE_REJECTED frame={} entityId={} "
                            + "reason=INCONSISTENT_VERTEX_BYTE_RANGE bufferBuilderIdentity={}",
                    SableM28BatchTrace.currentFrame(), capture.entityId,
                    System.identityHashCode(capture.builder));
            return false;
        }
        final int colorOffset = colorOffset(format);
        final Rgba color = diagnosticColor(capture.subLevelId);
        int modified = 0;
        boolean readbackVerified = false;
        if (Boolean.getBoolean(TINT_PROPERTY) && colorOffset >= 0) {
            final ByteBuffer buffer = access.sable$getBuffer();
            final int stride = format.getVertexSize();
            for (int vertex = capture.startVertex; vertex < endVertex; vertex++) {
                final int offset = capture.startByte + (vertex - capture.startVertex) * stride + colorOffset;
                if (offset < 0 || offset + 4 > endByte) {
                    break;
                }
                putColor(buffer, offset, color);
                modified++;
            }
            readbackVerified = modified == endVertex - capture.startVertex
                    && verifyColor(access.sable$getBuffer(), capture.startByte, modified,
                    format.getVertexSize(), colorOffset, color);
            if (sampledFrame()) {
                Sable.LOGGER.info("SABLE_M29_CPU_TINT phase=CPU_RANGE_MODIFIED frame={} entityId={} UUID={} "
                            + "subLevel={} sourceVertexRange={}..{} finalVertexRange={}..{} rgba={} "
                            + "verticesModified={} bufferBuilderIdentity={} renderedBufferIdentity=PENDING "
                            + "bearingAngle={} colorOffset={} stride={} readbackVerified={}",
                    SableM28BatchTrace.currentFrame(), capture.entityId, capture.entityUuid,
                    capture.subLevelId, capture.startVertex, endVertex, capture.startVertex, endVertex,
                    color, modified, System.identityHashCode(capture.builder), capture.bearingAngle,
                        colorOffset, format.getVertexSize(), readbackVerified);
            }
        }

        SableM28BatchTrace.noteCpuTargetRange(capture.entityId, capture.entityUuid, capture.subLevelId,
                capture.layer, capture.builder, capture.startVertex, endVertex, capture.startByte,
                capture.bearingAngle, color, colorOffset, modified, readbackVerified);
        return true;
    }

    private static boolean enabled() {
        return Boolean.getBoolean("sable.m28.visualOwnershipTrace")
                && (Boolean.getBoolean(TINT_PROPERTY)
                || Boolean.getBoolean(OVERLAY_PROPERTY)
                || Boolean.getBoolean(PRESENTATION_PROPERTY));
    }

    private static int colorOffset(final VertexFormat format) {
        for (int index = 0; index < format.getElements().size(); index++) {
            final VertexFormatElement element = format.getElements().get(index);
            if (element.getUsage() == VertexFormatElement.Usage.COLOR
                    && element.getType() == VertexFormatElement.Type.UBYTE && element.getCount() == 4) {
                return format.getOffset(index);
            }
        }
        return -1;
    }

    static boolean sampledFrame() {
        final long frame = SableM28BatchTrace.currentFrame();
        return frame <= 5 || frame % 20 == 0;
    }

    private static Rgba diagnosticColor(final UUID subLevelId) {
        return (subLevelId.getLeastSignificantBits() & 1L) == 0L
                ? new Rgba(0, 255, 255, 255)
                : new Rgba(255, 0, 255, 255);
    }

    private static void putColor(final ByteBuffer buffer, final int offset, final Rgba color) {
        buffer.put(offset, (byte) color.red);
        buffer.put(offset + 1, (byte) color.green);
        buffer.put(offset + 2, (byte) color.blue);
        buffer.put(offset + 3, (byte) color.alpha);
    }

    static boolean verifyColor(final ByteBuffer buffer, final int startByte, final int count,
                               final int stride, final int colorOffset, final Rgba color) {
        if (colorOffset < 0 || count <= 0) {
            return false;
        }
        for (int vertex = 0; vertex < count; vertex++) {
            final int offset = startByte + vertex * stride + colorOffset;
            if (offset < 0 || offset + 4 > buffer.limit()
                    || Byte.toUnsignedInt(buffer.get(offset)) != color.red
                    || Byte.toUnsignedInt(buffer.get(offset + 1)) != color.green
                    || Byte.toUnsignedInt(buffer.get(offset + 2)) != color.blue
                    || Byte.toUnsignedInt(buffer.get(offset + 3)) != color.alpha) {
                return false;
            }
        }
        return true;
    }

    public record Rgba(int red, int green, int blue, int alpha) {
        int argb() {
            return this.alpha << 24 | this.red << 16 | this.green << 8 | this.blue;
        }
    }

    private record Capture(int entityId, UUID entityUuid, UUID subLevelId, float bearingAngle,
                           RenderType layer, BufferBuilder builder, int startVertex, int startByte) {
    }
}
