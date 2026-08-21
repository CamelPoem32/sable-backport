# Sable Networking Backport Matrix

## Transport Boundary

M4 replaces the selected Sable 2.0.0 Veil/Minecraft 1.21 packet surface with Sable-owned contracts. `SablePacketCodec` serializes production packet records through Minecraft 1.20.1 `FriendlyByteBuf`; `SablePacketTransport` is loaded lazily through the existing `ServiceLoader` convention. Forge provides `ForgeSablePacketTransport` over a direction-bound `SimpleChannel` at `sable:main`, protocol version `1`.

`ForgeSablePacketDispatcher` rejects a direction mismatch before context creation, queues accepted work exactly once, and marks the Forge packet handled. Client context creation remains in the Dist-safe Forge client helper. Serverbound dispatch requires a non-null `ServerPlayer`. `SableTCPPackets.toClientboundVanillaPacket` owns the single checked `SimpleChannel.toVanillaPacket` cast used by mixed Sable/vanilla bundles.

No fake `RegistryFriendlyByteBuf`, `StreamCodec`, `ByteBufCodecs`, `CustomPacketPayload`, `VeilPacketManager`, or `PacketContext` compatibility types exist.

## TCP Catalog

The authoritative production catalog is `SableTCPPacketCatalog`. Handler attachment is separate in `SableTCPPackets`, allowing `:forge:networkTest` to compile and test the exact production packet definitions independently of the broader source frontier.

| ID | Packet | Direction | Wire order | Codec | Handler/thread | Role |
|---:|---|---|---|---|---|---|
| 0 | `ClientboundSableSnapshotDualPacket` | Clientbound | `int` tick, VarInt count, repeated `long` plot + Pose + 3 linear floats + 3 angular floats | Manual `FriendlyByteBuf` | Interpolation snapshot update, client main | Core movement; follows info |
| 1 | `ClientboundSableSnapshotInfoDualPacket` | Clientbound | `int` elapsed, `int` tick, `boolean` stopped | Manual | Interpolation state update, client main | Core movement; precedes snapshot |
| 2 | `ClientboundStopMovingSubLevelPacket` | Clientbound | `long` plot | Manual | Stop interpolation, client main | Core |
| 3 | `ClientboundChangeSubLevelNamePacket` | Clientbound | UUID, presence boolean, optional UTF | Manual | Rename sublevel, client main | Core |
| 4 | `ClientboundStartTrackingSubLevelPacket` | Clientbound | `long` plot, UUID, last Pose, current Pose, Bounds, optional UTF, `int` tick | Manual | Allocate client sublevel, client main | Core; before chunk/light packets |
| 5 | `ClientboundFinalizeSubLevelPacket` | Clientbound | `long` plot | Manual | Finalize client world/render state, client main | Core; after chunk/light packets |
| 6 | `ClientboundStopTrackingSubLevelPacket` | Clientbound | `long` plot | Manual | Remove client sublevel, client main | Core |
| 7 | `ClientboundChangeBoundsSubLevelPacket` | Clientbound | `long` plot, six bounds ints | Manual | Update plot bounds, client main | Core |
| 8 | `ClientboundFreezePlayerPacket` | Clientbound | UUID, 3 doubles | Manual | Apply freeze state, client main | Core |
| 9 | `ClientboundPhysicsPropertyPacket` | Clientbound | Physics definition through its DFU `Codec` and NBT ops | `SablePacketCodec.fromCodec` | Apply synchronized definition, client main | Core data sync |
| 10 | `ClientboundFloatingBlockMaterialPacket` | Clientbound | ResourceLocation, material through its DFU `Codec` and NBT ops | Manual + `fromCodec` | Install synchronized material, client main | Core data sync |
| 11 | `ClientboundRecentlySplitSubLevelPacket` | Clientbound | child UUID, parent UUID, Pose | Manual | Establish split relationship, client main | Core; after Start and before chunks |
| 12 | `ClientboundSableUDPActivationPacket` | Clientbound | UUID | Manual | Queue UDP authentication write on Netty event loop | Optional core UDP transport |
| 13 | `ServerboundPunchSubLevelPacket` | Serverbound | BlockPos, 3 position doubles, 3 direction doubles | Manual | Validate/apply impulse, server main | Core interaction |

Pose wire format remains 3 position doubles, 4 quaternion floats, and 3 rotation-point doubles. Pose scale is intentionally not transmitted, matching upstream behavior. Bounds remain six signed ints.

The two gizmo packets remain source-set excluded and do not appear in the 0-13 catalog.

## UDP Catalog

UDP uses `FriendlyByteBuf` payloads and validates `PacketFlow` in both encoder and decoder. Unknown IDs, direction mismatches, malformed payloads, and trailing bytes throw codec errors rather than entering a handler.

| Ordinal | Packet | Direction | Wire order | Handler/thread | Role |
|---:|---|---|---|---|---|
| 0 | `SableUDPEchoPacket` | Clientbound | UTF | Client tick queue | Debug, retained for ordinal stability |
| 1 | `ClientboundSableSnapshotDualPacket` | Clientbound | Same as TCP 0 | Client tick queue | Core movement |
| 2 | `ClientboundSableSnapshotInfoDualPacket` | Clientbound | Same as TCP 1 | Client tick queue | Core movement |
| 3 | `SableUDPAuthenticationPacket` | Serverbound | UUID encoded as UTF | Server UDP event loop | Optional core transport |
| 4 | `SableUDPClientboundKeepAlivePacket` | Clientbound | Empty | Client tick queue, then UDP event loop | Optional core transport |
| 5 | `SableUDPServerboundAlivePacket` | Serverbound | Empty | Server UDP event loop | Optional core transport |

`ProtocolSwapHandler`, `BandwidthDebugMonitor`, and `MonitorFrameDecoder` are no longer used. Normal UDP pipelines use the Minecraft 1.20.1 no-argument `Varint21FrameDecoder`; in-memory pipelines omit frame splitting/prepending.

## Ordering And Sinks

- Full sublevel sync remains: Start, optional RecentlySplit, vanilla chunk/light packets in their existing iteration order, Finalize.
- TCP snapshot fallback remains a two-packet bundle: SnapshotInfo, then Snapshot.
- `SablePacketSink` replaces Veil packet sinks without changing iteration order for synchronized physics/material data.
- UDP packet ordinals and TCP registration IDs are explicit and tested for uniqueness and direction.

## Validation

`:forge:networkTest` independently compiles production packet records/codecs and real Sable/Companion model types. Its 12 tests cover:

- Round trips for all 14 TCP registrations and all 6 UDP types with no unread payload bytes.
- Golden byte order for Snapshot, StartTracking, Punch, nullable names, physics definitions, and floating materials.
- Exact TCP IDs/directions, UDP ordinals/directions, and absence of gizmo registrations.
- Deferred execution, exactly-once enqueue/invocation, handled state, context propagation, missing context/sender suppression, and wrong-direction rejection.
- UDP invalid ID, wrong direction, trailing bytes, malformed payload, and encoder direction rejection through Netty embedded channels.

## Deferred Lifecycle Mixins

`DisconnectionDetails` and `CommonListenerCookie` are not part of the transport abstraction. Their 1.20.1 equivalents alter Mixin descriptors and surrounding control flow:

- `Connection.disconnect(Component)` replaces the newer `DisconnectionDetails` form.
- `PlayerList.placeNewPlayer(Connection, ServerPlayer)` lacks the newer `CommonListenerCookie` parameter.

Those Mixins remain visible for the Minecraft/Mixin milestone. M4 validates UDP framing and transport contracts but does not claim runtime UDP startup or disconnect cleanup.
