# M28 rotated contraption culling audit

Target: Minecraft 1.20.1, Create 6.0.8, Forge-stage Sable renderer bridge.

## Exact target path

`SableForgeCreateContraptionRenderBridge` renders hidden-plot Create entities at
`AFTER_ENTITIES`. The old bridge tested the frustum against
`getBoundingBoxForCulling().inflate(0.5)` after only the outer Sable render pose.
`BoundingBox3dc.transform` already transforms all eight corners, so the failure
was not a two-corner AABB transform. The input box itself came from Create's
entity bounding box and was not sampled through its current bearing rotation.

In the mapped Create 6.0.8 dependency, `AbstractContraptionEntity.setPos` sets
the entity box from `Contraption.bounds.move(getAnchorVec())`.
`AbstractContraptionEntity.toGlobalVector(local, partialTick)` subtracts the
block-center pivot `(0.5, 0.5, 0.5)`, calls `applyRotation`, restores that
pivot, and adds the current anchor. `ControlledContraptionEntity.applyRotation`
uses the interpolated bearing angle and rotation axis. The renderer's local
transform uses that same bearing angle. `BearingContraption.assemble` also
expands local bounds around the bearing axis. This expansion can make the old
box conservative; source inspection alone cannot prove that it was the sole
cause of entity 1830's observed frustum rejection.

## Corrected culling path

For each of the eight `Contraption.bounds` corners, the bridge now applies:

1. Create local -> raw plot world via `toGlobalVector(corner, partialTick)`.
2. Current-to-interpolated entity-anchor offset.
3. Raw plot world -> Sable visible world via the same `renderPose(partialTick)`
   used for the dispatcher anchor.
4. Componentwise minimum/maximum over all final visible corners.

The visible-world box, with the existing half-block culling allowance, enters
`Frustum.isVisible`. Camera subtraction remains confined to the dispatcher
translation. Hidden plot coordinates never enter the `PoseStack` as a large
translation/cancellation. Invalid or unavailable Create local bounds retain
the previous culling box; culling is not globally bypassed.

`SABLE_M13_CONTROL_BOUNDS` logs at most six samples per controlled entity,
initially and on frustum-result transitions. It records both old and rebuilt
boxes, all local/raw/visible corners, outer and Create rotations, camera,
both frustum outcomes, distance outcome, and captured block count. This is
needed to determine whether the previously failing orientation actually
crosses the corrected boundary. Runtime confirmation for entity 1830 is still
required; source and unit tests cannot establish a live camera/frustum result.
