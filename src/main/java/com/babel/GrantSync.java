package com.babel;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/*
 * Sends a player everything they are currently allowed to do, so their client can make the same
 * decisions as the server instead of predicting actions the server will undo
 */
public class GrantSync implements IMessage {
	private List<Grant> grants = new ArrayList<Grant>();

	public GrantSync() {}

	public GrantSync(List<Grant> grants) {
		this.grants = grants;
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(grants.size());
		for(Grant g : grants) {
			buf.writeInt(g.actions);
			ByteBufUtils.writeUTF8String(buf, g.pattern.pattern());
		}
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		int n = buf.readInt();
		for(int i = 0; i < n; i++) {
			int actions = buf.readInt();
			grants.add(new Grant(actions, Pattern.compile(ByteBufUtils.readUTF8String(buf))));
		}
	}

	public static class Handler implements IMessageHandler<GrantSync, IMessage> {
		@Override
		public IMessage onMessage(GrantSync message, MessageContext ctx) {
			SkillCache.setClientGrants(message.grants);
			return null;
		}
	}
}
