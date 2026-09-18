package dev.ryanhcode.sable.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.mixin.m28.ClientLevelEntityGetterAccessor;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.lang.StackWalker.StackFrame;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Bounded M28.7 diagnostics for identifying every render owner of a controlled sail. */
public final class SableM28VisualOwnershipTrace {
    public static final String TRACE_PROPERTY = "sable.m28.visualOwnershipTrace";
    public static final String SUPPRESS_VANILLA_PROPERTY = "sable.m28.suppressVanillaTargetContraption";

    private static final boolean TRACE = Boolean.getBoolean(TRACE_PROPERTY);
    private static final boolean SUPPRESS_VANILLA = Boolean.getBoolean(SUPPRESS_VANILLA_PROPERTY);
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final ThreadLocal<Deque<Owner>> OWNER_STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<RendererInvocation>> RENDERER_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<Integer, RenderCounts> FRAME_COUNTS = new LinkedHashMap<>();
    private static final Map<String, AtomicInteger> SAIL_DRAW_COUNTS = new ConcurrentHashMap<>();
    private static final java.util.Set<String> OFF_THREAD_FIELDS = ConcurrentHashMap.newKeySet();

    private static volatile long frame;
    private static volatile boolean dispatcherProbeApplied;
    private static volatile boolean contraptionRendererProbeApplied;

    private SableM28VisualOwnershipTrace() {
    }

    public enum Owner {
        SABLE_STATIC,
        SABLE_FORGE_STAGE_BRIDGE,
        SABLE_ENTITY_PHASE_BRIDGE,
        VANILLA_LEVEL_ENTITY_PASS,
        OTHER,
        UNATTRIBUTED
    }

    public static void beginFrame(final long currentFrame) {
        frame = currentFrame;
        FRAME_COUNTS.clear();
        SAIL_DRAW_COUNTS.clear();
        OWNER_STACK.get().clear();
        RENDERER_STACK.get().clear();
        if (TRACE && (currentFrame <= 5 || currentFrame % 200 == 0)) {
            Sable.LOGGER.info("SABLE_M28_GLOBAL_PROBE_APPLIED frame={} entityRenderDispatcher={} "
                            + "contraptionEntityRenderer={}",
                    currentFrame, dispatcherProbeApplied, contraptionRendererProbeApplied);
        }
    }

    public static long currentFrame() {
        return frame;
    }

    public static boolean markDispatcherProbeApplied() {
        dispatcherProbeApplied = true;
        return true;
    }

    public static boolean markContraptionRendererProbeApplied() {
        contraptionRendererProbeApplied = true;
        return true;
    }

    public static void endFrame() {
        if (!TRACE) {
            return;
        }
        for (final RenderCounts counts : FRAME_COUNTS.values()) {
            Sable.LOGGER.info("SABLE_M28_ENTITY_RENDER_COUNT frame={} entityId={} "
                            + "dispatcherInvocationCount={} contraptionRendererInvocationCount={} "
                            + "knownSableBridgeInvocationCount={} vanillaEntityPassInvocationCount={} "
                            + "sableEntityPhaseInvocationCount={} otherInvocationCount={}",
                    frame, counts.entityId, counts.dispatcherInvocations, counts.rendererInvocations,
                    counts.forgeBridgeInvocations, counts.vanillaInvocations,
                    counts.entityPhaseInvocations, counts.otherInvocations);
        }
        if (frame <= 5 || frame % 20 == 0) {
            enumerateEntityOwnership();
        }
    }

    public static Scope enter(final Owner owner) {
        if (!isActive()) {
            return Scope.INACTIVE;
        }
        OWNER_STACK.get().push(owner);
        return new Scope(owner, true);
    }

    public static Owner currentOwner() {
        final Deque<Owner> stack = OWNER_STACK.get();
        return stack.isEmpty() ? classifyCaller() : stack.peek();
    }

