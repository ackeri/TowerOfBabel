package com.tower.client;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import com.TowerOfBabel;

/*
 * A totem texture made while textures load, by carving a symbol into another block's texture.
 * Because it reads whatever texture that block currently has, it follows resource packs and other mods.
 *
 * Symbols are 16x16 text grids in assets/<domain>/symbols/<name>.txt:
 *   .  nothing         D  dark carving       L  light carving     K  deepest carving (eyes, mouths)
 *   W  raised outline  B  inlaid gem         R/T/M  red, tan and brown parts (carved like D, L, K)
 */
@SideOnly(Side.CLIENT)
public class CarvedSprite extends TextureAtlasSprite {
	// how much of the background's own texture shows through the carving; the rest is its average colour
	private static final double GRAIN = 0.35;
	private static final int GEM = 0x40BADE;

	private final String symbol;
	private final String[] backgrounds;
	private final float rim;

	public CarvedSprite(String name, String symbol, String[] backgrounds, float rim) {
		super(name);
		this.symbol = symbol;
		this.backgrounds = backgrounds;
		this.rim = rim;
	}

	@Override
	public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
		return true;
	}

	/*
	 * Returns false when the sprite loaded and should be stitched, despite what Forge's javadoc says
	 */
	@Override
	public boolean load(IResourceManager manager, ResourceLocation location) {
		char[][] grid;
		try {
			grid = loadSymbol(manager);
		} catch(Exception e) {
			TowerOfBabel.log.error("Couldn't read symbol " + symbol + " for " + getIconName() + ": " + e);
			grid = new char[16][16];
		}

		BufferedImage[] images = new BufferedImage[1 + Minecraft.getMinecraft().gameSettings.mipmapLevels];
		images[0] = carve(loadBackground(manager), grid);
		loadSprite(images, null, Minecraft.getMinecraft().gameSettings.anisotropicFiltering > 1);
		return false;
	}

	private char[][] loadSymbol(IResourceManager manager) throws Exception {
		ResourceLocation name = new ResourceLocation(symbol.contains(":") ? symbol : "towerofbabel:" + symbol);
		ResourceLocation file = new ResourceLocation(name.getResourceDomain(), "symbols/" + name.getResourcePath() + ".txt");
		BufferedReader in = new BufferedReader(new InputStreamReader(manager.getResource(file).getInputStream(), "UTF-8"));
		char[][] grid = new char[16][16];
		try {
			for(int y = 0; y < 16; y++) {
				String line = in.readLine();
				for(int x = 0; x < 16; x++) {
					grid[y][x] = line != null && x < line.length() ? line.charAt(x) : '.';
				}
			}
		} finally {
			in.close();
		}
		return grid;
	}

	/*
	 * The texture of the first background block that exists, read from its texture file
	 */
	private BufferedImage loadBackground(IResourceManager manager) {
		List<String> tried = new ArrayList<String>();
		for(String spec : backgrounds) {
			String[] parts = spec.trim().split(":");
			if(parts.length < 2) continue;
			String blockName = parts[0] + ":" + parts[1];
			if(!Block.blockRegistry.containsKey(blockName)) continue;
			try {
				int meta = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
				IIcon icon = ((Block)Block.blockRegistry.getObject(blockName)).getIcon(2, meta);
				ResourceLocation name = new ResourceLocation(icon.getIconName());
				ResourceLocation file = new ResourceLocation(name.getResourceDomain(), "textures/blocks/" + name.getResourcePath() + ".png");
				BufferedImage img = ImageIO.read(manager.getResource(file).getInputStream());
				//animated textures are a vertical strip of frames; use the first
				int size = Math.min(img.getWidth(), img.getHeight());
				return img.getSubimage(0, 0, size, size);
			} catch(Exception e) {
				tried.add(spec + " (" + e + ")");
			}
		}
		TowerOfBabel.log.warn("No usable background for " + getIconName() + ", using plain grey. Tried: " + tried);
		BufferedImage plain = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for(int y = 0; y < 16; y++) {
			for(int x = 0; x < 16; x++) {
				plain.setRGB(x, y, 0xFF808080);
			}
		}
		return plain;
	}

	/*
	 * Carves the symbol into the background, the way the hand made running rabbit totems were done: a darker
	 * recess with a shadowed top edge and a lit bottom edge, an optional pale rim, and gems inlaid
	 */
	private BufferedImage carve(BufferedImage bg, char[][] grid) {
		int size = bg.getWidth();
		double[] mean = new double[3];
		for(int y = 0; y < size; y++) {
			for(int x = 0; x < size; x++) {
				int c = bg.getRGB(x, y);
				mean[0] += (c >> 16) & 0xFF;
				mean[1] += (c >> 8) & 0xFF;
				mean[2] += c & 0xFF;
			}
		}
		for(int i = 0; i < 3; i++) {
			mean[i] /= size * size;
		}

		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		for(int y = 0; y < size; y++) {
			for(int x = 0; x < size; x++) {
				int gx = x * 16 / size, gy = y * 16 / size;
				char ch = grid[gy][gx];
				int base = bg.getRGB(x, y);
				int alpha = base & 0xFF000000;

				if(!isSymbol(ch)) {
					boolean edge = carved(grid, gx + 1, gy) || carved(grid, gx - 1, gy) || carved(grid, gx, gy + 1) || carved(grid, gx, gy - 1);
					out.setRGB(x, y, edge ? alpha | scale(base, rim) : base);
					continue;
				}
				if(ch == 'B') {
					out.setRGB(x, y, 0xFF000000 | scale(GEM, carved(grid, gx, gy - 1) ? 1.0 : 0.8));
					continue;
				}

				double f = shade(ch);
				if(ch != 'W') {
					if(!carved(grid, gx, gy - 1)) {
						f *= 0.72; //the top lip of the carving casts a shadow
					} else if(!carved(grid, gx, gy + 1)) {
						f *= 1.15; //the bottom edge catches the light
					}
				}
				int flat = 0;
				for(int i = 0; i < 3; i++) {
					int shift = 16 - 8 * i;
					double v = GRAIN * ((base >> shift) & 0xFF) + (1 - GRAIN) * mean[i];
					flat |= ((int)v & 0xFF) << shift;
				}
				out.setRGB(x, y, alpha | scale(flat, f));
			}
		}
		return out;
	}

	private static boolean isSymbol(char ch) {
		return ch != '.' && ch != 0 && "DLKWBRTM".indexOf(ch) >= 0;
	}

	private static boolean carved(char[][] grid, int x, int y) {
		return x >= 0 && x < 16 && y >= 0 && y < 16 && isSymbol(grid[y][x]) && grid[y][x] != 'W';
	}

	private static double shade(char ch) {
		switch(ch) {
			case 'L': case 'T': return 0.86;
			case 'K': case 'M': return 0.36;
			case 'W': return 1.30;
			default: return 0.62;
		}
	}

	private static int scale(int rgb, double f) {
		int out = 0;
		for(int shift = 0; shift <= 16; shift += 8) {
			int v = (int)Math.round(((rgb >> shift) & 0xFF) * f);
			out |= Math.max(0, Math.min(255, v)) << shift;
		}
		return out;
	}
}
