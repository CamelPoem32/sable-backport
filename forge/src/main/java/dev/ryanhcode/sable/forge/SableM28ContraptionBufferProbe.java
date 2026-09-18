package dev.ryanhcode.sable.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.LightTexture;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.mixin.m28.BufferBuilderProbeAccessor;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.render.TemplateMesh;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded M28 diagnostics at Create's real CPU-buffer and vertex-submission boundary. */
public final class SableM28ContraptionBufferProbe {
    private static final float SAMPLE_ANGLE_DELTA = 30.0F;
    private static final int MAX_SAMPLES_PER_ENTITY = 6;
    private static final int MAX_VERTICES_PER_SAMPLE = 12;

    private static final ThreadLocal<RenderType> PENDING_LAYER = new ThreadLocal<>();
    private static final ThreadLocal<Sample> ACTIVE_SAMPLE = new ThreadLocal<>();
    private static final ThreadLocal<SourceVertex> CURRENT_VERTEX = new ThreadLocal<>();
    private static final ThreadLocal<DestinationStart> DESTINATION_START = new ThreadLocal<>();
    private static final Map<Integer, Float> LAST_SAMPLE_ANGLE = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> SAMPLE_COUNTS = new ConcurrentHashMap<>();
    private static final Map<String, PreviousVertex> PREVIOUS_VERTICES = new ConcurrentHashMap<>();
    private static final Set<String> LOGGED_UNSUPPORTED_DESTINATIONS = ConcurrentHashMap.newKeySet();

    private SableM28ContraptionBufferProbe() {
    }

    public static void noteLayer(final AbstractContraptionEntity entity, final RenderType layer) {
        if (SableCreateContraptionContext.getContainingSubLevel(entity) instanceof ClientSubLevel
                || entity instanceof ControlledContraptionEntity
                && Boolean.getBoolean("sable.m28.normalCreateAB")) {
            PENDING_LAYER.set(layer);
        }
    }

