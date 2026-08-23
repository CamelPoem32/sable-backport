package dev.ryanhcode.sable.forge;

import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.impl.DefaultSableCompanion;
import dev.ryanhcode.sable.network.tcp.SablePacketRegistration;
import dev.ryanhcode.sable.network.tcp.SablePacketTransport;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.network.tcp.SableTCPPackets;
import dev.ryanhcode.sable.platform.SableAssemblyPlatform;
import dev.ryanhcode.sable.platform.SableChunkEventPlatform;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.platform.SableEventPublishPlatform;
import dev.ryanhcode.sable.platform.SableLoaderPlatform;
import dev.ryanhcode.sable.platform.SablePlatform;
import dev.ryanhcode.sable.platform.SablePlotPlatform;
import dev.ryanhcode.sable.platform.SableSubLevelRenderPlatform;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

public final class SableForgeRuntimeSmoke {

    private static final boolean ENABLED = Boolean.getBoolean("sable.runtimeSmoke");
    private static final AtomicBoolean COMMON_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean CLIENT_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean SERVER_ACTIVE = new AtomicBoolean();
    private static final AtomicInteger COMMON_CONFIG_LOADS = new AtomicInteger();
    private static final AtomicInteger CLIENT_CONFIG_LOADS = new AtomicInteger();
    private static final AtomicInteger COMMAND_REGISTRATIONS = new AtomicInteger();
    private static final AtomicInteger RELOAD_LISTENER_REGISTRATIONS = new AtomicInteger();
    private static final AtomicInteger SERVER_STARTING_EVENTS = new AtomicInteger();
    private static final AtomicInteger SERVER_STARTED_EVENTS = new AtomicInteger();
    private static final AtomicInteger SERVER_STOPPING_EVENTS = new AtomicInteger();
    private static final AtomicInteger SERVER_STOPPED_EVENTS = new AtomicInteger();
    private static final AtomicInteger PLAYER_LOGINS = new AtomicInteger();
    private static final AtomicInteger PLAYER_LOGOUTS = new AtomicInteger();
    private static final AtomicInteger CLIENT_LOGOUTS = new AtomicInteger();
    private static final AtomicInteger TARGET_PACKET_STAGE = new AtomicInteger(-1);
    private static volatile String targetPacketSequence;
    private static final String ACTIVE_COMPANION = ActiveSableCompanion.class.getName();
    private static final String DEFAULT_COMPANION = DefaultSableCompanion.class.getName();

    static final class LifecycleBaseline {
        private final int starting;
        private final int started;
        private final int stopping;
        private final int stopped;
        private final int commands;
        private final int reloadListeners;
        private final int playerLogins;
        private final int playerLogouts;
        private final int clientLogouts;

        private LifecycleBaseline(
                final int starting,
                final int started,
                final int stopping,
                final int stopped,
                final int commands,
                final int reloadListeners,
                final int playerLogins,
                final int playerLogouts,
                final int clientLogouts) {
            this.starting = starting;
            this.started = started;
            this.stopping = stopping;
            this.stopped = stopped;
            this.commands = commands;
            this.reloadListeners = reloadListeners;
            this.playerLogins = playerLogins;
            this.playerLogouts = playerLogouts;
            this.clientLogouts = clientLogouts;
        }
    }

    private SableForgeRuntimeSmoke() {
    }

