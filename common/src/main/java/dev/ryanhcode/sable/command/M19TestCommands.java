package dev.ryanhcode.sable.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.command.SableCommandHelper;
import dev.ryanhcode.sable.api.command.SubLevelArgumentType;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelBlockEditHelper;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.util.SubLevelBlockStateLookup;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** M19 Create placement-helper ghost-preview harness; visual PASS is user-observed. */
public final class M19TestCommands {
    private static final ResourceLocation SHAFT_ID = new ResourceLocation("create", "shaft");
    private static final ResourceLocation COGWHEEL_ID = new ResourceLocation("create", "cogwheel");
    private static final ResourceLocation LARGE_COGWHEEL_ID = new ResourceLocation("create", "large_cogwheel");
    private static final BlockPos SHAFT_CANARY_LOCAL = new BlockPos(0, 1, 0);
    private static final BlockPos SHAFT_EXPECTED_PREVIEW_LOCAL = new BlockPos(1, 1, 0);
    private static final BlockPos COG_CANARY_LOCAL = new BlockPos(0, 1, 3);
    private static final BlockPos COG_REFERENCE_EMPTY_LOCAL = new BlockPos(1, 1, 3);
    private static final BlockPos LARGE_COG_CANARY_LOCAL = new BlockPos(0, 1, 6);
    private static final int GIVE_COUNT = 16;
    private static final DynamicCommandExceptionType ERROR_M19_FAILED = new DynamicCommandExceptionType(
            message -> Component.literal("SABLE_M19 command failed: " + message));

