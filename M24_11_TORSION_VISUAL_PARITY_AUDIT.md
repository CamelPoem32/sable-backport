# M24.11 Torsion Visual Parity Audit

Authority: frozen Simulated commit `9e60263fb5cb00033f14af655a7e72cf7aebb3e2`.

## Root cause

M24.10 used placeholder cuboids textured with vanilla copper and referenced the
non-upstream partial `simulated:block/torsion_spring_spring`. It omitted frozen
Simulated's dedicated Torsion textures and exact UV-authored models. The result
did not satisfy the renderer's model contract and produced corrupted/missing
appearance rather than the upstream spring.

## Exact ownership

The frozen static block model owns only the casing/base. The dynamic renderer
owns the spring partial and the two kinetic half-shafts. The item model owns the
complete item-only geometry. The production textures are:

- `simulated:textures/block/torsion_spring.png`
- `simulated:textures/block/torsion_spring_inside.png`

M24.11 copies the exact frozen `block.json`, `spring.json`, `item.json`, and both
PNG textures. The blockstate uses the frozen facing rotations and references
`simulated:block/torsion_spring/block`; the item model references
`simulated:block/torsion_spring/item`; the BER references
`simulated:block/torsion_spring/spring`.

Create 6.0.8/Flywheel 1.0.5 adaptation remains the established BER fallback.
Inside a Sable, the M11 renderer bridge suppresses Flywheel's normal-world skip,
and Sable supplies the already-small local PoseStack. No hidden plot coordinate
is translated into the renderer. The spring angle is interpolated from the
existing old/current kinetic state. No Torsion kinetic state evolution changed.
