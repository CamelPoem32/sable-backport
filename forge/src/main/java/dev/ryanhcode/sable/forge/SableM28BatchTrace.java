package dev.ryanhcode.sable.forge;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferBuilder.RenderedBuffer;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixin.m28.BufferBuilderProbeAccessor;
import dev.ryanhcode.sable.mixin.m28.BufferSourceProbeAccessor;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tracks only sampled M28 contraption vertices from the real builder through the draw call. */
public final class SableM28BatchTrace {
    private static final boolean VERBOSE = Boolean.getBoolean("sable.m28.verboseBufferTrace");
    private static final ThreadLocal<Dispatch> DISPATCH = new ThreadLocal<>();
    private static final ThreadLocal<Flush> FLUSH = new ThreadLocal<>();
    private static final ThreadLocal<ScopedBatch> SCOPED_BATCH = new ThreadLocal<>();
    private static final Map<BufferBuilder, List<Range>> PENDING = new IdentityHashMap<>();
    private static final Map<Integer, Integer> OWNER_LOG_COUNTS = new HashMap<>();
    private static volatile long frame;

    private SableM28BatchTrace() {
    }

    public static void beginFrame() {
        frame++;
        SableM28FramebufferProbe.beginFrame(frame);
        SableM28PresentationTrace.beginFrame(frame);
        if (OWNER_LOG_COUNTS.size() > 128) {
            OWNER_LOG_COUNTS.clear();
        }
        PENDING.values().forEach(ranges -> ranges.removeIf(range -> {
            if (range.frame >= frame - 2) {
                return false;
            }
            Sable.LOGGER.info("SABLE_M28_FRAME_PIPELINE frame={} event=UNFLUSHED_RANGE "
                            + "writeFrame={} entityId={} builderIdentity={} range={}..{}",
                    frame, range.frame, range.entityId, System.identityHashCode(range.builder),
                    range.startVertex, range.endVertex);
            return true;
        }));
        PENDING.values().removeIf(List::isEmpty);
    }

    public static long currentFrame() {
        return frame;
    }

    public static void beginDispatch(final BufferSource source, final int entityId, final String stage) {
        DISPATCH.set(new Dispatch(frame, source, entityId, stage));
    }

    public static void endDispatch() {
        DISPATCH.remove();
    }

    public static void beginScopedBatch(final BufferSource source, final String stage) {
        SCOPED_BATCH.set(new ScopedBatch(frame, source, stage, frame <= 5 || frame % 20 == 0));
    }

    public static void beforeScopedFlush(final BufferSource source, final int renderedEntities) {
        final ScopedBatch scoped = SCOPED_BATCH.get();
        if (scoped == null || scoped.source != source) {
            return;
        }
        scoped.renderedEntities = renderedEntities;
        if (scoped.sampled && !scoped.ranges.isEmpty()) {
            Sable.LOGGER.info("SABLE_M28_ENTITY_BATCH frame={} event=END_SCOPED_BATCH "
                            + "renderStage={} lifecycleMode=SABLE_SCOPED bufferSourceClass={} "
                            + "bufferSourceIdentity={} renderedEntities={} trackedRanges={} flushOwner=SABLE",
                    frame, scoped.stage, source.getClass().getName(), System.identityHashCode(source),
                    renderedEntities, scoped.ranges.size());
        }
    }

    public static void afterScopedFlush(final BufferSource source, final int renderedEntities) {
        final ScopedBatch scoped = SCOPED_BATCH.get();
        if (scoped == null || scoped.source != source) {
            return;
        }
        final long pending = scoped.ranges.stream()
                .filter(range -> PENDING.getOrDefault(range.builder, List.of()).contains(range))
                .count();
        if ((scoped.sampled && !scoped.ranges.isEmpty()) || pending > 0) {
            Sable.LOGGER.info("SABLE_M28_ENTITY_BATCH frame={} event=FLUSH_COMPLETE "
                            + "renderStage={} lifecycleMode=SABLE_SCOPED bufferSourceClass={} "
                            + "bufferSourceIdentity={} renderedEntities={} flushOwner=SABLE flushFrame={} "
                            + "flushed={} unflushedRanges={}",
                    frame, scoped.stage, source.getClass().getName(), System.identityHashCode(source),
                    renderedEntities, frame, pending == 0, pending);
        }
        SCOPED_BATCH.remove();
    }

