package com;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import com.babel.SkillCache;
import com.tower.client.Tooltips;

public class ClientProxy extends CommonProxy {
	@Override
	public void init() {
		FMLCommonHandler.instance().bus().register(this);
		Tooltips.register();
	}

	@Override
	public boolean isLocalPlayer(EntityPlayer p) {
		return p == Minecraft.getMinecraft().thePlayer;
	}

	@SubscribeEvent
	public void disconnect(ClientDisconnectionFromServerEvent e) {
		SkillCache.setClientGrants(null);
	}
}
