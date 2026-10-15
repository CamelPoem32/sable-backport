# M44: Create Goggle Overlay / Physical Raycast Compatibility

Target: Minecraft 1.20.1, Forge 47.4.20, Java 17, Create 6.0.8, branch
`backport/forge-1.20.1-sable-2.0.0`. Pinned Create commit:
`1a1a9a2819b4f89f78caec41b55ed8cb222fa24b`.

M28-M43 and M41 functional MovementBehaviour closure retain the user's accepted
runtime status. No MovementBehaviour, raycast, Seat, collision or Jade feature
is reopened. M44 is a client overlay-only repair, IMPLEMENTED / runtime pending.

## Injection ownership

The cause is **Create: The Factory Must Grow (TFMG), mod id `tfmg`, version
1.0.2f**, not Sable and not native Create. Exact target-modpack jar:

`target_modpack/mods/tfmg-1.0.2f.jar`

SHA-256: `A789D1519FD4033334D0E217B252C557095B8007922247BFE53EE9DB4F76A63D`.

Mixin: `com.drmangotea.tfmg.mixin.GoggleOverlayRendererMixin`.
Config: `tfmg.mixins.json`, required, defaultRequire=1; this class is in its
`mixins` list (not its empty client list).

Target: `com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer`.

```text
@Inject(method="renderOverlay", at=@At("HEAD"), cancellable=true, remap=false)
private static void renderOverlay(
  ForgeGui, GuiGraphics, float partialTicks, int width, int height, CallbackInfo)
```

Callback descriptor:
`(Lnet/minecraftforge/client/gui/overlay/ForgeGui;Lnet/minecraft/client/gui/GuiGraphics;FIILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V`.

The packaged handler class hash is
`eb1ea0e6f8318606f01e3e21a6d98eb771c8e0882201c8147ed790db9d4ce4d8`.
Its actual SRG instruction sequence is:

```text
 0 INVOKESTATIC Minecraft.m_91087_()Minecraft
 7 GETFIELD Minecraft.f_91073_:ClientLevel
14 GETFIELD Minecraft.f_91077_:HitResult
17 ASTORE 8
19 ALOAD 8
21 CHECKCAST BlockHitResult                     <-- throwing cast, no type guard
24 ASTORE 9
28 INVOKEVIRTUAL BlockHitResult.m_82425_()BlockPos
37 INVOKESTATIC proxiedOverlayPosition(Level,BlockPos)BlockPos
46 INVOKEVIRTUAL ClientLevel.m_7702_(BlockPos)BlockEntity
... GUI/spectator/goggles/multimeter checks occur only AFTER this lookup
```

The config names `Create-The_Factory_Must_Grow.refmap.json`, but that entry is
absent from this exact jar. It contains `mixin.refmap.json`, which maps only
RecipeProviderMixin and has no overlay mapping. This is recorded, not repaired
in someone else's jar. The callback is remap=false and already contains correct
SRG Minecraft members: the exception is a real type error, not a mapping error.

An exhaustive scan of target-modpack `/mixin/` and `/mixins/` class bytes finds
two mixins referencing this Create target: TFMG and Numismatics. Numismatics'
`dev.ithundxr.createnumismatics.forge.mixin.client.MixinGoggleOverlayRenderer`,
from `numismatics.mixins.json` / `CreateNumismatics-forge-refmap.json`, only wraps
`ItemEntry.asStack()` in `changeDisplayItem`; it does not contain this cast or
an Inject callback named renderOverlay. Sable's pre-M44 packaged mixin config
contains no overlay hook.

`handler$heo000$renderOverlay` is the runtime-generated name of the merged
Inject handler, not a stable declared method name in the mod jar. TFMG is the
only matching injected renderOverlay callback and has the exact failing cast.
**No exported class from the user's running JVM was supplied or found locally.**
The audit therefore does not pretend to have inspected that exact exported
heo000 class or claim a stable generated suffix. It proves ownership from the
packaged callback/config/descriptor/cast and verifies the wrapping architecture
against the actual MixinExtras implementation. The modeled merge smoke below
is explicitly not an export of the user's runtime class.

## Native Create behavior

Exact pinned source: `build/m41Audit/source/GoggleOverlayRenderer.java`, fetched
from the commit above and compared with packaged production bytecode.
Native class SHA-256:
`3fd5af4de8efe089df151429016766e71439d6b8f409aac4ca3db4e5f3a434ae`.

