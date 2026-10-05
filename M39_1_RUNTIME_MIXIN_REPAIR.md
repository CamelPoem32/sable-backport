# M39.1 packaged teardown hook repair

M39 only. M28-M38 behavior and fixtures are unchanged. Minecraft was not launched.
The first M39 runtime crashed during lazy class loading, before any M39 READY,
SEAT_CAPTURED or MOUNT_SUCCESS. No M39 fixture recovery is inferred from that crash.

## Exact original injection

Class: `dev.ryanhcode.sable.mixin.m39.SeatOuterDisassemblyMixin`.

```java
@Mixin(value = SimAssemblyHelper.class, remap = false)
public class SeatOuterDisassemblyMixin {
    @Inject(
        method = "disassembleSubLevel(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Rotation;I)Ldev/simulated_team/simulated/util/SimAssemblyHelper$DisassemblyResult;",
        at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/compatibility/create/contraptions/SableNestedBearingOwnershipTransfer;begin(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;Ljava/lang/String;)V"))
    private static void sable$detachBeforeOwnershipTransfer(
            final ServerLevel level, final ServerSubLevel body,
            final BlockPos anchor, final BlockPos goal, final Rotation rotation,
            final int expected,
            final CallbackInfoReturnable<SimAssemblyHelper.DisassemblyResult> cir) {
        SableSeatPassengers.detachOuter(body);
    }
}
```

This is an Inject, not a Redirect/WrapOperation. No ordinal, shift, slice,
require, expect or allow is explicitly set. Defaults: ordinal=-1 (all matches),
shift=NONE, slice="", require=-1 (inherits config defaultRequire=1), expect=1
(debug injection-count check), allow=-1 (no explicit upper limit). Not cancellable.

Target class: `dev.simulated_team.simulated.util.SimAssemblyHelper`, owned by this
repository. The six-argument overload is declared on that class, not inherited.
Its complete descriptor is the method selector shown above. Handler arguments
and CallbackInfoReturnable agree with that overload.

## Proven mismatch in the crashing artifact

Preserved actual pre-repair all.jar:
`forge/build/m39Regression/broken-all.jar` (generated, ignored build output).
SHA-256: `F600BEDC778A69EDB903CE2670766F5A613A552C57444912F44276B3FA8DBD9E`.

The six-argument method's production bytecode contains:

```text
241 aload 9
243 invokeinterface java/util/List.isEmpty:()Z
248 ifne 257
251 aload 9
253 invokestatic SimAssemblyException.occupied:(Ljava/util/Collection;)Lcom/simibubi/create/content/contraptions/AssemblyException;
256 athrow
257 aload_0
258 aload 7
260 ldc "OUTER_DISASSEMBLY"
263 invokestatic SableNestedBearingOwnershipTransfer.begin:(Lnet/minecraft/server/level/ServerLevel;Ljava/util/Collection;Ljava/lang/String;)V
266 goto 274
269 astore 10
271 aload 10
273 athrow
274 aload 8
276 invokestatic SableNestedBearingOwnershipTransfer.bindTransform:(Ldev/ryanhcode/sable/api/SubLevelAssemblyHelper$AssemblyTransform;)V
329 invokestatic SubLevelAssemblyHelper.moveBlocks:(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/SubLevelAssemblyHelper$AssemblyTransform;Ljava/lang/Iterable;)V
481 aload 14
483 ifnull 495
486 aload 14
488 aload_1
489 getstatic SubLevelRemovalReason.REMOVED
492 invokevirtual ServerSubLevelContainer.removeSubLevel:(Ldev/ryanhcode/sable/sublevel/SubLevel;Ldev/ryanhcode/sable/sublevel/storage/SubLevelRemovalReason;)V
514 invokespecial DisassemblyResult.<init>:(IILjava/util/Collection;ZLdev/ryanhcode/sable/companion/math/BoundingBox3i;)V
517 areturn
```

There is also an early empty-body return at bytecode offset 197, before transfer.
The selected INVOKE owner and name agree; the descriptor does not:
the mixin requests `List`, but the invoked declaration accepts `Collection`.
The variable holding the argument is a List; JVM invocation descriptors use
the called method's declared parameter type, not the argument's concrete type.

Mixin 0.8.5 InjectionInfo.postInject reports injectedCallbackCount below required
count after scanning matched target methods. The error alone would not establish
every detail, but these bytecode/handler/refmap checks prove case A: the method
matched, and the injection point had zero matches. It was not a handler mismatch.

The packaged config uses `sable.refmap.json` and defaultRequire=1. The original
refmap contains NO entry for SeatOuterDisassemblyMixin. @Mixin remap=false means
the custom-class selector remains literal; there is no SRG alternate for this
custom member that could turn List into Collection. The development and packaged
method/owner names agree. No remap setting change is needed.

No other repository mixin targets SimAssemblyHelper. SeatOuterRemovalMixin targets
ServerSubLevelContainer.removeSubLevel, a different class/boundary. No competing
Sable transformation removed this call site; no priority change is warranted.
M38's post-physics lookup was only the lazy class-loading trigger.

## Stable mandatory hook

The obsolete self-mixin and its config entry are removed. SimAssemblyHelper now
calls the existing scoped helper explicitly after restore-space validation and
before ownership transfer:

```java
if (!occupied.isEmpty()) {
    throw SimAssemblyException.occupied(occupied);
}
SableSeatPassengers.detachOuter(subLevel);
SableNestedBearingOwnershipTransfer.begin(level, blocks, "OUTER_DISASSEMBLY");
```