    static void installCommon(final IEventBus modBus) {
        if (!ENABLED || !COMMON_INSTALLED.compareAndSet(false, true)) {
            return;
        }

        verifyCompanionSelection();
        verifyCommonProviders();
        verifyPacketTransport();
        verifyPacketCatalog();

        modBus.<ModConfigEvent.Loading>addListener(SableForgeRuntimeSmoke::onConfigLoading);
        modBus.<ModConfigEvent.Reloading>addListener(SableForgeRuntimeSmoke::onConfigReloading);
        MinecraftForge.EVENT_BUS.<ServerStartingEvent>addListener(SableForgeRuntimeSmoke::onServerStarting);
        MinecraftForge.EVENT_BUS.<ServerStartedEvent>addListener(SableForgeRuntimeSmoke::onServerStarted);
        MinecraftForge.EVENT_BUS.<ServerStoppingEvent>addListener(SableForgeRuntimeSmoke::onServerStopping);
        MinecraftForge.EVENT_BUS.<ServerStoppedEvent>addListener(SableForgeRuntimeSmoke::onServerStopped);
        MinecraftForge.EVENT_BUS.<LevelEvent.Load>addListener(SableForgeRuntimeSmoke::onLevelLoad);
        MinecraftForge.EVENT_BUS.<LevelEvent.Unload>addListener(SableForgeRuntimeSmoke::onLevelUnload);
        MinecraftForge.EVENT_BUS.<PlayerEvent.PlayerLoggedInEvent>addListener(
                SableForgeRuntimeSmoke::onPlayerLogin);
        MinecraftForge.EVENT_BUS.<PlayerEvent.PlayerLoggedOutEvent>addListener(
                SableForgeRuntimeSmoke::onPlayerLogout);

        pass("bootstrap", "common runtime probe installed");
    }