```text
Forge create:goggle_info IGuiOverlay -> GoggleOverlayRenderer.renderOverlay
 -> GUI/spectator guard
 -> mc.hitResult
 -> instanceof BlockHitResult, OTHERWISE clear lastHovered/hoverTicks and return
 -> suppress overlay while an active ValueBox is open
 -> result.getBlockPos
 -> proxiedOverlayPosition / IProxyHoveringInformation
 -> ClientLevel.getBlockEntity
 -> IHaveGoggleInformation / IHaveHoveringInformation / assembly exceptions
 -> goggles/shift/icon checks -> native tooltip drawing
```

Native bytecode guards with INSTANCEOF BlockHitResult at offset 40, CHECKCAST at
48, and returns on the non-block branch at 64, before any BE lookup. Native
Create does **not** unconditionally cast hitResult. It checks the Java type,
not HitResult.Type.BLOCK specifically: a BlockHitResult carrying MISS can pass
its type guard. M44 explicitly treats MISS as no target instead of evaluating
an endpoint as a real block. Ordinary successful stationary BLOCK hits retain
the complete original Create + TFMG + Numismatics path.

TFMG injects its cast at HEAD, before native Create's protective guard. Its
lookup also runs before its own visibility/equipment tests. Thus wearing goggles
is not necessary to trigger the original exception.

## Why an entity hit is legitimate

Vanilla GameRenderer.pick calls Entity.pick for block clipping and
ProjectileUtil.getEntityHitResult for eligible entity intersections, compares
physical distances, and stores the closer EntityHitResult in Minecraft.hitResult.
Sable's existing ProjectileUtilMixin transforms the rays/clips/distances through
the owning sublevel pose without changing the result category. The existing
GameRenderer.renderLevel wrapper supplies interpolated poses around native pick.

M11 `ClientSubLevelTargetHelper.refreshMinecraftHitResult` explicitly preserves
an existing ENTITY hit and returns before any static-block fallback. Static
visible Sable blocks use its existing block/plot interaction context. No M44
change is made to that established interaction contract.

Native `AbstractContraptionEntity.isPickable()` returns false. It is therefore
wrong to infer from this crash alone that the selected entity was the inner CCE.
Ordinary mobs, including M43's physical test entities, are valid candidates.
The old stack does not record which entity was selected. New bounded diagnostics
report its class rather than inventing that missing runtime detail.

Create's moving-block right click is a separate path:
`ContraptionHandlerClient.rightClickingOnContraptionsGetsHandledLocally`
 -> loaded contraptions -> rayTraceContraption -> local BlockHitResult
 -> handlePlayerInteraction / ContraptionInteractionPacket.
M39's existing seat bridge corrects bounds and physical ray/point composition
there. This is not a reason to globally replace a correct Minecraft EntityHitResult
with a synthetic block hit. An outer ServerSubLevel is itself a container/body,
not a new vanilla-pickable entity. Jade has its own retained ray path.

Root cause: **B, third-party overlay compatibility callback with an unsafe cast**.
The existence of EntityHitResult is a valid native/Sable-preserved case, not
evidence of a physical raycast regression. No class of entity is made unpickable.

## Repair boundary

New client mixin:
`dev.ryanhcode.sable.mixin.compatibility.tfmg.GoggleOverlayRendererMixin`.

It targets Create's exact declared public static method:
`renderOverlay(ForgeGui,GuiGraphics,float,int,int)V` with a MixinExtras WrapMethod.
There is no INVOKE ordinal or generated-handler-name target and no priority hack.

Mixin selection uses the existing optional-mod plugin: `compatibility.tfmg`
selects mod id tfmg, with `@MixinModVersionConstraint("[1.0.2f]")`. Thus no hook
applies when TFMG is absent or outside the audited version. There is no direct
TFMG API dependency. Other TFMG versions require a new audit rather than an
unbounded compatibility promise.

The type guard is intentionally **not conditional on entity ownership**: this
TFMG callback crashes on any legal EntityHitResult, including ordinary entities.
Conditioning the guard only on Sable-owned CCEs would miss the actual general
failure (and CCEs are normally not pickable). This is still narrowly confined to
the affected optional mod's Create overlay entry. Normal Create outside the
TFMG version gate has no additional code; normal valid block overlays inside
the gate delegate unchanged.

| Current hit/context | Overlay decision |
|---|---|
| No client world/player/gameMode | NO_CLIENT_CONTEXT, no callback |
| null | NULL_HIT, no callback |
| MISS | MISS, no callback |
| EntityHitResult / ENTITY | ENTITY_NO_BLOCK_CONTEXT, no callback |
| malformed non-block Java object claiming BLOCK | NON_BLOCK_HIT, no callback |
| BlockHitResult in reserved hidden plot grid | HIDDEN_STORAGE_NO_PHYSICAL_BLOCK_CONTEXT, no callback |
| Actual ordinary parent-world BlockHitResult | NATIVE_PARENT_BLOCK, call original with all arguments unchanged |