    public static void noteBuffer(final RenderType type, final VertexConsumer consumer) {
        final Dispatch dispatch = DISPATCH.get();
        if (dispatch == null || !(consumer instanceof final BufferBuilder builder)) {
            return;
        }
        final int logged = OWNER_LOG_COUNTS.merge(dispatch.entityId, 1, Integer::sum);
        if (!VERBOSE || logged > 6) {
            return;
        }
        final BufferSourceProbeAccessor source = (BufferSourceProbeAccessor) dispatch.source;
        final BufferBuilderProbeAccessor access = (BufferBuilderProbeAccessor) builder;
        Sable.LOGGER.info("SABLE_M28_BATCH_OWNER frame={} stage={} entityId={} "
                        + "bufferSourceClass={} bufferSourceIdentity={} renderType={} renderTypeIdentity={} "
                        + "consumerIdentity={} builderIdentity={} fixedBuffer={} lastState={} "
                        + "builderBuilding={} vertexCountAtEntry={}",
                dispatch.frame, dispatch.stage, dispatch.entityId,
                dispatch.source.getClass().getName(), System.identityHashCode(dispatch.source),
                type, System.identityHashCode(type), System.identityHashCode(consumer),
                System.identityHashCode(builder), source.sable$getFixedBuffers().containsKey(type),
                source.sable$getLastState(), builder.building(), access.sable$getVertices());
    }

    public static void noteRange(final int entityId, final RenderType type,
                                 final BufferBuilder builder, final int startVertex,
                                 final int endVertex, final int startByte,
                                 final String destinationKind, final float bearingAngle,
                                 final String renderCase) {
        if (builder == null || type == null || endVertex <= startVertex) {
            return;
        }
        final BufferBuilderProbeAccessor access = (BufferBuilderProbeAccessor) builder;
        final int stride = access.sable$getFormat().getVertexSize();
        final List<Vertex> signature = readVertices(access.sable$getBuffer(), startByte,
                Math.min(3, endVertex - startVertex), stride);
        final List<Vertex> allVertices = readVertices(access.sable$getBuffer(), startByte,
                endVertex - startVertex, stride);
        final Range range = new Range(frame, entityId, type, builder, startVertex, endVertex,
                signature, allVertices, destinationKind, bearingAngle, renderCase, null);
        PENDING.computeIfAbsent(builder, ignored -> new ArrayList<>()).add(range);
        final Dispatch dispatch = DISPATCH.get();
        final ScopedBatch scoped = SCOPED_BATCH.get();
        if (scoped != null && dispatch != null && scoped.source == dispatch.source) {
            scoped.ranges.add(range);
            if (scoped.sampled) {
                if (!scoped.beganLogging) {
                    scoped.beganLogging = true;
                    Sable.LOGGER.info("SABLE_M28_ENTITY_BATCH frame={} event=BEGIN_SCOPED_BATCH "
                                    + "renderStage={} lifecycleMode=SABLE_SCOPED bufferSourceClass={} "
                                    + "bufferSourceIdentity={} flushOwner=SABLE",
                            frame, scoped.stage, scoped.source.getClass().getName(),
                            System.identityHashCode(scoped.source));
                }
                Sable.LOGGER.info("SABLE_M28_ENTITY_BATCH frame={} event=WRITE entityId={} "
                                + "renderStage={} lifecycleMode=SABLE_SCOPED bufferSourceClass={} "
                                + "bufferSourceIdentity={} builderIdentity={} renderType={} range={}..{}",
                        frame, entityId, dispatch.stage, dispatch.source.getClass().getName(),
                        System.identityHashCode(dispatch.source), System.identityHashCode(builder),
                        type, startVertex, endVertex);
            }
        }
        if (VERBOSE) {
            Sable.LOGGER.info("SABLE_M28_FRAME_PIPELINE frame={} event=BRIDGE_DISPATCH entityId={} "
                            + "stage={} bufferSourceIdentity={}",
                    frame, entityId, dispatch == null ? "unavailable" : dispatch.stage,
                    dispatch == null ? "unavailable" : System.identityHashCode(dispatch.source));
            Sable.LOGGER.info("SABLE_M28_BATCH_RANGE frame={} entityId={} renderType={} "
                            + "builderIdentity={} startVertex={} endVertex={} signatureVertices={}",
                    frame, entityId, type, System.identityHashCode(builder), startVertex, endVertex, signature);
        }
    }

