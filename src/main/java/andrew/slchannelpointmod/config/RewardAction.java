package andrew.slchannelpointmod.config;

public class RewardAction {
    private ActionType type;
    private String value;
    private int count = 1;
    private String twitchRewardId;
    private int cost;

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

    public boolean hasTwitchReward() {
        return twitchRewardId != null && !twitchRewardId.isEmpty();
    }

    public enum ActionType {
        SPAWN_MOB,
        GIVE_ITEM,
        EXECUTE_COMMAND
    }
}