Suppressed frames clear only native Create's hover counter/lastHovered, as its
own non-block branch does. There is no exception catch or fallback item/block
selection. Hidden-grid rejection uses the existing plot-grid bounds API, including
stale/unallocated plots; it is not a hardcoded distance threshold.

A captured moving block is not a real BlockEntity at the corresponding physical
parent-world cell. Nor is the stored static Sable BE physically present in that
cell. M44 does not pretend otherwise: for entity-only and raw-plot hits it safely
omits the overlay. It does not introduce a fake Level/BE wrapper, nearest-block
selection, a fake BlockHitResult, or a second contraption tooltip renderer.
Where a meaningful actual parent block hit already exists, native context is
retained. New virtual/captured-BE overlay functionality is not claimed.

`Minecraft.hitResult` is never assigned. No Camera/GameRenderer/raycast/entity/
collision/Jade/MovementBehaviour code is changed. No PoseStack or camera transform
is introduced. Hidden raw positions appear only in explicitly labeled diagnostics,
never as the overlay's supplied block target.

## Injection ordering and static smoke

An ordinary cancellable HEAD injection would be unsafe here: its execution order
relative to TFMG's HEAD cannot be presumed. The wrapper is selected instead.
Actual pinned MixinExtras 0.5.3 `WrapMethodApplicatorExtension.postApply` applies
the collected WrapMethodStage after target injections; Wrapper.apply moves the
already-injected method body into the operation and makes the original entry
call the wrapper. Thus invalid hits cannot reach the preexisting TFMG HEAD.

`verifyM44GoggleOverlayCompatibility` runs the **actual framework wrapping stage**
on an ASM model built from the packaged Create method, the copied TFMG handler
and the packaged M44 wrapper. The model inserts the external HEAD call, then
invokes WrapMethodStage.Wrapper.apply reflectively. It asserts that the public
entry calls the M44 guard, no longer directly calls the external handler, and
the moved native operation still contains that handler exactly once.
This is a headless structural composition smoke, not a full Forge launch or a
pretended exact transformation/export of the user's running class. Its modeled
CallbackInfo value is never executed. Separate policy tests execute the actual
eligibility helper and reproduce the old ClassCastException as a negative test.

The packaged Sable config/refmap is checked. The target is a Create-owned method
name with an explicit full descriptor and remap=false; it requires no new SRG
member mapping in sable.refmap.json. Direct Minecraft field/method accesses in
the handler are reobfuscated normally. DefaultRequire remains 1. The new target
has exactly one structural METHOD match; all retained mixin checks remain.

## Diagnostics

Enable the existing JVM flag:
`-Dsable.m31.traceCreateActors=true`.

`SABLE_M44_GOGGLE` reports hitResultType, entityClass, sableNestedEntity,
physicalBlockResolved, physicalBlockPos, rawBlockPos, overlayDecision and
globalHitUnchanged. Logs are transition-based, at most one per 20 game ticks and
32 per client world. Null/miss/entity/hidden cases do not perform a BE lookup.

Expected examples:

```text
SABLE_M44_GOGGLE hitResultType=ENTITY entityClass=... physicalBlockResolved=false ... overlayDecision=ENTITY_NO_BLOCK_CONTEXT globalHitUnchanged=true
SABLE_M44_GOGGLE hitResultType=BLOCK ... physicalBlockResolved=true physicalBlockPos=<ordinary block> rawBlockPos=none overlayDecision=NATIVE_PARENT_BLOCK
SABLE_M44_GOGGLE hitResultType=BLOCK ... physicalBlockResolved=false physicalBlockPos=none rawBlockPos=<stored plot> overlayDecision=HIDDEN_STORAGE_NO_PHYSICAL_BLOCK_CONTEXT
```

## Minimal runtime workflow

Reuse accepted M43 geometry; no new fixture or manual block/glue construction:

```mcfunction
/function sable:m43/cleanup
/function sable:m43/setup
/give @s create:goggles
```

`create:goggles` is verified in the pinned AllItems registration and production
bytecode. Equip the goggles normally (do not overwrite an existing helmet with
a destructive inventory command). Before outer assembly, aim at a stationary
motor/clutch: its ordinary Create overlay must still work.

Click the Physics Assembler once. Aim at the visible Sable mechanism and move
the cursor between blocks and entities. Existing right-clicks must still work.
M43 run_all may be reused to exercise the original successful-runtime context;
it is not required to re-close the accepted MovementBehaviour gates.