    public static void noteCpuTargetRange(final int entityId, final UUID entityUuid,
                                          final UUID subLevelUuid, final RenderType type,
                                          final BufferBuilder builder, final int startVertex,
                                          final int endVertex, final int startByte,
                                          final float bearingAngle,
                                          final SableM28CpuTargetIdentity.Rgba color,
                                          final int colorOffset, final int modifiedVertices,
                                          final boolean cpuReadbackVerified) {
        if (builder == null || type == null || endVertex <= startVertex) {
            return;
        }
        final BufferBuilderProbeAccessor access = (BufferBuilderProbeAccessor) builder;
        final int stride = access.sable$getFormat().getVertexSize();
        final List<Vertex> signature = readVertices(access.sable$getBuffer(), startByte,
                Math.min(3, endVertex - startVertex), stride);
        final List<Vertex> allVertices = readVertices(access.sable$getBuffer(), startByte,
                endVertex - startVertex, stride);
        final CpuIdentity identity = new CpuIdentity(entityUuid, subLevelUuid, color, colorOffset,
                modifiedVertices, cpuReadbackVerified);
        final Range range = new Range(frame, entityId, type, builder, startVertex, endVertex,
                signature, allVertices, "CPU_TARGET_IDENTITY", bearingAngle, "SABLE_SUBLEVEL", identity);
        PENDING.computeIfAbsent(builder, ignored -> new ArrayList<>()).add(range);
    }

    public static void beforeEndBatch(final BufferSource source, final RenderType type) {
        final BufferSourceProbeAccessor access = (BufferSourceProbeAccessor) source;
        final BufferBuilder builder = access.sable$getFixedBuffers().getOrDefault(type, access.sable$getBuilder());
        final BufferBuilderProbeAccessor builderAccess = (BufferBuilderProbeAccessor) builder;
        final List<Range> ranges = PENDING.getOrDefault(builder, List.of()).stream()
                .filter(range -> range.type == type).toList();
        if (ranges.isEmpty()) {
            return;
        }
        final boolean activeGeneral = builder != access.sable$getBuilder()
                || access.sable$getLastState().equals(type.asOptional());
        final boolean started = access.sable$getStartedBuffers().contains(builder);
        if (!activeGeneral || !started) {
            if (VERBOSE) {
                Sable.LOGGER.info("SABLE_M28_BATCH_FLUSH frame={} phase=SKIPPED "
                            + "sourceIdentity={} renderType={} builderIdentity={} activeGeneral={} "
                            + "started={} pendingRanges={}",
                        frame, System.identityHashCode(source), type, System.identityHashCode(builder),
                        activeGeneral, started, ranges.size());
            }
            return;
        }
        final Flush flush = new Flush(frame, source, type, builder, ranges,
                builderAccess.sable$getVertices(), builderAccess.sable$getNextElementByte());
        FLUSH.set(flush);
        for (final Range range : ranges) {
            if (range.destinationKind.equals("SODIUM_BUFFER_BUILDER")) {
                Sable.LOGGER.info("SABLE_M28_SODIUM_BUFFER_LIFECYCLE frame={} entityId={} "
                                + "builderIdentity={} renderType={} buildingState={} vertexCount={} "
                                + "bytesWritten={} finalizationEvent=BEGIN sameFrame={}",
                        frame, range.entityId, System.identityHashCode(builder), type,
                        builder.building(), flush.verticesBefore, flush.bytesBefore,
                        range.frame == frame);
            }
        }
        if (VERBOSE) {
            Sable.LOGGER.info("SABLE_M28_BATCH_FLUSH frame={} phase=BEFORE_END_BATCH stage={} "
                        + "sourceIdentity={} renderType={} renderTypeIdentity={} builderIdentity={} "
                        + "vertexCount={} byteCount={} building={} lastState={} ranges={}",
                    frame, "SABLE_SCOPED_END", System.identityHashCode(source),
                    type, System.identityHashCode(type), System.identityHashCode(builder),
                    flush.verticesBefore, flush.bytesBefore, builder.building(),
                    access.sable$getLastState(), ranges.size());
        }
    }

