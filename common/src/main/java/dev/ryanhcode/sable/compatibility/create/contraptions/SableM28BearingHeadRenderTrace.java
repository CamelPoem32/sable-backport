package dev.ryanhcode.sable.compatibility.create.contraptions;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.contraptions.bearing.IBearingBlockEntity;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/** Client-only M28.13 trace of Create's bearing shaft/top renderer ownership. */
public final class SableM28BearingHeadRenderTrace {
    public static final String PROPERTY = "sable.m28.traceBearingHeadRender";
    private static final int MAX_SAMPLES_PER_BEARING = 16;
    private static final ThreadLocal<RenderSession> ACTIVE = new ThreadLocal<>();
    private static final java.util.Map<BlockPos, Integer> SAMPLES = new java.util.HashMap<>();

    private SableM28BearingHeadRenderTrace() {
    }

    public static boolean isSableContained(final KineticBlockEntity bearing) {
        return bearing.getLevel() != null
                && Sable.HELPER.getContaining(bearing.getLevel(), bearing.getBlockPos()) != null;
    }

    public static boolean visualizationDecision(final KineticBlockEntity bearing, final float partialTick,
                                                final PoseStack poseStack, final boolean original,
                                                final boolean returned) {
        if (!Boolean.getBoolean(PROPERTY) || !(bearing instanceof MechanicalBearingBlockEntity)
                || bearing.getLevel() == null) {
            return returned;
        }
        final SubLevel owner = Sable.HELPER.getContaining(bearing.getLevel(), bearing.getBlockPos());
        if (owner == null || SAMPLES.merge(bearing.getBlockPos().immutable(), 1, Integer::sum) > MAX_SAMPLES_PER_BEARING) {
            return returned;
        }
        final RenderSession session = new RenderSession(bearing, owner, partialTick, "BearingRenderer",
                new Matrix4f(poseStack.last().pose()));
        session.visualizationSupportedOriginal = original;
        session.visualizationSupportedReturned = returned;
        ACTIVE.set(session);
        if (returned) {
            finish(poseStack);
        }
        return returned;
    }

    public static void partialSelected(final PartialModel partial) {
        final RenderSession session = ACTIVE.get();
        if (session == null) {
            return;
        }
        session.partialName = partial == AllPartialModels.BEARING_TOP_WOODEN
                ? "BEARING_TOP_WOODEN" : partial == AllPartialModels.BEARING_TOP ? "BEARING_TOP" : partial.toString();
    }

    public static void partialRendered(final PoseStack poseStack) {
        final RenderSession session = ACTIVE.get();
        if (session != null) {
            session.partialRendered = true;
            session.poseBeforeEmission = new Matrix4f(poseStack.last().pose());
        }
    }

    public static void finish(final PoseStack poseStack) {
        final RenderSession session = ACTIVE.get();
        if (session == null) {
            return;
        }
        try {
            final KineticBlockEntity bearing = session.bearing;
            final MechanicalBearingBlockEntity mechanical = (MechanicalBearingBlockEntity) bearing;
            final ControlledContraptionEntity moved = mechanical.getMovedContraption();
            final Direction facing = bearing.getBlockState().getValue(BearingBlock.FACING);
            final float angle = ((IBearingBlockEntity) bearing).getInterpolatedAngle(session.partialTick - 1.0F);
            Sable.LOGGER.info("SABLE_M33_BEARING_RENDER subLevel={} bearingLocalPos={} bearingRawPos={} "
                            + "bearingState={} facing={} running={} speed={} angle={} contraptionPresent={} "
                            + "rendererClass={} partialName={} partialRendered={} poseTranslationBefore={} "
                            + "poseTranslationAtEmission={} poseTranslationAfter={} visualizationSupportedOriginal={} "
                            + "visualizationSupportedReturned={} renderOwner={} topOwner={}",
                    session.owner.getUniqueId(), local(session.owner, bearing.getBlockPos()), bearing.getBlockPos(),
                    bearing.getBlockState(), facing, mechanical.isRunning(), bearing.getSpeed(), angle, moved != null,
                    session.rendererClass, session.partialName, session.partialRendered,
                    translation(session.poseBefore), translation(session.poseBeforeEmission),
                    translation(poseStack.last().pose()), session.visualizationSupportedOriginal,
                    session.visualizationSupportedReturned,
                    session.visualizationSupportedReturned ? "FLYWHEEL_VISUAL" : "BEARING_RENDERER",
                    session.visualizationSupportedReturned ? "BearingVisual.topInstance" : "BearingRenderer.BEARING_TOP");
        } finally {
            ACTIVE.remove();
        }
    }

    private static BlockPos local(final SubLevel owner, final BlockPos raw) {
        return raw.subtract(owner.getPlot().getCenterBlock());
    }

    private static String translation(final @Nullable Matrix4f matrix) {
        return matrix == null ? "unreached" : "(" + matrix.m30() + "," + matrix.m31() + "," + matrix.m32() + ")";
    }

    private static final class RenderSession {
        private final KineticBlockEntity bearing;
        private final SubLevel owner;
        private final float partialTick;
        private final String rendererClass;
        private final Matrix4f poseBefore;
        private boolean visualizationSupportedOriginal;
        private boolean visualizationSupportedReturned;
        private String partialName = "NOT_SELECTED";
        private boolean partialRendered;
        private @Nullable Matrix4f poseBeforeEmission;

        private RenderSession(final KineticBlockEntity bearing, final SubLevel owner, final float partialTick,
                              final String rendererClass, final Matrix4f poseBefore) {
            this.bearing = bearing;
            this.owner = owner;
            this.partialTick = partialTick;
            this.rendererClass = rendererClass;
            this.poseBefore = poseBefore;
        }
    }
}