    public static void begin(final AbstractContraptionEntity entity, final SuperByteBuffer buffer,
                             final PoseStack rendererPose, final VertexConsumer consumer) {
        ACTIVE_SAMPLE.remove();
        SableM28CpuTargetIdentity.begin(entity, consumer, PENDING_LAYER.get());
        if (!(entity instanceof final ControlledContraptionEntity controlled)
                || entity.getContraption() == null) {
            PENDING_LAYER.remove();
            return;
        }
        final ClientSubLevel subLevel = SableCreateContraptionContext.getContainingSubLevel(entity)
                instanceof final ClientSubLevel found ? found : null;
        final boolean normalWorldAb = subLevel == null && Boolean.getBoolean("sable.m28.normalCreateAB");
        if (subLevel == null && !normalWorldAb) {
            PENDING_LAYER.remove();
            return;
        }

        final float partialTick = AnimationTickHolder.getPartialTicks();
        final float angle = controlled.getAngle(partialTick);
        final Float previous = LAST_SAMPLE_ANGLE.get(entity.getId());
        if (previous != null && Math.abs(Mth.wrapDegrees(angle - previous)) < SAMPLE_ANGLE_DELTA) {
            PENDING_LAYER.remove();
            return;
        }
        final int sampleIndex = SAMPLE_COUNTS.merge(entity.getId(), 1, Integer::sum);
        if (sampleIndex > MAX_SAMPLES_PER_ENTITY) {
            PENDING_LAYER.remove();
            return;
        }
        LAST_SAMPLE_ANGLE.put(entity.getId(), angle);

        final Matrix4f rendererMatrix = new Matrix4f(rendererPose.last().pose());
        final PoseStack bufferTransforms = buffer.getTransforms();
        final Matrix4f bufferMatrix = new Matrix4f(bufferTransforms.last().pose());
        final Matrix4f finalMatrix = new Matrix4f(rendererMatrix).mul(bufferMatrix);
        final List<String> states = entity.getContraption().getBlocks().values().stream()
                .map(info -> info.state().toString())
                .toList();
        final RenderType layer = PENDING_LAYER.get();
        PENDING_LAYER.remove();

        final Destination destination = inspectDestination(consumer);
        final BufferBuilder builder = destination.builder;
        final int countBefore = builder == null ? -1 : ((BufferBuilderProbeAccessor) builder).sable$getVertices();
        final int byteBefore = builder == null ? -1 : ((BufferBuilderProbeAccessor) builder).sable$getNextElementByte();
        final Sample sample = new Sample(entity.getId(), subLevel == null ? "NORMAL_WORLD" : subLevel.getUniqueId().toString(),
                subLevel == null ? "NORMAL_WORLD" : "SABLE_SUBLEVEL", sampleIndex,
                angle, finalMatrix, layer, System.identityHashCode(buffer), destination,
                countBefore, byteBefore, states.toString());
        ACTIVE_SAMPLE.set(sample);
        Sable.LOGGER.info("SABLE_M28_BUFFER_TRANSFORM entityId={} subLevel={} sample={} "
                        + "capturedBlockStates={} renderCategory=PLAIN_BAKED_MODEL_BUFFER "
                        + "activePath=CREATE_CPU_SUPER_BYTE_BUFFER flywheelContraptionPath=false "
                        + "renderLayer={} bufferClass={} bufferIdentity={} consumerClass={} consumerIdentity={} "
                        + "contraptionMatricesIdentity={} modelPoseStackIdentity={} "
                        + "rendererPoseStackIdentity={} bufferTransformPoseStackIdentity={} "
                        + "bearingAngle={} partialTick={} rendererPoseMatrix={} actualBufferTransform={} "
                        + "actualFinalMatrix={} hiddenPlotCoordinatePresent={}",
                entity.getId(), sample.subLevelId, sampleIndex, states,
                layer == null ? "unavailable" : layer,
                buffer.getClass().getName(), System.identityHashCode(buffer),
                consumer.getClass().getName(), System.identityHashCode(consumer),
                System.identityHashCode(entity.getContraption().getOrCreateClientContraptionLazy().getMatrices()),
                System.identityHashCode(entity.getContraption().getOrCreateClientContraptionLazy()
                        .getMatrices().getModel()),
                System.identityHashCode(rendererPose), System.identityHashCode(bufferTransforms),
                angle, partialTick, rendererMatrix, bufferMatrix, finalMatrix,
                hasHiddenPlotCoordinate(finalMatrix));
        Sable.LOGGER.info("SABLE_M28_CREATE_AB case={} entityId={} consumerClass={} renderType={} "
                        + "builderIdentity={} stage={} finalizationFrame=RUNTIME_PENDING drawFrame=RUNTIME_PENDING "
                        + "visibleResult=MANUAL_RUNTIME_REQUIRED",
                sample.renderCase, sample.entityId, consumer.getClass().getName(), sample.layer,
                builder == null ? "unsupported" : System.identityHashCode(builder),
                sample.renderCase.equals("SABLE_SUBLEVEL") ? SableForgeCreateContraptionRenderBridge.activeMode()
                        : "NORMAL_ENTITY_PHASE");
    }

    public static void beforeVertex(final VertexConsumer consumer, final TemplateMesh mesh,
                                    final int[] shadeSwapVertices, final Matrix4f productionModelMatrix,
                                    final int vertexIndex) {
        final Sample sample = ACTIVE_SAMPLE.get();
        if (sample == null) {
            return;
        }
        if (!sample.shadeLogged) {
            sample.shadeLogged = true;
            sample.productionModelMatrix = new Matrix4f(productionModelMatrix);
            Sable.LOGGER.info("SABLE_M28_SHADE_BUFFER entityId={} parentIdentity={} "
                            + "templateIdentity={} templateVertexCount={} childBufferCount=0 "
                            + "shadeSwapVertexCount={} shadeSwapVertices={} productionModelMatrix={} "
                            + "destinationConsumerIdentity={} renderLayer={}",
                    sample.entityId, sample.sourceBufferIdentity, System.identityHashCode(mesh),
                    mesh.vertexCount(), shadeSwapVertices.length, Arrays.toString(shadeSwapVertices),
                    sample.productionModelMatrix, System.identityHashCode(consumer), sample.layer);
        }
        CURRENT_VERTEX.set(new SourceVertex(vertexIndex, mesh.x(vertexIndex), mesh.y(vertexIndex),
                mesh.z(vertexIndex), System.identityHashCode(mesh)));
        if (sample.destination.kind.equals("SODIUM_BUFFER_BUILDER")
                && sample.destination.builder != null) {
            final BufferBuilderProbeAccessor access = (BufferBuilderProbeAccessor) sample.destination.builder;
            beforeDestinationWrite(sample.destination.builder, access.sable$getVertices(),
                    access.sable$getNextElementByte());
        }
    }

