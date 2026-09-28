package dev.ryanhcode.sable.command;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlockEntity;
import com.simibubi.create.content.contraptions.piston.PistonContraption;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.compatibility.create.contraptions.SableCreateContraptionContext;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.util.SubLevelBlockStateLookup;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** One loaded-dimension lookup for every M35 fixture command. Never selects by player proximity. */
final class M35FixtureLookup {
    static final String ORIGIN_TAG = "sable_m35_origin";
    static final String VERSION_TAG = "sable_m35_v5";

    private M35FixtureLookup() {
    }

    static Result find(final ServerLevel level, final Vec3 source) {
        final List<Candidate> candidates = new ArrayList<>();
        for (final net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (entity instanceof final ArmorStand marker && marker.getTags().contains(ORIGIN_TAG)) {
                candidates.add(inspect(level, marker, source));
            }
        }
        final M35FixtureSelection.Status status = M35FixtureSelection.classify(candidates.size(),
                candidates.size() == 1 ? candidates.get(0).matchCount() : 0,
                candidates.size() == 1 && candidates.get(0).orphan());
        return new Result(status, List.copyOf(candidates));
    }

    private static Candidate inspect(final ServerLevel level, final ArmorStand marker, final Vec3 source) {
        final BlockPos markerOrigin = marker.blockPosition();
        final List<Match> matches = new ArrayList<>();
        boolean sablePistonPresent = false;
        if (blockState(level, null, markerOrigin).is(com.simibubi.create.AllBlocks.STICKY_MECHANICAL_PISTON.get())) {
            matches.add(assess(level, markerOrigin, null));
        }
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            for (final ServerSubLevel body : container.getAllSubLevels()) {
                for (final BlockPos pos : SimAssemblyHelper.collectBlocks(level, body)) {
                    if (blockState(level, body, pos)
                            .is(com.simibubi.create.AllBlocks.STICKY_MECHANICAL_PISTON.get())) {
                        final Match bodyMatch = assess(level, pos, body);
                        sablePistonPresent |= bodyMatch.moving() || bodyMatch.extendedPayload()
                                || bodyMatch.rollerStatic() && bodyMatch.chestStatic() && bodyMatch.chassisStatic();
                        if (blockState(level, body, pos.north(2)).is(Blocks.IRON_BLOCK)) {
                            matches.add(bodyMatch);
                        }
                    }
                }
            }
        }
        // A partial controller is still a candidate: report its exact failed component.
        if (matches.isEmpty()) {
            matches.add(assess(level, markerOrigin, null));
            final Match partial = matches.get(0);
            final boolean orphan = partial.piston().isAir() && partial.motor().isAir()
                    && partial.clutch().isAir() && partial.lever().isAir()
                    && partial.assembler().isAir() && partial.hull().isAir()
                    && !partial.moving() && !partial.rollerStatic() && !partial.chestStatic()
                    && !partial.chassisStatic() && !hasDisplacedFixtureBlocks(level, markerOrigin)
                    && !sablePistonPresent;
            return new Candidate(marker, markerOrigin, source.distanceTo(marker.position()), List.of(),
                    List.copyOf(matches), orphan ? "ORPHAN_MARKER" : partial.rejectReason(), orphan);
        }
        final List<Match> valid = matches.stream().filter(match -> match.owned()
                || marker.getTags().contains("sable_m35_v4") && match.bodyId() == null
                && match.origin().equals(markerOrigin) && match.rejectReason().equals("HULL_MISSING"))
                .toList();
        if (valid.size() == 1) {
            return new Candidate(marker, markerOrigin, source.distanceTo(marker.position()), valid,
                    List.copyOf(matches), "NONE", false);
        }
        return new Candidate(marker, markerOrigin, source.distanceTo(marker.position()), valid,
                List.copyOf(matches),
                valid.isEmpty() ? matches.get(0).rejectReason() : "MULTIPLE_MATCHING_CONTROLLERS", false);
    }

    private static Match assess(final ServerLevel level, final BlockPos origin, final @Nullable ServerSubLevel body) {
        final BlockState pistonState = blockState(level, body, origin);
        final BlockState motorState = blockState(level, body, origin.below(2));
        final BlockState clutchState = blockState(level, body, origin.below());
        final BlockState leverState = blockState(level, body, origin.below().south());
        final BlockState assemblerState = blockState(level, body, origin.above().north(2));
        final BlockState hullState = blockState(level, body, origin.north(2));
        final MechanicalPistonBlockEntity piston = blockEntity(level, body, origin)
                instanceof final MechanicalPistonBlockEntity value ? value : null;
        final CreativeMotorBlockEntity motor = blockEntity(level, body, origin.below(2))
                instanceof final CreativeMotorBlockEntity value ? value : null;
        final AbstractContraptionEntity moving = movingPiston(level, body, origin, piston);
        final boolean rollerStatic = blockState(level, body, origin.east().above())
                .is(com.simibubi.create.AllBlocks.MECHANICAL_ROLLER.get());
        final boolean chestStatic = blockState(level, body, origin.east().south()).is(Blocks.CHEST);
        final boolean chassisStatic = blockState(level, body, origin.east())
                .is(com.simibubi.create.AllBlocks.RADIAL_CHASSIS.get());
        final int extendedOffset = piston == null ? 0 : Math.round(piston.offset);
        final boolean extendedPayload = moving == null && piston != null && !piston.running
                && pistonState.toString().contains("state=extended")
                && extendedOffset >= 1 && extendedOffset <= 12
                && Math.abs(piston.offset - extendedOffset) < 0.01F
                && blockState(level, body, origin.east(extendedOffset + 1))
                        .is(com.simibubi.create.AllBlocks.RADIAL_CHASSIS.get())
                && blockState(level, body, origin.east(extendedOffset + 1).above())
                        .is(com.simibubi.create.AllBlocks.MECHANICAL_ROLLER.get())
                && blockState(level, body, origin.east(extendedOffset + 1).south()).is(Blocks.CHEST);
        final String reject;
        if (piston == null || !pistonState.is(com.simibubi.create.AllBlocks.STICKY_MECHANICAL_PISTON.get())) {
            reject = "PISTON_OR_BLOCK_ENTITY_MISSING";
        } else if (!pistonState.hasProperty(BlockStateProperties.FACING)
                || pistonState.getValue(BlockStateProperties.FACING) != Direction.EAST) {
            reject = "PISTON_FACING_NOT_EAST";
        } else if (motor == null || !motorState.is(com.simibubi.create.AllBlocks.CREATIVE_MOTOR.get())) {
            reject = "MOTOR_OR_BLOCK_ENTITY_MISSING";
        } else if (!motorState.hasProperty(BlockStateProperties.FACING)
                || motorState.getValue(BlockStateProperties.FACING) != Direction.UP) {
            reject = "MOTOR_FACING_NOT_UP";
        } else if (!clutchState.is(com.simibubi.create.AllBlocks.CLUTCH.get())) {
            reject = "CLUTCH_MISSING";
        } else if (!leverState.is(Blocks.LEVER)) {
            reject = "LEVER_MISSING";
        } else if (moving != null && !movingPayloadComplete(moving)) {
            reject = "MOVING_PAYLOAD_INCOMPLETE";
        } else if (moving == null && piston.running) {
            reject = "RUNNING_PISTON_ENTITY_MISSING";
        } else if (moving == null && !extendedPayload && (!rollerStatic || !chestStatic || !chassisStatic)) {
            reject = "STATIC_PAYLOAD_MISSING";
        } else if (!hullState.is(Blocks.IRON_BLOCK)
                || !blockState(level, body, origin.north()).is(Blocks.IRON_BLOCK)) {
            reject = "HULL_MISSING";
        } else if (!(blockEntity(level, body, origin.above().north(2)) instanceof PhysicsAssemblerBlockEntity)) {
            reject = "ASSEMBLER_BLOCK_ENTITY_MISSING";
        } else {
            reject = "NONE";
        }
        return new Match(origin, body == null ? null : body.getUniqueId(), reject.equals("NONE"), reject,
                pistonState, motorState, clutchState, leverState, assemblerState, hullState,
                rollerStatic, chestStatic, chassisStatic, moving != null, extendedPayload,
                moving == null ? null : moving.getUUID(),
                piston != null && piston.running, piston == null ? 0 : piston.offset,
                piston == null ? 0 : piston.getMovementSpeed());
    }

    private static @Nullable AbstractContraptionEntity movingPiston(final ServerLevel level,
                                                                     final @Nullable ServerSubLevel body,
                                                                     final BlockPos origin,
                                                                     final @Nullable MechanicalPistonBlockEntity piston) {
        if (piston != null && piston.movedContraption != null && !piston.movedContraption.isRemoved()
                && piston.movedContraption.getContraption() instanceof PistonContraption) {
            return piston.movedContraption;
        }
        final List<AbstractContraptionEntity> nearby = level.getEntitiesOfClass(AbstractContraptionEntity.class,
                new AABB(origin).inflate(32), entity -> !entity.isRemoved()
                        && entity.getContraption() instanceof PistonContraption
                        && origin.equals(SableCreateContraptionContext.getControllerPos(entity))
                        && SableCreateContraptionContext.getContainingSubLevel(entity) == body);
        return nearby.size() == 1 ? nearby.get(0) : null;
    }

    private static boolean movingPayloadComplete(final AbstractContraptionEntity entity) {
        if (entity.getContraption() == null) {
            return false;
        }
        boolean roller = false;
        boolean chest = false;
        boolean chassis = false;
        for (final net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo block
                : entity.getContraption().getBlocks().values()) {
            roller |= block.state().is(com.simibubi.create.AllBlocks.MECHANICAL_ROLLER.get());
            chest |= block.state().is(Blocks.CHEST);
            chassis |= block.state().is(com.simibubi.create.AllBlocks.RADIAL_CHASSIS.get());
        }
        return roller && chest && chassis;
    }

    private static boolean hasDisplacedFixtureBlocks(final ServerLevel level, final BlockPos origin) {
        for (int x = -12; x <= -1; x++) {
            if (level.getBlockState(origin.offset(x, 0, 0))
                    .is(com.simibubi.create.AllBlocks.PISTON_EXTENSION_POLE.get())) {
                return true;
            }
        }
        for (int x = 1; x <= 13; x++) {
            final BlockPos cell = origin.east(x);
            if (level.getBlockState(cell).is(com.simibubi.create.AllBlocks.RADIAL_CHASSIS.get())
                    || level.getBlockState(cell.above()).is(com.simibubi.create.AllBlocks.MECHANICAL_ROLLER.get())
                    || level.getBlockState(cell.south()).is(Blocks.CHEST)) {
                return true;
            }
        }
        return false;
    }

    static BlockState blockState(final ServerLevel level, final @Nullable ServerSubLevel body, final BlockPos pos) {
        return body == null ? level.getBlockState(pos) : SubLevelBlockStateLookup.getBlockStateOrAir(body, pos);
    }

    static @Nullable BlockEntity blockEntity(final ServerLevel level, final @Nullable ServerSubLevel body,
                                            final BlockPos pos) {
        return body == null ? level.getBlockEntity(pos) : SubLevelBlockStateLookup.getBlockEntity(body, pos);
    }

    record Result(M35FixtureSelection.Status status, List<Candidate> candidates) {
        @Nullable Candidate sole() {
            return this.status.cleanupAllowed() ? this.candidates.get(0) : null;
        }
    }

    record Candidate(ArmorStand marker, BlockPos markerOrigin, double distance, List<Match> matches,
                     List<Match> inspected, String rejectReason, boolean orphan) {
        int matchCount() {
            return this.matches.size();
        }

        @Nullable Match match() {
            return this.matches.size() == 1 ? this.matches.get(0) : null;
        }
    }

    record Match(BlockPos origin, @Nullable UUID bodyId, boolean owned, String rejectReason,
                 BlockState piston, BlockState motor, BlockState clutch, BlockState lever,
                 BlockState assembler, BlockState hull, boolean rollerStatic, boolean chestStatic,
                 boolean chassisStatic, boolean moving, boolean extendedPayload,
                 @Nullable UUID movingEntityId, boolean pistonRunning,
                 float pistonOffset, float pistonMovementSpeed) {
        String confidence(final boolean currentVersion) {
            if (this.rejectReason.equals("HULL_MISSING")) {
                return "RECOVERABLE_PARTIAL_V4";
            }
            if (this.bodyId != null) {
                return "RECOVERABLE_ASSEMBLED_OUTER";
            }
            if (this.moving) {
                return "RECOVERABLE_MOVING_INNER";
            }
            return currentVersion ? "EXACT_CURRENT" : "EXACT_PREVIOUS";
        }
    }
}
