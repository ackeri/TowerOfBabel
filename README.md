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
registered as `TowerOfBabel:totem_<category>`, so a skill can use `S:block=TowerOfBabel:totem_rabbit_wood`.

```
rabbit_stone {
    S:name=Running Rabbit Stone Totem
    S:material=stone                   # wood, stone or glass (sound, tool, see-through)
    D:hardness=3.0
    I:light=0
    S:texture=towerofbabel:totem_rabbit_stone   # a resource pack can add textures for new totems
    S:recipe <                         # shaped recipe rows; leave empty for no recipe
        SSS
        STS
        SSS
     >
    S:ingredients <                    # X=modid:name, X=modid:name:meta or X=ore:oreDictName
        S=ore:stone
        T=TowerOfBabel:totem_rabbit_wood
     >
}
```

The bundled totems use the pixel art drawn for the mod: the running rabbit in wood and stone (plus an
alternate drawing of each, without recipes) and the magic hand in glass. `art/` holds source art that
isn't a block yet, like the bare running rabbit symbol for carving into other materials.

## Building
Needs Java 17 or newer to run Gradle; a Java 8 toolchain is used to compile the mod and is downloaded
automatically if it isn't installed.

```
./gradlew build        # mod jar in build/libs/TowerOfBabel-1.7.10-<version>.jar
./gradlew runClient    # or runServer, to test in a dev environment
```