    public static void afterEndBatch() {
        final Flush flush = FLUSH.get();
        if (flush == null) {
            return;
        }
        final BufferBuilderProbeAccessor access = (BufferBuilderProbeAccessor) flush.builder;
        if (VERBOSE) {
            Sable.LOGGER.info("SABLE_M28_BATCH_FLUSH frame={} phase=AFTER_END_BATCH "
                        + "sourceIdentity={} builderIdentity={} vertexCountAfter={} byteCountAfter={} "
                        + "renderedBufferIdentity={} drawCallReached={}",
                    frame, System.identityHashCode(flush.source), System.identityHashCode(flush.builder),
                    access.sable$getVertices(), access.sable$getNextElementByte(),
                    flush.rendered == null ? "none" : System.identityHashCode(flush.rendered),
                    flush.drawReached);
        }
        final List<Range> pending = PENDING.get(flush.builder);
        if (pending != null) {
            pending.removeAll(flush.ranges);
            if (pending.isEmpty()) {
                PENDING.remove(flush.builder);
            }
        }
        FLUSH.remove();
    }

    public static void noteRenderedBuffer(final RenderType type, final BufferBuilder builder,
                                          final RenderedBuffer rendered) {
        final Flush flush = FLUSH.get();
        if (flush == null || flush.builder != builder || flush.type != type) {
            return;
        }
        flush.rendered = rendered;
        final BufferBuilder.DrawState state = rendered.drawState();
        if (VERBOSE) {
            Sable.LOGGER.info("SABLE_M28_RENDERTYPE_END frame={} renderType={} renderTypeIdentity={} "
                            + "builderIdentity={} renderedBufferIdentity={} vertexCount={} indexCount={} "
                            + "byteSize={} mode={} vertexFormat={} thread={}",
                    frame, type, System.identityHashCode(type), System.identityHashCode(builder),
                    System.identityHashCode(rendered), state.vertexCount(), state.indexCount(),
                    state.vertexBufferSize(), state.mode(), state.format(), Thread.currentThread().getName());
        }
        final ByteBuffer vertices = rendered.vertexBuffer();
        final int stride = state.format().getVertexSize();
        for (final Range range : flush.ranges) {
            final List<Vertex> flushed = readVertices(vertices, range.startVertex * stride,
                    range.signature.size(), stride);
            if (range.destinationKind.equals("SODIUM_BUFFER_BUILDER")) {
                Sable.LOGGER.info("SABLE_M28_SODIUM_BUFFER_LIFECYCLE frame={} entityId={} "
                                + "builderIdentity={} renderType={} finalizationEvent=FINALIZED "
                                + "renderedBufferIdentity={} finalVertexCount={} indexCount={} "
                                + "writeFrame={} sameFrame={}",
                        frame, range.entityId, System.identityHashCode(builder), type,
                        System.identityHashCode(rendered), state.vertexCount(), state.indexCount(),
                        range.frame, range.frame == frame);
            }
            Sable.LOGGER.info("SABLE_M28_FLUSHED_VERTEX frame={} writeFrame={} entityId={} builderIdentity={} "
                            + "renderedBufferIdentity={} sourceRange={}..{} written={} finalized={} "
                            + "positionsUnchanged={}",
                    frame, range.frame, range.entityId, System.identityHashCode(builder),
                    System.identityHashCode(rendered), range.startVertex, range.endVertex,
                    range.signature, flushed, range.signature.equals(flushed));
            Sable.LOGGER.info("SABLE_M28_FRAME_PIPELINE frame={} event=RENDERTYPE_END entityId={} "
                            + "renderedBufferIdentity={}", frame, range.entityId,
                    System.identityHashCode(rendered));
        }
    }

    public static void beforeGpuDraw(final RenderedBuffer rendered) {
        final Flush flush = resolveFlushAtDraw(rendered);
        if (flush == null) {
            return;
        }
        logPreDraw(flush, rendered);
    }