    public static void afterVertex(final VertexConsumer consumer, final TemplateMesh mesh,
                                   final int vertexIndex, final float x, final float y, final float z) {
        final Sample sample = ACTIVE_SAMPLE.get();
        if (sample != null && sample.destination.kind.equals("SODIUM_BUFFER_BUILDER")
                && sample.destination.builder != null) {
            final BufferBuilderProbeAccessor access = (BufferBuilderProbeAccessor) sample.destination.builder;
            afterDestinationWrite(sample.destination.builder, access.sable$getVertices(),
                    access.sable$getNextElementByte());
        }
        if (sample != null && vertexIndex == 0) {
            Sable.LOGGER.info("SABLE_M28_BUFFER_WRITE entityId={} phase=FIRST_TRANSFER "
                            + "consumerClass={} consumerIdentity={} sourceMeshIdentity={} "
                            + "submittedArgument=({},{},{}) destinationInspectionSupported={} "
                            + "destinationKind={}",
                    sample.entityId, consumer.getClass().getName(), System.identityHashCode(consumer),
                    System.identityHashCode(mesh), x, y, z,
                    sample.destination.builder != null, sample.destination.kind);
        }
        CURRENT_VERTEX.remove();
    }

    public static boolean hasCurrentVertex() {
        return CURRENT_VERTEX.get() != null && ACTIVE_SAMPLE.get() != null;
    }

    public static void beforeDestinationWrite(final BufferBuilder builder, final int vertices,
                                              final int byteOffset) {
        if (hasCurrentVertex()) {
            DESTINATION_START.set(new DestinationStart(builder, vertices, byteOffset));
        }
    }