    private M19TestCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m19")
                .then(Commands.literal("spawn_placement")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(M19TestCommands::spawnPlacement)))
                .then(Commands.literal("validate")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M19TestCommands::validate)))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M19TestCommands::inspect)))
                .then(Commands.literal("give_shaft").executes(context -> giveItem(context, SHAFT_ID, "shaft")))
                .then(Commands.literal("give_cogwheel").executes(context -> giveItem(context, COGWHEEL_ID, "cogwheel")))
                .then(Commands.literal("give_large_cogwheel").executes(context -> giveItem(context, LARGE_COGWHEEL_ID, "large_cogwheel")))
                .then(Commands.literal("test_translate_parent")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M19TestCommands::testTranslateParent)))
                .then(Commands.literal("test_rotate_parent")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M19TestCommands::testRotateParent)))
                .then(Commands.literal("acceptance")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M19TestCommands::acceptance))));
    }

    private static int spawnPlacement(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(context);
        final String name = StringArgumentType.getString(context, "name");
        ServerSubLevel subLevel = null;
        try {
            subLevel = createEmptySubLevel(context, container, name);
            final List<SubLevelBlockEditHelper.BlockChange> changes = applyBlocks(subLevel, placementFixtureBlocks());
            SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
            subLevel.updateLastPose();
            final FixtureCheck check = checkFixture(subLevel);
            final String line = "SABLE_M19_SPAWN name=" + name
                    + " uuid=" + subLevel.getUniqueId()
                    + " status=" + pass(check.ready())
                    + " shaftCanaryLocal=" + fmt(SHAFT_CANARY_LOCAL)
                    + " shaftExpectedPreviewLocal=" + fmt(SHAFT_EXPECTED_PREVIEW_LOCAL)
                    + " cogCanaryLocal=" + fmt(COG_CANARY_LOCAL)
                    + " largeCogCanaryLocal=" + fmt(LARGE_COG_CANARY_LOCAL)
                    + " previewPath=PlacementClient->IPlacementHelper->GhostBlocks->TransparentGhostBlockRenderer"
                    + " visualAcceptance=USER_OBSERVED_RUNTIME_REQUIRED"
                    + " failures=" + check.failures();
            send(context, line);
            Sable.LOGGER.info(line);
            return check.ready() ? 1 : 0;
        } catch (final RuntimeException exception) {
            rollbackSpawnSubLevel(container, subLevel, name, exception);
            throw commandFailure(exception);
        }
    }

    private static int validate(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck check = checkFixture(subLevel);
        final String line = "SABLE_M19_VALIDATE id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " status=" + pass(check.ready())
                + " FIXTURE_LAYOUT=" + pass(check.layoutValid())
                + " SHAFT_CANARY=" + pass(check.shaftReady())
                + " COG_CANARY=" + pass(check.cogReady())
                + " LARGE_COG_CANARY=" + pass(check.largeCogReady())
                + " CLIENT_PREVIEW=UNVERIFIED_USER_OBSERVATION_REQUIRED"
                + " hiddenPlotPoseTranslation=false"
                + " failures=" + check.failures();
        send(context, line);
        Sable.LOGGER.info(line);
        return check.ready() ? 1 : 0;
    }

    private static int inspect(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureStats stats = inspectStats(subLevel);
        final String summary = "SABLE_M19_INSPECT id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " blockCount=" + stats.blockCount()
                + " mass=" + fmt(stats.mass())
                + " posePosition=" + formatVector(stats.position())
                + " linearVelocity=" + formatVector(stats.linearVelocity())
                + " angularVelocity=" + formatVector(stats.angularVelocity())
                + " rawPlotOrigin=" + fmt(subLevel.getPlot().getCenterBlock())
                + " currentClientTarget=CLIENT_RUNTIME_ONLY"
                + " previewRenderPathReached=CLIENT_LOG_SABLE_M19_GHOST"
                + " placementHelperMatch=CLIENT_RUNTIME_ONLY"
                + " computedPlacementOffset=CLIENT_RUNTIME_ONLY"
                + " hiddenPlotPoseTranslation=false";
        send(context, summary);
        Sable.LOGGER.info(summary);
        for (final LayoutExpectation expectation : layoutExpectations()) {
            final BlockState state = getLocalBlockState(subLevel, expectation.localPos());
            final String line = "SABLE_M19_LAYOUT id=" + subLevel.getUniqueId()
                    + " localPos=" + fmt(expectation.localPos())
                    + " rawPos=" + fmt(toPlot(subLevel, expectation.localPos()))
                    + " blockId=" + blockId(state)
                    + " state=" + state
                    + " role=" + expectation.role()
                    + " expected=" + expectation.expected()
                    + " valid=" + expectation.valid(state);
            send(context, line);
            Sable.LOGGER.info(line);
        }
        return 1;
    }

    private static int giveItem(final CommandContext<CommandSourceStack> context,
                                final ResourceLocation id,
                                final String label) throws CommandSyntaxException {
        final ServerPlayer player = context.getSource().getPlayerOrException();
        final Block block = requireBlock(id);
        final ItemStack stack = new ItemStack(block.asItem(), GIVE_COUNT);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        final String line = "SABLE_M19_GIVE item=" + id
                + " label=" + label
                + " count=" + GIVE_COUNT
                + " hand=MAIN_HAND"
                + " note=aim_at_matching_Sable_canary_before_clicking";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int testTranslateParent(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setBodyVelocity(context, "translate_parent", new Vector3d(0.0, 1.0, 0.0), new Vector3d());
    }

    private static int testRotateParent(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return setBodyVelocity(context, "rotate_parent", new Vector3d(),
                new Vector3d(0.0, Math.toRadians(20.0), 0.0));
    }

    private static int acceptance(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck check = checkFixture(subLevel);
        final String line = "SABLE_M19_ACCEPTANCE id=" + subLevel.getUniqueId()
                + " machineState=" + pass(check.ready())
                + " visualPass=USER_OBSERVED_REQUIRED"
                + " expectedShaftPreview=aim_shaft_at_" + fmt(SHAFT_CANARY_LOCAL)
                + "_ghost_at_" + fmt(SHAFT_EXPECTED_PREVIEW_LOCAL)
                + " expectedCogPreview=aim_cogwheel_at_" + fmt(COG_CANARY_LOCAL)
                + "_ghost_on_valid_adjacent_empty_space"
                + " expectedLargeCogPreview=aim_large_cogwheel_at_" + fmt(LARGE_COG_CANARY_LOCAL)
                + "_ghost_on_valid_adjacent_empty_space"
                + " normalWorldPassThrough=UNCHANGED_BY_SABLE_GHOST_GATE"
                + " hiddenPlotPoseTranslation=false"
                + " failures=" + check.failures();
        send(context, line);
        Sable.LOGGER.info(line);
        return check.ready() ? 1 : 0;
    }

    private static int setBodyVelocity(final CommandContext<CommandSourceStack> context,
                                       final String preset,
                                       final Vector3dc linearVelocity,
                                       final Vector3dc angularVelocityRad) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final SubLevelPhysicsSystem physicsSystem = SableCommandHelper.requireSubLevelPhysicsSystem(context);
        final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        handle.setLinearAndAngularVelocity(linearVelocity, angularVelocityRad);
        final String line = "SABLE_M19_TEST_PARENT id=" + subLevel.getUniqueId()
                + " preset=" + preset
                + " result=APPLIED_M10_PHYSICS_VELOCITY"
                + " requestedLinear=" + formatVector(linearVelocity)
                + " requestedAngularRadS=" + formatVector(angularVelocityRad)
                + " hiddenStorageUnchanged=true"
                + " anchored=false";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static Map<BlockPos, BlockState> placementFixtureBlocks() {
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int x = -2; x <= 3; x++) {
            for (int z = -1; z <= 7; z++) {
                blocks.put(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        blocks.put(SHAFT_CANARY_LOCAL, setProperty(requireBlockState(SHAFT_ID), "axis", "x"));
        blocks.put(COG_CANARY_LOCAL, setProperty(requireBlockState(COGWHEEL_ID), "axis", "y"));
        blocks.put(LARGE_COG_CANARY_LOCAL, setProperty(requireBlockState(LARGE_COGWHEEL_ID), "axis", "y"));
        return blocks;
    }

    private static List<LayoutExpectation> layoutExpectations() {
        return List.of(
                new LayoutExpectation(SHAFT_CANARY_LOCAL, "shaft_chaining_canary",
                        "create:shaft[axis=x], hold shaft and aim at either shaft end",
                        state -> blockIdMatches(state, SHAFT_ID) && propertyMatches(state, "axis", "x")),
                new LayoutExpectation(SHAFT_EXPECTED_PREVIEW_LOCAL, "shaft_primary_empty_preview_cell",
                        "air before placement; normal Create shaft helper should preview here when aimed along +X",
                        BlockState::isAir),
                new LayoutExpectation(COG_CANARY_LOCAL, "small_cogwheel_canary",
                        "create:cogwheel[axis=y], hold cogwheel and aim at side face",
                        state -> blockIdMatches(state, COGWHEEL_ID) && propertyMatches(state, "axis", "y")),
                new LayoutExpectation(COG_REFERENCE_EMPTY_LOCAL, "small_cogwheel_reference_empty_cell",
                        "air candidate near the small cogwheel",
                        BlockState::isAir),
                new LayoutExpectation(LARGE_COG_CANARY_LOCAL, "large_cogwheel_canary",
                        "create:large_cogwheel[axis=y], hold large cogwheel and aim at side/top as Create allows",
                        state -> blockIdMatches(state, LARGE_COGWHEEL_ID) && propertyMatches(state, "axis", "y")));
    }

    private static FixtureCheck checkFixture(final ServerSubLevel subLevel) {
        final List<String> failures = new ArrayList<>();
        boolean shaftReady = false;
        boolean cogReady = false;
        boolean largeCogReady = false;
        for (final LayoutExpectation expectation : layoutExpectations()) {
            final BlockState state = getLocalBlockState(subLevel, expectation.localPos());
            if (!expectation.valid(state)) {
                failures.add("invalid_" + expectation.role() + "_at_local_" + fmt(expectation.localPos())
                        + "_state_" + state);
            }
            if (expectation.localPos().equals(SHAFT_CANARY_LOCAL) && expectation.valid(state)) {
                shaftReady = true;
            }
            if (expectation.localPos().equals(COG_CANARY_LOCAL) && expectation.valid(state)) {
                cogReady = true;
            }
            if (expectation.localPos().equals(LARGE_COG_CANARY_LOCAL) && expectation.valid(state)) {
                largeCogReady = true;
            }
        }
        final boolean layoutValid = failures.isEmpty();
        return new FixtureCheck(failures, layoutValid, shaftReady, cogReady, largeCogReady,
                layoutValid && shaftReady && cogReady && largeCogReady);
    }

    private static ServerSubLevel createEmptySubLevel(final CommandContext<CommandSourceStack> context,
                                                      final ServerSubLevelContainer container,
                                                      final String name) {
        final Vec3 spawnPos = Vec3.atCenterOf(BlockPos.containing(context.getSource().getPosition()));
        final Pose3d pose = new Pose3d();
        pose.position().set(spawnPos.x, spawnPos.y, spawnPos.z);
        final ServerSubLevel subLevel = (ServerSubLevel) container.allocateNewSubLevel(pose);
        subLevel.setName(name);
        subLevel.getPlot().newEmptyChunk(subLevel.getPlot().getCenterChunk());
        return subLevel;
    }

    private static List<SubLevelBlockEditHelper.BlockChange> applyBlocks(final ServerSubLevel subLevel,
                                                                         final Map<BlockPos, BlockState> blocks) {
        final List<SubLevelBlockEditHelper.BlockChange> changes = new ArrayList<>(blocks.size());
        for (final Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            changes.add(SubLevelBlockEditHelper.setLocalBlock(subLevel, entry.getKey(), entry.getValue(), 3, false));
        }
        return changes;
    }

    private static FixtureStats inspectStats(final ServerSubLevel subLevel) {
        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(subLevel.getLevel());
        final RigidBodyHandle handle = physicsSystem == null ? null : physicsSystem.getPhysicsHandle(subLevel);
        final MassData mass = subLevel.getMassTracker();
        return new FixtureStats(countNonAirBlocks(subLevel),
                mass == null ? Double.NaN : mass.getMass(),
                new Vector3d(subLevel.logicalPose().position()),
                handle == null ? new Vector3d(Double.NaN) : handle.getLinearVelocity(new Vector3d()),
                handle == null ? new Vector3d(Double.NaN) : handle.getAngularVelocity(new Vector3d()));
    }

    private static int countNonAirBlocks(final ServerSubLevel subLevel) {
        int count = 0;
        for (final PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            final LevelChunk chunk = holder.getChunk();
            for (int sectionIndex = 0; sectionIndex < chunk.getSectionsCount(); sectionIndex++) {
                final LevelChunkSection section = chunk.getSection(sectionIndex);
                if (section.hasOnlyAir()) {
                    continue;
                }
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            if (!section.getBlockState(x, y, z).isAir()) {
                                count++;
                            }
                        }
                    }
                }
            }
        }
        return count;
    }

    private static BlockState getLocalBlockState(final ServerSubLevel subLevel, final BlockPos localPos) {
        return SubLevelBlockStateLookup.getBlockStateOrAir(subLevel, toPlot(subLevel, localPos));
    }

    private static BlockPos toPlot(final ServerSubLevel subLevel, final BlockPos localPos) {
        return SubLevelBlockEditHelper.localOffsetToPlotBlock(subLevel, localPos);
    }

    private static BlockState requireBlockState(final ResourceLocation id) {
        return requireBlock(id).defaultBlockState();
    }

    private static Block requireBlock(final ResourceLocation id) {
        final Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
        if (block.isEmpty() || block.get() == Blocks.AIR) {
            throw new IllegalStateException("Required block is not registered: " + id);
        }
        return block.get();
    }

    private static BlockState setProperty(final BlockState state, final String propertyName, final String valueName) {
        for (final Property<?> property : state.getProperties()) {
            if (!property.getName().equals(propertyName)) {
                continue;
            }
            final Optional<?> value = property.getValue(valueName);
            if (value.isEmpty()) {
                throw new IllegalStateException("Property " + propertyName + " on " + state
                        + " does not accept value " + valueName);
            }
            return setPropertyUnchecked(state, property, (Comparable<?>) value.get());
        }
        throw new IllegalStateException("Block state " + state + " is missing property " + propertyName);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState setPropertyUnchecked(final BlockState state,
                                                   final Property property,
                                                   final Comparable value) {
        return state.setValue(property, value);
    }

    private static boolean blockIdMatches(final BlockState state, final ResourceLocation blockId) {
        return blockId.equals(blockId(state));
    }

    private static ResourceLocation blockId(final BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static boolean propertyMatches(final BlockState state, final String propertyName, final String valueName) {
        for (final Property<?> property : state.getProperties()) {
            if (property.getName().equals(propertyName)) {
                return String.valueOf(state.getValue(property)).equalsIgnoreCase(valueName);
            }
        }
        return false;
    }

    private static void rollbackSpawnSubLevel(final ServerSubLevelContainer container, final @Nullable SubLevel subLevel,
                                              final String name, final Throwable failure) {
        if (subLevel == null || subLevel.isRemoved()) {
            return;
        }
        Sable.LOGGER.warn("SABLE_M19 phase=rollback_begin name={} id={} reason={}",
                name, subLevel.getUniqueId(), failure.getClass().getSimpleName());
        try {
            container.removeSubLevel(subLevel, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        } catch (final Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static CommandSyntaxException commandFailure(final RuntimeException exception) {
        Sable.LOGGER.error("SABLE_M19 phase=failed", exception);
        final String message = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        return ERROR_M19_FAILED.create(message);
    }

    private static void send(final CommandContext<CommandSourceStack> context, final String line) {
        context.getSource().sendSuccess(() -> Component.literal(line), false);
    }

    private static String nameOrNone(final ServerSubLevel subLevel) {
        return subLevel.getName() != null ? subLevel.getName() : "<none>";
    }

    private static String pass(final boolean value) {
        return value ? "PASS" : "FAIL";
    }

    private static String fmt(final BlockPos pos) {
        return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    private static String fmt(final double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.6f", value) : "nan";
    }

    private static String formatVector(final Vector3dc vector) {
        return String.format(Locale.ROOT, "(%.6f,%.6f,%.6f)", vector.x(), vector.y(), vector.z());
    }

    private record FixtureStats(int blockCount, double mass, Vector3dc position, Vector3dc linearVelocity,
                                Vector3dc angularVelocity) {
    }

    private record FixtureCheck(List<String> failures, boolean layoutValid, boolean shaftReady,
                                boolean cogReady, boolean largeCogReady, boolean ready) {
    }

    private record LayoutExpectation(BlockPos localPos, String role, String expected,
                                     java.util.function.Predicate<BlockState> validator) {
        private boolean valid(final BlockState state) {
            return this.validator.test(state);
        }
    }
}