    static void installClient() {
        if (!ENABLED || !CLIENT_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        requireProvider(
                "SableSubLevelRenderPlatform",
                SableSubLevelRenderPlatform.INSTANCE,
                "dev.ryanhcode.sable.forge.platform.SableSubLevelRenderPlatformImpl"
        );
        pass("bootstrap", "client runtime probe installed");
    }

    static void commonSetup() {
        pass("lifecycle", "FML common setup");
    }

    static void commandRegistered() {
        pass("lifecycle", "Sable command registered cycle=" + COMMAND_REGISTRATIONS.incrementAndGet());
    }

    static void reloadListenersRegistered() {
        pass("lifecycle", "server reload listeners registered cycle="
                + RELOAD_LISTENER_REGISTRATIONS.incrementAndGet());
    }

    static void dataPackSync(final int players) {
        pass("network", "datapack sync players=" + players);
    }

    static void clientReloadListenerRegistered() {
        pass("lifecycle", "client reload listener registered");
    }

    static void clientLogout() {
        pass("lifecycle", "client logout cleanup cycle=" + CLIENT_LOGOUTS.incrementAndGet());
    }

    public static <T extends SableTCPPacket> void packetThread(
            final String side,
            final SablePacketRegistration<T> registration,
            final boolean expectedThread) {
        if (!ENABLED) {
            return;
        }
        require(expectedThread, side + " packet ran off its main thread: "
                + registration.packetType().getName());
        recordTargetPacket(side, registration.packetType().getSimpleName());
        pass("network", side + " packet id=" + registration.id()
                + " type=" + registration.packetType().getSimpleName());
    }

    static void beginTargetPacketSequence(final String sequence) {
        require(TARGET_PACKET_STAGE.compareAndSet(-1, 0),
                "Packet sequence already active: " + targetPacketSequence);
        targetPacketSequence = sequence;
        pass("network", "target sequence started=" + sequence);
    }

    static void verifyTargetPacketSequence() {
        require(TARGET_PACKET_STAGE.compareAndSet(2, -1),
                "Target packet sequence did not reach StartTracking -> Finalize: "
                        + targetPacketSequence + " stage=" + TARGET_PACKET_STAGE.get());
        pass("network", "target sequence ordered StartTracking -> Finalize: " + targetPacketSequence);
        targetPacketSequence = null;
    }

    static LifecycleBaseline captureLifecycleBaseline(final String phase) {
        final LifecycleBaseline baseline = new LifecycleBaseline(
                SERVER_STARTING_EVENTS.get(),
                SERVER_STARTED_EVENTS.get(),
                SERVER_STOPPING_EVENTS.get(),
                SERVER_STOPPED_EVENTS.get(),
                COMMAND_REGISTRATIONS.get(),
                RELOAD_LISTENER_REGISTRATIONS.get(),
                PLAYER_LOGINS.get(),
                PLAYER_LOGOUTS.get(),
                CLIENT_LOGOUTS.get());
        pass("lifecycle", phase + " baseline captured starting=" + baseline.starting
                + " started=" + baseline.started
                + " stopping=" + baseline.stopping
                + " stopped=" + baseline.stopped
                + " commands=" + baseline.commands
                + " reloadListeners=" + baseline.reloadListeners
                + " playerLogins=" + baseline.playerLogins
                + " playerLogouts=" + baseline.playerLogouts
                + " clientLogouts=" + baseline.clientLogouts);
        return baseline;
    }

    static void verifyFirstIntegratedServerStopped(final LifecycleBaseline baseline) {
        verifyLifecycleDeltas(baseline, 0, 0, 1, 1, 0, 0, 0, 1, 1, false, "first server stopped");
    }

    static void verifySecondIntegratedServerActive(final LifecycleBaseline baseline) {
        verifyLifecycleDeltas(baseline, 1, 1, 0, 0, 1, 1, 1, 0, -1, true, "second server active");
    }

    static void verifySecondIntegratedServerStopped(final LifecycleBaseline baseline) {
        verifyLifecycleDeltas(baseline, 0, 0, 1, 1, 0, 0, 0, 1, 1, false, "second server stopped");
    }

    private static void recordTargetPacket(final String side, final String packetType) {
        if (TARGET_PACKET_STAGE.get() < 0 || !side.equals("client")) {
            return;
        }
        if (packetType.equals("ClientboundStartTrackingSubLevelPacket")) {
            require(TARGET_PACKET_STAGE.compareAndSet(0, 1),
                    "Duplicate or out-of-order StartTracking in " + targetPacketSequence);
        } else if (packetType.equals("ClientboundFinalizeSubLevelPacket")) {
            require(TARGET_PACKET_STAGE.compareAndSet(1, 2),
                    "Finalize arrived before StartTracking in " + targetPacketSequence);
        }
    }

    private static void verifyLifecycleDeltas(
            final LifecycleBaseline baseline,
            final int starting,
            final int started,
            final int stopping,
            final int stopped,
            final int commands,
            final int reloadListeners,
            final int playerLogins,
            final int playerLogouts,
            final int clientLogouts,
            final boolean active,
            final String phase) {
        require(baseline != null, phase + " lifecycle baseline was not captured");
        requireDelta(SERVER_STARTING_EVENTS.get(), baseline.starting, starting, phase, "starting events");
        requireDelta(SERVER_STARTED_EVENTS.get(), baseline.started, started, phase, "started events");
        requireDelta(SERVER_STOPPING_EVENTS.get(), baseline.stopping, stopping, phase, "stopping events");
        requireDelta(SERVER_STOPPED_EVENTS.get(), baseline.stopped, stopped, phase, "stopped events");
        requireDelta(COMMAND_REGISTRATIONS.get(), baseline.commands, commands, phase, "command registrations");
        requireDelta(RELOAD_LISTENER_REGISTRATIONS.get(), baseline.reloadListeners, reloadListeners,
                phase, "reload listener registrations");
        requireDelta(PLAYER_LOGINS.get(), baseline.playerLogins, playerLogins, phase, "player logins");
        requireDelta(PLAYER_LOGOUTS.get(), baseline.playerLogouts, playerLogouts, phase, "player logouts");
        if (clientLogouts >= 0) {
            requireDelta(CLIENT_LOGOUTS.get(), baseline.clientLogouts, clientLogouts, phase, "client logouts");
        }
        require(COMMON_CONFIG_LOADS.get() == 1 && CLIENT_CONFIG_LOADS.get() == 1,
                phase + " config loads common=" + COMMON_CONFIG_LOADS.get()
                        + " client=" + CLIENT_CONFIG_LOADS.get());
        require(COMMON_INSTALLED.get() && CLIENT_INSTALLED.get(),
                phase + " runtime probes common=" + COMMON_INSTALLED.get()
                        + " client=" + CLIENT_INSTALLED.get());
        require(SERVER_ACTIVE.get() == active,
                phase + " server active=" + SERVER_ACTIVE.get() + ", expected=" + active);
        pass("lifecycle", phase + " delta duplicate-registration audit passed");
    }

    private static void requireDelta(
            final int actual,
            final int baseline,
            final int expectedDelta,
            final String phase,
            final String label) {
        final int delta = actual - baseline;
        require(delta == expectedDelta,
                phase + " " + label + " delta=" + delta
                        + ", expected=" + expectedDelta
                        + " actual=" + actual
                        + " baseline=" + baseline);
    }

    private static void verifyCompanionSelection() {
        final List<Class<? extends SableCompanion>> candidates = ServiceLoader.load(SableCompanion.class)
                .stream()
                .map(ServiceLoader.Provider::type)
                .sorted(Comparator.comparing(Class::getName))
                .toList();
        final Class<? extends SableCompanion> activeType = candidates.stream()
                .filter(type -> type.getName().equals(ACTIVE_COMPANION))
                .findFirst()
                .orElseThrow(() -> failure("ActiveSableCompanion provider is missing"));
        final Class<? extends SableCompanion> defaultType = candidates.stream()
                .filter(type -> type.getName().equals(DEFAULT_COMPANION))
                .findFirst()
                .orElseThrow(() -> failure("DefaultSableCompanion provider is missing"));
        final int activePriority = priority(activeType);
        final int defaultPriority = priority(defaultType);

        require(activePriority == 1000, "ActiveSableCompanion priority is " + activePriority + ", expected 1000");
        require(defaultPriority == 500, "DefaultSableCompanion priority is " + defaultPriority + ", expected 500");
        for (final Class<? extends SableCompanion> candidate : candidates) {
            if (candidate != activeType) {
                require(activePriority > priority(candidate),
                        "ActiveSableCompanion is not the unique highest-priority provider: " + candidate.getName());
            }
        }
        require(SableCompanion.INSTANCE.getClass() == activeType,
                "Companion selected " + SableCompanion.INSTANCE.getClass().getName() + " instead of " + ACTIVE_COMPANION);
        pass("companion", "selected=" + ACTIVE_COMPANION + " activePriority=" + activePriority
                + " defaultPriority=" + defaultPriority + " candidates="
                + candidates.stream().map(Class::getName).toList());
    }

    private static int priority(final Class<? extends SableCompanion> type) {
        final SableCompanion.LoadPriority annotation = type.getAnnotation(SableCompanion.LoadPriority.class);
        return annotation != null ? annotation.value() : 1000;
    }

    private static void verifyCommonProviders() {
        requireProvider("SableAssemblyPlatform", SableAssemblyPlatform.INSTANCE,
                "dev.ryanhcode.sable.forge.platform.SableAssemblyPlatformImpl");
        requireProvider("SableChunkEventPlatform", SableChunkEventPlatform.INSTANCE,
                "dev.ryanhcode.sable.forge.platform.SableChunkEventPlatformImpl");
        requireProvider("SableEventPlatform", SableEventPlatform.INSTANCE,
                "dev.ryanhcode.sable.forge.platform.SableEventPlatformImpl");
        requireProvider("SableEventPublishPlatform", SableEventPublishPlatform.INSTANCE,
                "dev.ryanhcode.sable.forge.platform.SableEventPublishPlatformImpl");
        requireProvider("SableLoaderPlatform", SableLoaderPlatform.INSTANCE,
                "dev.ryanhcode.sable.forge.platform.SableLoaderPlatformImpl");
        requireProvider("SablePlatform", SablePlatform.INSTANCE,
                "dev.ryanhcode.sable.forge.platform.SablePlatformImpl");
        requireProvider("SablePlotPlatform", SablePlotPlatform.INSTANCE,
                "dev.ryanhcode.sable.forge.platform.SablePlotPlatformImpl");
    }

    private static void verifyPacketTransport() {
        final List<String> providers = ServiceLoader.load(SablePacketTransport.class)
                .stream()
                .map(provider -> provider.type().getName())
                .sorted()
                .toList();
        require(providers.equals(List.of("dev.ryanhcode.sable.forge.network.ForgeSablePacketTransport")),
                "Unexpected Sable packet transports: " + providers);
        pass("provider", "SablePacketTransport=" + providers.get(0));
    }

    private static void verifyPacketCatalog() {
        final List<SablePacketRegistration<?>> registrations = SableTCPPackets.registrations();
        require(registrations.size() == 14, "Expected 14 TCP registrations, found " + registrations.size());
        for (int id = 0; id < registrations.size(); id++) {
            require(registrations.get(id).id() == id,
                    "Expected TCP registration id " + id + ", found " + registrations.get(id).id());
        }
        pass("network", "protocol=" + SableTCPPackets.PROTOCOL_VERSION + " registrations=14 ids=0..13");
    }

    private static void requireProvider(final String service, final Object provider, final String expectedClass) {
        require(provider.getClass().getName().equals(expectedClass),
                service + " selected " + provider.getClass().getName() + " instead of " + expectedClass);
        pass("provider", service + "=" + expectedClass);
    }

    private static void onConfigLoading(final ModConfigEvent.Loading event) {
        if (!event.getConfig().getModId().equals(Sable.MOD_ID)) {
            return;
        }
        final AtomicInteger count = event.getConfig().getType() == ModConfig.Type.CLIENT
                ? CLIENT_CONFIG_LOADS
                : COMMON_CONFIG_LOADS;
        final int loads = count.incrementAndGet();
        require(loads == 1, "Config loaded more than once: " + event.getConfig().getFileName());
        pass("config", "loaded type=" + event.getConfig().getType()
                + " file=" + event.getConfig().getFileName());
    }

    private static void onConfigReloading(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getModId().equals(Sable.MOD_ID)) {
            pass("config", "reloaded type=" + event.getConfig().getType()
                    + " file=" + event.getConfig().getFileName());
        }
    }

