package dev.ryanhcode.sable.compatibility.create.dispenser;

import dev.ryanhcode.sable.command.M38DispenserFixtureLayout;
import dev.ryanhcode.sable.command.M38DispenserFixtureLayout.Status;
import dev.ryanhcode.sable.command.M38DispenserFixturePlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public final class M38DispenserFixtureLifecycleTest {
    private M38DispenserFixtureLifecycleTest() {
    }

    public static void run() {
        final var layout = M38DispenserFixtureLayout.blocks(true, false);
        require(layout.size() == 22, "native 22-block fixture");
        require(layout.get(new BlockPos(0, 0, -2)).equals("minecraft:iron_block"), "assembler starting hull");
        require(layout.get(new BlockPos(0, 1, -2)).equals("simulated:physics_assembler"), "assembler coordinate");
        require(layout.get(new BlockPos(1, 0, 0)).contains("sticky_north=true,sticky_east=true"), "native chassis sides");
        for (final var pos : layout.keySet()) {
            require(M38DispenserFixturePlacement.requiredAir(false, false).containsKey(pos), "unreserved build cell " + pos);
        }
        final BlockPos relocated = new BlockPos(37, 104, -57);
        final var glue = M38DispenserFixtureLayout.outerGlueBounds(relocated);
        require(glue.contains(Vec3.atCenterOf(relocated.below(2))), "native outer glue includes motor");
        require(glue.contains(Vec3.atCenterOf(relocated.north(2))), "native outer glue includes starting hull");
        require(glue.contains(Vec3.atCenterOf(relocated.above().north(2))), "native outer glue includes assembler");
        require(!glue.contains(Vec3.atCenterOf(relocated.east())), "outer glue does not bind moving chassis");
        require(!glue.contains(Vec3.atCenterOf(relocated.east().above())), "actor uses native chassis, not outer glue");
        final Map<BlockPos, String> world = new HashMap<>();
        layout.forEach((pos, state) -> world.put(relocated.offset(pos), M38DispenserFixtureLayout.blockId(state)));
        require(M38DispenserFixtureLayout.controllerSignature(pos -> world.get(relocated.offset(pos))), "relocated signature");
        require(M38DispenserFixtureLayout.classify(true, true, false, true, false, false) == Status.STATIC_VALID,
                "current static lookup");
        require(M38DispenserFixtureLayout.classify(true, true, true, true, false, false) == Status.ASSEMBLED_VALID,
                "current assembled lookup");
        require(M38DispenserFixtureLayout.classify(true, true, true, false, false, false) == Status.ASSEMBLED_RECOVERABLE,
                "old untyped marker/manual body recovery");
        require(M38DispenserFixtureLayout.classify(false, true, true, false, false, false) == Status.ASSEMBLED_RECOVERABLE,
                "markerless known legacy body");
        require(M38DispenserFixtureLayout.classify(false, true, false, false, false, false) == Status.STATIC_PARTIAL,
                "markerless known legacy static recovery");
        world.remove(relocated.offset(1, 1, 0)); // The old deferred builder never created the actor.
        require(M38DispenserFixtureLayout.legacyPartialSignature(pos -> world.get(relocated.offset(pos))),
                "legacy base-only partial fixture");
        world.remove(relocated.offset(0, 0, -2));
        require(!M38DispenserFixtureLayout.controllerSignature(pos -> world.get(relocated.offset(pos))), "missing starting hull rejected");
        require(M38DispenserFixtureLayout.legacyPartialSignature(pos -> world.get(relocated.offset(pos))), "bounded partial recovery");
        require(M38DispenserFixtureLayout.ownedRestoreResidue(BlockPos.ZERO,
                "create:sticky_mechanical_piston", "create:sticky_mechanical_piston", true, false), "owned duplicate restore block");
        require(!M38DispenserFixtureLayout.ownedRestoreResidue(BlockPos.ZERO,
                "minecraft:stone", "create:sticky_mechanical_piston", true, false), "unrelated blocker preserved");
        require(!M38DispenserFixtureLayout.ownedRestoreResidue(new BlockPos(-1, -5, -2),
                "minecraft:stone", "minecraft:stone", true, false), "fallen-pose floor never owned");
        world.clear();
        require(M38DispenserFixtureLayout.footprintClear(true, false, pos -> !world.containsKey(relocated.offset(pos))),
                "rollback zero residual blocks");
        world.put(relocated, "create:sticky_mechanical_piston");
        require(!M38DispenserFixtureLayout.footprintClear(true, false, pos -> !world.containsKey(relocated.offset(pos))),
                "rollback residue cannot be complete");
        for (int retry = 0; retry < 2; retry++) {
            require(M38DispenserFixtureLayout.classify(false, false, false, false, true, false) == Status.NONE, "idempotent absence");
            require(M38DispenserFixtureLayout.classify(true, false, false, false, true, false) == Status.ORPHAN_MARKER, "orphan recovery");
        }
        require(M38DispenserFixtureLayout.classify(true, true, true, true, false, true) == Status.CORRUPT_AMBIGUOUS,
                "multiple identities never selected");
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