For a stable optional vanilla entity control near the observer:

```mcfunction
/summon minecraft:pig ~2 ~ ~ {NoAI:1b,NoGravity:1b,Tags:["sable_m44_goggle_probe"]}
```

Aim at this entity: no block context/overlay, no exception, correct entity
selection remains. The old TFMG cast fails on this type too. Remove only that
explicitly tagged probe, and clean the reused fixture:

```mcfunction
/kill @e[type=minecraft:pig,tag=sable_m44_goggle_probe]
/function sable:m43/cleanup
```

M44 runtime PASS requires no ClassCastException / create:goggle_info error,
ordinary Create overlay working, Sable targets safely blank or correct physical
context, unchanged right-click/entity/Jade interaction, and no raw overlay input.
No M44 runtime PASS is printed or claimed by static tooling.

## Verification and delivery

Final verification on 2026-10-15: **PASS**. Full invocation completed in 1m 42s,
280 actionable tasks (249 executed, 31 up-to-date):

- `spotlessApply`, `spotlessCheck`, `:forge:compileJava`.
- `:forge:verifyM44GoggleOverlayCompatibility`: pinned Create/TFMG ownership and
  native guard, actual hit-policy tests, optional/version scope, packaged target,
  and real MixinExtras wrapping-stage structural smoke.
- `:forge:verifyCreateMovementBehaviourCoverage`: PASS, REAL_GAP_FOUND=0;
  behavior/registration/coverage rows unchanged. Reviewed mixin fingerprint only
  changes to `6e033ad0ef8bfddcc608be3ba53739108e1ebd4ccf701280227b13e5c8e88bf7`.
  All 210 retained checks remain, with one new METHOD check: 211 checks and 303
  class fingerprints. No old check was removed or made optional.
- `:forge:verifyCreateProductionFixtureBoundary` and every retained M31-M43 gate,
  including M35 moving Roller rendering and M39 runtime mixin targets.
- Every earlier rendering/mixin gate and `:forge:verifySableProductionBaseline`
  included by full `build`.
- `:forge:reobfJar`, `:forge:jarJar`, `:forge:reobfJarJar`, standalone Forge
  artifact verification, Rapier packaging, and full `build`.
- `git diff --check HEAD`: PASS.

Whole `dev/` production/fixture class audit:
`build/m44-production-before.json` -> `build/m44-hash-audit.json`.
**1,001 retained classes unchanged, zero modified, zero removed, four added**:
the policy/Decision enum, diagnostic guard, and optional overlay mixin. Thus all
retained MovementBehaviour, raycast, interaction, Seat, rendering, collision and
Jade class files are byte-identical. The external TFMG jar hash is unchanged.
Preexisting M43 work was preserved; no closed feature implementation was edited.

Artifact:
`C:\Users\camel\Documents\Всякое\CreateAeronautics\sable-backport\forge\build\libs\sable-forge-1.20.1-2.0.0-all.jar`

Size: **12,968,732 bytes**.

SHA-256: `0465A5CB6DC5797A016D62AB7EA105DEDA69EF9A1245F5441B092558D3F3F198`.

No Minecraft launch or M44 runtime PASS is claimed.

M44 changed files only (preexisting M43 work is retained untouched):

- `M44_GOGGLE_OVERLAY_COMPATIBILITY.md`
- `buildSrc/src/main/java/M44GoggleOverlayVerifier.java`
- `common/src/main/java/dev/ryanhcode/sable/compatibility/create/goggles/GoggleOverlayHitPolicy.java`
- `common/src/main/java/dev/ryanhcode/sable/compatibility/create/goggles/SableGoggleOverlayGuard.java`
- `common/src/main/java/dev/ryanhcode/sable/mixin/compatibility/tfmg/GoggleOverlayRendererMixin.java`
- `config/m41-create-movement-coverage.json` (reviewed mixin fingerprint only; coverage rows unchanged)
- `forge/build.gradle` (gate registration / explained additive hash entries)
- `forge/m44-goggle-verification.gradle`
- `forge/src/m44GoggleTest/java/dev/ryanhcode/sable/compatibility/create/goggles/M44GoggleHitPolicyTest.java`
- `forge/src/main/resources/sable-common-forge.mixins.json`
- `sable-common-forge.mixins.json`

M28-M43: CLOSED / PASS (retaining the accepted M37 fixture caveat).
Create MovementBehaviour compatibility: COVERAGE COMPLETE, REAL_GAP_FOUND=0.
M44: IMPLEMENTED / runtime pending. M45 was not started.
