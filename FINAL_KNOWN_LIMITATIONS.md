# Final known limitations: accepted Forge 1.20.1 release

Scope: Minecraft 1.20.1, Forge 47.4.20, Java 17, Create 6.0.8,
Flywheel 1.0.5. This is not a compatibility claim for arbitrary newer versions.
Runtime acceptance of M28-M45 and the final M45.1 smoke comes from the user's
reported milestone evidence. The existing accepted jar's size and SHA-256 were
independently verified during documentation-only M45.2.

M45.1 static release audit and full build PASS. The five production/fixture edges
and stale Veil verifier model are genuinely repaired, not accepted limitations.
The backport is FUNCTIONALLY COMPLETE; the candidate is ACCEPTED / RELEASE READY.
Exact artifact and final runtime acceptance are in `M45_FINAL_RELEASE_AUDIT.md`.
No code, build, networking, runtime assets or jar bytes changed in M45.2, and no
build or Minecraft session was run. A release tag is withheld because accepted
M45.1 implementation/build changes remain uncommitted; that is source-provenance
bookkeeping, not a new compatibility limitation or an implementation milestone.

## OBSERVED_NON_BLOCKING_WARNING

- During M38 cleanup, the user observed `Received a sub-level movement packet
  for a non-existent sub-level`. It had also occurred during initial world
  loading, before the final smoke. Cleanup succeeded without an associated
  crash or stack trace. This is an observed client synchronization warning,
  not a confirmed M45.1 regression. Inspected historical local runtime logs also
  contain this message; they are not the final smoke session. Networking and
  lifecycle code were not modified to suppress it.
- For M27 `aero_surface_basic`, `expectedControlPayloadBlocks=0`,
  `controlSurfaceCount=0` and `controlBearingOwner=UNAVAILABLE` are
  NOT_APPLICABLE: this fixture has no control-surface payload. They are not a
  missing production component or a compatibility gap.

## USER_VISIBLE_LIMITATION

- Create goggles do not provide virtual block information for a block inside
  an assembled Sable when the selected hit is an entity or hidden-only context.
  M44 deliberately returns without an overlay. Ordinary physical block overlays
  retain Create/TFMG behavior. Global raycasts and interaction are not replaced.
  Virtual `BlockHitResult` support is not part of this release.
- Optional integrations are validated only against the available target-modpack
  versions. In particular, the TFMG overlay guard is restricted to **1.0.2f**;
  no claim is made for other TFMG releases. A new third-party injection can
  require a new audit even if Create itself has not changed.

## TEST_HARNESS_LIMITATION

- M37's synthetic INSERT receiving fixture was not perfected end-to-end. Its
  moving native extraction, parent item capture, stationary-source capture,
  mounted chest storage and rendering were accepted. This is not an outstanding
  gameplay transform defect or a claim that the synthetic collector passed.
- Historical milestone reports retain the status at the time they were written,
  including "runtime pending". The final ledger records subsequent user runtime
  acceptance; it does not fabricate new logs or rerun every historical fixture.
- Fixtures are retained in the accepted release (packaging option A). They are operator
  tools, not required gameplay state. Their finite payloads, cleanup ownership,
  safe refusal and limited layouts are not a general construction API.

## INTENTIONAL_SAFE_NO_OP

- PSI/PFI, Funnel and moving Dropper/Dispenser external operations require the
  level-orientation envelope documented by their resolvers. The shared M36
  geometry requires transformed world-up Y > 0.999 and squared facing error
  <= 0.5 against the nearest cardinal direction. This is the exact native
  Create-facing envelope, not an invented claim of arbitrary 3D block behavior.
- Sliding Door handoff additionally requires transformed inner/world up and
  horizontal cardinal facing alignment > 0.999 (about 2.56 degrees). Material
  pitch/roll and non-cardinal door facing do not mutate a parent door.
- Roller parent terrain interaction requires level world-up alignment > 0.999;
  unsupported poses do not clear/pave arbitrary projected terrain.
- Parent block/capability interactions do not fall back to raw hidden plots.
  Unloaded/invalid targets and unsupported cross-Sable targets are refused.
  Physical interaction with another Sable is not implied by parent-world support.
- Live nested controller transfer preserves the supported controller families
  through the existing transactional ownership bridge. A rotated ownership
  transfer outside its supported assembly transform is safely rejected, not
  approximated by rebuilding an unrelated payload.
- Physical restoration cells occupied by unrelated blocks still block outer
  disassembly. M22 safety is retained; fixture recovery may remove only proven
  fixture-owned residue, never arbitrary terrain.

## OUT_OF_SCOPE

- Train-specific controls, carriage mechanics and cross-dimension riding are
  not implied by MovementBehaviour inventory coverage. Train-only behavior is
  an explicit excluded category, not a newly implemented feature.
- Unsupported optional upstream integrations excluded from the Forge source
  set (for example NeoForge/Fabric-only or deferred renderer integrations) are
  not silently supported by retaining their source in the repository.
- M40 supports the native moving Basin item-dump path. It does not add fluid
  spilling or a new Mixer/Press processing feature.
- This release audits the touched Create/Sable boundary, not every behavior in
  every third-party mod. Unrelated modpack resource/startup warnings are not
  automatically Sable release blockers.
- No M46 is required or started by this release closure.
