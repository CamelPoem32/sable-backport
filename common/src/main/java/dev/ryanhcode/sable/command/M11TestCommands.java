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
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelBlockEditHelper;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.util.SubLevelBlockStateLookup;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Diagnostic surface for the generic M11 BlockEntity baseline. */
public final class M11TestCommands {
    private static final String CREATE_PACKAGE = "com.simibubi.create.";
    private static final ResourceLocation CREATIVE_MOTOR_ID = new ResourceLocation("create", "creative_motor");
    private static final ResourceLocation SHAFT_ID = new ResourceLocation("create", "shaft");
    private static final BlockPos KINETIC_MOTOR_LOCAL = new BlockPos(0, 0, 0);
    private static final BlockPos KINETIC_SHAFT_ONE_LOCAL = new BlockPos(1, 0, 0);
    private static final BlockPos KINETIC_SHAFT_TWO_LOCAL = new BlockPos(2, 0, 0);
    private static final Map<BlockPos, String> KINETIC_EXPECTED_BLOCKS = Map.of(
            KINETIC_MOTOR_LOCAL, "create:creative_motor[facing=east]",
            KINETIC_SHAFT_ONE_LOCAL, "create:shaft[axis=x]",
            KINETIC_SHAFT_TWO_LOCAL, "create:shaft[axis=x]"
    );
    private static final DynamicCommandExceptionType ERROR_M11_FAILED = new DynamicCommandExceptionType(
            message -> Component.literal("SABLE_M11 command failed: " + message));

