package dev.ryanhcode.sable.forge;

import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.impl.DefaultSableCompanion;
import dev.ryanhcode.sable.network.udp.SableUDPServer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

/** Short standalone artifact smoke for the final Forge jar with Companion packaged by JarJar. */
final class SableForgeStandaloneRuntimeSmoke {

    private static final boolean ENABLED = Boolean.getBoolean("sable.standaloneRuntimeSmoke");
    private static final String WORLD_NAME = "M6_Smoke_Empty";
    private static final String SUB_LEVEL_NAME = "create6_runtime_smoke";
    private static final String PACKAGED_COMPANION =
            "META-INF/jarjar/sable-companion-common-1.20.1-1.6.0.jar";
    private static final int WORLD_STABILITY_TICKS = 40;
    private static final int PHASE_TIMEOUT_TICKS = 2400;

    private static Phase phase = Phase.INITIAL_TITLE;
    private static int phaseTicks;
    private static int stableTicks;
    private static volatile int stoppedServers;
    private static volatile boolean serverReady;
    private static volatile Throwable serverFailure;
    private static MinecraftServer activeServer;
    private static UUID expectedId;

    private SableForgeStandaloneRuntimeSmoke() {
    }

    static void install() {
        if (!ENABLED) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener(SableForgeStandaloneRuntimeSmoke::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, SableForgeStandaloneRuntimeSmoke::onServerStopped);
        pass("bootstrap", "standalone packaged-artifact smoke installed");
    }

