package andrew.slchannelpointmod.config;

import andrew.slchannelpointmod.SLChannelPointMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("slchannelpointmod.json");

    private static ModConfig INSTANCE;
    private static long lastModifiedTime = 0;

    // Twitch OAuth token
    private String clientId = "";
    private String accessToken = "";
    private String refreshToken = "";
    private String channelId = "";
    private String channelName = "";

    // Reward mappings: reward name -> action config
    private Map<String, RewardAction> rewards = new HashMap<>();

    // Settings
    private boolean showChatMessages = false;  // Default disabled

    // Random mob pool - null means use defaults (naturally spawning mobs)
    private Set<String> randomMobPool = null;

    // HUD settings
    private HudPosition hudPosition = HudPosition.TOP_RIGHT;
    private int hudMaxEntries = 5;
    private float hudScale = 1.0f;

    public enum HudPosition {
        TOP_LEFT("Top Left"),
        TOP_RIGHT("Top Right"),
        BOTTOM_LEFT("Bottom Left"),
        BOTTOM_RIGHT("Bottom Right");

        private final String displayName;

        HudPosition(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public HudPosition next() {
            HudPosition[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public static ModConfig get() {
        if (INSTANCE == null) {
            load();
        } else {
            // Check if config file has been modified externally and reload if needed
            try {
                if (Files.exists(CONFIG_PATH)) {
                    long currentModTime = Files.getLastModifiedTime(CONFIG_PATH).toMillis();
                    if (currentModTime > lastModifiedTime) {
                        load();
                    }
                }
            } catch (IOException ignored) {
                // Ignore errors checking file time
            }
        }
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                INSTANCE = GSON.fromJson(json, ModConfig.class);
                lastModifiedTime = Files.getLastModifiedTime(CONFIG_PATH).toMillis();
                SLChannelPointMod.LOGGER.info("Config loaded successfully");
            } catch (IOException e) {
                SLChannelPointMod.LOGGER.error("Failed to load config", e);
                INSTANCE = new ModConfig();
            }
        } else {
            INSTANCE = new ModConfig();
            save();
        }
    }

    public static void save() {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(INSTANCE));
            lastModifiedTime = Files.getLastModifiedTime(CONFIG_PATH).toMillis();
            SLChannelPointMod.LOGGER.info("Config saved successfully");
        } catch (IOException e) {
            SLChannelPointMod.LOGGER.error("Failed to save config", e);
        }
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
        save();
    }

    public boolean hasValidClientId() {
        return clientId != null && !clientId.isEmpty();
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
        save();
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
        save();
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
        save();
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
        save();
    }

    public Map<String, RewardAction> getRewards() {
        return rewards;
    }

    public void setReward(String rewardName, RewardAction action) {
        // Remove any existing reward with same name (case-insensitive) to avoid duplicates
        String existingKey = findRewardKey(rewardName);
        if (existingKey != null) {
            rewards.remove(existingKey);
        }
        rewards.put(rewardName, action);
        save();
    }

    public void removeReward(String rewardName) {
        String existingKey = findRewardKey(rewardName);
        if (existingKey != null) {
            rewards.remove(existingKey);
            save();
        }
    }

    public RewardAction getReward(String rewardName) {
        String existingKey = findRewardKey(rewardName);
        return existingKey != null ? rewards.get(existingKey) : null;
    }

    // Find the actual key in the map (case-insensitive search)
    private String findRewardKey(String rewardName) {
        for (String key : rewards.keySet()) {
            if (key.equalsIgnoreCase(rewardName)) {
                return key;
            }
        }
        return null;
    }

    public boolean hasValidToken() {
        return accessToken != null && !accessToken.isEmpty();
    }

    public boolean isShowChatMessages() {
        return showChatMessages;
    }

    public void setShowChatMessages(boolean showChatMessages) {
        this.showChatMessages = showChatMessages;
        save();
    }

    public Set<String> getRandomMobPool() {
        return randomMobPool;
    }

    public void setRandomMobPool(Set<String> pool) {
        this.randomMobPool = pool;
        save();
    }

    public boolean hasCustomRandomMobPool() {
        return randomMobPool != null && !randomMobPool.isEmpty();
    }

    public HudPosition getHudPosition() {
        return hudPosition != null ? hudPosition : HudPosition.TOP_RIGHT;
    }

    public void setHudPosition(HudPosition hudPosition) {
        this.hudPosition = hudPosition;
        save();
    }

    public int getHudMaxEntries() {
        return hudMaxEntries > 0 ? hudMaxEntries : 5;
    }

    public void setHudMaxEntries(int hudMaxEntries) {
        this.hudMaxEntries = Math.max(1, Math.min(10, hudMaxEntries));  // Clamp between 1 and 10
        save();
    }

    public float getHudScale() {
        return hudScale > 0 ? hudScale : 1.0f;
    }

    public void setHudScale(float hudScale) {
        this.hudScale = Math.max(0.5f, Math.min(2.0f, hudScale));  // Clamp between 0.5 and 2.0
        save();
    }
}
