package dev.ryanhcode.sable.mixin.compatibility.create.contraptions;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockCluster;
import dev.ryanhcode.sable.physics.floating_block.FloatingClusterContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = AbstractContraptionEntity.class, remap = false)
public abstract class AbstractContraptionEntityMixin extends Entity implements KinematicContraption {
    @Unique
    private final Vector3d sable$cachedGlobalPosition = new Vector3d();
    @Unique
    private final Object2ObjectMap<BlockPos, BlockSubLevelLiftProvider.LiftProviderContext> sable$liftProviderContexts =
            new Object2ObjectOpenHashMap<>();
    @Unique
    private final FloatingClusterContainer sable$floatingClusterContainer = new FloatingClusterContainer();
    @Shadow
    protected Contraption contraption;
    @Unique
    private BoundingBox3i sable$localBounds;
    @Unique
    private MassTracker sable$massTracker;
    @Unique
    private boolean sable$initialized = false;
    @Unique
    private boolean sable$removedFromRuntime = false;

    public AbstractContraptionEntityMixin(final EntityType<?> type, final Level level) {
        super(type, level);
    }

    @Shadow
    public abstract Vec3 applyRotation(Vec3 localPos, float partialTicks);

    @Shadow
    public abstract Vec3 getPrevAnchorVec();

    @Shadow
    public abstract Vec3 getAnchorVec();

