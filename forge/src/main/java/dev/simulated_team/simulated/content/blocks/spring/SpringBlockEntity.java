package dev.simulated_team.simulated.content.blocks.spring;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.index.SimulatedBlockEntityTypes;
import dev.simulated_team.simulated.index.SimulatedBlocks;
import dev.simulated_team.simulated.index.SimulatedConfig;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.UUID;

public class SpringBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {

    public static final double MAX_LENGTH = 9.0D;

    private static final double TIME_TO_SNAP = 0.75D;
    private static final double POINT_DAMPING = 4.5D;
    private static final double HOOKE_STIFFNESS = 145.0D;
    private static final double ALIGNMENT_TORQUE = 20.0D;
    private static final double AXIAL_ANGULAR_DAMPING = 2.0D;

    private static final Vector3d LOCAL_LINEAR_VELOCITY = new Vector3d();
    private static final Vector3d LOCAL_ANGULAR_VELOCITY = new Vector3d();
    private static final Vector3d EXPECTED_VELOCITY = new Vector3d();
    private static final Vector3d LOCAL_DAMPING_FORCE = new Vector3d();

    private boolean controller;
    private boolean assembling;
    private double desiredLength;
    private BlockPos partnerPos;
    @Nullable
    private UUID partnerSubLevel;
    private int ticksWithoutPartner;
    private double snappingTime;
    private boolean restoredFromSave;
    private ForceTotal forceTotal;
    private ForceTotal partnerForceTotal;
    private double lastCurrentLength;
    private double lastRelativeVelocity;
    private boolean loggedSettling;

    public SpringBlockEntity(final BlockPos pos, final BlockState state) {
        super(SimulatedBlockEntityTypes.SPRING.get(), pos, state);
    }

    public Vector3d getCenter() {
        final Direction facing = this.getBlockState().getValue(SpringBlock.FACING);
        final double scale = 0.5D - 4.0D / 16.0D;
        return JOMLConversion.atCenterOf(this.worldPosition)
                .sub(facing.getStepX() * scale, facing.getStepY() * scale, facing.getStepZ() * scale);
    }

    public @Nullable String tryChangeLengthOrError(final double delta) {
        if (this.level == null || this.partnerPos == null) {
            return "not_paired";
        }
        if (delta > 0.0D && this.desiredLength >= MAX_LENGTH) {
            return "max_length";
        }
        if (delta < 0.0D && this.desiredLength <= 1.0D) {
            return "min_length";
        }

        double newDesiredLength = clamp(this.desiredLength + delta, 1.0D, MAX_LENGTH);
        newDesiredLength = Math.round(newDesiredLength / 0.25D) * 0.25D;
        final double currentLength = Sable.HELPER.distanceSquaredWithSubLevels(this.level,
                this.worldPosition.getCenter(), this.partnerPos.getCenter()) + 1.0D;
        if (delta < 0.0D && currentLength > newDesiredLength * newDesiredLength * 4.0D) {
            return "too_stretched";
        }
        if (delta > 0.0D && currentLength < newDesiredLength * newDesiredLength / 4.0D) {
            return "too_compressed";
        }

        this.setDesiredLength(newDesiredLength);
        final SpringBlockEntity partner = this.getPairedSpring();
        if (partner != null) {
            partner.setDesiredLength(newDesiredLength);
            partner.sendData();
        }
        return null;
    }