    private M11TestCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m11")
                .then(Commands.literal("spawn_kinetic")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(M11TestCommands::spawnKinetic)))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M11TestCommands::inspect)))
                .then(Commands.literal("validate_kinetic")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M11TestCommands::validateKinetic))));
    }

    private static int spawnKinetic(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final CommandSourceStack source = context.getSource();
        final String name = StringArgumentType.getString(context, "name");
        final ServerSubLevelContainer plotContainer = SableCommandHelper.requireSubLevelContainer(context);
        ServerSubLevel subLevel = null;

        Sable.LOGGER.info("SABLE_M11_KINETIC phase=spawn_begin command=spawn_kinetic name={} position={}",
                name, source.getPosition());
        try {
            final Vec3 spawnPos = Vec3.atCenterOf(BlockPos.containing(source.getPosition()));
            final Pose3d pose = new Pose3d();
            pose.position().set(spawnPos.x, spawnPos.y, spawnPos.z);

            subLevel = (ServerSubLevel) plotContainer.allocateNewSubLevel(pose);
            subLevel.setName(name);

            final ServerLevelPlot plot = subLevel.getPlot();
            plot.newEmptyChunk(plot.getCenterChunk());

            final Map<BlockPos, BlockState> blocks = kineticTopology();
            final List<SubLevelBlockEditHelper.BlockChange> changes = new ObjectArrayList<>(blocks.size());
            for (final Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                changes.add(SubLevelBlockEditHelper.setLocalBlock(subLevel, entry.getKey(), entry.getValue(), 3, false));
            }

            Sable.LOGGER.info("SABLE_M11_KINETIC phase=blocks_set id={} name={} blockCount={} topology={}",
                    subLevel.getUniqueId(), name, changes.size(), KINETIC_EXPECTED_BLOCKS);
            SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
            subLevel.updateLastPose();

            Sable.LOGGER.info("SABLE_M11_KINETIC phase=finalized id={} name={} mass={} bounds={} topology=motor_east_to_two_x_shafts",
                    subLevel.getUniqueId(), name, subLevel.getMassTracker().getMass(), plot.getBoundingBox());

            final ServerSubLevel created = subLevel;
            source.sendSuccess(() -> Component.literal("SABLE_M11_SPAWN_KINETIC id=" + created.getUniqueId()
                    + " name=" + name
                    + " topology=motor_east_to_two_x_shafts"
                    + " localBlocks=" + KINETIC_EXPECTED_BLOCKS
                    + " note=Create_kinetics_may_attach_after_server_ticks"), false);
            return 1;
        } catch (final RuntimeException exception) {
            rollbackSpawnSubLevel(plotContainer, subLevel, name, exception);
            throw commandFailure(exception);
        }
    }

    private static int inspect(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final List<BlockEntity> blockEntities = collectBlockEntities(subLevel);
        final long createCount = blockEntities.stream().filter(M11TestCommands::isCreateBlockEntity).count();

        send(context, "SABLE_M11_INSPECT");
        send(context, "id=" + subLevel.getUniqueId());
        send(context, "name=" + (subLevel.getName() == null ? "" : subLevel.getName()));
        send(context, "blockCount=" + countNonAirBlocks(subLevel));
        send(context, "blockEntityCount=" + blockEntities.size());
        send(context, "createBlockEntityCount=" + createCount);
        send(context, "kineticTopologyCandidates=" + findDeterministicKineticTopologies(subLevel, collectKineticSnapshots(subLevel)).size());

        for (final BlockEntity blockEntity : blockEntities) {
            final BlockPos plotPos = blockEntity.getBlockPos();
            final BlockPos localPos = plotPos.subtract(subLevel.getPlot().getCenterBlock());
            final ResourceLocation typeId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
            final Level level = blockEntity.getLevel();
            final BlockState blockState = blockEntity.getBlockState();
            final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock());
            final boolean create = isCreateBlockEntity(blockEntity);
            final StringBuilder line = new StringBuilder("SABLE_M11_BE")
                    .append(" posLocal=").append(localPos)
                    .append(" posPlot=").append(plotPos)
                    .append(" block=").append(blockId)
                    .append(" blockState=").append(blockState)
                    .append(" type=").append(typeId)
                    .append(" class=").append(blockEntity.getClass().getName())
                    .append(" create=").append(create)
                    .append(" removed=").append(blockEntity.isRemoved())
                    .append(" levelPresent=").append(level != null)
                    .append(" levelClass=").append(level == null ? "null" : level.getClass().getName())
                    .append(" side=").append(level == null ? "unknown" : (level.isClientSide ? "client" : "server"))
                    .append(" tickerPresent=").append(tickerPresent(blockEntity, level));

            if (create) {
                line.append(" initialized=").append(readField(blockEntity, "initialized"))
                        .append(" lazyTickRate=").append(readField(blockEntity, "lazyTickRate"))
                        .append(" speed=").append(invokeNoArg(blockEntity, "getSpeed"))
                        .append(" theoreticalSpeed=").append(invokeNoArg(blockEntity, "getTheoreticalSpeed"))
                        .append(" hasNetwork=").append(invokeNoArg(blockEntity, "hasNetwork"))
                        .append(" networkId=").append(readField(blockEntity, "network"))
                        .append(" hasSource=").append(invokeNoArg(blockEntity, "hasSource"))
                        .append(" source=").append(formatMaybeLocalSource(subLevel, readFieldRaw(blockEntity, "source")))
                        .append(" networkDirty=").append(readField(blockEntity, "networkDirty"))
                        .append(" updateSpeed=").append(readField(blockEntity, "updateSpeed"))
                        .append(" stress=").append(readField(blockEntity, "stress"))
                        .append(" capacity=").append(readField(blockEntity, "capacity"))
                        .append(" lastStressApplied=").append(readField(blockEntity, "lastStressApplied"))
                        .append(" lastCapacityProvided=").append(readField(blockEntity, "lastCapacityProvided"));
            }

            send(context, line.toString());
            Sable.LOGGER.info(line.toString());
        }

        send(context, "clientCounterpart=unavailable_server_command");
        Sable.LOGGER.info("SABLE_M11_BE_SUMMARY id={} blockEntities={} createBlockEntities={}",
                subLevel.getUniqueId(), blockEntities.size(), createCount);
        return blockEntities.size();
    }

    private static int validateKinetic(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final List<String> failures = new ObjectArrayList<>();
        final Map<BlockPos, KineticSnapshot> snapshots = collectKineticSnapshots(subLevel);
        final List<DeterministicKineticTopology> topologies = findDeterministicKineticTopologies(subLevel, snapshots);
        final DeterministicKineticTopology topology = topologies.size() == 1 ? topologies.get(0) : null;

        final KineticSnapshot motor = topology == null ? null : topology.motor();
        final KineticSnapshot shaftOne = topology == null ? null : topology.shaftOne();
        final KineticSnapshot shaftTwo = topology == null ? null : topology.shaftTwo();
        final BlockPos motorLocal = topology == null ? KINETIC_MOTOR_LOCAL : topology.motor().localPos();
        final BlockPos shaftOneLocal = topology == null ? KINETIC_SHAFT_ONE_LOCAL : topology.shaftOne().localPos();
        final BlockPos shaftTwoLocal = topology == null ? KINETIC_SHAFT_TWO_LOCAL : topology.shaftTwo().localPos();

        requireInvariant(!subLevel.isRemoved(), "sublevel_not_removed", failures);
        requireInvariant(!topologies.isEmpty(), "kinetic_topology_not_found", failures);
        requireInvariant(topologies.size() <= 1, "kinetic_topology_ambiguous", failures);
        requireExpectedState(subLevel, motorLocal, CREATIVE_MOTOR_ID, "facing", "east", failures);
        requireExpectedState(subLevel, shaftOneLocal, SHAFT_ID, "axis", "x", failures);
        requireExpectedState(subLevel, shaftTwoLocal, SHAFT_ID, "axis", "x", failures);
        requireKinetic(motor, "motor", failures);
        requireKinetic(shaftOne, "shaft1", failures);
        requireKinetic(shaftTwo, "shaft2", failures);

        if (motor != null && shaftOne != null && shaftTwo != null) {
            requireInvariant(Math.abs(motor.speed()) > 0.001, "motor_speed_zero", failures);
            requireInvariant(Math.abs(shaftOne.speed()) > 0.001, "shaft1_speed_zero", failures);
            requireInvariant(Math.abs(shaftTwo.speed()) > 0.001, "shaft2_speed_zero", failures);
            requireInvariant(approximatelyEqual(motor.speed(), shaftOne.speed()), "shaft1_speed_ratio_unexpected", failures);
            requireInvariant(approximatelyEqual(motor.speed(), shaftTwo.speed()), "shaft2_speed_ratio_unexpected", failures);

            if (motor.network() != null && shaftOne.network() != null && shaftTwo.network() != null) {
                requireInvariant(motor.network().equals(shaftOne.network()), "shaft1_network_mismatch", failures);
                requireInvariant(motor.network().equals(shaftTwo.network()), "shaft2_network_mismatch", failures);
            } else {
                requireInvariant(motor.hasNetwork() && shaftOne.hasNetwork() && shaftTwo.hasNetwork(), "kinetic_network_missing", failures);
            }
        }

        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(subLevel.getLevel());
        final RigidBodyHandle handle = physicsSystem == null ? null : physicsSystem.getPhysicsHandle(subLevel);
        requireInvariant(physicsSystem != null, "physicsSystem_missing", failures);
        requireInvariant(handle != null && handle.isValid(), "rigidBody_missing", failures);
        requireInvariant(physicsSystem != null && physicsSystem.getPipeline().isBodyRegistered(subLevel), "rigidBody_not_registered", failures);

        final String line = "SABLE_M11_KINETIC_VALIDATE status=" + (failures.isEmpty() ? "PASS" : "FAIL")
                + " id=" + subLevel.getUniqueId()
                + " reason=" + (failures.isEmpty() ? "none" : failures.get(0))
                + " topology=motor_east_to_two_x_shafts"
                + " expected=" + KINETIC_EXPECTED_BLOCKS
                + " discoveredMotorLocal=" + motorLocal
                + " discoveredShaft1Local=" + shaftOneLocal
                + " discoveredShaft2Local=" + shaftTwoLocal
                + " topologyCandidates=" + topologies.size()
                + " kineticPositions=" + snapshots.keySet()
                + " motorSpeed=" + fmt(motor == null ? Double.NaN : motor.speed())
                + " shaft1Speed=" + fmt(shaftOne == null ? Double.NaN : shaftOne.speed())
                + " shaft2Speed=" + fmt(shaftTwo == null ? Double.NaN : shaftTwo.speed())
                + " motorInitializedDiagnostic=" + (motor != null && motor.initialized())
                + " shaft1InitializedDiagnostic=" + (shaftOne != null && shaftOne.initialized())
                + " shaft2InitializedDiagnostic=" + (shaftTwo != null && shaftTwo.initialized())
                + " motorNetwork=" + (motor == null ? "missing" : motor.network())
                + " shaft1Network=" + (shaftOne == null ? "missing" : shaftOne.network())
                + " shaft2Network=" + (shaftTwo == null ? "missing" : shaftTwo.network())
                + " failures=" + failures;
        Sable.LOGGER.info(line);
        send(context, line);
        return failures.isEmpty() ? 1 : 0;
    }

    private static Map<BlockPos, BlockState> kineticTopology() {
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        blocks.put(KINETIC_MOTOR_LOCAL, setProperty(requireBlockState(CREATIVE_MOTOR_ID), "facing", "east"));
        blocks.put(KINETIC_SHAFT_ONE_LOCAL, setProperty(requireBlockState(SHAFT_ID), "axis", "x"));
        blocks.put(KINETIC_SHAFT_TWO_LOCAL, setProperty(requireBlockState(SHAFT_ID), "axis", "x"));
        return blocks;
    }

    private static BlockState requireBlockState(final ResourceLocation id) {
        final Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
        if (block.isEmpty() || block.get() == Blocks.AIR) {
            throw new IllegalStateException("Required block is not registered: " + id);
        }
        return block.get().defaultBlockState();
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
    private static BlockState setPropertyUnchecked(final BlockState state, final Property property,
                                                   final Comparable value) {
        return state.setValue(property, value);
    }

    private static Map<BlockPos, KineticSnapshot> collectKineticSnapshots(final ServerSubLevel subLevel) {
        final Map<BlockPos, KineticSnapshot> result = new LinkedHashMap<>();
        for (final BlockEntity blockEntity : collectBlockEntities(subLevel)) {
            if (!isKineticBlockEntity(blockEntity)) {
                continue;
            }

            final BlockPos plotPos = blockEntity.getBlockPos();
            final BlockPos localPos = plotPos.subtract(subLevel.getPlot().getCenterBlock());
            result.put(localPos, new KineticSnapshot(
                    blockEntity,
                    localPos,
                    asBoolean(readFieldRaw(blockEntity, "initialized")),
                    asDouble(invokeNoArgRaw(blockEntity, "getSpeed")),
                    asDouble(invokeNoArgRaw(blockEntity, "getTheoreticalSpeed")),
                    asBoolean(invokeNoArgRaw(blockEntity, "hasNetwork")),
                    readFieldRaw(blockEntity, "network"),
                    asBoolean(invokeNoArgRaw(blockEntity, "hasSource")),
                    readFieldRaw(blockEntity, "source")));
        }
        return result;
    }

    private static List<DeterministicKineticTopology> findDeterministicKineticTopologies(
            final ServerSubLevel subLevel, final Map<BlockPos, KineticSnapshot> snapshots) {
        final List<DeterministicKineticTopology> result = new ObjectArrayList<>();
        final List<KineticSnapshot> orderedSnapshots = new ObjectArrayList<>(snapshots.values());
        orderedSnapshots.sort((first, second) -> compareBlockPos(first.localPos(), second.localPos()));

        for (final KineticSnapshot candidateMotor : orderedSnapshots) {
            if (!blockIdMatches(subLevel, candidateMotor.localPos(), CREATIVE_MOTOR_ID)) {
                continue;
            }

            final BlockPos shaftOneLocal = candidateMotor.localPos().relative(Direction.EAST);
            final BlockPos shaftTwoLocal = shaftOneLocal.relative(Direction.EAST);
            final KineticSnapshot shaftOne = snapshots.get(shaftOneLocal);
            final KineticSnapshot shaftTwo = snapshots.get(shaftTwoLocal);
            if (shaftOne != null && shaftTwo != null) {
                result.add(new DeterministicKineticTopology(candidateMotor, shaftOne, shaftTwo));
            }
        }

        return result;
    }

    private static List<BlockEntity> collectBlockEntities(final ServerSubLevel subLevel) {
        final List<BlockEntity> result = new ArrayList<>();
        for (final PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            result.addAll(holder.getChunk().getBlockEntities().values());
        }
        return result;
    }

    private static int countNonAirBlocks(final ServerSubLevel subLevel) {
        int count = 0;
        for (final PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            final LevelChunk chunk = holder.getChunk();
            for (int sectionIndex = 0; sectionIndex < chunk.getSectionsCount(); sectionIndex++) {
                final var section = chunk.getSection(sectionIndex);
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

    private static boolean isCreateBlockEntity(final BlockEntity blockEntity) {
        for (Class<?> type = blockEntity.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().startsWith(CREATE_PACKAGE)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKineticBlockEntity(final BlockEntity blockEntity) {
        for (Class<?> type = blockEntity.getClass(); type != null; type = type.getSuperclass()) {
            if ("com.simibubi.create.content.kinetics.base.KineticBlockEntity".equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean tickerPresent(final BlockEntity blockEntity, final Level level) {
        if (level == null) {
            return false;
        }
        try {
            final Method getTicker = blockEntity.getBlockState().getClass()
                    .getMethod("getTicker", Level.class, BlockEntityType.class);
            return getTicker.invoke(blockEntity.getBlockState(), level, blockEntity.getType()) != null;
        } catch (final ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static String invokeNoArg(final BlockEntity blockEntity, final String methodName) {
        final Object value = invokeNoArgRaw(blockEntity, methodName);
        return value == null ? "unavailable" : String.valueOf(value);
    }

    private static Object invokeNoArgRaw(final BlockEntity blockEntity, final String methodName) {
        try {
            return blockEntity.getClass().getMethod(methodName).invoke(blockEntity);
        } catch (final ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static String readField(final BlockEntity blockEntity, final String fieldName) {
        final Object value = readFieldRaw(blockEntity, fieldName);
        return value == null ? "unavailable" : String.valueOf(value);
    }

    private static Object readFieldRaw(final BlockEntity blockEntity, final String fieldName) {
        for (Class<?> type = blockEntity.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(blockEntity);
            } catch (final NoSuchFieldException ignored) {
                // Common Create state may live on a superclass.
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String formatMaybeLocalSource(final ServerSubLevel subLevel, @Nullable final Object source) {
        if (source instanceof final BlockPos plotPos) {
            return plotPos.subtract(subLevel.getPlot().getCenterBlock()) + "/" + plotPos;
        }
        return source == null ? "unavailable" : String.valueOf(source);
    }

    private static void requireExpectedState(final ServerSubLevel subLevel, final BlockPos localPos,
                                             final ResourceLocation blockId, final String propertyName,
                                             final String propertyValue, final List<String> failures) {
        final BlockState state = getLocalBlockState(subLevel, localPos);
        requireInvariant(blockIdMatches(state, blockId),
                "missing_" + blockId.getPath() + "_at_" + localPos.toShortString(), failures);
        requireInvariant(propertyMatches(state, propertyName, propertyValue),
                "wrong_" + propertyName + "_at_" + localPos.toShortString(), failures);
    }

    private static BlockState getLocalBlockState(final ServerSubLevel subLevel, final BlockPos localPos) {
        final BlockPos plotPos = SubLevelBlockEditHelper.localOffsetToPlotBlock(subLevel, localPos);
        return SubLevelBlockStateLookup.getBlockStateOrAir(subLevel, plotPos);
    }

    private static boolean blockIdMatches(final ServerSubLevel subLevel, final BlockPos localPos,
                                          final ResourceLocation blockId) {
        return blockIdMatches(getLocalBlockState(subLevel, localPos), blockId);
    }

    private static boolean blockIdMatches(final BlockState state, final ResourceLocation blockId) {
        return blockId.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    private static boolean propertyMatches(final BlockState state, final String propertyName,
                                           final String propertyValue) {
        for (final Property<?> property : state.getProperties()) {
            if (property.getName().equals(propertyName)) {
                return propertyValue.equals(propertyValueName(state, property));
            }
        }
        return false;
    }

    private static String propertyValueName(final BlockState state, final Property<?> property) {
        return propertyValueNameUnchecked(state, property);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String propertyValueNameUnchecked(final BlockState state, final Property property) {
        return property.getName((Comparable) state.getValue(property));
    }

    private static void requireKinetic(@Nullable final KineticSnapshot snapshot, final String name,
                                       final List<String> failures) {
        requireInvariant(snapshot != null, name + "_be_missing", failures);
        if (snapshot == null) {
            return;
        }
        requireInvariant(!snapshot.blockEntity().isRemoved(), name + "_be_removed", failures);
        requireInvariant(Double.isFinite(snapshot.speed()), name + "_speed_not_finite", failures);
        requireInvariant(Double.isFinite(snapshot.theoreticalSpeed()), name + "_theoreticalSpeed_not_finite", failures);
    }

    private static void requireInvariant(final boolean condition, final String reason, final List<String> failures) {
        if (!condition) {
            failures.add(reason);
        }
    }

    private static boolean approximatelyEqual(final double expected, final double actual) {
        return Math.abs(expected - actual) <= Math.max(0.001, Math.abs(expected) * 0.001);
    }

    private static int compareBlockPos(final BlockPos first, final BlockPos second) {
        int result = Integer.compare(first.getX(), second.getX());
        if (result != 0) {
            return result;
        }
        result = Integer.compare(first.getY(), second.getY());
        if (result != 0) {
            return result;
        }
        return Integer.compare(first.getZ(), second.getZ());
    }

    private static boolean asBoolean(@Nullable final Object value) {
        return value instanceof Boolean && (Boolean) value;
    }

    private static double asDouble(@Nullable final Object value) {
        if (value instanceof final Number number) {
            return number.doubleValue();
        }
        return Double.NaN;
    }

    private static void rollbackSpawnSubLevel(final ServerSubLevelContainer plotContainer, final @Nullable SubLevel subLevel,
                                              final String name, final Throwable failure) {
        if (subLevel == null || subLevel.isRemoved()) {
            return;
        }

        Sable.LOGGER.warn("SABLE_M11_KINETIC phase=rollback_begin command=spawn_kinetic name={} id={} reason={}",
                name, subLevel.getUniqueId(), failure.getClass().getSimpleName());
        try {
            plotContainer.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
            Sable.LOGGER.warn("SABLE_M11_KINETIC phase=rollback_complete command=spawn_kinetic name={} id={}",
                    name, subLevel.getUniqueId());
        } catch (final Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
            Sable.LOGGER.error("SABLE_M11_KINETIC phase=rollback_failed command=spawn_kinetic name={} id={}",
                    name, subLevel.getUniqueId(), cleanupFailure);
        }
    }

    private static CommandSyntaxException commandFailure(final RuntimeException exception) {
        Sable.LOGGER.error("SABLE_M11 phase=failed", exception);
        final String message = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        return ERROR_M11_FAILED.create(message);
    }

    private static String fmt(final double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static void send(final CommandContext<CommandSourceStack> context, final String line) {
        context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(line), false);
    }

    private record KineticSnapshot(BlockEntity blockEntity, BlockPos localPos, boolean initialized, double speed,
                                   double theoreticalSpeed, boolean hasNetwork, @Nullable Object network,
                                   boolean hasSource, @Nullable Object source) {
    }

    private record DeterministicKineticTopology(KineticSnapshot motor, KineticSnapshot shaftOne,
                                                KineticSnapshot shaftTwo) {
    }
}
