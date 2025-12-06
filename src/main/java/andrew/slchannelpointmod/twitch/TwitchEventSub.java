package andrew.slchannelpointmod.twitch;

import andrew.slchannelpointmod.SLChannelPointMod;
import andrew.slchannelpointmod.config.ModConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;

public class TwitchEventSub {
    private static final String PRODUCTION_URL = "wss://eventsub.wss.twitch.tv/ws";
    private static final String TEST_URL = "ws://127.0.0.1:8080/ws";
    private static final String PRODUCTION_API = "https://api.twitch.tv/helix";
    private static final String TEST_API = "http://127.0.0.1:8080/mock";

    private static boolean testMode = false;

    private static WebSocketClient webSocketClient;
    private static String sessionId;
    private static boolean connected = false;
    private static Consumer<ChannelPointRedemption> redemptionCallback;
    private static MinecraftServer server;

    public static void setServer(MinecraftServer minecraftServer) {
        server = minecraftServer;
    }

    public static void setTestMode(boolean enabled) {
        testMode = enabled;
    }

    public static boolean isTestMode() {
        return testMode;
    }

    private static String getEventSubUrl() {
        return testMode ? TEST_URL : PRODUCTION_URL;
    }

    private static String getApiBase() {
        return testMode ? TEST_API : PRODUCTION_API;
    }

