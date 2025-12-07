package andrew.slchannelpointmod.config;

public class RewardAction {
    private ActionType type;
    private String value;
    private int count = 1;
    private String twitchRewardId;
    private int cost;
    private int cooldownSeconds = 0;  // Global cooldown in seconds (0 = no cooldown)
    private boolean hidden = false;

    // Quantity mode and range
    private QuantityMode quantityMode = QuantityMode.FIXED;
    private int maxCount = 0;  // Used when quantityMode is RANGE

    // Random mob options (only for RANDOM_MOB_SAME/RANDOM_MOB_EACH types)
    // Kept for backward compatibility but now uses ActionType instead
    private boolean randomMob = false;
    private boolean sameRandomMob = true;

    // Per-reward mob pool for random mob types (null = use default hostile mobs)
    private java.util.List<String> mobPool = null;

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
        return type;
    }

    public void setType(ActionType type) {
        this.type = type;
    }

    // Get effective action type, handling backward compatibility
    public ActionType getEffectiveType() {
        // Handle backward compatibility - if old randomMob flags are set
        if (type == ActionType.SPAWN_MOB && randomMob) {
            return sameRandomMob ? ActionType.RANDOM_MOB_SAME : ActionType.RANDOM_MOB_EACH;
        }
        return type;
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
        // Handle backward compatibility
        if (quantityMode == null) {
            return (maxCount > 0 && maxCount > count) ? QuantityMode.RANGE : QuantityMode.FIXED;
        }
        return quantityMode;
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

    // Backward compatibility methods
    public boolean isRandomMob() {
        ActionType effective = getEffectiveType();
        return effective == ActionType.RANDOM_MOB_SAME || effective == ActionType.RANDOM_MOB_EACH;
    }

    public void setRandomMob(boolean randomMob) {
        this.randomMob = randomMob;
    }

    public boolean isSameRandomMob() {
        ActionType effective = getEffectiveType();
        return effective != ActionType.RANDOM_MOB_EACH;
    }

    public void setSameRandomMob(boolean sameRandomMob) {
        this.sameRandomMob = sameRandomMob;
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

    public enum ActionType {
        SPAWN_MOB("Specific Mob"),
        RANDOM_MOB_SAME("Random Mob (All Same)"),
        RANDOM_MOB_EACH("Random Mob (Each Different)"),
        GIVE_ITEM("Give Item"),
        EXECUTE_COMMAND("Run Command");

        private final String displayName;

        ActionType(String displayName) {
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
