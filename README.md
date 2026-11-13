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

A totem's texture is either an image (`texture`) or a symbol carved into another block's texture while the game
loads (`symbol` and `background`). Carving reads the background block's current texture, so it follows resource
packs and works with blocks from other mods. The first background block that exists is used, which lets a totem
use a mod's block when that mod is installed and fall back to a vanilla one otherwise:

```
magic_arcane {
    S:name=Arcane Magic Totem
    S:material=stone                   # wood, stone or glass (sound, tool, see-through)
    D:hardness=3.5
    I:light=0
    S:symbol=magic                     # assets/towerofbabel/symbols/magic.txt
    S:background <                     # first block that exists, as modid:name or modid:name:meta
        Thaumcraft:blockCosmeticSolid:6
        minecraft:stonebrick
     >
    D:rim=1.0                          # brightness of the outline; above 1 helps on busy textures
    S:recipe <                         # shaped recipe rows; leave empty for no recipe
        XXX
        XYX
        XXX
     >
    S:ingredients <                    # X=modid:name, X=modid:name:meta or X=ore:oreDictName
        X=minecraft:stonebrick
        Y=TowerOfBabel:totem_magic_glass
     >
}
```

The bundled totems come from pixel art drawn for the mod. Six symbols (running rabbit, sitting rabbit,
rabbit head, bull, tools and pipes) are each carved into oak log, cobblestone and stone, and each level is
crafted from the one below it. The running rabbit's wood and stone totems, two alternate drawings of it and
the glass magic hand are hand drawn textures, and the magic hand symbol is also carved into Thaumcraft's
arcane stone (or stone bricks without Thaumcraft).

Symbols are 16x16 text grids in `assets/towerofbabel/symbols/`, transcribed from the original sketches, and a
resource pack can add more. Each character is one pixel:
`.` nothing, `D` dark carving, `L` light carving, `K` deepest carving, `W` raised outline, `B` inlaid gem,
and `R`/`T`/`M` (the bull's red, tan and brown parts) carve like `D`, `L` and `K`.

## Building
Needs Java 17 or newer to run Gradle; a Java 8 toolchain is used to compile the mod and is downloaded
automatically if it isn't installed.

```
./gradlew build        # mod jar in build/libs/TowerOfBabel-1.7.10-<version>.jar
./gradlew runClient    # or runServer, to test in a dev environment
```
