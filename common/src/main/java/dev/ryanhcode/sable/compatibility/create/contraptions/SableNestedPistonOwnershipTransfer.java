package dev.ryanhcode.sable.compatibility.create.contraptions;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlockEntity;
import com.simibubi.create.content.contraptions.piston.PistonContraption;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper.AssemblyTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Controller adapter in the existing outer transfer transaction. Native NBT owns all payload data. */
public final class SableNestedPistonOwnershipTransfer {
    private final List<Snapshot> snapshots = new ArrayList<>();
    private boolean committed;

    private SableNestedPistonOwnershipTransfer() { }

    static SableNestedPistonOwnershipTransfer capture(final ServerLevel level, final Collection<BlockPos> selected) {
        final var session = new SableNestedPistonOwnershipTransfer();
        for (final BlockPos pos : selected) {
            if (!(level.getBlockEntity(pos) instanceof MechanicalPistonBlockEntity piston)
                    || !(piston.movedContraption instanceof ControlledContraptionEntity entity)
                    || !(entity.getContraption() instanceof PistonContraption contraption)
                    || contraption.getActors().stream().noneMatch(actor -> actor.left.state().getBlock() instanceof SlidingDoorBlock)) continue;
            if (entity.isRemoved() || entity.level() != level || !piston.running
                    || !pos.equals(SableCreateContraptionContext.getControllerPos(entity)) || !entity.getPassengers().isEmpty()) {
                throw new IllegalStateException("Invalid live door piston ownership at " + pos);
            }
            for (final var info : contraption.getBlocks().values()) {
                final BlockPos absolute = contraption.anchor.offset(info.pos());
                final boolean generatedHead = absolute.equals(pos) && AllBlocks.MECHANICAL_PISTON_HEAD.has(info.state());
                if (!generatedHead && selected.contains(absolute)) throw new IllegalStateException("Live piston payload overlaps outer static selection " + absolute);
            }
            session.snapshots.add(new Snapshot(pos.immutable(), piston, entity, contraption,
                    contraption.writeNBT(false).copy(), piston.saveWithFullMetadata(), piston.offset, entity.position(), entity.getDeltaMovement()));
            Sable.LOGGER.info("SABLE_M42_NESTED_PISTON event=SNAPSHOT_CREATED controller={} sourceUuid={} blocks={} offset={} serializer=NATIVE_CREATE",
                    pos, entity.getUUID(), contraption.getBlocks().size(), piston.offset);
        }
        return session;
    }

    void validateTransform(final AssemblyTransform transform) {
        if (!snapshots.isEmpty() && transform.getRotation() != Rotation.NONE) {
            throw new IllegalStateException("Live door piston ownership transfer requires translation; retract before rotated disassembly");
        }
    }

    void restore(final ServerLevel targetLevel, final BlockPos sourcePos, final BlockPos targetPos, final AssemblyTransform transform) {
        for (final Snapshot snapshot : snapshots) {
            if (!snapshot.pos.equals(sourcePos)) continue;
            if (transform == null) throw new IllegalStateException("Piston ownership transfer has no transform");
            validateTransform(transform);
            if (!(targetLevel.getBlockEntity(targetPos) instanceof MechanicalPistonBlockEntity target)) throw new IllegalStateException("Destination piston missing " + targetPos);
            final PistonContraption restored = new PistonContraption();
            restored.readNBT(targetLevel, snapshot.tag.copy(), false);
            restored.anchor = transform.apply(snapshot.contraption.anchor);
            // Translation leaves local block/actor/storage coordinates intact. Never rebuild from fixture blocks.
            if (!restored.getBlocks().equals(snapshot.contraption.getBlocks())) throw new IllegalStateException("Piston payload state/NBT changed");
            if (restored.getActors().size() != snapshot.contraption.getActors().size()) throw new IllegalStateException("Piston actor count changed");
            for (final var actor : restored.getActors()) {
                final var original = snapshot.contraption.getActors().stream().filter(a -> a.left.pos().equals(actor.left.pos())).findFirst().orElseThrow();
                if (!actor.right.data.equals(original.right.data)) throw new IllegalStateException("Piston actor data changed " + actor.left.pos());
                if (actor.right.position != null) actor.right.position = transform.apply(actor.right.position);
            }
            final CompoundTag beforeStorage = new CompoundTag();
            final CompoundTag afterStorage = new CompoundTag();
            snapshot.contraption.writeStorage(beforeStorage, false);
            restored.writeStorage(afterStorage, false);
            if (!beforeStorage.equals(afterStorage)) throw new IllegalStateException("Native mounted storage changed during piston transfer");
            if (Math.abs(target.offset - snapshot.offset) > 1e-6) throw new IllegalStateException("Native piston controller offset was not copied");
            final CompoundTag targetTag = target.saveWithFullMetadata();
            for (final String key : List.of("Running", "Waiting", "Offset", "ExtensionLength", "SequencedOffsetLimit")) {
                if (!Objects.equals(snapshot.controllerTag.get(key), targetTag.get(key))) throw new IllegalStateException("Native piston controller state changed: " + key);
            }
            if (movementMode(target) != snapshot.movementMode) throw new IllegalStateException("Native piston movement mode was not copied");
            final ControlledContraptionEntity replacement = ControlledContraptionEntity.create(targetLevel, target, restored);
            final Vec3 position = transform.apply(snapshot.position);
            replacement.setPos(position.x, position.y, position.z);
            replacement.setContraptionMotion(snapshot.motion);
            target.movedContraption = replacement;
            target.assembleNextTick = false;
            snapshot.target = target;
            snapshot.replacement = replacement;
            try {
                if (!targetLevel.addFreshEntity(replacement)) throw new IllegalStateException("Piston replacement entity registration rejected");
                target.attach(replacement);
                if (SableCreateContraptionContext.getContainingSubLevel(replacement) != Sable.HELPER.getContaining(targetLevel, targetPos)
                        || !targetPos.equals(SableCreateContraptionContext.getControllerPos(replacement))) throw new IllegalStateException("Piston replacement ownership mismatch");
                verify(snapshot);
                Sable.LOGGER.info("SABLE_M42_NESTED_PISTON event=DESTINATION_REGISTERED controller={} sourceUuid={} replacementUuid={} blocks={} offset={} actorsPreserved=true storagePreserved=true",
                        targetPos, snapshot.entity.getUUID(), replacement.getUUID(), restored.getBlocks().size(), target.offset);
            } catch (final RuntimeException failure) {
                detach(target, replacement);
                replacement.discard();
                snapshot.target = null;
                snapshot.replacement = null;
                throw failure;
            }
        }
    }

