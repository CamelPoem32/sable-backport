package dev.ryanhcode.sable.compatibility.create.dispenser;

import dev.ryanhcode.sable.command.M38DispenserFixturePlacement;
import dev.ryanhcode.sable.command.M38DispenserFixturePlacement.Category;
import dev.ryanhcode.sable.command.M38DispenserFixturePlacement.Reason;
import dev.ryanhcode.sable.command.M38DispenserFixturePlacement.Rejection;
import net.minecraft.core.BlockPos;

public final class M38DispenserFixturePlacementTest {
    private static final BlockPos BASE = new BlockPos(0, 100, 0);

    private M38DispenserFixturePlacementTest() {
    }

    public static void run() {
        final var cells = M38DispenserFixturePlacement.requiredAir(false, false);
        if (cells.containsKey(new BlockPos(-12, -3, -16))
                || M38DispenserFixturePlacement.classify(new BlockPos(-12, -3, -16), false, false)
                        != Category.MAY_BE_SUPPORT_GROUND
                || cells.get(new BlockPos(-12, 0, 0)) != Category.INNER_CREATE_MECHANISM
                || cells.get(new BlockPos(0, 0, -2)) != Category.ASSEMBLER_HULL
                || cells.get(new BlockPos(14, 0, 0)) != Category.EMISSION_CORRIDOR
                || M38DispenserFixturePlacement.requiredAir(true, false).get(new BlockPos(14, 0, 0))
                        != Category.WILL_BE_REPLACED_BY_FIXTURE
                || !M38DispenserFixturePlacement.requiredAir(false, true)
                        .containsKey(new BlockPos(0, 1, -18))) {
            throw new AssertionError("M38 structural/swept footprint changed");
        }

        final var flatFloor = M38DispenserFixturePlacement.find(BASE, false, false,
                (pos, category) -> pos.getY() <= BASE.getY() - 3
                        ? rejected(pos, category, Reason.OCCUPIED) : null);
        if (!BASE.equals(flatFloor.origin()) || flatFloor.checked() != 1) {
            throw new AssertionError("flat stone floor must not block the air fixture: " + flatFloor);
        }

        final BlockPos firstPole = BASE.offset(-12, 0, 0);
        final var second = M38DispenserFixturePlacement.find(BASE, false, false,
                (pos, category) -> pos.equals(firstPole) ? rejected(pos, category, Reason.OCCUPIED) : null);
        if (!BASE.offset(8, 0, 0).equals(second.origin()) || second.checked() != 2
                || second.rejected().get(0).reason() != Reason.OCCUPIED) {
            throw new AssertionError("blocked first candidate must choose deterministic second: " + second);
        }

        final var unloaded = M38DispenserFixturePlacement.find(BASE, false, false,
                (pos, category) -> pos.equals(firstPole) ? rejected(pos, category, Reason.UNLOADED) : null);
        if (!BASE.offset(8, 0, 0).equals(unloaded.origin())
                || unloaded.rejected().get(0).chunkLoaded()) {
            throw new AssertionError("unloaded candidate must be rejected without a block read: " + unloaded);
        }

        final var blockEntity = M38DispenserFixturePlacement.find(BASE, false, false,
                (pos, category) -> pos.equals(firstPole) ? rejected(pos, category, Reason.BLOCK_ENTITY) : null);
        if (blockEntity.rejected().get(0).reason() != Reason.BLOCK_ENTITY
                || !BASE.offset(8, 0, 0).equals(blockEntity.origin())) {
            throw new AssertionError("unrelated block entity must not be overwritten: " + blockEntity);
        }

        final var none = M38DispenserFixturePlacement.find(BASE, false, false,
                (pos, category) -> rejected(pos, category, Reason.OCCUPIED));
        if (none.origin() != null || none.checked() != M38DispenserFixturePlacement.candidateOffsets().size()
                || none.rejected().size() != none.checked()) {
            throw new AssertionError("no valid candidate must fail before build: " + none);
        }
    }

    private static Rejection rejected(final BlockPos pos, final Category category, final Reason reason) {
        return new Rejection(pos, category, reason != Reason.UNLOADED,
                reason == Reason.UNLOADED ? "<unloaded>" : "Block{minecraft:stone}",
                "EmptyFluid", reason == Reason.BLOCK_ENTITY ? "TestBlockEntity" : "none", false, reason);
    }
}
