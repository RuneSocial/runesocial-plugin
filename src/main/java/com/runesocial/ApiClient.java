package com.runesocial;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import java.util.HashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;


@Slf4j
@Singleton
public class ApiClient {

    private static final String BASE_URL = "https://runesocial.cloud/api";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Inject private OkHttpClient httpClient;
    @Inject private Gson gson;
    @Inject private RuneSocialPlugin plugin;  // ← agrega esto

    // Registers the player and returns their apiKey
    public String register(String username) {
        String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
        Request request = new Request.Builder()
                .url(BASE_URL + "/player/register?username=" + encoded)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            Map<?, ?> map = gson.fromJson(response.body().string(), Map.class);
            return (String) map.get("apiKey");
        } catch (IOException e) {
            log.error("Error registering player", e);
            return null;
        }
    }

    // Updates your profile on the server - only sends active pet
    public void updateProfile(String username, String apiKey, RuneSocialConfig config, String petId, String petName, String petColor) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("apiKey", apiKey);
        body.put("nameColor", colorToHex(config.nameColor()));
        body.put("movementEffect", config.movementEffect().name());
        body.put("emoticon", config.emoticon().name());
        body.put("colorEffect", config.colorEffect().name());

        if (petId != null && petName != null && !petName.isEmpty()) {
            body.put("pets", Map.of(petId, Map.of(
                    "name", petName,
                    "color", petColor
            )));
        }

        RequestBody requestBody = RequestBody.create(JSON, gson.toJson(body));
        Request request = new Request.Builder()
                .url(BASE_URL + "/player/update")
                .post(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                log.error("Error updating profile", e);
            }
            @Override public void onResponse(Call call, Response response) {
                if (response.code() == 429) {
                    try {
                        String body = response.body() != null ? response.body().string() : "";
                        Map<?, ?> map = gson.fromJson(body, Map.class);
                        String msg = (String) map.get("message");
                        if (msg != null) plugin.sendChatMessage("<col=ff0000>" + msg + "</col>");
                    } catch (Exception e) {
                        log.error("Error reading rate limit response", e);
                    }
                }
                response.close();
            }
        });
    }


    public RemoteConfig fetchConfig() {
        Request request = new Request.Builder()
                .url(BASE_URL + "/config")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            return gson.fromJson(response.body().string(), RemoteConfig.class);
        } catch (IOException e) {
            log.error("Error fetching remote config", e);
            return null;
        }
    }
    // Fetch your own profile from the server
    public PlayerProfile fetchOwnProfile(String username) {
        String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
        Request request = new Request.Builder()
                .url(BASE_URL + "/player/" + encoded)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            return gson.fromJson(response.body().string(), PlayerProfile.class);
        } catch (IOException e) {
            log.error("Error fetching own profile", e);
            return null;
        }
    }

    // Validate pet name on the server before saving
    public String validatePetName(String name, String username) {
        Map<String, String> body = Map.of("name", name, "username", username);
        RequestBody requestBody = RequestBody.create(JSON, gson.toJson(body));

        Request request = new Request.Builder()
                .url(BASE_URL + "/petname/validate")
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) return null;
            if (response.body() == null) return "Invalid or prohibited name.";
            Map<?, ?> map = gson.fromJson(response.body().string(), Map.class);
            return (String) map.get("error");
        } catch (IOException e) {
            log.error("Error validating pet name", e);
            return null;
        }
    }

    // Query nearby players
    public List<PlayerProfile> fetchPlayers(List<String> usernames) {
        Map<String, Object> body = Map.of("usernames", usernames);
        RequestBody requestBody = RequestBody.create(JSON, gson.toJson(body));

        Request request = new Request.Builder()
                .url(BASE_URL + "/players/batch")
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return List.of();
            return List.of(gson.fromJson(response.body().string(), PlayerProfile[].class));
        } catch (IOException e) {
            log.error("Error fetching nearby players", e);
            return List.of();
        }
    }

    private String colorToHex(java.awt.Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }
}