    void requireRestored() {
        if (snapshots.stream().anyMatch(s -> s.replacement == null)) throw new IllegalStateException("Not every live piston was restored");
    }

    void commit() {
        requireRestored();
        for (final Snapshot snapshot : snapshots) {
            // Remove the old controller association BEFORE source BE removal can call native disassemble().
            detach(snapshot.controller, snapshot.entity);
            snapshot.entity.discard();
        }
        committed = true;
    }

    void verify() { for (final Snapshot snapshot : snapshots) verify(snapshot); }

    private static void verify(final Snapshot snapshot) {
        final var entity = Objects.requireNonNull(snapshot.replacement, "restored piston entity");
        if (entity.isRemoved() || snapshot.target.movedContraption != entity || !snapshot.target.running
                || !entity.getContraption().getBlocks().equals(snapshot.contraption.getBlocks())) throw new IllegalStateException("Live piston payload ownership lost");
        final ServerLevel level = (ServerLevel) entity.level();
        for (final var info : entity.getContraption().getBlocks().values()) {
            final BlockPos absolute = entity.getContraption().anchor.offset(info.pos());
            final boolean generatedHead = absolute.equals(snapshot.target.getBlockPos()) && AllBlocks.MECHANICAL_PISTON_HEAD.has(info.state());
            if (!generatedHead && !level.getBlockState(absolute).isAir()) throw new IllegalStateException("Duplicate static piston payload owner " + absolute);
        }
    }

    void rollback() {
        if (committed) return;
        for (final Snapshot snapshot : snapshots) if (snapshot.replacement != null) {
            detach(snapshot.target, snapshot.replacement);
            snapshot.replacement.discard();
            snapshot.replacement = null;
        }
    }

    private static void detach(final MechanicalPistonBlockEntity controller, final ControlledContraptionEntity entity) {
        if (controller.movedContraption != entity) throw new IllegalStateException("Piston controller association changed during transfer");
        controller.movedContraption = null;
        controller.running = false;
        controller.assembleNextTick = false;
        controller.setChanged();
    }

    private static int movementMode(final MechanicalPistonBlockEntity controller) {
        final var behaviour = controller.getBehaviour(ScrollValueBehaviour.TYPE);
        if (!(behaviour instanceof ScrollOptionBehaviour<?> option)) throw new IllegalStateException("Native piston movement mode absent");
        return option.getValue();
    }

    private static final class Snapshot {
        final BlockPos pos; final MechanicalPistonBlockEntity controller; final ControlledContraptionEntity entity;
        final PistonContraption contraption; final CompoundTag tag; final CompoundTag controllerTag; final float offset; final Vec3 position; final Vec3 motion;
        MechanicalPistonBlockEntity target; ControlledContraptionEntity replacement;
        final int movementMode;
        Snapshot(final BlockPos pos, final MechanicalPistonBlockEntity controller, final ControlledContraptionEntity entity,
                 final PistonContraption contraption, final CompoundTag tag, final CompoundTag controllerTag, final float offset, final Vec3 position, final Vec3 motion) {
            this.pos = pos; this.controller = controller; this.entity = entity; this.contraption = contraption;
            this.tag = tag; this.controllerTag = controllerTag; this.offset = offset; this.position = position; this.motion = motion;
            this.movementMode = movementMode(controller);
        }
    }
}
