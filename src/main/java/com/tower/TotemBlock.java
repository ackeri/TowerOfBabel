package com.tower;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.world.IBlockAccess;
import net.minecraft.util.Facing;

/*
 * A decorative block defined in the totems config, meant to be stacked on a tower base as a skill block
 */
public class TotemBlock extends Block {
	public enum Kind {
		WOOD(Material.wood, soundTypeWood, "axe"),
		STONE(Material.rock, soundTypePiston, "pickaxe"),
		GLASS(Material.glass, soundTypeGlass, null);

		final Material material;
		final SoundType sound;
		final String tool;

		Kind(Material material, SoundType sound, String tool) {
			this.material = material;
			this.sound = sound;
			this.tool = tool;
		}
	}

	public final String id;
	private final Kind kind;

	public TotemBlock(String id, Kind kind, String texture, float hardness, int light) {
		super(kind.material);
		this.id = id;
		this.kind = kind;
		this.setBlockName("towerofbabel.totem_" + id);
		this.setBlockTextureName(texture);
		this.setCreativeTab(CreativeTabs.tabDecorations);
		this.setStepSound(kind.sound);
		this.setHardness(hardness);
		this.setResistance(hardness * 5);
		this.setLightLevel(light / 15f);
		if(kind.tool != null) {
			this.setHarvestLevel(kind.tool, 0);
		}
	}

	//glass totems have see-through pixels, so they render like vanilla glass

	@Override
	public boolean isOpaqueCube() {
		return kind != Kind.GLASS;
	}

	@Override
	public boolean renderAsNormalBlock() {
		return kind != Kind.GLASS;
	}

	@Override
	public boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
		if(kind == Kind.GLASS && world.getBlock(x, y, z) == this) {
			return false;
		}
		return super.shouldSideBeRendered(world, x, y, z, side);
	}
}
