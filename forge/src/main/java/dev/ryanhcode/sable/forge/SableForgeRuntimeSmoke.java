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
    private static final String ACTIVE_COMPANION = ActiveSableCompanion.class.getName();
    private static final String DEFAULT_COMPANION = DefaultSableCompanion.class.getName();

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
        pass("lifecycle", "Sable command registered");
    }

    static void reloadListenersRegistered() {
        pass("lifecycle", "server reload listeners registered");
    }

    static void dataPackSync(final int players) {
        pass("network", "datapack sync players=" + players);
    }

    static void clientReloadListenerRegistered() {
        pass("lifecycle", "client reload listener registered");
    }

    static void clientLogout() {
        pass("lifecycle", "client logout cleanup");
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
        pass("network", side + " packet id=" + registration.id()
                + " type=" + registration.packetType().getSimpleName());
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
        pass("lifecycle", "server starting thread=" + Thread.currentThread().getName());
    }

    private static void onServerStarted(final ServerStartedEvent event) {
        require(SERVER_ACTIVE.get(), "Server started without a preceding starting event");
        pass("lifecycle", "server started");
    }

    private static void onServerStopping(final ServerStoppingEvent event) {
        require(SERVER_ACTIVE.get(), "Server stopping without an active server");
        pass("lifecycle", "server stopping");
    }

    private static void onServerStopped(final ServerStoppedEvent event) {
        require(SERVER_ACTIVE.compareAndSet(true, false), "Server stopped without an active smoke server");
        pass("lifecycle", "server stopped");
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
        pass("lifecycle", "player login=" + event.getEntity().getScoreboardName());
    }

    private static void onPlayerLogout(final PlayerEvent.PlayerLoggedOutEvent event) {
        pass("lifecycle", "player logout=" + event.getEntity().getScoreboardName());
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