    public static boolean beginDispatcher(final Entity entity,
                                          final double x, final double y, final double z,
                                          final float partialTick, final PoseStack poseStack,
                                          final MultiBufferSource bufferSource) {
        if (!(entity instanceof final AbstractContraptionEntity contraption) || !isActive()) {
            return false;
        }
        final Owner owner = currentOwner();
        final SubLevel containing = SableCreateContraptionContext.getContainingSubLevel(contraption);
        final EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        final RenderCounts counts = FRAME_COUNTS.computeIfAbsent(
                System.identityHashCode(entity), ignored -> new RenderCounts(entity.getId()));
        counts.dispatcherInvocations++;
        counts.record(owner);
        if (TRACE) {
            final Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Sable.LOGGER.info("SABLE_M28_ENTITY_DISPATCH frame={} entityId={} entityUuid={} entityIdentity={} "
                            + "entityClass={} entityLevelClass={} entityLevelIdentity={} containingSableSubLevel={} "
                            + "rawEntityPosition={} renderXYZ=({},{},{}) cameraPosition={} poseMatrix={} "
                            + "rendererClass={} bufferSourceClass={} bufferSourceIdentity={} currentRenderType={} "
                            + "framebuffer={} shader={} bearingAngle={} currentM28RenderContextOwner={} "
                            + "callSiteClassification={} stackFingerprint={}",
                    frame, entity.getId(), entity.getUUID(), System.identityHashCode(entity),
                    entity.getClass().getName(), entity.level().getClass().getName(),
                    System.identityHashCode(entity.level()), containingId(containing), entity.position(),
                    x, y, z, camera, poseStack.last().pose(), renderer.getClass().getName(),
                    bufferSource.getClass().getName(), System.identityHashCode(bufferSource),
                    "UNAVAILABLE_AT_DISPATCH", currentFramebuffer(), shaderName(), angle(contraption, partialTick),
                    owner, owner, stackFingerprint());
        }
        final boolean suppress = SUPPRESS_VANILLA
                && owner == Owner.VANILLA_LEVEL_ENTITY_PASS
                && contraption instanceof ControlledContraptionEntity
                && containing != null;
        if (suppress) {
            Sable.LOGGER.info("SABLE_M28_ENTITY_DISPATCH frame={} entityId={} event=SUPPRESSED "
                            + "suppressionFlag={} callSiteClassification={} containingSableSubLevel={}",
                    frame, entity.getId(), SUPPRESS_VANILLA_PROPERTY, owner, containingId(containing));
        }
        return suppress;
    }

    public static void beginContraptionRenderer(final AbstractContraptionEntity entity,
                                                final float partialTick, final PoseStack poseStack,
                                                final MultiBufferSource bufferSource) {
        if (!isActive()) {
            return;
        }
        final Owner owner = currentOwner();
        final RenderCounts counts = FRAME_COUNTS.computeIfAbsent(
                System.identityHashCode(entity), ignored -> new RenderCounts(entity.getId()));
        counts.rendererInvocations++;
        final int invocation = counts.rendererInvocations;
        final RendererInvocation rendererInvocation = new RendererInvocation(entity, owner, invocation);
        RENDERER_STACK.get().push(rendererInvocation);
        if (TRACE) {
            final SubLevel containing = SableCreateContraptionContext.getContainingSubLevel(entity);
            Sable.LOGGER.info("SABLE_M28_CONTRAPTION_RENDER_GLOBAL phase=HEAD frame={} entityId={} "
                            + "entityIdentity={} bearingAngle={} partialTick={} rendererInvocationCount={} "
                            + "callerClassification={} poseMatrix={} bufferSourceClass={} bufferSourceIdentity={} "
                            + "framebuffer={} shader={} capturedBlockCount={} suppressionState={} "
                            + "geometryEmission=false stackFingerprint={}",
                    frame, entity.getId(), System.identityHashCode(entity), angle(entity, partialTick), partialTick,
                    invocation, owner, poseStack.last().pose(), bufferSource.getClass().getName(),
                    System.identityHashCode(bufferSource), currentFramebuffer(), shaderName(),
                    entity.getContraption() == null ? 0 : entity.getContraption().getBlocks().size(),
                    suppressionState(entity, containing, owner), stackFingerprint());
        }
    }

    public static void markContraptionGeometryEmission() {
        final Deque<RendererInvocation> stack = RENDERER_STACK.get();
        if (!stack.isEmpty()) {
            stack.peek().geometryEmitted = true;
        }
    }

