# M24.11 Docking Re-pair Lifecycle Audit

Authority: frozen Simulated commit `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`.

## Frozen lifecycle

`DockingConnectorBlock.neighborChanged` transfers the real neighbor signal into
the connector's `POWERED` state. `DockingConnectorBlockEntity.tick` chases its
extension toward one only while powered and calls `searchForPairs` only when
`isExtended()` is true. That predicate requires both full extension and
`powered=true`.

On a powered-to-unpowered transition, `updateState` invokes
`DockingConnectorPair.unDock` symmetrically. Each endpoint clears its explicit
partner, removes its Fixed handle, disconnects transferred capabilities, and
enters `UNPOWERED`. Retraction also removes that connector's generated
`paired_docking_connector` extension proxy. The proxy is not a replacement
blockstate for the real connector and is not itself the second logical docking
endpoint.

Consequently, two aligned connectors cannot immediately pair after one retracts:
the unpowered endpoint is no longer magnet-active. A real rising signal must
extend it again; once fully extended, normal proximity/orientation discovery can
redock it. No cooldown or permanent pair blacklist exists upstream.

## Target defect and adaptation

The M24.10 generic `findPartner` path scanned every compatible loaded endpoint on
every actor tick. It did not consult Docking power/extension eligibility. After
the falling-edge teardown cleared both logical endpoints, the unchanged geometry
was therefore enough to recreate the same relation immediately.

M24.11 gives Docking a persisted backend-neutral `dockingPairEligible` lifecycle.
The six-block fixture replaces one decorative payload block with a real redstone
source, so both test endpoints begin powered without changing selection size.
Only the primary `docking_connector` owns discovery; the target's
`paired_docking_connector` remains its passive fixture counterpart. Pairing now
requires both endpoints to be powered/eligible, opposing, and geometrically
valid. Falling power disables the retracting endpoint before symmetric teardown.
A later real rising edge rearms it.

Disconnected eligibility is serialized independently from transient backend
handles. Reloading an unpowered disconnected endpoint cannot resurrect its old
relation; re-powering performs the normal rearm and discovery path.

The target fixture's two payload endpoint block IDs remain unchanged during
teardown. Symmetry applies to explicit partner identity, active-constraint
ownership, and backend removal. This is intentional: frozen upstream does not
convert a real connector into the paired proxy; it creates/removes the proxy in
front of the real connector.
