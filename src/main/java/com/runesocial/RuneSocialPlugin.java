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
import net.runelite.client.events.ConfigChanged;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
		name = "RuneSocial",
		description = "A social plugin for OSRS players. Show your mood, name effects, and pet names to other players.",
		tags = {"social", "players", "pets", "community"}
)
public class RuneSocialPlugin extends Plugin
{
	private static final String CONFIG_GROUP = "RuneSocial";
	private static final String API_KEY_CONFIG = "apiKey";

	@Inject private Client client;
	@Inject private RuneSocialConfig config;
	@Inject private OverlayManager overlayManager;
	@Inject private NameOverlay nameOverlay;
	@Inject private ConfigManager configManager;
	@Inject private ChatboxPanelManager chatboxPanelManager;
	@Inject private ChatMessageManager chatMessageManager;
	@Inject private ApiClient apiClient;
	@Inject private net.runelite.client.callback.ClientThread clientThread;

	private int lastFollowerId = -1;
	private boolean askedForName = false;
	private String apiKey = null;
	public String activePetName = null;    // ← aquí
	public Color activePetColor = null;    // ← aquí

	private ScheduledExecutorService scheduler;

	// Nearby player profiles cache
	public final Map<String, PlayerProfile> nearbyProfiles = new HashMap<>();

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(nameOverlay);
		lastFollowerId = -1;        // ← agrega esto
		activePetName = null;       // ← agrega esto
		activePetColor = null;      // ← agrega esto
		scheduler = Executors.newSingleThreadScheduledExecutor();

		// Poll nearby players every 5 seconds
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
		activePetName = null;    // ← aquí
		activePetColor = null;   // ← aquí
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("RuneSocial")) return;

		clientThread.invoke(() -> {
			NPC follower = client.getFollower();
			String petId = follower != null ? String.valueOf(follower.getId()) : null;

			scheduler.submit(() -> {
				Player local = client.getLocalPlayer();
				if (local == null || apiKey == null) return;

				String username = local.getName();
				if (username == null) return;

				String petName = null;
				String petColor = String.format("#%02X%02X%02X",
						config.petNameColor().getRed(),
						config.petNameColor().getGreen(),
						config.petNameColor().getBlue());

				if (petId != null)
				{
					PlayerProfile profile = apiClient.fetchOwnProfile(username);
					if (profile != null && profile.pets != null)
					{
						PlayerProfile.PetInfo petData = profile.pets.get(petId);
						if (petData != null)
						{
							petName = petData.name;
							if (petData.color != null) petColor = petData.color;
						}
					}
				}

				apiClient.updateProfile(username, apiKey, config, petId, petName, petColor);
				activePetColor = config.petNameColor();  // ← aquí
			});
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)  // ← LOADING en vez de LOGGED_IN
		{
			lastFollowerId = -1;
			activePetName = null;
			activePetColor = null;
		}
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			scheduler.submit(this::registerPlayer);
		}
	}
	private void registerPlayer()
	{
		Player local = client.getLocalPlayer();
		if (local == null) return;

		String username = local.getName();
		if (username == null) return;

		String savedKey = configManager.getConfiguration(CONFIG_GROUP, API_KEY_CONFIG);
		if (savedKey != null && !savedKey.isEmpty())
		{
			apiKey = savedKey;
			syncProfile();
			return;
		}

		String newKey = apiClient.register(username);
		if (newKey != null)
		{
			apiKey = newKey;
			configManager.setConfiguration(CONFIG_GROUP, API_KEY_CONFIG, apiKey);
			syncProfile();
		}
	}

	// Sync your profile to the server - only sends active pet
	public void syncProfile()
	{

		Player local = client.getLocalPlayer();

		if (local == null || apiKey == null) return;

		String username = local.getName();
		if (username == null) return;

		NPC follower = client.getFollower();
		if (follower == null)
		{
			apiClient.updateProfile(username, apiKey, config, null, null, null);
			return;
		}

		// Fetch own profile to get active pet name
		scheduler.submit(() -> {
			PlayerProfile profile = apiClient.fetchOwnProfile(username);
			String petId = String.valueOf(follower.getId());
			String petName = null;
			String petColor = String.format("#%02X%02X%02X",
					config.petNameColor().getRed(),
					config.petNameColor().getGreen(),
					config.petNameColor().getBlue());

			if (profile != null && profile.pets != null)
			{
				PlayerProfile.PetInfo petData = profile.pets.get(petId);
				if (petData != null)
				{
					petName = petData.name;
					String savedColor = petData.color;
					if (savedColor != null) petColor = savedColor;
				}
			}

			apiClient.updateProfile(username, apiKey, config, petId, petName, petColor);
		});
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

		// Fetch pet name from server
		scheduler.submit(() -> {
			Player local = client.getLocalPlayer();
			if (local == null) return;


			PlayerProfile profile = apiClient.fetchOwnProfile(local.getName());


			String petId = String.valueOf(followerId);
			String existingName = null;
			String existingColor = null;
			log.debug("petId buscado: {}", petId);  // ← luego el log
			log.debug("pets en perfil: {}", profile != null && profile.pets != null ? profile.pets.keySet() : "null");

			if (profile != null && profile.pets != null)
			{
				PlayerProfile.PetInfo petData = profile.pets.get(petId);
				if (petData != null)
				{
					existingName = petData.name;
					existingColor = petData.color;
				}
			}

			activePetName = existingName;  // ← aquí
			activePetColor = existingColor != null  // ← aquí
					? hexToColor(existingColor)
					: config.petNameColor();

			final String finalName = existingName;
			log.debug("askedForName: {}", askedForName);
			log.debug("finalName: {}", finalName);
			if (!askedForName)
			{
				askedForName = true;
				if (finalName == null || finalName.isEmpty())
				{
					clientThread.invokeLater(() -> askPetName(follower));
				}
				else
				{
					sendChatMessage("Your pet's name is: <col=ff00ff>" + finalName + "</col>. Type ::petname to change it.");
				}
			}
		});
	}

	private void askPetName(NPC follower)
	{
		String petDisplayName = follower.getName() != null ? follower.getName() : "your pet";
		int followerId = follower.getId();

		chatboxPanelManager.openTextInput("What is your pet's name? (" + petDisplayName + ") (leave empty to skip)")
				.onDone((input) -> {
					String trimmed = Text.removeTags(input).trim();
					if (trimmed.isEmpty()) return;

					scheduler.submit(() -> {

						Player local = client.getLocalPlayer();
						if (local == null || apiKey == null) return;

						String username = local.getName();
						if (username == null) return;


						boolean isValid = apiClient.validatePetName(trimmed, username);
						if (!isValid)
						{
							sendChatMessage("<col=ff0000>Invalid or prohibited name. Type <col=ff00ff>::petname</col><col=ff0000> to try again.</col>");
							askedForName = false;  // ← permite volver a intentar
							return;
						}

						String petColor = String.format("#%02X%02X%02X",
								config.petNameColor().getRed(),
								config.petNameColor().getGreen(),
								config.petNameColor().getBlue());

						apiClient.updateProfile(username, apiKey, config,
								String.valueOf(followerId), trimmed, petColor);

						activePetName = trimmed;
						activePetColor = config.petNameColor();

						sendChatMessage("Name saved: <col=ff00ff>" + trimmed + "</col> for " + petDisplayName + ".");
					});
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

	private Color hexToColor(String hex)  // ← aquí
	{
		try {
			return Color.decode(hex);
		} catch (Exception e) {
			return config.petNameColor();
		}
	}

	@Provides
	RuneSocialConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneSocialConfig.class);
	}
}