# M23.3: Spring Owner Actor Teardown

Status after static implementation: `M23 IMPLEMENTED / RUNTIME_REQUIRED`.

Do not close M23 until a fresh manual runtime fixture proves Spring teardown
clears actor and active-constraint state on both assembled bodies.

## Runtime-Proven Root Cause

Latest runtime proved the Spring endpoint blocks were removed but cleanup was
incomplete:

```text
SABLE_M23_CONSTRAINT phase=REMOVE_SUCCESS
ownerActorRegisteredAfter=true
partnerActorRegisteredAfter=false
springEndpointCount=0
DISASSEMBLY_BLOCKED activeConstraints=[5629783017250652289:5629783841875988609]
```

The ordinary payload was still intact, so M23.1 block preservation was not the
failure. The stale state was the owner Spring `BlockEntitySubLevelActor` and the
logical active-constraint id observed by the M22 disassembly guard.

## Ownership

For Sable-to-Sable Springs, the controller endpoint is the logical force actor
owner. This follows the current ported upstream behavior:
`sable$physicsTick` returns for the follower when both endpoints are in
sublevels, so only the controller contributes the body-to-body Spring force.

The M22 active-constraint guard is intentionally conservative. It derives
logical Spring constraints from Spring BlockEntities inside the sublevel block
set through `SimAssemblyHelper.activeSpringConstraintIds`; it must continue to
block while a real Spring relationship exists.

## Target Bug

The previous `breakPair` cleared the current visible BlockEntity metadata and
destroyed the partner endpoint. The partner endpoint removal reconciled its
actor, but the originally broken endpoint could remain in
`LevelPlot.blockEntityActors` because the plot mirror observes block changes
while the removed BlockEntity can still be resolvable in the chunk. That made
`REMOVE_SUCCESS` a false positive.

## Fix

Cleanup now:

1. captures the logical id, controller endpoint, follower endpoint, sublevels,
   and actor counts before mutation;
2. clears pair metadata on the live endpoint BlockEntities and any actor-map
   instances for the same exact endpoint positions;
3. removes only those exact Spring endpoint actors from the owning plot maps;
4. destroys the paired endpoint after its metadata is cleared so recursive
   cleanup is a no-op;
5. verifies actor and logical active-constraint absence on both bodies before
   emitting `REMOVE_SUCCESS`;
6. emits `REMOVE_PENDING` instead of `REMOVE_SUCCESS` if stale state remains.

No Spring stiffness, damping, Hooke force, persistence, M22 block movement, or
Rapier terrain collision code changed.

## Diagnostics

`SABLE_M23_SPRING_CLEANUP` reports:

```text
logicalId=
controllerEndpoint=
followerEndpoint=
actorOwner=
actorCountBeforeA=
actorCountBeforeB=
actorCountAfterA=
actorCountAfterB=
activeOnBodyABefore=
activeOnBodyBBefore=
activeOnBodyAAfter=
activeOnBodyBAfter=
ownerBlockPresentAfter=
partnerBlockPresentAfter=
result=
```

`/sable m23 inspect spring` now prints `constraintMode=SABLE_TO_SABLE` for a
valid M23 force test. Static-world pairs print
`runtimeState=STATIC_WORLD_NO_SABLE_FORCE_TEST` and must not count as a Spring
gate pass.

`/sable m23 nudge spring_a <dx> <dy> <dz>` and
`/sable m23 nudge spring_b <dx> <dy> <dz>` resolve the nearest two assembled
Physics Assembler bodies and call the existing real Sable physics handle.