    @Override
    public void sable$physicsTick(final ServerSubLevel subLevel, final RigidBodyHandle handle, final double timeStep) {
        if (!SimulatedConfig.ENABLE_M23_SPRING_CONSTRAINTS.get()) {
            return;
        }
        final SpringBlockEntity partner = this.getPairedSpring();
        if (this.level == null || this.partnerPos == null || partner == null || this.ticksWithoutPartner != 0) {
            return;
        }

        final SubLevel partnerContaining = Sable.HELPER.getContaining(this.level, this.partnerPos);
        final ServerSubLevel partnerSubLevel = partnerContaining instanceof final ServerSubLevel serverPartner
                ? serverPartner
                : null;
        if (this.partnerSubLevel != null && partnerSubLevel == null) {
            return;
        }
        if (partnerSubLevel != null && !this.controller) {
            return;
        }
        if (partnerSubLevel == subLevel) {
            return;
        }

        final SubLevelPhysicsSystem system = SubLevelPhysicsSystem.get(subLevel.getLevel());
        if (system != null) {
            system.updatePose(subLevel);
            if (partnerSubLevel != null) {
                system.updatePose(partnerSubLevel);
            }
        }

        final BlockState state = this.getBlockState();
        final SpringBlock.Size size = state.getValue(SpringBlock.SIZE);
        final Vector3dc center = this.getCenter();
        final Vector3dc partnerCenter = partner.getCenter();
        final Vector3d velocityA = Sable.HELPER.getVelocity(this.level, center, new Vector3d());
        final Vector3d velocityB = Sable.HELPER.getVelocity(this.level, partnerCenter, new Vector3d());

        final Vector3d positionA = subLevel.logicalPose().transformPosition(center, new Vector3d());
        final Vector3d positionB = partnerSubLevel != null
                ? partnerSubLevel.logicalPose().transformPosition(JOMLConversion.atCenterOf(this.partnerPos))
                : JOMLConversion.atCenterOf(this.partnerPos);

        final Vector3d relativeVelocity = velocityA.sub(velocityB);
        final Vector3d dampingPointForce = new Vector3d(relativeVelocity).mul(-POINT_DAMPING);
        this.lastCurrentLength = positionA.distance(positionB);
        this.lastRelativeVelocity = relativeVelocity.length();

        final double desired = (this.controller ? this.desiredLength : partner.desiredLength) - 0.75D;
        this.snappingTime = positionA.distanceSquared(positionB) > Mth.square(this.getSnappingDistance())
                ? this.snappingTime + timeStep
                : 0.0D;

        final Vector3d globalNormalA = JOMLConversion.atLowerCornerOf(state.getValue(SpringBlock.FACING).getNormal());
        final Vector3d globalNormalB = JOMLConversion.atLowerCornerOf(partner.getBlockState().getValue(SpringBlock.FACING).getNormal());
        subLevel.logicalPose().transformNormal(globalNormalA);
        if (partnerSubLevel != null) {
            partnerSubLevel.logicalPose().transformNormal(globalNormalB);
        }

        final Vector3d torque = globalNormalA.cross(globalNormalB.negate(), new Vector3d())
                .mul(ALIGNMENT_TORQUE)
                .mul(timeStep);
        final Vector3d mediumNormal = globalNormalA.lerp(globalNormalB, 0.5D);
        final Vector3d middle = new Vector3d(positionA).lerp(positionB, 0.5D);
        final Vector3d desireA = middle.fma(-desired / 2.0D, mediumNormal, new Vector3d());
        final Vector3d hookesPointForce = desireA.sub(positionA).mul(HOOKE_STIFFNESS);

        final Vector3d angularA = new Vector3d();
        final Vector3d angularB = new Vector3d();
        handle.getAngularVelocity(angularA);
        if (partnerSubLevel != null) {
            final RigidBodyHandle otherHandle = RigidBodyHandle.of(partnerSubLevel);
            if (otherHandle != null) {
                otherHandle.getAngularVelocity(angularB);
            }
        }

        final Vector3d relativeAngular = angularA.sub(angularB);
        final Vector3d dampingTorque = new Vector3d();
        if (mediumNormal.lengthSquared() > 0.0D) {
            mediumNormal.normalize();
            final double dot = mediumNormal.dot(relativeAngular);
            relativeAngular.set(mediumNormal).mul(dot);
            dampingTorque.fma(-AXIAL_ANGULAR_DAMPING, relativeAngular);
        }

        final double sizeScale = size.forceScale();
        hookesPointForce.mul(sizeScale);
        torque.mul(sizeScale);
        dampingTorque.mul(sizeScale);
        dampingPointForce.mul(sizeScale);

        if (this.forceTotal == null || this.partnerForceTotal == null) {
            this.forceTotal = new ForceTotal();
            this.partnerForceTotal = new ForceTotal();
        }

        this.applyLocalDamping(subLevel, handle, this.forceTotal, center, dampingPointForce, dampingTorque, timeStep);
        this.forceTotal.applyImpulseAtPoint(subLevel, center,
                subLevel.logicalPose().transformNormalInverse(new Vector3d(hookesPointForce)).mul(timeStep));
        this.forceTotal.applyLinearAndAngularImpulse(JOMLConversion.ZERO,
                subLevel.logicalPose().transformNormalInverse(torque, new Vector3d()));
        handle.applyForcesAndReset(this.forceTotal);

        if (partnerSubLevel != null) {
            final RigidBodyHandle partnerHandle = RigidBodyHandle.of(partnerSubLevel);
            if (partnerHandle != null) {
                this.applyLocalDamping(partnerSubLevel, partnerHandle, this.partnerForceTotal, partnerCenter,
                        dampingPointForce.negate(), dampingTorque.negate(), timeStep);
                this.partnerForceTotal.applyImpulseAtPoint(partnerSubLevel, partnerCenter,
                        partnerSubLevel.logicalPose().transformNormalInverse(hookesPointForce).mul(-timeStep));
                this.partnerForceTotal.applyLinearAndAngularImpulse(JOMLConversion.ZERO,
                        partnerSubLevel.logicalPose().transformNormalInverse(torque.negate()));
                partnerHandle.applyForcesAndReset(this.partnerForceTotal);
            }
        }

        if (!this.loggedSettling && this.level.getGameTime() % 40L == 0L) {
            this.loggedSettling = true;
            Sable.LOGGER.info("SABLE_M23_CONSTRAINT phase=CREATE_SUCCESS family=spring logicalId={} owner={} restLength={} currentLength={} relativeVelocity={}",
                    this.logicalConstraintId(),
                    this.worldPosition.toShortString(),
                    this.desiredLength,
                    this.lastCurrentLength,
                    this.lastRelativeVelocity);
        }
    }

