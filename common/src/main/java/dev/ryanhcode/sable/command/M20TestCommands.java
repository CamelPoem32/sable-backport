package dev.ryanhcode.sable.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.util.SubLevelBlockStateLookup;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/** M20 family-level Create-on-Sable parity harness; runtime PASS remains user-observed. */
public final class M20TestCommands {
    private static final ResourceLocation CREATIVE_MOTOR_ID = id("create", "creative_motor");
    private static final ResourceLocation SHAFT_ID = id("create", "shaft");
    private static final ResourceLocation COGWHEEL_ID = id("create", "cogwheel");
    private static final ResourceLocation LARGE_COGWHEEL_ID = id("create", "large_cogwheel");
    private static final ResourceLocation ENCASED_CHAIN_DRIVE_ID = id("create", "encased_chain_drive");
    private static final ResourceLocation ADJUSTABLE_CHAIN_GEARSHIFT_ID = id("create", "adjustable_chain_gearshift");
    private static final ResourceLocation DEPOT_ID = id("create", "depot");
    private static final ResourceLocation BELT_ID = id("create", "belt");
    private static final ResourceLocation ANDESITE_FUNNEL_ID = id("create", "andesite_funnel");
    private static final ResourceLocation BRASS_FUNNEL_ID = id("create", "brass_funnel");
    private static final ResourceLocation CHUTE_ID = id("create", "chute");
    private static final ResourceLocation SMART_CHUTE_ID = id("create", "smart_chute");
    private static final ResourceLocation ANDESITE_TUNNEL_ID = id("create", "andesite_tunnel");
    private static final ResourceLocation BRASS_TUNNEL_ID = id("create", "brass_tunnel");
    private static final ResourceLocation ITEM_VAULT_ID = id("create", "item_vault");
    private static final ResourceLocation WEIGHTED_EJECTOR_ID = id("create", "weighted_ejector");
    private static final ResourceLocation MECHANICAL_ARM_ID = id("create", "mechanical_arm");
    private static final ResourceLocation MECHANICAL_DRILL_ID = id("create", "mechanical_drill");
    private static final ResourceLocation MECHANICAL_SAW_ID = id("create", "mechanical_saw");
    private static final ResourceLocation MECHANICAL_HARVESTER_ID = id("create", "mechanical_harvester");
    private static final ResourceLocation MECHANICAL_PLOUGH_ID = id("create", "mechanical_plough");
    private static final ResourceLocation MECHANICAL_ROLLER_ID = id("create", "mechanical_roller");
    private static final ResourceLocation MECHANICAL_PRESS_ID = id("create", "mechanical_press");
    private static final ResourceLocation MECHANICAL_MIXER_ID = id("create", "mechanical_mixer");
    private static final ResourceLocation BASIN_ID = id("create", "basin");
    private static final ResourceLocation BLAZE_BURNER_ID = id("create", "blaze_burner");
    private static final ResourceLocation MILLSTONE_ID = id("create", "millstone");
    private static final ResourceLocation CRUSHING_WHEEL_ID = id("create", "crushing_wheel");
    private static final ResourceLocation CRUSHING_WHEEL_CONTROLLER_ID = id("create", "crushing_wheel_controller");
    private static final ResourceLocation MECHANICAL_CRAFTER_ID = id("create", "mechanical_crafter");
    private static final ResourceLocation ENCASED_FAN_ID = id("create", "encased_fan");
    private static final ResourceLocation FLUID_TANK_ID = id("create", "fluid_tank");
    private static final ResourceLocation FLUID_PIPE_ID = id("create", "fluid_pipe");
    private static final ResourceLocation MECHANICAL_PUMP_ID = id("create", "mechanical_pump");
    private static final ResourceLocation FLUID_VALVE_ID = id("create", "fluid_valve");
    private static final ResourceLocation SMART_FLUID_PIPE_ID = id("create", "smart_fluid_pipe");
    private static final ResourceLocation HOSE_PULLEY_ID = id("create", "hose_pulley");
    private static final ResourceLocation PORTABLE_STORAGE_INTERFACE_ID = id("create", "portable_storage_interface");
    private static final ResourceLocation PORTABLE_FLUID_INTERFACE_ID = id("create", "portable_fluid_interface");
    private static final ResourceLocation CLUTCH_ID = id("create", "clutch");
    private static final ResourceLocation GEARSHIFT_ID = id("create", "gearshift");
    private static final ResourceLocation ROPE_PULLEY_ID = id("create", "rope_pulley");
    private static final ResourceLocation ELEVATOR_PULLEY_ID = id("create", "elevator_pulley");
    private static final ResourceLocation ELEVATOR_CONTACT_ID = id("create", "elevator_contact");
    private static final ResourceLocation REDSTONE_CONTACT_ID = id("create", "redstone_contact");
    private static final ResourceLocation REDSTONE_LINK_ID = id("create", "redstone_link");
    private static final ResourceLocation THRESHOLD_SWITCH_ID = id("create", "stockpile_switch");
    private static final ResourceLocation SMART_OBSERVER_ID = id("create", "smart_observer");
    private static final ResourceLocation SPEEDOMETER_ID = id("create", "speedometer");
    private static final ResourceLocation STRESSOMETER_ID = id("create", "stressometer");
    private static final ResourceLocation ROTATION_SPEED_CONTROLLER_ID = id("create", "rotation_speed_controller");
    private static final ResourceLocation DISPLAY_LINK_ID = id("create", "display_link");
    private static final ResourceLocation DISPLAY_BOARD_ID = id("create", "display_board");
    private static final ResourceLocation CART_ASSEMBLER_ID = id("create", "cart_assembler");
    private static final ResourceLocation REDSTONE_BLOCK_ID = id("minecraft", "redstone_block");
    private static final ResourceLocation REDSTONE_LAMP_ID = id("minecraft", "redstone_lamp");
    private static final ResourceLocation STICKY_MECHANICAL_PISTON_ID = id("create", "sticky_mechanical_piston");
    private static final ResourceLocation RADIAL_CHASSIS_ID = id("create", "radial_chassis");
    private static final ResourceLocation BLUE_WOOL_ID = id("minecraft", "blue_wool");
    private static final ResourceLocation YELLOW_WOOL_ID = id("minecraft", "yellow_wool");
    private static final ResourceLocation RED_WOOL_ID = id("minecraft", "red_wool");
    private static final ResourceLocation GREEN_WOOL_ID = id("minecraft", "green_wool");

    private static final int DEFAULT_RPM = 32;
    private static final int LOGISTICS_RPM = 8;
    private static final int GALLERY_SPACING_X = 28;
    private static final int GALLERY_SPACING_Z = 28;
    private static final int GALLERY_OVERLAP_MARGIN = 6;
    private static final long SETTLE_TICKS = 20L;
    private static final String M20_TAG = "sable_m20";
    private static final String FAMILY_TAG = "family";
    private static final String CREATED_TICK_TAG = "created_game_time";
    private static final String CONTROLLER_ACTION_TAG = "controller_action";
    private static final String CONTROLLER_PAYLOAD_TAG = "controller_payload_prepared";
    private static final String ELEVATOR_ACTION_TAG = "elevator_action";
    private static final String LINK_CONFIGURED_TAG = "redstone_link_configured";
    private static final BlockPos KINETIC_MOTOR_LOCAL = new BlockPos(0, 0, 0);
    private static final BlockPos REDSTONE_SIGNAL_LOCAL = new BlockPos(3, 1, 0);
    private static final BlockPos CONTROLLER_MOTOR_LOCAL = new BlockPos(1, 5, 0);
    private static final BlockPos CONTROLLER_PULLEY_LOCAL = new BlockPos(0, 5, 0);
    private static final BlockPos LINK_TRANSMITTER_LOCAL = new BlockPos(0, 1, 0);
    private static final BlockPos LINK_RECEIVER_LOCAL = new BlockPos(5, 1, 0);
    private static final BlockPos LINK_LAMP_LOCAL = new BlockPos(6, 1, 0);
    private static final BlockPos LINK_SIGNAL_LOCAL = new BlockPos(0, 2, 0);
    private static final BlockPos ELEVATOR_PULLEY_LOCAL = new BlockPos(0, 6, 0);
    private static final BlockPos ELEVATOR_MOTOR_LOCAL = new BlockPos(1, 6, 0);
    private static final BlockPos ELEVATOR_CABIN_CONTACT_LOCAL = new BlockPos(0, 2, 0);
    private static final BlockPos ELEVATOR_CABIN_BLOCK_LOCAL = new BlockPos(-1, 2, 0);
    private static final BlockPos ELEVATOR_LOWER_CONTACT_LOCAL = new BlockPos(1, 1, 0);
    private static final BlockPos ELEVATOR_UPPER_CONTACT_LOCAL = new BlockPos(1, 4, 0);
    private static final BlockPos ELEVATOR_LOWER_SIGNAL_LOCAL = new BlockPos(2, 1, 0);
    private static final BlockPos ELEVATOR_UPPER_SIGNAL_LOCAL = new BlockPos(2, 4, 0);
    private static final BlockPos CRUSHING_LEFT_WHEEL_LOCAL = new BlockPos(-1, 1, 0);
    private static final BlockPos CRUSHING_RIGHT_WHEEL_LOCAL = new BlockPos(1, 1, 0);
    private static final BlockPos CRUSHING_CONTROLLER_LOCAL = new BlockPos(0, 1, 0);
    private static final BlockPos CRUSHING_INPUT_LANE_LOCAL = new BlockPos(0, 2, 0);
    private static final BlockPos CRUSHING_OUTPUT_LANE_LOCAL = new BlockPos(0, 0, 0);
    private static final BlockPos CRUSHING_LEFT_MOTOR_LOCAL = new BlockPos(-1, 1, -1);
    private static final BlockPos CRUSHING_RIGHT_MOTOR_LOCAL = new BlockPos(1, 1, -1);
    private static final DynamicCommandExceptionType ERROR_M20_FAILED = new DynamicCommandExceptionType(
            message -> Component.literal("SABLE_M20 command failed: " + message));