    private static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || phase == Phase.COMPLETE || phase == Phase.FAILED) {
            return;
        }

        try {
            if (serverFailure != null) {
                throw new IllegalStateException("Standalone server gate failed", serverFailure);
            }
            phaseTicks++;
            require(phaseTicks <= PHASE_TIMEOUT_TICKS, "Timed out in phase " + phase);

            final Minecraft minecraft = Minecraft.getInstance();
            switch (phase) {
                case INITIAL_TITLE -> initialTitle(minecraft);
                case OPENING_WORLD -> openingWorld(minecraft);
                case CLIENT_SYNC -> clientSync(minecraft);
                case SERVER_STOP -> serverStop(minecraft);
                default -> throw new IllegalStateException("Unexpected phase " + phase);
            }
        } catch (final Throwable throwable) {
            fail(throwable);
            Minecraft.getInstance().stop();
            if (throwable instanceof final RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Standalone runtime smoke failed", throwable);
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
        verifyPackagedCompanion();
        require(new File(minecraft.gameDirectory, "saves/" + WORLD_NAME).isDirectory(),
                "Disposable smoke world is missing: " + WORLD_NAME);
        pass("gate1", "title reached with final Sable artifact and exact Create 6 runtime stack");

        transition(Phase.OPENING_WORLD);
        minecraft.createWorldOpenFlows().loadLevel(minecraft.screen, WORLD_NAME);
    }

    private static void openingWorld(final Minecraft minecraft) {
        final MinecraftServer server = readyIntegratedServer(minecraft);
        if (server == null || ++stableTicks < WORLD_STABILITY_TICKS) {
            return;
        }

        activeServer = server;
        transition(Phase.CLIENT_SYNC);
        server.execute(() -> runServerGate(server));
    }

    private static void clientSync(final Minecraft minecraft) {
        if (!serverReady || !clientObjectMatches(minecraft)) {
            stableTicks = 0;
            return;
        }
        if (++stableTicks < WORLD_STABILITY_TICKS) {
            return;
        }

        pass("gate2-3", "world joined; /sable present; packaged Companion active; persisted stone mass=2.0");
        transition(Phase.SERVER_STOP);
        disconnectToTitle(minecraft);
    }

    private static void serverStop(final Minecraft minecraft) {
        if (stoppedServers < 1 || !(minecraft.screen instanceof TitleScreen)
                || minecraft.getSingleplayerServer() != null || minecraft.level != null) {
            return;
        }

        require(SableUDPServer.getServer(activeServer) == null, "Integrated-server UDP endpoint survived stop");
        pass("gate5", "standalone artifact smoke saved, quit to title, and cleaned up server/UDP state");
        phase = Phase.COMPLETE;
        pass("complete", "standalone packaged-artifact runtime smoke passed; requesting clean client exit");
        minecraft.stop();
    }

    private static void runServerGate(final MinecraftServer server) {
        try {
            final List<ServerPlayer> players = server.getPlayerList().getPlayers();
            require(players.size() == 1, "Expected one integrated-server player, found " + players.size());
            final ServerPlayer player = players.get(0);
            final ServerSubLevelContainer container = SubLevelContainer.getContainer(player.serverLevel());
            require(container != null, "Server sublevel container is unavailable");
            require(server.getCommands().getDispatcher().getRoot().getChild("sable") != null,
                    "/sable is absent from the command dispatcher");
            require(server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "sable info @l") == 1,
                    "Exact Sable info command failed");

            final ServerSubLevel subLevel = requireSingleServerObject(container);
            expectedId = subLevel.getUniqueId();
            serverReady = true;
        } catch (final Throwable throwable) {
            serverFailure = throwable;
            Sable.LOGGER.error("SABLE_STANDALONE_RUNTIME phase=server status=FAIL", throwable);
        }
    }

    private static ServerSubLevel requireSingleServerObject(final ServerSubLevelContainer container) {
        require(container.getLoadedCount() == 1,
                "Expected exactly one persisted object, found " + container.getLoadedCount());
        final SubLevel rawSubLevel = container.getAllSubLevels().get(0);
        require(rawSubLevel instanceof ServerSubLevel, "Loaded object is not a ServerSubLevel");
        final ServerSubLevel subLevel = (ServerSubLevel) rawSubLevel;
        require(SUB_LEVEL_NAME.equals(subLevel.getName()), "Unexpected sublevel name: " + subLevel.getName());
        require(subLevel.getPlot().getEmbeddedLevelAccessor().getBlockState(BlockPos.ZERO).is(Blocks.STONE),
                "Expected minecraft:stone at the sublevel origin");
        require(subLevel.getMassTracker().getMass() == 2.0,
                "Expected stationary stone mass 2.0, found " + subLevel.getMassTracker().getMass());
        require(subLevel.latestLinearVelocity.lengthSquared() == 0.0,
                "Sublevel has nonzero linear velocity: " + subLevel.latestLinearVelocity);
        require(subLevel.latestAngularVelocity.lengthSquared() == 0.0,
                "Sublevel has nonzero angular velocity: " + subLevel.latestAngularVelocity);
        return subLevel;
    }

    private static boolean clientObjectMatches(final Minecraft minecraft) {
        if (minecraft.level == null || expectedId == null) {
            return false;
        }
        final ClientSubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        if (container == null || container.getLoadedCount() != 1) {
            return false;
        }
        final SubLevel subLevel = container.getAllSubLevels().get(0);
        return subLevel.getUniqueId().equals(expectedId)
                && SUB_LEVEL_NAME.equals(subLevel.getName())
                && subLevel.getPlot().getEmbeddedLevelAccessor().getBlockState(BlockPos.ZERO).is(Blocks.STONE);
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

    private static void verifyPackagedCompanion() throws IOException {
        SableForgeRuntimeSmoke.verifyCompanionSelection();
        final ClassLoader loader = SableForgeStandaloneRuntimeSmoke.class.getClassLoader();
        final URL api = verifyUniqueRuntimeClass(loader, SableCompanion.class.getName());
        final URL active = verifyUniqueRuntimeClass(loader, ActiveSableCompanion.class.getName());
        final URL fallback = verifyUniqueRuntimeClass(loader, DefaultSableCompanion.class.getName());
        final URL smoke = verifyUniqueRuntimeClass(loader, SableForgeStandaloneRuntimeSmoke.class.getName());
        final String outerArtifact = requireStandaloneSableOuterArtifact("Sable standalone smoke", smoke);
        requireNestedCompanionSource("SableCompanion", api, outerArtifact, SableCompanion.class.getName());
        requireOuterSableSource("ActiveSableCompanion", active, outerArtifact, ActiveSableCompanion.class.getName());
        requireNestedCompanionSource("DefaultSableCompanion", fallback, outerArtifact,
                DefaultSableCompanion.class.getName());
        pass("companion", "packaged sources api=" + api + " active=" + active + " default=" + fallback);
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

    private static void requireNestedCompanionSource(
            final String label,
            final URL source,
            final String outerArtifact,
            final String className) {
        final ParsedRuntimeResource parsed = parseRuntimeResourceUrl(source.toString());
        requireNoDevelopmentSource(label, parsed.normalizedUrl, source);
        require(parsed.outerArtifact.equals(outerArtifact)
                        && PACKAGED_COMPANION.equals(parsed.nestedEntry)
                        && parsed.resource.equals(className.replace('.', '/') + ".class"),
                label + " did not resolve from the nested Companion JarJar inside the staged Sable artifact: "
                        + source);
    }

    private static void requireNoDevelopmentSource(final String label, final String path, final URL source) {
        require(!path.contains("/build/classes/") && !path.contains("/sable_companion_1_20/build/")
                        && !path.contains("/forge/build/classes/"),
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

    private enum Phase {
        INITIAL_TITLE,
        OPENING_WORLD,
        CLIENT_SYNC,
        SERVER_STOP,
        COMPLETE,
        FAILED
    }
}