Final production bytecode:

```text
253 invokestatic SimAssemblyException.occupied:(Ljava/util/Collection;)Lcom/simibubi/create/content/contraptions/AssemblyException;
256 athrow
257 aload_1
258 invokestatic SableSeatPassengers.detachOuter:(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)V
261 aload_0
262 aload 7
264 ldc_w "OUTER_DISASSEMBLY"
267 invokestatic SableNestedBearingOwnershipTransfer.begin:(Lnet/minecraft/server/level/ServerLevel;Ljava/util/Collection;Ljava/lang/String;)V
280 invokestatic SableNestedBearingOwnershipTransfer.bindTransform:...
333 invokestatic SubLevelAssemblyHelper.moveBlocks:...
496 invokevirtual ServerSubLevelContainer.removeSubLevel:...
521 areturn
```

HEAD would detach even when restore-space validation refuses disassembly; RETURN
would be after ownership/block transfer. The direct call preserves the intended
semantic boundary and is checked by the Java compiler instead of matching an
INVOKE descriptor in our own class. Forge already compiles against the common
helper; the helper has no dependency on SimAssemblyHelper. No module cycle.

The existing helper only visits vehicles semantically owned by this body and
detaches eligible parent-world Seat passengers. Forge dismount veto still refuses
teardown. SeatOuterRemovalMixin remains the independent removal safeguard.
No riding architecture, physics, rendering, fixture or M38 code was changed.
No require=0, optional safety hook or exception-swallowing fallback was introduced.

## Packaged smoke verifier

New task: `:forge:verifyM39RuntimeMixinTargets`, also required by `:forge:check`.
It uses ASM against the FINAL reobfuscated/JarJar artifact and the exact raw
Create 6.0.8 runtime jar, without loading target classes or launching Minecraft.

It checks all five configured M39 Seat mixin classes, all 15 hook annotations,
target owners/method descriptors, handler callback argument descriptors, packaged
refmap member mappings, exact INVOKE occurrence counts, and supported HEAD/RETURN
boundaries. It rejects making a required hook optional. It checks the direct
detach call exactly once, using the body argument, after occupied-space refusal
and before ownership transfer/moveBlocks. Obsolete mixin class/config must be absent.

The real preserved crashing artifact is tested negatively and rejected with:
`SeatOuterDisassemblyMixin ... matches=0 expected=1 ... Ljava/util/List;`.
An always-run List-versus-Collection bytecode canary also checks the descriptor
mismatch even on a clean checkout without that local regression artifact.

This is a structural application-target smoke test, NOT full Mixin transformation
or an in-game boot claim. Reproducing the full transformer requires ModLauncher,
Forge discovery/access transformers, MixinExtras and side-specific mod bootstrap;
the no-Minecraft ASM gate is the mandatory alternative requested for this repair.

PASS: spotlessApply, spotlessCheck, compileJava, verifyM39SeatParentWorld,
verifyM39RuntimeMixinTargets (including old-artifact rejection), every retained
M31-M38 gate, verifySableProductionBaseline, reobfJar, jarJar, reobfJarJar,
verifyStandaloneForgeArtifact, full build, git diff --check.
Full chain: BUILD SUCCESSFUL; 274 actionable tasks, 251 executed, 23 up-to-date.
Existing deprecation/Mixin/compiler warnings remain; no compilation errors.

## Runtime smoke FIRST

1. Install the repaired jar and load the SAME existing world normally.
2. Wait 10-20 seconds BEFORE creating an M39 fixture. Require normal Sable ticks,
   no MixinTransformerError/InjectionError and no M38-triggered class-load crash.
3. Run `/function sable:m39/cleanup`. With no previous M39 setup, expect
   `CLEANUP COMPLETE alreadyClean=true`.
4. Run `/function sable:m39/player`, wait for READY, click assembler once,
   run `/function sable:m39/run`, wait for SEAT_CAPTURED, then right-click the
   physically visible Seat. Do not proceed to yaw/dismount until mounting works.

There is no evidence that an M39 fixture existed in the crashed world. This repair
does not invent a recovery procedure or alter the existing world's M38 state.

## Artifact

Path: `forge/build/libs/sable-forge-1.20.1-2.0.0-all.jar`
Size: 12,796,585 bytes
SHA-256: `5C1B5BD590F9066CC23150AE80DD417A14DF59F2C9E68839DE203E1C3D8A0336`

## Repair-only changed files

```text
buildSrc/src/main/java/M39RuntimeMixinTargetVerifier.java (new)
forge/build.gradle
forge/src/main/java/dev/simulated_team/simulated/util/SimAssemblyHelper.java
forge/src/main/resources/sable-common-forge.mixins.json
forge/src/main/java/dev/ryanhcode/sable/mixin/m39/SeatOuterDisassemblyMixin.java (removed)
M39_1_RUNTIME_MIXIN_REPAIR.md (new)
M39_SEAT_PARENT_WORLD.md
M39_RUNTIME_TEST_COMMANDS.md
```

The broader pre-existing M39 implementation is preserved, not rewritten.

Status: M28-M38 CLOSED / PASS (M37 accepted known fixture limitation).
M39.1 required teardown hook REPAIRED / packaged static gates PASS / boot pending.
M39 Seat riding RUNTIME NOT REACHED; overall OPEN. No M40 work.
