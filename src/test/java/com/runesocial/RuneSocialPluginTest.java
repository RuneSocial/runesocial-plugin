package com.runesocial;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

import com.runesocial.RuneSocialPlugin;
import com.salvageafk.SalvageAfkPlugin;

public class RuneSocialPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(
				RuneSocialPlugin.class,
				SalvageAfkPlugin.class
		);

		RuneLite.main(args);
	}
}