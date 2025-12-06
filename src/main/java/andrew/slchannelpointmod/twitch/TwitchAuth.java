package andrew.slchannelpointmod.twitch;

import andrew.slchannelpointmod.SLChannelPointMod;
import andrew.slchannelpointmod.config.ModConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class TwitchAuth {
    public static final String CLIENT_ID = "h3izv07nbz4w7f8u107mr9xglpibay";

    private static final int CALLBACK_PORT = 17563;
    private static final String REDIRECT_URI = "http://localhost:" + CALLBACK_PORT + "/callback";
    private static final String SCOPES = "channel:read:redemptions+channel:manage:redemptions";

    private static HttpServer callbackServer;
    private static CompletableFuture<String> authCodeFuture;
    private static boolean authInProgress = false;

    public static String getClientId() {
        return CLIENT_ID;
    }

    public static boolean hasValidClientId() {
        return CLIENT_ID != null && !CLIENT_ID.isEmpty() && !CLIENT_ID.equals("YOUR_CLIENT_ID_HERE");
    }

    public static String getAuthUrl() {
        if (!hasValidClientId()) {
            return null;
        }
        return "https://id.twitch.tv/oauth2/authorize" +
                "?client_id=" + CLIENT_ID +
                "&redirect_uri=" + REDIRECT_URI +
                "&response_type=token" +
                "&scope=" + SCOPES;
    }

    public static void startAuthFlow(Consumer<Boolean> callback) {
        if (!hasValidClientId()) {
            SLChannelPointMod.LOGGER.error("Client ID not configured in TwitchAuth.java");
            callback.accept(false);
            return;
        }

        if (authInProgress) {
            SLChannelPointMod.LOGGER.warn("Auth already in progress");
            callback.accept(false);
            return;
        }

        try {
            stopCallbackServer();

            authInProgress = true;

            authCodeFuture = new CompletableFuture<>();
            startCallbackServer();

            String authUrl = getAuthUrl();

            boolean browserOpened = false;

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                try {
                    Desktop.getDesktop().browse(URI.create(authUrl));
                    browserOpened = true;
                    SLChannelPointMod.LOGGER.info("Opening browser for Twitch authentication...");
                } catch (Exception e) {
                    SLChannelPointMod.LOGGER.warn("Desktop.browse failed: " + e.getMessage());
                }
            }

            if (!browserOpened) {
                try {
                    String os = System.getProperty("os.name").toLowerCase();
                    ProcessBuilder pb;
                    if (os.contains("win")) {
                        pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", authUrl);
                    } else if (os.contains("mac")) {
                        pb = new ProcessBuilder("open", authUrl);
                    } else {
                        pb = new ProcessBuilder("xdg-open", authUrl);
                    }
                    pb.start();
                    browserOpened = true;
                    SLChannelPointMod.LOGGER.info("Opening browser via system command...");
                } catch (Exception e) {
                    SLChannelPointMod.LOGGER.warn("System command failed: " + e.getMessage());
                }
            }

            if (!browserOpened) {
                SLChannelPointMod.LOGGER.error("Could not open browser. Please open this URL manually:");
                SLChannelPointMod.LOGGER.error(authUrl);
            }

            authCodeFuture.thenAccept(token -> {
                authInProgress = false;
                if (token != null && !token.isEmpty()) {
                    ModConfig.get().setAccessToken(token);
                    fetchUserInfo(token, callback);
                } else {
                    callback.accept(false);
                }
                stopCallbackServer();
            }).exceptionally(e -> {
                authInProgress = false;
                SLChannelPointMod.LOGGER.error("Auth flow failed", e);
                callback.accept(false);
                stopCallbackServer();
                return null;
            });

        } catch (Exception e) {
            authInProgress = false;
            SLChannelPointMod.LOGGER.error("Failed to start auth flow", e);
            callback.accept(false);
            stopCallbackServer();
        }
    }

    private static void startCallbackServer() throws IOException {
        callbackServer = HttpServer.create(new InetSocketAddress(CALLBACK_PORT), 0);

        callbackServer.createContext("/callback", exchange -> {
            String html = """
                <!DOCTYPE html>
                <html>
                <head><title>Twitch Auth</title></head>
                <body>
                    <h1>Authenticating...</h1>
                    <script>
                        const fragment = window.location.hash.substring(1);
                        const params = new URLSearchParams(fragment);
                        const token = params.get('access_token');
                        if (token) {
                            fetch('/token?access_token=' + token)
                                .then(() => {
                                    document.body.innerHTML = '<h1>Authentication successful! You can close this window.</h1>';
                                });
                        } else {
                            document.body.innerHTML = '<h1>Authentication failed. Please try again.</h1>';
                        }
                    </script>
                </body>
                </html>
                """;
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, html.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(html.getBytes());
            }
        });

        callbackServer.createContext("/token", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String token = null;
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2 && pair[0].equals("access_token")) {
                        token = pair[1];
                        break;
                    }
                }
            }

            String response = "OK";
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }

            if (token != null) {
                authCodeFuture.complete(token);
            } else {
                authCodeFuture.complete(null);
            }
        });

        callbackServer.setExecutor(null);
        callbackServer.start();
        SLChannelPointMod.LOGGER.info("Callback server started on port " + CALLBACK_PORT);
    }

    private static void stopCallbackServer() {
        if (callbackServer != null) {
            callbackServer.stop(0);
            callbackServer = null;
            SLChannelPointMod.LOGGER.info("Callback server stopped");
        }
    }

    private static void fetchUserInfo(String token, Consumer<Boolean> callback) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.twitch.tv/helix/users"))
                    .header("Authorization", "Bearer " + token)
                    .header("Client-Id", getClientId())
                    .GET()
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            JsonObject user = json.getAsJsonArray("data").get(0).getAsJsonObject();
                            String channelId = user.get("id").getAsString();
                            String channelName = user.get("login").getAsString();

                            ModConfig.get().setChannelId(channelId);
                            ModConfig.get().setChannelName(channelName);

                            SLChannelPointMod.LOGGER.info("Authenticated as: " + channelName + " (ID: " + channelId + ")");
                            callback.accept(true);
                        } else {
                            SLChannelPointMod.LOGGER.error("Failed to fetch user info: " + response.statusCode());
                            callback.accept(false);
                        }
                    })
                    .exceptionally(e -> {
                        SLChannelPointMod.LOGGER.error("Failed to fetch user info", e);
                        callback.accept(false);
                        return null;
                    });
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to fetch user info", e);
            callback.accept(false);
        }
    }

    public static boolean validateToken() {
        String token = ModConfig.get().getAccessToken();
        if (token == null || token.isEmpty()) {
            return false;
        }

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://id.twitch.tv/oauth2/validate"))
                    .header("Authorization", "OAuth " + token)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to validate token", e);
            return false;
        }
    }
}
