package dev.ryanhcode.sable.compatibility.create.goggles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** No Level/client bootstrap. Invalid hits must never enter the original TFMG callback body. */
public final class M44GoggleHitPolicyTest {
    private M44GoggleHitPolicyTest() { }
    public static void run() {
        final BlockHitResult normal = new BlockHitResult(new Vec3(1.5, 80, 2.5), Direction.UP, new BlockPos(1, 79, 2), false);
        final EntityHitResult entity = new EntityHitResult(null, new Vec3(1.5, 80, 2.5));
        final BlockHitResult miss = BlockHitResult.miss(Vec3.ZERO, Direction.NORTH, BlockPos.ZERO);
        check(null, true, false, GoggleOverlayHitPolicy.Decision.NULL_HIT);
        check(miss, true, false, GoggleOverlayHitPolicy.Decision.MISS);
        check(entity, true, false, GoggleOverlayHitPolicy.Decision.ENTITY_NO_BLOCK_CONTEXT);
        check(normal, false, false, GoggleOverlayHitPolicy.Decision.NO_CLIENT_CONTEXT);
        check(normal, true, false, GoggleOverlayHitPolicy.Decision.NATIVE_PARENT_BLOCK);
        final BlockHitResult raw = new BlockHitResult(new Vec3(20_000_000.5, 101, 20_000_000.5), Direction.UP, new BlockPos(20_000_000, 100, 20_000_000), false);
        check(raw, true, true, GoggleOverlayHitPolicy.Decision.HIDDEN_STORAGE_NO_PHYSICAL_BLOCK_CONTEXT);
        final HitResult malformed = new HitResult(Vec3.ZERO) {
            @Override public Type getType() { return Type.BLOCK; }
        };
        check(malformed, true, false, GoggleOverlayHitPolicy.Decision.NON_BLOCK_HIT);
        // Negative reproduction: the exact external pre-guard cast is invalid for a legitimate entity hit.
        boolean oldFailed = false;
        try { final BlockHitResult impossible = (BlockHitResult) (HitResult) entity; impossible.getBlockPos(); }
        catch (final ClassCastException expected) { oldFailed = true; }
        if (!oldFailed) throw new AssertionError("old TFMG failure not reproduced");
        int delegated = 0;
        for (final HitResult hit : new HitResult[] {null, miss, entity, malformed, normal}) {
            if (GoggleOverlayHitPolicy.decide(hit, true, false) == GoggleOverlayHitPolicy.Decision.NATIVE_PARENT_BLOCK) {
                if (hit != normal) throw new AssertionError("unsafe callback entered");
                delegated++;
            }
        }
        if (delegated != 1 || normal.getBlockPos().getX() != 1 || entity.getType() != HitResult.Type.ENTITY) throw new AssertionError("ordinary block / entity identity changed");
    }
    private static void check(final HitResult hit, final boolean ready, final boolean hidden, final GoggleOverlayHitPolicy.Decision expected) {
        if (GoggleOverlayHitPolicy.decide(hit, ready, hidden) != expected) throw new AssertionError("hit eligibility " + expected);
    }
}