    public static void endContraptionRenderer(final AbstractContraptionEntity entity, final float partialTick,
                                              final PoseStack poseStack, final MultiBufferSource bufferSource) {
        if (!isActive()) {
            return;
        }
        final Deque<RendererInvocation> stack = RENDERER_STACK.get();
        final RendererInvocation invocation = stack.isEmpty()
                ? new RendererInvocation(entity, currentOwner(), -1) : stack.pop();
        if (TRACE) {
            final SubLevel containing = SableCreateContraptionContext.getContainingSubLevel(entity);
            Sable.LOGGER.info("SABLE_M28_CONTRAPTION_RENDER_GLOBAL phase=RETURN frame={} entityId={} "
                            + "entityIdentity={} bearingAngle={} partialTick={} rendererInvocationCount={} "
                            + "callerClassification={} poseMatrix={} bufferSourceClass={} bufferSourceIdentity={} "
                            + "framebuffer={} shader={} capturedBlockCount={} suppressionState={} "
                            + "geometryEmission={}",
                    frame, entity.getId(), System.identityHashCode(entity), angle(entity, partialTick), partialTick,
                    invocation.invocationIndex, invocation.owner, poseStack.last().pose(),
                    bufferSource.getClass().getName(), System.identityHashCode(bufferSource), currentFramebuffer(),
                    shaderName(), entity.getContraption() == null ? 0 : entity.getContraption().getBlocks().size(),
                    suppressionState(entity, containing, invocation.owner), invocation.geometryEmitted);
        }
    }

    public static @Nullable AbstractContraptionEntity currentContraption() {
        final Deque<RendererInvocation> stack = RENDERER_STACK.get();
        return stack.isEmpty() ? null : stack.peek().entity;
    }

    public static void logSailModelDraw(final String api, final BlockState state, @Nullable final BlockPos pos,
                                        @Nullable final BlockAndTintGetter level, final PoseStack.Pose pose,
                                        final Object consumer, @Nullable final RenderType renderType) {
        if (!TRACE || !"simulated:white_symmetric_sail".equals(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())) {
            return;
        }
        final AbstractContraptionEntity enclosing = currentContraption();
        final SubLevel containing = enclosing == null ? null
                : SableCreateContraptionContext.getContainingSubLevel(enclosing);
        final Owner owner = currentOwner();
        final String countKey = frame + ":" + (enclosing == null ? "none" : enclosing.getId());
        final int invocation = SAIL_DRAW_COUNTS.computeIfAbsent(countKey, ignored -> new AtomicInteger())
                .incrementAndGet();
        Sable.LOGGER.info("SABLE_M28_SAIL_MODEL_DRAW frame={} api={} blockState={} sourceBlockPos={} "
                        + "levelClass={} levelIdentity={} poseMatrix={} renderType={} consumerClass={} "
                        + "consumerIdentity={} framebuffer={} shader={} callerStackFingerprint={} "
                        + "enclosingContraptionEntity={} enclosingSableSubLevel={} renderContextOwner={} "
                        + "drawInvocationIndex={}",
                frame, api, state, pos == null ? "UNAVAILABLE" : pos,
                level == null ? "UNAVAILABLE" : level.getClass().getName(),
                level == null ? -1 : System.identityHashCode(level), pose.pose(),
                renderType == null ? "UNAVAILABLE" : renderType,
                consumer.getClass().getName(), System.identityHashCode(consumer), currentFramebuffer(), shaderName(),
                stackFingerprint(), enclosing == null ? "none" : enclosing.getId(), containingId(containing),
                owner, invocation);
    }

    public static String stackFingerprint() {
        return STACK_WALKER.walk(stream -> stream
                .filter(SableM28VisualOwnershipTrace::isUsefulFrame)
                .limit(12)
                .map(SableM28VisualOwnershipTrace::formatFrame)
                .collect(Collectors.joining(" <- ")));
    }

    private static boolean isActive() {
        return TRACE || SUPPRESS_VANILLA;
    }

