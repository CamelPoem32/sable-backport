# M39 one-command Seat fixtures

Op/Creative test in an open loaded area. Do not manually build or glue anything.
Keep `-Dsable.m31.traceCreateActors=true`. Minecraft was not launched by Codex.

## M39.1 boot smoke before any fixture

Load the same existing world and wait 10-20 seconds. Require normal Sable ticks,
no MixinTransformerError/InjectionError, and no crash from old M38 world state.
No M39 fixture was started in the failed launch; do not invent world repairs.

```mcfunction
/function sable:m39/cleanup
```

With no M39 fixture, expect `CLEANUP COMPLETE alreadyClean=true`.
Only after this boot smoke should the player fixture below be started.

## Player baseline

M39.2: boot/setup/capture are now runtime proven. Recover the previous failed
boarding fixture first, then create the fresh player fixture:

```mcfunction
/function sable:m39/cleanup
```

```mcfunction
/function sable:m39/player
```

Wait for READY. Click the generated Physics Assembler once.

```mcfunction
/function sable:m39/run
```

Native inner assembly stops for boarding. Wait for SEAT_CAPTURED first.
Require `RUN phase=BOARDING_WAIT_FOR_PLAYER`, then do nothing for 5-10 seconds
before right-clicking. The native Seat vehicle must remain assembled and stopped.
VEHICLE_DISCOVERY/VEHICLE_LOOKUP show its UUID, Level and removal status;
MOUNT_ATTEMPT comes from the read-only native Forge mount event observer.
Then stand on the generated parent boarding steps (south of Seat) and right-click
the visible Seat with an empty hand. Do not proceed to yaw/dismount until mount succeeds.
Native travel starts only after actual mount; extension, hold and return run
automatically. No teleport-to-seat command is used for the player.

During extension/hold:

```mcfunction
/function sable:m39/yaw90
```

Verify the rider follows new physical yaw with no look jump, snapback, hidden
flash or bogus impulse. Run completes with PLAYER ... PASS only from observed
native seat mapping, position samples, full test travel and safe dismount.

For normal-input dismount, repeat run and press sneak while seated. The helper
below is additionally packaged, not a substitute for testing sneak:

```mcfunction
/function sable:m39/dismount
```

An intentional early dismount makes the full-travel summary INCOMPLETE rather
than falsely claiming a continuous ride. The helper starts native retraction;
inspect the real detached player and safe parent position as the separate gate.

Cleanup can also be tested WHILE seated:

```mcfunction
/function sable:m39/cleanup
```

Requires CLEANUP COMPLETE, living player in parent world, vehicle=null. Repeat
cleanup to prove idempotency. After a failed run/reload, same cleanup command
uses persistent marker/body ownership, not a surviving Java run task.

Mob gate after player baseline:

```mcfunction
/function sable:m39/mob
```

Click assembler once, then `/function sable:m39/run`. A tagged parent-world
pig is automatically spawned and mounted via native addSittingPassenger.
Require MOB ... PASS and cleanup COMPLETE. No manual spawning/leashing.

READY proves static native attachment, not riding runtime PASS. Expected phases:
SEAT_CAPTURED -> MOUNT_SUCCESS -> PASSENGER_UPDATE -> FULL_TEST_EXTENSION ->
PLAYER/MOB summary. PositionError <= .25, parentWorldEntity=true,
hiddenPlotPosition=false, velocityValid=true, dismountPositionSafe=true,
vehicleNull=true. No runtime PASS is declared before real in-game player tests.