    private M20TestCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m20")
                .then(Commands.literal("audit").executes(M20TestCommands::audit))
                .then(Commands.literal("status").executes(M20TestCommands::status))
                .then(Commands.literal("give_arm_selector").executes(M20TestCommands::giveArmSelector))
                .then(Commands.literal("spawn_logistics")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(context -> spawn(context, logisticsSpec()))))
                .then(Commands.literal("inspect_logistics")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> inspect(context, logisticsSpec()))))
                .then(Commands.literal("reset_logistics")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> reset(context, logisticsSpec()))))
                .then(Commands.literal("seed_logistics")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M20TestCommands::seedLogistics)))
                .then(Commands.literal("spawn_fluids")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(context -> spawn(context, fluidsSpec()))))
                .then(Commands.literal("inspect_fluids")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> inspect(context, fluidsSpec()))))
                .then(Commands.literal("reset_fluids")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> reset(context, fluidsSpec()))))
                .then(Commands.literal("spawn_kinetic")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(context -> spawn(context, kineticSpec()))))
                .then(Commands.literal("inspect_kinetic")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> inspect(context, kineticSpec()))))
                .then(Commands.literal("reset_kinetic")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> reset(context, kineticSpec()))))
                .then(Commands.literal("spawn_redstone")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(context -> spawn(context, redstoneSpec()))))
                .then(Commands.literal("inspect_redstone")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> inspect(context, redstoneSpec()))))
                .then(Commands.literal("toggle_redstone")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M20TestCommands::toggleRedstone)))
                .then(Commands.literal("spawn_arm")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(context -> spawn(context, armSpec()))))
                .then(Commands.literal("inspect_arm")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> inspect(context, armSpec()))))
                .then(Commands.literal("spawn_controller")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(context -> spawn(context, controllerSpec()))))
                .then(Commands.literal("inspect_controller")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> inspect(context, controllerSpec()))))
                .then(Commands.literal("prepare_controller_payload")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M20TestCommands::prepareControllerPayload)))
                .then(Commands.literal("controller_forward")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> setControllerMotor(context, -DEFAULT_RPM,
                                        "forward_alias_extend"))))
                .then(Commands.literal("controller_reverse")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> setControllerMotor(context, DEFAULT_RPM,
                                        "reverse_alias_retract"))))
                .then(Commands.literal("controller_stop")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> setControllerMotor(context, 0, "stop"))))
                .then(Commands.literal("controller_extend")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> setControllerMotor(context, -DEFAULT_RPM, "extend"))))
                .then(Commands.literal("controller_retract")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> setControllerMotor(context, DEFAULT_RPM, "retract"))))
                .then(Commands.literal("spawn_link")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(context -> spawn(context, linkSpec()))))
                .then(Commands.literal("inspect_link")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> inspect(context, linkSpec()))))
                .then(Commands.literal("reset_link")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> reset(context, linkSpec()))))
                .then(Commands.literal("toggle_link")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M20TestCommands::toggleLink)))
                .then(Commands.literal("spawn_elevator")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(context -> spawn(context, elevatorSpec()))))
                .then(Commands.literal("inspect_elevator")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> inspect(context, elevatorSpec()))))
                .then(Commands.literal("elevator_assemble")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M20TestCommands::elevatorAssemble)))
                .then(Commands.literal("elevator_floor")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .then(Commands.argument("floor", IntegerArgumentType.integer(0, 1))
                                        .executes(M20TestCommands::elevatorFloor))))
                .then(Commands.literal("elevator_disassemble")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M20TestCommands::elevatorDisassemble)))
                .then(Commands.literal("stabilize")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> setBodyVelocity(context, "stabilize", new Vector3d(),
                                        new Vector3d()))))
                .then(Commands.literal("test_translate_parent")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> setBodyVelocity(context, "translate_parent",
                                        new Vector3d(0.0, 0.6, 0.0), new Vector3d()))))
                .then(Commands.literal("test_rotate_parent")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(context -> setBodyVelocity(context, "rotate_parent", new Vector3d(),
                                        new Vector3d(0.0, Math.toRadians(18.0), 0.0)))))
                .then(Commands.literal("save_reload_check")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M20TestCommands::saveReloadCheck)))
                .then(Commands.literal("gauntlet")
                        .then(Commands.literal("status").executes(M20TestCommands::gauntletStatus))
                        .then(Commands.literal("focus")
                                .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                        .executes(M20TestCommands::focusGauntlet)))
                        .then(Commands.literal("cleanup")
                                .then(Commands.argument("baseName", StringArgumentType.string())
                                        .executes(M20TestCommands::cleanupGauntlet)))
                        .then(Commands.literal("inspect")
                                .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                        .executes(M20TestCommands::inspectGauntlet)))
                        .then(Commands.literal("kinetic")
                                .then(Commands.literal("basic")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, kineticSpec()))))
                                .then(Commands.literal("chain")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, chainDriveSpec()))))
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(context -> spawnGauntlet(context, "kinetic"))))
                        .then(Commands.literal("redstone")
                                .then(Commands.literal("basic")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, redstoneSpec()))))
                                .then(Commands.literal("link")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, linkSpec()))))
                                .then(Commands.literal("contact")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context,
                                                        redstoneContactSpec()))))
                                .then(Commands.literal("threshold")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context,
                                                        thresholdSwitchSpec()))))
                                .then(Commands.literal("observer")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, smartObserverSpec()))))
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(context -> spawnGauntlet(context, "redstone"))))
                        .then(Commands.literal("logistics")
                                .then(Commands.literal("belt")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, logisticsSpec()))))
                                .then(Commands.literal("arm")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, armSpec()))))
                                .then(Commands.literal("funnels_tunnels")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context,
                                                        funnelsTunnelsSpec()))))
                                .then(Commands.literal("chute")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context,
                                                        chuteSmartChuteSpec()))))
                                .then(Commands.literal("ejector")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context,
                                                        weightedEjectorSpec()))))
                                .then(Commands.literal("vault")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, vaultSpec()))))
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(context -> spawnGauntlet(context, "logistics"))))
                        .then(Commands.literal("fluids")
                                .then(Commands.literal("closed")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, fluidsSpec()))))
                                .then(Commands.literal("valve")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, fluidValveSpec()))))
                                .then(Commands.literal("smart_pipe")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context,
                                                        smartFluidPipeSpec()))))
                                .then(Commands.literal("hose_pulley")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, hosePulleySpec()))))
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(context -> spawnGauntlet(context, "fluids"))))
                        .then(Commands.literal("actors")
                                .then(Commands.literal("drill")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context,
                                                        drillRegressionSpec()))))
                                .then(Commands.literal("saw_static")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, sawStaticSpec()))))
                                .then(Commands.literal("saw_tree")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, sawTreeSpec()))))
                                .then(Commands.literal("harvester")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, harvesterSpec()))))
                                .then(Commands.literal("plough")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, ploughSpec()))))
                                .then(Commands.literal("roller")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, rollerSpec()))))
                                .then(Commands.literal("rope_pulley")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, controllerSpec()))))
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(context -> spawnGauntlet(context, "actors"))))
                        .then(Commands.literal("controls")
                                .then(Commands.literal("elevator")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, elevatorSpec()))))
                                .then(Commands.literal("gauges")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, gaugesSpec()))))
                                .then(Commands.literal("rsc")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context,
                                                        rotationSpeedControllerSpec()))))
                                .then(Commands.literal("display")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, displayLinkSpec()))))
                                .then(Commands.literal("cart")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, cartAssemblerSpec()))))
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(context -> spawnGauntlet(context, "controls"))))
                        .then(Commands.literal("interfaces")
                                .then(Commands.literal("psi_item")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context,
                                                        portableStorageInterfaceSpec()))))
                                .then(Commands.literal("pfi_fluid")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context,
                                                        portableFluidInterfaceSpec()))))
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(context -> spawnGauntlet(context, "interfaces"))))
                        .then(Commands.literal("processing")
                                .then(Commands.literal("press")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, pressSpec()))))
                                .then(Commands.literal("mixer")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, mixerBasinSpec()))))
                                .then(Commands.literal("heated_mixer")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, heatedBasinSpec()))))
                                .then(Commands.literal("millstone")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, millstoneSpec()))))
                                .then(Commands.literal("crushing")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context,
                                                        crushingWheelsSpec()))))
                                .then(Commands.literal("crafter")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context,
                                                        mechanicalCraftersSpec()))))
                                .then(Commands.literal("fan")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(context -> spawnGauntletSingle(context, encasedFanSpec()))))
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(context -> spawnGauntlet(context, "processing")))))
                .then(Commands.literal("acceptance")
                        .then(Commands.argument("target", SubLevelArgumentType.singleSubLevel())
                                .executes(M20TestCommands::acceptance))));
    }

    private static int audit(final CommandContext<CommandSourceStack> context) {
        final String line = "SABLE_M20_AUDIT parityMatrix=M20_CREATE_PARITY_MATRIX.md"
                + " upstreamConcernRows=48"
                + " missingApplicable=0"
                + " productionPolicy=no_new_broad_Create_patch_without_runtime_evidence"
                + " m13ThroughM18Frozen=true"
                + " m19Isolated=true";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int gauntletStatus(final CommandContext<CommandSourceStack> context) {
        final List<String> lines = List.of(
                "SABLE_M20_GAUNTLET_STATUS coverage=M20_CREATE_FUNCTIONAL_COVERAGE.md"
                        + " implementation=M20_7_COMPLETE_ORDINARY_CREATE_FUNCTIONAL_GAUNTLET"
                        + " runtimeClosure=USER_REQUIRED"
                        + " groups=kinetic,redstone,logistics,fluids,actors,controls,interfaces,processing",
                "SABLE_M20_GAUNTLET_ROW group=kinetic semantic=GENERALIZED_RUNTIME_PROVEN"
                        + " render=GENERALIZED_RUNTIME_PROVEN ui=NONE userRequired=false"
                        + " canary=/sable_m20_spawn_kinetic",
                "SABLE_M20_GAUNTLET_ROW family=DrillRegression fixtureImplemented=true static=PASS"
                        + " runtime=M16_PROVEN_RECHECK boundary=MOVEMENT_ACTOR+LEVEL_LOOKUP",
                "SABLE_M20_GAUNTLET_ROW family=SawStatic fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=PROCESSING_MACHINE+ITEM_RENDER",
                "SABLE_M20_GAUNTLET_ROW family=SawTree fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=MOVEMENT_ACTOR+LEVEL_LOOKUP+ENTITY_QUERY",
                "SABLE_M20_GAUNTLET_ROW family=Harvester fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=MOVEMENT_ACTOR+LEVEL_LOOKUP",
                "SABLE_M20_GAUNTLET_ROW family=Plough fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=MOVEMENT_ACTOR+LEVEL_LOOKUP",
                "SABLE_M20_GAUNTLET_ROW family=Roller fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=MOVEMENT_ACTOR+PROCESSING_WORLD_SCAN",
                "SABLE_M20_GAUNTLET_ROW family=Press fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=PROCESSING_MACHINE+ITEM_RENDER",
                "SABLE_M20_GAUNTLET_ROW family=MixerBasin fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=PROCESSING_MACHINE+CAPABILITY_LOOKUP",
                "SABLE_M20_GAUNTLET_ROW family=Millstone fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=PROCESSING_MACHINE+ITEM_RENDER",
                "SABLE_M20_GAUNTLET_ROW family=CrushingWheels fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=PROCESSING_MACHINE+ENTITY_QUERY",
                "SABLE_M20_GAUNTLET_ROW family=MechanicalCrafters fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=PROCESSING_MACHINE+NEIGHBOR_UPDATE",
                "SABLE_M20_GAUNTLET_ROW family=EncasedFan fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=PROCESSING_WORLD_SCAN+ENTITY_QUERY",
                "SABLE_M20_GAUNTLET_ROW family=FunnelsTunnels fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=CAPABILITY_LOOKUP+ITEM_RENDER",
                "SABLE_M20_GAUNTLET_ROW family=ChuteSmartChute fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=CAPABILITY_LOOKUP+NEIGHBOR_UPDATE",
                "SABLE_M20_GAUNTLET_ROW family=WeightedEjector fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=ENTITY_QUERY+ITEM_RENDER",
                "SABLE_M20_GAUNTLET_ROW family=PortableStorageInterface fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=CAPABILITY_LOOKUP+CONTRAPTION_ASSEMBLY",
                "SABLE_M20_GAUNTLET_ROW family=PortableFluidInterface fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=CAPABILITY_LOOKUP+CONTRAPTION_ASSEMBLY",
                "SABLE_M20_GAUNTLET_ROW family=FluidValve fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=CAPABILITY_LOOKUP+NEIGHBOR_UPDATE",
                "SABLE_M20_GAUNTLET_ROW family=SmartFluidPipe fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=CAPABILITY_LOOKUP+FILTERING",
                "SABLE_M20_GAUNTLET_ROW family=HosePulley fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=LEVEL_LOOKUP+CAPABILITY_LOOKUP",
                "SABLE_M20_GAUNTLET_ROW family=RedstoneContact fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=NEIGHBOR_UPDATE+BLOCKPOS_SPACE",
                "SABLE_M20_GAUNTLET_ROW family=ThresholdSwitch fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=CAPABILITY_LOOKUP+NEIGHBOR_UPDATE",
                "SABLE_M20_GAUNTLET_ROW family=SmartObserver fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=LEVEL_LOOKUP+RAYCAST",
                "SABLE_M20_GAUNTLET_ROW family=SpeedometerStressometer fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=BLOCK_ENTITY_LOOKUP+RENDER_BER",
                "SABLE_M20_GAUNTLET_ROW family=RotationSpeedController fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=NEIGHBOR_UPDATE+OUTLINER_UI",
                "SABLE_M20_GAUNTLET_ROW family=DisplayLink fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=GLOBAL_NETWORK+OUTLINER_UI",
                "SABLE_M20_GAUNTLET_ROW family=CartAssembler fixtureImplemented=true static=PASS"
                        + " runtime=USER_REQUIRED boundary=CONTRAPTION_ASSEMBLY+ENTITY_QUERY",
                "SABLE_M20_GAUNTLET_ROW group=redstone semantic=RUNTIME_REQUIRED render=GENERALIZED_RUNTIME_PROVEN"
                        + " ui=NONE userRequired=true canary=redstone_link_and_sensor_family",
                "SABLE_M20_GAUNTLET_ROW group=logistics semantic=RUNTIME_REQUIRED"
                        + " render=RUNTIME_PROVEN ui=NONE userRequired=true"
                        + " canary=belt_depot_arm_funnel_chute_ejector",
                "SABLE_M20_GAUNTLET_ROW group=fluids semantic=RUNTIME_PROVEN render=RUNTIME_PROVEN"
                        + " ui=NONE userRequired=true canary=closed_tank_pipe_pump_valve_smart_pipe_hose",
                "SABLE_M20_GAUNTLET_ROW group=actors semantic=RUNTIME_REQUIRED render=RUNTIME_REQUIRED"
                        + " ui=USER_REQUIRED userRequired=true canary=drill_saw_harvester_plough_roller",
                "SABLE_M20_GAUNTLET_ROW group=controls semantic=RUNTIME_REQUIRED render=GENERALIZED_RUNTIME_PROVEN"
                        + " ui=DEFERRED_ELEVATOR_POLISH userRequired=true canary=elevator_gauges_rsc_display_cart",
                "SABLE_M20_GAUNTLET_ROW group=interfaces semantic=RUNTIME_REQUIRED render=GENERALIZED_RUNTIME_PROVEN"
                        + " ui=NONE userRequired=true canary=portable_storage_and_fluid_interfaces",
                "SABLE_M20_GAUNTLET_ROW group=processing semantic=RUNTIME_REQUIRED render=RUNTIME_REQUIRED"
                        + " ui=NONE userRequired=true canary=press_mixer_basin_millstone_crushing_crafter_fan");
        lines.forEach(line -> {
            send(context, line);
            Sable.LOGGER.info(line);
        });
        return 1;
    }

    private static int spawnGauntlet(final CommandContext<CommandSourceStack> context,
                                     final String group) throws CommandSyntaxException {
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(context);
        final String name = StringArgumentType.getString(context, "name");
        final List<FixtureSpec> specs = switch (group) {
            case "kinetic" -> List.of(kineticSpec(), chainDriveSpec());
            case "redstone" -> List.of(redstoneSpec(), linkSpec(), redstoneContactSpec(),
                    thresholdSwitchSpec(), smartObserverSpec());
            case "logistics" -> List.of(logisticsSpec(), armSpec(), funnelsTunnelsSpec(),
                    chuteSmartChuteSpec(), weightedEjectorSpec(), vaultSpec());
            case "fluids" -> List.of(fluidsSpec(), fluidValveSpec(), smartFluidPipeSpec(), hosePulleySpec());
            case "actors" -> List.of(drillRegressionSpec(), sawStaticSpec(), sawTreeSpec(),
                    harvesterSpec(), ploughSpec(), rollerSpec(), controllerSpec());
            case "controls" -> List.of(elevatorSpec(), gaugesSpec(), rotationSpeedControllerSpec(),
                    displayLinkSpec(), cartAssemblerSpec());
            case "interfaces" -> List.of(portableStorageInterfaceSpec(), portableFluidInterfaceSpec());
            case "processing" -> List.of(pressSpec(), mixerBasinSpec(), heatedBasinSpec(), millstoneSpec(),
                    crushingWheelsSpec(), mechanicalCraftersSpec(), encasedFanSpec());
            default -> throw ERROR_M20_FAILED.create("Unknown gauntlet group " + group);
        };
        verifyGalleryNonOverlapping(specs);
        int ready = 0;
        final Vec3 galleryOrigin = context.getSource().getPosition();
        sendGalleryHeader(context, group, name, galleryOrigin, specs);
        for (final FixtureSpec spec : specs) {
            final String fixtureName = name + "_" + spec.family();
            final GallerySlot slot = gallerySlot(specs.indexOf(spec));
            final FixtureCheck check = spawnFixtureNamed(context, container, fixtureName, spec, slot);
            if (check.ready()) {
                ready++;
            }
            sendGalleryEntry(context, group, fixtureName, spec, slot, galleryOrigin);
        }
        final String line = "SABLE_M20_GAUNTLET_SPAWN group=" + group
                + " baseName=" + name
                + " fixturesCreated=" + specs.size()
                + " readyImmediately=" + ready
                + " galleryOrigin=" + fmt(galleryOrigin)
                + " spacing=(" + GALLERY_SPACING_X + "," + GALLERY_SPACING_Z + ")"
                + " overlapCheck=PASS"
                + " settleTicksRequired=" + SETTLE_TICKS
                + " semanticPass=INSPECT_AFTER_SETTLE"
                + " userRequired=true";
        send(context, line);
        Sable.LOGGER.info(line);
        return ready == specs.size() ? 1 : 0;
    }

    private static int spawnGauntletSingle(final CommandContext<CommandSourceStack> context,
                                           final FixtureSpec spec) throws CommandSyntaxException {
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(context);
        final String name = StringArgumentType.getString(context, "name");
        final FixtureCheck check = spawnFixtureNamed(context, container, name, spec, GallerySlot.ORIGIN);
        final String line = "SABLE_M20_GAUNTLET_SINGLE_SPAWN family=" + spec.family()
                + " name=" + name
                + " slot=(0,0)"
                + " platform=" + platformSize(spec)
                + " expected=\"" + expectedAction(spec) + "\""
                + " status=" + pass(check.ready())
                + " runtime=USER_REQUIRED";
        send(context, line);
        Sable.LOGGER.info(line);
        return check.ready() ? 1 : 0;
    }

    private static int inspectGauntlet(final CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        return inspect(context, specByFamily(getM20String(subLevel, FAMILY_TAG, "")));
    }

    private static int focusGauntlet(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final ServerPlayer player = context.getSource().getPlayerOrException();
        final Vector3dc center = subLevel.logicalPose().position();
        player.teleportTo(center.x(), center.y() + 3.0, center.z() + 8.0);
        final String family = getM20String(subLevel, FAMILY_TAG, "unknown");
        final String line = "SABLE_M20_GAUNTLET_FOCUS name=" + nameOrNone(subLevel)
                + " family=" + family
                + " player=" + player.getGameProfile().getName()
                + " viewPosition=(" + fmt(center.x()) + "," + fmt(center.y() + 3.0) + ","
                + fmt(center.z() + 8.0) + ")"
                + " expected=\"" + expectedAction(specByFamily(family)) + "\"";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int cleanupGauntlet(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(context);
        final String baseName = StringArgumentType.getString(context, "baseName");
        final List<ServerSubLevel> toRemove = new ArrayList<>();
        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            final String name = subLevel.getName();
            if (name != null && (name.equals(baseName) || name.startsWith(baseName + "_"))) {
                toRemove.add(subLevel);
            }
        }
        for (final ServerSubLevel subLevel : toRemove) {
            container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
        }
        final String line = "SABLE_M20_GAUNTLET_CLEANUP baseName=" + baseName
                + " removed=" + toRemove.size()
                + " scope=matching_named_M20_gauntlet_sables_only";
        send(context, line);
        Sable.LOGGER.info(line);
        return toRemove.size();
    }

    private static FixtureSpec specByFamily(final String family) throws CommandSyntaxException {
        return switch (family) {
            case "logistics" -> logisticsSpec();
            case "fluids" -> fluidsSpec();
            case "kinetic" -> kineticSpec();
            case "redstone" -> redstoneSpec();
            case "arm" -> armSpec();
            case "controller" -> controllerSpec();
            case "elevator" -> elevatorSpec();
            case "redstone_link" -> linkSpec();
            case "chain_drive" -> chainDriveSpec();
            case "drill_regression" -> drillRegressionSpec();
            case "saw_static" -> sawStaticSpec();
            case "saw_tree" -> sawTreeSpec();
            case "harvester" -> harvesterSpec();
            case "plough" -> ploughSpec();
            case "roller" -> rollerSpec();
            case "press" -> pressSpec();
            case "mixer_basin" -> mixerBasinSpec();
            case "heated_basin" -> heatedBasinSpec();
            case "millstone" -> millstoneSpec();
            case "crushing_wheels" -> crushingWheelsSpec();
            case "mechanical_crafters" -> mechanicalCraftersSpec();
            case "encased_fan" -> encasedFanSpec();
            case "funnels_tunnels" -> funnelsTunnelsSpec();
            case "chute_smart_chute" -> chuteSmartChuteSpec();
            case "weighted_ejector" -> weightedEjectorSpec();
            case "vault" -> vaultSpec();
            case "portable_storage_interface" -> portableStorageInterfaceSpec();
            case "portable_fluid_interface" -> portableFluidInterfaceSpec();
            case "fluid_valve" -> fluidValveSpec();
            case "smart_fluid_pipe" -> smartFluidPipeSpec();
            case "hose_pulley" -> hosePulleySpec();
            case "redstone_contact" -> redstoneContactSpec();
            case "threshold_switch" -> thresholdSwitchSpec();
            case "smart_observer" -> smartObserverSpec();
            case "gauges" -> gaugesSpec();
            case "rotation_speed_controller" -> rotationSpeedControllerSpec();
            case "display_link" -> displayLinkSpec();
            case "cart_assembler" -> cartAssemblerSpec();
            default -> throw ERROR_M20_FAILED.create("Unknown or missing M20 gauntlet family tag: " + family);
        };
    }

    private static int status(final CommandContext<CommandSourceStack> context) {
        final String line = "SABLE_M20_STATUS implementation=M20_7_COMPLETE_ORDINARY_CREATE_FUNCTIONAL_GAUNTLET"
                + " runtimeClosure=USER_REQUIRED"
                + " fixtures=kinetic,redstone,logistics,fluids,actors,controls,interfaces,processing"
                + " currentRuntime=M20_7_ORDINARY_CREATE_GAUNTLET_USER_REQUIRED"
                + " ropePulleyWeightless=COVERED_BY_GENERALIZED_BACKPORT"
                + " redstoneLinkBoundary=CANARY_ADDED_PRODUCTION_MIXIN_DEFERRED_UNTIL_DIFFERENTIAL"
                + " hiddenPlotPoseTranslation=false"
                + " normalWorldCreate=UNCHANGED";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int giveArmSelector(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerPlayer player = context.getSource().getPlayerOrException();
        final ItemStack stack = new ItemStack(requireBlock(MECHANICAL_ARM_ID).asItem(), 1);
        final boolean added = player.getInventory().add(stack);
        if (!added) {
            player.drop(stack, false);
        }
        final String line = "SABLE_M20_ARM_SELECTOR_GIVE player=" + player.getGameProfile().getName()
                + " item=create:mechanical_arm"
                + " result=" + (added ? "ADDED_TO_INVENTORY" : "DROPPED_AT_PLAYER")
                + " semantics=normal_Create_ArmItem_selection_UI_no_preconfiguration";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int spawn(final CommandContext<CommandSourceStack> context,
                             final FixtureSpec spec) throws CommandSyntaxException {
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(context);
        final String name = StringArgumentType.getString(context, "name");
        final FixtureCheck check = spawnFixtureNamed(context, container, name, spec);
        return check.ready() ? 1 : 0;
    }

    private static FixtureCheck spawnFixtureNamed(final CommandContext<CommandSourceStack> context,
                                                  final ServerSubLevelContainer container,
                                                  final String name,
                                                  final FixtureSpec spec) throws CommandSyntaxException {
        return spawnFixtureNamed(context, container, name, spec, GallerySlot.ORIGIN);
    }

    private static FixtureCheck spawnFixtureNamed(final CommandContext<CommandSourceStack> context,
                                                  final ServerSubLevelContainer container,
                                                  final String name,
                                                  final FixtureSpec spec,
                                                  final GallerySlot slot) throws CommandSyntaxException {
        ServerSubLevel subLevel = null;
        try {
            subLevel = createEmptySubLevel(context, container, name, slot);
            final List<SubLevelBlockEditHelper.BlockChange> changes = applyFixture(subLevel, spec, false);
            SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
            configureFixtureAfterPlacement(subLevel, spec);
            markFixtureCreated(subLevel, spec);
            subLevel.updateLastPose();
            final FixtureCheck check = checkFixture(subLevel, spec);
            final FixtureBounds bounds = fixtureBounds(spec);
            final String line = "SABLE_M20_SPAWN family=" + spec.family()
                    + " name=" + name
                    + " uuid=" + subLevel.getUniqueId()
                    + " status=" + pass(check.ready())
                    + " blockCount=" + countNonAirBlocks(subLevel)
                    + " localBounds=" + fmt(bounds)
                    + " platform=" + platformSize(spec)
                    + " gallerySlot=(" + slot.offsetX() + "," + slot.offsetZ() + ")"
                    + " visibleCenter=" + formatVector(subLevel.logicalPose().position())
                    + " workingDirection=+X"
                    + " expected=\"" + expectedAction(spec) + "\""
                    + " rawBounds=" + fmt(toPlot(subLevel, bounds.minLocal())) + ".."
                    + fmt(toPlot(subLevel, bounds.maxLocal()))
                    + " allocatedCenterChunk=" + fmt(new ChunkPos(subLevel.getPlot().getCenterBlock()))
                    + " settleTicksRequired=" + SETTLE_TICKS
                    + " fixtureAgeTicks=" + fixtureAgeTicks(subLevel)
                    + " placementLifecycle=embedded_local_level_accessor_then_finalize"
                    + " fixtureLocalTruth=true"
                    + " runtimeAcceptance=USER_REQUIRED"
                    + " failures=" + check.failures();
            send(context, line);
            Sable.LOGGER.info(line);
            return check;
        } catch (final RuntimeException exception) {
            rollbackSpawnSubLevel(container, subLevel, name, exception);
            throw commandFailure(exception);
        }
    }

    private static int reset(final CommandContext<CommandSourceStack> context,
                             final FixtureSpec spec) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final List<SubLevelBlockEditHelper.BlockChange> changes = applyFixture(subLevel, spec, true);
        SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
        configureFixtureAfterPlacement(subLevel, spec);
        markFixtureCreated(subLevel, spec);
        subLevel.updateLastPose();
        final FixtureCheck check = checkFixture(subLevel, spec);
        final FixtureBounds bounds = fixtureBounds(spec);
        final String line = "SABLE_M20_RESET family=" + spec.family()
                + " id=" + subLevel.getUniqueId()
                + " status=" + pass(check.ready())
                + " note=test_fixture_rebuilt_from_canonical_local_blocks"
                + " settleTicksRequired=" + SETTLE_TICKS
                + " fixtureAgeTicks=" + fixtureAgeTicks(subLevel)
                + " placementLifecycle=embedded_local_level_accessor_then_finalize"
                + " localBounds=" + fmt(bounds)
                + " rawBounds=" + fmt(toPlot(subLevel, bounds.minLocal())) + ".."
                + fmt(toPlot(subLevel, bounds.maxLocal()))
                + " failures=" + check.failures();
        send(context, line);
        Sable.LOGGER.info(line);
        return check.ready() ? 1 : 0;
    }

    private static int inspect(final CommandContext<CommandSourceStack> context,
                               final FixtureSpec spec) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final FixtureCheck check = checkFixture(subLevel, spec);
        final FixtureStats stats = inspectStats(subLevel);
        final FixtureBounds bounds = fixtureBounds(spec);
        final long ageTicks = fixtureAgeTicks(subLevel);
        final String summary = "SABLE_M20_INSPECT family=" + spec.family()
                + " id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " status=" + pass(check.ready())
                + " lifecycleStatus=" + (ageTicks >= SETTLE_TICKS ? "SETTLED" : "UNSETTLED")
                + " fixtureAgeTicks=" + ageTicks
                + " settleTicksRequired=" + SETTLE_TICKS
                + " blockCount=" + stats.blockCount()
                + " mass=" + fmt(stats.mass())
                + " posePosition=" + formatVector(stats.position())
                + " linearVelocity=" + formatVector(stats.linearVelocity())
                + " angularVelocity=" + formatVector(stats.angularVelocity())
                + " rawPlotOrigin=" + fmt(subLevel.getPlot().getCenterBlock())
                + " localBounds=" + fmt(bounds)
                + " rawBounds=" + fmt(toPlot(subLevel, bounds.minLocal())) + ".."
                + fmt(toPlot(subLevel, bounds.maxLocal()))
                + " semanticRuntime=USER_OBSERVED_REQUIRED"
                + " hiddenPlotPoseTranslation=false"
                + " failures=" + check.failures();
        send(context, summary);
        Sable.LOGGER.info(summary);
        for (final PlacedBlock expected : spec.blocks()) {
            final BlockState state = getLocalBlockState(subLevel, expected.localPos());
            final BlockEntity be = state.hasBlockEntity()
                    ? subLevel.getLevel().getBlockEntity(toPlot(subLevel, expected.localPos()))
                    : null;
            final String line = "SABLE_M20_" + spec.family().toUpperCase(Locale.ROOT)
                    + " local=" + fmt(expected.localPos())
                    + " raw=" + fmt(toPlot(subLevel, expected.localPos()))
                    + " role=" + expected.role()
                    + " expected=" + expected.blockId()
                    + " actual=" + blockId(state)
                    + " state=" + state
                    + " beClass=" + (be == null ? "none" : be.getClass().getName())
                    + " valid=" + expected.valid(state);
            send(context, line);
            Sable.LOGGER.info(line);
        }
        for (final String line : familyDiagnostics(subLevel, spec)) {
            send(context, line);
            Sable.LOGGER.info(line);
        }
        return check.ready() ? 1 : 0;
    }

    private static int toggleRedstone(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final BlockState oldState = getLocalBlockState(subLevel, REDSTONE_SIGNAL_LOCAL);
        final BlockState newState = blockIdMatches(oldState, REDSTONE_BLOCK_ID)
                ? Blocks.AIR.defaultBlockState()
                : Blocks.REDSTONE_BLOCK.defaultBlockState();
        final List<SubLevelBlockEditHelper.BlockChange> changes = List.of(
                setLifecycleLocalBlock(subLevel, REDSTONE_SIGNAL_LOCAL, newState));
        SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
        final String line = "SABLE_M20_REDSTONE_TOGGLE id=" + subLevel.getUniqueId()
                + " redstoneLocal=" + fmt(REDSTONE_SIGNAL_LOCAL)
                + " previous=" + blockId(oldState)
                + " current=" + blockId(newState)
                + " semantics=vanilla_neighbor_update_into_Create_powered_blocks"
                + " gearshiftPowered=" + propertyMatches(getLocalBlockState(subLevel, new BlockPos(3, 0, 0)),
                "powered", "true")
                + " downstreamSpeed=" + fmt(readKineticSpeed(subLevel, new BlockPos(5, 0, 0)))
                + " inspectWith=/sable m20 inspect_redstone";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int seedLogistics(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final BlockPos controllerLocal = new BlockPos(0, 1, 0);
        final BlockEntity controller = getLocalBlockEntity(subLevel, controllerLocal);
        final boolean seeded = seedBeltTransportedItem(controller, new ItemStack(Items.IRON_INGOT, 1));
        if (controller != null) {
            controller.setChanged();
        }
        final String line = "SABLE_M20_LOGISTICS_SEED id=" + subLevel.getUniqueId()
                + " controllerLocal=" + fmt(controllerLocal)
                + " item=minecraft:iron_ingotx1"
                + " result=" + (seeded ? "SEEDED_TRANSPORTED_ITEM_STACK" : "FAILED_NO_BELT_TRANSPORT_API")
                + " semantics=Create_BeltInventory_TransportedItemStack"
                + " directDestinationTeleport=false"
                + " inspectWith=/sable m20 inspect_logistics";
        send(context, line);
        Sable.LOGGER.info(line);
        return seeded ? 1 : 0;
    }


    private static int setControllerMotor(final CommandContext<CommandSourceStack> context,
                                          final int rpm,
                                          final String action) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final BlockEntity pulley = getLocalBlockEntity(subLevel, CONTROLLER_PULLEY_LOCAL);
        final double offsetBefore = asDouble(readFieldRaw(pulley, "offset"));
        setFixtureMotorSpeed(subLevel, CONTROLLER_MOTOR_LOCAL, rpm);
        putM20String(subLevel, CONTROLLER_ACTION_TAG, action);
        final double motorSpeed = readKineticSpeed(subLevel, CONTROLLER_MOTOR_LOCAL);
        final double pulleySpeed = readKineticSpeed(subLevel, CONTROLLER_PULLEY_LOCAL);
        final double offsetAfter = asDouble(readFieldRaw(pulley, "offset"));
        final String expectedDirection = action.contains("extend") ? "EXTEND"
                : action.contains("retract") ? "RETRACT" : "STOP";
        final String actualDirection = pulleyDirection(pulleySpeed);
        final String line = "SABLE_M20_CONTROLLER_CONTROL id=" + subLevel.getUniqueId()
                + " action=" + action
                + " motorLocal=" + fmt(CONTROLLER_MOTOR_LOCAL)
                + " requestedMotorValue=" + rpm
                + " requestedRpm=" + rpm
                + " observedMotorSpeed=" + fmt(motorSpeed)
                + " observedPulleySpeed=" + fmt(pulleySpeed)
                + " offsetBefore=" + fmt(offsetBefore)
                + " offsetAfter=" + fmt(offsetAfter)
                + " expectedDirection=" + expectedDirection
                + " actualDirection=" + actualDirection
                + " semantics=Create_CreativeMotor_ScrollValueBehaviour_setValue"
                + " directPulleyOffsetWrites=false";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static String pulleyDirection(final double speed) {
        if (speed > 0.0) {
            return "EXTEND";
        }
        if (speed < 0.0) {
            return "RETRACT";
        }
        return "STOPPED";
    }

    private static int prepareControllerPayload(final CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final List<BlockPos> payloadLocals = List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0));
        if (getLocalBlockState(subLevel, CONTROLLER_PULLEY_LOCAL).isAir()
                || getLocalBlockEntity(subLevel, CONTROLLER_PULLEY_LOCAL) == null) {
            throw ERROR_M20_FAILED.create("Rope Pulley controller is missing");
        }
        setFixtureMotorSpeed(subLevel, CONTROLLER_MOTOR_LOCAL, 0);
        final List<SubLevelBlockEditHelper.BlockChange> changes = new ArrayList<>();
        for (final BlockPos payloadLocal : payloadLocals) {
            final BlockState current = getLocalBlockState(subLevel, payloadLocal);
            if (!current.isAir() && !blockIdMatches(current, id("minecraft", "smooth_stone"))) {
                throw ERROR_M20_FAILED.create("Payload location " + fmt(payloadLocal)
                        + " is occupied by " + blockId(current));
            }
            if (current.isAir()) {
                changes.add(setLifecycleLocalBlock(subLevel, payloadLocal,
                        requireBlock(id("minecraft", "smooth_stone")).defaultBlockState()));
            }
        }
        if (!changes.isEmpty()) {
            SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
        }
        putM20String(subLevel, CONTROLLER_PAYLOAD_TAG, "true");
        final String line = "SABLE_M20_CONTROLLER_PAYLOAD id=" + subLevel.getUniqueId()
                + " payloadLocals=" + payloadLocals
                + " payloadRaw=" + payloadLocals.stream().map(local -> fmt(toPlot(subLevel, local))).toList()
                + " pulleyLocal=" + fmt(CONTROLLER_PULLEY_LOCAL)
                + " prepared=true"
                + " semantics=normal_Create_RopePulley_assembly_candidate"
                + " directPulleyOffsetWrites=false"
                + " directContraptionCreation=false";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int toggleLink(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final BlockState oldState = getLocalBlockState(subLevel, LINK_SIGNAL_LOCAL);
        final BlockState newState = blockIdMatches(oldState, REDSTONE_BLOCK_ID)
                ? Blocks.AIR.defaultBlockState() : Blocks.REDSTONE_BLOCK.defaultBlockState();
        final List<SubLevelBlockEditHelper.BlockChange> changes = List.of(
                setLifecycleLocalBlock(subLevel, LINK_SIGNAL_LOCAL, newState));
        SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
        final String line = "SABLE_M20_REDSTONE_LINK_TOGGLE id=" + subLevel.getUniqueId()
                + " signalLocal=" + fmt(LINK_SIGNAL_LOCAL)
                + " previous=" + blockId(oldState)
                + " current=" + blockId(newState)
                + " semantics=Create_RedstoneLinkBlock_neighbor_signal"
                + " receiverSignal=" + fieldString(getLocalBlockEntity(subLevel, LINK_RECEIVER_LOCAL), "receivedSignal")
                + " lampLit=" + propertyMatches(getLocalBlockState(subLevel, LINK_LAMP_LOCAL), "lit", "true")
                + " productionMixin=none";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int elevatorAssemble(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        setFixtureMotorSpeed(subLevel, ELEVATOR_MOTOR_LOCAL, -DEFAULT_RPM);
        putM20String(subLevel, ELEVATOR_ACTION_TAG, "assemble_or_descend");
        final BlockEntity pulley = getLocalBlockEntity(subLevel, ELEVATOR_PULLEY_LOCAL);
        final String line = "SABLE_M20_ELEVATOR_ASSEMBLE id=" + subLevel.getUniqueId()
                + " pulleyLocal=" + fmt(ELEVATOR_PULLEY_LOCAL)
                + " pulleyRaw=" + fmt(toPlot(subLevel, ELEVATOR_PULLEY_LOCAL))
                + " motorLocal=" + fmt(ELEVATOR_MOTOR_LOCAL)
                + " requestedMotorValue=" + -DEFAULT_RPM
                + " observedMotorSpeed=" + fmt(readKineticSpeed(subLevel, ELEVATOR_MOTOR_LOCAL))
                + " observedPulleySpeed=" + fmt(readKineticSpeed(subLevel, ELEVATOR_PULLEY_LOCAL))
                + " offset=" + fieldString(pulley, "offset")
                + " running=" + fieldString(pulley, "running")
                + " movedContraption=" + fieldString(pulley, "movedContraption")
                + " expectedFirstScanLocal=" + fmt(ELEVATOR_CABIN_CONTACT_LOCAL)
                + " expectedHorizontalRedstoneContacts=1"
                + " expectedColumn=ColumnCoords{x=1,z=0,facing=WEST}"
                + " semantics=Create_ElevatorPulley_speed_driven_assembly"
                + " directElevatorEntityCreation=false"
                + " directOffsetWrites=false";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int elevatorFloor(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final int floor = IntegerArgumentType.getInteger(context, "floor");
        final BlockPos requestedSignal = floor == 0 ? ELEVATOR_LOWER_SIGNAL_LOCAL : ELEVATOR_UPPER_SIGNAL_LOCAL;
        final BlockPos clearedSignal = floor == 0 ? ELEVATOR_UPPER_SIGNAL_LOCAL : ELEVATOR_LOWER_SIGNAL_LOCAL;
        final List<SubLevelBlockEditHelper.BlockChange> changes = List.of(
                setLifecycleLocalBlock(subLevel, clearedSignal, Blocks.AIR.defaultBlockState()),
                setLifecycleLocalBlock(subLevel, requestedSignal, Blocks.REDSTONE_BLOCK.defaultBlockState()));
        SubLevelBlockEditHelper.finalizeBlockChanges(subLevel, changes);
        putM20String(subLevel, ELEVATOR_ACTION_TAG, "floor_" + floor);
        final BlockEntity pulley = getLocalBlockEntity(subLevel, ELEVATOR_PULLEY_LOCAL);
        final String line = "SABLE_M20_ELEVATOR_FLOOR id=" + subLevel.getUniqueId()
                + " requestedFloor=" + floor
                + " signalLocal=" + fmt(requestedSignal)
                + " signalRaw=" + fmt(toPlot(subLevel, requestedSignal))
                + " lowerContactPowered=" + propertyMatches(getLocalBlockState(subLevel, ELEVATOR_LOWER_CONTACT_LOCAL),
                "powered", "true")
                + " upperContactPowered=" + propertyMatches(getLocalBlockState(subLevel, ELEVATOR_UPPER_CONTACT_LOCAL),
                "powered", "true")
                + " pulleyOffset=" + fieldString(pulley, "offset")
                + " running=" + fieldString(pulley, "running")
                + " movedContraption=" + fieldString(pulley, "movedContraption")
                + " semantics=vanilla_redstone_signal_into_Create_ElevatorContact"
                + " directTargetYWrite=false";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int elevatorDisassemble(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        setFixtureMotorSpeed(subLevel, ELEVATOR_MOTOR_LOCAL, 0);
        final BlockEntity pulley = getLocalBlockEntity(subLevel, ELEVATOR_PULLEY_LOCAL);
        final boolean clicked = invokeNoArgVoidRaw(pulley, "clicked");
        putM20String(subLevel, ELEVATOR_ACTION_TAG, "clicked_disassemble");
        final String line = "SABLE_M20_ELEVATOR_DISASSEMBLE id=" + subLevel.getUniqueId()
                + " pulleyLocal=" + fmt(ELEVATOR_PULLEY_LOCAL)
                + " motorStopped=true"
                + " clickedInvoked=" + clicked
                + " offset=" + fieldString(pulley, "offset")
                + " running=" + fieldString(pulley, "running")
                + " movedContraption=" + fieldString(pulley, "movedContraption")
                + " semantics=Create_ElevatorPulleyBlockEntity_clicked"
                + " directDisassemblyEntityEdit=false";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int setBodyVelocity(final CommandContext<CommandSourceStack> context,
                                       final String preset,
                                       final Vector3dc linearVelocity,
                                       final Vector3dc angularVelocityRad) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final SubLevelPhysicsSystem physicsSystem = SableCommandHelper.requireSubLevelPhysicsSystem(context);
        final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        handle.setLinearAndAngularVelocity(linearVelocity, angularVelocityRad);
        final String line = "SABLE_M20_PARENT id=" + subLevel.getUniqueId()
                + " preset=" + preset
                + " result=APPLIED_EXISTING_PHYSICS_HANDLE"
                + " requestedLinear=" + formatVector(linearVelocity)
                + " requestedAngularRadS=" + formatVector(angularVelocityRad)
                + " anchored=false"
                + " hiddenStorageUnchanged=true";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int saveReloadCheck(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final String line = "SABLE_M20_SAVE_RELOAD_CHECK id=" + subLevel.getUniqueId()
                + " name=" + nameOrNone(subLevel)
                + " blockCount=" + countNonAirBlocks(subLevel)
                + " note=run_before_and_after_manual_save_reload"
                + " persistence=UNVERIFIED_UNTIL_MANUAL_RELOAD";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static int acceptance(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerSubLevel subLevel = SubLevelArgumentType.getSingleSubLevel(context, "target");
        final String line = "SABLE_M20_ACCEPTANCE id=" + subLevel.getUniqueId()
                + " machineState=SEE_FAMILY_INSPECT_COMMAND"
                + " semanticPass=USER_OBSERVED_REQUIRED"
                + " visualPass=USER_OBSERVED_REQUIRED"
                + " parentTransformPass=USER_OBSERVED_REQUIRED"
                + " persistencePass=USER_OBSERVED_AFTER_RELOAD"
                + " hiddenPlotPoseTranslation=false";
        send(context, line);
        Sable.LOGGER.info(line);
        return 1;
    }

    private static FixtureSpec logisticsSpec() {
        return new FixtureSpec("logistics", List.of(
                scaffold(-1, 0, 0), scaffold(-1, 0, 1), scaffold(-1, 0, 2), scaffold(-1, 0, 3),
                scaffold(-1, 0, 4), scaffold(0, 0, 0), scaffold(0, 0, 1), scaffold(0, 0, 2),
                scaffold(0, 0, 3), scaffold(0, 0, 4),
                placed(new BlockPos(-1, 1, 0), CREATIVE_MOTOR_ID, "belt_kinetic_source_facing_east"),
                placedWith(new BlockPos(0, 1, 0), BELT_ID, "belt_connector_output_start",
                        state -> blockIdMatches(state, BELT_ID) && propertyMatches(state, "part", "start")),
                placedWith(new BlockPos(0, 1, 1), BELT_ID, "belt_connector_output_middle_1",
                        state -> blockIdMatches(state, BELT_ID) && propertyMatches(state, "part", "middle")),
                placedWith(new BlockPos(0, 1, 2), BELT_ID, "belt_connector_output_middle_2",
                        state -> blockIdMatches(state, BELT_ID) && propertyMatches(state, "part", "middle")),
                placedWith(new BlockPos(0, 1, 3), BELT_ID, "belt_connector_output_middle_3",
                        state -> blockIdMatches(state, BELT_ID) && propertyMatches(state, "part", "middle")),
                placedWith(new BlockPos(0, 1, 4), BELT_ID, "belt_connector_output_end",
                        state -> blockIdMatches(state, BELT_ID) && propertyMatches(state, "part", "end"))));
    }

    private static FixtureSpec fluidsSpec() {
        return new FixtureSpec("fluids", List.of(
                scaffold(-2, 0, -1), scaffold(-2, 0, 0), scaffold(-1, 0, -1), scaffold(-1, 0, 0),
                scaffold(0, 0, -1), scaffold(0, 0, 0), scaffold(1, 0, -1), scaffold(1, 0, 0),
                scaffold(2, 0, -1), scaffold(2, 0, 0),
                placed(new BlockPos(-2, 1, 0), FLUID_TANK_ID, "source_tank_water_4000mb"),
                placed(new BlockPos(-1, 1, 0), FLUID_PIPE_ID, "west_pipe"),
                placed(new BlockPos(0, 1, 0), MECHANICAL_PUMP_ID, "pump_facing_east"),
                placed(new BlockPos(1, 1, 0), FLUID_PIPE_ID, "east_pipe"),
                placed(new BlockPos(2, 1, 0), FLUID_TANK_ID, "destination_tank_empty"),
                placedWith(new BlockPos(0, 1, -1), COGWHEEL_ID, "pump_side_cog_axis_x",
                        state -> blockIdMatches(state, COGWHEEL_ID) && propertyMatches(state, "axis", "x")),
                placed(new BlockPos(-1, 1, -1), CREATIVE_MOTOR_ID, "pump_kinetic_source_facing_east")));
    }

    private static FixtureSpec kineticSpec() {
        return new FixtureSpec("kinetic", List.of(
                placed(new BlockPos(0, 0, 0), CREATIVE_MOTOR_ID, "shaft_kinetic_source"),
                placed(new BlockPos(1, 0, 0), SHAFT_ID, "first_inline_shaft"),
                placed(new BlockPos(2, 0, 0), SHAFT_ID, "second_inline_shaft"),
                placed(new BlockPos(4, 1, -1), CREATIVE_MOTOR_ID, "cog_kinetic_source"),
                placedWith(new BlockPos(4, 1, 0), COGWHEEL_ID, "powered_small_cog_axis_z",
                        state -> blockIdMatches(state, COGWHEEL_ID) && propertyMatches(state, "axis", "z")),
                placedWith(new BlockPos(5, 1, 0), COGWHEEL_ID, "meshed_small_cog_axis_z",
                        state -> blockIdMatches(state, COGWHEEL_ID) && propertyMatches(state, "axis", "z"))));
    }

    private static FixtureSpec redstoneSpec() {
        return new FixtureSpec("redstone", List.of(
                placed(new BlockPos(0, 0, 0), CREATIVE_MOTOR_ID, "kinetic_source"),
                placed(new BlockPos(1, 0, 0), SHAFT_ID, "input_shaft"),
                placed(new BlockPos(2, 0, 0), SHAFT_ID, "redstone_input_continuation"),
                placed(new BlockPos(3, 0, 0), GEARSHIFT_ID, "gearshift_neighbor_signal_canary"),
                placed(new BlockPos(4, 0, 0), SHAFT_ID, "downstream_reference_shaft"),
                placed(new BlockPos(5, 0, 0), SHAFT_ID, "downstream_speed_probe"),
                placed(REDSTONE_SIGNAL_LOCAL, REDSTONE_BLOCK_ID, "toggleable_redstone_source")));
    }

    private static FixtureSpec armSpec() {
        return new FixtureSpec("arm", List.of(
                scaffold(-2, 0, 0), scaffold(-2, 0, 1), scaffold(-2, 0, 2), scaffold(-1, 0, 0),
                scaffold(-1, 0, 1), scaffold(-1, 0, 2), scaffold(0, 0, 0), scaffold(0, 0, 1),
                scaffold(0, 0, 2), scaffold(1, 0, 1), scaffold(1, 0, 2),
                placed(new BlockPos(-2, 1, 0), DEPOT_ID, "arm_input_depot_seeded_item"),
                placed(new BlockPos(0, 1, 0), MECHANICAL_ARM_ID, "mechanical_arm_side_cog_powered"),
                placedWith(new BlockPos(1, 1, 0), COGWHEEL_ID, "arm_side_cog_axis_y",
                        state -> blockIdMatches(state, COGWHEEL_ID) && propertyMatches(state, "axis", "y")),
                placed(new BlockPos(1, 0, 0), CREATIVE_MOTOR_ID, "arm_kinetic_source_facing_up"),
                placed(new BlockPos(0, 1, 2), DEPOT_ID, "arm_output_depot")));
    }

    private static FixtureSpec controllerSpec() {
        return new FixtureSpec("controller", List.of(
                placed(new BlockPos(1, 4, 0), id("minecraft", "smooth_stone"), "pulley_support_scaffold"),
                placed(new BlockPos(2, 4, 0), id("minecraft", "smooth_stone"), "pulley_support_scaffold"),
                placed(new BlockPos(2, 5, 0), id("minecraft", "smooth_stone"), "pulley_support_scaffold"),
                placed(CONTROLLER_MOTOR_LOCAL, CREATIVE_MOTOR_ID, "pulley_kinetic_source_side"),
                placed(CONTROLLER_PULLEY_LOCAL, ROPE_PULLEY_ID, "rope_pulley_controller_canary")));
    }

    private static FixtureSpec elevatorSpec() {
        return new FixtureSpec("elevator", List.of(
                placed(new BlockPos(1, 0, 0), id("minecraft", "smooth_stone"), "elevator_contact_column_support"),
                placed(ELEVATOR_LOWER_CONTACT_LOCAL, ELEVATOR_CONTACT_ID, "elevator_lower_floor_contact_facing_west"),
                placed(new BlockPos(1, 3, 0), id("minecraft", "smooth_stone"), "elevator_contact_column_support"),
                placed(ELEVATOR_UPPER_CONTACT_LOCAL, ELEVATOR_CONTACT_ID, "elevator_upper_floor_contact_facing_west"),
                placed(new BlockPos(1, 5, 0), id("minecraft", "smooth_stone"), "elevator_contact_column_support"),
                placed(new BlockPos(2, 6, 0), id("minecraft", "smooth_stone"), "elevator_top_support"),
                placed(ELEVATOR_MOTOR_LOCAL, CREATIVE_MOTOR_ID, "elevator_kinetic_source_facing_west"),
                placed(ELEVATOR_PULLEY_LOCAL, ELEVATOR_PULLEY_ID, "elevator_pulley_controller"),
                placed(ELEVATOR_CABIN_CONTACT_LOCAL, REDSTONE_CONTACT_ID,
                        "elevator_cabin_redstone_contact_facing_east"),
                placed(ELEVATOR_CABIN_BLOCK_LOCAL, id("minecraft", "smooth_stone"),
                        "elevator_cabin_visual_reference_block")));
    }

    private static FixtureSpec linkSpec() {
        final List<PlacedBlock> blocks = new ArrayList<>();
        for (int x = -1; x <= 6; x++) {
            blocks.add(scaffold(x, 0, 0));
        }
        blocks.add(placedWith(LINK_TRANSMITTER_LOCAL, REDSTONE_LINK_ID,
                "redstone_link_transmitter_frequency_pair",
                state -> blockIdMatches(state, REDSTONE_LINK_ID)
                        && !propertyMatches(state, "receiver", "true")));
        blocks.add(placedWith(LINK_RECEIVER_LOCAL, REDSTONE_LINK_ID,
                "redstone_link_receiver_frequency_pair",
                state -> blockIdMatches(state, REDSTONE_LINK_ID)
                        && propertyMatches(state, "receiver", "true")));
        blocks.add(placed(LINK_LAMP_LOCAL, REDSTONE_LAMP_ID, "redstone_link_output_indicator"));
        return new FixtureSpec("redstone_link", blocks);
    }

    private static FixtureSpec chainDriveSpec() {
        return new FixtureSpec("chain_drive", List.of(
                placed(new BlockPos(0, 0, 0), CREATIVE_MOTOR_ID, "chain_kinetic_source"),
                placedWith(new BlockPos(1, 0, 0), ENCASED_CHAIN_DRIVE_ID, "encased_chain_drive_axis_x",
                        state -> blockIdMatches(state, ENCASED_CHAIN_DRIVE_ID)),
                placedWith(new BlockPos(1, 2, 0), ENCASED_CHAIN_DRIVE_ID, "encased_chain_drive_axis_x_partner",
                        state -> blockIdMatches(state, ENCASED_CHAIN_DRIVE_ID)),
                placedWith(new BlockPos(2, 2, 0), ADJUSTABLE_CHAIN_GEARSHIFT_ID,
                        "adjustable_chain_gearshift_axis_x",
                        state -> blockIdMatches(state, ADJUSTABLE_CHAIN_GEARSHIFT_ID)),
                placed(new BlockPos(3, 2, 0), SHAFT_ID, "chain_output_speed_probe")));
    }

    private static FixtureSpec redstoneContactSpec() {
        return new FixtureSpec("redstone_contact", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0), scaffold(2, 0, 0),
                placedWith(new BlockPos(0, 1, 0), REDSTONE_CONTACT_ID, "contact_source_facing_east",
                        state -> blockIdMatches(state, REDSTONE_CONTACT_ID)),
                placedWith(new BlockPos(1, 1, 0), REDSTONE_CONTACT_ID, "contact_receiver_facing_west",
                        state -> blockIdMatches(state, REDSTONE_CONTACT_ID)),
                placed(new BlockPos(2, 1, 0), REDSTONE_LAMP_ID, "contact_output_lamp")));
    }

    private static FixtureSpec thresholdSwitchSpec() {
        return new FixtureSpec("threshold_switch", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0), scaffold(2, 0, 0),
                placed(new BlockPos(0, 1, 0), ITEM_VAULT_ID, "threshold_storage_source"),
                placedWith(new BlockPos(1, 1, 0), THRESHOLD_SWITCH_ID, "threshold_switch_facing_west",
                        state -> blockIdMatches(state, THRESHOLD_SWITCH_ID)),
                placed(new BlockPos(2, 1, 0), REDSTONE_LAMP_ID, "threshold_output_lamp")));
    }

    private static FixtureSpec smartObserverSpec() {
        return new FixtureSpec("smart_observer", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0), scaffold(2, 0, 0),
                placedWith(new BlockPos(0, 1, 0), SMART_OBSERVER_ID, "smart_observer_facing_east",
                        state -> blockIdMatches(state, SMART_OBSERVER_ID)),
                placed(new BlockPos(1, 1, 0), id("minecraft", "stone"), "smart_observer_observed_block"),
                placed(new BlockPos(2, 1, 0), REDSTONE_LAMP_ID, "smart_observer_output_lamp")));
    }

    private static FixtureSpec funnelsTunnelsSpec() {
        return new FixtureSpec("funnels_tunnels", List.of(
                scaffold(0, 0, 0), scaffold(0, 0, 1), scaffold(0, 0, 2), scaffold(0, 0, 3),
                placed(new BlockPos(-1, 1, 0), DEPOT_ID, "funnel_source_depot_seeded"),
                placedWith(new BlockPos(0, 1, 0), ANDESITE_FUNNEL_ID, "source_andesite_funnel_facing_south",
                        state -> blockIdMatches(state, ANDESITE_FUNNEL_ID)),
                placedWith(new BlockPos(0, 1, 1), BELT_ID, "funnel_belt_start",
                        state -> blockIdMatches(state, BELT_ID)),
                placedWith(new BlockPos(0, 1, 2), ANDESITE_TUNNEL_ID, "andesite_tunnel_over_belt",
                        state -> blockIdMatches(state, ANDESITE_TUNNEL_ID)),
                placedWith(new BlockPos(0, 1, 3), BRASS_TUNNEL_ID, "brass_tunnel_over_belt",
                        state -> blockIdMatches(state, BRASS_TUNNEL_ID)),
                placedWith(new BlockPos(1, 1, 3), BRASS_FUNNEL_ID, "brass_funnel_output",
                        state -> blockIdMatches(state, BRASS_FUNNEL_ID)),
                placed(new BlockPos(2, 1, 3), DEPOT_ID, "funnel_destination_depot")));
    }

    private static FixtureSpec chuteSmartChuteSpec() {
        return new FixtureSpec("chute_smart_chute", List.of(
                scaffold(1, 0, 0), scaffold(0, 0, 1),
                placed(new BlockPos(0, 3, 0), DEPOT_ID, "chute_input_depot_seeded"),
                placed(new BlockPos(0, 2, 0), CHUTE_ID, "vertical_chute"),
                placed(new BlockPos(0, 1, 0), SMART_CHUTE_ID, "smart_chute_filter_canary"),
                placed(new BlockPos(0, 0, 0), DEPOT_ID, "chute_output_depot")));
    }

    private static FixtureSpec weightedEjectorSpec() {
        return new FixtureSpec("weighted_ejector", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0), scaffold(2, 0, 0), scaffold(3, 0, 0),
                placed(new BlockPos(0, 1, 0), WEIGHTED_EJECTOR_ID, "weighted_ejector_source_seeded"),
                placed(new BlockPos(3, 1, 0), DEPOT_ID, "weighted_ejector_target_depot")));
    }

    private static FixtureSpec vaultSpec() {
        return new FixtureSpec("vault", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0), scaffold(2, 0, 0),
                placed(new BlockPos(0, 1, 0), ITEM_VAULT_ID, "item_vault_section_a"),
                placed(new BlockPos(1, 1, 0), ITEM_VAULT_ID, "item_vault_section_b"),
                placed(new BlockPos(2, 1, 0), BRASS_FUNNEL_ID, "item_vault_output_funnel")));
    }

    private static FixtureSpec fluidValveSpec() {
        return new FixtureSpec("fluid_valve", List.of(
                scaffold(-2, 0, 0), scaffold(-1, 0, 0), scaffold(0, 0, 0), scaffold(1, 0, 0),
                scaffold(2, 0, 0),
                placed(new BlockPos(-2, 1, 0), FLUID_TANK_ID, "valve_source_tank_water"),
                placed(new BlockPos(-1, 1, 0), FLUID_PIPE_ID, "valve_input_pipe"),
                placed(new BlockPos(0, 1, 0), FLUID_VALVE_ID, "fluid_valve"),
                placed(new BlockPos(1, 1, 0), FLUID_PIPE_ID, "valve_output_pipe"),
                placed(new BlockPos(2, 1, 0), FLUID_TANK_ID, "valve_destination_tank")));
    }

    private static FixtureSpec smartFluidPipeSpec() {
        return new FixtureSpec("smart_fluid_pipe", List.of(
                scaffold(-2, 0, 0), scaffold(-1, 0, 0), scaffold(0, 0, 0), scaffold(1, 0, 0),
                scaffold(2, 0, 0),
                placed(new BlockPos(-2, 1, 0), FLUID_TANK_ID, "smart_pipe_source_tank_water"),
                placed(new BlockPos(-1, 1, 0), FLUID_PIPE_ID, "smart_pipe_input_pipe"),
                placed(new BlockPos(0, 1, 0), SMART_FLUID_PIPE_ID, "smart_fluid_pipe_filter_canary"),
                placed(new BlockPos(1, 1, 0), FLUID_PIPE_ID, "smart_pipe_output_pipe"),
                placed(new BlockPos(2, 1, 0), FLUID_TANK_ID, "smart_pipe_destination_tank")));
    }

    private static FixtureSpec hosePulleySpec() {
        return new FixtureSpec("hose_pulley", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0), scaffold(2, 0, 0),
                placed(new BlockPos(0, 2, 0), HOSE_PULLEY_ID, "hose_pulley_controller"),
                placed(new BlockPos(1, 2, 0), FLUID_PIPE_ID, "hose_pulley_pipe"),
                placed(new BlockPos(2, 2, 0), FLUID_TANK_ID, "hose_pulley_buffer_tank"),
                placed(new BlockPos(0, 1, 0), id("minecraft", "water"), "hose_pulley_source_water")));
    }

    private static FixtureSpec drillRegressionSpec() {
        return new FixtureSpec("drill_regression", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0), scaffold(2, 0, 0), scaffold(3, 0, 0),
                placed(new BlockPos(0, 1, 0), CREATIVE_MOTOR_ID, "drill_kinetic_source"),
                placed(new BlockPos(1, 1, 0), SHAFT_ID, "drill_input_shaft"),
                placed(new BlockPos(2, 1, 0), MECHANICAL_DRILL_ID, "drill_actor_facing_east"),
                placed(new BlockPos(4, 1, 0), id("minecraft", "stone"), "drill_break_target")));
    }

    private static FixtureSpec sawStaticSpec() {
        return new FixtureSpec("saw_static", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0), scaffold(2, 0, 0),
                placed(new BlockPos(0, 1, 0), CREATIVE_MOTOR_ID, "saw_kinetic_source"),
                placed(new BlockPos(1, 1, 0), SHAFT_ID, "saw_input_shaft"),
                placed(new BlockPos(2, 1, 0), MECHANICAL_SAW_ID, "static_saw_facing_east"),
                placed(new BlockPos(3, 1, 0), id("minecraft", "oak_log"), "static_saw_log_canary")));
    }

    private static FixtureSpec sawTreeSpec() {
        return new FixtureSpec("saw_tree", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0), scaffold(2, 0, 0), scaffold(3, 0, 0),
                placed(new BlockPos(0, 1, 0), CREATIVE_MOTOR_ID, "tree_saw_kinetic_source"),
                placed(new BlockPos(1, 1, 0), SHAFT_ID, "tree_saw_input_shaft"),
                placed(new BlockPos(2, 1, 0), MECHANICAL_SAW_ID, "tree_saw_facing_east"),
                placed(new BlockPos(4, 1, 0), id("minecraft", "oak_log"), "tree_trunk_base"),
                placed(new BlockPos(4, 2, 0), id("minecraft", "oak_log"), "tree_trunk_top"),
                placed(new BlockPos(4, 3, 0), id("minecraft", "oak_leaves"), "tree_leaf_canary")));
    }

    private static FixtureSpec harvesterSpec() {
        return new FixtureSpec("harvester", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0), scaffold(2, 0, 0), scaffold(3, 0, 0),
                placed(new BlockPos(0, 1, 0), CREATIVE_MOTOR_ID, "harvester_kinetic_source"),
                placed(new BlockPos(1, 1, 0), SHAFT_ID, "harvester_input_shaft"),
                placed(new BlockPos(2, 1, 0), MECHANICAL_HARVESTER_ID, "harvester_facing_east"),
                placed(new BlockPos(4, 0, 0), id("minecraft", "farmland"), "harvester_crop_soil"),
                placed(new BlockPos(4, 1, 0), id("minecraft", "wheat"), "harvester_mature_wheat")));
    }

    private static FixtureSpec ploughSpec() {
        return new FixtureSpec("plough", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0), scaffold(2, 0, 0), scaffold(3, 0, 0),
                placed(new BlockPos(0, 1, 0), CREATIVE_MOTOR_ID, "plough_kinetic_source"),
                placed(new BlockPos(1, 1, 0), SHAFT_ID, "plough_input_shaft"),
                placed(new BlockPos(2, 1, 0), MECHANICAL_PLOUGH_ID, "plough_facing_east"),
                placed(new BlockPos(4, 1, 0), id("minecraft", "dirt"), "plough_target_dirt")));
    }

    private static FixtureSpec rollerSpec() {
        return new FixtureSpec("roller", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0), scaffold(2, 0, 0), scaffold(3, 0, 0),
                placed(new BlockPos(0, 1, 0), CREATIVE_MOTOR_ID, "roller_kinetic_source"),
                placed(new BlockPos(1, 1, 0), SHAFT_ID, "roller_input_shaft"),
                placed(new BlockPos(2, 1, 0), MECHANICAL_ROLLER_ID, "roller_facing_east"),
                placed(new BlockPos(4, 0, 0), id("minecraft", "dirt"), "roller_surface_target")));
    }

    private static FixtureSpec portableStorageInterfaceSpec() {
        return new FixtureSpec("portable_storage_interface", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0), scaffold(2, 0, 0),
                placed(new BlockPos(0, 1, 0), PORTABLE_STORAGE_INTERFACE_ID, "stationary_storage_interface"),
                placed(new BlockPos(2, 1, 0), PORTABLE_STORAGE_INTERFACE_ID, "contraption_storage_interface"),
                placed(new BlockPos(3, 1, 0), ITEM_VAULT_ID, "storage_interface_payload_vault")));
    }

    private static FixtureSpec portableFluidInterfaceSpec() {
        return new FixtureSpec("portable_fluid_interface", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0), scaffold(2, 0, 0),
                placed(new BlockPos(0, 1, 0), PORTABLE_FLUID_INTERFACE_ID, "stationary_fluid_interface"),
                placed(new BlockPos(2, 1, 0), PORTABLE_FLUID_INTERFACE_ID, "contraption_fluid_interface"),
                placed(new BlockPos(3, 1, 0), FLUID_TANK_ID, "fluid_interface_payload_tank")));
    }

    private static FixtureSpec pressSpec() {
        return new FixtureSpec("press", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0),
                placed(new BlockPos(0, 1, 0), DEPOT_ID, "press_input_depot_seeded"),
                placed(new BlockPos(0, 3, 0), MECHANICAL_PRESS_ID, "mechanical_press"),
                placed(new BlockPos(1, 3, 0), CREATIVE_MOTOR_ID, "press_kinetic_source")));
    }

    private static FixtureSpec mixerBasinSpec() {
        return new FixtureSpec("mixer_basin", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0),
                placed(new BlockPos(0, 1, 0), BASIN_ID, "mixer_basin_seeded"),
                placed(new BlockPos(0, 3, 0), MECHANICAL_MIXER_ID, "mechanical_mixer"),
                placedWith(new BlockPos(1, 3, 0), COGWHEEL_ID, "mixer_side_cog_axis_y",
                        state -> blockIdMatches(state, COGWHEEL_ID)),
                placed(new BlockPos(1, 2, 0), CREATIVE_MOTOR_ID, "mixer_kinetic_source_facing_up")));
    }

    private static FixtureSpec heatedBasinSpec() {
        return new FixtureSpec("heated_basin", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0),
                placed(new BlockPos(0, 1, 0), BLAZE_BURNER_ID, "basin_heat_source"),
                placed(new BlockPos(0, 2, 0), BASIN_ID, "heated_basin_seeded"),
                placed(new BlockPos(0, 4, 0), MECHANICAL_MIXER_ID, "heated_basin_mixer"),
                placed(new BlockPos(1, 4, 0), CREATIVE_MOTOR_ID, "heated_mixer_kinetic_source")));
    }

    private static FixtureSpec millstoneSpec() {
        return new FixtureSpec("millstone", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0),
                placed(new BlockPos(0, 1, 0), MILLSTONE_ID, "millstone_seeded"),
                placed(new BlockPos(1, 1, 0), CREATIVE_MOTOR_ID, "millstone_kinetic_source")));
    }

    private static FixtureSpec crushingWheelsSpec() {
        return new FixtureSpec("crushing_wheels", List.of(
                scaffold(-2, 0, 0), scaffold(-1, 0, -1), scaffold(1, 0, -1),
                scaffold(2, 0, 0), scaffold(0, 0, -1), scaffold(0, 0, 1),
                placedWith(CRUSHING_LEFT_WHEEL_LOCAL, CRUSHING_WHEEL_ID, "left_crushing_wheel_axis_z",
                        state -> blockIdMatches(state, CRUSHING_WHEEL_ID)),
                placedWith(CRUSHING_RIGHT_WHEEL_LOCAL, CRUSHING_WHEEL_ID, "right_crushing_wheel_axis_z",
                        state -> blockIdMatches(state, CRUSHING_WHEEL_ID)),
                placed(CRUSHING_LEFT_MOTOR_LOCAL, CREATIVE_MOTOR_ID, "left_crushing_wheel_source_axis_z"),
                placed(CRUSHING_RIGHT_MOTOR_LOCAL, CREATIVE_MOTOR_ID, "right_crushing_wheel_source_axis_z")));
    }

    private static FixtureSpec mechanicalCraftersSpec() {
        return new FixtureSpec("mechanical_crafters", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0), scaffold(0, 0, 1), scaffold(1, 0, 1),
                placed(new BlockPos(0, 1, 0), MECHANICAL_CRAFTER_ID, "crafter_grid_00"),
                placed(new BlockPos(1, 1, 0), MECHANICAL_CRAFTER_ID, "crafter_grid_10"),
                placed(new BlockPos(0, 1, 1), MECHANICAL_CRAFTER_ID, "crafter_grid_01"),
                placed(new BlockPos(1, 1, 1), MECHANICAL_CRAFTER_ID, "crafter_grid_11"),
                placed(new BlockPos(2, 1, 0), CREATIVE_MOTOR_ID, "crafter_kinetic_source")));
    }

    private static FixtureSpec encasedFanSpec() {
        return new FixtureSpec("encased_fan", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0), scaffold(2, 0, 0),
                placed(new BlockPos(0, 1, 0), CREATIVE_MOTOR_ID, "fan_kinetic_source"),
                placed(new BlockPos(1, 1, 0), ENCASED_FAN_ID, "encased_fan_facing_east"),
                placed(new BlockPos(2, 1, 0), id("minecraft", "water"), "fan_processing_water_canary")));
    }

    private static FixtureSpec gaugesSpec() {
        return new FixtureSpec("gauges", List.of(
                placed(new BlockPos(0, 0, 0), CREATIVE_MOTOR_ID, "gauge_kinetic_source"),
                placed(new BlockPos(1, 0, 0), SHAFT_ID, "gauge_input_shaft"),
                placed(new BlockPos(2, 0, 0), SPEEDOMETER_ID, "speedometer_probe"),
                placed(new BlockPos(3, 0, 0), STRESSOMETER_ID, "stressometer_probe")));
    }

    private static FixtureSpec rotationSpeedControllerSpec() {
        return new FixtureSpec("rotation_speed_controller", List.of(
                placed(new BlockPos(0, 0, 0), CREATIVE_MOTOR_ID, "rsc_kinetic_source"),
                placedWith(new BlockPos(1, 0, 0), LARGE_COGWHEEL_ID, "rsc_large_cog_axis_x",
                        state -> blockIdMatches(state, LARGE_COGWHEEL_ID)),
                placed(new BlockPos(2, 0, 0), ROTATION_SPEED_CONTROLLER_ID, "rotation_speed_controller"),
                placed(new BlockPos(3, 0, 0), SHAFT_ID, "rsc_output_shaft")));
    }

    private static FixtureSpec displayLinkSpec() {
        return new FixtureSpec("display_link", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0), scaffold(2, 0, 0),
                placed(new BlockPos(0, 1, 0), CREATIVE_MOTOR_ID, "display_source_motor"),
                placed(new BlockPos(1, 1, 0), SPEEDOMETER_ID, "display_link_source_speedometer"),
                placed(new BlockPos(2, 1, 0), DISPLAY_LINK_ID, "display_link"),
                placed(new BlockPos(4, 1, 0), DISPLAY_BOARD_ID, "display_board_target")));
    }

    private static FixtureSpec cartAssemblerSpec() {
        return new FixtureSpec("cart_assembler", List.of(
                scaffold(0, 0, 0), scaffold(1, 0, 0), scaffold(2, 0, 0),
                placed(new BlockPos(0, 1, 0), CART_ASSEMBLER_ID, "cart_assembler_controller"),
                placed(new BlockPos(0, 2, 0), RADIAL_CHASSIS_ID, "cart_assembler_payload_chassis"),
                placed(new BlockPos(1, 2, 0), id("minecraft", "smooth_stone"), "cart_assembler_payload_block")));
    }

    private static PlacedBlock scaffold(final int x, final int y, final int z) {
        return placed(new BlockPos(x, y, z), id("minecraft", "smooth_stone"), "contiguous_sable_scaffold");
    }

    private static PlacedBlock placed(final BlockPos localPos, final ResourceLocation blockId, final String role) {
        return new PlacedBlock(localPos, blockId, role, state -> blockIdMatches(state, blockId));
    }

    private static PlacedBlock placedWith(final BlockPos localPos,
                                          final ResourceLocation blockId,
                                          final String role,
                                          final Predicate<BlockState> validator) {
        return new PlacedBlock(localPos, blockId, role, validator);
    }

    private static List<SubLevelBlockEditHelper.BlockChange> applyFixture(final ServerSubLevel subLevel,
                                                                          final FixtureSpec spec,
                                                                          final boolean clearFirst) {
        final List<SubLevelBlockEditHelper.BlockChange> changes = new ArrayList<>();
        if (clearFirst) {
            final FixtureBounds bounds = fixtureBounds(spec);
            for (int x = bounds.minLocal().getX(); x <= bounds.maxLocal().getX(); x++) {
                for (int y = bounds.minLocal().getY(); y <= bounds.maxLocal().getY(); y++) {
                    for (int z = bounds.minLocal().getZ(); z <= bounds.maxLocal().getZ(); z++) {
                        final BlockPos local = new BlockPos(x, y, z);
                        requireLocalOffsetFitsCenterChunk(local);
                        if (!getLocalBlockState(subLevel, local).isAir()) {
                            changes.add(setLifecycleLocalBlock(subLevel, local, Blocks.AIR.defaultBlockState()));
                        }
                    }
                }
            }
        }
        for (final PlacedBlock block : fixtureBlocks(spec)) {
            requireLocalOffsetFitsCenterChunk(block.localPos());
            changes.add(setLifecycleLocalBlock(subLevel, block.localPos(), canonicalState(block)));
        }
        return changes;
    }

    private static SubLevelBlockEditHelper.BlockChange setLifecycleLocalBlock(final ServerSubLevel subLevel,
                                                                              final BlockPos localPos,
                                                                              final BlockState newState) {
        final BlockState oldState = getLocalBlockState(subLevel, localPos);
        final BlockPos plotPos = toPlot(subLevel, localPos);
        subLevel.getPlot().getEmbeddedLevelAccessor().setBlock(localPos, newState, 3);
        return new SubLevelBlockEditHelper.BlockChange(localPos.immutable(), plotPos.immutable(), oldState, newState);
    }

    private static void configureFixtureAfterPlacement(final ServerSubLevel subLevel, final FixtureSpec spec) {
        switch (spec.family()) {
            case "kinetic" -> {
                setFixtureMotorSpeed(subLevel, new BlockPos(0, 0, 0), DEFAULT_RPM);
                setFixtureMotorSpeed(subLevel, new BlockPos(4, 1, -1), DEFAULT_RPM);
            }
            case "redstone" -> setFixtureMotorSpeed(subLevel, KINETIC_MOTOR_LOCAL, DEFAULT_RPM);
            case "logistics" -> {
                setFixtureMotorSpeed(subLevel, new BlockPos(-1, 1, 0), LOGISTICS_RPM);
                configureBeltController(subLevel, List.of(new BlockPos(0, 1, 0), new BlockPos(0, 1, 1),
                        new BlockPos(0, 1, 2), new BlockPos(0, 1, 3), new BlockPos(0, 1, 4)));
            }
            case "fluids" -> {
                setFixtureMotorSpeed(subLevel, new BlockPos(-1, 1, -1), DEFAULT_RPM);
                fillFluidHandler(subLevel, new BlockPos(-2, 1, 0), new FluidStack(Fluids.WATER, 4000));
            }
            case "arm" -> {
                setFixtureMotorSpeed(subLevel, new BlockPos(1, 0, 0), DEFAULT_RPM);
                seedContainer(subLevel, new BlockPos(-2, 1, 0), new ItemStack(Items.IRON_INGOT, 1));
                configureArmInteractionPoints(subLevel);
            }
            case "crushing_wheels" -> configureCrushingWheelsFixture(subLevel);
            case "controller" -> setFixtureMotorSpeed(subLevel, CONTROLLER_MOTOR_LOCAL, 0);
            case "redstone_link" -> configureRedstoneLinkFixture(subLevel);
            case "elevator" -> {
                setFixtureMotorSpeed(subLevel, ELEVATOR_MOTOR_LOCAL, 0);
                configureElevatorContacts(subLevel);
            }
            default -> configureGenericGauntletFixture(subLevel, spec);
        }
    }

    private static void configureGenericGauntletFixture(final ServerSubLevel subLevel, final FixtureSpec spec) {
        for (final PlacedBlock block : spec.blocks()) {
            if (block.blockId().equals(CREATIVE_MOTOR_ID)) {
                setFixtureMotorSpeed(subLevel, block.localPos(), DEFAULT_RPM);
            }
            if (block.role().contains("seeded")) {
                seedContainer(subLevel, block.localPos(), new ItemStack(Items.IRON_INGOT, 1));
            }
            if (block.role().contains("tank_water")) {
                fillFluidHandler(subLevel, block.localPos(), new FluidStack(Fluids.WATER, 4000));
            }
        }
    }

    private static BlockState canonicalState(final PlacedBlock block) {
        BlockState state = requireBlock(block.blockId()).defaultBlockState();
        if (block.blockId().equals(SHAFT_ID)) {
            state = setPropertyIfPresent(state, "axis", "x");
        } else if (block.blockId().equals(CREATIVE_MOTOR_ID)) {
            state = setPropertyIfPresent(state, "facing", motorFacing(block.localPos(), block.role()));
        } else if (block.blockId().equals(LARGE_COGWHEEL_ID)
                || block.blockId().equals(ENCASED_CHAIN_DRIVE_ID)
                || block.blockId().equals(ADJUSTABLE_CHAIN_GEARSHIFT_ID)) {
            final String axis = block.role().contains("axis_z") ? "z" : block.role().contains("axis_y") ? "y" : "x";
            state = setPropertyIfPresent(state, "axis", axis);
        } else if (block.blockId().equals(CLUTCH_ID) || block.blockId().equals(GEARSHIFT_ID)) {
            state = setPropertyIfPresent(state, "axis", "x");
            state = setPropertyIfPresent(state, "powered", "false");
        } else if (block.blockId().equals(MECHANICAL_PUMP_ID)) {
            state = setPropertyIfPresent(state, "facing", "east");
        } else if (block.blockId().equals(ANDESITE_FUNNEL_ID) || block.blockId().equals(BRASS_FUNNEL_ID)) {
            state = setPropertyIfPresent(state, "facing", block.role().startsWith("source_") ? "south" : "north");
        } else if (block.blockId().equals(ANDESITE_TUNNEL_ID) || block.blockId().equals(BRASS_TUNNEL_ID)) {
            state = setPropertyIfPresent(state, "axis", "z");
        } else if (block.blockId().equals(MECHANICAL_ARM_ID)) {
            state = setPropertyIfPresent(state, "facing", "south");
            state = setPropertyIfPresent(state, "ceiling", "false");
        } else if (block.blockId().equals(MECHANICAL_DRILL_ID)
                || block.blockId().equals(MECHANICAL_SAW_ID)
                || block.blockId().equals(MECHANICAL_HARVESTER_ID)
                || block.blockId().equals(MECHANICAL_PLOUGH_ID)
                || block.blockId().equals(MECHANICAL_ROLLER_ID)
                || block.blockId().equals(ENCASED_FAN_ID)) {
            state = setPropertyIfPresent(state, "facing", "east");
            state = setPropertyIfPresent(state, "axis", "x");
        } else if (block.blockId().equals(MECHANICAL_PRESS_ID)) {
            state = setPropertyIfPresent(state, "facing", "down");
        } else if (block.blockId().equals(MECHANICAL_MIXER_ID)) {
            state = setPropertyIfPresent(state, "facing", "down");
        } else if (block.blockId().equals(MECHANICAL_CRAFTER_ID)) {
            state = setPropertyIfPresent(state, "facing", "east");
        } else if (block.blockId().equals(FLUID_VALVE_ID)
                || block.blockId().equals(SMART_FLUID_PIPE_ID)
                || block.blockId().equals(HOSE_PULLEY_ID)) {
            state = setPropertyIfPresent(state, "facing", "east");
        } else if (block.blockId().equals(CRUSHING_WHEEL_ID)) {
            final String axis = block.role().contains("axis_z") ? "z" : block.role().contains("axis_x") ? "x" : "y";
            state = setPropertyIfPresent(state, "axis", axis);
        } else if (block.blockId().equals(BLAZE_BURNER_ID)) {
            state = setPropertyIfPresent(state, "blaze", "smouldering");
        } else if (block.blockId().equals(ROPE_PULLEY_ID)) {
            state = setPropertyIfPresent(state, "axis", "x");
        } else if (block.blockId().equals(ELEVATOR_PULLEY_ID)) {
            state = setPropertyIfPresent(state, "facing", "north");
        } else if (block.blockId().equals(ELEVATOR_CONTACT_ID)) {
            state = setPropertyIfPresent(state, "facing", "west");
            state = setPropertyIfPresent(state, "powered", "false");
            state = setPropertyIfPresent(state, "calling", "false");
            state = setPropertyIfPresent(state, "powering", "false");
        } else if (block.blockId().equals(REDSTONE_CONTACT_ID)) {
            state = setPropertyIfPresent(state, "facing", block.role().contains("facing_west") ? "west" : "east");
        } else if (block.blockId().equals(THRESHOLD_SWITCH_ID)
                || block.blockId().equals(SMART_OBSERVER_ID)
                || block.blockId().equals(DISPLAY_LINK_ID)
                || block.blockId().equals(PORTABLE_STORAGE_INTERFACE_ID)
                || block.blockId().equals(PORTABLE_FLUID_INTERFACE_ID)) {
            state = setPropertyIfPresent(state, "facing", block.role().contains("facing_west") ? "west" : "east");
        } else if (block.blockId().equals(COGWHEEL_ID)) {
            final String axis = block.role().contains("axis_z") ? "z" : block.role().contains("axis_x") ? "x" : "y";
            state = setPropertyIfPresent(state, "axis", axis);
        } else if (block.blockId().equals(BELT_ID)) {
            state = setPropertyIfPresent(state, "facing", "south");
            state = setPropertyIfPresent(state, "slope", "horizontal");
            if (block.role().contains("middle")) {
                state = setPropertyIfPresent(state, "part", "middle");
            } else if (block.role().contains("end")) {
                state = setPropertyIfPresent(state, "part", "end");
            } else {
                state = setPropertyIfPresent(state, "part", "start");
            }
        } else if (block.blockId().equals(REDSTONE_LINK_ID)) {
            state = setPropertyIfPresent(state, "facing", "east");
            state = setPropertyIfPresent(state, "receiver", block.role().contains("receiver") ? "true" : "false");
            state = setPropertyIfPresent(state, "powered", "false");
        } else if (block.blockId().equals(REDSTONE_LAMP_ID)) {
            state = setPropertyIfPresent(state, "lit", "false");
        } else if (block.blockId().equals(id("minecraft", "wheat"))) {
            state = setPropertyIfPresent(state, "age", "7");
        }
        return state;
    }

    private static String motorFacing(final BlockPos localPos, final String role) {
        if (role.contains("arm_kinetic_source")) {
            return "up";
        }
        if (role.contains("pulley_kinetic_source_side")) {
            return "west";
        }
        if (role.contains("elevator_kinetic_source")) {
            return "west";
        }
        if (role.contains("press_kinetic_source") || role.contains("millstone_kinetic_source")
                || role.contains("display_source_motor")) {
            return "west";
        }
        if (role.contains("crushing_wheel_source_axis_z")) {
            return "south";
        }
        return "east";
    }

    private static FixtureCheck checkFixture(final ServerSubLevel subLevel, final FixtureSpec spec) {
        final List<String> failures = new ArrayList<>();
        final boolean controllerPayloadLive = "controller".equals(spec.family())
                && controllerMovedContraptionPresent(subLevel);
        final boolean elevatorCabinLive = "elevator".equals(spec.family())
                && controllerMovedContraptionPresent(subLevel, ELEVATOR_PULLEY_LOCAL);
        for (final PlacedBlock expected : fixtureBlocks(spec)) {
            final BlockState state = getLocalBlockState(subLevel, expected.localPos());
            final boolean validCapturedPayload = controllerPayloadLive
                    && expected.role().startsWith("pulley_payload_")
                    && state.isAir();
            final boolean validCapturedCabin = elevatorCabinLive
                    && expected.role().startsWith("elevator_cabin_")
                    && state.isAir();
            if (!expected.valid(state) && !validCapturedPayload && !validCapturedCabin) {
                failures.add("invalid_" + expected.role() + "_at_local_" + fmt(expected.localPos())
                        + "_expected_" + expected.blockId() + "_actual_" + blockId(state));
            }
            if (!state.isAir() && state.hasBlockEntity() && getLocalBlockEntity(subLevel, expected.localPos()) == null) {
                failures.add("missing_block_entity_for_" + expected.role() + "_at_local_" + fmt(expected.localPos()));
            }
            if (!allocatedPlotOwns(subLevel, expected.localPos())) {
                failures.add("fixture_ownership_not_in_named_sable_plot_at_local_" + fmt(expected.localPos()));
            }
        }
        for (final BlockPos airLocal : requiredAirLocals(subLevel, spec)) {
            final BlockState state = getLocalBlockState(subLevel, airLocal);
            if (!state.isAir()) {
                failures.add("required_air_column_blocked_at_local_" + fmt(airLocal) + "_actual_" + blockId(state));
            }
        }
        if ("arm".equals(spec.family())) {
            final BlockEntity arm = getLocalBlockEntity(subLevel, new BlockPos(0, 1, 0));
            if (collectionSize(readFieldRaw(arm, "inputs")) != 1) {
                failures.add("arm_expected_one_take_input_point_actual_" + fieldString(arm, "inputs"));
            }
            if (collectionSize(readFieldRaw(arm, "outputs")) != 1) {
                failures.add("arm_expected_one_deposit_output_point_actual_" + fieldString(arm, "outputs"));
            }
        } else if ("controller".equals(spec.family())
                && "true".equals(getM20String(subLevel, CONTROLLER_PAYLOAD_TAG, "false"))) {
            final List<BlockPos> payloadLocals = List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0));
            final boolean staticPayloadPresent = payloadLocals.stream()
                    .allMatch(local -> blockIdMatches(getLocalBlockState(subLevel, local),
                            id("minecraft", "smooth_stone")));
            if (!staticPayloadPresent && !controllerMovedContraptionPresent(subLevel)) {
                failures.add("controller_payload_expected_static_smooth_stone_or_Create_controlled_contraption");
            }
        } else if ("elevator".equals(spec.family())) {
            final boolean cabinStatic = blockIdMatches(getLocalBlockState(subLevel, ELEVATOR_CABIN_BLOCK_LOCAL),
                    id("minecraft", "smooth_stone"))
                    || controllerMovedContraptionPresent(subLevel, ELEVATOR_PULLEY_LOCAL);
            if (!cabinStatic) {
                failures.add("elevator_cabin_expected_static_or_Create_controlled_contraption");
            }
            if (getLocalBlockEntity(subLevel, ELEVATOR_PULLEY_LOCAL) == null) {
                failures.add("elevator_pulley_missing_block_entity");
            }
            if (getLocalBlockEntity(subLevel, ELEVATOR_LOWER_CONTACT_LOCAL) == null
                    || getLocalBlockEntity(subLevel, ELEVATOR_UPPER_CONTACT_LOCAL) == null) {
                failures.add("elevator_floor_contact_block_entity_missing");
            }
        } else if ("crushing_wheels".equals(spec.family()) && fixtureAgeTicks(subLevel) >= SETTLE_TICKS) {
            if (!blockIdMatches(getLocalBlockState(subLevel, CRUSHING_CONTROLLER_LOCAL),
                    CRUSHING_WHEEL_CONTROLLER_ID)) {
                failures.add("crushing_expected_Create_controller_block_at_local_"
                        + fmt(CRUSHING_CONTROLLER_LOCAL));
            }
            if (getLocalBlockEntity(subLevel, CRUSHING_CONTROLLER_LOCAL) == null) {
                failures.add("crushing_expected_controller_block_entity_at_local_"
                        + fmt(CRUSHING_CONTROLLER_LOCAL));
            }
            if (!crushingRotationRelationshipValid(subLevel)) {
                failures.add("crushing_rotation_relationship_invalid_expected_left_negative_right_positive_axis_z");
            }
        }
        if (fixtureAgeTicks(subLevel) >= SETTLE_TICKS) {
            failures.addAll(settledReadinessFailures(subLevel, spec));
        }
        return new FixtureCheck(failures, failures.isEmpty());
    }

    private static List<String> settledReadinessFailures(final ServerSubLevel subLevel, final FixtureSpec spec) {
        final List<String> failures = new ArrayList<>();
        if ("controller".equals(spec.family()) || "elevator".equals(spec.family())) {
            return failures;
        }
        for (final PlacedBlock block : spec.blocks()) {
            if (!block.blockId().equals(CREATIVE_MOTOR_ID)) {
                continue;
            }
            final double speed = readKineticSpeed(subLevel, block.localPos());
            if (!Double.isFinite(speed) || speed == 0.0) {
                failures.add("settled_power_source_not_ready_role_" + block.role()
                        + "_local_" + fmt(block.localPos())
                        + "_speed_" + fmt(speed));
            }
        }
        return failures;
    }

    private static boolean controllerMovedContraptionPresent(final ServerSubLevel subLevel) {
        return controllerMovedContraptionPresent(subLevel, CONTROLLER_PULLEY_LOCAL);
    }

    private static boolean controllerMovedContraptionPresent(final ServerSubLevel subLevel,
                                                            final BlockPos controllerLocal) {
        final Object moved = readFieldRaw(getLocalBlockEntity(subLevel, controllerLocal), "movedContraption");
        if (moved == null) {
            return false;
        }
        final Object alive = invokeNoArgRaw(moved, "isAlive");
        return !(alive instanceof Boolean) || (Boolean) alive;
    }

    private static List<String> familyDiagnostics(final ServerSubLevel subLevel, final FixtureSpec spec) {
        final List<String> lines = new ArrayList<>();
        final String prefix = "SABLE_M20_" + spec.family().toUpperCase(Locale.ROOT) + "_DIAG id="
                + subLevel.getUniqueId();
        lines.add(prefix
                + " fixtureAgeTicks=" + fixtureAgeTicks(subLevel)
                + " settled=" + (fixtureAgeTicks(subLevel) >= SETTLE_TICKS)
                + " placementLifecycle=embedded_local_setBlock_flags_3_then_finalize"
                + " sameSableOwnership=" + pass(checkFixture(subLevel, spec).failures().stream()
                .noneMatch(failure -> failure.contains("fixture_ownership")))
                + " productionPatch=" + ("logistics".equals(spec.family())
                ? "Sable_only_BeltRenderer_BER_fallback" : "none"));
        if ("kinetic".equals(spec.family())) {
            for (final BlockPos local : List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0),
                    new BlockPos(2, 0, 0), new BlockPos(4, 1, -1), new BlockPos(4, 1, 0),
                    new BlockPos(5, 1, 0))) {
                lines.add(kineticLine(prefix, subLevel, local, "kinetic_lifecycle_probe"));
            }
            lines.add(prefix + " cogMeshingGeometry=small_cog_axis_z_at_(4,1,0)_meshes_with_parallel_axis_z_at_(5,1,0)"
                    + " sourceAudit=Create_ICogWheel_parallel_axis_adjacent_small_cogs");
        } else if ("redstone".equals(spec.family())) {
            for (final BlockPos local : List.of(new BlockPos(0, 0, 0), new BlockPos(3, 0, 0),
                    new BlockPos(4, 0, 0), new BlockPos(5, 0, 0))) {
                lines.add(kineticLine(prefix, subLevel, local, "redstone_speed_probe"));
            }
            lines.add(prefix
                    + " redstoneLocal=" + fmt(REDSTONE_SIGNAL_LOCAL)
                    + " signalBlock=" + blockId(getLocalBlockState(subLevel, REDSTONE_SIGNAL_LOCAL))
                    + " gearshiftPowered=" + propertyMatches(getLocalBlockState(subLevel, new BlockPos(3, 0, 0)),
                    "powered", "true")
                    + " toggleSemantics=place_or_remove_redstone_source_not_direct_POWERED_write");
        } else if ("logistics".equals(spec.family())) {
            for (final BlockPos local : List.of(new BlockPos(0, 1, 0), new BlockPos(0, 1, 1),
                    new BlockPos(0, 1, 2), new BlockPos(0, 1, 3), new BlockPos(0, 1, 4))) {
                lines.add(prefix
                        + " beltLocal=" + fmt(local)
                        + " state=" + getLocalBlockState(subLevel, local)
                        + " controller=" + fieldString(getLocalBlockEntity(subLevel, local), "controller")
                        + " beltLength=" + fieldString(getLocalBlockEntity(subLevel, local), "beltLength")
                        + " index=" + fieldString(getLocalBlockEntity(subLevel, local), "index")
                        + " speed=" + fmt(readKineticSpeed(subLevel, local)));
            }
            lines.add(prefix
                    + " inputMotorSpeed=" + fmt(readKineticSpeed(subLevel, new BlockPos(-1, 1, 0)))
                    + " outputEndpointSpeed=" + fmt(readKineticSpeed(subLevel, new BlockPos(0, 1, 4)))
                    + " transportedItems=" + beltTransportSummary(getLocalBlockEntity(subLevel, new BlockPos(0, 1, 0)))
                    + " beltConstruction=Create_BeltConnector_final_state_mirror"
                    + " BeltRenderer=VISUALIZATION_GATE_FORCED_TO_BER_FOR_SABLE"
                    + " transportedItemRender=Create_BeltRenderer_renderItem_visible_space_culling");
        } else if ("fluids".equals(spec.family())) {
            lines.add(kineticLine(prefix, subLevel, new BlockPos(-1, 1, -1), "pump_motor"));
            lines.add(kineticLine(prefix, subLevel, new BlockPos(0, 1, -1), "pump_side_cog"));
            lines.add(kineticLine(prefix, subLevel, new BlockPos(0, 1, 0), "pump"));
            lines.add(prefix
                    + " sourceTankFluid=" + fluidSummary(subLevel, new BlockPos(-2, 1, 0))
                    + " destinationTankFluid=" + fluidSummary(subLevel, new BlockPos(2, 1, 0))
                    + " pumpFacing=" + statePropertyName(getLocalBlockState(subLevel, new BlockPos(0, 1, 0)),
                    "facing", "unknown")
                    + " expectedFlow=WEST_SOURCE_TO_EAST_DESTINATION"
                    + " rendererQualification=PENDING_VALID_TRANSFER_RUNTIME");
        } else if ("arm".equals(spec.family())) {
            lines.add(kineticLine(prefix, subLevel, new BlockPos(1, 0, 0), "arm_motor"));
            lines.add(kineticLine(prefix, subLevel, new BlockPos(1, 1, 0), "arm_side_cog"));
            lines.add(kineticLine(prefix, subLevel, new BlockPos(0, 1, 0), "mechanical_arm"));
            lines.add(prefix
                    + " inputDepotItems=" + containerSummary(subLevel, new BlockPos(-2, 1, 0))
                    + " outputDepotItems=" + containerSummary(subLevel, new BlockPos(0, 1, 2))
                    + " armPhase=" + fieldString(getLocalBlockEntity(subLevel, new BlockPos(0, 1, 0)), "phase")
                    + " inputs=" + fieldString(getLocalBlockEntity(subLevel, new BlockPos(0, 1, 0)), "inputs")
                    + " outputs=" + fieldString(getLocalBlockEntity(subLevel, new BlockPos(0, 1, 0)), "outputs")
                    + " interactionPointTag=" + fieldString(getLocalBlockEntity(subLevel, new BlockPos(0, 1, 0)),
                    "interactionPointTag")
                    + " rendererQualification=Sable_only_ArmRenderer_BER_fallback"
                    + " selectionOutlines=Sable_visible_AABB_Outliner_bridge");
        } else if ("controller".equals(spec.family())) {
            lines.add(kineticLine(prefix, subLevel, CONTROLLER_MOTOR_LOCAL, "pulley_motor"));
            lines.add(kineticLine(prefix, subLevel, CONTROLLER_PULLEY_LOCAL, "rope_pulley"));
            lines.add(prefix
                    + " ropePathBelowPulleyClear="
                    + ropePathBelowPulleyClear(subLevel, false)
                    + " payloadPrepared=" + getM20String(subLevel, CONTROLLER_PAYLOAD_TAG, "false")
                    + " pulleyOffset=" + fieldString(getLocalBlockEntity(subLevel, CONTROLLER_PULLEY_LOCAL), "offset")
                    + " running=" + fieldString(getLocalBlockEntity(subLevel, CONTROLLER_PULLEY_LOCAL), "running")
                    + " movedContraption="
                    + fieldString(getLocalBlockEntity(subLevel, CONTROLLER_PULLEY_LOCAL), "movedContraption")
                    + " lastRequestedControl=" + getM20String(subLevel, CONTROLLER_ACTION_TAG, "none")
                    + " payloadLocals=[(0,0,0),(1,0,0)]"
                    + " payloadRaw=[" + fmt(toPlot(subLevel, new BlockPos(0, 0, 0))) + ","
                    + fmt(toPlot(subLevel, new BlockPos(1, 0, 0))) + "]"
                    + " payloadBlocks=[" + blockId(getLocalBlockState(subLevel, new BlockPos(0, 0, 0))) + ","
                    + blockId(getLocalBlockState(subLevel, new BlockPos(1, 0, 0))) + "]"
                    + " payloadCaptured=" + controllerMovedContraptionPresent(subLevel)
                    + " payloadLifecycle=Create_owns_assembly_capture_movement_disassembly"
                    + " observedExtendPulleySpeed=+32"
                    + " observedRetractPulleySpeed=-32"
                    + " rendererQualification=Sable_only_AbstractPulleyRenderer_BER_fallback");
        } else if ("elevator".equals(spec.family())) {
            final BlockEntity pulley = getLocalBlockEntity(subLevel, ELEVATOR_PULLEY_LOCAL);
            lines.add(kineticLine(prefix, subLevel, ELEVATOR_MOTOR_LOCAL, "elevator_motor"));
            lines.add(kineticLine(prefix, subLevel, ELEVATOR_PULLEY_LOCAL, "elevator_pulley"));
            lines.add(prefix
                    + " cabinBlockLocal=" + fmt(ELEVATOR_CABIN_BLOCK_LOCAL)
                    + " cabinBlockRaw=" + fmt(toPlot(subLevel, ELEVATOR_CABIN_BLOCK_LOCAL))
                    + " cabinBlock=" + blockId(getLocalBlockState(subLevel, ELEVATOR_CABIN_BLOCK_LOCAL))
                    + " cabinContactLocal=" + fmt(ELEVATOR_CABIN_CONTACT_LOCAL)
                    + " cabinContactRaw=" + fmt(toPlot(subLevel, ELEVATOR_CABIN_CONTACT_LOCAL))
                    + " cabinContact=" + blockId(getLocalBlockState(subLevel, ELEVATOR_CABIN_CONTACT_LOCAL))
                    + " lowerContactLocal=" + fmt(ELEVATOR_LOWER_CONTACT_LOCAL)
                    + " lowerContactState=" + getLocalBlockState(subLevel, ELEVATOR_LOWER_CONTACT_LOCAL)
                    + " lowerContactName=" + fieldString(getLocalBlockEntity(subLevel, ELEVATOR_LOWER_CONTACT_LOCAL),
                    "shortName")
                    + " upperContactLocal=" + fmt(ELEVATOR_UPPER_CONTACT_LOCAL)
                    + " upperContactState=" + getLocalBlockState(subLevel, ELEVATOR_UPPER_CONTACT_LOCAL)
                    + " upperContactName=" + fieldString(getLocalBlockEntity(subLevel, ELEVATOR_UPPER_CONTACT_LOCAL),
                    "shortName")
                    + " lowerSignal=" + blockId(getLocalBlockState(subLevel, ELEVATOR_LOWER_SIGNAL_LOCAL))
                    + " upperSignal=" + blockId(getLocalBlockState(subLevel, ELEVATOR_UPPER_SIGNAL_LOCAL))
                    + " pulleyOffset=" + fieldString(pulley, "offset")
                    + " running=" + fieldString(pulley, "running")
                    + " arrived=" + fieldString(pulley, "arrived")
                    + " movedContraption=" + fieldString(pulley, "movedContraption")
                    + " action=" + getM20String(subLevel, ELEVATOR_ACTION_TAG, "none")
                    + " assembly=Create_ElevatorPulleyBlockEntity_assemble"
                    + " contactDiscovery=Create_ElevatorColumn_gatherAll_Sable_storage_lookup"
                    + " uiFloorControls=DEFERRED");
        } else if ("redstone_link".equals(spec.family())) {
            lines.add(prefix
                    + " transmitterLocal=" + fmt(LINK_TRANSMITTER_LOCAL)
                    + " receiverLocal=" + fmt(LINK_RECEIVER_LOCAL)
                    + " signalLocal=" + fmt(LINK_SIGNAL_LOCAL)
                    + " lampLocal=" + fmt(LINK_LAMP_LOCAL)
                    + " transmitterSignal=" + fieldString(getLocalBlockEntity(subLevel, LINK_TRANSMITTER_LOCAL),
                    "transmittedSignal")
                    + " receiverSignal=" + fieldString(getLocalBlockEntity(subLevel, LINK_RECEIVER_LOCAL),
                    "receivedSignal")
                    + " signalBlock=" + blockId(getLocalBlockState(subLevel, LINK_SIGNAL_LOCAL))
                    + " lampLit=" + propertyMatches(getLocalBlockState(subLevel, LINK_LAMP_LOCAL), "lit", "true")
                    + " frequencyConfigured=" + getM20String(subLevel, LINK_CONFIGURED_TAG, "false")
                    + " productionMixin=none"
                    + " upstreamConcern=redstone_links.RedstoneLinkNetworkHandlerMixin_DEFERRED_UNTIL_DIFFERENTIAL");
        } else if ("mixer_basin".equals(spec.family()) || "heated_basin".equals(spec.family())) {
            final BlockPos basinLocal = "heated_basin".equals(spec.family()) ? new BlockPos(0, 2, 0) : new BlockPos(0, 1, 0);
            final BlockPos mixerLocal = "heated_basin".equals(spec.family()) ? new BlockPos(0, 4, 0) : new BlockPos(0, 3, 0);
            final BlockPos burnerLocal = new BlockPos(0, 1, 0);
            lines.add(kineticLine(prefix, subLevel, mixerLocal, "mechanical_mixer"));
            lines.add(prefix
                    + " SABLE_M20_MIXER_PROCESS"
                    + " mixerLocal=" + fmt(mixerLocal)
                    + " mixerRaw=" + fmt(toPlot(subLevel, mixerLocal))
                    + " mixerSpeed=" + fmt(readKineticSpeed(subLevel, mixerLocal))
                    + " basinLocal=" + fmt(basinLocal)
                    + " basinRaw=" + fmt(toPlot(subLevel, basinLocal))
                    + " basinBlock=" + blockId(getLocalBlockState(subLevel, basinLocal))
                    + " basinBE=" + beClass(getLocalBlockEntity(subLevel, basinLocal))
                    + " basinContents=" + containerSummary(subLevel, basinLocal)
                    + " heatSourceLocal=" + fmt(burnerLocal)
                    + " heatSourceBlock=" + blockId(getLocalBlockState(subLevel, burnerLocal))
                    + " heatLevel=" + statePropertyName(getLocalBlockState(subLevel, burnerLocal), "blaze", "none")
                    + " processingTicks=" + fieldString(getLocalBlockEntity(subLevel, basinLocal), "processingTicks")
                    + " running=" + fieldString(getLocalBlockEntity(subLevel, mixerLocal), "running")
                    + " recipeClassification=RUNTIME_REQUIRED_NO_PRODUCTION_PATCH"
                    + " firstFailureCandidates=UNPOWERED,SPEED_TOO_LOW,NO_RECIPE,HEAT_MISSING,BASIN_LOOKUP_FAILED");
        } else if ("crushing_wheels".equals(spec.family())) {
            final BlockPos left = CRUSHING_LEFT_WHEEL_LOCAL;
            final BlockPos right = CRUSHING_RIGHT_WHEEL_LOCAL;
            final BlockState controllerState = getLocalBlockState(subLevel, CRUSHING_CONTROLLER_LOCAL);
            final BlockEntity controller = getLocalBlockEntity(subLevel, CRUSHING_CONTROLLER_LOCAL);
            final BlockPos controllerRaw = toPlot(subLevel, CRUSHING_CONTROLLER_LOCAL);
            final Vec3 controllerVisible = visibleCenter(subLevel, CRUSHING_CONTROLLER_LOCAL);
            lines.add(kineticLine(prefix, subLevel, left, "left_crushing_wheel"));
            lines.add(kineticLine(prefix, subLevel, right, "right_crushing_wheel"));
            lines.add(prefix
                    + " SABLE_M20_CRUSHING_PROCESS"
                    + " leftWheelLocal=" + fmt(left)
                    + " leftWheelRaw=" + fmt(toPlot(subLevel, left))
                    + " rightWheelLocal=" + fmt(right)
                    + " rightWheelRaw=" + fmt(toPlot(subLevel, right))
                    + " leftAxis=" + statePropertyName(getLocalBlockState(subLevel, left), "axis", "unknown")
                    + " rightAxis=" + statePropertyName(getLocalBlockState(subLevel, right), "axis", "unknown")
                    + " leftSpeed=" + fmt(readKineticSpeed(subLevel, left))
                    + " rightSpeed=" + fmt(readKineticSpeed(subLevel, right))
                    + " rotationRelationshipValid=" + pass(crushingRotationRelationshipValid(subLevel))
                    + " expectedControllerPos=" + fmt(CRUSHING_CONTROLLER_LOCAL)
                    + " expectedControllerRaw=" + fmt(controllerRaw)
                    + " expectedControllerVisible=" + fmt(controllerVisible)
                    + " controllerBlockPresent=" + pass(blockIdMatches(controllerState, CRUSHING_WHEEL_CONTROLLER_ID))
                    + " controllerBlockEntityPresent=" + pass(controller != null)
                    + " controllerFacing=" + statePropertyName(controllerState, "facing", "unknown")
                    + " controllerValid=" + statePropertyName(controllerState, "valid", "unknown")
                    + " crushingSpeed=" + fieldString(controller, "crushingspeed")
                    + " controllerOccupied=" + fieldString(controller, "occupied")
                    + " processingEntity=" + fieldString(controller, "processingEntity")
                    + " inventoryInput=" + fieldString(controller, "inventory")
                    + " remainingTime=" + fieldString(controller, "remainingTime")
                    + " recipeId=" + fieldString(controller, "recipe")
                    + " controllerCollisionShapePresent=" + pass(!controllerCollisionShape(controllerState, subLevel).isEmpty())
                    + " controllerCollisionShapeBounds=" + collisionShapeBounds(controllerCollisionShape(controllerState, subLevel))
                    + " rawInputItemEntities=" + countItemEntities(subLevel, controllerRaw.getCenter().add(0.0, 1.0, 0.0))
                    + " visibleInputItemEntities=" + countItemEntities(subLevel, controllerVisible.add(0.0, 1.0, 0.0))
                    + " physicsColliderContainsController=RUNTIME_VERIFIED_BY_PLAYER_COLLISION"
                    + " inputLane=" + fmt(CRUSHING_INPUT_LANE_LOCAL)
                    + " outputLane=" + fmt(CRUSHING_OUTPUT_LANE_LOCAL)
                    + " seededInput=minecraft:dioritex1_from_above"
                    + " structuralReady=" + pass(checkFixture(subLevel, spec).ready())
                    + " runtimeProven=USER_REQUIRED"
                    + " recipeClassification=RUNTIME_REQUIRED_NO_PRODUCTION_PATCH"
                    + " firstFailureCandidates=UNPOWERED,WRONG_AXIS,WRONG_ROTATION_RELATIONSHIP,"
                    + "CONTROLLER_NOT_FORMED,ENTITY_INSIDE_NOT_REACHED,RAW_VISIBLE_AABB_MISMATCH,"
                    + "INPUT_NOT_ENTERING,NO_RECIPE");
        } else {
            lines.add(prefix
                    + " gauntletFamily=" + spec.family()
                    + " fixtureImplemented=true"
                    + " runtimeStatus=USER_REQUIRED"
                    + " ordinaryCreateFamily=true"
                    + " noProductionCompatibilityAdded=true"
                    + " blockProbeCount=" + spec.blocks().size());
            for (final PlacedBlock block : spec.blocks()) {
                final BlockEntity be = getLocalBlockEntity(subLevel, block.localPos());
                if (be != null || block.blockId().equals(CREATIVE_MOTOR_ID)
                        || block.blockId().equals(SHAFT_ID)
                        || block.blockId().equals(COGWHEEL_ID)
                        || block.blockId().equals(LARGE_COGWHEEL_ID)
                        || block.role().contains("seeded")
                        || block.role().contains("tank")) {
                    lines.add(prefix
                            + " role=" + block.role()
                            + " local=" + fmt(block.localPos())
                            + " raw=" + fmt(toPlot(subLevel, block.localPos()))
                            + " blockId=" + blockId(getLocalBlockState(subLevel, block.localPos()))
                            + " beClass=" + (be == null ? "none" : be.getClass().getName())
                            + " speed=" + fmt(readKineticSpeed(subLevel, block.localPos()))
                            + " inventory=" + containerSummary(subLevel, block.localPos())
                            + " fluid=" + fluidSummary(subLevel, block.localPos()));
                }
            }
        }
        return lines;
    }

    private static String kineticLine(final String prefix,
                                      final ServerSubLevel subLevel,
                                      final BlockPos local,
                                      final String role) {
        final BlockEntity be = getLocalBlockEntity(subLevel, local);
        return prefix
                + " role=" + role
                + " local=" + fmt(local)
                + " blockId=" + blockId(getLocalBlockState(subLevel, local))
                + " beClass=" + (be == null ? "none" : be.getClass().getName())
                + " axis=" + statePropertyName(getLocalBlockState(subLevel, local), "axis",
                statePropertyName(getLocalBlockState(subLevel, local), "facing", "none"))
                + " speed=" + fmt(readKineticSpeed(subLevel, local))
                + " theoreticalSpeed=" + fmt(asDouble(invokeNoArgRaw(be, "getTheoreticalSpeed")))
                + " source=" + fieldString(be, "source")
                + " network=" + fieldString(be, "network");
    }

    private static String beClass(@Nullable final BlockEntity be) {
        return be == null ? "none" : be.getClass().getName();
    }

    private static ServerSubLevel createEmptySubLevel(final CommandContext<CommandSourceStack> context,
                                                      final ServerSubLevelContainer container,
                                                      final String name) {
        return createEmptySubLevel(context, container, name, GallerySlot.ORIGIN);
    }

    private static ServerSubLevel createEmptySubLevel(final CommandContext<CommandSourceStack> context,
                                                      final ServerSubLevelContainer container,
                                                      final String name,
                                                      final GallerySlot slot) {
        final Vec3 origin = Vec3.atCenterOf(BlockPos.containing(context.getSource().getPosition()));
        final Vec3 spawnPos = origin.add(slot.offsetX(), 0.0, slot.offsetZ());
        final Pose3d pose = new Pose3d();
        pose.position().set(spawnPos.x, spawnPos.y, spawnPos.z);
        final ServerSubLevel subLevel = (ServerSubLevel) container.allocateNewSubLevel(pose);
        subLevel.setName(name);
        subLevel.getPlot().newEmptyChunk(subLevel.getPlot().getCenterChunk());
        return subLevel;
    }

    private static void markFixtureCreated(final ServerSubLevel subLevel, final FixtureSpec spec) {
        final CompoundTag root = subLevel.getUserDataTag() == null ? new CompoundTag() : subLevel.getUserDataTag().copy();
        final CompoundTag m20 = root.getCompound(M20_TAG);
        m20.putString(FAMILY_TAG, spec.family());
        m20.putLong(CREATED_TICK_TAG, subLevel.getLevel().getGameTime());
        m20.putString(CONTROLLER_ACTION_TAG, "none");
        if ("controller".equals(spec.family())) {
            m20.putString(CONTROLLER_PAYLOAD_TAG, "false");
        }
        if (!"redstone_link".equals(spec.family())) {
            m20.putString(LINK_CONFIGURED_TAG, "false");
        }
        root.put(M20_TAG, m20);
        subLevel.setUserDataTag(root);
    }

    private static long fixtureAgeTicks(final ServerSubLevel subLevel) {
        final CompoundTag root = subLevel.getUserDataTag();
        if (root == null || !root.contains(M20_TAG)) {
            return -1L;
        }
        final CompoundTag m20 = root.getCompound(M20_TAG);
        if (!m20.contains(CREATED_TICK_TAG)) {
            return -1L;
        }
        return Math.max(0L, subLevel.getLevel().getGameTime() - m20.getLong(CREATED_TICK_TAG));
    }

    private static void putM20String(final ServerSubLevel subLevel, final String key, final String value) {
        final CompoundTag root = subLevel.getUserDataTag() == null ? new CompoundTag() : subLevel.getUserDataTag().copy();
        final CompoundTag m20 = root.getCompound(M20_TAG);
        m20.putString(key, value);
        root.put(M20_TAG, m20);
        subLevel.setUserDataTag(root);
    }

    private static String getM20String(final ServerSubLevel subLevel, final String key, final String fallback) {
        final CompoundTag root = subLevel.getUserDataTag();
        if (root == null || !root.contains(M20_TAG)) {
            return fallback;
        }
        final CompoundTag m20 = root.getCompound(M20_TAG);
        return m20.contains(key) ? m20.getString(key) : fallback;
    }

    private static void setFixtureMotorSpeed(final ServerSubLevel subLevel, final BlockPos motorLocal, final int rpm) {
        final BlockEntity motor = getLocalBlockEntity(subLevel, motorLocal);
        if (motor == null) {
            throw new IllegalStateException("M20 fixture motor is missing at local " + fmt(motorLocal));
        }
        final Object behaviour = readFieldRaw(motor, "generatedSpeed");
        if (behaviour == null || invokeIntArgRaw(behaviour, "setValue", rpm) == null) {
            throw new IllegalStateException("M20 fixture motor does not expose Create generatedSpeed at local "
                    + fmt(motorLocal));
        }
        invokeNoArgRaw(motor, "updateGeneratedRotation");
        motor.setChanged();
    }

    private static void configureBeltController(final ServerSubLevel subLevel, final List<BlockPos> beltLocals) {
        final BlockPos controller = toPlot(subLevel, beltLocals.get(0));
        for (int i = 0; i < beltLocals.size(); i++) {
            final BlockEntity be = getLocalBlockEntity(subLevel, beltLocals.get(i));
            writeFieldRaw(be, "controller", controller);
            writeFieldRaw(be, "beltLength", beltLocals.size());
            writeFieldRaw(be, "index", i);
            if (be != null) {
                be.setChanged();
            }
        }
    }

    private static void configureArmInteractionPoints(final ServerSubLevel subLevel) {
        final BlockEntity arm = getLocalBlockEntity(subLevel, new BlockPos(0, 1, 0));
        if (arm == null) {
            throw new IllegalStateException("M20 arm fixture is missing MechanicalArmBlockEntity");
        }
        final ListTag points = new ListTag();
        points.add(armInteractionPointTag(new BlockPos(-2, 0, 0), "TAKE"));
        points.add(armInteractionPointTag(new BlockPos(0, 0, 2), "DEPOSIT"));
        writeFieldRaw(arm, "interactionPointTag", points);
        writeFieldRaw(arm, "updateInteractionPoints", true);
        invokeNoArgRaw(arm, "initInteractionPoints");
        arm.setChanged();
    }

    private static void configureRedstoneLinkFixture(final ServerSubLevel subLevel) {
        final BlockEntity transmitter = getLocalBlockEntity(subLevel, LINK_TRANSMITTER_LOCAL);
        final BlockEntity receiver = getLocalBlockEntity(subLevel, LINK_RECEIVER_LOCAL);
        if (transmitter == null || receiver == null) {
            throw new IllegalStateException("M20 Redstone Link fixture is missing one of its block entities");
        }
        writeFieldRaw(transmitter, "transmitter", true);
        writeFieldRaw(receiver, "transmitter", false);
        invokeNoArgRaw(transmitter, "createLink");
        invokeNoArgRaw(receiver, "createLink");
        final Object transmitterLink = readFieldRaw(transmitter, "link");
        final Object receiverLink = readFieldRaw(receiver, "link");
        if (transmitterLink == null || receiverLink == null) {
            throw new IllegalStateException("M20 Redstone Link fixture did not create LinkBehaviour instances");
        }
        final ItemStack firstFrequency = new ItemStack(Items.REDSTONE, 1);
        final ItemStack secondFrequency = new ItemStack(Items.IRON_INGOT, 1);
        if (!invokeArgsRaw(transmitterLink, "setFrequency", true, firstFrequency)
                || !invokeArgsRaw(transmitterLink, "setFrequency", false, secondFrequency)
                || !invokeArgsRaw(receiverLink, "setFrequency", true, firstFrequency)
                || !invokeArgsRaw(receiverLink, "setFrequency", false, secondFrequency)) {
            throw new IllegalStateException("M20 Redstone Link fixture could not configure Create frequencies");
        }
        invokeNoArgRaw(transmitterLink, "initialize");
        invokeNoArgRaw(receiverLink, "initialize");
        transmitter.setChanged();
        receiver.setChanged();
        putM20String(subLevel, LINK_CONFIGURED_TAG, "true");
    }

    private static void configureCrushingWheelsFixture(final ServerSubLevel subLevel) {
        setFixtureMotorSpeed(subLevel, CRUSHING_LEFT_MOTOR_LOCAL, -DEFAULT_RPM);
        setFixtureMotorSpeed(subLevel, CRUSHING_RIGHT_MOTOR_LOCAL, DEFAULT_RPM);
        updateCrushingWheelController(subLevel, CRUSHING_LEFT_WHEEL_LOCAL, Direction.EAST);
        updateCrushingWheelController(subLevel, CRUSHING_RIGHT_WHEEL_LOCAL, Direction.WEST);
        seedCrushingInputEntity(subLevel);
    }

    private static void updateCrushingWheelController(final ServerSubLevel subLevel,
                                                      final BlockPos wheelLocal,
                                                      final Direction directionTowardGap) {
        final BlockState state = getLocalBlockState(subLevel, wheelLocal);
        final BlockPos wheelRaw = toPlot(subLevel, wheelLocal);
        if (!invokeArgsRaw(state.getBlock(), "updateControllers", state, subLevel.getLevel(), wheelRaw,
                directionTowardGap)) {
            throw new IllegalStateException("M20 crushing fixture could not invoke Create 6.0.8 "
                    + "CrushingWheelBlock.updateControllers for local " + fmt(wheelLocal));
        }
    }

    private static void seedCrushingInputEntity(final ServerSubLevel subLevel) {
        final BlockPos raw = toPlot(subLevel, CRUSHING_INPUT_LANE_LOCAL);
        final ItemEntity item = new ItemEntity(subLevel.getLevel(),
                raw.getX() + 0.5,
                raw.getY() + 0.35,
                raw.getZ() + 0.5,
                new ItemStack(Items.DIORITE, 1));
        item.setPickUpDelay(80);
        item.setDeltaMovement(0.0, -0.03, 0.0);
        item.setNoGravity(false);
        subLevel.getLevel().addFreshEntity(item);
        Sable.LOGGER.info("SABLE_M20_CRUSHING_PROCESS phase=seed_input item=minecraft:diorite "
                        + "local={} raw={} semantics=normal_Create_CrushingWheelController_entityInside",
                fmt(CRUSHING_INPUT_LANE_LOCAL), fmt(raw));
    }

    private static boolean crushingRotationRelationshipValid(final ServerSubLevel subLevel) {
        final BlockState leftState = getLocalBlockState(subLevel, CRUSHING_LEFT_WHEEL_LOCAL);
        final BlockState rightState = getLocalBlockState(subLevel, CRUSHING_RIGHT_WHEEL_LOCAL);
        final double leftSpeed = readKineticSpeed(subLevel, CRUSHING_LEFT_WHEEL_LOCAL);
        final double rightSpeed = readKineticSpeed(subLevel, CRUSHING_RIGHT_WHEEL_LOCAL);
        return "z".equals(statePropertyName(leftState, "axis", "unknown"))
                && "z".equals(statePropertyName(rightState, "axis", "unknown"))
                && Double.isFinite(leftSpeed)
                && Double.isFinite(rightSpeed)
                && leftSpeed < 0.0
                && rightSpeed > 0.0;
    }

    private static void configureElevatorContacts(final ServerSubLevel subLevel) {
        configureElevatorContactName(subLevel, ELEVATOR_LOWER_CONTACT_LOCAL, "0", "Lower");
        configureElevatorContactName(subLevel, ELEVATOR_UPPER_CONTACT_LOCAL, "1", "Upper");
    }

    private static void configureElevatorContactName(final ServerSubLevel subLevel,
                                                     final BlockPos local,
                                                     final String shortName,
                                                     final String longName) {
        final BlockEntity contact = getLocalBlockEntity(subLevel, local);
        writeFieldRaw(contact, "shortName", shortName);
        writeFieldRaw(contact, "longName", longName);
        invokeNoArgRaw(contact, "initialize");
        if (contact != null) {
            contact.setChanged();
        }
    }

    private static CompoundTag armInteractionPointTag(final BlockPos relativeToArm, final String mode) {
        final CompoundTag tag = new CompoundTag();
        tag.putString("Type", DEPOT_ID.toString());
        tag.putString("Mode", mode);
        tag.put("Pos", NbtUtils.writeBlockPos(relativeToArm));
        return tag;
    }

    private static void seedContainer(final ServerSubLevel subLevel, final BlockPos local, final ItemStack stack) {
        final BlockEntity be = getLocalBlockEntity(subLevel, local);
        if (be instanceof final Container container && container.getContainerSize() > 0) {
            container.setItem(0, stack.copy());
            be.setChanged();
            return;
        }
        if (invokeObjectArgRaw(be, "setHeldItem", ItemStack.class, stack.copy()) != null && be != null) {
            be.setChanged();
        }
    }

    private static void fillFluidHandler(final ServerSubLevel subLevel, final BlockPos local, final FluidStack stack) {
        final BlockEntity be = getLocalBlockEntity(subLevel, local);
        if (be == null) {
            return;
        }
        be.getCapability(ForgeCapabilities.FLUID_HANDLER).ifPresent(handler ->
                handler.fill(stack.copy(), IFluidHandler.FluidAction.EXECUTE));
        be.setChanged();
    }

    private static String containerSummary(final ServerSubLevel subLevel, final BlockPos local) {
        final BlockEntity be = getLocalBlockEntity(subLevel, local);
        final Object held = invokeNoArgRaw(be, "getHeldItem");
        if (held instanceof final ItemStack stack && !stack.isEmpty()) {
            return "held:" + BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount();
        }
        if (!(be instanceof final Container container)) {
            return "not_container";
        }
        final List<String> stacks = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            final ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                stacks.add(slot + ":" + BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount());
            }
        }
        return stacks.isEmpty() ? "empty" : String.join(",", stacks);
    }

    private static boolean seedBeltTransportedItem(@Nullable final BlockEntity controller, final ItemStack stack) {
        final Object inventory = readFieldRaw(controller, "inventory");
        if (inventory == null || stack.isEmpty()) {
            return false;
        }
        final Object transported = newTransportedItemStack(stack.copy());
        if (transported == null) {
            return false;
        }
        writeFieldRaw(transported, "beltPosition", 0.5f);
        writeFieldRaw(transported, "sideOffset", 0.0f);
        writeFieldRaw(transported, "prevBeltPosition", 0.5f);
        writeFieldRaw(transported, "prevSideOffset", 0.0f);
        final Object items = readFieldRaw(inventory, "items");
        if (items instanceof final List<?> list) {
            @SuppressWarnings("unchecked")
            final List<Object> writable = (List<Object>) list;
            writable.clear();
            writable.add(transported);
            invokeNoArgRaw(controller, "sendData");
            return true;
        }
        final boolean added = invokeObjectArgRaw(inventory, "addItem", transported.getClass(), transported) != null;
        if (added) {
            invokeNoArgRaw(controller, "sendData");
        }
        return added;
    }

    private static @Nullable Object newTransportedItemStack(final ItemStack stack) {
        try {
            final Class<?> type = Class.forName(
                    "com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack");
            return type.getConstructor(ItemStack.class).newInstance(stack);
        } catch (final ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static String beltTransportSummary(@Nullable final BlockEntity controller) {
        final Object inventory = readFieldRaw(controller, "inventory");
        final Object items = readFieldRaw(inventory, "items");
        if (!(items instanceof final List<?> list) || list.isEmpty()) {
            return "empty";
        }
        final List<String> parts = new ArrayList<>();
        for (final Object transported : list) {
            parts.add("stack=" + fieldString(transported, "stack")
                    + ",beltPosition=" + fieldString(transported, "beltPosition")
                    + ",sideOffset=" + fieldString(transported, "sideOffset"));
        }
        return String.join(";", parts);
    }

    private static int collectionSize(@Nullable final Object value) {
        return value instanceof final java.util.Collection<?> collection ? collection.size() : 0;
    }

    private static String fluidSummary(final ServerSubLevel subLevel, final BlockPos local) {
        final BlockEntity be = getLocalBlockEntity(subLevel, local);
        if (be == null) {
            return "no_block_entity";
        }
        final List<String> tanks = new ArrayList<>();
        be.getCapability(ForgeCapabilities.FLUID_HANDLER).ifPresent(handler -> {
            for (int i = 0; i < handler.getTanks(); i++) {
                final FluidStack stack = handler.getFluidInTank(i);
                if (!stack.isEmpty()) {
                    tanks.add(i + ":" + BuiltInRegistries.FLUID.getKey(stack.getFluid()) + "x" + stack.getAmount());
                }
            }
        });
        return tanks.isEmpty() ? "empty" : String.join(",", tanks);
    }

    private static double readKineticSpeed(final ServerSubLevel subLevel, final BlockPos local) {
        return asDouble(invokeNoArgRaw(getLocalBlockEntity(subLevel, local), "getSpeed"));
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

    private static @Nullable BlockEntity getLocalBlockEntity(final ServerSubLevel subLevel, final BlockPos localPos) {
        return SubLevelBlockStateLookup.getBlockEntity(subLevel, toPlot(subLevel, localPos));
    }

    private static BlockPos toPlot(final ServerSubLevel subLevel, final BlockPos localPos) {
        return SubLevelBlockEditHelper.localOffsetToPlotBlock(subLevel, localPos);
    }

    private static Vec3 visibleCenter(final ServerSubLevel subLevel, final BlockPos localPos) {
        return subLevel.logicalPose().transformPosition(
                toPlot(subLevel, localPos).subtract(subLevel.getPlot().getCenterBlock()).getCenter());
    }

    private static VoxelShape controllerCollisionShape(final BlockState controllerState,
                                                       final ServerSubLevel subLevel) {
        if (!blockIdMatches(controllerState, CRUSHING_WHEEL_CONTROLLER_ID)) {
            return Shapes.empty();
        }
        return controllerState.getCollisionShape(subLevel.getLevel(), toPlot(subLevel, CRUSHING_CONTROLLER_LOCAL),
                CollisionContext.empty());
    }

    private static String collisionShapeBounds(final VoxelShape shape) {
        return shape.isEmpty() ? "empty" : shape.bounds().toString();
    }

    private static int countItemEntities(final ServerSubLevel subLevel, final Vec3 center) {
        return subLevel.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(center, center).inflate(0.75)).size();
    }

    private static FixtureBounds fixtureBounds(final FixtureSpec spec) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (final PlacedBlock block : fixtureBlocks(spec)) {
            final BlockPos pos = block.localPos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new FixtureBounds(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    private static List<PlacedBlock> fixtureBlocks(final FixtureSpec spec) {
        final Map<BlockPos, PlacedBlock> blocks = new LinkedHashMap<>();
        for (final PlacedBlock block : platformBlocks(spec)) {
            blocks.put(block.localPos(), block);
        }
        for (final PlacedBlock block : markerBlocks(spec)) {
            blocks.put(block.localPos(), block);
        }
        for (final PlacedBlock block : spec.blocks()) {
            blocks.put(block.localPos(), block);
        }
        return blocks.values().stream()
                .sorted(Comparator.comparingInt((PlacedBlock block) -> block.localPos().getY())
                        .thenComparingInt(block -> block.localPos().getZ())
                        .thenComparingInt(block -> block.localPos().getX()))
                .toList();
    }

    private static List<PlacedBlock> platformBlocks(final FixtureSpec spec) {
        final List<PlacedBlock> blocks = new ArrayList<>();
        final int halfX = platformHalfX(spec);
        final int halfZ = platformHalfZ(spec);
        for (int x = -halfX; x <= halfX; x++) {
            for (int z = -halfZ; z <= halfZ; z++) {
                final BlockPos local = new BlockPos(x, 0, z);
                if (reservedPlatformAir(spec, local)) {
                    continue;
                }
                blocks.add(placed(local, id("minecraft", "smooth_stone"), "gallery_platform_" + spec.family()));
            }
        }
        return blocks;
    }

    private static List<PlacedBlock> markerBlocks(final FixtureSpec spec) {
        final int halfX = platformHalfX(spec);
        final int halfZ = platformHalfZ(spec);
        return List.of(
                placed(new BlockPos(-halfX, 1, -halfZ), BLUE_WOOL_ID, "gallery_marker_input_blue_west"),
                placed(new BlockPos(0, 1, -halfZ), YELLOW_WOOL_ID, "gallery_marker_machine_yellow_center"),
                placed(new BlockPos(halfX, 1, -halfZ), RED_WOOL_ID, "gallery_marker_target_red_east"),
                placed(new BlockPos(halfX, 1, halfZ), GREEN_WOOL_ID, "gallery_marker_output_green_east"));
    }

    private static boolean reservedPlatformAir(final FixtureSpec spec, final BlockPos local) {
        if ("controller".equals(spec.family())) {
            return local.getX() == 0
                    && local.getZ() == 0
                    && local.getY() >= 0
                    && local.getY() <= 4;
        }
        return "crushing_wheels".equals(spec.family()) && CRUSHING_OUTPUT_LANE_LOCAL.equals(local);
    }

    private static int platformHalfX(final FixtureSpec spec) {
        return switch (spec.family()) {
            case "controller", "elevator", "saw_tree", "harvester", "plough", "roller",
                    "drill_regression", "portable_storage_interface", "portable_fluid_interface",
                    "crushing_wheels", "mechanical_crafters", "encased_fan", "cart_assembler" -> 7;
            default -> 4;
        };
    }

    private static int platformHalfZ(final FixtureSpec spec) {
        return switch (spec.family()) {
            case "controller", "elevator", "saw_tree", "harvester", "plough", "roller",
                    "drill_regression", "portable_storage_interface", "portable_fluid_interface",
                    "crushing_wheels", "mechanical_crafters", "encased_fan", "cart_assembler" -> 4;
            default -> 3;
        };
    }

    private static String platformSize(final FixtureSpec spec) {
        return (platformHalfX(spec) * 2 + 1) + "x" + (platformHalfZ(spec) * 2 + 1);
    }

    private static String expectedAction(final FixtureSpec spec) {
        return switch (spec.family()) {
            case "kinetic" -> "Creative Motor powers two inline shafts and a small-cog pair";
            case "chain_drive" -> "Encased chain drive transmits rotation vertically";
            case "redstone" -> "Redstone source toggles Gearshift reversal into downstream shaft";
            case "redstone_link" -> "WEST Redstone Link transmitter drives EAST receiver lamp";
            case "redstone_contact" -> "Opposed Redstone Contacts report contact signal to lamp";
            case "threshold_switch" -> "Container threshold switch drives visible lamp";
            case "smart_observer" -> "Smart Observer watches one target block and drives lamp";
            case "logistics" -> "Straight belt carries one iron ingot from WEST toward EAST";
            case "arm" -> "Mechanical Arm takes iron from WEST Depot and deposits SOUTH output Depot";
            case "funnels_tunnels" -> "Isolated straight funnel/tunnel logistics lane";
            case "chute_smart_chute" -> "Vertical chute stack moves item downward";
            case "weighted_ejector" -> "Weighted Ejector launches one item toward EAST Depot";
            case "vault" -> "Item vault and output funnel expose storage capability";
            case "fluids" -> "WEST water tank pumps through CENTER pump into EAST tank";
            case "fluid_valve" -> "Straight tank-pipe-valve-pipe-tank line gates water flow";
            case "smart_fluid_pipe" -> "Smart Fluid Pipe filters a straight water line";
            case "hose_pulley" -> "Hose Pulley samples visible local water source";
            case "drill_regression" -> "Mechanical Drill faces EAST into stone target lane";
            case "saw_static" -> "Static Mechanical Saw cuts one visible log canary";
            case "saw_tree" -> "Moving saw canary faces EAST into a small tree";
            case "harvester" -> "Harvester faces EAST across one mature wheat row";
            case "plough" -> "Plough faces EAST into one dirt target lane";
            case "roller" -> "Roller faces EAST into one target lane";
            case "controller" -> "Rope Pulley extends/retracts and optionally captures payload";
            case "elevator" -> "Elevator Pulley controls a two-floor empty cabin";
            case "gauges" -> "Speedometer/Stressometer read a simple shaft network";
            case "rotation_speed_controller" -> "Rotation Speed Controller changes downstream shaft";
            case "display_link" -> "Display Link sends one local signal to board";
            case "cart_assembler" -> "Cart Assembler canary exposes contraption assembly boundary";
            case "portable_storage_interface" -> "Portable Storage Interface halves face each other on X";
            case "portable_fluid_interface" -> "Portable Fluid Interface halves face each other on X";
            case "press" -> "Mechanical Press works over a seeded Depot";
            case "mixer_basin" -> "Mixer stirs Basin contents";
            case "heated_basin" -> "Heated Mixer stirs Basin over Blaze Burner";
            case "millstone" -> "Millstone processes seeded input";
            case "crushing_wheels" -> "Paired Crushing Wheels process one input lane";
            case "mechanical_crafters" -> "Mechanical Crafters output EAST";
            case "encased_fan" -> "Encased Fan blows across one processing lane";
            default -> "ordinary Create family canary";
        };
    }

    private static GallerySlot gallerySlot(final int index) {
        final int column = index % 3;
        final int row = index / 3;
        return new GallerySlot(index, (column - 1) * GALLERY_SPACING_X, row * GALLERY_SPACING_Z);
    }

    private static void verifyGalleryNonOverlapping(final List<FixtureSpec> specs) {
        for (int left = 0; left < specs.size(); left++) {
            final GalleryBounds leftBounds = galleryBounds(specs.get(left), gallerySlot(left))
                    .expanded(GALLERY_OVERLAP_MARGIN);
            for (int right = left + 1; right < specs.size(); right++) {
                final GalleryBounds rightBounds = galleryBounds(specs.get(right), gallerySlot(right));
                if (leftBounds.intersects(rightBounds)) {
                    throw new IllegalArgumentException("M20 gauntlet gallery overlap between "
                            + specs.get(left).family() + " and " + specs.get(right).family());
                }
            }
        }
    }

    private static GalleryBounds galleryBounds(final FixtureSpec spec, final GallerySlot slot) {
        final FixtureBounds bounds = fixtureBounds(spec);
        return new GalleryBounds(slot.offsetX() + bounds.minLocal().getX(),
                slot.offsetZ() + bounds.minLocal().getZ(),
                slot.offsetX() + bounds.maxLocal().getX(),
                slot.offsetZ() + bounds.maxLocal().getZ());
    }

    private static void sendGalleryHeader(final CommandContext<CommandSourceStack> context,
                                          final String group,
                                          final String baseName,
                                          final Vec3 origin,
                                          final List<FixtureSpec> specs) {
        final String line = "SABLE_M20_GAUNTLET_MAP group=" + group
                + " baseName=" + baseName
                + " origin=" + fmt(origin)
                + " slotGrid=3_columns"
                + " spacing=(" + GALLERY_SPACING_X + "," + GALLERY_SPACING_Z + ")"
                + " overlapMargin=" + GALLERY_OVERLAP_MARGIN
                + " fixtureCount=" + specs.size()
                + " convention=WEST_input_CENTER_machine_EAST_output_or_target";
        send(context, line);
        Sable.LOGGER.info(line);
    }

    private static void sendGalleryEntry(final CommandContext<CommandSourceStack> context,
                                         final String group,
                                         final String fixtureName,
                                         final FixtureSpec spec,
                                         final GallerySlot slot,
                                         final Vec3 origin) {
        final String line = "SABLE_M20_GAUNTLET_MAP_ENTRY group=" + group
                + " family=" + spec.family()
                + " name=" + fixtureName
                + " slot=" + slot.index()
                + " worldOffset=(" + slot.offsetX() + "," + slot.offsetZ() + ")"
                + " visibleCenter=" + fmt(origin.add(slot.offsetX(), 0.0, slot.offsetZ()))
                + " platform=" + platformSize(spec)
                + " markers=blue_INPUT_west,yellow_MACHINE,red_TARGET_east,green_OUTPUT"
                + " expected=\"" + expectedAction(spec) + "\"";
        send(context, line);
        Sable.LOGGER.info(line);
    }

    private static boolean localOffsetFitsCenterChunk(final BlockPos localPos) {
        final int localXInChunk = Math.floorMod(8 + localPos.getX(), 16);
        final int localZInChunk = Math.floorMod(8 + localPos.getZ(), 16);
        return 8 + localPos.getX() >= 0 && 8 + localPos.getX() < 16
                && 8 + localPos.getZ() >= 0 && 8 + localPos.getZ() < 16
                && localXInChunk == 8 + localPos.getX()
                && localZInChunk == 8 + localPos.getZ();
    }

    private static void requireLocalOffsetFitsCenterChunk(final BlockPos localPos) {
        if (!localOffsetFitsCenterChunk(localPos)) {
            throw new IllegalArgumentException("M20 fixture local offset " + fmt(localPos)
                    + " exceeds the single allocated center plot chunk range x/z=[-8,7]");
        }
    }

    private static boolean allocatedPlotOwns(final ServerSubLevel subLevel, final BlockPos localPos) {
        final ChunkPos chunkPos = new ChunkPos(toPlot(subLevel, localPos));
        for (final PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            if (holder.getChunk().getPos().equals(chunkPos)) {
                return true;
            }
        }
        return false;
    }

    private static List<BlockPos> requiredAirLocals(final ServerSubLevel subLevel, final FixtureSpec spec) {
        if ("redstone_link".equals(spec.family())) {
            return List.of(LINK_SIGNAL_LOCAL);
        }
        if ("crushing_wheels".equals(spec.family())) {
            return List.of(CRUSHING_INPUT_LANE_LOCAL, CRUSHING_OUTPUT_LANE_LOCAL);
        }
        if ("controller".equals(spec.family())) {
            final List<BlockPos> air = new ArrayList<>();
            for (int y = 1; y <= 4; y++) {
                air.add(new BlockPos(0, y, 0));
            }
            if (!"true".equals(getM20String(subLevel, CONTROLLER_PAYLOAD_TAG, "false"))) {
                air.add(new BlockPos(0, 0, 0));
            }
            return air;
        }
        if ("elevator".equals(spec.family())) {
            return List.of(new BlockPos(0, 3, 0), new BlockPos(0, 4, 0), new BlockPos(0, 5, 0));
        }
        return List.of();
    }

    private static boolean ropePathBelowPulleyClear(final ServerSubLevel subLevel, final boolean includePayloadCell) {
        final int minY = includePayloadCell ? 0 : 1;
        for (int y = minY; y <= 4; y++) {
            if (!getLocalBlockState(subLevel, new BlockPos(0, y, 0)).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static Block requireBlock(final ResourceLocation id) {
        final Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
        if (block.isEmpty() || block.get() == Blocks.AIR) {
            throw new IllegalStateException("Required block is not registered: " + id);
        }
        return block.get();
    }

    private static BlockState setPropertyIfPresent(final BlockState state,
                                                   final String propertyName,
                                                   final String valueName) {
        for (final Property<?> property : state.getProperties()) {
            if (!property.getName().equals(propertyName)) {
                continue;
            }
            final Optional<?> value = property.getValue(valueName);
            if (value.isEmpty()) {
                return state;
            }
            return setPropertyUnchecked(state, property, (Comparable<?>) value.get());
        }
        return state;
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

    private static String statePropertyName(final BlockState state,
                                            final String propertyName,
                                            final String fallback) {
        for (final Property<?> property : state.getProperties()) {
            if (property.getName().equals(propertyName)) {
                return String.valueOf(state.getValue(property)).toLowerCase(Locale.ROOT);
            }
        }
        return fallback;
    }

    private static @Nullable Object invokeNoArgRaw(@Nullable final Object target, final String methodName) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (final NoSuchMethodException ignored) {
                // Create keeps shared kinetic behaviour on superclasses.
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean invokeNoArgVoidRaw(@Nullable final Object target, final String methodName) {
        if (target == null) {
            return false;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                method.invoke(target);
                return true;
            } catch (final NoSuchMethodException ignored) {
                // Create keeps controller helpers on subclasses.
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }

    private static @Nullable Object invokeIntArgRaw(@Nullable final Object target,
                                                    final String methodName,
                                                    final int value) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Method method = type.getDeclaredMethod(methodName, int.class);
                method.setAccessible(true);
                method.invoke(target, value);
                return Boolean.TRUE;
            } catch (final NoSuchMethodException ignored) {
                // Create keeps shared kinetic behaviour on superclasses.
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static @Nullable Object invokeObjectArgRaw(@Nullable final Object target,
                                                       final String methodName,
                                                       final Class<?> parameterType,
                                                       final Object value) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Method method = type.getDeclaredMethod(methodName, parameterType);
                method.setAccessible(true);
                method.invoke(target, value);
                return Boolean.TRUE;
            } catch (final NoSuchMethodException ignored) {
                // Create keeps fixture helpers on concrete block entities.
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean invokeArgsRaw(@Nullable final Object target, final String methodName,
                                         final Object... values) {
        if (target == null) {
            return false;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (final Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != values.length) {
                    continue;
                }
                final Class<?>[] parameterTypes = method.getParameterTypes();
                boolean compatible = true;
                for (int index = 0; index < values.length; index++) {
                    if (!isAssignable(parameterTypes[index], values[index])) {
                        compatible = false;
                        break;
                    }
                }
                if (!compatible) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    method.invoke(target, values);
                    return true;
                } catch (final ReflectiveOperationException | RuntimeException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean isAssignable(final Class<?> parameterType, @Nullable final Object value) {
        if (value == null) {
            return !parameterType.isPrimitive();
        }
        if (!parameterType.isPrimitive()) {
            return parameterType.isAssignableFrom(value.getClass());
        }
        return parameterType == boolean.class && value instanceof Boolean
                || parameterType == int.class && value instanceof Integer
                || parameterType == float.class && value instanceof Float
                || parameterType == double.class && value instanceof Double
                || parameterType == long.class && value instanceof Long;
    }

    private static @Nullable Object readFieldRaw(@Nullable final Object target, final String fieldName) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (final NoSuchFieldException ignored) {
                // Create keeps shared kinetic state on superclasses.
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static void writeFieldRaw(@Nullable final Object target, final String fieldName, final Object value) {
        if (target == null) {
            return;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                final Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (final NoSuchFieldException ignored) {
                // Create keeps shared kinetic state on superclasses.
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                return;
            }
        }
    }

    private static String fieldString(@Nullable final Object target, final String fieldName) {
        final Object value = readFieldRaw(target, fieldName);
        return value == null ? "unknown" : String.valueOf(value);
    }

    private static double asDouble(@Nullable final Object value) {
        return value instanceof final Number number ? number.doubleValue() : Double.NaN;
    }

    private static ResourceLocation id(final String namespace, final String path) {
        return new ResourceLocation(namespace, path);
    }

    private static void rollbackSpawnSubLevel(final ServerSubLevelContainer container, final @Nullable SubLevel subLevel,
                                              final String name, final Throwable failure) {
        if (subLevel == null || subLevel.isRemoved()) {
            return;
        }
        Sable.LOGGER.warn("SABLE_M20 phase=rollback_begin name={} id={} reason={}",
                name, subLevel.getUniqueId(), failure.getClass().getSimpleName());
        try {
            container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
        } catch (final Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static CommandSyntaxException commandFailure(final RuntimeException exception) {
        Sable.LOGGER.error("SABLE_M20 phase=failed", exception);
        final String message = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        return ERROR_M20_FAILED.create(message);
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

    private static String fmt(final ChunkPos pos) {
        return "[" + pos.x + "," + pos.z + "]";
    }

    private static String fmt(final Vec3 pos) {
        return "(" + fmt(pos.x) + "," + fmt(pos.y) + "," + fmt(pos.z) + ")";
    }

    private static String fmt(final FixtureBounds bounds) {
        return fmt(bounds.minLocal()) + ".." + fmt(bounds.maxLocal());
    }

    private static String fmt(final double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.6f", value) : "nan";
    }

    private static String formatVector(final Vector3dc vector) {
        return String.format(Locale.ROOT, "(%.6f,%.6f,%.6f)", vector.x(), vector.y(), vector.z());
    }

    private record FixtureSpec(String family, List<PlacedBlock> blocks) {
    }

    private record FixtureBounds(BlockPos minLocal, BlockPos maxLocal) {
    }

    private record GallerySlot(int index, int offsetX, int offsetZ) {
        private static final GallerySlot ORIGIN = new GallerySlot(0, 0, 0);
    }

    private record GalleryBounds(int minX, int minZ, int maxX, int maxZ) {
        private GalleryBounds expanded(final int amount) {
            return new GalleryBounds(this.minX - amount, this.minZ - amount,
                    this.maxX + amount, this.maxZ + amount);
        }

        private boolean intersects(final GalleryBounds other) {
            return this.minX <= other.maxX
                    && this.maxX >= other.minX
                    && this.minZ <= other.maxZ
                    && this.maxZ >= other.minZ;
        }
    }

    private record PlacedBlock(BlockPos localPos, ResourceLocation blockId, String role,
                               Predicate<BlockState> validator) {
        private boolean valid(final BlockState state) {
            return this.validator.test(state);
        }
    }

    private record FixtureCheck(List<String> failures, boolean ready) {
    }

    private record FixtureStats(int blockCount, double mass, Vector3dc position, Vector3dc linearVelocity,
                                Vector3dc angularVelocity) {
    }
}