    private static void onServerStarting(final ServerStartingEvent event) {
        require(SERVER_ACTIVE.compareAndSet(false, true), "A server started while another smoke server was active");
        pass("lifecycle", "server starting cycle=" + SERVER_STARTING_EVENTS.incrementAndGet()
                + " thread=" + Thread.currentThread().getName());
    }

    private static void onServerStarted(final ServerStartedEvent event) {
        require(SERVER_ACTIVE.get(), "Server started without a preceding starting event");
        pass("lifecycle", "server started cycle=" + SERVER_STARTED_EVENTS.incrementAndGet());
    }

    private static void onServerStopping(final ServerStoppingEvent event) {
        require(SERVER_ACTIVE.get(), "Server stopping without an active server");
        pass("lifecycle", "server stopping cycle=" + SERVER_STOPPING_EVENTS.incrementAndGet());
    }

    private static void onServerStopped(final ServerStoppedEvent event) {
        require(SERVER_ACTIVE.compareAndSet(true, false), "Server stopped without an active smoke server");
        pass("lifecycle", "server stopped cycle=" + SERVER_STOPPED_EVENTS.incrementAndGet());
    }

    private static void onLevelLoad(final LevelEvent.Load event) {
        if (event.getLevel() instanceof final Level level) {
            pass("lifecycle", "level load dimension=" + level.dimension().location()
                    + " side=" + (level.isClientSide ? "client" : "server"));
        }
    }

