package dev.ryanhcode.sable.mixin.compatibility.create.contraptions;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.gantry.GantryContraption;
import com.simibubi.create.content.contraptions.gantry.GantryContraptionEntity;
import com.simibubi.create.content.kinetics.gantry.GantryShaftBlock;
import com.simibubi.create.content.kinetics.gantry.GantryShaftBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionControllerLookup;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SubLevelBlockStateLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ports Create 6.0.9's GantryContraptionEntity floating-point fix for Sable-contained
 * Create 6.0.8 gantries. Normal-world gantries keep Create's original implementation.
 */
@Mixin(value = GantryContraptionEntity.class, remap = false)
public abstract class GantryContraptionEntityMixin extends AbstractContraptionEntity {
    @Unique
    private static final Map<String, Integer> SABLE$GANTRY_PREDICTION_SAMPLES = new ConcurrentHashMap<>();

    @Shadow
    Direction movementAxis;
    @Shadow
    double clientOffsetDiff;
    @Shadow
    double axisMotion;
    @Shadow
    public double sequencedOffsetLimit;

    @Shadow
    public abstract double getAxisCoord();

    @Unique
    private boolean sable$loggedGantryShaftLookup;

    protected GantryContraptionEntityMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    @Inject(method = "checkPinionShaft", at = @At("HEAD"), cancellable = true)
    private void sable$checkPinionShaftWithDoubleThreshold(final CallbackInfo ci) {
        final SubLevel containing = Sable.HELPER.getContaining(this);
        if (containing == null || !(this.contraption instanceof final GantryContraption gantryContraption)) {
            return;
        }

        final Direction carriageFacing = gantryContraption.getFacing();
        final Vec3 currentPosition = this.getAnchorVec().add(.5, .5, .5);
        final BlockPos gantryShaftPos = BlockPos.containing(currentPosition).relative(carriageFacing.getOpposite());
        final BlockEntity pinionBlockEntity = sable$getSableAwarePinionShaft(this.level(), gantryShaftPos);
        sable$logPinionPrediction("before", gantryShaftPos, pinionBlockEntity);

        if (!(pinionBlockEntity instanceof final GantryShaftBlockEntity gantryShaftBlockEntity)
                || !AllBlocks.GANTRY_SHAFT.has(pinionBlockEntity.getBlockState())) {
            if (!this.level().isClientSide) {
                this.setContraptionMotion(Vec3.ZERO);
                this.disassemble();
            }
            sable$logPinionPrediction("after", gantryShaftPos, pinionBlockEntity);
            ci.cancel();
            return;
        }

        final BlockState blockState = pinionBlockEntity.getBlockState();
        final Direction movementDirection = blockState.getValue(GantryShaftBlock.FACING);
        float pinionMovementSpeed = gantryShaftBlockEntity.getPinionMovementSpeed();
        if (blockState.getValue(GantryShaftBlock.POWERED) || pinionMovementSpeed == 0) {
            this.setContraptionMotion(Vec3.ZERO);
            if (!this.level().isClientSide) {
                this.disassemble();
            }
            sable$logPinionPrediction("after", gantryShaftPos, pinionBlockEntity);
            ci.cancel();
            return;
        }

        if (this.sequencedOffsetLimit >= 0) {
            pinionMovementSpeed = (float) Mth.clamp(
                    pinionMovementSpeed,
                    -this.sequencedOffsetLimit,
                    this.sequencedOffsetLimit);
        }

        final Vec3 movement = Vec3.atLowerCornerOf(movementDirection.getNormal()).scale(pinionMovementSpeed);
        final Vec3 nextPosition = currentPosition.add(movement);
        final double currentCoord = movementDirection.getAxis().choose(currentPosition.x, currentPosition.y, currentPosition.z);
        final double nextCoord = movementDirection.getAxis().choose(nextPosition.x, nextPosition.y, nextPosition.z);
        final boolean travellingNegative = pinionMovementSpeed * movementDirection.getAxisDirection().getStep() < 0;

        if (Mth.floor(currentCoord) + .5 < nextCoord != travellingNegative
                && !gantryShaftBlockEntity.canAssembleOn()) {
            this.setContraptionMotion(Vec3.ZERO);
            if (!this.level().isClientSide) {
                this.disassemble();
            }
            sable$logPinionPrediction("after", gantryShaftPos, pinionBlockEntity);
            ci.cancel();
            return;
        }

        if (!this.level().isClientSide) {
            this.axisMotion = pinionMovementSpeed;
            this.setContraptionMotion(movement);
        }
        sable$logPinionPrediction("after", gantryShaftPos, pinionBlockEntity);
        ci.cancel();
    }

