package andrew.slchannelpointmod.twitch;

import andrew.slchannelpointmod.SLChannelPointMod;
import andrew.slchannelpointmod.config.ModConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;

public class TwitchAPI {

    private static final String PRODUCTION_API = "https://api.twitch.tv/helix";
    // Twitch CLI mock-api uses /mock prefix
    private static final String TEST_API = "http://localhost:8080/mock";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private static String getApiBase() {
        return TwitchEventSub.isTestMode() ? TEST_API : PRODUCTION_API;
    }

    public static void createReward(String title, int cost, int cooldownSeconds, Consumer<String> callback) {
        String token = ModConfig.get().getAccessToken();
        String channelId = ModConfig.get().getChannelId();

        if (token == null || token.isEmpty() || channelId == null || channelId.isEmpty()) {
            SLChannelPointMod.LOGGER.error("Not authenticated, cannot create reward");
            callback.accept(null);
            return;
        }

        try {
            JsonObject body = new JsonObject();
            body.addProperty("title", title);
            body.addProperty("cost", cost);
            body.addProperty("is_enabled", true);
            body.addProperty("is_user_input_required", false);

            // Add cooldown if specified
            if (cooldownSeconds > 0) {
                body.addProperty("is_global_cooldown_enabled", true);
                body.addProperty("global_cooldown_seconds", cooldownSeconds);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getApiBase() + "/channel_points/custom_rewards?broadcaster_id=" + channelId))
                    .header("Authorization", "Bearer " + token)
                    .header("Client-Id", TwitchAuth.getClientId())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            try {
                                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                                JsonArray data = json.getAsJsonArray("data");
                                if (data.size() > 0) {
                                    String rewardId = data.get(0).getAsJsonObject().get("id").getAsString();
                                    SLChannelPointMod.LOGGER.info("Created Twitch reward: " + title + " (ID: " + rewardId + ")");
                                    callback.accept(rewardId);
                                    return;
                                }
                            } catch (Exception e) {
                                SLChannelPointMod.LOGGER.error("Failed to parse reward creation response", e);
                            }
                        } else {
                            SLChannelPointMod.LOGGER.error("Failed to create reward: " + response.statusCode() + " - " + response.body());
                        }
                        callback.accept(null);
                    })
                    .exceptionally(e -> {
                        SLChannelPointMod.LOGGER.error("Failed to create reward", e);
                        callback.accept(null);
                        return null;
                    });
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to create reward", e);
            callback.accept(null);
        }
    }

    public static void updateReward(String rewardId, int cost, int cooldownSeconds, Consumer<Boolean> callback) {
        String token = ModConfig.get().getAccessToken();
        String channelId = ModConfig.get().getChannelId();

        if (token == null || token.isEmpty() || channelId == null || channelId.isEmpty()) {
            SLChannelPointMod.LOGGER.error("Not authenticated, cannot update reward");
            callback.accept(false);
            return;
        }

        try {
            JsonObject body = new JsonObject();
            body.addProperty("cost", cost);

            // Update cooldown settings
            if (cooldownSeconds > 0) {
                body.addProperty("is_global_cooldown_enabled", true);
                body.addProperty("global_cooldown_seconds", cooldownSeconds);
            } else {
                body.addProperty("is_global_cooldown_enabled", false);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getApiBase() + "/channel_points/custom_rewards?broadcaster_id=" + channelId + "&id=" + rewardId))
                    .header("Authorization", "Bearer " + token)
                    .header("Client-Id", TwitchAuth.getClientId())
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            SLChannelPointMod.LOGGER.info("Updated Twitch reward: " + rewardId);
                            callback.accept(true);
                        } else {
                            SLChannelPointMod.LOGGER.error("Failed to update reward: " + response.statusCode() + " - " + response.body());
                            callback.accept(false);
                        }
                    })
                    .exceptionally(e -> {
                        SLChannelPointMod.LOGGER.error("Failed to update reward", e);
                        callback.accept(false);
                        return null;
                    });
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to update reward", e);
            callback.accept(false);
        }
    }

    public static void deleteReward(String rewardId, Consumer<Boolean> callback) {
        String token = ModConfig.get().getAccessToken();
        String channelId = ModConfig.get().getChannelId();

        if (token == null || token.isEmpty() || channelId == null || channelId.isEmpty()) {
            SLChannelPointMod.LOGGER.error("Not authenticated, cannot delete reward");
            callback.accept(false);
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getApiBase() + "/channel_points/custom_rewards?broadcaster_id=" + channelId + "&id=" + rewardId))
                    .header("Authorization", "Bearer " + token)
                    .header("Client-Id", TwitchAuth.getClientId())
                    .DELETE()
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 204) {
                            SLChannelPointMod.LOGGER.info("Deleted Twitch reward: " + rewardId);
                            callback.accept(true);
                        } else {
                            SLChannelPointMod.LOGGER.error("Failed to delete reward: " + response.statusCode() + " - " + response.body());
                            callback.accept(false);
                        }
                    })
                    .exceptionally(e -> {
                        SLChannelPointMod.LOGGER.error("Failed to delete reward", e);
                        callback.accept(false);
                        return null;
                    });
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to delete reward", e);
            callback.accept(false);
        }
    }

    public static void getRewards(Consumer<JsonArray> callback) {
        String token = ModConfig.get().getAccessToken();
        String channelId = ModConfig.get().getChannelId();

        if (token == null || token.isEmpty() || channelId == null || channelId.isEmpty()) {
            SLChannelPointMod.LOGGER.error("Not authenticated, cannot get rewards");
            callback.accept(null);
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getApiBase() + "/channel_points/custom_rewards?broadcaster_id=" + channelId))
                    .header("Authorization", "Bearer " + token)
                    .header("Client-Id", TwitchAuth.getClientId())
                    .GET()
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            try {
                                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                                JsonArray data = json.getAsJsonArray("data");
                                callback.accept(data);
                                return;
                            } catch (Exception e) {
                                SLChannelPointMod.LOGGER.error("Failed to parse rewards response", e);
                            }
                        } else {
                            SLChannelPointMod.LOGGER.error("Failed to get rewards: " + response.statusCode() + " - " + response.body());
                        }
                        callback.accept(null);
                    })
                    .exceptionally(e -> {
                        SLChannelPointMod.LOGGER.error("Failed to get rewards", e);
                        callback.accept(null);
                        return null;
                    });
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to get rewards", e);
            callback.accept(null);
        }
    }
}
