# Forge 1.20.1 Access Transformer Provenance

The upstream common AT remains unchanged. ForgeGradle 6 consumes SRG member names, so retained entries are mapped through `forge/build/createMcpToSrg/output.tsrg` into `forge/src/main/resources/META-INF/accesstransformer-forge.cfg`. `verifyForgeAccessTransformer` checks the mapped class file, descriptor, access flags, and this provenance inventory.

| Upstream target | Dependent source | Purpose | Exact 1.20.1 mapping or deferral proof | Final action |
|---|---|---|---|---|
| `public net.minecraft.client.renderer.block.ModelBlockRenderer$Cache` | dynamic directional shading Mixins | Renderer cache extension | Owning Mixins are M5-deferred. | REMOVE_DEFERRED |
| `public net.minecraft.server.level.ChunkHolder entityTickingChunkFuture` | `PlotChunkHolder` | Install an already-loaded plot chunk future | `f_140004_` with the 1.20 `Either` future contract. | RETAIN_SRG |
| `public net.minecraft.server.level.ChunkHolder tickingChunkFuture` | `PlotChunkHolder` | Install ticking future | `f_140003_`; populated through `SableChunkFutures`. | RETAIN_SRG |
| `public net.minecraft.server.level.ChunkHolder fullChunkFuture` | `PlotChunkHolder` | Install full future | `f_140002_`; populated through `SableChunkFutures`. | RETAIN_SRG |
| `public net.minecraft.client.renderer.block.BlockRenderDispatcher modelRenderer` | Forge `SableSubLevelRenderPlatformImpl` | Basic single-block tessellation | `f_110900_`; direct retained client call. | RETAIN_SRG |
| `public net.minecraft.client.renderer.block.ModelBlockRenderer$AmbientOcclusionFace` | directional shading Mixin | Replace directional shading data | Owning advanced shader cluster is deferred. | REMOVE_DEFERRED |
| `public net.minecraft.server.level.ServerChunkCache$MainThreadExecutor` | former chunk accelerator code | Reach nested executor | No selected M5 source references the type. | REMOVE_UNUSED |
| `public net.minecraft.client.renderer.LevelRenderer cullingFrustum` | Sodium renderer bridge | Read renderer frustum | Sodium/chunked rendering is deferred. | REMOVE_DEFERRED |
| `public net.minecraft.world.entity.projectile.AbstractHurtingProjectile accelerationPower` | explosion projectile handling | Clear acceleration | 1.20 has `xPower/yPower/zPower`; selected source now writes those public fields. | REMOVE_PORTED |
| `public net.minecraft.world.entity.projectile.Projectile Projectile(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V` | upstream projectile subclasses | Constructor access | No selected source invokes this malformed/obsolete constructor target. | REMOVE_UNUSED |
| `public net.minecraft.server.level.ServerChunkCache$ChunkAndHolder` | 1.21 chunk-loading path | Access result pair | Type is absent from the selected 1.20 path; `SableChunkFutures` uses `Either`. | REMOVE_PORTED |
| `public net.minecraft.server.level.ServerChunkCache$ChunkAndHolder <init>(Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/server/level/ChunkHolder;)V` | 1.21 chunk-loading path | Construct result pair | Same replacement proof as the class row. | REMOVE_PORTED |
| `public-f net.minecraft.client.renderer.chunk.SectionRenderDispatcher bufferPool` | chunked sublevel renderer | Replace section buffers | Chunked renderer is deferred. | REMOVE_DEFERRED |
| `public-f net.minecraft.world.entity.ai.attributes.AttributeSupplier instances` | Forge `SableAttributes` replacement | Add Sable player attributes to vanilla supplier | `f_22241_`; direct retained write and final removal. | RETAIN_SRG |
| `public net.minecraft.server.level.ServerPlayer findRespawnAndUseSpawnBlock(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;FZZ)Ljava/util/Optional;` | respawn Mixins | Resolve custom respawn | Port uses public 1.20 `Player.findRespawnPositionAndUseSpawnBlock`. | REMOVE_PORTED |
| `public net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection updateGlobalBlockEntities(Ljava/util/Collection;)V` | chunked sublevel renderer | Update global block entities | Chunked renderer is deferred. | REMOVE_DEFERRED |
| `public net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection setCompiled(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$CompiledSection;)V` | chunked sublevel renderer | Install compiled section | Chunked renderer is deferred. | REMOVE_DEFERRED |
| `public net.minecraft.world.entity.Entity removalReason` | former entity transfer path | Inspect removal state | Selected code uses public `isRemoved`/`getRemovalReason`; Mixin shadows need no AT. | REMOVE_PORTED |
| `public net.minecraft.world.entity.Entity levelCallback` | `ServerLevelPlot` | Remove transferred entities from parent callback | `f_146801_`; direct retained access. | RETAIN_SRG |
| `public-f net.minecraft.world.entity.Entity getEyePosition(F)Lnet/minecraft/world/phys/Vec3;` | optional Exposure/entity rendering overrides | Override final interpolation method | Optional/advanced rendering owners are deferred; retained code only calls the public method. | REMOVE_DEFERRED |
| `public-f net.minecraft.world.entity.Entity setRemoved(Lnet/minecraft/world/entity/Entity$RemovalReason;)V` | former entity transfer override | Override removal | No selected source overrides or directly invokes the protected method. | REMOVE_UNUSED |
| `public net.minecraft.server.level.ChunkLevel ENTITY_TICKING_LEVEL` | `PlotChunkHolder` | Construct entity-ticking holder | `f_286937_`. | RETAIN_SRG |
| `public net.minecraft.server.level.ChunkLevel BLOCK_TICKING_LEVEL` | plot chunk lifecycle | Compare block-ticking level | `f_286976_`; retained selected lifecycle graph. | RETAIN_SRG |
| `public net.minecraft.world.entity.decoration.HangingEntity calculateSupportBox()Lnet/minecraft/world/phys/AABB;` | hanging entity collision | Obtain support bounds | Port uses public `getBoundingBox`. | REMOVE_PORTED |
| `public net.minecraft.world.entity.LivingEntity dismountVehicle(Lnet/minecraft/world/entity/Entity;)V` | riding Mixins | Custom dismount placement | Selected 1.20 code computes placement through its own helper and public APIs. | REMOVE_PORTED |
| `public net.minecraft.world.level.lighting.LevelLightEngine skyEngine` | `ServerLevelPlot` | Mirror parent light-engine capabilities and load sky data | `f_75803_`; direct retained access. | RETAIN_SRG |
| `public net.minecraft.world.level.lighting.LevelLightEngine blockEngine` | `ServerLevelPlot` | Mirror parent light-engine capabilities and load block data | `f_75802_`; direct retained access. | RETAIN_SRG |
| `public net.minecraft.server.level.ThreadedLevelLightEngine runUpdate()V` | former light scheduling path | Force light update | No selected source calls it. | REMOVE_UNUSED |
| `public net.minecraft.server.level.ThreadedLevelLightEngine updateChunkStatus(Lnet/minecraft/world/level/ChunkPos;)V` | former light scheduling path | Update light chunk status | No selected source calls it. | REMOVE_UNUSED |
| `public net.minecraft.client.renderer.block.ModelBlockRenderer CACHE` | directional shading Mixins | Reach renderer cache thread-local | Advanced directional shading is deferred. | REMOVE_DEFERRED |
| `public net.minecraft.server.network.PlayerChunkSender sendChunk(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;)V` | full-sync networking | Send plot chunk | M4 constructs and orders vanilla chunk/light packets directly. | REMOVE_PORTED |
| `public net.minecraft.server.network.ServerCommonPacketListenerImpl connection` | old Veil packet context | Reach player connection | M4 transport/context replacement removed the caller. | REMOVE_PORTED |
| `public net.minecraft.client.multiplayer.ClientChunkCache$Storage` | loaded-chunk debug accessor | Inspect client storage | Loaded-chunk debug cluster is deferred. | REMOVE_DEFERRED |
| `public net.minecraft.network.Connection channel` | old packet/UDP bridge | Reach Netty channel | M4 transport and retained UDP Mixins no longer access the field. | REMOVE_PORTED |
| `public net.minecraft.client.renderer.RenderStateShard name` | shader preprocessors | Build shader key | Advanced shader cluster is deferred. | REMOVE_DEFERRED |
| `public net.minecraft.client.renderer.ShaderInstance samplerLocations` | shader preprocessors | Rewrite sampler bindings | Advanced shader cluster is deferred. | REMOVE_DEFERRED |
| `public net.minecraft.world.level.entity.PersistentEntitySectionManager sectionStorage` | `ServerLevelPlot` | Enumerate and transfer plot entities | `f_157495_`; direct retained access. | RETAIN_SRG |
| `public net.minecraft.world.level.storage.DimensionDataStorage dataFolder` | `SubLevelHoldingChunkMap` | Locate Sable storage beside dimension data | `f_78146_`; direct retained access. | RETAIN_SRG |
| `public net.minecraft.client.renderer.GameRenderer getFov(Lnet/minecraft/client/Camera;FZ)D` | modern camera/zoom Mixins | Compute alternate-camera FOV | Settings/new-camera cluster is deferred. | REMOVE_DEFERRED |
| `public-f net.minecraft.server.level.GenerationChunkHolder rescheduleChunkTask(Lnet/minecraft/server/level/ChunkMap;Lnet/minecraft/world/level/chunk/status/ChunkStatus;)V` | `PlotChunkHolder` 1.21 lifecycle | Reschedule generation task | `GenerationChunkHolder` and status package are absent in 1.20; selected plot holder installs completed 1.20 futures and never reschedules. | REMOVE_PORTED |
| `public net.minecraft.client.particle.Particle zo` | selected particle projection | Transform previous Z | `f_107211_`. | RETAIN_SRG |
| `public net.minecraft.client.particle.Particle yo` | selected particle projection | Transform previous Y | `f_107210_`. | RETAIN_SRG |
| `public net.minecraft.client.particle.Particle xo` | selected particle projection | Transform previous X | `f_107209_`. | RETAIN_SRG |
| `public net.minecraft.client.particle.Particle x` | selected particle projection | Transform current X | `f_107212_`. | RETAIN_SRG |
| `public net.minecraft.client.particle.Particle y` | selected particle projection | Transform current Y | `f_107213_`. | RETAIN_SRG |
| `public net.minecraft.client.particle.Particle zd` | selected particle projection | Transform Z velocity | `f_107217_`. | RETAIN_SRG |
| `public net.minecraft.client.particle.Particle z` | selected particle projection | Transform current Z | `f_107214_`. | RETAIN_SRG |
| `public net.minecraft.client.particle.Particle yd` | selected particle projection | Transform Y velocity | `f_107216_`. | RETAIN_SRG |
| `public net.minecraft.client.particle.Particle xd` | selected particle projection | Transform X velocity | `f_107215_`. | RETAIN_SRG |
| `public net.minecraft.world.level.ClipContext collisionContext` | `BlockGetterMixin` | Preserve entity collision semantics when transforming rays | `f_45686_`; copied via 1.20 `EntityCollisionContext.getEntity`. | RETAIN_SRG |
| `public net.minecraft.world.level.ClipContext fluid` | `BlockGetterMixin` | Preserve fluid ray mode | `f_45685_`. | RETAIN_SRG |
| `public net.minecraft.world.level.ClipContext block` | `BlockGetterMixin` | Preserve block ray mode | `f_45684_`. | RETAIN_SRG |
| `public net.minecraft.server.level.ChunkMap$DistanceManager` | selected entity ticking Mixin | Use nested range tracker as an injection argument | Exact class exists in mapped 1.20.1; class access is widened. | RETAIN_CLASS |
| `public net.minecraft.server.level.ServerPlayer$RespawnPosAngle` | respawn Mixins | Carry respawn position/angle | 1.20 port uses `Optional<Vec3>` plus queued angle and public Player helper. | REMOVE_PORTED |
| `public net.minecraft.server.level.ServerPlayer$RespawnPosAngle <init>(Lnet/minecraft/world/phys/Vec3;F)V` | respawn Mixins | Construct respawn result | Same 1.20 respawn replacement proof. | REMOVE_PORTED |
| `public net.minecraft.server.level.ChunkMap unloadQueue` | serialization Mixin | Replace queue implementation | Access occurs inside a Mixin shadow; Mixin handles private/final access. | REMOVE_MIXIN_SHADOW |
| `public net.minecraft.server.level.ChunkMap toDrop` | former chunk lifecycle code | Inspect drop set | No selected source references the field. | REMOVE_UNUSED |
| `public net.minecraft.server.level.ChunkMap updatingChunkMap` | `ServerLevelPlot` | Add/remove synthetic plot holders | `f_140129_`; direct retained access. | RETAIN_SRG |
| `public net.minecraft.server.level.ChunkMap visibleChunkMap` | `SubLevelHoldingChunkMap` | Find retained visible holder | `f_140130_`; direct retained access. | RETAIN_SRG |
| `public net.minecraft.server.level.ChunkMap modified` | `ServerLevelPlot` | Publish map mutation | `f_140140_`; direct retained write. | RETAIN_SRG |
| `public net.minecraft.server.level.ChunkMap chunkSaveCooldowns` | former save scheduling | Control save cooldown | No selected source references the field. | REMOVE_UNUSED |
| `public net.minecraft.server.level.ChunkMap scheduleUnload(JLnet/minecraft/server/level/ChunkHolder;)V` | former plot unload path | Schedule holder unload | No selected source directly invokes it. | REMOVE_UNUSED |
| `public net.minecraft.server.level.ChunkMap onFullChunkStatusChange(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/server/level/FullChunkStatus;)V` | `ServerLevelPlot` | Publish synthetic holder status | `m_287285_` with the exact 1.20 descriptor. | RETAIN_SRG |
| `public net.minecraft.server.level.ChunkMap getChunks()Ljava/lang/Iterable;` | loaded-chunk debug | Enumerate holders | Loaded-chunk debug is deferred. | REMOVE_DEFERRED |
| `public net.minecraft.server.level.ChunkMap saveChunkIfNeeded(Lnet/minecraft/server/level/ChunkHolder;)Z` | plot save Mixin | Cancel vanilla save for plot holders | Used only as a Mixin injection target; no AT is required. | REMOVE_MIXIN_TARGET |
| `public net.minecraft.server.level.ServerLevel entityManager` | `ServerLevelPlot` | Update plot entity chunk status | `f_143244_`; direct retained access. | RETAIN_SRG |
| `public net.minecraft.server.level.Ticket <init>(Lnet/minecraft/server/level/TicketType;ILjava/lang/Object;)V` | `PhysicsChunkTicketManager` | Construct UUID physics ticket | Exact 1.20 constructor retained. | RETAIN_DESCRIPTOR |
| `public net.minecraft.server.level.DistanceManager tickingTicketsTracker` | `PhysicsChunkTicketManager` | Keep ticking tracker synchronized | `f_183901_`; direct retained access. | RETAIN_SRG |
| `public net.minecraft.server.level.DistanceManager addTicket(JLnet/minecraft/server/level/Ticket;)V` | `PhysicsChunkTicketManager` | Add exact ticket instance | `m_140784_` with exact 1.20 descriptor. | RETAIN_SRG |
| `public net.minecraft.server.level.DistanceManager removeTicket(JLnet/minecraft/server/level/Ticket;)V` | `PhysicsChunkTicketManager` | Remove exact ticket instance | `m_140818_` with exact 1.20 descriptor. | RETAIN_SRG |

## Forge-Only Additions

| Forge target | Dependent source | Purpose | Evidence | Final action |
|---|---|---|---|---|
| `public net.minecraft.server.level.DistanceManager` | `PhysicsChunkTicketManager` | Name the package-private 1.20 manager type used by `ChunkMap.getDistanceManager()` | Both top-level `DistanceManager` and nested `ChunkMap$DistanceManager` exist in the mapped Forge jar and serve distinct selected callers. | RETAIN_CLASS |

