package dev.ryanhcode.sable.forge;

import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineProvider;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.impl.DefaultSableCompanion;
import dev.ryanhcode.sable.network.udp.SableUDPServer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import org.joml.Vector3d;

/** M8 standalone packaged-artifact smoke for the final Forge jar with real Rapier physics packaged by JarJar. */
final class SableForgeStandaloneRuntimeSmoke {

    private static final boolean ENABLED = Boolean.getBoolean("sable.standaloneRuntimeSmoke");
    private static final String WORLD_NAME = "M6_Smoke_Empty";
    private static final String SUB_LEVEL_NAME = "m8_rapier_gravity_smoke";
    private static final String PACKAGED_COMPANION =
            "META-INF/jarjar/sable-companion-common-1.20.1-1.6.0.jar";
    private static final String PACKAGED_RAPIER =
            "META-INF/jarjar/sable-rapier-common-1.20.1-2.0.0.jar";
    private static final String PACKAGED_LZ4 = "META-INF/jarjar/lz4-java-1.11.0.jar";
    private static final String RAPIER_PROVIDER =
            "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipelineProvider";
    private static final String RAPIER_PIPELINE =
            "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline";
    private static final String RAPIER_3D = "dev.ryanhcode.sable.physics.impl.rapier.Rapier3D";
    private static final int WORLD_STABILITY_TICKS = 20;
    private static final int PHASE_TIMEOUT_TICKS = 2400;
    private static final int PHYSICS_TIMEOUT_TICKS = 600;
    private static final int COLLISION_STABLE_TICKS = 20;
    private static final double SPAWN_X = 0.5;
    private static final double SPAWN_Y = 88.0;
    private static final double SPAWN_Z = 0.5;
    private static final int PLATFORM_Y = 80;
    private static final double GRAVITY_EPSILON = 0.05;
    private static final double INITIAL_VELOCITY_EPSILON = 0.25;
    private static final double DOWNWARD_VELOCITY_EPSILON = -0.01;
    private static final double REST_VELOCITY_EPSILON = 0.15;
    private static final double REST_POSITION_EPSILON = 0.02;

    private static Phase phase = Phase.INITIAL_TITLE;
    private static int phaseTicks;
    private static int stableTicks;
    private static volatile int stoppedServers;
    private static volatile boolean physicsGateComplete;
    private static volatile Throwable serverFailure;
    private static MinecraftServer activeServer;
    private static UUID expectedId;
    private static ServerSubLevel observedSubLevel;
    private static PhysicsSample initialSample;
    private static PhysicsSample previousSample;
    private static int physicsTicks;
    private static int collisionStableTicks;
    private static double minObservedY = Double.POSITIVE_INFINITY;
    private static boolean sawDownwardMotion;
    private static boolean sawDownwardVelocity;

    private SableForgeStandaloneRuntimeSmoke() {
    }

    static void install() {
        if (!ENABLED) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener(SableForgeStandaloneRuntimeSmoke::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(SableForgeStandaloneRuntimeSmoke::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, SableForgeStandaloneRuntimeSmoke::onServerStopped);
        pass("bootstrap", "M8 Rapier packaged-artifact smoke installed");
    }

    private static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || phase == Phase.COMPLETE || phase == Phase.FAILED) {
            return;
        }

        try {
            if (serverFailure != null) {
                throw new IllegalStateException("M8 Rapier server gate failed", serverFailure);
            }
            phaseTicks++;
            require(phaseTicks <= PHASE_TIMEOUT_TICKS, "Timed out in phase " + phase);

            final Minecraft minecraft = Minecraft.getInstance();
            switch (phase) {
                case INITIAL_TITLE -> initialTitle(minecraft);
                case OPENING_WORLD -> openingWorld(minecraft);
                case PHYSICS_OBSERVE -> physicsObserve(minecraft);
                case SERVER_STOP -> serverStop(minecraft);
                default -> throw new IllegalStateException("Unexpected phase " + phase);
            }
        } catch (final Throwable throwable) {
            fail(throwable);
            Minecraft.getInstance().stop();
            if (throwable instanceof final RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("M8 Rapier standalone runtime smoke failed", throwable);
        }
    }

