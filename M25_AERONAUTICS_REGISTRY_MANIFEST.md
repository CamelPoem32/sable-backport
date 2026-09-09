# M25 Aeronautics Registry Manifest

| Registry/config surface | M25 count | Entries |
| --- | ---: | --- |
| Blocks | 1 | `aeronautics:levitite` |
| Items | 1 | `aeronautics:levitite` |
| Block entities | 0 | Levitite is an ordinary block upstream |
| Entities | 0 | Deferred |
| Menus | 0 | Not required |
| Recipe types | 0 | Deferred |
| Recipe serializers | 0 | Deferred |
| Creative tabs | 1 | `aeronautics:main_tab` |
| Network channels | 1 | `aeronautics:main`, protocol `m25-bootstrap` |
| Network packets | 0 | Frozen crystallization packet belongs to deferred Levitite Blend production |
| Configs | 1 | Forge common spec; only gates the test fixture and does not tune lift |

The exact frozen Levitite lift constants are data-driven, not config values. M25 therefore does not port unrelated `AeroPhysics` propulsion or hot-air settings. Runtime dependencies are Forge, Minecraft, Create, Sable, and Simulated.