    @Redirect(method = "checkPinionShaft",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
                    remap = true))
    private BlockEntity sable$getPinionShaftBlockEntity(final Level level, final BlockPos controllerPos) {
        return sable$getSableAwarePinionShaft(level, controllerPos);
    }

    @Unique
    private BlockEntity sable$getSableAwarePinionShaft(final Level level, final BlockPos controllerPos) {
        final BlockEntity blockEntity =
                SableCreateContraptionControllerLookup.getControllerBlockEntity(level, controllerPos);
        if (!this.sable$loggedGantryShaftLookup && Sable.HELPER.getContaining(level, controllerPos) != null) {
            this.sable$loggedGantryShaftLookup = true;
            final SubLevel containing = Sable.HELPER.getContaining(this);
            final BlockPos localPos = containing == null
                    ? controllerPos
                    : controllerPos.subtract(containing.getPlot().getCenterBlock());
            Sable.LOGGER.info("SABLE_M15_GANTRY_SHAFT_LOOKUP entityId={} controllerPos={} controllerLocal={} "
                            + "controllerBE={} controllerIsSable={} containingSubLevel={}",
                    this.getId(),
                    controllerPos,
                    localPos,
                    blockEntity == null ? "none" : blockEntity.getClass().getName(),
                    blockEntity != null,
                    containing == null ? "none" : containing.getUniqueId());
        }
        return blockEntity;
    }

    @Unique
    private void sable$logPinionPrediction(final String phase,
                                           final BlockPos expectedPinionRaw,
                                           final BlockEntity resolvedPinion) {
        final SubLevel containing = Sable.HELPER.getContaining(this);
        if (containing == null) {
            return;
        }
        final String key = this.getId() + ":" + phase + ":" + this.level().isClientSide;
        final int sample = SABLE$GANTRY_PREDICTION_SAMPLES.merge(key, 1, Integer::sum);
        if (sample > 16) {
            return;
        }

        final BlockPos plotOrigin = containing.getPlot().getCenterBlock();
        final BlockPos expectedLocal = expectedPinionRaw.subtract(plotOrigin);
        final BlockPos actualLocal = resolvedPinion == null
                ? expectedLocal
                : resolvedPinion.getBlockPos().subtract(plotOrigin);
        Sable.LOGGER.info("SABLE_M15_PREDICTION phase={} sample={} entityId={} clientSide={} rawPos={} "
                        + "rawPrev=({},{},{}) deltaMovement={} movementAxis={} axisMotion={} clientOffsetDiff={} "
                        + "axisCoord={} expectedPinionRaw={} expectedPinionLocal={} actualResolvedControllerRaw={} "
                        + "actualResolvedControllerLocal={} resolvedShaftClass={} resolvedShaftState={} "
                        + "pinionMovementSpeed={} containingSubLevel={}",
                phase,
                sample,
                this.getId(),
                this.level().isClientSide,
                this.position(),
                this.xOld,
                this.yOld,
                this.zOld,
                this.getDeltaMovement(),
                this.movementAxis,
                this.axisMotion,
                this.clientOffsetDiff,
                this.getAxisCoord(),
                expectedPinionRaw,
                expectedLocal,
                resolvedPinion == null ? "none" : resolvedPinion.getBlockPos(),
                actualLocal,
                resolvedPinion == null ? "none" : resolvedPinion.getClass().getName(),
                SubLevelBlockStateLookup.getBlockStateOrAir(containing, expectedPinionRaw),
                resolvedPinion instanceof final GantryShaftBlockEntity shaft ? shaft.getPinionMovementSpeed() : "none",
                containing.getUniqueId());
    }
}
