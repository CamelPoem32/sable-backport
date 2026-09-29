# M36 Runtime Test Commands

Use cheats/operator permission. Stand in an open area with a clear box at least 31 blocks east-west, 7 blocks tall and 5 blocks north-south. The yaw test also needs clear space about 15 blocks north of the fixture, potentially up to 16 blocks above it. The setup function builds the entire Create piston, Sable assembler, moving interface, mounted storage, stationary interface, and native transfer equipment four blocks above your feet. No blocks, glue, items, or fluids need to be placed manually.

## Item interface

```mcfunction
/function sable:m36/item
```

Wait for `[M36] SETUP phase=READY outerSelectedBlocks=22`. Click the generated Physics Assembler above the two iron hull blocks once. Its starting block is the iron block directly beneath it. Then run:

```mcfunction
/function sable:m36/run_item
```

The moving chest begins with 16 cobblestone. The stationary Portable Storage Interface has a hopper underneath feeding an empty destination chest. PASS requires native connection plus a lower moving source count and a higher parent-world destination count. The `[M36] ITEM ... PASS` line is emitted only after those states are observed. If it says `INCOMPLETE`, inspect `SABLE_M36_PSI` with `-Dsable.m31.traceCreateActors=true` and do not classify M36 as runtime PASS.

To test a level 90-degree yaw after the first run, use:

```mcfunction
/function sable:m36/yaw90
```

Wait for `[M36] YAW90 COMPLETE`. The helper disconnects and retracts the native Create piston, moves only the tagged M36 Sable and its command-owned stationary equipment, and refills the finite mounted source. It leaves the piston retracted and stopped. Then run the same native test again:

```mcfunction
/function sable:m36/run_item
```

The second `ITEM ... PASS` must come from a fresh native connection and actual transfer at the new north-facing physical station. The old station is removed, so it cannot remain a stale connection. If a destination cell contains an unrelated block, yaw refuses before changing the body or station and prints its exact position and blockstate.

To move the body out of connection range without moving the stationary interface:

```mcfunction
/function sable:m36/disconnect
```

## Fluid interface

Use a separate open area after cleaning up the item fixture.

```mcfunction
/function sable:m36/fluid
```

The moving Create tank is filled with 4000 mB water by the setup validator. The stationary Portable Fluid Interface is backed by a powered native Mechanical Pump and an empty Create tank. Wait for `[M36] SETUP phase=READY outerSelectedBlocks=22`, click the Physics Assembler once, then run:

```mcfunction
/function sable:m36/run_fluid
```

PASS requires native `canTransfer`, a decrease in the moving tank and an increase in the stationary parent-world tank. A visual extension alone is not PASS. After the first PASS, run `/function sable:m36/yaw90`, wait for `YAW90 COMPLETE station=<new> facing=north source=4000 destination=0`, then run `/function sable:m36/run_fluid` again. The helper inspects the five fixture-owned old station components, then refills the mounted tank to 4000 mB for this second native transfer. The second run must report a new native connection and `FLUID ... transferred=true hiddenPlotHandshake=false PASS`. The disconnect helper above also applies to this fixture.

## Teardown

After either test, use one command:

```mcfunction
/function sable:m36/cleanup
```

The helper stops the tagged native piston, powers only the fixture-owned stationary interface to make Create disconnect, reverses its Creative Motor, waits for native retraction, disassembles the outer Sable if it still exists, then removes known M36 fixture blocks and markers. Wait for `[M36] CLEANUP COMPLETE` before setting up the other fixture. If a bounded timeout occurs, the marker remains recoverable: run the same cleanup command again. It will inspect the current world and skip stages that have already completed. Repeating cleanup after completion reports that the fixture is already absent.

After installing the M36.1 repair over a world with the earlier partially cleaned fluid fixture, run `/function sable:m36/cleanup` first. Do not break the marker or controller blocks manually. The cleanup entry line reports the detected state and native piston/connection values; an unexpected exception is also written with its stack trace to `latest.log`.

For an already disassembled and fully retracted fixture, this optional helper restores the finite source and empty destination:

```mcfunction
/function sable:m36/reset
```
