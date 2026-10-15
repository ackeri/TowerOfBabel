"""Carves the totem symbols into each material, the way the hand-made running rabbit totems were done.

Symbols are 16x16 text grids in symbols/, transcribed from the original graph paper sketches:
  .  nothing         D  dark carving       L  light carving     K  deepest carving (eyes, mouths)
  W  raised outline  B  inlaid gem         R/T/M  red, tan and brown parts of the bull (carved like D, L, K)
Materials are the 16x16 backgrounds in materials/.

Run from the repository root:  python3 art/carve.py
Writes src/main/resources/assets/towerofbabel/textures/blocks/totem_<symbol>_<material>.png, skipping any
texture listed in HANDMADE so hand drawn versions are never overwritten.
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'towerofbabel', 'textures', 'blocks')
MATERIALS = ['wood', 'cobblestone', 'stone']
HANDMADE = {'running_rabbit_wood', 'running_rabbit_stone'}

# how much each kind of pixel darkens (or lightens) the material underneath
SHADE = {'D': 0.62, 'R': 0.62, 'L': 0.86, 'T': 0.86, 'K': 0.36, 'M': 0.36, 'W': 1.30}
# pale rim around the carving, strongest on busy materials so the shape stands out
RIM = {'wood': 1.30, 'cobblestone': 1.22, 'stone': 1.0}
# how much of the material's own texture shows through the carving; the rest is its average colour
GRAIN = 0.35
GEM = (64, 186, 222)


def load_symbol(path):
    rows = [line.rstrip('\n') for line in open(path)][:16]
    return [row.ljust(16, '.')[:16] for row in rows]


def scale(rgb, f):
    return tuple(max(0, min(255, int(round(c * f)))) for c in rgb[:3]) + (255,)


def carve(bg, grid, material):
    out = bg.copy()
    pixels = [bg.getpixel((x, y)) for y in range(16) for x in range(16)]
    mean = tuple(sum(p[i] for p in pixels) / len(pixels) for i in range(3))

    def carved(x, y):
        return 0 <= x < 16 and 0 <= y < 16 and grid[y][x] not in '.W'

    for y in range(16):
        for x in range(16):
            ch = grid[y][x]
            base = bg.getpixel((x, y))
            if ch == '.':
                if RIM[material] != 1.0 and any(carved(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    out.putpixel((x, y), scale(base, RIM[material]))
                continue
            if ch == 'B':
                f = 0.8 if not carved(x, y - 1) else 1.0
                out.putpixel((x, y), scale(GEM, f))
                continue
            f = SHADE[ch]
            if ch != 'W':
                if not carved(x, y - 1):
                    f *= 0.72  # the top lip of the carving casts a shadow
                elif not carved(x, y + 1):
                    f *= 1.15  # the bottom edge catches the light
            flat = tuple(GRAIN * b + (1 - GRAIN) * m for b, m in zip(base[:3], mean))
            out.putpixel((x, y), scale(flat, f))
    return out


def main():
    for name in sorted(os.listdir(os.path.join(HERE, 'symbols'))):
        if not name.endswith('.txt'):
            continue
        symbol = name[:-4]
        grid = load_symbol(os.path.join(HERE, 'symbols', name))
        for material in MATERIALS:
            texture = symbol + '_' + material
            if texture in HANDMADE:
                continue
            bg = Image.open(os.path.join(HERE, 'materials', material + '.png')).convert('RGBA')
            carve(bg, grid, material).save(os.path.join(OUT, 'totem_' + texture + '.png'))
            print('wrote totem_' + texture + '.png')


if __name__ == '__main__':
    main()