    @Override
    public void sable$tick(final ServerSubLevel subLevel) {
        this.serverTick();
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        if (this.snappingTime > TIME_TO_SNAP) {
            this.level.destroyBlock(this.getBlockPos(), true);
            return;
        }
        if (this.partnerPos == null) {
            this.level.destroyBlock(this.getBlockPos(), true);
            return;
        }
        if (this.level.isLoaded(this.partnerPos)) {
            if (this.getPairedSpring() == null && this.ticksWithoutPartner++ > 20) {
                this.level.destroyBlock(this.getBlockPos(), true);
            } else if (this.getPairedSpring() != null) {
                this.ticksWithoutPartner = 0;
            }
        }
    }

    private void applyLocalDamping(final ServerSubLevel subLevel, final RigidBodyHandle handle,
                                   final ForceTotal forceTotal, final Vector3dc worldSpringPos,
                                   final Vector3dc dampingPointForce, final Vector3dc dampingTorque,
                                   final double timeStep) {
        final Pose3d pose = subLevel.logicalPose();

        handle.getAngularVelocity(LOCAL_ANGULAR_VELOCITY);
        handle.getLinearVelocity(LOCAL_LINEAR_VELOCITY);
        pose.orientation().transformInverse(LOCAL_ANGULAR_VELOCITY);
        pose.orientation().transformInverse(LOCAL_LINEAR_VELOCITY);

        final Vector3dc centerOfMass = subLevel.getMassTracker().getCenterOfMass();
        pose.orientation().transformInverse(dampingPointForce, LOCAL_DAMPING_FORCE);

        final Vector3d angularDamping = new Vector3d(dampingTorque);
        pose.orientation().transformInverse(angularDamping);
        angularDamping.add(worldSpringPos.sub(centerOfMass, new Vector3d()).cross(LOCAL_DAMPING_FORCE));

        final Vector3d linearDamping = new Vector3d(LOCAL_DAMPING_FORCE);
        EXPECTED_VELOCITY.set(linearDamping).mul(subLevel.getMassTracker().getInverseMass()).mul(timeStep);
        final double forceScale = this.getClampingFactor(LOCAL_LINEAR_VELOCITY, EXPECTED_VELOCITY);

        EXPECTED_VELOCITY.set(angularDamping);
        subLevel.getMassTracker().getInverseInertiaTensor().transform(EXPECTED_VELOCITY);
        EXPECTED_VELOCITY.mul(timeStep);
        final double torqueScale = this.getClampingFactor(LOCAL_ANGULAR_VELOCITY, EXPECTED_VELOCITY);

        forceTotal.applyLinearAndAngularImpulse(linearDamping.mul(forceScale * timeStep),
                angularDamping.mul(torqueScale * timeStep));
    }

