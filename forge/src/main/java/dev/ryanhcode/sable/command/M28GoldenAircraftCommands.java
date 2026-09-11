package dev.ryanhcode.sable.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.content.propulsion.WoodenPropellerBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlockEntity;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Read-only diagnostics for a manually built M28 aircraft. */
public final class M28GoldenAircraftCommands {

    private M28GoldenAircraftCommands() {
    }

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> sableBuilder,
                                final CommandBuildContext buildContext) {
        sableBuilder.then(Commands.literal("m28")
                .then(Commands.literal("status").executes(M28GoldenAircraftCommands::status))
                .then(Commands.literal("inspect").executes(M28GoldenAircraftCommands::inspect)));
    }

    private static int status(final CommandContext<CommandSourceStack> context) {
        send(context.getSource(), "SABLE_M28_STATUS implementationRevision=" + Aeronautics.IMPLEMENTATION_REVISION
                + " m27=CLOSED_RUNTIME_PROVEN"
                + " controlArchitecture=STEERING_WHEEL_TO_CREATE_KINETICS_TO_M27_SURFACES"
                + " commands=READ_ONLY_ONLY"
                + " status=IMPLEMENTED_RUNTIME_REQUIRED");
        return 1;
    }

    private static int inspect(final CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        if (source.getEntity() == null) {
            send(source, "SABLE_M28_INSPECT status=FAIL reason=player_required");
            return 0;
        }
        final SubLevel tracked = Sable.HELPER.getTrackingOrVehicleSubLevel(source.getEntity());
        if (!(tracked instanceof final ServerSubLevel body)) {
            send(source, "SABLE_M28_INSPECT status=FAIL reason=player_not_on_sable"
                    + " fixtureSessionDependency=false");
            return 0;
        }

        final List<BlockPos> blocks = SimAssemblyHelper.collectBlocks(source.getLevel(), body);
        final List<String> wheels = new ArrayList<>();
        final List<String> propellers = new ArrayList<>();
        int chestCount = 0;
        int redstoneComponentCount = 0;
        int kineticComponentCount = 0;
        for (final BlockPos raw : blocks) {
            final BlockEntity blockEntity = source.getLevel().getBlockEntity(raw);
            if (blockEntity instanceof final SteeringWheelBlockEntity wheel) {
                wheels.add("{local=" + local(body, raw)
                        + ",angle=" + wheel.getAngle()
                        + ",target=" + wheel.getTargetAngle()
                        + ",generatedRpm=" + wheel.getGeneratedSpeed()
                        + ",held=" + wheel.isHeld()
                        + ",controller=" + nullable(wheel.getController()) + "}");
            }
            if (blockEntity instanceof final WoodenPropellerBlockEntity propeller) {
                propellers.add("{local=" + local(body, raw)
                        + ",kineticSpeed=" + propeller.getSpeed()
                        + ",active=" + propeller.isActive() + "}");
            }
            final ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(source.getLevel().getBlockState(raw).getBlock());
            if (blockId != null) {
                chestCount += blockId.getPath().endsWith("chest") ? 1 : 0;
                redstoneComponentCount += blockId.getPath().contains("lever")
                        || blockId.getPath().contains("redstone") ? 1 : 0;
                kineticComponentCount += "create".equals(blockId.getNamespace()) ? 1 : 0;
            }
        }

        int controlProviderCount = 0;
        for (final KinematicContraption contraption : body.getPlot().getContraptions()) {
            controlProviderCount += contraption.sable$liftProviders().size();
        }
        final int staticProviderCount = body.getPlot().getLiftProviders().size();
        final SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(source.getLevel());
        final RigidBodyHandle handle = physics == null ? null : physics.getPhysicsHandle(body);
        final boolean handleValid = handle != null && handle.isValid();
        final Vector3d linearVelocity = handleValid
                ? handle.getLinearVelocity(new Vector3d()) : new Vector3d(Double.NaN);
        final Vector3d angularVelocity = handleValid
                ? handle.getAngularVelocity(new Vector3d()) : new Vector3d(Double.NaN);
        final Vector3dc rawCenterOfMass = body.getMassTracker().getCenterOfMass();
        final Vector3d visibleCenterOfMass = rawCenterOfMass == null ? null
                : body.logicalPose().transformPosition(new Vector3d(rawCenterOfMass));
        final boolean finite = handleValid && finite(body.logicalPose().position())
                && finite(linearVelocity) && finite(angularVelocity)
                && (visibleCenterOfMass == null || finite(visibleCenterOfMass));

        send(source, "SABLE_M28_INSPECT status=" + (finite ? "PASS" : "FAIL")
                + " sableId=" + body.getUniqueId()
                + " mass=" + body.getMassTracker().getMass()
                + " centerOfMassVisible=" + visibleCenterOfMass
                + " position=" + body.logicalPose().position()
                + " linearVelocity=" + linearVelocity
                + " angularVelocity=" + angularVelocity
                + " propulsionState=" + propellers
                + " aeroProviderCount=" + (staticProviderCount + controlProviderCount)
                + " staticAeroProviderCount=" + staticProviderCount
                + " controlProviderCount=" + controlProviderCount
                + " steeringWheels=" + wheels
                + " pitchInput=MECHANICAL_TOPOLOGY"
                + " yawInput=MECHANICAL_TOPOLOGY"
                + " rollInput=MECHANICAL_TOPOLOGY"
                + " chestCount=" + chestCount
                + " redstoneComponentCount=" + redstoneComponentCount
                + " createComponentCount=" + kineticComponentCount
                + " pilotControlOwner=ONBOARD_STEERING_WHEEL_BLOCKS"
                + " fixtureSessionDependency=false"
                + " finite=" + finite);
        return finite ? 1 : 0;
    }

    private static BlockPos local(final ServerSubLevel body, final BlockPos raw) {
        return raw.subtract(body.getPlot().getCenterBlock());
    }

    private static boolean finite(final Vector3dc value) {
        return Double.isFinite(value.x()) && Double.isFinite(value.y()) && Double.isFinite(value.z());
    }

    private static String nullable(final Object value) {
        return value == null ? "none" : value.toString();
    }

    private static void send(final CommandSourceStack source, final String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }
}
