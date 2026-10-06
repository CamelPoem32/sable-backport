package dev.ryanhcode.sable.compatibility.create.basin;

import dev.ryanhcode.sable.command.M40BasinFixtureLayout;
import dev.ryanhcode.sable.command.M40FixturePreconditions;
import dev.ryanhcode.sable.compatibility.create.contraptions.NestedBearingCaptureDecision;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class M40BasinOutputTest {
    private M40BasinOutputTest() { }

    public static void run() {
        final UUID owned = new UUID(0, 40);
        final UUID unrelated = new UUID(0, 39);
        final var known = new M40FixturePreconditions.BodyCandidate(owned, true, true);
        final var nearby = new M40FixturePreconditions.BodyCandidate(unrelated, true, false);
        if (!M40FixturePreconditions.matchingBodies(List.of(known, nearby, known)).equals(List.of(owned))) throw new AssertionError("nonmatching or repeated candidate poisoned unique ownership");
        if (M40FixturePreconditions.matchingBodies(List.of(known, new M40FixturePreconditions.BodyCandidate(unrelated, true, true))).size() != 2) throw new AssertionError("real ambiguity hidden");
        if (!M40FixturePreconditions.matchingBodies(List.of(new M40FixturePreconditions.BodyCandidate(unrelated, false, true))).isEmpty()) throw new AssertionError("unrelated identity adopted");
        if (!M40FixturePreconditions.bearingReady(true, true, true, 16, 16, 16, 40L, 40L, 40L)) throw new AssertionError("native shaft chain rejected");
        if (M40FixturePreconditions.bearingReady(true, true, true, 16, 16, 0, 40L, 40L, null)
                || M40FixturePreconditions.bearingReady(true, false, true, 16, 16, 16, 40L, 40L, 40L)
                || M40FixturePreconditions.bearingReady(true, true, true, 16, 16, 16, 40L, 40L, 39L)) throw new AssertionError("dead/missing/split network entered CAPTURE");
        if (NestedBearingCaptureDecision.evaluate(false, Set.of(), null, false) != NestedBearingCaptureDecision.Action.SKIP_NO_SELECTED_STEERING_NETWORK
                || NestedBearingCaptureDecision.evaluate(true, Set.of(), null, true) != NestedBearingCaptureDecision.Action.SNAPSHOT_EXISTING) throw new AssertionError("M40 must stage a live rotor rather than assume steering-only static capture");
        if (!M40BasinFixtureLayout.MOTOR.east().equals(M40BasinFixtureLayout.CLUTCH)
                || !M40BasinFixtureLayout.CLUTCH.east().equals(BlockPos.ZERO)) throw new AssertionError("native east-facing source/clutch/bearing topology disconnected");
        if (SableBasinOutput.finite(null) || SableBasinOutput.finite(new Vec3(Double.NaN, 0, 0))) throw new AssertionError("missing/invalid pose must not fall back to plot");
        for (final double yaw : new double[] {0, Math.PI / 2, Math.PI}) {
            final Pose3d pose = new Pose3d(new Vector3d(10, 80, -30), new Quaterniond().rotationY(yaw),
                    new Vector3d(20_000_000, 100, 20_000_000), new Vector3d(1));
            for (final double angle : new double[] {0, 30, 120, 150, 180, 270, 360}) {
                final double radians = Math.toRadians(angle);
                final Vec3 raw = new Vec3(20_000_001.5, 100.5 + Math.cos(radians), 20_000_000.5 + Math.sin(radians));
                final Vec3 physical = pose.transformPosition(raw);
                final Vec3 expected = new Vec3(10 + 1.5 * Math.cos(yaw) + (.5 + Math.sin(radians)) * Math.sin(yaw),
                        80.5 + Math.cos(radians), -30 - 1.5 * Math.sin(yaw) + (.5 + Math.sin(radians)) * Math.cos(yaw));
                if (physical.distanceTo(expected) > 1e-7 || Math.abs(physical.x) > 1000 || Math.abs(physical.z) > 1000) throw new AssertionError("inner then outer composition failed");
                if (pose.transformPositionInverse(physical).distanceTo(raw) > 1e-7) throw new AssertionError("double transform");
                final Vec3 up = pose.transformNormal(new Vec3(0, Math.cos(radians), Math.sin(radians)));
                if (SableBasinOutput.nativeDumpDirection(up) != (angle == 150 || angle == 180)) throw new AssertionError("native nearest-DOWN contract changed at " + angle);
                if (Math.abs(up.scale(.05).length() - .05) > 1e-9) throw new AssertionError("native velocity magnitude changed");
            }
        }
        // Basin has vector semantics, not a cardinal-world block interaction envelope.
        if (!SableBasinOutput.nativeDumpDirection(new Vec3(.1, -.99, .1))) throw new AssertionError("native inversion must remain valid");
        final var cells = M40BasinFixtureLayout.mechanism();
        if (!"minecraft:iron_block".equals(cells.get(M40BasinFixtureLayout.ASSEMBLER.below()))
                || !cells.get(M40BasinFixtureLayout.BASIN).equals("create:basin[facing=down]")
                || !cells.get(new BlockPos(1, 0, 0)).contains("sticky_north=true,sticky_east=true")) throw new AssertionError("native fixture topology invalid");
        if (M40BasinFixtureLayout.glue(BlockPos.ZERO).contains(Vec3.atCenterOf(new BlockPos(1, 0, 0)))) throw new AssertionError("outer glue captures inner controller");
        if (!M40BasinFixtureLayout.reservation().containsAll(cells.keySet())) throw new AssertionError("unreserved build cell");
        if (M40BasinFixtureLayout.reservation().contains(new BlockPos(0, -5, 0))) throw new AssertionError("ordinary ground must be unreserved");
        if (M40BasinFixtureLayout.reservation().size() > 150) throw new AssertionError("broad cuboid reservation returned");
        for (final BlockPos floor : M40BasinFixtureLayout.platform().keySet()) {
            if (cells.containsKey(floor) || floor.getY() != -4) throw new AssertionError("parent floor joins rotor or hull");
        }
        for (int angle = 0; angle <= 360; angle++) {
            final double radians = Math.toRadians(angle);
            final Vec3 center = new Vec3(1.5, .5 + Math.cos(radians), .5 + Math.sin(radians));
            for (final BlockPos floor : M40BasinFixtureLayout.platform().keySet()) {
                if (new net.minecraft.world.phys.AABB(floor).intersects(center.x - .9, center.y - .9, center.z - .9,
                        center.x + .9, center.y + .9, center.z + .9)) throw new AssertionError("rotor intersects floor");
            }
        }
    }
}
