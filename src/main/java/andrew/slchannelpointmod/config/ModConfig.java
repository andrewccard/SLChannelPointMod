package andrew.slchannelpointmod.config;

import andrew.slchannelpointmod.SLChannelPointMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("slchannelpointmod.json");

    private static ModConfig INSTANCE;

    // Twitch OAuth token
    private String clientId = "";
    private String accessToken = "";
    private String refreshToken = "";
    private String channelId = "";
    private String channelName = "";

    // Reward mappings: reward name -> action config
    private Map<String, RewardAction> rewards = new HashMap<>();

    public static ModConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                INSTANCE = GSON.fromJson(json, ModConfig.class);
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
        rewards.put(rewardName.toLowerCase(), action);
        save();
    }

    public void removeReward(String rewardName) {
        rewards.remove(rewardName.toLowerCase());
        save();
    }

    public RewardAction getReward(String rewardName) {
        return rewards.get(rewardName.toLowerCase());
    }

    public boolean hasValidToken() {
        return accessToken != null && !accessToken.isEmpty();
    }
}