    private static Owner classifyCaller() {
        return STACK_WALKER.walk(stream -> {
            final List<StackFrame> frames = stream.limit(40).toList();
            if (contains(frames, "SableForgeCreateContraptionRenderBridge", "renderEntityPhase")) {
                return Owner.SABLE_ENTITY_PHASE_BRIDGE;
            }
            if (contains(frames, "SableForgeCreateContraptionRenderBridge", "render")) {
                return Owner.SABLE_FORGE_STAGE_BRIDGE;
            }
            if (contains(frames, "net.minecraft.client.renderer.LevelRenderer", "renderEntity")) {
                return Owner.VANILLA_LEVEL_ENTITY_PASS;
            }
            return Owner.OTHER;
        });
    }

    private static boolean contains(final List<StackFrame> frames, final String classPart, final String method) {
        return frames.stream().anyMatch(frame -> frame.getClassName().contains(classPart)
                && frame.getMethodName().equals(method));
    }

    private static boolean isUsefulFrame(final StackFrame frame) {
        final String name = frame.getClassName();
        return !name.equals(SableM28VisualOwnershipTrace.class.getName())
                && !name.startsWith("org.spongepowered.asm.mixin")
                && !name.startsWith("com.llamalad7.mixinextras")
                && !name.startsWith("java.lang.invoke");
    }

    private static String formatFrame(final StackFrame frame) {
        return frame.getClassName() + "#" + frame.getMethodName() + ":" + frame.getLineNumber();
    }

    private static float angle(final AbstractContraptionEntity entity, final float partialTick) {
        return entity instanceof final ControlledContraptionEntity controlled
                ? controlled.getAngle(partialTick) : Float.NaN;
    }

    private static String containingId(@Nullable final SubLevel containing) {
        return containing == null ? "none" : containing.getUniqueId().toString();
    }

    private static String suppressionState(final AbstractContraptionEntity entity,
                                           @Nullable final SubLevel containing, final Owner owner) {
        if (Boolean.getBoolean("sable.m28.suppressDynamicContraption") && containing != null) {
            return "KNOWN_BRIDGE_SUPPRESSION_ENABLED";
        }
        if (SUPPRESS_VANILLA && containing != null && owner == Owner.VANILLA_LEVEL_ENTITY_PASS
                && entity instanceof ControlledContraptionEntity) {
            return "VANILLA_TARGET_SUPPRESSION_ENABLED";
        }
        return "NONE";
    }

    private static String shaderName() {
        if (!RenderSystem.isOnRenderThread()) {
            logOffRenderThread("shader");
            return "UNAVAILABLE_OFF_RENDER_THREAD";
        }
        final ShaderInstance shader = RenderSystem.getShader();
        return shader == null ? "none" : shader.getName() + "@" + System.identityHashCode(shader);
    }

    private static String currentFramebuffer() {
        if (!RenderSystem.isOnRenderThread()) {
            logOffRenderThread("framebuffer");
            return "UNAVAILABLE_OFF_RENDER_THREAD";
        }
        return Integer.toString(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
    }

    private static void logOffRenderThread(final String skippedField) {
        final String threadName = Thread.currentThread().getName();
        if (OFF_THREAD_FIELDS.add(threadName + ':' + skippedField)) {
            Sable.LOGGER.info("SABLE_M28_DIAGNOSTIC_THREAD threadName={} renderThread=false "
                            + "skippedField={} value=UNAVAILABLE_OFF_RENDER_THREAD",
                    threadName, skippedField);
        }
    }

    private static void enumerateEntityOwnership() {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        final List<Occurrence> occurrences = new ArrayList<>();
        final Iterable<Entity> renderIterable = level.entitiesForRendering();
        addOccurrences(occurrences, "PARENT_ENTITIES_FOR_RENDERING", System.identityHashCode(renderIterable),
                renderIterable, null);
        addOccurrences(occurrences, "SABLE_FORGE_BRIDGE_SOURCE", System.identityHashCode(renderIterable),
                renderIterable, null);

        final LevelEntityGetter<Entity> inclusiveGetter =
                ((ClientLevelEntityGetterAccessor) (Object) level).sable$invokeGetEntities();
        addOccurrences(occurrences, "SUBLEVEL_INCLUSIVE_GET_ALL", System.identityHashCode(inclusiveGetter),
                inclusiveGetter.getAll(), null);

        final ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            for (final ClientSubLevel subLevel : container.getAllSubLevels()) {
                addOccurrences(occurrences, "OWNING_SABLE_FILTERED_VIEW", System.identityHashCode(subLevel),
                        inclusiveGetter.getAll(), subLevel);
            }
        }

        final IdentityHashMap<Entity, Integer> identityOccurrences = new IdentityHashMap<>();
        for (final Occurrence occurrence : occurrences) {
            identityOccurrences.merge(occurrence.entity, 1, Integer::sum);
        }
        for (final Occurrence occurrence : occurrences) {
            final boolean differentIdIdentity = occurrences.stream().anyMatch(other -> other.entity.getId() == occurrence.entity.getId()
                    && other.entity != occurrence.entity);
            final boolean differentUuidIdentity = occurrences.stream().anyMatch(other -> other.entity.getUUID().equals(occurrence.entity.getUUID())
                    && other.entity != occurrence.entity);
            Sable.LOGGER.info("SABLE_M28_ENTITY_REGISTRY_OWNERSHIP frame={} entityId={} UUID={} "
                            + "identityHashCode={} collectionOwner={} collectionIdentity={} rawPosition={} "
                            + "containingSubLevel={} duplicateIdentityPresent={} duplicateIdPresent={} "
                            + "duplicateUuidPresent={} viewAliasOfParentCollection={}",
                    frame, occurrence.entity.getId(), occurrence.entity.getUUID(),
                    System.identityHashCode(occurrence.entity), occurrence.owner, occurrence.collectionIdentity,
                    occurrence.entity.position(), containingId(occurrence.containing),
                    identityOccurrences.getOrDefault(occurrence.entity, 0) > 1,
                    differentIdIdentity, differentUuidIdentity,
                    !"PARENT_ENTITIES_FOR_RENDERING".equals(occurrence.owner));
        }
    }