    private static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || phase != Phase.PHYSICS_OBSERVE || activeServer == null
                || event.getServer() != activeServer || physicsGateComplete || serverFailure != null) {
            return;
        }
        try {
            if (observedSubLevel == null) {
                startPhysicsGate(event.getServer());
            } else {
                observePhysicsGate();
            }
        } catch (final Throwable throwable) {
            serverFailure = throwable;
            Sable.LOGGER.error("SABLE_STANDALONE_RUNTIME phase=m8-physics-server status=FAIL", throwable);
        }
    }

    private static void initialTitle(final Minecraft minecraft) throws IOException {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) {
            return;
        }
        if (++stableTicks < 20) {
            return;
        }

        verifyRuntimeVersions();
        verifyPackagedLibraries();
        require(new File(minecraft.gameDirectory, "saves/" + WORLD_NAME).isDirectory(),
                "Disposable smoke world is missing: " + WORLD_NAME);
        pass("gate1", "title reached with mapped-from-final Sable artifact, nested Rapier, nested LZ4, and exact Create 6 runtime stack");

        transition(Phase.OPENING_WORLD);
        minecraft.createWorldOpenFlows().loadLevel(minecraft.screen, WORLD_NAME);
    }

    private static void openingWorld(final Minecraft minecraft) {
        final MinecraftServer server = readyIntegratedServer(minecraft);
        if (server == null || ++stableTicks < WORLD_STABILITY_TICKS) {
            return;
        }

        activeServer = server;
        transition(Phase.PHYSICS_OBSERVE);
    }

    private static void physicsObserve(final Minecraft minecraft) {
        if (!physicsGateComplete) {
            stableTicks = 0;
            return;
        }
        if (++stableTicks < WORLD_STABILITY_TICKS) {
            return;
        }

        pass("gate2-3", "Rapier provider/native/gravity/collision smoke passed for fresh " + SUB_LEVEL_NAME);
        transition(Phase.SERVER_STOP);
        disconnectToTitle(minecraft);
    }

    private static void serverStop(final Minecraft minecraft) {
        if (stoppedServers < 1 || !(minecraft.screen instanceof TitleScreen)
                || minecraft.getSingleplayerServer() != null || minecraft.level != null) {
            return;
        }

        require(SableUDPServer.getServer(activeServer) == null, "Integrated-server UDP endpoint survived stop");
        pass("gate5", "M8 Rapier standalone smoke saved, quit to title, and cleaned up server/UDP state");
        phase = Phase.COMPLETE;
        pass("complete", "M8 Rapier packaged-artifact runtime smoke passed; requesting clean client exit");
        minecraft.stop();
    }

    private static void startPhysicsGate(final MinecraftServer server) {
        final List<ServerPlayer> players = server.getPlayerList().getPlayers();
        require(players.size() == 1, "Expected one integrated-server player, found " + players.size());
        final ServerPlayer player = players.get(0);
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(player.serverLevel());
        require(container != null, "Server sublevel container is unavailable");
        require(server.getCommands().getDispatcher().getRoot().getChild("sable") != null,
                "/sable is absent from the command dispatcher");

        final String providerName = PhysicsPipelineProvider.INSTANCE.getClass().getName();
        require(RAPIER_PROVIDER.equals(providerName), "PhysicsPipelineProvider selected " + providerName);
        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final PhysicsPipeline pipeline = physicsSystem.getPipeline();
        require(RAPIER_PIPELINE.equals(pipeline.getClass().getName()),
                "SubLevelPhysicsSystem pipeline is " + pipeline.getClass().getName());
        rapierPass("provider", "provider=RapierPhysicsPipelineProvider pipeline=" + pipeline.getClass().getName());
        rapierPass("native", "Rapier pipeline initialized; native/JNI init completed before first server tick");

        cleanupPreviousM8Object(container);
        prepareCollisionPlatform(player);
        require(server.getCommands().performPrefixedCommand(
                        player.createCommandSourceStack(), "tp @s " + SPAWN_X + " " + SPAWN_Y + " " + SPAWN_Z) == 1,
                "Failed to teleport player to deterministic M8 spawn point");
        require(server.getCommands().performPrefixedCommand(
                        player.createCommandSourceStack(), "sable spawn block minecraft:stone " + SUB_LEVEL_NAME) == 1,
                "Failed to spawn fresh M8 Rapier stone sublevel");

        observedSubLevel = requireNamedM8Object(container);
        expectedId = observedSubLevel.getUniqueId();
        final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(observedSubLevel);
        initialSample = sample(observedSubLevel, handle);
        previousSample = initialSample;
        minObservedY = initialSample.y;
        require(initialSample.mass == 2.0, "Expected M8 stone mass 2.0, found " + initialSample.mass);
        require(initialSample.finite(), "Initial M8 physics sample contains non-finite values: " + initialSample);
        require(initialSample.approximatelyStationary(INITIAL_VELOCITY_EPSILON),
                "Fresh M8 object was not approximately stationary before gravity observation: " + initialSample);
        rapierPass("spawn", "freshObject=" + SUB_LEVEL_NAME + " initial=" + initialSample);
    }

    private static void observePhysicsGate() {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(observedSubLevel.getLevel());
        require(container != null, "Container disappeared while observing M8 Rapier physics");
        final SubLevel raw = container.getSubLevel(expectedId);
        require(raw instanceof ServerSubLevel, "Fresh M8 sublevel disappeared before physics completion");
        final ServerSubLevel subLevel = (ServerSubLevel) raw;
        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
        final PhysicsSample current = sample(subLevel, handle);
        require(current.finite(), "M8 physics sample contains non-finite values: " + current);
        require(current.y > PLATFORM_Y - 4.0, "M8 sublevel fell through the collision region: " + current);

        physicsTicks++;
        minObservedY = Math.min(minObservedY, current.y);
        sawDownwardMotion |= current.y < initialSample.y - GRAVITY_EPSILON;
        sawDownwardVelocity |= current.linearVelocityY < DOWNWARD_VELOCITY_EPSILON;

        final boolean nearCollisionRegion = current.y <= PLATFORM_Y + 3.0;
        final boolean lowVelocity = Math.abs(current.linearVelocityY) <= REST_VELOCITY_EPSILON;
        final boolean locallyStable = Math.abs(current.y - previousSample.y) <= REST_POSITION_EPSILON;
        collisionStableTicks = nearCollisionRegion && lowVelocity && locallyStable ? collisionStableTicks + 1 : 0;
        previousSample = current;

        if (sawDownwardMotion && sawDownwardVelocity && collisionStableTicks >= COLLISION_STABLE_TICKS) {
            rapierPass("gravity", "initialY=" + initialSample.y + " minY=" + minObservedY
                    + " currentY=" + current.y + " velocityY=" + current.linearVelocityY);
            rapierPass("collision", "platformY=" + PLATFORM_Y + " stableTicks=" + collisionStableTicks
                    + " final=" + current);
            physicsGateComplete = true;
            return;
        }
        require(physicsTicks <= PHYSICS_TIMEOUT_TICKS,
                "M8 Rapier gravity/collision did not complete within " + PHYSICS_TIMEOUT_TICKS
                        + " server ticks; downwardMotion=" + sawDownwardMotion
                        + ", downwardVelocity=" + sawDownwardVelocity
                        + ", stableTicks=" + collisionStableTicks
                        + ", initial=" + initialSample
                        + ", current=" + current);
    }

    private static void cleanupPreviousM8Object(final ServerSubLevelContainer container) {
        for (final ServerSubLevel subLevel : new ArrayList<>(container.getAllSubLevels())) {
            if (SUB_LEVEL_NAME.equals(subLevel.getName())) {
                container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
            }
        }
        require(container.getAllSubLevels().stream().noneMatch(subLevel -> SUB_LEVEL_NAME.equals(subLevel.getName())),
                "Previous M8 sublevel survived cleanup");
    }

    private static void prepareCollisionPlatform(final ServerPlayer player) {
        final BlockState platform = Blocks.STONE.defaultBlockState();
        final BlockState air = Blocks.AIR.defaultBlockState();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = PLATFORM_Y + 1; y <= SPAWN_Y + 2; y++) {
                    player.serverLevel().setBlock(pos.set(x, y, z), air, 3);
                }
                player.serverLevel().setBlock(pos.set(x, PLATFORM_Y, z), platform, 3);
            }
        }
    }

    private static ServerSubLevel requireNamedM8Object(final ServerSubLevelContainer container) {
        final List<ServerSubLevel> matches = container.getAllSubLevels().stream()
                .filter(subLevel -> SUB_LEVEL_NAME.equals(subLevel.getName()))
                .toList();
        require(matches.size() == 1, "Expected exactly one fresh M8 object, found " + matches.size());
        final ServerSubLevel subLevel = matches.get(0);
        require(subLevel.getPlot().getEmbeddedLevelAccessor().getBlockState(BlockPos.ZERO).is(Blocks.STONE),
                "Expected minecraft:stone at the M8 sublevel origin");
        return subLevel;
    }

    private static PhysicsSample sample(final ServerSubLevel subLevel, final RigidBodyHandle handle) {
        final Vector3d position = new Vector3d(subLevel.logicalPose().position());
        final Vector3d linearVelocity = handle.getLinearVelocity(new Vector3d());
        final Vector3d angularVelocity = handle.getAngularVelocity(new Vector3d());
        return new PhysicsSample(
                subLevel.getMassTracker().getMass(),
                position.x,
                position.y,
                position.z,
                subLevel.logicalPose().orientation().x(),
                subLevel.logicalPose().orientation().y(),
                subLevel.logicalPose().orientation().z(),
                subLevel.logicalPose().orientation().w(),
                linearVelocity.x,
                linearVelocity.y,
                linearVelocity.z,
                angularVelocity.x,
                angularVelocity.y,
                angularVelocity.z);
    }

    private static MinecraftServer readyIntegratedServer(final Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        final MinecraftServer server = minecraft.getSingleplayerServer();
        return server != null && server.isReady() && server.getPlayerCount() == 1 ? server : null;
    }

    private static void disconnectToTitle(final Minecraft minecraft) {
        require(minecraft.level != null && minecraft.isLocalServer(), "Save and Quit requested outside local world");
        minecraft.level.disconnect();
        minecraft.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        minecraft.setScreen(new TitleScreen());
    }

    private static void verifyRuntimeVersions() {
        final Map<String, String> versions = Map.of(
                "minecraft", "1.20.1",
                "forge", "47.4.20",
                "create", "6.0.8",
                "flywheel", "1.0.5",
                "ponder", "1.0.91",
                "veil", "1.0.0");
        versions.forEach((modId, expected) -> {
            final String actual = ModList.get().getModContainerById(modId)
                    .orElseThrow(() -> new IllegalStateException("Required runtime mod is absent: " + modId))
                    .getModInfo().getVersion().toString();
            require(expected.equals(actual), modId + " version=" + actual + ", expected=" + expected);
        });
        require(Runtime.version().feature() == 17,
                "Java feature version=" + Runtime.version().feature() + ", expected=17");
    }

    private static void verifyPackagedLibraries() throws IOException {
        SableForgeRuntimeSmoke.verifyCompanionSelection();
        final ClassLoader loader = SableForgeStandaloneRuntimeSmoke.class.getClassLoader();
        final URL api = verifyUniqueRuntimeClass(loader, SableCompanion.class.getName());
        final URL active = verifyUniqueRuntimeClass(loader, ActiveSableCompanion.class.getName());
        final URL fallback = verifyUniqueRuntimeClass(loader, DefaultSableCompanion.class.getName());
        final URL smoke = verifyUniqueRuntimeClass(loader, SableForgeStandaloneRuntimeSmoke.class.getName());
        final URL rapierProvider = verifyUniqueRuntimeClass(loader, RAPIER_PROVIDER);
        final URL rapier3d = verifyUniqueRuntimeClass(loader, RAPIER_3D);
        final URL lz4 = verifyUniqueRuntimeClass(loader, "net.jpountz.lz4.LZ4FrameInputStream");
        final String outerArtifact = requireStandaloneSableOuterArtifact("Sable standalone smoke", smoke);
        requireNestedJarSource("SableCompanion", api, outerArtifact, PACKAGED_COMPANION,
                SableCompanion.class.getName());
        requireOuterSableSource("ActiveSableCompanion", active, outerArtifact, ActiveSableCompanion.class.getName());
        requireNestedJarSource("DefaultSableCompanion", fallback, outerArtifact, PACKAGED_COMPANION,
                DefaultSableCompanion.class.getName());
        requireNestedJarSource("RapierPhysicsPipelineProvider", rapierProvider, outerArtifact, PACKAGED_RAPIER,
                RAPIER_PROVIDER);
        requireNestedJarSource("Rapier3D", rapier3d, outerArtifact, PACKAGED_RAPIER, RAPIER_3D);
        requireNestedJarSource("LZ4FrameInputStream", lz4, outerArtifact, PACKAGED_LZ4,
                "net.jpountz.lz4.LZ4FrameInputStream");
        pass("provenance", "companion=" + api + " rapier=" + rapierProvider + " lz4=" + lz4);
    }

    private static URL verifyUniqueRuntimeClass(final ClassLoader loader, final String className) throws IOException {
        final String resourceName = className.replace('.', '/') + ".class";
        final List<URL> resources = Collections.list(loader.getResources(resourceName));
        require(resources.size() == 1,
                className + " resource count=" + resources.size() + ", expected exactly one: " + resources);
        return resources.get(0);
    }

    private static String requireStandaloneSableOuterArtifact(final String label, final URL source) {
        final ParsedRuntimeResource parsed = parseRuntimeResourceUrl(source.toString());
        requireNoDevelopmentSource(label, parsed.normalizedUrl, source);
        final String outerArtifact = parsed.outerArtifact;
        require(outerArtifact.contains("/run/standalone-client/mods/")
                        && outerArtifact.endsWith("/sable-forge-1.20.1-2.0.0-all-userdev.jar"),
                label + " did not resolve from the staged Sable artifact: " + source);
        return outerArtifact;
    }

    private static void requireOuterSableSource(
            final String label,
            final URL source,
            final String outerArtifact,
            final String className) {
        final ParsedRuntimeResource parsed = parseRuntimeResourceUrl(source.toString());
        requireNoDevelopmentSource(label, parsed.normalizedUrl, source);
        require(parsed.outerArtifact.equals(outerArtifact)
                        && parsed.nestedEntry == null
                        && parsed.resource.equals(className.replace('.', '/') + ".class"),
                label + " did not resolve from the staged Sable outer artifact: " + source);
    }

    private static void requireNestedJarSource(
            final String label,
            final URL source,
            final String outerArtifact,
            final String nestedJar,
            final String className) {
        final ParsedRuntimeResource parsed = parseRuntimeResourceUrl(source.toString());
        requireNoDevelopmentSource(label, parsed.normalizedUrl, source);
        require(parsed.outerArtifact.equals(outerArtifact)
                        && nestedJar.equals(parsed.nestedEntry)
                        && parsed.resource.equals(className.replace('.', '/') + ".class"),
                label + " did not resolve from " + nestedJar + " inside the staged Sable artifact: " + source);
    }

    private static void requireNoDevelopmentSource(final String label, final String path, final URL source) {
        require(!path.contains("/build/classes/") && !path.contains("/sable_companion_1_20/build/")
                        && !path.contains("/forge/build/classes/") && !path.contains("/rapierBackport/"),
                label + " resolved from development output instead of packaged artifact: " + source);
    }

    static String normalizeResourceUrl(final String rawUrl) {
        String path = URLDecoder.decode(rawUrl, StandardCharsets.UTF_8).replace('\\', '/');
        if (path.startsWith("jar:")) {
            path = path.substring("jar:".length());
        }
        if (path.startsWith("union:")) {
            path = path.substring("union:".length());
        }
        if (path.startsWith("file:")) {
            path = path.substring("file:".length());
        }
        if (path.length() >= 3 && path.charAt(0) == '/' && path.charAt(2) == ':') {
            path = path.substring(1);
        }
        return path;
    }

    static ParsedRuntimeResource parseRuntimeResourceUrl(final String rawUrl) {
        final String path = normalizeResourceUrl(rawUrl);
        final int outerJarEnd = findJarEnd(path, 0);
        final String outerArtifact = path.substring(0, outerJarEnd);
        String suffix = stripSecureJarId(path.substring(outerJarEnd));
        if (suffix.startsWith("!/")) {
            return new ParsedRuntimeResource(path, outerArtifact, null, suffix.substring("!/".length()));
        }
        if (suffix.startsWith("/")) {
            final String nestedAndResource = suffix.substring(1);
            final int nestedJarEnd = findJarEnd(nestedAndResource, 0);
            final String nestedEntry = nestedAndResource.substring(0, nestedJarEnd);
            suffix = stripSecureJarId(nestedAndResource.substring(nestedJarEnd));
            require(suffix.startsWith("!/"), "Nested JarJar resource did not end with !/: " + path);
            return new ParsedRuntimeResource(path, outerArtifact, nestedEntry, suffix.substring("!/".length()));
        }
        require(false, "Resource did not resolve from a supported jar artifact URL: " + path);
        throw new IllegalStateException("unreachable");
    }

    private static int findJarEnd(final String path, final int fromIndex) {
        int jarName = path.indexOf(".jar", fromIndex);
        while (jarName >= 0) {
            final int jarEnd = jarName + ".jar".length();
            if (jarEnd == path.length()
                    || path.charAt(jarEnd) == '#'
                    || path.charAt(jarEnd) == '!'
                    || path.charAt(jarEnd) == '/') {
                return jarEnd;
            }
            jarName = path.indexOf(".jar", jarEnd);
        }
        require(false, "Resource did not contain a jar artifact: " + path);
        throw new IllegalStateException("unreachable");
    }

    private static String stripSecureJarId(final String suffix) {
        if (!suffix.startsWith("#")) {
            return suffix;
        }
        int index = 1;
        while (index < suffix.length() && Character.isDigit(suffix.charAt(index))) {
            index++;
        }
        require(index > 1, "SecureJar id did not contain digits: " + suffix);
        require(index < suffix.length(), "SecureJar id was not followed by a resource separator: " + suffix);
        final char separator = suffix.charAt(index);
        require(separator == '!' || separator == '_',
                "SecureJar id used unsupported separator '" + separator + "': " + suffix);
        if (separator == '_') {
            return suffix.substring(index + 1);
        }
        return suffix.substring(index);
    }

    static final class ParsedRuntimeResource {
        final String normalizedUrl;
        final String outerArtifact;
        final String nestedEntry;
        final String resource;

        ParsedRuntimeResource(
                final String normalizedUrl,
                final String outerArtifact,
                final String nestedEntry,
                final String resource) {
            this.normalizedUrl = normalizedUrl;
            this.outerArtifact = outerArtifact;
            this.nestedEntry = nestedEntry;
            this.resource = resource;
        }
    }

    private static void onServerStopped(final ServerStoppedEvent event) {
        stoppedServers++;
        if (phase == Phase.FAILED) {
            Sable.LOGGER.info("SABLE_STANDALONE_RUNTIME phase=lifecycle status=SKIP after failed M8 gate cycle={}",
                    stoppedServers);
            return;
        }
        pass("lifecycle", "observed integrated server stop cycle=" + stoppedServers);
    }

    private static void transition(final Phase next) {
        phase = next;
        phaseTicks = 0;
        stableTicks = 0;
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void fail(final Throwable throwable) {
        phase = Phase.FAILED;
        Sable.LOGGER.error("SABLE_STANDALONE_RUNTIME phase={} status=FAIL", phase, throwable);
    }

    private static void pass(final String gate, final String detail) {
        Sable.LOGGER.info("SABLE_STANDALONE_RUNTIME phase={} status=PASS {}", gate, detail);
    }

    private static void rapierPass(final String gate, final String detail) {
        Sable.LOGGER.info("SABLE_RAPIER_SMOKE phase={} status=PASS {}", gate, detail);
    }

    private enum Phase {
        INITIAL_TITLE,
        OPENING_WORLD,
        PHYSICS_OBSERVE,
        SERVER_STOP,
        COMPLETE,
        FAILED
    }

    private record PhysicsSample(
            double mass,
            double x,
            double y,
            double z,
            double orientationX,
            double orientationY,
            double orientationZ,
            double orientationW,
            double linearVelocityX,
            double linearVelocityY,
            double linearVelocityZ,
            double angularVelocityX,
            double angularVelocityY,
            double angularVelocityZ) {

        boolean finite() {
            return Double.isFinite(this.mass)
                    && Double.isFinite(this.x)
                    && Double.isFinite(this.y)
                    && Double.isFinite(this.z)
                    && Double.isFinite(this.orientationX)
                    && Double.isFinite(this.orientationY)
                    && Double.isFinite(this.orientationZ)
                    && Double.isFinite(this.orientationW)
                    && Double.isFinite(this.linearVelocityX)
                    && Double.isFinite(this.linearVelocityY)
                    && Double.isFinite(this.linearVelocityZ)
                    && Double.isFinite(this.angularVelocityX)
                    && Double.isFinite(this.angularVelocityY)
                    && Double.isFinite(this.angularVelocityZ);
        }

        boolean approximatelyStationary(final double epsilon) {
            return Math.abs(this.linearVelocityX) <= epsilon
                    && Math.abs(this.linearVelocityY) <= epsilon
                    && Math.abs(this.linearVelocityZ) <= epsilon
                    && Math.abs(this.angularVelocityX) <= epsilon
                    && Math.abs(this.angularVelocityY) <= epsilon
                    && Math.abs(this.angularVelocityZ) <= epsilon;
        }
    }
}
