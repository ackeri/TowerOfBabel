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

No tower can get too far ahead: a tower holds at most `general.towerVariance` (default 3) more skills than the
shortest other tower, and totems above that don't count until the others catch up. Only towers of players who
have been online in the last `general.towerActiveDays` (default 7) count, so someone who stopped playing doesn't
hold everyone back. `general.towerCapacity` adds a fixed limit as well (off by default).

`/tower who [skill]` lists who has which skills and `/tower [player] list` shows a player's skills; anyone can
use those. Ops can hand out skills directly with `/tower [player] <add|remove> <skill>`, and `/tower reload`
re-reads the skills config without a restart (new totem blocks still need one).

## Playing together
The mod is built to push a group of players into different roles and then make them need each other:

- **Scarcity:** no tower can be more than a few skills taller than the shortest, and skills can exclude each other,
  so nobody can learn everything and every player's tower is a choice. When a new player joins, everyone's towers
  are held back until they catch up, which gives veterans a reason to help them, for instance by giving them totems.
- **Interdependence:** making and using are separate skills. In the default config smiths forge iron and
  diamond gear but can't use it, and the ironclad can use it but not make it, so gear has to change hands.
- **Discoverability:** when an action is blocked the message names the skill needed and who already has it,
  e.g. "Cannot carry Iron Sword (needs ironclad or smithing; Alice has it)", and `/tower who` lists everyone.
  A full tower says whose tower is holding it back.

The default config is an example of that: `smithing`/`mastersmithing` (tools totems) make iron/diamond gear,
`ironclad`/`diamondclad` (iron and diamond blocks) use it, and `swiftness` (the running rabbit) and `might`
(the bull) exclude each other.

## Configuration
`config/TowerOfBabel.cfg` is generated with the example above. Every category other than `general` is a skill:

```
ironclad {
    S:block=minecraft:iron_block       # block that grants it, modid:name or modid:name:meta
    S:prereqs <                        # skills that must be lower in the same tower
     >
    S:excludes <                       # skills that can't share a tower with this one
     >
    I:heights <                        # tower levels it counts at (1 = directly on the base), empty = any
     >
    S:grants <                         # actions:regex over registry names
        wear,hold,carry,break,attack,use:minecraft:iron_(sword|shovel|pickaxe|axe|hoe|helmet|chestplate|leggings|boots)
     >
    B:dimensionSpecific=false          # only applies while the owner is in the tower's dimension
}
```

Skills can also give potion effects while active, like a beacon, and a description shown on tooltips:

```
might {
    S:block=TowerOfBabel:totem_bull_wood
    S:effects <                        # name or name:level, e.g. speed, strength:2, night_vision, haste
        strength
     >
    S:description=The bull: Strength
}
```

Hovering a skill block shows which skill it grants, what it does and where it has to go in the tower, and
any item the player is locked out of says which skill they need. Blocked actions say the same in chat,
along with who has it. Active totems send enchanting glyphs down to the base.

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