    private static void logPreDraw(final Flush flush, final RenderedBuffer rendered) {
        if (flush.preDrawLogged) {
            return;
        }
        flush.preDrawLogged = true;
        final BufferBuilder.DrawState state = rendered.drawState();
        final ByteBuffer vertices = rendered.vertexBuffer();
        final int stride = state.format().getVertexSize();
        for (final Range range : flush.ranges) {
            final int renderedStartVertex = flush.renderedStartVertices.getOrDefault(range, range.startVertex);
            final List<Vertex> stored = readVertices(vertices, renderedStartVertex * stride,
                    range.signature.size(), stride);
            Sable.LOGGER.info("SABLE_M28_PRE_DRAW_VERTEX frame={} entityId={} "
                            + "sourceBuilderIdentity={} renderedBufferIdentity={} sourceRange={}..{} "
                            + "renderedStartVertex={} "
                            + "writePositions={} storedPositions={} deltaFromWrite={} "
                            + "writeFrame={} sameFrame={}",
                    frame, range.entityId, System.identityHashCode(flush.builder),
                    System.identityHashCode(rendered), range.startVertex, range.endVertex,
                    renderedStartVertex, range.signature, stored, maxDelta(range.signature, stored),
                    range.frame, range.frame == frame);
        }
    }

    public static void actualDraw(final RenderedBuffer rendered, final VertexBuffer gpuBuffer,
                                  final Matrix4f modelView, final Matrix4f projection,
                                  final ShaderInstance shader) {
        final Flush flush = resolveFlushAtDraw(rendered);
        if (flush == null) {
            return;
        }
        logPreDraw(flush, rendered);
        flush.drawReached = true;
        final BufferBuilder.DrawState state = rendered.drawState();
        final Uniform chunkOffset = shader == null ? null : shader.CHUNK_OFFSET;
        final FloatBuffer offsetValues = chunkOffset == null ? null : chunkOffset.getFloatBuffer();
        final String chunkOffsetValue = offsetValues == null || offsetValues.limit() < 3
                ? "unavailable" : "(" + offsetValues.get(0) + "," + offsetValues.get(1)
                + "," + offsetValues.get(2) + ")";
        Sable.LOGGER.info("SABLE_M28_GPU_DRAW frame={} renderType={} renderedBufferIdentity={} "
                        + "sourceBuilderIdentity={} vertexCount={} indexCount={} glPrimitiveMode={} "
                        + "shader={} shaderIdentity={} modelView={} projection={} chunkOffset={} "
                        + "gpuBufferIdentity={} drawCallReached=true",
                frame, flush.type, System.identityHashCode(rendered),
                System.identityHashCode(flush.builder), state.vertexCount(), state.indexCount(),
                state.mode(), shader == null ? "none" : shader.getName(),
                shader == null ? "none" : System.identityHashCode(shader),
                modelView, projection, chunkOffsetValue, System.identityHashCode(gpuBuffer));
        for (final Range range : flush.ranges) {
            final int renderedStartVertex = flush.renderedStartVertices.getOrDefault(range, range.startVertex);
            final List<Vertex> allStored = readVertices(rendered.vertexBuffer(),
                    renderedStartVertex * state.format().getVertexSize(),
                    range.endVertex - range.startVertex, state.format().getVertexSize());
            Sable.LOGGER.info("SABLE_M28_DRAW_PROVENANCE frame={} entityId={} case={} bearingAngle={} "
                            + "renderType={} sourceBuilderIdentity={} renderedBufferIdentity={} "
                            + "vertexCount={} indexCount={} shader={} modelView={} projection={} "
                            + "writeFrame={} sameFrame={} trackedVertexRangeIncluded=true drawReached=true",
                    frame, range.entityId, range.renderCase, range.bearingAngle, flush.type,
                    System.identityHashCode(flush.builder), System.identityHashCode(rendered),
                    state.vertexCount(), state.indexCount(), shader == null ? "none" : shader.getName(),
                    modelView, projection, range.frame, range.frame == frame);
            if ("SABLE_SUBLEVEL".equals(range.renderCase)) {
                SableM28FramebufferProbe.recordDynamicDraw(
                        range.entityId, range.bearingAngle,
                        allStored.stream().map(vertex -> new SableM28FramebufferProbe.Point(
                                vertex.x, vertex.y, vertex.z)).toList(),
                        modelView, projection, flush.type, System.identityHashCode(rendered),
                        shader == null ? "none" : shader.getName());
            }
            if (range.cpuIdentity != null) {
                final CpuIdentity identity = range.cpuIdentity;
                final int stride = state.format().getVertexSize();
                final int renderedStartByte = renderedStartVertex * stride;
                final boolean finalColorVerified = identity.modifiedVertices > 0
                        && SableM28CpuTargetIdentity.verifyColor(rendered.vertexBuffer(), renderedStartByte,
                        range.endVertex - range.startVertex, stride, identity.colorOffset, identity.color);
                if (SableM28CpuTargetIdentity.sampledFrame()) {
                    Sable.LOGGER.info("SABLE_M29_CPU_TINT phase=PRE_DRAW_VERIFIED frame={} entityId={} UUID={} "
                                + "subLevel={} sourceVertexRange={}..{} finalVertexRange={}..{} rgba={} "
                                + "verticesModified={} bufferBuilderIdentity={} renderedBufferIdentity={} "
                                + "bearingAngle={} cpuReadbackVerified={} finalReadbackVerified={}",
                        frame, range.entityId, identity.entityUuid, identity.subLevelUuid,
                        range.startVertex, range.endVertex, renderedStartVertex,
                        renderedStartVertex + range.endVertex - range.startVertex, identity.color,
                        identity.modifiedVertices, System.identityHashCode(flush.builder),
                        System.identityHashCode(rendered), range.bearingAngle,
                            identity.cpuReadbackVerified, finalColorVerified);
                }
                SableM28FramebufferProbe.recordCpuTargetDraw(range.entityId, identity.subLevelUuid,
                        range.bearingAngle, allStored.stream().map(vertex ->
                                new SableM28FramebufferProbe.Point(vertex.x, vertex.y, vertex.z)).toList(),
                        modelView, projection, identity.color);
                SableM28PresentationTrace.targetDraw(range.entityId, identity.subLevelUuid,
                        range.bearingAngle, System.identityHashCode(rendered));
            }
            Sable.LOGGER.info("SABLE_M28_FRAME_PIPELINE frame={} event=GPU_DRAW entityId={} "
                            + "renderedBufferIdentity={}", frame, range.entityId,
                    System.identityHashCode(rendered));
        }
        if (flush.inferredAtDraw) {
            removePending(flush);
            FLUSH.remove();
        }
    }

