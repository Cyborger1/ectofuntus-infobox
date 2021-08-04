/*
 * Copyright (c) 2021, Cyborger1
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.ectofuntusinfo;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.util.Arrays;
import java.util.Set;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

@Slf4j
@PluginDescriptor(
	name = "Ectofuntus Info",
	description = "Tells you how many Ecto-tokens you can collect",
	tags = {"Ecto", "Token", "Ectotoken", "Ecto-token", "Funtus", "Ectofuntus"}
)
public class EctofuntusInfoPlugin extends Plugin
{
	private static final Set<Integer> ECTOFUNTUS_REGIONS = ImmutableSet.of(14647, 14746);
	private static final int LOWER_TOKEN_VARBIT = 4769;
	private static final int UPPER_TOKEN_VARBIT = 5671;

	public static final int TOKENS_PER_BONEMEAL = 5;
	public static final int MAX_TOKEN_AMOUNT = 1000;
	public static final int HIGH_WARN_TOKEN_AMOUNT = MAX_TOKEN_AMOUNT - (13 * TOKENS_PER_BONEMEAL);
	public static final int LOW_WARN_TOKEN_AMOUNT = MAX_TOKEN_AMOUNT - (39 * TOKENS_PER_BONEMEAL);

	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private EctofuntusInfoConfig config;

	private EctofuntusInfobox infobox;

	@Getter
	private boolean inEctofuntusRegion;
	@Getter
	private int storedTokens;

	@Provides
	EctofuntusInfoConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(EctofuntusInfoConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		inEctofuntusRegion = checkEctofuntusRegion();
		storedTokens = 0;
		setupInfoBox();
	}

	@Override
	protected void shutDown() throws Exception
	{
		inEctofuntusRegion = false;
		storedTokens = 0;
		infoBoxManager.removeInfoBox(infobox);
		infobox = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOADING:
				inEctofuntusRegion = checkEctofuntusRegion();
				break;
			case LOGIN_SCREEN:
				inEctofuntusRegion = false;
				storedTokens = 0;
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!inEctofuntusRegion)
		{
			return;
		}

		// Modulo in-case it ever gets fixed? It would break the plugin.
		int lower = client.getVarbitValue(LOWER_TOKEN_VARBIT) % 64;
		int upper = client.getVarbitValue(UPPER_TOKEN_VARBIT);

		storedTokens = (upper * 256) + ((lower + upper) % 5) * 64 + lower;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(EctofuntusInfoConfig.CONFIG_GROUP)
			&& event.getKey().equals(EctofuntusInfoConfig.COUNTER_TYPE_KEY_NAME))
		{
			setupInfoBox();
		}
	}

	private boolean checkEctofuntusRegion()
	{
		GameState gameState = client.getGameState();
		if (gameState != GameState.LOGGED_IN
			&& gameState != GameState.LOADING)
		{
			return false;
		}

		return Arrays.stream(client.getMapRegions()).anyMatch(ECTOFUNTUS_REGIONS::contains);
	}

	private void setupInfoBox()
	{
		int item;
		if (config.counterType() == EctofuntusCounterType.BONEMEAL)
		{
			item = ItemID.BONEMEAL;
		}
		else
		{
			item = ItemID.ECTOTOKEN;
		}

		if (infobox != null)
		{
			infoBoxManager.removeInfoBox(infobox);
		}

		infobox = new EctofuntusInfobox(itemManager.getImage(item, 10, false), this, config);
		infoBoxManager.addInfoBox(infobox);
	}
}