    @Redirect(method = "moveCollidedEntitiesOnDisassembly",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;toLocalVector(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 sable$toLocalVectorInContainingSubLevel(final AbstractContraptionEntity instance,
                                                         final Vec3 localVec,
                                                         final float partialTicks) {
        final SubLevel subLevel = Sable.HELPER.getContaining(instance);
        return instance.toLocalVector(subLevel != null ? subLevel.logicalPose().transformPositionInverse(localVec) : localVec,
                partialTicks);
    }

    @WrapOperation(method = "moveCollidedEntitiesOnDisassembly",
            at = {
                    @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setPos(DDD)V", remap = true),
                    @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;teleportTo(DDD)V", remap = true)
            })
    private void sable$projectDisassembledEntityOutOfSubLevel(final Entity instance,
                                                              final double x,
                                                              final double y,
                                                              final double z,
                                                              final Operation<Void> original) {
        final Vector3d pos = Sable.HELPER.projectOutOfSubLevel(instance.level(), new Vector3d(x, y, z), new Vector3d());
        original.call(instance, pos.x, pos.y, pos.z);
    }

    @Inject(method = "contraptionInitialize", at = @At("HEAD"))
    private void sable$initializeKinematicContraption(final CallbackInfo ci) {
        if (this.sable$initialized || !(this.level() instanceof final ServerLevel serverLevel)) {
            return;
        }

        this.sable$buildProperties();
        if (this.sable$massTracker == null || this.sable$massTracker.getCenterOfMass() == null) {
            this.sable$initialized = true;
            return;
        }

        this.sable$addToPlot();
        this.sable$addToPipeline(serverLevel);
        this.sable$initialized = true;
        Sable.LOGGER.info("SABLE_M13_CONTRAPTION phase=registered entityId={} class={} containingSubLevel={} mass={} localBounds={}",
                this.getId(), this.getClass().getName(), Sable.HELPER.getContaining(this) != null,
                this.sable$massTracker.getMass(), this.sable$localBounds);
    }

    @Inject(method = {"remove", "m_142687_"}, at = @At("HEAD"), remap = false)
    private void sable$removeKinematicContraption(final Entity.RemovalReason removalReason,
                                                  final CallbackInfo ci) {
        if (this.sable$removedFromRuntime || !(this.level() instanceof final ServerLevel serverLevel)) {
            return;
        }

        this.sable$removedFromRuntime = true;

        final SubLevel subLevel = Sable.HELPER.getContaining(this);
        if (subLevel instanceof final ServerSubLevel serverSubLevel) {
            serverSubLevel.getPlot().removeContraption(this);
        }

        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(serverLevel);
        if (physics != null) {
            physics.getPipeline().remove(this);
        }

        Sable.LOGGER.info("SABLE_M13_CONTRAPTION phase=removed entityId={} class={} reason={} containingSubLevel={}",
                this.getId(), this.getClass().getName(), removalReason, subLevel != null);
    }

    @Unique
    private void sable$buildProperties() {
        if (this.contraption == null || this.contraption.getBlocks().isEmpty()) {
            return;
        }

        for (final Map.Entry<BlockPos, StructureTemplate.StructureBlockInfo> entry : this.contraption.getBlocks().entrySet()) {
            final BlockPos blockPos = entry.getKey();
            final BlockState state = entry.getValue().state();
            if (state.isAir()) {
                continue;
            }

            if (this.sable$localBounds == null) {
                this.sable$localBounds = new BoundingBox3i(blockPos.getX(), blockPos.getY(), blockPos.getZ(),
                        blockPos.getX(), blockPos.getY(), blockPos.getZ());
            }
            this.sable$localBounds.expandTo(blockPos.getX(), blockPos.getY(), blockPos.getZ());

            if (state.getBlock() instanceof final BlockSubLevelLiftProvider liftProvider) {
                this.sable$liftProviderContexts.put(blockPos, new BlockSubLevelLiftProvider.LiftProviderContext(
                        blockPos, state, Vec3.atLowerCornerOf(liftProvider.sable$getNormal(state).getNormal())));
            }
            if (PhysicsBlockPropertyHelper.getFloatingMaterial(state) != null) {
                this.sable$floatingClusterContainer.addFloatingBlock(state,
                        new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
            }
        }

        if (this.sable$localBounds == null) {
            return;
        }

        this.sable$massTracker = MassTracker.build(this.sable$blockGetter(), this.sable$localBounds);
        if (this.sable$massTracker.getCenterOfMass() == null) {
            return;
        }

        final Vector3d translation = this.sable$massTracker.getCenterOfMass()
                .negate(new Vector3d())
                .add(0.5, 0.5, 0.5);
        for (final FloatingBlockCluster cluster : this.sable$floatingClusterContainer.clusters) {
            cluster.getBlockData().translateOrigin(translation);
        }
    }

    @Unique
    private void sable$addToPlot() {
        final SubLevel subLevel = Sable.HELPER.getContaining(this);
        if (subLevel instanceof final ServerSubLevel serverSubLevel) {
            serverSubLevel.getPlot().addContraption(this);
        }
    }

    @Unique
    private void sable$addToPipeline(final ServerLevel serverLevel) {
        SubLevelPhysicsSystem.require(serverLevel).getPipeline().add(this);
    }

    @Override
    public void sable$getLocalBounds(final BoundingBox3i bounds) {
        bounds.set(this.sable$localBounds == null ? BoundingBox3i.EMPTY : this.sable$localBounds);
    }

    @Override
    public BlockGetter sable$blockGetter() {
        return this.contraption.getContraptionWorld();
    }

    @Override
    public MassTracker sable$getMassTracker() {
        return this.sable$massTracker;
    }

    @Override
    public Vector3dc sable$getPosition(final double partialTick) {
        Vec3 localVec = JOMLConversion.toMojang(this.sable$massTracker.getCenterOfMass());
        final Vec3 anchor = this.getPrevAnchorVec().lerp(this.getAnchorVec(), partialTick);
        final Vec3 rotationOffset = VecHelper.getCenterOf(BlockPos.ZERO);
        localVec = localVec.subtract(rotationOffset);
        localVec = this.applyRotation(localVec, (float) partialTick);
        localVec = localVec.add(rotationOffset).add(anchor);
        return JOMLConversion.toJOML(localVec, this.sable$cachedGlobalPosition);
    }

    @Override
    public Quaterniond sable$getOrientation(final double partialTick) {
        final Matrix3d matrix = new Matrix3d();
        final Vector3d tempColumn = new Vector3d();
        for (int i = 0; i < 3; i++) {
            matrix.getColumn(i, tempColumn);
            final Vec3 transformed = this.applyRotation(JOMLConversion.toMojang(tempColumn), (float) partialTick);
            matrix.setColumn(i, transformed.x, transformed.y, transformed.z);
        }
        return matrix.getNormalizedRotation(new Quaterniond());
    }

    @Override
    public Map<BlockPos, BlockSubLevelLiftProvider.LiftProviderContext> sable$liftProviders() {
        return this.sable$liftProviderContexts;
    }

    @Override
    public FloatingClusterContainer sable$getFloatingClusterContainer() {
        return this.sable$floatingClusterContainer;
    }

    @Override
    public boolean sable$shouldCollide() {
        return true;
    }

    @Override
    public boolean sable$isValid() {
        return !this.isRemoved();
    }
}
