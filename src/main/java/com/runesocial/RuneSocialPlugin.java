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


class RemoteConfig {
	public int maxTiles = 5;
	public int maxPlayers = 20;
	public int pollInterval = 5000;
	public String motd;
	public String playerIndicatorsWarning;
}

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
	@Inject private net.runelite.client.plugins.PluginManager pluginManager;

	private int lastFollowerId = -1;
	private boolean askedForName = false;
	private String apiKey = null;
	public String activePetName = null;
	public Color activePetColor = null;
	public RemoteConfig remoteConfig = new RemoteConfig();

	// FIX: Track which pet IDs already showed the name message this session
	private final Set<String> shownPetNameIds = new HashSet<>();
	// FIX: Only show playerIndicatorsWarning once per login session
	private boolean hasShownIndicatorsWarning = false;

	private ScheduledExecutorService scheduler;

	// Nearby player profiles cache
	public final Map<String, PlayerProfile> nearbyProfiles = new HashMap<>();

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(nameOverlay);
		lastFollowerId = -1;
		activePetName = null;
		activePetColor = null;
		shownPetNameIds.clear();
		hasShownIndicatorsWarning = false;
		scheduler = Executors.newSingleThreadScheduledExecutor();

		scheduler.submit(() -> {
			RemoteConfig fetched = apiClient.fetchConfig();
			if (fetched != null) remoteConfig = fetched;

			scheduler.scheduleAtFixedRate(this::pollNearbyPlayers,
					remoteConfig.pollInterval / 1000,
					remoteConfig.pollInterval / 1000,
					TimeUnit.SECONDS);
		});
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
		activePetName = null;
		activePetColor = null;
		shownPetNameIds.clear();
		hasShownIndicatorsWarning = false;
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
						}
					}
				}

				apiClient.updateProfile(username, apiKey, config, petId, petName, petColor);
				activePetColor = config.petNameColor();
			});
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			lastFollowerId = -1;
			activePetName = null;
			activePetColor = null;
			// FIX: DO NOT clear shownPetNameIds here — that caused the spam on boat/zone change.
			// We only clear it on full re-login (LOGGED_IN after being at LOGIN_SCREEN).
		}
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			scheduler.submit(this::registerPlayer);

			if (remoteConfig != null && remoteConfig.motd != null && !remoteConfig.motd.isEmpty())
			{
				sendChatMessage(remoteConfig.motd);
			}

			// FIX: Only show playerIndicatorsWarning once per login session
			if (!hasShownIndicatorsWarning)
			{
				pluginManager.getPlugins().stream()
						.filter(p -> p.getClass().getSimpleName().equals("PlayerIndicatorsPlugin"))
						.filter(p -> pluginManager.isPluginEnabled(p))
						.findFirst()
						.ifPresent(p -> {
							if (remoteConfig != null && remoteConfig.playerIndicatorsWarning != null)
							{
								sendChatMessage(remoteConfig.playerIndicatorsWarning);
								hasShownIndicatorsWarning = true;
							}
						});
			}
		}

		// FIX: Reset session flags when player is at login screen (actual logout/relog)
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			shownPetNameIds.clear();
			hasShownIndicatorsWarning = false;
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

		scheduler.submit(() -> {
			PlayerProfile profile = apiClient.fetchOwnProfile(username);

			if (profile != null && profile.petNameStatus != null && !profile.petNameStatus.isEmpty()) {
				clientThread.invokeLater(() ->
						sendChatMessage("<col=ffff00>[RuneSocial]</col> " + profile.petNameStatus)
				);
			}
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

	private void pollNearbyPlayers()
	{
		if (client.getGameState() != GameState.LOGGED_IN) return;

		List<Player> players = client.getPlayers();
		if (players == null || players.isEmpty()) return;

		Player local = client.getLocalPlayer();
		if (local == null) return;

		List<String> usernames = players.stream()
				.filter(p -> p != null && p != local && p.getName() != null)
				.filter(p -> p.getWorldLocation().distanceTo(local.getWorldLocation()) <= remoteConfig.maxTiles)
				.sorted((a, b) -> {
					int distA = a.getWorldLocation().distanceTo(local.getWorldLocation());
					int distB = b.getWorldLocation().distanceTo(local.getWorldLocation());
					return Integer.compare(distA, distB);
				})
				.limit(remoteConfig.maxPlayers)
				.map(Player::getName)
				.collect(java.util.stream.Collectors.toList());

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
			if (profile != null && profile.petNameStatus != null && !profile.petNameStatus.isEmpty()) {
				clientThread.invokeLater(() ->
						sendChatMessage("<col=ffff00>[RuneSocial]</col> " + profile.petNameStatus)
				);
			}

			String petId = String.valueOf(followerId);
			String existingName = null;
			String existingColor = null;
			log.debug("petId buscado: {}", petId);
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

			activePetName = existingName;
			activePetColor = existingColor != null
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
					// FIX: Only show "Your pet's name is" message once per pet per session
					if (!shownPetNameIds.contains(petId))
					{
						shownPetNameIds.add(petId);
						sendChatMessage("Your pet's name is: <col=ff00ff>" + finalName + "</col>. Type ::petname to change it.");
					}
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

						String nameError = apiClient.validatePetName(trimmed, username);
						if (nameError != null)
						{
							sendChatMessage("<col=ff0000>" + nameError + "</col>");
							askedForName = false;
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

						// FIX: Mark this pet as shown after naming it
						shownPetNameIds.add(String.valueOf(followerId));

						sendChatMessage("Name saved: <col=ff00ff>" + trimmed + "</col> for " + petDisplayName + ".");
					});
				})
				.build();
	}

	public void sendChatMessage(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.GAMEMESSAGE)
				.runeLiteFormattedMessage(message)
				.build());
	}

	private Color hexToColor(String hex)
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