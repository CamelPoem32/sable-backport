package dev.ryanhcode.sable.forge;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.network.udp.SableUDPServer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
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
import org.joml.Vector3d;

/** One-process runtime regression harness for the exact Create 6 target baseline. */
final class SableForgeTargetRuntimeSmoke {

    private static final boolean ENABLED = Boolean.getBoolean("sable.targetRuntimeSmoke");
    private static final String WORLD_NAME = "M6_Smoke_Empty";
    private static final String SUB_LEVEL_NAME = "create6_runtime_smoke";
    private static final String REGISTRATE_RUNTIME_JAR = "Registrate-MC1.20-1.3.3.jar";
    private static final int WORLD_STABILITY_TICKS = 40;
    private static final int PHASE_TIMEOUT_TICKS = 2400;
    private static final Pattern DASH_VERSION = Pattern.compile("-([.\\d]+)");
    private static final Pattern NON_ALPHANUM = Pattern.compile("[^A-Za-z0-9]");
    private static final Pattern REPEATING_DOTS = Pattern.compile("(\\.)(\\1)+");
    private static final Pattern LEADING_DOTS = Pattern.compile("^\\.");
    private static final Pattern TRAILING_DOTS = Pattern.compile("\\.$");
    private static final Pattern NUMBERLIKE_PARTS = Pattern.compile("(?<=^|\\.)([0-9]+)");
    private static final String JAVA_KEYWORDS = "abstract|continue|for|new|switch|assert|default|goto|package|"
            + "synchronized|boolean|do|if|private|this|break|double|implements|protected|throw|byte|else|"
            + "import|public|throws|case|enum|instanceof|return|transient|catch|extends|int|short|try|char|"
            + "final|interface|static|void|class|finally|long|strictfp|volatile|const|float|native|super|while";
    private static final Pattern KEYWORD_PARTS = Pattern.compile("(?<=^|\\.)(" + JAVA_KEYWORDS + ")(?=\\.|$)");

    private static Phase phase = Phase.INITIAL_TITLE;
    private static int phaseTicks;
    private static int stableTicks;
    private static volatile int stoppedServers;
    private static volatile boolean firstServerReady;
    private static volatile boolean firstTrackingReady;
    private static volatile boolean firstTrackingScheduled;
    private static volatile boolean secondServerReady;
    private static volatile boolean secondTrackingReady;
    private static volatile boolean secondTrackingScheduled;
    private static volatile Throwable serverFailure;
    private static UUID expectedId;
    private static Vector3d expectedPosition;
    private static MinecraftServer firstServer;
    private static MinecraftServer secondServer;
    private static SableForgeRuntimeSmoke.LifecycleBaseline firstWorldActiveBaseline;
    private static SableForgeRuntimeSmoke.LifecycleBaseline firstServerStoppedBaseline;
    private static SableForgeRuntimeSmoke.LifecycleBaseline secondWorldActiveBaseline;

    private SableForgeTargetRuntimeSmoke() {
    }

    static void install() {
        if (!ENABLED) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener(SableForgeTargetRuntimeSmoke::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, SableForgeTargetRuntimeSmoke::onServerStopped);
        pass("bootstrap", "single-JVM Create 6 runtime harness installed");
    }