    public static void afterDestinationWrite(final BufferBuilder builder, final int vertices,
                                             final int byteOffset) {
        final DestinationStart start = DESTINATION_START.get();
        DESTINATION_START.remove();
        final Sample sample = ACTIVE_SAMPLE.get();
        final SourceVertex source = CURRENT_VERTEX.get();
        if (start == null || sample == null || source == null || start.builder != builder
                || vertices <= start.vertices || sample.vertexCount >= MAX_VERTICES_PER_SAMPLE) {
            return;
        }
        final BufferBuilderProbeAccessor access = (BufferBuilderProbeAccessor) builder;
        final float actualX = access.sable$getBuffer().getFloat(start.byteOffset);
        final float actualY = access.sable$getBuffer().getFloat(start.byteOffset + Float.BYTES);
        final float actualZ = access.sable$getBuffer().getFloat(start.byteOffset + 2 * Float.BYTES);
        final Vector4f expected = new Vector4f(source.x, source.y, source.z, 1.0F)
                .mul(sample.productionModelMatrix);
        final double error = Math.sqrt(square(expected.x() - actualX)
                + square(expected.y() - actualY) + square(expected.z() - actualZ));
        final String sourceVertexId = sample.entityId + ":" + sample.sourceBufferIdentity + ":"
                + source.meshIdentity + ":" + source.index;
        sample.vertexCount++;
        sample.destinationWrites++;
        sample.distinctLocalVertices.add(source.x + ":" + source.y + ":" + source.z);
        Sable.LOGGER.info("SABLE_M28_ACTUAL_VERTEX entityId={} subLevel={} sample={} "
                        + "sourceVertexId={} capturedBlockState={} renderLayer={} bearingAngle={} "
                        + "sourceBufferIdentity={} templateIdentity={} sourceVertexIndex={} "
                        + "localBakedVertex=({},{},{}) actualBufferTransformAndRendererMatrix={} "
                        + "actualDestinationPosition=({},{},{}) expectedPosition=({},{},{}) error={} "
                        + "destinationBufferBuilderIdentity={} vertexFormat={} "
                        + "destinationVertexCountBefore={} destinationVertexCountAfter={} "
                        + "sourceByteOffset={} destinationByteOffset={} byteWriteAfter={} finite={}",
                sample.entityId, sample.subLevelId, sample.sampleIndex, sourceVertexId,
                sample.capturedBlockState, sample.layer, sample.angle,
                sample.sourceBufferIdentity, source.meshIdentity, source.index,
                source.x, source.y, source.z, sample.productionModelMatrix,
                actualX, actualY, actualZ, expected.x(), expected.y(), expected.z(), error,
                System.identityHashCode(builder), access.sable$getFormat(),
                start.vertices, vertices, source.index * TemplateMesh.BYTE_STRIDE,
                start.byteOffset, byteOffset,
                Float.isFinite(actualX) && Float.isFinite(actualY) && Float.isFinite(actualZ));
        if (sample.destination.kind.equals("SODIUM_BUFFER_BUILDER")) {
            Sable.LOGGER.info("SABLE_M28_SODIUM_VERTEX_WRITE frame={} entityId={} bearingAngle={} "
                            + "sourceMeshIdentity={} sourceVertexIndex={} sourceVertexId={} "
                            + "localBakedVertex=({},{},{}) submittedTransformedPosition=({},{},{}) "
                            + "sodiumBufferBuilderIdentity={} backingBufferBuilderIdentity={} "
                            + "destinationVertexIndex={} destinationByteOffset={} "
                            + "actualStoredPosition=({},{},{}) destinationStride={} destinationVertexFormat={} "
                            + "builderVertexCountBefore={} builderVertexCountAfter={} finite={}",
                    SableM28BatchTrace.currentFrame(), sample.entityId, sample.angle,
                    source.meshIdentity, source.index, sourceVertexId, source.x, source.y, source.z,
                    expected.x(), expected.y(), expected.z(), sample.destination.consumerIdentity,
                    System.identityHashCode(builder), start.vertices, start.byteOffset,
                    actualX, actualY, actualZ, access.sable$getFormat().getVertexSize(),
                    access.sable$getFormat(), start.vertices, vertices,
                    Float.isFinite(actualX) && Float.isFinite(actualY) && Float.isFinite(actualZ));
        }
        final PreviousVertex previous = PREVIOUS_VERTICES.put(sourceVertexId,
                new PreviousVertex(sample.angle, actualX, actualY, actualZ));
        if (previous != null && Math.abs(Mth.wrapDegrees(sample.angle - previous.angle)) >= SAMPLE_ANGLE_DELTA) {
            final double delta = Math.sqrt(square(actualX - previous.x) + square(actualY - previous.y)
                    + square(actualZ - previous.z));
            Sable.LOGGER.info("SABLE_M28_VERTEX_COMPARE sourceVertexId={} angleA={} "
                            + "actualSubmittedPositionA=({},{},{}) angleB={} "
                            + "actualSubmittedPositionB=({},{},{}) delta={} expectedToMove={} "
                            + "sourceLocalVertex=({},{},{})",
                    sourceVertexId, previous.angle, previous.x, previous.y, previous.z,
                    sample.angle, actualX, actualY, actualZ, delta, true,
                    source.x, source.y, source.z);
            if (sample.destination.kind.equals("SODIUM_BUFFER_BUILDER")) {
                Sable.LOGGER.info("SABLE_M28_SODIUM_VERTEX_COMPARE sourceVertexId={} "
                                + "bearingAngleA={} storedPositionA=({},{},{}) bearingAngleB={} "
                                + "storedPositionB=({},{},{}) delta={}",
                        sourceVertexId, previous.angle, previous.x, previous.y, previous.z,
                        sample.angle, actualX, actualY, actualZ, delta);
            }
        }
    }

