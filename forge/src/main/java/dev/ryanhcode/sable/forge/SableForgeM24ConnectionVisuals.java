package dev.ryanhcode.sable.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSubLevelRenderTransforms;
import dev.simulated_team.simulated.content.blocks.m24.M24Family;
import dev.simulated_team.simulated.content.blocks.m24.M24PhysicalBlockEntity;
import dev.simulated_team.simulated.content.blocks.m24.M24TorsionSpringBlock;
import dev.simulated_team.simulated.content.blocks.m24.M24TorsionSpringBlockEntity;
import dev.simulated_team.simulated.index.SimulatedBlockEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;

final class SableForgeM24ConnectionVisuals {

    private SableForgeM24ConnectionVisuals() {
    }

    static void register(final IEventBus modBus) {
        modBus.<EntityRenderersEvent.RegisterRenderers>addListener(SableForgeM24ConnectionVisuals::registerRenderers);
    }

    private static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SimulatedBlockEntityTypes.SWIVEL_BEARING.get(), M24ConnectionRenderer::new);
        event.registerBlockEntityRenderer(SimulatedBlockEntityTypes.SWIVEL_BEARING_LINK_BLOCK.get(), M24ConnectionRenderer::new);
        event.registerBlockEntityRenderer(SimulatedBlockEntityTypes.ROPE_CONNECTOR.get(), M24ConnectionRenderer::new);
        event.registerBlockEntityRenderer(SimulatedBlockEntityTypes.ROPE_WINCH.get(), M24ConnectionRenderer::new);
        event.registerBlockEntityRenderer(SimulatedBlockEntityTypes.DOCKING_CONNECTOR.get(), M24ConnectionRenderer::new);
        event.registerBlockEntityRenderer(SimulatedBlockEntityTypes.PAIRED_DOCKING_CONNECTOR.get(), M24ConnectionRenderer::new);
        event.registerBlockEntityRenderer(SimulatedBlockEntityTypes.TORSION_SPRING.get(), M24TorsionRenderer::new);
    }

    private static final class M24TorsionRenderer extends KineticBlockEntityRenderer<M24TorsionSpringBlockEntity> {
        private static int loggedBakeGeneration = -1;

        private M24TorsionRenderer(final BlockEntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        protected void renderSafe(final M24TorsionSpringBlockEntity blockEntity, final float partialTick,
                                  final PoseStack poseStack, final MultiBufferSource buffer,
                                  final int packedLight, final int packedOverlay) {
            logRenderState(blockEntity);
            super.renderSafe(blockEntity, partialTick, poseStack, buffer, packedLight, packedOverlay);
            final Direction facing = blockEntity.getBlockState().getValue(M24TorsionSpringBlock.FACING);
            final SuperByteBuffer spring = CachedBuffers.partial(
                    SableForgeM24PartialModels.TORSION_SPRING, blockEntity.getBlockState());
            kineticRotationTransform(spring, blockEntity, facing.getAxis(),
                    Mth.DEG_TO_RAD * blockEntity.simulated$getInterpolatedTorsionAngle(partialTick), packedLight);
            if (facing.getAxis().isHorizontal()) {
                spring.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())), Direction.UP);
            }
            spring.rotateCentered(AngleHelper.rad(-90.0F - AngleHelper.verticalAngle(facing)), Direction.EAST);
            spring.renderInto(poseStack, buffer.getBuffer(RenderType.solid()));

            final SuperByteBuffer output = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF,
                    blockEntity.getBlockState(), facing);
            kineticRotationTransform(output, blockEntity.getExtraKinetics(), facing.getAxis(),
                    getAngleForBe(blockEntity.getExtraKinetics(), blockEntity.getBlockPos(), facing.getAxis()),
                    packedLight);
            output.renderInto(poseStack, buffer.getBuffer(RenderType.solid()));
        }

        private static void logRenderState(final M24TorsionSpringBlockEntity blockEntity) {
            final int generation = SableForgeM24PartialModels.bakeGeneration();
            if (loggedBakeGeneration == generation) {
                return;
            }
            loggedBakeGeneration = generation;
            final boolean visualizationSupported = blockEntity.getLevel() != null
                    && VisualizationManager.supportsVisualization(blockEntity.getLevel());
            Sable.LOGGER.info("SABLE_M24_TORSION_RENDER rendererClass={} visualizationSupported={} usingBerFallback=true modelResource={} modelRegisteredBeforeBake={} bakedModelPresent={} missingModel={} spriteResource={} spriteAtlasLocation={} spriteMissing={} renderType={}",
                    M24TorsionRenderer.class.getName(),
                    visualizationSupported,
                    SableForgeM24PartialModels.TORSION_SPRING_LOCATION,
                    SableForgeM24PartialModels.registeredBeforeBake(),
                    SableForgeM24PartialModels.bakedModelPresent(),
                    SableForgeM24PartialModels.missingModel(),
                    SableForgeM24PartialModels.particleSprite(),
                    SableForgeM24PartialModels.spriteAtlas(),
                    SableForgeM24PartialModels.missingSprite(),
                    RenderType.solid());
        }

        @Override
        protected SuperByteBuffer getRotatedModel(final M24TorsionSpringBlockEntity blockEntity,
                                                   final BlockState state) {
            return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state,
                    state.getValue(M24TorsionSpringBlock.FACING).getOpposite());
        }
    }

    private static final class M24ConnectionRenderer implements BlockEntityRenderer<M24PhysicalBlockEntity> {

        private M24ConnectionRenderer(final BlockEntityRendererProvider.Context context) {
        }

        @Override
        public boolean shouldRenderOffScreen(final M24PhysicalBlockEntity blockEntity) {
            return true;
        }

        @Override
        public void render(final M24PhysicalBlockEntity blockEntity, final float partialTick,
                           final PoseStack poseStack, final MultiBufferSource buffer,
                           final int packedLight, final int packedOverlay) {
            if (!blockEntity.simulated$isController()) {
                return;
            }
            final M24Family family = blockEntity.family();
            if (family == M24Family.TORSION_SPRING || family.isSensorOrControl()) {
                return;
            }
            final ClientLevel level = Minecraft.getInstance().level;
            final BlockPos partnerPos = blockEntity.simulated$getPartnerPos();
            final UUID partnerSableId = blockEntity.simulated$getPartnerSableId();
            if (level == null || partnerPos == null || partnerSableId == null) {
                return;
            }
            final ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) {
                return;
            }
            final ClientSubLevel owner = findOwner(container, blockEntity.getBlockPos());
            final ClientSubLevel partner = findById(container, partnerSableId);
            if (owner == null || partner == null || owner == partner) {
                return;
            }
            final BlockState ownerState = blockEntity.getBlockState();
            final BlockState partnerState = level.getBlockState(partnerPos);
            final Vector3d ownerEndpointRaw = blockEntity.simulated$getEndpointRaw(blockEntity.getBlockPos(), ownerState);
            final Vector3d partnerEndpointRaw = blockEntity.simulated$getEndpointRaw(partnerPos, partnerState);
            final Pose3dc ownerPose = owner.renderPose(partialTick);
            final Pose3dc partnerPose = partner.renderPose(partialTick);
            final Vector3d ownerEndpointVisible = ownerPose.transformPosition(ownerEndpointRaw, new Vector3d());
            final Vector3d partnerEndpointVisible = partnerPose.transformPosition(partnerEndpointRaw, new Vector3d());
            final Vector3d ownerBlockVisible = VanillaSubLevelRenderTransforms.blockWorldPosition(
                    ownerPose, blockEntity.getBlockPos(), new Vector3d());
            final Quaterniond inverseOwnerRotation = new Quaterniond(ownerPose.orientation()).conjugate();
            final Vector3d localA = inverseOwnerRotation.transform(ownerEndpointVisible.sub(ownerBlockVisible, new Vector3d()));
            final Vector3d localB = inverseOwnerRotation.transform(partnerEndpointVisible.sub(ownerBlockVisible, new Vector3d()));
            if (!finite(localA) || !finite(localB)) {
                return;
            }

            final float[] color = color(family);
            final VertexConsumer line = buffer.getBuffer(RenderType.lines());
            drawLine(poseStack, line, localA, localB, color[0], color[1], color[2], color[3]);
            if (family == M24Family.SWIVEL_BEARING) {
                final Vector3d axisEnd = new Vector3d(localA).add(new Vector3d(0.0D, 0.0D, 0.75D));
                drawLine(poseStack, line, localA, axisEnd, 0.4F, 0.9F, 1.0F, 1.0F);
            }
        }

        private static void drawLine(final PoseStack poseStack, final VertexConsumer line,
                                     final Vector3d a, final Vector3d b,
                                     final float red, final float green, final float blue, final float alpha) {
            final Matrix4f pose = poseStack.last().pose();
            final Matrix3f normal = poseStack.last().normal();
            final Vector3d direction = b.sub(a, new Vector3d());
            if (direction.lengthSquared() <= 1.0E-8D) {
                direction.set(0.0D, 1.0D, 0.0D);
            } else {
                direction.normalize();
            }
            line.vertex(pose, (float) a.x, (float) a.y, (float) a.z)
                    .color(red, green, blue, alpha)
                    .normal(normal, (float) direction.x, (float) direction.y, (float) direction.z)
                    .endVertex();
            line.vertex(pose, (float) b.x, (float) b.y, (float) b.z)
                    .color(red, green, blue, alpha)
                    .normal(normal, (float) direction.x, (float) direction.y, (float) direction.z)
                    .endVertex();
        }

        private static @Nullable ClientSubLevel findOwner(final ClientSubLevelContainer container, final BlockPos rawPos) {
            for (final ClientSubLevel subLevel : container.getAllSubLevels()) {
                if (subLevel.getPlot().getBoundingBox().contains(rawPos.getX(), rawPos.getY(), rawPos.getZ())) {
                    return subLevel;
                }
            }
            return null;
        }

        private static @Nullable ClientSubLevel findById(final ClientSubLevelContainer container, final UUID uuid) {
            for (final ClientSubLevel subLevel : container.getAllSubLevels()) {
                if (subLevel.getUniqueId().equals(uuid)) {
                    return subLevel;
                }
            }
            return null;
        }

        private static boolean finite(final Vector3d vector) {
            return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
        }

        private static float[] color(final M24Family family) {
            return switch (family) {
                case SWIVEL_BEARING -> new float[]{0.35F, 0.85F, 1.0F, 1.0F};
                case ROPE_CONNECTOR, ROPE_WINCH -> new float[]{0.9F, 0.72F, 0.42F, 1.0F};
                case DOCKING_CONNECTOR, PAIRED_DOCKING_CONNECTOR -> new float[]{0.55F, 1.0F, 0.55F, 1.0F};
                default -> new float[]{1.0F, 1.0F, 1.0F, 1.0F};
            };
        }
    }
}
