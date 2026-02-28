package com.runesocial;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.GameState;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(name = "RuneSocial")
public class RuneSocialPlugin extends Plugin
{
	private static final String CONFIG_GROUP = "RuneSocial";
	private static final String API_KEY_CONFIG = "apiKey";

	@Inject private Client client;
	@Inject private RuneSocialConfig config;
	@Inject private OverlayManager overlayManager;
	@Inject private NameOverlay nameOverlay;
	@Inject private PetNameManager petNameManager;
	@Inject private ConfigManager configManager;
	@Inject private ChatboxPanelManager chatboxPanelManager;
	@Inject private ChatMessageManager chatMessageManager;
	@Inject private ApiClient apiClient;

	private int lastFollowerId = -1;
	private boolean askedForName = false;
	private String apiKey = null;

	private ScheduledExecutorService scheduler;

	// Nearby player profiles cache
	public final Map<String, PlayerProfile> nearbyProfiles = new HashMap<>();

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(nameOverlay);
		scheduler = Executors.newSingleThreadScheduledExecutor();

		// Polling every 5 seconds
		scheduler.scheduleAtFixedRate(this::pollNearbyPlayers, 5, 5, TimeUnit.SECONDS);
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(nameOverlay);
		if (scheduler != null) scheduler.shutdown();
		nearbyProfiles.clear();
		lastFollowerId = -1;
		askedForName = false;
		apiKey = null;
	}



	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			// Register player when first logged in
			scheduler.submit(this::registerPlayer);
		}
	}

	private void registerPlayer()
	{
		Player local = client.getLocalPlayer();
		if (local == null) return;

		String username = local.getName();
		if (username == null) return;

		// Check if we already have a saved apiKey
		String savedKey = configManager.getConfiguration(CONFIG_GROUP, API_KEY_CONFIG);
		if (savedKey != null && !savedKey.isEmpty())
		{
			apiKey = savedKey;
			syncProfile();
			return;
		}

		// Register on the server
		String newKey = apiClient.register(username);
		if (newKey != null)
		{
			apiKey = newKey;
			configManager.setConfiguration(CONFIG_GROUP, API_KEY_CONFIG, apiKey);
			syncProfile();
		}
	}

	// Sync your profile to the server
	public void syncProfile()
	{
		Player local = client.getLocalPlayer();
		if (local == null || apiKey == null) return;

		String username = local.getName();
		if (username == null) return;

		// Build pet map
		Map<String, Map<String, String>> pets = new HashMap<>();
		for (NPC npc : client.getNpcs())
		{
			if (npc == null) continue;
			var comp = npc.getComposition();
			if (comp != null && comp.isFollower())
			{
				int id = npc.getId();
				String petName = petNameManager.getPetName(id);
				Color petColor = petNameManager.getPetColor(id, config.petNameColor());
				if (!petName.isEmpty())
				{
					pets.put(String.valueOf(id), Map.of(
							"name", petName,
							"color", String.format("#%02X%02X%02X",
									petColor.getRed(), petColor.getGreen(), petColor.getBlue())
					));
				}
			}
		}

		apiClient.updateProfile(username, apiKey, config, pets);
	}


	// Query nearby players and update cache
	private void pollNearbyPlayers()
	{
		if (client.getGameState() != GameState.LOGGED_IN) return;

		List<Player> players = client.getPlayers();
		if (players == null || players.isEmpty()) return;

		List<String> usernames = new ArrayList<>();
		for (Player p : players)
		{
			if (p == null || p == client.getLocalPlayer()) continue;
			String name = p.getName();
			if (name != null) usernames.add(name);
		}

		if (usernames.isEmpty()) return;

		List<PlayerProfile> profiles = apiClient.fetchPlayers(usernames);
		synchronized (nearbyProfiles)
		{
			nearbyProfiles.clear();
			for (PlayerProfile profile : profiles)
			{
				nearbyProfiles.put(profile.username, profile);
			}
		}
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (!event.getCommand().equalsIgnoreCase("petname")) return;

		NPC follower = client.getFollower();
		if (follower == null)
		{
			sendChatMessage("You don't have any pet following you.");
			return;
		}

		askPetName(follower);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (apiKey == null)
		{
			scheduler.submit(this::registerPlayer);
		}
		NPC follower = client.getFollower();

		if (follower == null)
		{
			lastFollowerId = -1;
			askedForName = false;
			return;
		}

		int followerId = follower.getId();
		if (followerId == lastFollowerId) return;

		lastFollowerId = followerId;
		askedForName = false;

		String existingName = petNameManager.getPetName(followerId);

		if (!askedForName)
		{
			askedForName = true;
			if (existingName.isEmpty())
			{
				askPetName(follower);
			}
			else
			{
				sendChatMessage("Your pet's name is: <col=ff00ff>" + existingName + "</col>. Type ::petname to change it.");
			}
		}
	}

	private void askPetName(NPC follower)
	{
		String petDisplayName = follower.getName() != null ? follower.getName() : "your pet";
		sendChatMessage("Type <col=ff00ff>::petname</col> to assign a name to your pet.");


		chatboxPanelManager.openTextInput("What is your pet's name? (" + petDisplayName + ") (leave empty to skip)")
				.onDone((input) -> {
					String trimmed = Text.removeTags(input).trim();
					if (!trimmed.isEmpty())
					{
						petNameManager.setPetName(follower.getId(), trimmed);
						sendChatMessage("Name saved: <col=ff00ff>" + trimmed + "</col> for " + petDisplayName + ".");
						syncProfile();
					}
				})
				.build();
	}

	private void sendChatMessage(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.GAMEMESSAGE)
				.runeLiteFormattedMessage(message)
				.build());
	}

	@Provides
	RuneSocialConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneSocialConfig.class);
	}
}