    private double getClampingFactor(final Vector3dc currentVelocity, final Vector3dc expectedVelocityChange) {
        final double k = -currentVelocity.dot(expectedVelocityChange);
        final double v = currentVelocity.lengthSquared();
        if (k < 0.0D) {
            return 0.0D;
        }
        if (10.0D * k < v) {
            return 1.0D;
        }
        if (v < 1.0E-10D) {
            return v / (k + 1.0E-10D);
        }
        return v * (1.0D - Math.exp(-k / v)) / k;
    }

    @Override
    public @Nullable Iterable<@NotNull SubLevel> sable$getConnectionDependencies() {
        if (this.partnerSubLevel == null || this.level == null) {
            return List.of();
        }
        final SubLevel subLevel = Sable.HELPER.getContaining(this);
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (container == null) {
            return List.of();
        }
        final SubLevel partner = container.getSubLevel(this.partnerSubLevel);
        if (partner != null && partner != subLevel) {
            return List.of(partner);
        }
        return List.of();
    }

    public void breakPair() {
        if (this.level == null || this.level.isClientSide || this.partnerPos == null || this.assembling) {
            return;
        }
        final ServerLevel serverLevel = (ServerLevel) this.level;
        final BlockPos owner = this.worldPosition.immutable();
        final BlockPos partner = this.partnerPos.immutable();
        final SubLevel ownerSubLevelBefore = Sable.HELPER.getContaining(this.level, this.worldPosition);
        final SubLevel partnerSubLevelBefore = Sable.HELPER.getContaining(this.level, partner);
        final SpringBlockEntity partnerSpring = this.level.getBlockEntity(partner) instanceof final SpringBlockEntity spring
                ? spring
                : null;
        final SpringBlockEntity ownerActor = findSpringActor(ownerSubLevelBefore, owner);
        final SpringBlockEntity partnerActor = findSpringActor(partnerSubLevelBefore, partner);
        final String logicalId = this.logicalConstraintId();
        final BlockPos controllerEndpoint = this.controller
                ? owner
                : partnerSpring != null && partnerSpring.controller ? partner : owner;
        final BlockPos followerEndpoint = controllerEndpoint.equals(owner) ? partner : owner;
        final String actorOwner = controllerEndpoint.toShortString();
        final int actorCountBeforeA = countSpringActors(ownerSubLevelBefore);
        final int actorCountBeforeB = partnerSubLevelBefore == ownerSubLevelBefore
                ? actorCountBeforeA
                : countSpringActors(partnerSubLevelBefore);
        final boolean activeOnBodyABefore = activeConstraintPresent(serverLevel, ownerSubLevelBefore, logicalId);
        final boolean activeOnBodyBBefore = activeConstraintPresent(serverLevel, partnerSubLevelBefore, logicalId);
        if (ownerSubLevelBefore instanceof final ServerSubLevel serverOwnerBefore) {
            SimAssemblyHelper.logBodySnapshot((ServerLevel) this.level, serverOwnerBefore, "before_spring_teardown", 0);
        }
        if (partnerSubLevelBefore instanceof final ServerSubLevel serverPartnerBefore
                && serverPartnerBefore != ownerSubLevelBefore) {
            SimAssemblyHelper.logBodySnapshot((ServerLevel) this.level, serverPartnerBefore, "before_spring_teardown", 0);
        }
        final SpringTeardownSnapshot ownerBefore = this.teardownSnapshot(this.worldPosition);
        final SpringTeardownSnapshot partnerBefore = this.teardownSnapshot(partner);

        clearPairMetadata(this);
        clearPairMetadata(ownerActor);
        clearPairMetadata(partnerSpring);
        clearPairMetadata(partnerActor);
        final boolean ownerActorRemovedBeforeDestroy = unregisterSpringActor(ownerSubLevelBefore, owner, ownerActor);
        final boolean partnerActorRemovedBeforeDestroy = unregisterSpringActor(partnerSubLevelBefore, partner, partnerActor);

        boolean partnerDestroyed = false;
        if (partnerSpring != null && this.level.getBlockState(partner).is(SimulatedBlocks.SPRING.get())) {
            partnerDestroyed = this.level.destroyBlock(partner, false);
        }
        final boolean ownerActorRemovedAfterDestroy = unregisterSpringActor(ownerSubLevelBefore, owner, null);
        final boolean partnerActorRemovedAfterDestroy = unregisterSpringActor(partnerSubLevelBefore, partner, null);
        this.setChanged();
        final SpringTeardownSnapshot ownerAfter = this.teardownSnapshot(this.worldPosition);
        final SpringTeardownSnapshot partnerAfter = this.teardownSnapshot(partner);
        final SubLevel ownerSubLevelAfter = Sable.HELPER.getContaining(this.level, this.worldPosition);
        final SubLevel partnerSubLevelAfter = Sable.HELPER.getContaining(this.level, partner);
        final int actorCountAfterA = countSpringActors(ownerSubLevelAfter);
        final int actorCountAfterB = partnerSubLevelAfter == ownerSubLevelAfter
                ? actorCountAfterA
                : countSpringActors(partnerSubLevelAfter);
        final boolean activeOnBodyAAfter = activeConstraintPresent(serverLevel, ownerSubLevelAfter, logicalId);
        final boolean activeOnBodyBAfter = activeConstraintPresent(serverLevel, partnerSubLevelAfter, logicalId);
        final boolean cleanupComplete = !ownerAfter.actorRegistered()
                && !partnerAfter.actorRegistered()
                && !activeOnBodyAAfter
                && !activeOnBodyBAfter;
        if (ownerSubLevelAfter instanceof final ServerSubLevel serverOwnerAfter) {
            SimAssemblyHelper.logBodySnapshot((ServerLevel) this.level, serverOwnerAfter, "after_spring_teardown", 0);
        }
        if (partnerSubLevelAfter instanceof final ServerSubLevel serverPartnerAfter
                && serverPartnerAfter != ownerSubLevelAfter) {
            SimAssemblyHelper.logBodySnapshot((ServerLevel) this.level, serverPartnerAfter, "after_spring_teardown", 0);
        }
        Sable.LOGGER.info("SABLE_M23_TEARDOWN springOwnerPos={} pairedEndpointPos={} ownerSableId={} partnerSableId={} logicalConstraintId={} physicsHandleValidBefore={} physicsHandleValidAfter={} ownerActorRegisteredBefore={} ownerActorRegisteredAfter={} partnerActorRegisteredBefore={} partnerActorRegisteredAfter={} ownerBlockStateBefore={} ownerBlockStateAfter={} partnerBlockStateBefore={} partnerBlockStateAfter={} cleanup=setBlockAIR:false,destroyBlock:{},removeBlockEntity:false,removeTrackingPoint:false,removeActor:{},removeSableBlock:false,removeParentWorldBlock:false",
                this.worldPosition.toShortString(),
                partner.toShortString(),
                ownerBefore.sableId(),
                partnerBefore.sableId(),
                logicalId,
                ownerBefore.physicsHandleValid() || partnerBefore.physicsHandleValid(),
                ownerAfter.physicsHandleValid() || partnerAfter.physicsHandleValid(),
                ownerBefore.actorRegistered(),
                ownerAfter.actorRegistered(),
                partnerBefore.actorRegistered(),
                partnerAfter.actorRegistered(),
                ownerBefore.blockState(),
                ownerAfter.blockState(),
                partnerBefore.blockState(),
                partnerAfter.blockState(),
                partnerDestroyed,
                ownerActorRemovedBeforeDestroy || ownerActorRemovedAfterDestroy
                        || partnerActorRemovedBeforeDestroy || partnerActorRemovedAfterDestroy);
        Sable.LOGGER.info("SABLE_M23_SPRING_CLEANUP logicalId={} controllerEndpoint={} followerEndpoint={} actorOwner={} actorCountBeforeA={} actorCountBeforeB={} actorCountAfterA={} actorCountAfterB={} activeOnBodyABefore={} activeOnBodyBBefore={} activeOnBodyAAfter={} activeOnBodyBAfter={} ownerBlockPresentAfter={} partnerBlockPresentAfter={} ownerActorRemovedBeforeDestroy={} partnerActorRemovedBeforeDestroy={} ownerActorRemovedAfterDestroy={} partnerActorRemovedAfterDestroy={} result={}",
                logicalId,
                controllerEndpoint.toShortString(),
                followerEndpoint.toShortString(),
                actorOwner,
                actorCountBeforeA,
                actorCountBeforeB,
                actorCountAfterA,
                actorCountAfterB,
                activeOnBodyABefore,
                activeOnBodyBBefore,
                activeOnBodyAAfter,
                activeOnBodyBAfter,
                isSpringBlockPresent(owner),
                isSpringBlockPresent(partner),
                ownerActorRemovedBeforeDestroy,
                partnerActorRemovedBeforeDestroy,
                ownerActorRemovedAfterDestroy,
                partnerActorRemovedAfterDestroy,
                cleanupComplete ? "SUCCESS" : "PENDING");
        Sable.LOGGER.info("SABLE_M23_CONSTRAINT phase={} family=spring logicalId={} owner={}",
                cleanupComplete ? "REMOVE_SUCCESS" : "REMOVE_PENDING",
                logicalId, this.worldPosition.toShortString());
    }

