# M27 Aerodynamics Port Matrix

Authority: `Creators-of-Aeronautics/Simulated-Project` at
`9e60263fb5cb00033f14af655a7e72cf7aebb3e2`.

## Selected Graph

| Classification | Java owners | Resource paths | Result |
| --- | ---: | ---: | --- |
| `ADAPT_M27_AERODYNAMICS` | 3 | 7 | Sable provider retained, Create sail mixin restored, white symmetric sail ported |
| `ADAPT_M27_CONTROL_SURFACES` | 1 | 0 | M27 authoritative fixture/diagnostic harness using target Create bearing APIs |
| `DEFER_M28_GOLDEN_AIRCRAFT` | 2 | 2 | Full Ponder aircraft/rudder scene and placement-helper UX |
| `NOT_APPLICABLE_1_20_1` | 0 | 0 | No selected gameplay owner discarded |
| **Selected M27 total** | **4** | **7** | **11 paths** |

The three aerodynamic Java owners are
`BlockSubLevelLiftProvider`, `SailBlockMixin`, and `SymmetricSailBlock`.
`M27AerodynamicsCommands` is the fourth selected Java owner and is test harness
only. The seven resource paths are the white symmetric-sail blockstate, shared
block model, registered-ID block-model alias, shared item model, white item
model, white side texture, and white-sail conversion recipe.

## Adaptations

- The frozen NeoForge-only Create sail mixin is moved into the common mixin
  graph so Forge can apply it to Create 6.0.8.
- The selected `SymmetricSailBlock` retains its aerodynamic interface and
  scalars while deferring dye propagation, placement-helper convenience,
  special schematic requirements, and bounce UX.
- The exact frozen white texture and shared block/item model graph are retained;
  axis variants are adapted to the target 1.20.1 blockstate format.
- Control composition uses target Create 6.0.8's public
  `MechanicalBearingBlockEntity.assemble()` and a real Creative Motor speed.
  No command applies force, torque, velocity, or Sable pose changes.

## Deferred

M28 owns polished aircraft integration, all color variants, Ponder scenes,
aircraft UX, and any cockpit/controller layer. M27 adds no custom packet and no
new aerodynamic block entity because the frozen selected graph needs neither.

M27.2 changes only the command-owned fixture topology, diagnostics, and safe
stow preview. Production aerodynamic, propulsion, lift, and Rapier owners are
unchanged.

Manual runtime qualification proved static and bearing-carried aerodynamic
providers, corrected normal changes, isolated payload ownership, propelled
flight, reload identity, normal payload return, and lossless Physics Assembler
disassembly. Final status: `M27 CLOSED / RUNTIME_PROVEN`.