    public static void end(final VertexConsumer consumer) {
        final boolean identityRangeRecorded = SableM28CpuTargetIdentity.end();
        final Sample sample = ACTIVE_SAMPLE.get();
        if (sample != null) {
            if (Boolean.getBoolean("sable.m28.referenceGeometry") && sample.layer == RenderType.solid()
                    && sample.productionModelMatrix != null) {
                emitReferenceGeometry(sample, consumer);
            }
            final BufferBuilderProbeAccessor access = sample.destination.builder == null ? null
                    : (BufferBuilderProbeAccessor) sample.destination.builder;
            final String writeState = access == null ? "INSPECTION_UNSUPPORTED"
                    : sample.destinationWrites > 0 ? "INSPECTED_WRITTEN" : "INSPECTED_NO_WRITE";
            Sable.LOGGER.info("SABLE_M28_BUFFER_WRITE entityId={} subLevel={} sample={} "
                            + "destinationBufferBuilderIdentity={} vertexCountBefore={} vertexCountAfter={} "
                            + "byteWritePositionBefore={} byteWritePositionAfter={} renderLayer={} "
                            + "actualVertexProbeCount={} distinctLocalVertexCount={} "
                            + "destinationWrites={} destinationInspectionSupported={} "
                            + "geometryWriteState={} phase=RENDER_INTO_COMPLETE",
                    sample.entityId, sample.subLevelId, sample.sampleIndex,
                    sample.destination.builder == null ? "unsupported" : System.identityHashCode(sample.destination.builder),
                    sample.countBefore, access == null ? "wrapped_consumer" : access.sable$getVertices(),
                    sample.byteBefore, access == null ? "wrapped_consumer" : access.sable$getNextElementByte(),
                    sample.layer, sample.vertexCount, sample.distinctLocalVertices.size(),
                    sample.destinationWrites, access != null, writeState);
            if (sample.destination.builder != null && access != null && !identityRangeRecorded) {
                SableM28BatchTrace.noteRange(sample.entityId, sample.layer,
                        sample.destination.builder, sample.countBefore, access.sable$getVertices(), sample.byteBefore,
                        sample.destination.kind, sample.angle, sample.renderCase);
            }
        }
        ACTIVE_SAMPLE.remove();
        CURRENT_VERTEX.remove();
        DESTINATION_START.remove();
        PENDING_LAYER.remove();
    }

    private static void emitReferenceGeometry(final Sample sample, final VertexConsumer consumer) {
        final TextureAtlasSprite sprite = Minecraft.getInstance().getBlockRenderer()
                .getBlockModel(Blocks.WHITE_CONCRETE.defaultBlockState()).getParticleIcon();
        final float[][] points = {
                {0.0F, 0.0F, 0.0F}, {0.75F, 0.0F, 0.0F},
                {0.0F, 0.25F, 0.5F}, {0.0F, 0.0F, 0.0F}
        };
        final float[] us = {sprite.getU0(), sprite.getU1(), sprite.getU0(), sprite.getU0()};
        final float[] vs = {sprite.getV0(), sprite.getV0(), sprite.getV1(), sprite.getV0()};
        final BufferBuilderProbeAccessor access = sample.destination.builder == null ? null
                : (BufferBuilderProbeAccessor) sample.destination.builder;
        final int before = access == null ? -1 : access.sable$getVertices();
        for (int i = 0; i < points.length; i++) {
            final Vector4f point = new Vector4f(points[i][0], points[i][1], points[i][2], 1.0F)
                    .mul(sample.productionModelMatrix);
            consumer.vertex(point.x(), point.y(), point.z(), 0.0F, 1.0F, 0.25F, 1.0F,
                    us[i], vs[i], OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT,
                    0.0F, 1.0F, 0.0F);
        }
        Sable.LOGGER.info("SABLE_M28_REFERENCE_GEOMETRY entityId={} sample={} bearingAngle={} "
                        + "enabled=true localPoints=P0(0,0,0),P1(0.75,0,0),P2(0,0.25,0.5) "
                        + "sourceLayer={} destinationConsumerIdentity={} vertexCountBefore={} "
                        + "vertexCountAfter={} visibleResult=MANUAL_RUNTIME_REQUIRED",
                sample.entityId, sample.sampleIndex, sample.angle, sample.layer,
                System.identityHashCode(consumer), before,
                access == null ? "wrapped_consumer" : access.sable$getVertices());
    }

