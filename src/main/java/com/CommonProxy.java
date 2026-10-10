package com;

import net.minecraft.entity.player.EntityPlayer;

/*
 * Side specific hooks; the dedicated server uses this one and the client uses ClientProxy
 */
public class CommonProxy {
	public void init() {}

	/*
	 * Whether p is the player this game client is controlling
	 */
	public boolean isLocalPlayer(EntityPlayer p) {
		return false;
	}
}