    public static void afterGpuDraw() {
        SableM28FramebufferProbe.afterDynamicDraw();
        SableM28PresentationTrace.targetDrawComplete();
    }

    public static void endWorldRenderProbe() {
        SableM28FramebufferProbe.endWorldRender(frame);
    }

    /** Oculus segments bypass BufferSource.endBatch(RenderType), so match their real draw buffer by vertex bytes. */
    private static Flush resolveFlushAtDraw(final RenderedBuffer rendered) {
        final Flush active = FLUSH.get();
        if (active != null && active.rendered == rendered) {
            return active;
        }
        final BufferBuilder.DrawState state = rendered.drawState();
        final ByteBuffer vertices = rendered.vertexBuffer();
        final int stride = state.format().getVertexSize();
        for (final Map.Entry<BufferBuilder, List<Range>> entry : PENDING.entrySet()) {
            final List<Range> matched = new ArrayList<>();
            final IdentityHashMap<Range, Integer> starts = new IdentityHashMap<>();
            for (final Range range : entry.getValue()) {
                final int start = findSignature(vertices, state.vertexCount(), stride, range.signature);
                if (start >= 0) {
                    matched.add(range);
                    starts.put(range, start);
                }
            }
            if (matched.isEmpty()) {
                continue;
            }
            final Flush inferred = new Flush(frame, null, matched.get(0).type, entry.getKey(), matched,
                    state.vertexCount(), state.vertexBufferSize());
            inferred.rendered = rendered;
            inferred.inferredAtDraw = true;
            inferred.renderedStartVertices.putAll(starts);
            FLUSH.set(inferred);
            for (final Range range : matched) {
                Sable.LOGGER.info("SABLE_M28_SODIUM_BUFFER_LIFECYCLE frame={} entityId={} "
                                + "builderIdentity={} renderType={} finalizationEvent=OCULUS_SEGMENT_DRAW "
                                + "renderedBufferIdentity={} finalVertexCount={} indexCount={} "
                                + "writeFrame={} sameFrame={}",
                        frame, range.entityId, System.identityHashCode(entry.getKey()), range.type,
                        System.identityHashCode(rendered), state.vertexCount(), state.indexCount(),
                        range.frame, range.frame == frame);
                Sable.LOGGER.info("SABLE_M28_FRAME_PIPELINE frame={} event=OCULUS_SEGMENT_FINALIZED "
                                + "entityId={} renderedBufferIdentity={}",
                        frame, range.entityId, System.identityHashCode(rendered));
            }
            return inferred;
        }
        return null;
    }