    private static void clearPairMetadata(final @Nullable SpringBlockEntity spring) {
        if (spring == null) {
            return;
        }
        spring.partnerPos = null;
        spring.partnerSubLevel = null;
        spring.ticksWithoutPartner = 0;
        spring.snappingTime = 0.0D;
        spring.forceTotal = null;
        spring.partnerForceTotal = null;
        spring.setChanged();
    }

    private static @Nullable SpringBlockEntity findSpringActor(final @Nullable SubLevel subLevel, final BlockPos pos) {
        if (!(subLevel instanceof ServerSubLevel)) {
            return null;
        }
        final Object direct = subLevel.getPlot().getBlockEntityActor(pos);
        if (direct instanceof final SpringBlockEntity spring) {
            return spring;
        }
        for (final Object actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof final SpringBlockEntity spring && spring.getBlockPos().equals(pos)) {
                return spring;
            }
        }
        return null;
    }

    private static boolean unregisterSpringActor(final @Nullable SubLevel subLevel, final BlockPos pos,
                                                 final @Nullable SpringBlockEntity expectedActor) {
        if (!(subLevel instanceof ServerSubLevel)) {
            return false;
        }
        return subLevel.getPlot().removeBlockEntityActor(pos, expectedActor);
    }

    private static int countSpringActors(final @Nullable SubLevel subLevel) {
        if (subLevel instanceof final ServerSubLevel serverSubLevel) {
            return SimAssemblyHelper.countSpringActors(serverSubLevel);
        }
        return 0;
    }

    private static boolean activeConstraintPresent(final ServerLevel level, final @Nullable SubLevel subLevel,
                                                   final String logicalId) {
        return subLevel instanceof final ServerSubLevel serverSubLevel
                && SimAssemblyHelper.activeSpringConstraintIds(level, serverSubLevel).contains(logicalId);
    }

    private boolean isSpringBlockPresent(final BlockPos pos) {
        return this.level != null && this.level.getBlockState(pos).is(SimulatedBlocks.SPRING.get());
    }

    private SpringTeardownSnapshot teardownSnapshot(final BlockPos pos) {
        if (this.level == null) {
            return new SpringTeardownSnapshot("null", false, false, "null");
        }
        final SubLevel subLevel = Sable.HELPER.getContaining(this.level, pos);
        boolean handleValid = false;
        boolean actorRegistered = false;
        if (subLevel instanceof final ServerSubLevel serverSubLevel) {
            final RigidBodyHandle handle = RigidBodyHandle.of(serverSubLevel);
            handleValid = handle != null && handle.isValid();
            for (final Object actor : serverSubLevel.getPlot().getBlockEntityActors()) {
                if (actor instanceof final SpringBlockEntity spring && spring.getBlockPos().equals(pos)) {
                    actorRegistered = true;
                    break;
                }
            }
        }
        return new SpringTeardownSnapshot(
                subLevel == null ? "static_world" : subLevel.getUniqueId().toString(),
                handleValid,
                actorRegistered,
                this.level.getBlockState(pos).toString());
    }

    @Override
    public AABB getRenderBoundingBox() {
        final SpringBlockEntity goal = this.getPairedSpring();
        if (goal == null || this.partnerPos == null) {
            return new AABB(this.getBlockPos());
        }
        return new AABB(this.getBlockPos().getCenter(), this.partnerPos.getCenter()).inflate(3.0D);
    }

    public @Nullable SpringBlockEntity getPairedSpring() {
        if (this.level == null || this.partnerPos == null) {
            return null;
        }
        final BlockEntity blockEntity = this.level.getBlockEntity(this.partnerPos);
        return blockEntity instanceof final SpringBlockEntity spring ? spring : null;
    }

    public String inspect() {
        final SubLevel owner = Sable.HELPER.getContaining(this);
        final SubLevel partner = this.partnerPos == null || this.level == null
                ? null
                : Sable.HELPER.getContaining(this.level, this.partnerPos);
        final boolean handleA = owner instanceof final ServerSubLevel ownerServer
                && RigidBodyHandle.of(ownerServer) != null
                && RigidBodyHandle.of(ownerServer).isValid();
        final boolean handleB = partner instanceof final ServerSubLevel partnerServer
                && RigidBodyHandle.of(partnerServer) != null
                && RigidBodyHandle.of(partnerServer).isValid();
        final boolean sableToSable = owner instanceof ServerSubLevel && partner instanceof ServerSubLevel;
        final boolean staticWorld = owner == null && partner == null;
        final String constraintMode = sableToSable ? "SABLE_TO_SABLE" : "STATIC_WORLD";
        final String runtimeState = staticWorld && this.getPairedSpring() != null
                ? "STATIC_WORLD_NO_SABLE_FORCE_TEST"
                : this.getPairedSpring() == null ? "WAITING_FOR_ENDPOINT" : "ACTIVE";
        final double extension = this.lastCurrentLength - this.desiredLength;
        final double computedAxialForce = extension * HOOKE_STIFFNESS;
        return "family=spring ownerPos=" + this.worldPosition.toShortString()
                + " constraintMode=" + constraintMode
                + " ownerSable=" + (owner == null ? "static_world" : owner.getUniqueId())
                + " logicalConstraintId=" + this.logicalConstraintId()
                + " bodyA=" + (owner == null ? "static_world" : owner.getUniqueId())
                + " bodyB=" + (partner == null ? "static_world_or_unresolved" : partner.getUniqueId())
                + " bodyAHandleValid=" + handleA
                + " bodyBHandleValid=" + handleB
                + " forceActorRegistered=" + (owner instanceof final ServerSubLevel ownerServer
                        && findSpringActor(ownerServer, this.worldPosition) != null)
                + " physicsConstraintHandleValid=force_actor"
                + " endpointALocal=" + this.getCenter()
                + " endpointBLocal=" + this.partnerPos
                + " created=" + (this.partnerPos != null)
                + " restoredFromSave=" + this.restoredFromSave
                + " removalPending=false"
                + " runtimeState=" + runtimeState
                + " restLength=" + this.desiredLength
                + " currentLength=" + this.lastCurrentLength
                + " extension=" + extension
                + " computedAxialForce=" + computedAxialForce
                + " stiffness=" + HOOKE_STIFFNESS
                + " damping=" + POINT_DAMPING;
    }

    public String logicalConstraintId() {
        if (this.partnerPos == null) {
            return this.worldPosition.asLong() + ":unpaired";
        }
        final long a = this.worldPosition.asLong();
        final long b = this.partnerPos.asLong();
        return Math.min(a, b) + ":" + Math.max(a, b);
    }

    public void setPartnerPos(final BlockPos pos, @Nullable final UUID subLevel) {
        this.partnerPos = pos;
        this.partnerSubLevel = subLevel;
        this.setChanged();
    }

    public void setController(final boolean controller) {
        this.controller = controller;
        this.setChanged();
    }

    public void setDesiredLength(final double desiredLength) {
        this.desiredLength = desiredLength;
        this.setChanged();
    }

    public void setAssembling(final boolean assembling) {
        this.assembling = assembling;
    }

    public double getSnappingDistance() {
        return this.desiredLength * 4.0D + 2.0D;
    }

    public boolean isController() {
        return this.controller;
    }

    public boolean isActiveConstraint() {
        return this.partnerPos != null && this.getPairedSpring() != null;
    }

    public @Nullable UUID getPartnerSubLevelID() {
        return this.partnerSubLevel;
    }

    public @Nullable BlockPos getPartnerPos() {
        return this.partnerPos;
    }

    public double getDesiredLength() {
        return this.desiredLength;
    }

    public void sendData() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Controller", this.controller);
        tag.putDouble("DesiredLength", this.desiredLength);
        if (this.partnerPos != null) {
            tag.putLong("Goal", this.partnerPos.asLong());
        }
        if (this.partnerSubLevel != null) {
            tag.putUUID("GoalSubLevel", this.partnerSubLevel);
        }
    }

    @Override
    public void load(final CompoundTag tag) {
        super.load(tag);
        this.controller = tag.getBoolean("Controller");
        this.desiredLength = tag.getDouble("DesiredLength");
        this.partnerPos = tag.contains("Goal") ? BlockPos.of(tag.getLong("Goal")) : null;
        this.partnerSubLevel = tag.hasUUID("GoalSubLevel") ? tag.getUUID("GoalSubLevel") : null;
        this.restoredFromSave = this.partnerPos != null;
    }

    private static double clamp(final double value, final double min, final double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record SpringTeardownSnapshot(String sableId, boolean physicsHandleValid,
                                          boolean actorRegistered, String blockState) {
    }
}
