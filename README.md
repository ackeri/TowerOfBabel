# Tower of Babel
Build obelisks that give you skills. Primarily meant for building modpacks.

Minecraft 1.7.10, Forge 10.13.4.1614.

## How it works
Every item and block is locked behind skills. Players can only wear, hold, carry, break blocks with, attack with,
use, interact with, place or craft things their skills allow.

Skills come from towers. Placing a **Skill Tower Base** makes it yours (one per player). Every skill block stacked
directly on top of it applies to you immediately, and stops applying as soon as it is removed. The tower is read
upward until the first block that isn't a skill block. Right-click the base with an empty hand to see what it grants.

Recipe: a gold block surrounded by stone bricks.

The server decides everything and sends each player their own permissions whenever they change, so the client
refuses the same actions itself instead of showing blocks break, get placed or get crafted and then snap back.

Ops can also hand out skills directly: `/tower [player] <add|remove> <skill>` and `/tower [player] list`.

## Configuration
`config/TowerOfBabel.cfg` is generated with an example where iron and diamond tools and armor need the
`ironworking` and `diamondworking` skills. Every category other than `general` is a skill:

```
ironworking {
    S:block=minecraft:iron_block       # block that grants it, modid:name or modid:name:meta
    S:prereqs <                        # skills that must be lower in the same tower
     >
    I:heights <                        # tower levels it counts at (1 = directly on the base), empty = any
     >
    S:grants <                         # actions:regex over registry names
        all:minecraft:iron_(sword|shovel|pickaxe|axe|hoe|helmet|chestplate|leggings|boots)
     >
    B:dimensionSpecific=false          # only applies while the owner is in the tower's dimension
}
```

Actions are `all` or a comma list of `wear,hold,carry,break,attack,use,interact,place,craft`.
`general.defaultGrants` uses the same format for what every player can do without skills.

## Totems
`config/TowerOfBabel-totems.cfg` defines totem blocks to use as skill blocks. Each category becomes a block
registered as `TowerOfBabel:totem_<category>`, so a skill can use `S:block=TowerOfBabel:totem_bull_stone`.

```
bull_stone {
    S:name=Bull Totem (Stone)
    S:material=stone                   # wood, stone or glass (sound, tool, see-through)
    D:hardness=3.5
    I:light=0
    S:texture=towerofbabel:totem_bull_stone   # a resource pack can add textures for new totems
    S:recipe <                         # shaped recipe rows; leave empty for no recipe
        XXX
        XYX
        XXX
     >
    S:ingredients <                    # X=modid:name, X=modid:name:meta or X=ore:oreDictName
        X=ore:stone
        Y=TowerOfBabel:totem_bull_cobblestone
     >
}
```

The bundled totems come from pixel art drawn for the mod. Six symbols (running rabbit, sitting rabbit,
rabbit head, bull, tools and pipes) each come in wood, cobblestone and stone, and each level is crafted
from the one below it: logs around a themed item, then cobblestone around the wood totem, then stone around
the cobblestone one. There are also two alternate running rabbit drawings without recipes, and the magic
hand in glass.

### Totem art
`art/symbols/` holds each symbol as a 16x16 text grid transcribed from the original sketches, and
`art/materials/` the backgrounds they are carved into. After editing either, regenerate the textures with
`python3 art/carve.py` (needs Pillow). The hand drawn wood and stone running rabbits and the magic glass are
not generated and are never overwritten.

## Building
Needs Java 17 or newer to run Gradle; a Java 8 toolchain is used to compile the mod and is downloaded
automatically if it isn't installed.

```
./gradlew build        # mod jar in build/libs/TowerOfBabel-1.7.10-<version>.jar
./gradlew runClient    # or runServer, to test in a dev environment
```