    private static int findSignature(final ByteBuffer vertices, final int vertexCount, final int stride,
                                     final List<Vertex> signature) {
        if (signature.isEmpty()) {
            return -1;
        }
        for (int start = 0; start <= vertexCount - signature.size(); start++) {
            final List<Vertex> candidate = readVertices(vertices, start * stride, signature.size(), stride);
            if (signature.equals(candidate)) {
                return start;
            }
        }
        return -1;
    }

    private static void removePending(final Flush flush) {
        final List<Range> pending = PENDING.get(flush.builder);
        if (pending == null) {
            return;
        }
        pending.removeAll(flush.ranges);
        if (pending.isEmpty()) {
            PENDING.remove(flush.builder);
        }
    }

    private static List<Vertex> readVertices(final ByteBuffer buffer, final int start,
                                             final int count, final int stride) {
        final List<Vertex> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final int offset = start + i * stride;
            if (offset < 0 || offset + 3 * Float.BYTES > buffer.limit()) {
                break;
            }
            result.add(new Vertex(buffer.getFloat(offset), buffer.getFloat(offset + Float.BYTES),
                    buffer.getFloat(offset + 2 * Float.BYTES)));
        }
        return result;
    }

    private static double maxDelta(final List<Vertex> expected, final List<Vertex> actual) {
        double maximum = 0.0;
        for (int i = 0; i < Math.min(expected.size(), actual.size()); i++) {
            final Vertex a = expected.get(i);
            final Vertex b = actual.get(i);
            final double dx = a.x - b.x;
            final double dy = a.y - b.y;
            final double dz = a.z - b.z;
            maximum = Math.max(maximum, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return maximum;
    }

    private record Dispatch(long frame, BufferSource source, int entityId, String stage) {
    }

    private record Range(long frame, int entityId, RenderType type, BufferBuilder builder,
                         int startVertex, int endVertex, List<Vertex> signature, List<Vertex> allVertices,
                         String destinationKind, float bearingAngle, String renderCase,
                         CpuIdentity cpuIdentity) {
    }

    private record CpuIdentity(UUID entityUuid, UUID subLevelUuid,
                               SableM28CpuTargetIdentity.Rgba color, int colorOffset,
                               int modifiedVertices, boolean cpuReadbackVerified) {
    }

    private record Vertex(float x, float y, float z) {
    }

    private static final class Flush {
        private final long frame;
        private final BufferSource source;
        private final RenderType type;
        private final BufferBuilder builder;
        private final List<Range> ranges;
        private final int verticesBefore;
        private final int bytesBefore;
        private RenderedBuffer rendered;
        private boolean drawReached;
        private boolean preDrawLogged;
        private boolean inferredAtDraw;
        private final IdentityHashMap<Range, Integer> renderedStartVertices = new IdentityHashMap<>();

        private Flush(final long frame, final BufferSource source, final RenderType type,
                      final BufferBuilder builder, final List<Range> ranges,
                      final int verticesBefore, final int bytesBefore) {
            this.frame = frame;
            this.source = source;
            this.type = type;
            this.builder = builder;
            this.ranges = ranges;
            this.verticesBefore = verticesBefore;
            this.bytesBefore = bytesBefore;
        }
    }

    private static final class ScopedBatch {
        private final long frame;
        private final BufferSource source;
        private final String stage;
        private final boolean sampled;
        private final List<Range> ranges = new ArrayList<>();
        private int renderedEntities;
        private boolean beganLogging;

        private ScopedBatch(final long frame, final BufferSource source, final String stage,
                            final boolean sampled) {
            this.frame = frame;
            this.source = source;
            this.stage = stage;
            this.sampled = sampled;
        }
    }
}