    private static void broadcastMessage(String message) {
        if (server != null) {
            server.execute(() -> {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal(message),
                        false
                );
            });
        }
    }

    public static void connect(Consumer<ChannelPointRedemption> callback) {
        redemptionCallback = callback;

        if (!ModConfig.get().hasValidToken()) {
            SLChannelPointMod.LOGGER.error("No valid token, cannot connect to EventSub");
            return;
        }

        try {
            webSocketClient = new WebSocketClient(new URI(getEventSubUrl())) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    SLChannelPointMod.LOGGER.info("Connected to Twitch EventSub");
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    SLChannelPointMod.LOGGER.info("Disconnected from Twitch EventSub: " + reason);
                    connected = false;
                    sessionId = null;

                    if (remote) {
                        new Thread(() -> {
                            try {
                                Thread.sleep(5000);
                                if (ModConfig.get().hasValidToken()) {
                                    SLChannelPointMod.LOGGER.info("Attempting to reconnect...");
                                    TwitchEventSub.connect(redemptionCallback);
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }).start();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    SLChannelPointMod.LOGGER.error("EventSub WebSocket error", ex);
                }
            };

            webSocketClient.connect();
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to connect to EventSub", e);
        }
    }

    public static void disconnect() {
        if (webSocketClient != null) {
            webSocketClient.close();
            webSocketClient = null;
        }
        connected = false;
        sessionId = null;
    }

    public static boolean isConnected() {
        return connected;
    }

    private static void handleMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            JsonObject metadata = json.getAsJsonObject("metadata");
            String messageType = metadata.get("message_type").getAsString();

            switch (messageType) {
                case "session_welcome" -> {
                    JsonObject session = json.getAsJsonObject("payload").getAsJsonObject("session");
                    sessionId = session.get("id").getAsString();
                    SLChannelPointMod.LOGGER.info("EventSub session established: " + sessionId);

                    if (testMode) {
                        connected = true;
                        SLChannelPointMod.LOGGER.info("Test mode: Skipping subscription (mock server auto-subscribes)");
                        broadcastMessage("\u00A7a[Twitch] \u00A7fConnected to test server");
                    } else {
                        subscribeToChannelPoints();
                    }
                }
                case "session_keepalive" -> {
                }
                case "session_reconnect" -> {
                    JsonObject session = json.getAsJsonObject("payload").getAsJsonObject("session");
                    String reconnectUrl = session.get("reconnect_url").getAsString();
                    SLChannelPointMod.LOGGER.info("Reconnecting to: " + reconnectUrl);
                    reconnect(reconnectUrl);
                }
                case "notification" -> {
                    handleNotification(json);
                }
                case "revocation" -> {
                    SLChannelPointMod.LOGGER.warn("Subscription revoked");
                }
            }
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to handle EventSub message", e);
        }
    }

    private static void handleNotification(JsonObject json) {
        try {
            JsonObject payload = json.getAsJsonObject("payload");
            JsonObject subscription = payload.getAsJsonObject("subscription");
            String type = subscription.get("type").getAsString();

            if (type.equals("channel.channel_points_custom_reward_redemption.add")) {
                JsonObject event = payload.getAsJsonObject("event");

                String rewardTitle;
                String userName;
                String userInput = "";

                if (event == null) {
                    // Twitch CLI mock server format (for testing)
                    userName = subscription.has("id") ? subscription.get("id").getAsString() : "TestUser";
                    rewardTitle = subscription.has("status") ? subscription.get("status").getAsString() : "Test Reward";
                } else {
                    // Production Twitch EventSub format
                    rewardTitle = "Unknown Reward";
                    if (event.has("reward") && event.get("reward").isJsonObject()) {
                        JsonObject reward = event.getAsJsonObject("reward");
                        if (reward.has("title")) {
                            rewardTitle = reward.get("title").getAsString();
                        }
                    }

                    userName = event.has("user_name") ? event.get("user_name").getAsString() : "Unknown";
                    userInput = event.has("user_input") && !event.get("user_input").isJsonNull()
                        ? event.get("user_input").getAsString() : "";
                }

                SLChannelPointMod.LOGGER.info("Channel point redemption: " + userName + " redeemed " + rewardTitle);

                ChannelPointRedemption redemption = new ChannelPointRedemption(rewardTitle, userName, userInput);
                if (redemptionCallback != null) {
                    redemptionCallback.accept(redemption);
                }
            }
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to handle notification", e);
        }
    }

    private static void subscribeToChannelPoints() {
        String channelId = ModConfig.get().getChannelId();
        String token = ModConfig.get().getAccessToken();

        if (channelId == null || channelId.isEmpty()) {
            SLChannelPointMod.LOGGER.error("No channel ID configured");
            return;
        }

        try {
            JsonObject body = new JsonObject();
            body.addProperty("type", "channel.channel_points_custom_reward_redemption.add");
            body.addProperty("version", "1");

            JsonObject condition = new JsonObject();
            condition.addProperty("broadcaster_user_id", channelId);
            body.add("condition", condition);

            JsonObject transport = new JsonObject();
            transport.addProperty("method", "websocket");
            transport.addProperty("session_id", sessionId);
            body.add("transport", transport);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getApiBase() + "/eventsub/subscriptions"))
                    .header("Authorization", "Bearer " + token)
                    .header("Client-Id", TwitchAuth.getClientId())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 202) {
                            SLChannelPointMod.LOGGER.info("Successfully subscribed to channel point redemptions");
                            connected = true;
                            String channelName = ModConfig.get().getChannelName();
                            broadcastMessage("\u00A7a[Twitch] \u00A7fConnected to channel: \u00A7b" + channelName);
                        } else {
                            SLChannelPointMod.LOGGER.error("Failed to subscribe: " + response.statusCode() + " - " + response.body());
                            broadcastMessage("\u00A7c[Twitch] \u00A7fFailed to connect. Check logs for details.");
                        }
                    })
                    .exceptionally(e -> {
                        SLChannelPointMod.LOGGER.error("Failed to subscribe to channel points", e);
                        return null;
                    });
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to subscribe to channel points", e);
        }
    }

    private static void reconnect(String reconnectUrl) {
        try {
            WebSocketClient newClient = new WebSocketClient(new URI(reconnectUrl)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    SLChannelPointMod.LOGGER.info("Reconnected to Twitch EventSub");
                    if (webSocketClient != null) {
                        webSocketClient.close();
                    }
                    webSocketClient = this;
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    SLChannelPointMod.LOGGER.info("Disconnected from Twitch EventSub: " + reason);
                    connected = false;
                }

                @Override
                public void onError(Exception ex) {
                    SLChannelPointMod.LOGGER.error("EventSub WebSocket error", ex);
                }
            };

            newClient.connect();
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to reconnect", e);
        }
    }

    public record ChannelPointRedemption(String rewardTitle, String userName, String userInput) {}
}