    private static void onLevelUnload(final LevelEvent.Unload event) {
        if (event.getLevel() instanceof final Level level) {
            pass("lifecycle", "level unload dimension=" + level.dimension().location()
                    + " side=" + (level.isClientSide ? "client" : "server"));
        }
    }

    private static void onPlayerLogin(final PlayerEvent.PlayerLoggedInEvent event) {
        pass("lifecycle", "player login cycle=" + PLAYER_LOGINS.incrementAndGet()
                + " player=" + event.getEntity().getScoreboardName());
    }

    private static void onPlayerLogout(final PlayerEvent.PlayerLoggedOutEvent event) {
        pass("lifecycle", "player logout cycle=" + PLAYER_LOGOUTS.incrementAndGet()
                + " player=" + event.getEntity().getScoreboardName());
    }

    private static void require(final boolean condition, final String message) {
        if (!ENABLED || condition) {
            return;
        }
        throw failure(message);
    }

    private static IllegalStateException failure(final String message) {
        Sable.LOGGER.error("SABLE_M6 phase=assertion status=FAIL {}", message);
        return new IllegalStateException("SABLE_M6 " + message);
    }

    private static void pass(final String phase, final String detail) {
        if (ENABLED) {
            Sable.LOGGER.info("SABLE_M6 phase={} status=PASS {}", phase, detail);
        }
    }
}