    private static boolean hasHiddenPlotCoordinate(final Matrix4f matrix) {
        return hasHiddenPlotCoordinate(matrix.m30(), matrix.m31(), matrix.m32());
    }

    private static boolean hasHiddenPlotCoordinate(final double x, final double y, final double z) {
        return Math.abs(x) > 1_000_000 || Math.abs(y) > 1_000_000 || Math.abs(z) > 1_000_000;
    }

    private static double square(final double value) {
        return value * value;
    }

    static BufferBuilder inspectBackingBuilder(final VertexConsumer consumer) {
        return inspectDestination(consumer).builder;
    }

    private static Destination inspectDestination(final VertexConsumer consumer) {
        if (consumer instanceof final BufferBuilder builder) {
            return new Destination(builder, "VANILLA_BUFFER_BUILDER", System.identityHashCode(consumer));
        }
        if (!consumer.getClass().getName()
                .equals("me.jellysquid.mods.sodium.client.render.vertex.buffer.SodiumBufferBuilder")) {
            logUnsupportedDestination(consumer, "unknown_consumer");
            return new Destination(null, "UNSUPPORTED", System.identityHashCode(consumer));
        }
        try {
            final Method method = consumer.getClass().getMethod("getOriginalBufferBuilder");
            final Object value = method.invoke(consumer);
            if (value instanceof final BufferBuilder builder) {
                return new Destination(builder, "SODIUM_BUFFER_BUILDER", System.identityHashCode(consumer));
            }
            logUnsupportedDestination(consumer, "unexpected_backing_type");
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            logUnsupportedDestination(consumer, exception.getClass().getSimpleName());
        }
        return new Destination(null, "SODIUM_INSPECTION_UNAVAILABLE", System.identityHashCode(consumer));
    }

    private static void logUnsupportedDestination(final VertexConsumer consumer, final String reason) {
        if (LOGGED_UNSUPPORTED_DESTINATIONS.add(consumer.getClass().getName() + ":" + reason)) {
            Sable.LOGGER.info("SABLE_M28_DESTINATION_INSPECTION consumerClass={} "
                            + "destinationInspectionSupported=false reason={}",
                    consumer.getClass().getName(), reason);
        }
    }

    private static final class Sample {
        private final int entityId;
        private final String subLevelId;
        private final String renderCase;
        private final int sampleIndex;
        private final float angle;
        private final Matrix4f finalMatrix;
        private Matrix4f productionModelMatrix;
        private final RenderType layer;
        private final int sourceBufferIdentity;
        private final Destination destination;
        private final int countBefore;
        private final int byteBefore;
        private final String capturedBlockState;
        private final Set<String> distinctLocalVertices = new HashSet<>();
        private int vertexCount;
        private int destinationWrites;
        private boolean shadeLogged;

        private Sample(final int entityId, final String subLevelId, final String renderCase, final int sampleIndex,
                       final float angle, final Matrix4f finalMatrix, final RenderType layer,
                       final int sourceBufferIdentity, final Destination destination,
                       final int countBefore, final int byteBefore, final String capturedBlockState) {
            this.entityId = entityId;
            this.subLevelId = subLevelId;
            this.renderCase = renderCase;
            this.sampleIndex = sampleIndex;
            this.angle = angle;
            this.finalMatrix = finalMatrix;
            this.layer = layer;
            this.sourceBufferIdentity = sourceBufferIdentity;
            this.destination = destination;
            this.countBefore = countBefore;
            this.byteBefore = byteBefore;
            this.capturedBlockState = capturedBlockState;
        }
    }

    private record SourceVertex(int index, float x, float y, float z, int meshIdentity) {
    }

    private record DestinationStart(BufferBuilder builder, int vertices, int byteOffset) {
    }

    private record Destination(BufferBuilder builder, String kind, int consumerIdentity) {
    }

    private record PreviousVertex(float angle, float x, float y, float z) {
    }
}