    private static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || phase == Phase.COMPLETE || phase == Phase.FAILED) {
            return;
        }

        try {
            if (serverFailure != null) {
                throw new IllegalStateException("Integrated-server gate failed", serverFailure);
            }
            phaseTicks++;
            require(phaseTicks <= PHASE_TIMEOUT_TICKS, "Timed out in phase " + phase);

            final Minecraft minecraft = Minecraft.getInstance();
            switch (phase) {
                case INITIAL_TITLE -> initialTitle(minecraft);
                case OPENING_FIRST_WORLD -> openingFirstWorld(minecraft);
                case FIRST_CLIENT_SYNC -> firstClientSync(minecraft);
                case FIRST_SERVER_STOP -> firstServerStop(minecraft);
                case OPENING_SECOND_WORLD -> openingSecondWorld(minecraft);
                case SECOND_CLIENT_SYNC -> secondClientSync(minecraft);
                case SECOND_SERVER_STOP -> secondServerStop(minecraft);
                default -> throw new IllegalStateException("Unexpected phase " + phase);
            }
        } catch (final Throwable throwable) {
            fail(throwable);
            Minecraft.getInstance().stop();
            if (throwable instanceof final RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Target runtime smoke failed", throwable);
        }
    }

    private static void initialTitle(final Minecraft minecraft) throws ReflectiveOperationException, IOException {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) {
            return;
        }
        if (++stableTicks < 20) {
            return;
        }

        verifyRuntimeVersions();
        require(new File(minecraft.gameDirectory, "saves/" + WORLD_NAME).isDirectory(),
                "Disposable smoke world is missing: " + WORLD_NAME);
        pass("gate1", "MAIN_MENU Minecraft 1.20.1 / Forge 47.4.20 / Java 17 / Sable / "
                + "Create 6.0.8 / Flywheel 1.0.5 / Registrate MC1.20-1.3.3 / Ponder 1.0.91 / "
                + "Veil mod 1.0.0 (artifact 1.0.0.296) initialized");

        transition(Phase.OPENING_FIRST_WORLD);
        minecraft.createWorldOpenFlows().loadLevel(minecraft.screen, WORLD_NAME);
    }

    private static void openingFirstWorld(final Minecraft minecraft) {
        final MinecraftServer server = readyIntegratedServer(minecraft);
        if (server == null || ++stableTicks < WORLD_STABILITY_TICKS) {
            return;
        }

        firstServer = server;
        pass("gate2", "WORLD player joined; ticking/chunk loading stable; /sable scheduling next");
        firstWorldActiveBaseline = SableForgeRuntimeSmoke.captureLifecycleBaseline("first world active");
        transition(Phase.FIRST_CLIENT_SYNC);
        server.execute(() -> runServerGate(server, false));
    }

    private static void firstClientSync(final Minecraft minecraft) {
        if (!firstServerReady || !clientObjectMatches(minecraft)) {
            stableTicks = 0;
            return;
        }
        if (!firstTrackingReady) {
            scheduleTrackingCheck(firstServer, false);
            stableTicks = 0;
            return;
        }
        if (++stableTicks < WORLD_STABILITY_TICKS) {
            return;
        }

        SableForgeRuntimeSmoke.verifyTargetPacketSequence();
        pass("gate3", "SABLE_SUBLEVEL exactly one named stationary minecraft:stone object; tracking ordered");
        transition(Phase.FIRST_SERVER_STOP);
        disconnectToTitle(minecraft);
    }

    private static void firstServerStop(final Minecraft minecraft) {
        if (stoppedServers < 1 || !(minecraft.screen instanceof TitleScreen)
                || minecraft.getSingleplayerServer() != null || minecraft.level != null) {
            return;
        }

        require(SableUDPServer.getServer(firstServer) == null, "First integrated-server UDP endpoint survived stop");
        SableForgeRuntimeSmoke.verifyFirstIntegratedServerStopped(firstWorldActiveBaseline);
        firstServerStoppedBaseline = SableForgeRuntimeSmoke.captureLifecycleBaseline("first server stopped");
        SableForgeRuntimeSmoke.beginTargetPacketSequence("same-JVM reload");
        pass("gate4", "first Save and Quit returned to title without exiting client JVM");
        transition(Phase.OPENING_SECOND_WORLD);
        minecraft.createWorldOpenFlows().loadLevel(minecraft.screen, WORLD_NAME);
    }

    private static void openingSecondWorld(final Minecraft minecraft) {
        final MinecraftServer server = readyIntegratedServer(minecraft);
        if (server == null || ++stableTicks < WORLD_STABILITY_TICKS) {
            return;
        }

        secondServer = server;
        transition(Phase.SECOND_CLIENT_SYNC);
        server.execute(() -> runServerGate(server, true));
    }

    private static void secondClientSync(final Minecraft minecraft) {
        if (!secondServerReady || !clientObjectMatches(minecraft)) {
            stableTicks = 0;
            return;
        }
        if (!secondTrackingReady) {
            scheduleTrackingCheck(secondServer, true);
            stableTicks = 0;
            return;
        }
        if (++stableTicks < WORLD_STABILITY_TICKS) {
            return;
        }

        SableForgeRuntimeSmoke.verifyTargetPacketSequence();
        SableForgeRuntimeSmoke.verifySecondIntegratedServerActive(firstServerStoppedBaseline);
        secondWorldActiveBaseline = SableForgeRuntimeSmoke.captureLifecycleBaseline("second world active");
        pass("gate4", "SAME_JVM_RELOAD restored UUID/name/stone/pose; duplicate-registration audit passed");
        transition(Phase.SECOND_SERVER_STOP);
        disconnectToTitle(minecraft);
    }

    private static void secondServerStop(final Minecraft minecraft) {
        if (stoppedServers < 2 || !(minecraft.screen instanceof TitleScreen)
                || minecraft.getSingleplayerServer() != null || minecraft.level != null) {
            return;
        }

        require(SableUDPServer.getServer(secondServer) == null, "Second integrated-server UDP endpoint survived stop");
        SableForgeRuntimeSmoke.verifySecondIntegratedServerStopped(secondWorldActiveBaseline);
        pass("gate5", "CLEAN_EXIT both integrated servers, client connections, and UDP endpoints stopped cleanly");
        phase = Phase.COMPLETE;
        pass("complete", "all five target-runtime gates passed in one client JVM; requesting one clean exit");
        minecraft.stop();
    }

    private static void runServerGate(final MinecraftServer server, final boolean reload) {
        try {
            final List<ServerPlayer> players = server.getPlayerList().getPlayers();
            require(players.size() == 1, "Expected one integrated-server player, found " + players.size());
            final ServerPlayer player = players.get(0);
            final ServerSubLevelContainer container = SubLevelContainer.getContainer(player.serverLevel());
            require(container != null, "Server sublevel container is unavailable");
            require(server.getCommands().getDispatcher().getRoot().getChild("sable") != null,
                    "/sable is absent from the command dispatcher");

            if (!reload) {
                if (container.getLoadedCount() > 0) {
                    require(execute(server, player, "sable remove @e") == 1,
                            "Historical smoke object removal command failed");
                }
                require(container.getLoadedCount() == 0,
                        "Historical smoke objects remain after cleanup: " + container.getLoadedCount());
                SableForgeRuntimeSmoke.beginTargetPacketSequence("new Create 6 baseline object");
                require(execute(server, player,
                        "sable spawn block minecraft:stone " + SUB_LEVEL_NAME) == 1,
                        "Exact Sable spawn command failed");
            }

            require(execute(server, player, "sable info @l") == 1, "Exact Sable info command failed");
            final ServerSubLevel subLevel = requireSingleServerObject(container);
            if (!reload) {
                expectedId = subLevel.getUniqueId();
                expectedPosition = new Vector3d(subLevel.logicalPose().position());
            } else {
                require(subLevel.getUniqueId().equals(expectedId),
                        "Reloaded sublevel UUID changed: " + subLevel.getUniqueId() + " expected " + expectedId);
                require(subLevel.logicalPose().position().equals(expectedPosition),
                        "Reloaded sublevel pose changed: " + subLevel.logicalPose().position()
                                + " expected " + expectedPosition);
            }

            if (reload) {
                secondServerReady = true;
            } else {
                firstServerReady = true;
            }
        } catch (final Throwable throwable) {
            serverFailure = throwable;
            Sable.LOGGER.error("SABLE_TARGET_RUNTIME phase=server status=FAIL reload={}", reload, throwable);
        }
    }

    private static ServerSubLevel requireSingleServerObject(final ServerSubLevelContainer container) {
        require(container.getLoadedCount() == 1,
                "/sable info boundary expected exactly one object, found " + container.getLoadedCount());
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

    private static void scheduleTrackingCheck(final MinecraftServer server, final boolean reload) {
        if (reload ? secondTrackingScheduled : firstTrackingScheduled) {
            return;
        }
        if (reload) {
            secondTrackingScheduled = true;
        } else {
            firstTrackingScheduled = true;
        }
        server.execute(() -> {
            try {
                final List<ServerPlayer> players = server.getPlayerList().getPlayers();
                require(players.size() == 1, "Tracking check expected one player");
                final ServerSubLevelContainer container = SubLevelContainer.getContainer(players.get(0).serverLevel());
                require(container != null, "Tracking check has no server sublevel container");
                final ServerSubLevel subLevel = requireSingleServerObject(container);
                require(subLevel.getTrackingPlayers().contains(players.get(0).getUUID()),
                        "Player is not tracking the expected sublevel");
                if (reload) {
                    secondTrackingReady = true;
                } else {
                    firstTrackingReady = true;
                }
            } catch (final Throwable throwable) {
                serverFailure = throwable;
            }
        });
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

    private static int execute(
            final MinecraftServer server,
            final ServerPlayer player,
            final String command) {
        final int result = server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
        pass("command", "/" + command + " result=" + result);
        return result;
    }

    private static void disconnectToTitle(final Minecraft minecraft) {
        require(minecraft.level != null && minecraft.isLocalServer(), "Save and Quit requested outside local world");
        minecraft.level.disconnect();
        minecraft.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        minecraft.setScreen(new TitleScreen());
    }

    private static void verifyRuntimeVersions() throws ReflectiveOperationException, IOException {
        require(ModList.get().isLoaded(Sable.MOD_ID), "Sable is not loaded");
        final Map<String, String> versions = Map.of(
                "minecraft", "1.20.1",
                "forge", "47.4.20",
                "create", "6.0.8",
                "flywheel", "1.0.5",
                "ponder", "1.0.91",
                // The published artifact coordinate is 1.0.0.296, while Veil's own mods.toml
                // deliberately exposes the Forge mod version as 1.0.0.
                "veil", "1.0.0");
        versions.forEach((modId, expected) -> {
            final String actual = ModList.get().getModContainerById(modId)
                    .orElseThrow(() -> new IllegalStateException("Required runtime mod is absent: " + modId))
                    .getModInfo().getVersion().toString();
            require(expected.equals(actual), modId + " version=" + actual + ", expected=" + expected);
        });
        require(Runtime.version().feature() == 17,
                "Java feature version=" + Runtime.version().feature() + ", expected=17");
        final ClassLoader loader = SableForgeTargetRuntimeSmoke.class.getClassLoader();
        verifyUniqueRuntimeClass(loader,
                "com.llamalad7.mixinextras.platform.forge.MixinExtrasConfigPlugin", "mixinextras");
        final Class<?> mixinExtrasBootstrap = verifyUniqueRuntimeClass(loader,
                "com.llamalad7.mixinextras.MixinExtrasBootstrap", "MixinExtras");
        final String mixinExtrasVersion = (String) mixinExtrasBootstrap.getMethod("getVersion").invoke(null);
        require("0.5.3".equals(mixinExtrasVersion),
                "MixinExtras effective version=" + mixinExtrasVersion + ", expected=0.5.3");
        verifyUniqueRuntimeClass(loader, "com.simibubi.create.Create", "create");
        verifyUniqueRuntimeClass(loader,
                "dev.engine_room.flywheel.api.visualization.VisualizationManager", "flywheel");
        final String registrateModule = secureJarModuleNameFromFileName(REGISTRATE_RUNTIME_JAR);
        verifyUniqueRuntimeClass(loader, "com.tterrag.registrate.AbstractRegistrate", registrateModule);
        verifyUniqueRuntimeClass(loader,
                "net.createmod.catnip.levelWrappers.WrappedServerLevel", "ponder");
        verifyUniqueRuntimeClass(loader, "foundry.veil.api.client.render.VeilRenderSystem", "veil");
        pass("module", "Registrate module=" + registrateModule + " derived from " + REGISTRATE_RUNTIME_JAR
                + "; MixinExtras Forge/common unique version=" + mixinExtrasVersion
                + "; filtered Create 0.4.1 wrapper absent");
    }

    private static String secureJarModuleNameFromFileName(final String fileName) {
        String name = fileName;
        final int extension = name.lastIndexOf('.');
        if (extension > 0) {
            name = name.substring(0, extension);
        }
        name = DASH_VERSION.matcher(name).replaceAll("");
        name = NON_ALPHANUM.matcher(name).replaceAll(".");
        name = REPEATING_DOTS.matcher(name).replaceAll(".");
        name = LEADING_DOTS.matcher(name).replaceAll("");
        name = TRAILING_DOTS.matcher(name).replaceAll("");
        name = NUMBERLIKE_PARTS.matcher(name).replaceAll("_$1");
        return KEYWORD_PARTS.matcher(name).replaceAll("_$1");
    }

    private static Class<?> verifyUniqueRuntimeClass(
            final ClassLoader loader,
            final String className,
            final String expectedModule) throws ClassNotFoundException, IOException {
        final String resourceName = className.replace('.', '/') + ".class";
        final List<URL> resources = Collections.list(loader.getResources(resourceName));
        require(resources.size() == 1,
                className + " resource count=" + resources.size() + ", expected exactly one: " + resources);
        final Class<?> runtimeClass = Class.forName(className, false, loader);
        final String moduleName = runtimeClass.getModule().getName();
        require(expectedModule.equals(moduleName),
                className + " module=" + moduleName + ", expected=" + expectedModule);
        pass("module", className + " module=" + moduleName + " source=" + resources.get(0));
        return runtimeClass;
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
        Sable.LOGGER.error("SABLE_TARGET_RUNTIME phase={} status=FAIL", phase, throwable);
    }

    private static void pass(final String gate, final String detail) {
        Sable.LOGGER.info("SABLE_TARGET_RUNTIME phase={} status=PASS {}", gate, detail);
    }

    private enum Phase {
        INITIAL_TITLE,
        OPENING_FIRST_WORLD,
        FIRST_CLIENT_SYNC,
        FIRST_SERVER_STOP,
        OPENING_SECOND_WORLD,
        SECOND_CLIENT_SYNC,
        SECOND_SERVER_STOP,
        COMPLETE,
        FAILED
    }
}
