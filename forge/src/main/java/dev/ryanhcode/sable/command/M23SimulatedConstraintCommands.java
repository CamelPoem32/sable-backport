package dev.ryanhcode.sable.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.command.SableCommandHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity;
import dev.simulated_team.simulated.index.SimulatedBlocks;
import dev.simulated_team.simulated.index.SimulatedConfig;
import dev.simulated_team.simulated.index.SimulatedItems;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3d;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class M23SimulatedConstraintCommands {

    private M23SimulatedConstraintCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m23")
                .then(Commands.literal("status").executes(M23SimulatedConstraintCommands::status))
                .then(Commands.literal("fixture")
                        .then(Commands.literal("spring")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> fixtureSpring(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("torsion")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> deferredFixture(ctx.getSource(), "torsion_spring"))))
                        .then(Commands.literal("swivel")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> deferredFixture(ctx.getSource(), "swivel_bearing"))))
                        .then(Commands.literal("rope")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> deferredFixture(ctx.getSource(), "rope_connector/rope_winch"))))
                        .then(Commands.literal("docking")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> deferredFixture(ctx.getSource(), "docking_connector")))))
                .then(Commands.literal("inspect")
                        .then(Commands.literal("spring").executes(M23SimulatedConstraintCommands::inspectSpring))
                        .then(Commands.literal("bodies").executes(M23SimulatedConstraintCommands::inspectFixtureBodies)))
                .then(Commands.literal("nudge")
                        .then(Commands.literal("spring_a")
                                .then(Commands.argument("dx", DoubleArgumentType.doubleArg(-16.0D, 16.0D))
                                        .then(Commands.argument("dy", DoubleArgumentType.doubleArg(-16.0D, 16.0D))
                                                .then(Commands.argument("dz", DoubleArgumentType.doubleArg(-16.0D, 16.0D))
                                                        .executes(ctx -> nudgeSpringBody(ctx, 0))))))
                        .then(Commands.literal("spring_b")
                                .then(Commands.argument("dx", DoubleArgumentType.doubleArg(-16.0D, 16.0D))
                                        .then(Commands.argument("dy", DoubleArgumentType.doubleArg(-16.0D, 16.0D))
                                                .then(Commands.argument("dz", DoubleArgumentType.doubleArg(-16.0D, 16.0D))
                                                        .executes(ctx -> nudgeSpringBody(ctx, 1))))))));
    }

    private static int status(final CommandContext<CommandSourceStack> ctx) {
        send(ctx.getSource(), "SABLE_M23_STATUS simulatedBaseline=" + Simulated.BASELINE_COMMIT
                + " spring=ADAPT_NOW"
                + " torsion_spring=DEFER_M24"
                + " swivel_bearing=DEFER_M24"
                + " rope_connector=STRUCTURAL_ONLY"
                + " rope_winch=DEFER_M24"
                + " docking_connector=DEFER_M24"
                + " springConstraintsEnabled=" + SimulatedConfig.ENABLE_M23_SPRING_CONSTRAINTS.get()
                + " proven=force,reload,active_disassembly_block,spring_removal"
                + " blockers=assembly_selection_absorbs_touching_terrain"
                + " status=PARTIAL_RUNTIME_PROVEN_ASSEMBLY_SELECTION_REGRESSION_BLOCKER");
        return 1;
    }

    private static int fixtureSpring(final CommandSourceStack source, final String name) {
        if (!"basic".equals(name)) {
            if ("teardown".equals(name)) {
                return fixtureSpringTeardown(source);
            }
            send(source, "SABLE_M23_FIXTURE status=FAIL family=spring unknownFixture=" + name + " expected=basic|teardown");
            return 0;
        }

        final ServerLevel level = source.getLevel();
        final BlockPos origin = BlockPos.containing(source.getPosition()).offset(4, 1, 0);
        final BlockPos bodyA = origin;
        final BlockPos bodyB = origin.offset(7, 0, 0);

        buildSpringBody(level, bodyA, Blocks.COPPER_BLOCK.defaultBlockState());
        buildSpringBody(level, bodyB, Blocks.CUT_COPPER.defaultBlockState());
        level.setBlock(bodyA.west(), AllBlocks.SHAFT.get().defaultBlockState(), 3);
        level.setBlock(bodyB.east(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), 3);
        final BlockEntity chest = level.getBlockEntity(bodyB.east());
        if (chest instanceof final ChestBlockEntity chestBlockEntity) {
            chestBlockEntity.setItem(0, SimulatedItems.SPRING.get().getDefaultInstance());
            chestBlockEntity.setChanged();
        }
        final SelectionPreview bodyAPreview = previewAssemblySelection(level, bodyA, platformPositions(bodyA));

        if (source.getEntity() instanceof final net.minecraft.server.level.ServerPlayer player) {
            player.getInventory().add(new ItemStack(SimulatedItems.SPRING.get()));
        }

        send(source, "SABLE_M23_FIXTURE status=PASS family=spring name=basic"
                + " bodyAAssembler=" + bodyA.above().toShortString()
                + " bodyBAssembler=" + bodyB.above().toShortString()
                + " bodyASpringSupport=" + bodyA.east().toShortString()
                + " bodyBSpringSupport=" + bodyB.west().toShortString()
                + " supportPlatform=ordinary_minecraft_stone"
                + " glue=CREATE_SUPER_GLUE"
                + " sequence=right_click_each_assembler_then_use_spring_item_on_facing_support_faces");
        send(source, "SABLE_M22_ASSEMBLY_SELECTION startPos=" + bodyA.toShortString()
                + " gluedBlockCount=5"
                + " selectedBlockCount=" + bodyAPreview.selectedBlockCount()
                + " platformBlockCount=" + bodyAPreview.platformBlockCount()
                + " selectedPlatformBlocks=" + bodyAPreview.selectedPlatformBlocks()
                + " selectionBounds=" + bodyAPreview.selectionBounds()
                + " selectionSha256=" + bodyAPreview.selectionSha256());
        return bodyAPreview.selectedPlatformBlocks() == 0 ? 1 : 0;
    }

    private static int fixtureSpringTeardown(final CommandSourceStack source) {
        final ServerLevel level = source.getLevel();
        final BlockPos origin = BlockPos.containing(source.getPosition()).offset(4, 1, 0);
        final BlockPos bodyA = origin;
        final BlockPos bodyB = origin.offset(8, 0, 0);

        buildTeardownBodyA(level, bodyA);
        buildTeardownBodyB(level, bodyB);

        if (source.getEntity() instanceof final net.minecraft.server.level.ServerPlayer player) {
            player.getInventory().add(new ItemStack(SimulatedItems.SPRING.get()));
        }

        send(source, "SABLE_M23_FIXTURE status=PASS family=spring name=teardown"
                + " bodyAAssembler=" + bodyA.above().toShortString()
                + " bodyBAssembler=" + bodyB.above().toShortString()
                + " bodyASpringSupport=" + bodyA.east().toShortString()
                + " bodyBSpringSupport=" + bodyB.west().toShortString()
                + " bodyAExpectedPayload=5"
                + " bodyBExpectedPayload=5"
                + " supportPlatform=ordinary_minecraft_stone"
                + " glue=CREATE_SUPER_GLUE"
                + " sequence=right_click_each_assembler_then_use_spring_item_on_facing_support_faces");
        return 1;
    }

    private static void buildSpringBody(final ServerLevel level, final BlockPos origin, final net.minecraft.world.level.block.state.BlockState payload) {
        buildSupportPlatform(level, origin);
        level.setBlock(origin, Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        level.setBlock(origin.above(), SimulatedBlocks.PHYSICS_ASSEMBLER.get().defaultBlockState(), 3);
        level.setBlock(origin.east(), payload, 3);
        level.setBlock(origin.west(), payload, 3);
        level.setBlock(origin.north(), Blocks.AMETHYST_BLOCK.defaultBlockState(), 3);
        level.setBlock(origin.south(), Blocks.CALCITE.defaultBlockState(), 3);
        glueBody(level, origin);
    }

    private static void buildTeardownBodyA(final ServerLevel level, final BlockPos origin) {
        buildSupportPlatform(level, origin);
        level.setBlock(origin, Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        level.setBlock(origin.above(), SimulatedBlocks.PHYSICS_ASSEMBLER.get().defaultBlockState(), 3);
        level.setBlock(origin.east(), Blocks.COPPER_BLOCK.defaultBlockState(), 3);
        level.setBlock(origin.west(), Blocks.LAPIS_BLOCK.defaultBlockState(), 3);
        level.setBlock(origin.north(), Blocks.AMETHYST_BLOCK.defaultBlockState(), 3);
        level.setBlock(origin.south(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), 3);
        glueBody(level, origin);
        final BlockEntity chest = level.getBlockEntity(origin.south());
        if (chest instanceof final ChestBlockEntity chestBlockEntity) {
            final ItemStack canary = Items.NAME_TAG.getDefaultInstance();
            canary.setHoverName(Component.literal("M23_BODY_A_CANARY"));
            chestBlockEntity.setItem(0, canary);
            chestBlockEntity.setChanged();
        }
    }

    private static void buildTeardownBodyB(final ServerLevel level, final BlockPos origin) {
        buildSupportPlatform(level, origin);
        level.setBlock(origin, Blocks.DEEPSLATE_TILES.defaultBlockState(), 3);
        level.setBlock(origin.above(), SimulatedBlocks.PHYSICS_ASSEMBLER.get().defaultBlockState(), 3);
        level.setBlock(origin.west(), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        level.setBlock(origin.east(), Blocks.CALCITE.defaultBlockState(), 3);
        level.setBlock(origin.north(), Blocks.WAXED_COPPER_BLOCK.defaultBlockState(), 3);
        level.setBlock(origin.south(), AllBlocks.SHAFT.get().defaultBlockState(), 3);
        glueBody(level, origin);
    }

    private static void buildSupportPlatform(final ServerLevel level, final BlockPos origin) {
        for (final BlockPos pos : platformPositions(origin)) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        }
    }

    private static Set<BlockPos> platformPositions(final BlockPos origin) {
        final Set<BlockPos> platform = new HashSet<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                platform.add(origin.offset(x, -1, z));
            }
        }
        return platform;
    }

    private static void glueBody(final ServerLevel level, final BlockPos origin) {
        glue(level, origin, origin.above());
        glue(level, origin, origin.east());
        glue(level, origin, origin.west());
        glue(level, origin, origin.north());
        glue(level, origin, origin.south());
    }

    private static void glue(final ServerLevel level, final BlockPos first, final BlockPos second) {
        level.addFreshEntity(new SuperGlueEntity(level, SuperGlueEntity.span(first, second)));
    }

    private static SelectionPreview previewAssemblySelection(final ServerLevel level, final BlockPos start,
                                                             final Set<BlockPos> platform) {
        final SimAssemblyContraption contraption = new SimAssemblyContraption(null);
        try {
            contraption.searchMovedStructure(level, start);
        } catch (final AssemblyException exception) {
            return new SelectionPreview(0, platform.size(), 0,
                    "failed:" + exception.getMessage(), "failed");
        }
        int selectedPlatformBlocks = 0;
        for (final BlockPos selected : contraption.getBlocks()) {
            if (platform.contains(selected)) {
                selectedPlatformBlocks++;
            }
        }
        return new SelectionPreview(contraption.getBlocks().size(), platform.size(), selectedPlatformBlocks,
                describeBounds(contraption.getBlocks()), selectionDigest(level, contraption.getBlocks()));
    }

    private static String describeBounds(final Collection<BlockPos> positions) {
        if (positions.isEmpty()) {
            return "empty";
        }
        final int minX = positions.stream().map(BlockPos::getX).min(Comparator.naturalOrder()).orElse(0);
        final int minY = positions.stream().map(BlockPos::getY).min(Comparator.naturalOrder()).orElse(0);
        final int minZ = positions.stream().map(BlockPos::getZ).min(Comparator.naturalOrder()).orElse(0);
        final int maxX = positions.stream().map(BlockPos::getX).max(Comparator.naturalOrder()).orElse(0);
        final int maxY = positions.stream().map(BlockPos::getY).max(Comparator.naturalOrder()).orElse(0);
        final int maxZ = positions.stream().map(BlockPos::getZ).max(Comparator.naturalOrder()).orElse(0);
        return minX + "," + minY + "," + minZ + "->" + maxX + "," + maxY + "," + maxZ;
    }

    private static String selectionDigest(final ServerLevel level, final Collection<BlockPos> positions) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            positions.stream()
                    .sorted(Comparator.comparingLong(BlockPos::asLong))
                    .forEach(pos -> digest.update((pos.toShortString() + "|"
                            + level.getBlockState(pos) + "\n").getBytes(StandardCharsets.UTF_8)));
            return toHex(digest.digest());
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String toHex(final byte[] bytes) {
        final StringBuilder result = new StringBuilder(bytes.length * 2);
        for (final byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private record SelectionPreview(int selectedBlockCount, int platformBlockCount,
                                    int selectedPlatformBlocks, String selectionBounds,
                                    String selectionSha256) {
    }

    private static int deferredFixture(final CommandSourceStack source, final String family) {
        send(source, "SABLE_M23_FIXTURE status=DEFERRED family=" + family
                + " reason=source_documented_not_runtime_ported_in_spring_gate");
        return 1;
    }

    private static int inspectSpring(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final SpringRelation relation = findActiveSableToSableSpring(source);
        if (relation != null) {
            logSpringBodySnapshot(source.getLevel(), relation.spring(), "inspect_spring");
            send(source, "SABLE_M23_INSPECT " + describeSpringRelation(source.getLevel(), relation));
            return relation.sameBody() ? 0 : 1;
        }

        final SpringBlockEntity spring = findNearestParentSpring(source.getLevel(),
                BlockPos.containing(source.getPosition()), 32);
        if (spring == null) {
            send(source, "SABLE_M23_INSPECT family=spring runtimeState=NOT_FOUND");
            return 0;
        }
        logSpringBodySnapshot(source.getLevel(), spring, "inspect_spring");
        send(source, "SABLE_M23_INSPECT " + spring.inspect());
        return 1;
    }

    private static int inspectFixtureBodies(final CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final List<ServerSubLevel> bodies = findPhysicsAssemblerBodies(source);
        for (int index = 0; index < Math.min(8, bodies.size()); index++) {
            logBodyCountAudit(source, bodies.get(index), 6, index);
        }

        final List<ServerSubLevel> validFixtureBodies = bodies.stream()
                .filter(body -> isValidFixtureBody(source.getLevel(), body))
                .sorted(Comparator.comparingDouble(body -> body.logicalPose().position().x()))
                .toList();
        if (validFixtureBodies.size() < 2) {
            send(source, "SABLE_M23_FIXTURE_BODIES status=FAIL reason=invalid_assembled_fixture_body"
                    + " foundPhysicsAssemblerBodies=" + bodies.size()
                    + " validSixBlockBodies=" + validFixtureBodies.size()
                    + " expectedBodies=2"
                    + " expectedStoredBlockCount=6"
                    + " expectedAssemblerPresent=true"
                    + " expectedSpringSupportPresent=true");
            return 0;
        }
        final ServerSubLevel bodyA = validFixtureBodies.get(0);
        final ServerSubLevel bodyB = validFixtureBodies.get(1);
        final BlockPos bodyAAssemblerRaw = findPhysicsAssemblerRaw(bodyA);
        final BlockPos bodyBAssemblerRaw = findPhysicsAssemblerRaw(bodyB);
        final BlockPos bodyASpringSupportRaw = findVisibleSupportRaw(bodyA, Direction.EAST);
        final BlockPos bodyBSpringSupportRaw = findVisibleSupportRaw(bodyB, Direction.WEST);
        final int bodyABlockCount = SimAssemblyHelper.collectBlocks(source.getLevel(), bodyA).size();
        final int bodyBBlockCount = SimAssemblyHelper.collectBlocks(source.getLevel(), bodyB).size();
        final boolean validBodyA = bodyABlockCount == 6 && bodyAAssemblerRaw != null && bodyASpringSupportRaw != null;
        final boolean validBodyB = bodyBBlockCount == 6 && bodyBAssemblerRaw != null && bodyBSpringSupportRaw != null;
        if (bodyA == bodyB || !validBodyA || !validBodyB) {
            send(source, "SABLE_M23_FIXTURE_BODIES status=FAIL reason=invalid_assembled_fixture_body"
                    + " bodyASableId=" + bodyA.getUniqueId()
                    + " bodyBSableId=" + bodyB.getUniqueId()
                    + " bodyABlockCount=" + bodyABlockCount
                    + " bodyBBlockCount=" + bodyBBlockCount
                    + " bodyAAssemblerPresent=" + (bodyAAssemblerRaw != null)
                    + " bodyBAssemblerPresent=" + (bodyBAssemblerRaw != null)
                    + " bodyASpringSupportPresent=" + (bodyASpringSupportRaw != null)
                    + " bodyBSpringSupportPresent=" + (bodyBSpringSupportRaw != null)
                    + " sameSable=" + (bodyA == bodyB));
            return 0;
        }
        send(source, "SABLE_M23_FIXTURE_BODIES status=PASS"
                + " bodyAAssemblerVisible=" + visibleBlockPos(bodyA, bodyAAssemblerRaw)
                + " bodyBAssemblerVisible=" + visibleBlockPos(bodyB, bodyBAssemblerRaw)
                + " bodyASableId=" + bodyA.getUniqueId()
                + " bodyBSableId=" + bodyB.getUniqueId()
                + " bodyAAssemblerRaw=" + shortPos(bodyAAssemblerRaw)
                + " bodyBAssemblerRaw=" + shortPos(bodyBAssemblerRaw)
                + " bodyABlockCount=" + bodyABlockCount
                + " bodyBBlockCount=" + bodyBBlockCount
                + " sameSable=" + (bodyA == bodyB));
        send(source, "SABLE_M23_FIXTURE_SUPPORTS"
                + " bodyASpringSupportVisible=" + visibleBlockPos(bodyA, bodyASpringSupportRaw)
                + " bodyASpringSupportSableId=" + bodyA.getUniqueId()
                + " bodyASpringSupportRaw=" + shortPos(bodyASpringSupportRaw)
                + " bodyBSpringSupportVisible=" + visibleBlockPos(bodyB, bodyBSpringSupportRaw)
                + " bodyBSpringSupportSableId=" + bodyB.getUniqueId()
                + " bodyBSpringSupportRaw=" + shortPos(bodyBSpringSupportRaw));
        return bodyA == bodyB ? 0 : 1;
    }

    private static boolean isValidFixtureBody(final ServerLevel level, final ServerSubLevel body) {
        return SimAssemblyHelper.collectBlocks(level, body).size() == 6
                && findPhysicsAssemblerRaw(body) != null
                && (findVisibleSupportRaw(body, Direction.EAST) != null
                        || findVisibleSupportRaw(body, Direction.WEST) != null);
    }

    private static void logBodyCountAudit(final CommandSourceStack source, final ServerSubLevel body,
                                          final int selectedBlockCount, final int candidateIndex) {
        final ServerLevel level = source.getLevel();
        final List<BlockPos> blocks = SimAssemblyHelper.collectBlocks(level, body);
        final BodyCountAudit audit = bodyCountAudit(level, body, blocks);
        send(source, "SABLE_M23_BODY_COUNT_AUDIT"
                + " candidateIndex=" + candidateIndex
                + " sableId=" + body.getUniqueId()
                + " selectedBlockCount=" + selectedBlockCount
                + " storedBlockCount=" + audit.storedBlockCount()
                + " nonAirBlockCount=" + audit.nonAirBlockCount()
                + " payloadBlockCount=" + audit.payloadBlockCount()
                + " blockEntityCount=" + audit.blockEntityCount()
                + " trackingPointCount=" + audit.trackingPointCount()
                + " actorCount=" + audit.actorCount()
                + " rawBounds=" + audit.rawBounds()
                + " boundsVolume=" + audit.boundsVolume()
                + " mass=" + audit.mass()
                + " collisionUploadedBlockCount=" + audit.collisionUploadedBlockCount());
        send(source, "SABLE_M23_BODY_COUNT_AUDIT_BLOCKS"
                + " candidateIndex=" + candidateIndex
                + " sableId=" + body.getUniqueId()
                + " firstNonAirRawBlocks=" + describeRawBlocks(level, body, blocks, 32));
    }

    private static BodyCountAudit bodyCountAudit(final ServerLevel level, final ServerSubLevel body,
                                                 final List<BlockPos> blocks) {
        int blockEntityCount = 0;
        int specialBlocks = 0;
        for (final BlockPos block : blocks) {
            if (level.getBlockEntity(block) != null) {
                blockEntityCount++;
            }
            if (level.getBlockState(block).is(SimulatedBlocks.PHYSICS_ASSEMBLER.get())
                    || level.getBlockState(block).is(SimulatedBlocks.SPRING.get())) {
                specialBlocks++;
            }
        }
        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
        return new BodyCountAudit(
                blocks.size(),
                blocks.size(),
                Math.max(0, blocks.size() - specialBlocks),
                blockEntityCount,
                SimAssemblyHelper.countTrackingPoints(level, body.getPlot().getBoundingBox()),
                SimAssemblyHelper.countBlockEntityActors(body),
                body.getPlot().getBoundingBox().toString(),
                body.getPlot().getBoundingBox().volume(),
                body.getMassTracker().getMass(),
                physicsSystem == null ? -1 : physicsSystem.getUploadedCollisionBlockCount(body));
    }

    private static String describeRawBlocks(final ServerLevel level, final ServerSubLevel body,
                                            final List<BlockPos> blocks, final int limit) {
        final StringBuilder result = new StringBuilder();
        int count = 0;
        for (final BlockPos block : blocks.stream()
                .sorted(Comparator.comparingLong(BlockPos::asLong))
                .limit(limit)
                .toList()) {
            if (count++ > 0) {
                result.append(';');
            }
            result.append("rawPos=").append(block.toShortString())
                    .append(",localPos=").append(block.subtract(body.getPlot().getCenterBlock()).toShortString())
                    .append(",blockId=").append(ForgeRegistries.BLOCKS.getKey(level.getBlockState(block).getBlock()));
        }
        if (blocks.size() > limit) {
            result.append(";truncated=").append(blocks.size() - limit);
        }
        return result.isEmpty() ? "empty" : result.toString();
    }

    private static void logSpringBodySnapshot(final ServerLevel level, final SpringBlockEntity spring, final String phase) {
        final Object ownerContaining = Sable.HELPER.getContaining(level, spring.getBlockPos());
        if (ownerContaining instanceof final ServerSubLevel owner) {
            SimAssemblyHelper.logBodySnapshot(level, owner, phase, 0);
        }
        if (spring.getPartnerPos() != null
                && Sable.HELPER.getContaining(level, spring.getPartnerPos()) instanceof final ServerSubLevel partner
                && ownerContaining != partner) {
            SimAssemblyHelper.logBodySnapshot(level, partner, phase, 0);
        }
    }

    private static SpringBlockEntity findNearestParentSpring(final ServerLevel level, final BlockPos center, final int radius) {
        SpringBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (final BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (level.getBlockEntity(pos) instanceof final SpringBlockEntity spring) {
                final double distance = pos.distSqr(center);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = spring;
                }
            }
        }
        return best;
    }

    private static int nudgeSpringBody(final CommandContext<CommandSourceStack> ctx, final int fixtureIndex)
            throws CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final SpringRelation relation = findActiveSableToSableSpring(source);
        if (relation == null) {
            send(source, "SABLE_M23_NUDGE status=FAIL reason=no_active_sable_to_sable_spring");
            return 0;
        }
        if (relation.sameBody()) {
            send(source, "SABLE_M23_NUDGE status=FAIL reason=active_spring_has_same_body"
                    + " sableId=" + relation.owner().getUniqueId());
            return 0;
        }
        final ServerSubLevel subLevel = fixtureIndex == 0 ? relation.owner() : relation.partner();
        final SubLevelPhysicsSystem physicsSystem = SableCommandHelper.requireSubLevelPhysicsSystem(ctx);
        final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        if (handle == null || !handle.isValid()) {
            send(source, "SABLE_M23_NUDGE status=FAIL target=" + (fixtureIndex == 0 ? "spring_a" : "spring_b")
                    + " sableId=" + subLevel.getUniqueId()
                    + " reason=missingRigidBodyHandle");
            return 0;
        }
        final Vector3d before = new Vector3d(subLevel.logicalPose().position());
        final Vector3d after = new Vector3d(before)
                .add(DoubleArgumentType.getDouble(ctx, "dx"),
                        DoubleArgumentType.getDouble(ctx, "dy"),
                        DoubleArgumentType.getDouble(ctx, "dz"));
        handle.teleport(after, subLevel.logicalPose().orientation());
        send(source, "SABLE_M23_NUDGE status=PASS target=" + (fixtureIndex == 0 ? "spring_a" : "spring_b")
                + " sableId=" + subLevel.getUniqueId()
                + " before=" + before
                + " after=" + after);
        return 1;
    }

    private static SpringRelation findActiveSableToSableSpring(final CommandSourceStack source) {
        final List<SpringRelation> relations = findActiveSableToSableSprings(source.getLevel());
        if (relations.isEmpty()) {
            return null;
        }
        final Vector3d sourcePos = new Vector3d(source.getPosition().x, source.getPosition().y, source.getPosition().z);
        relations.sort(Comparator.comparingDouble(relation -> relation.midpoint().distanceSquared(sourcePos)));
        return relations.get(0);
    }

    private static List<SpringRelation> findActiveSableToSableSprings(final ServerLevel level) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return List.of();
        }
        final List<SpringRelation> relations = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            for (final BlockPos block : SimAssemblyHelper.collectBlocks(level, subLevel)) {
                if (!(level.getBlockEntity(block) instanceof final SpringBlockEntity spring)
                        || !spring.isActiveConstraint()
                        || spring.getPartnerPos() == null) {
                    continue;
                }
                final Object partnerContaining = Sable.HELPER.getContaining(level, spring.getPartnerPos());
                if (!(partnerContaining instanceof final ServerSubLevel partner)) {
                    continue;
                }
                final String logicalId = spring.logicalConstraintId();
                if (seen.add(logicalId)) {
                    relations.add(new SpringRelation(spring, subLevel, partner));
                }
            }
        }
        return relations;
    }

    private static List<ServerSubLevel> findPhysicsAssemblerBodies(final CommandSourceStack source)
            throws CommandSyntaxException {
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(source);
        final List<ServerSubLevel> bodies = new ArrayList<>();
        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (containsPhysicsAssembler(subLevel)) {
                bodies.add(subLevel);
            }
        }
        final Vector3d sourcePos = new Vector3d(source.getPosition().x, source.getPosition().y, source.getPosition().z);
        bodies.sort(Comparator.comparingDouble(body -> body.logicalPose().position().distanceSquared(sourcePos)));
        return bodies;
    }

    private static boolean containsPhysicsAssembler(final ServerSubLevel subLevel) {
        for (final BlockPos block : SimAssemblyHelper.collectBlocks(subLevel.getLevel(), subLevel)) {
            if (subLevel.getLevel().getBlockState(block).is(SimulatedBlocks.PHYSICS_ASSEMBLER.get())) {
                return true;
            }
        }
        return false;
    }

    private static String describeSpringRelation(final ServerLevel level, final SpringRelation relation) {
        final SpringBlockEntity spring = relation.spring();
        final BlockPos endpointA = spring.getBlockPos();
        final BlockPos endpointB = spring.getPartnerPos();
        final Vec3 visibleA = visibleVec(relation.owner(), endpointA);
        final Vec3 visibleB = visibleVec(relation.partner(), endpointB);
        final RigidBodyHandle handleA = RigidBodyHandle.of(relation.owner());
        final RigidBodyHandle handleB = RigidBodyHandle.of(relation.partner());
        final boolean actorRegistered = hasSpringActor(relation.owner(), endpointA);
        final double currentLength = visibleA.distanceTo(visibleB);
        final double extension = currentLength - spring.getDesiredLength();
        return "family=spring runtimeState=" + (relation.sameBody() ? "ERROR_SAME_BODY" : "ACTIVE")
                + " constraintMode=SABLE_TO_SABLE"
                + " logicalConstraintId=" + spring.logicalConstraintId()
                + " bodyA=" + relation.owner().getUniqueId()
                + " bodyB=" + relation.partner().getUniqueId()
                + " sameBody=" + relation.sameBody()
                + " bodyAHandleValid=" + (handleA != null && handleA.isValid())
                + " bodyBHandleValid=" + (handleB != null && handleB.isValid())
                + " endpointALocal=" + localPos(relation.owner(), endpointA)
                + " endpointBLocal=" + localPos(relation.partner(), endpointB)
                + " endpointARaw=" + endpointA.toShortString()
                + " endpointBRaw=" + shortPos(endpointB)
                + " endpointAVisible=" + visibleA
                + " endpointBVisible=" + visibleB
                + " restLength=" + spring.getDesiredLength()
                + " currentLength=" + currentLength
                + " extension=" + extension
                + " forceActorRegistered=" + actorRegistered
                + " activeConstraintCountA=" + SimAssemblyHelper.activeSpringConstraintIds(level, relation.owner()).size()
                + " activeConstraintCountB=" + SimAssemblyHelper.activeSpringConstraintIds(level, relation.partner()).size();
    }

    private static boolean hasSpringActor(final ServerSubLevel subLevel, final BlockPos pos) {
        for (final Object actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof final SpringBlockEntity spring && spring.getBlockPos().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos findPhysicsAssemblerRaw(final ServerSubLevel subLevel) {
        for (final BlockPos block : SimAssemblyHelper.collectBlocks(subLevel.getLevel(), subLevel)) {
            if (subLevel.getLevel().getBlockState(block).is(SimulatedBlocks.PHYSICS_ASSEMBLER.get())) {
                return block;
            }
        }
        return null;
    }

    private static BlockPos findVisibleSupportRaw(final ServerSubLevel subLevel, final Direction preferredDirection) {
        final BlockPos assembler = findPhysicsAssemblerRaw(subLevel);
        if (assembler != null) {
            final BlockPos candidate = assembler.below().relative(preferredDirection);
            if (!subLevel.getLevel().getBlockState(candidate).isAir()) {
                return candidate;
            }
        }
        return null;
    }

    private static BlockPos localPos(final ServerSubLevel subLevel, final BlockPos raw) {
        return raw == null ? null : raw.subtract(subLevel.getPlot().getCenterBlock());
    }

    private static Vec3 visibleVec(final ServerSubLevel subLevel, final BlockPos raw) {
        if (raw == null) {
            return Vec3.ZERO;
        }
        return subLevel.logicalPose().transformPosition(raw.getCenter());
    }

    private static String visibleBlockPos(final ServerSubLevel subLevel, final BlockPos raw) {
        if (raw == null) {
            return "null";
        }
        return BlockPos.containing(visibleVec(subLevel, raw)).toShortString();
    }

    private static String shortPos(final BlockPos pos) {
        return pos == null ? "null" : pos.toShortString();
    }

    private record SpringRelation(SpringBlockEntity spring, ServerSubLevel owner, ServerSubLevel partner) {
        private boolean sameBody() {
            return this.owner == this.partner;
        }

        private Vector3d midpoint() {
            final Vec3 a = visibleVec(this.owner, this.spring.getBlockPos());
            final Vec3 b = visibleVec(this.partner, this.spring.getPartnerPos());
            return new Vector3d((a.x + b.x) * 0.5D, (a.y + b.y) * 0.5D, (a.z + b.z) * 0.5D);
        }
    }

    private record BodyCountAudit(int storedBlockCount, int nonAirBlockCount, int payloadBlockCount,
                                  int blockEntityCount, int trackingPointCount, int actorCount,
                                  String rawBounds, int boundsVolume, double mass,
                                  int collisionUploadedBlockCount) {
    }

    private static void send(final CommandSourceStack source, final String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }
}