    private static void addOccurrences(final List<Occurrence> target, final String owner,
                                       final int collectionIdentity, final Iterable<Entity> entities,
                                       @Nullable final ClientSubLevel requiredSubLevel) {
        for (final Entity entity : entities) {
            if (!(entity instanceof final AbstractContraptionEntity contraption)) {
                continue;
            }
            final SubLevel containing = SableCreateContraptionContext.getContainingSubLevel(contraption);
            if (containing == null || requiredSubLevel != null && containing != requiredSubLevel) {
                continue;
            }
            target.add(new Occurrence(entity, owner, collectionIdentity, containing));
        }
    }

    public static final class Scope implements AutoCloseable {
        private static final Scope INACTIVE = new Scope(Owner.UNATTRIBUTED, false);
        private final Owner owner;
        private final boolean active;
        private boolean closed;

        private Scope(final Owner owner, final boolean active) {
            this.owner = owner;
            this.active = active;
        }

        @Override
        public void close() {
            if (!this.active || this.closed) {
                return;
            }
            this.closed = true;
            final Deque<Owner> stack = OWNER_STACK.get();
            if (!stack.isEmpty() && stack.peek() == this.owner) {
                stack.pop();
            } else {
                stack.removeFirstOccurrence(this.owner);
            }
        }
    }

    private static final class RenderCounts {
        private final int entityId;
        private int dispatcherInvocations;
        private int rendererInvocations;
        private int forgeBridgeInvocations;
        private int vanillaInvocations;
        private int entityPhaseInvocations;
        private int otherInvocations;

        private RenderCounts(final int entityId) {
            this.entityId = entityId;
        }

        private void record(final Owner owner) {
            switch (owner) {
                case SABLE_FORGE_STAGE_BRIDGE -> this.forgeBridgeInvocations++;
                case VANILLA_LEVEL_ENTITY_PASS -> this.vanillaInvocations++;
                case SABLE_ENTITY_PHASE_BRIDGE -> this.entityPhaseInvocations++;
                default -> this.otherInvocations++;
            }
        }
    }

    private static final class RendererInvocation {
        private final AbstractContraptionEntity entity;
        private final Owner owner;
        private final int invocationIndex;
        private boolean geometryEmitted;

        private RendererInvocation(final AbstractContraptionEntity entity, final Owner owner,
                                   final int invocationIndex) {
            this.entity = entity;
            this.owner = owner;
            this.invocationIndex = invocationIndex;
        }
    }

    private record Occurrence(Entity entity, String owner, int collectionIdentity, SubLevel containing) {
    }
}
