# M24 Simulated System Matrix

Frozen Simulated upstream: `https://github.com/Creators-of-Aeronautics/Simulated-Project.git`
commit `9e60263fb5cb00033f14af655a7e72cf7aebb3e2` (`Simulated 1.3.0`,
Minecraft 1.21.1, NeoForge 21.1.228, Java 21, Create 6.0.10, Flywheel 1.0.6,
Sable 2.0.0, Sable Companion 1.6.0).

M21, M22, and M23 are frozen. M24 does not start Aeronautics and does not port
lift, propulsion, wings, aircraft controls, or Golden Aircraft gameplay.
Those systems are recorded as `DEFER_AERONAUTICS`.

| Family | Upstream registry IDs | Upstream implementation summary | Target M24 disposition |
| --- | --- | --- | --- |
| Torsion Spring | `simulated:torsion_spring` | `TorsionSpringBlockEntity` is a Create `KineticBlockEntity`/`ExtraKinetics` spring output with sequenced angle state. It is not a native Sable backend joint in the frozen source. | `RUNTIME_PROVEN`: one-body Create kinetic/ExtraKinetics behavior, signed angle limits, return/hold behavior, output motion, and dynamic rendering are accepted. |
| Swivel Bearing | `simulated:swivel_bearing`, `simulated:swivel_bearing_link_block` | `SwivelBearingBlockEntity` assembles a plate sublevel and attaches a Sable `RotaryConstraintConfiguration`, then drives `RotaryConstraintHandle.DEFAULT_AXIS` with a motor. | `RUNTIME_PROVEN`: stable active Sable-to-Sable Rotary constraint with the intended hinge behavior and no body loss. |
| Rope Connector | `simulated:rope_connector`, `simulated:rope_coupling` | Connector endpoint participates in rope ownership through Simulated rope graph and Sable rope object APIs. | `RUNTIME_PROVEN`: stable physical Rope relationship and visible connection rendering. |
| Rope Winch | `simulated:rope_winch` | Winch endpoint controls rope segment length through the Sable rope handle. | `RUNTIME_PROVEN`: real Create kinetic RPM changes rope length correctly, including stop and reverse. |
| Docking Connector | `simulated:docking_connector`, `simulated:paired_docking_connector` | `DockingConnectorBlockEntity` pairs through magnet/docking maps, attaches a Sable `FixedConstraintConfiguration`, and also contains inventory/fluid/energy/CC transfer surfaces. | `RUNTIME_PROVEN` for FixedConstraint and lifecycle: real redstone falling-edge disconnect, no immediate re-pair, and post-disconnect disassembly. Transfer surfaces remain `STRUCTURAL_ONLY`. |
| Altitude Sensor | `simulated:altitude_sensor` | Onboard sensor block entity reads vehicle/world state and exposes redstone/display signal. | `ADAPT_NOW`: visible-coordinate altitude signal from Sable logical pose. |
| Velocity Sensor | `simulated:velocity_sensor` | Onboard sensor block entity reads body velocity and exposes redstone/display signal. | `ADAPT_NOW`: backported through `RigidBodyHandle.getLinearVelocity`. |
| Optical Sensor | `simulated:optical_sensor` | Onboard sensor/ray surface used by Simulated/Aeronautics control stacks. | `ADAPT_NOW`: visible-coordinate block raycast signal, no Aeronautics control coupling. |
| Steering Wheel | `simulated:steering_wheel` | Onboard input/control block for later vehicle/Aeronautics systems. | `STRUCTURAL_ONLY`: registered input block with persisted enabled state and diagnostics; no aircraft control authority. |

Backend map:

- Rotary: `RotaryConstraintConfiguration` + `RotaryConstraintHandle`.
- Fixed: `FixedConstraintConfiguration` + `FixedConstraintHandle` via the
  common `PhysicsConstraintHandle` contract; docking does not merge bodies.
- Rope: `RopePhysicsObject` + `RopeHandle`.
- Torsion/onboard controls: no new Sable backend constraint invented.
- Docking transfer: STRUCTURAL_ONLY for transfer surfaces in M24; inventory,
  fluid, energy, and ComputerCraft transfer behavior is not presented as
  runtime-complete.

Final status after manual M24.12 acceptance: `M24 CLOSED / RUNTIME_PROVEN`.
M21-M24 are frozen as the completed Simulated/Sable foundation; Aeronautics
remains deferred to M25.
