package com.runesocial;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import java.util.HashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
public class ApiClient {

    private static final String BASE_URL = "https://runesocial.cloud/api";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Inject private OkHttpClient httpClient;
    @Inject private Gson gson;

    // Registra al jugador y devuelve su apiKey
    public String register(String username) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/player/register?username=" + username)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            Map<?, ?> map = gson.fromJson(response.body().string(), Map.class);
            return (String) map.get("apiKey");
        } catch (IOException e) {
            log.error("Error registrando jugador", e);
            return null;
        }
    }

    // Actualiza tu perfil en el servidor
    public void updateProfile(String username, String apiKey, RuneSocialConfig config, Map<String, Map<String, String>> pets) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("apiKey", apiKey);
        body.put("nameColor", colorToHex(config.nameColor()));
        body.put("movementEffect", config.movementEffect().name());
        body.put("emoticon", config.emoticon().name());
        body.put("colorEffect", config.colorEffect().name());
        body.put("pets", pets);

        RequestBody requestBody = RequestBody.create(JSON, gson.toJson(body));
        Request request = new Request.Builder()
                .url(BASE_URL + "/player/update")
                .post(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                log.error("Error actualizando perfil", e);
            }
            @Override public void onResponse(Call call, Response response) {
                response.close();
            }
        });
    }

    // Consulta jugadores cercanos
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
            log.error("Error consultando jugadores", e);
            return List.of();
        }
    }

    private String colorToHex(java.awt.Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }
}