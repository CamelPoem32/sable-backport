# M24.6 Client Visual Matrix

Baseline: frozen Simulated `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`.

| Family | Frozen upstream visual path | Target M24.6 visual path | Classification |
| --- | --- | --- | --- |
| Spring | Dedicated Spring block/entity visual already ported in M23 | Unchanged | UPSTREAM_DYNAMIC_RENDERER |
| Torsion Spring | `TorsionSpringVisual` Flywheel visual | M24.12 BER fallback with eagerly registered `simulated:block/torsion_spring/spring` partial and frozen textures | UPSTREAM_DYNAMIC_RENDERER, RUNTIME_PROVEN |
| Swivel Bearing | `SwivelBearingVisual` plus `SwivelBearingRenderer` for cog/shaft | M24 connection BE renderer registered for bearing/link relationship; static model remains | UPSTREAM_DYNAMIC_RENDERER |
| Swivel Bearing Link Block | `OrientedRotatingVisual.of(SHAFT_SIXTEENTH)` plus `SwivelBearingPlateBlockRenderer` | M24 connection BE renderer registration; follower does not duplicate the connection line | UPSTREAM_DYNAMIC_RENDERER |
| Rope Connector | `RopeConnectorRenderer` and `RopeStrandRenderer` render attached rope/knot | M24 connection BE renderer draws the rope endpoint connection from client Sable render poses | UPSTREAM_CONNECTION_VISUAL |
| Rope Winch | Rope endpoint/strand visual through the rope holder path | M24 connection BE renderer shares rope endpoint rendering | UPSTREAM_CONNECTION_VISUAL |
| Docking Connector | Fixed connector block/entity; no separate backend rewrite in M24.6 | M24 connection BE renderer shows the fixed connector relation | UPSTREAM_STATIC_MODEL_ONLY |
| Paired Docking Connector | Paired connector block/entity | M24 connection BE renderer shows the fixed connector relation | UPSTREAM_STATIC_MODEL_ONLY |
| Steering Wheel | `SteeringWheelVisual` Flywheel visual | Static/onboard block; Aeronautics control remains deferred | UPSTREAM_DYNAMIC_RENDERER, AERONAUTICS_DEFERRED |
| Altitude Sensor | Static/onboard sensor block entity | Static model and existing visible-coordinate diagnostics | UPSTREAM_STATIC_MODEL_ONLY |
| Velocity Sensor | Static/onboard sensor block entity | Static model and existing visible-coordinate diagnostics | UPSTREAM_STATIC_MODEL_ONLY |
| Optical Sensor | Static/onboard sensor block entity | Static model and existing visible-coordinate diagnostics | UPSTREAM_STATIC_MODEL_ONLY |

## Visible-Space Rule

The M24.6 renderer never renders a connection by translating to hidden plot
coordinates. Each endpoint is transformed independently:

1. endpoint raw plot coordinate
2. owning client sublevel `renderPose(partialTick)`
3. visible world endpoint
4. owner block visible position
5. inverse owner render orientation to obtain a small BE-local render vector

That keeps the `PoseStack` in the same camera-relative Sable block-entity frame
already used by the vanilla sublevel BE renderer.
