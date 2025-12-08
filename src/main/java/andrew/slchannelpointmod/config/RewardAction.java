package andrew.slchannelpointmod.config;

public class RewardAction {
    private ActionType type;
    private String value;
    private int count = 1;
    private String twitchRewardId;
    private int cost;
    private int cooldownSeconds = 0;
    private boolean hidden = false;

    // Quantity mode and range
    private QuantityMode quantityMode = QuantityMode.FIXED;
    private int maxCount = 0;

    // Random mode for mobs/items
    private RandomMode randomMode = RandomMode.NONE;

    // Per-reward mob pool for random mob types (null = use default hostile mobs)
    private java.util.List<String> mobPool = null;

    // Redemption tracking
    private int redemptionCount = 0;

    // Temporary disable state
    private String disabledTwitchRewardId = null;

    // Effect settings (duration in seconds, amplifier 0-255)
    private int effectDuration = 10;
    private int effectMaxDuration = 0;  // For random duration range (0 = fixed duration)
    private int effectAmplifier = 0;

    // Effect pool for random effects (null = use specific effect)
    private java.util.List<String> effectPool = null;

    // Sound settings (volume 0.0-1.0, pitch 0.5-2.0)
    private float soundVolume = 1.0f;
    private float soundPitch = 1.0f;

    public RewardAction() {}

    public RewardAction(ActionType type, String value, int count) {
        this.type = type;
        this.value = value;
        this.count = count;
    }

    public RewardAction(ActionType type, String value, int count, String twitchRewardId, int cost) {
        this.type = type;
        this.value = value;
        this.count = count;
        this.twitchRewardId = twitchRewardId;
        this.cost = cost;
    }

    public ActionType getType() {
        return type != null ? type : ActionType.SPAWN_MOB;
    }

    public void setType(ActionType type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getTwitchRewardId() {
        return twitchRewardId;
    }

    public void setTwitchRewardId(String twitchRewardId) {
        this.twitchRewardId = twitchRewardId;
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
    }

    public boolean hasTwitchReward() {
        return twitchRewardId != null && !twitchRewardId.isEmpty();
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public QuantityMode getQuantityMode() {
        return quantityMode != null ? quantityMode : QuantityMode.FIXED;
    }

    public void setQuantityMode(QuantityMode mode) {
        this.quantityMode = mode;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }

    public boolean hasRandomCount() {
        return getQuantityMode() == QuantityMode.RANGE && maxCount > 0 && maxCount > count;
    }

    public RandomMode getRandomMode() {
        return randomMode != null ? randomMode : RandomMode.NONE;
    }

    public void setRandomMode(RandomMode randomMode) {
        this.randomMode = randomMode;
    }

    public boolean isRandomEnabled() {
        return getRandomMode() != RandomMode.NONE;
    }

    public java.util.List<String> getMobPool() {
        return mobPool;
    }

    public void setMobPool(java.util.List<String> mobPool) {
        this.mobPool = mobPool;
    }

    public boolean hasCustomMobPool() {
        return mobPool != null && !mobPool.isEmpty();
    }

    public int getRedemptionCount() {
        return redemptionCount;
    }

    public void incrementRedemptionCount() {
        this.redemptionCount++;
    }

    public void setRedemptionCount(int count) {
        this.redemptionCount = count;
    }

    public String getDisabledTwitchRewardId() {
        return disabledTwitchRewardId;
    }

    public void setDisabledTwitchRewardId(String id) {
        this.disabledTwitchRewardId = id;
    }

    public boolean isTemporarilyDisabled() {
        return disabledTwitchRewardId != null && !disabledTwitchRewardId.isEmpty();
    }

    public int getEffectDuration() {
        return effectDuration;
    }

    public void setEffectDuration(int duration) {
        this.effectDuration = Math.max(1, duration);
    }

    public int getEffectMaxDuration() {
        return effectMaxDuration;
    }

    public void setEffectMaxDuration(int maxDuration) {
        this.effectMaxDuration = Math.max(0, maxDuration);
    }

    public boolean hasRandomDuration() {
        return effectMaxDuration > 0 && effectMaxDuration > effectDuration;
    }

    public int getEffectAmplifier() {
        return effectAmplifier;
    }

    public void setEffectAmplifier(int amplifier) {
        this.effectAmplifier = Math.max(0, Math.min(255, amplifier));
    }

    public java.util.List<String> getEffectPool() {
        return effectPool;
    }

    public void setEffectPool(java.util.List<String> effectPool) {
        this.effectPool = effectPool;
    }

    public boolean hasCustomEffectPool() {
        return effectPool != null && !effectPool.isEmpty();
    }

    public boolean isRandomEffectEnabled() {
        return hasCustomEffectPool();
    }

    public float getSoundVolume() {
        return soundVolume;
    }

    public void setSoundVolume(float volume) {
        this.soundVolume = Math.max(0.0f, Math.min(1.0f, volume));
    }

    public float getSoundPitch() {
        return soundPitch;
    }

    public void setSoundPitch(float pitch) {
        this.soundPitch = Math.max(0.5f, Math.min(2.0f, pitch));
    }

    public enum ActionType {
        SPAWN_MOB("Spawn Mob"),
        GIVE_ITEM("Give Item"),
        EXECUTE_COMMAND("Run Command"),
        APPLY_EFFECT("Apply Effect"),
        PLAY_SOUND("Play Sound"),
        SPECIAL("Special Action");

        private final String displayName;

        ActionType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum SpecialActionType {
        SHUFFLE_INVENTORY("Shuffle Inventory"),
        DROP_HELD_ITEM("Drop Held Item"),
        DROP_INVENTORY("Drop Inventory");

        private final String displayName;

        SpecialActionType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum RandomMode {
        NONE("Specific"),
        ALL_SAME("Random (All Same)"),
        EACH_DIFFERENT("Random (Each Different)");

        private final String displayName;

        RandomMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum QuantityMode {
        FIXED("Fixed"),
        RANGE("Random Range");

        private final String displayName;

        QuantityMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
