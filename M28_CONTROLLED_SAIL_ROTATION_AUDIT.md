# M28 controlled sail rotation boundary

Target authority: mapped Create 6.0.8 for Minecraft 1.20.1, Forge 47.4.20.
M28 steering, kinetic propagation, `ROTATE_NEVER_PLACE`, and the corrected
visible-frustum bounds remain unchanged.

## Create's normal path

`MechanicalBearingBlockEntity.tick()` advances its `angle` by the Create angular
speed, and `applyRotation()` calls `ControlledContraptionEntity.setAngle(angle)`
and sets the axis from the bearing's facing. The entity stores `angle` and
`prevAngle`; `tickContraption()` updates `prevAngle`, and `getAngle(partialTick)`
uses Create/Catnip angle interpolation. `applyLocalTransforms` nudges the model,
centers it on the block-center pivot `(0.5, 0.5, 0.5)`, rotates by that
interpolated angle about the Create rotation axis, then uncenters it.

`ContraptionEntityRenderer.render()` obtains the client contraption matrices,
calls `ContraptionMatrices.setup(poseStack, entity)`, then transforms the baked
`SuperByteBuffer` by the prepared model matrix and renders it into the supplied
PoseStack. `setup` calls `entity.applyLocalTransforms` with
`AnimationTickHolder.getPartialTicks()`. Thus an advancing client entity angle
and non-null axis should produce an advancing inner model transform without a
Sable-owned visual angle.

## Coordinate frames

- `C`: Create contraption block/model coordinates, with the centered bearing
  pivot used by `applyLocalTransforms`.
- `P`: raw hidden-plot world; the controlled entity anchor is in this frame.
- `S`: Sable-local coordinates relative to its plot, owned by Sable's pose.
- `W`: visible physical world.
- `Cam`: visible world relative to the camera.

The ordinary renderer starts with the entity/camera translation and then
applies Create's centered inner model rotation. The Sable Forge-stage bridge
substitutes the *visible* entity anchor minus camera for the impossible raw
plot translation. At renderer entry the Sable mixin composes the outer Sable
quaternion onto that small PoseStack. Create's `ContraptionMatrices.setup` still
adds the inner centered model rotation afterward. Raw plot coordinates are
never put into PoseStack. Culling separately uses Create's `toGlobalVector`
and the Sable render pose on all eight bounds corners.

## Unresolved live boundary

Source/bytecode inspection shows no missing `applyLocalTransforms` call in the
target render path. Without a simultaneous server and client runtime sample,
the report of a visually static sail does not establish whether the client
controlled entity angle is static (client tick/controller/sync boundary) or its
angle advances but the prepared/rendered matrix remains static (render boundary).
Adding another rotation now would risk double-rotating Create geometry.

`SABLE_M28_CREATE_ROTATION` samples the server bearing and controlled entity
angles during real steering. `SABLE_M28_RENDER_ROTATION` samples the client
bearing, client entity angle, Create's actual partial tick, prepared inner model
matrix, an offset probe after that matrix, and the same probe after the
camera-relative renderer matrix. Samples are bounded and emitted on angle
changes. Compare the same entity ID and Sable ID on both sides:

1. Server bearing changes but server entity does not: bearing-to-entity issue.
2. Server entity changes but client entity does not: client tick/sync issue.
3. Client entity changes but prepared inner probe does not: Create model setup
   or axis issue.
4. Inner probe changes but final probe does not: Sable/renderer composition.
5. Both probes change but sail appears static: inspect emitted geometry/model
   ownership, not the bearing angle or culling.

Do not mark M28 rendering PASS until the live trace and visual angle sweep agree.
