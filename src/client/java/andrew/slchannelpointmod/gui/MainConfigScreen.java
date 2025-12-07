package andrew.slchannelpointmod.gui;

import andrew.slchannelpointmod.config.ModConfig;
import andrew.slchannelpointmod.config.RewardAction;
import andrew.slchannelpointmod.rewards.RewardHandler;
import andrew.slchannelpointmod.twitch.TwitchAPI;
import andrew.slchannelpointmod.twitch.TwitchAuth;
import andrew.slchannelpointmod.twitch.TwitchEventSub;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainConfigScreen extends Screen {
    private final Screen parent;

    private static final int TAB_HEIGHT = 24;

    // Static to remember tab selection across screen opens
    private static Tab lastTab = Tab.REWARDS;
    private Tab currentTab = lastTab;
    private Button rewardsTabBtn;
    private Button settingsTabBtn;

    // Settings tab - collapsible sections (static to persist across menu opens)
    private static boolean connectionSettingsExpanded = true;  // Start expanded by default

    // Rewards tab - custom list
    private List<RewardEntry> rewardEntries = new ArrayList<>();
    private int selectedRewardIndex = -1;
    private static String selectedRewardName = null;  // Static to persist across screen navigations
    private int rewardScrollOffset = 0;
    private static final int REWARD_ENTRY_HEIGHT = 28;
    private static final int REWARDS_LIST_TOP = 80;  // Below tabs (30 + 24 + margin)

    // Method to select a reward by name (called after adding/editing)
    public static void selectReward(String name) {
        selectedRewardName = name;
    }

    // Sync status
    private String syncStatus = null;
    private long syncStatusTime = 0;

    // Channel points status: null = unknown/checking, true = enabled, false = not enabled
    private Boolean channelPointsEnabled = null;
    private boolean channelPointsChecked = false;

    // Show hidden rewards toggle
    private boolean showHiddenRewards = false;

    // Settings tab - collapsible sections (static to persist)
    private static boolean hudSettingsExpanded = false;
    private static int settingsScrollOffset = 0;

    // Reward list buttons (for click handling)
    private List<Button> rewardListButtons = new ArrayList<>();

    // Track connection status for auto-refresh
    private boolean lastKnownConnectionStatus = false;

    public MainConfigScreen(Screen parent) {
        super(Component.translatable("screen.slchannelpointmod.config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Clear reward list buttons on init
        rewardListButtons.clear();

        int tabWidth = 120;
        int tabY = 30;
        int startX = (this.width - tabWidth * 2) / 2;

        // Tab buttons (2 tabs)
        rewardsTabBtn = Button.builder(Component.literal("Rewards"), btn -> switchTab(Tab.REWARDS))
                .pos(startX, tabY)
                .size(tabWidth, TAB_HEIGHT)
                .build();

        settingsTabBtn = Button.builder(Component.literal("Settings"), btn -> switchTab(Tab.SETTINGS))
                .pos(startX + tabWidth, tabY)
                .size(tabWidth, TAB_HEIGHT)
                .build();

        this.addRenderableWidget(rewardsTabBtn);
        this.addRenderableWidget(settingsTabBtn);

        // Done button
        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .pos(this.width / 2 - 50, this.height - 28)
                .size(100, 20)
                .build());

        initTabContent();
        updateTabButtons();
    }

    private void switchTab(Tab tab) {
        this.currentTab = tab;
        lastTab = tab;  // Remember for next time
        selectedRewardIndex = -1;  // Clear selection when switching tabs
        selectedRewardName = null;
        rebuildWidgets();
    }

    private void updateTabButtons() {
        rewardsTabBtn.active = currentTab != Tab.REWARDS;
        settingsTabBtn.active = currentTab != Tab.SETTINGS;
    }

    private void initTabContent() {
        int contentY = 70;
        int centerX = this.width / 2;

        switch (currentTab) {
            case REWARDS -> initRewardsTab(centerX, contentY);
            case SETTINGS -> initSettingsTab(centerX, contentY);
        }
    }

    private void initRewardsTab(int centerX, int startY) {
        // Build reward entries list (filter hidden unless showHidden is true)
        rewardEntries.clear();
        for (Map.Entry<String, RewardAction> entry : ModConfig.get().getRewards().entrySet()) {
            if (showHiddenRewards || !entry.getValue().isHidden()) {
                rewardEntries.add(new RewardEntry(entry.getKey(), entry.getValue()));
            }
        }

        // Restore selection by name after rebuilding the list (case-insensitive)
        if (selectedRewardName != null) {
            selectedRewardIndex = -1;
            for (int i = 0; i < rewardEntries.size(); i++) {
                if (rewardEntries.get(i).name.equalsIgnoreCase(selectedRewardName)) {
                    selectedRewardIndex = i;
                    selectedRewardName = rewardEntries.get(i).name;  // Update to actual name
                    break;
                }
            }
            if (selectedRewardIndex == -1) {
                selectedRewardName = null;  // Reward no longer exists
            }
        }

        boolean hasSelection = selectedRewardIndex >= 0 && selectedRewardIndex < rewardEntries.size();
        boolean hasToken = ModConfig.get().hasValidToken();
        RewardEntry selectedEntry = hasSelection ? rewardEntries.get(selectedRewardIndex) : null;

        // Top row buttons
        int listX = 30;
        int listWidth = this.width - 60;

        // Sync button - top left (only enabled when logged in)
        Button syncBtn = Button.builder(Component.literal("Sync from Twitch"), btn -> syncFromTwitch())
                .pos(listX, REWARDS_LIST_TOP - 22)
                .size(110, 20)
                .tooltip(Tooltip.create(Component.literal("Import channel point rewards from your Twitch channel")))
                .build();
        syncBtn.active = hasToken;
        this.addRenderableWidget(syncBtn);

        // Show Hidden toggle button
        int hiddenCount = (int) ModConfig.get().getRewards().values().stream().filter(RewardAction::isHidden).count();
        String showHiddenText = showHiddenRewards ? "Hide Hidden (" + hiddenCount + ")" : "Show Hidden (" + hiddenCount + ")";
        Button showHiddenBtn = Button.builder(Component.literal(showHiddenText), btn -> {
            showHiddenRewards = !showHiddenRewards;
            selectedRewardIndex = -1;
            selectedRewardName = null;
            rebuildWidgets();
        })
                .pos(listX + 115, REWARDS_LIST_TOP - 22)
                .size(100, 20)
                .tooltip(Tooltip.create(Component.literal("Toggle visibility of hidden rewards")))
                .build();
        showHiddenBtn.active = hiddenCount > 0 || showHiddenRewards;
        this.addRenderableWidget(showHiddenBtn);

        // Add button - top right of list area
        this.addRenderableWidget(Button.builder(Component.literal("+ Add Reward"), btn -> {
            this.minecraft.setScreen(new RewardEditorScreen(this, null, null));
        })
                .pos(listX + listWidth - 100, REWARDS_LIST_TOP - 22)
                .size(100, 20)
                .build());

        // Create clickable buttons for each visible reward entry
        int listHeight = this.height - 165;  // More space for bottom buttons (2 rows + Done + margins)
        int visibleEntries = listHeight / REWARD_ENTRY_HEIGHT;
        int startIndex = rewardScrollOffset;
        int endIndex = Math.min(startIndex + visibleEntries, rewardEntries.size());

        for (int i = startIndex; i < endIndex; i++) {
            final int rewardIndex = i;
            final String rewardName = rewardEntries.get(i).name;
            int relIndex = i - startIndex;
            int entryY = REWARDS_LIST_TOP + relIndex * REWARD_ENTRY_HEIGHT;

            // Create an invisible button that covers the entire entry row
            Button entryBtn = Button.builder(Component.empty(), btn -> {
                // Toggle selection
                if (selectedRewardIndex == rewardIndex) {
                    selectedRewardIndex = -1;
                    selectedRewardName = null;
                } else {
                    selectedRewardIndex = rewardIndex;
                    selectedRewardName = rewardName;
                }
                rebuildWidgets();
            })
                    .pos(listX, entryY)
                    .size(listWidth, REWARD_ENTRY_HEIGHT - 2)
                    .build();

            // Make button invisible but still clickable
            rewardListButtons.add(entryBtn);
            this.addRenderableWidget(entryBtn);
        }

        // Scroll buttons (up/down arrows) if needed
        if (rewardEntries.size() > visibleEntries) {
            // Scroll up button
            Button scrollUpBtn = Button.builder(Component.literal("\u25B2"), btn -> {
                if (rewardScrollOffset > 0) {
                    rewardScrollOffset--;
                    rebuildWidgets();
                }
            })
                    .pos(listX + listWidth + 5, REWARDS_LIST_TOP)
                    .size(20, 20)
                    .build();
            scrollUpBtn.active = rewardScrollOffset > 0;
            this.addRenderableWidget(scrollUpBtn);

            // Scroll down button
            int maxScroll = Math.max(0, rewardEntries.size() - visibleEntries);
            Button scrollDownBtn = Button.builder(Component.literal("\u25BC"), btn -> {
                int max = Math.max(0, rewardEntries.size() - visibleEntries);
                if (rewardScrollOffset < max) {
                    rewardScrollOffset++;
                    rebuildWidgets();
                }
            })
                    .pos(listX + listWidth + 5, REWARDS_LIST_TOP + listHeight - 20)
                    .size(20, 20)
                    .build();
            scrollDownBtn.active = rewardScrollOffset < maxScroll;
            this.addRenderableWidget(scrollDownBtn);
        }

        // Action buttons at bottom - two rows
        int buttonWidth = 60;
        int buttonY = this.height - 56;
        int spacing = 5;

        // Row 1: Edit, Delete, Test
        int row1Width = buttonWidth * 3 + spacing * 2;
        int row1StartX = (this.width - row1Width) / 2;

        Button editBtn = Button.builder(Component.literal("Edit"), btn -> {
            if (selectedRewardIndex >= 0 && selectedRewardIndex < rewardEntries.size()) {
                RewardEntry entry = rewardEntries.get(selectedRewardIndex);
                this.minecraft.setScreen(new RewardEditorScreen(this, entry.name, entry.action));
            }
        })
                .pos(row1StartX, buttonY)
                .size(buttonWidth, 20)
                .build();
        editBtn.active = hasSelection;
        this.addRenderableWidget(editBtn);

        Button deleteBtn = Button.builder(Component.literal("Delete"), btn -> {
            if (selectedRewardIndex >= 0 && selectedRewardIndex < rewardEntries.size()) {
                RewardEntry entry = rewardEntries.get(selectedRewardIndex);
                ModConfig.get().removeReward(entry.name);
                selectedRewardIndex = -1;
                selectedRewardName = null;
                rebuildWidgets();
            }
        })
                .pos(row1StartX + buttonWidth + spacing, buttonY)
                .size(buttonWidth, 20)
                .build();
        deleteBtn.active = hasSelection;
        this.addRenderableWidget(deleteBtn);

        Button testBtn = Button.builder(Component.literal("Test"), btn -> {
            if (selectedRewardIndex >= 0 && selectedRewardIndex < rewardEntries.size()) {
                RewardEntry entry = rewardEntries.get(selectedRewardIndex);
                RewardHandler.simulateRedemption(entry.name, "TestUser");
            }
        })
                .pos(row1StartX + (buttonWidth + spacing) * 2, buttonY)
                .size(buttonWidth, 20)
                .build();
        testBtn.active = hasSelection;
        this.addRenderableWidget(testBtn);

        // Row 2: Publish/Update/Unpublish, Hide/Show (above row 1)
        int row2Y = buttonY - 24;
        boolean isPublished = selectedEntry != null && selectedEntry.action.hasTwitchReward();
        boolean canPublish = selectedEntry != null && !isPublished &&
                selectedEntry.action.getValue() != null && !selectedEntry.action.getValue().isEmpty();

        // Layout depends on whether reward is published (3 buttons) or not (2 buttons)
        int smallBtnWidth = 60;
        int row2Width = isPublished ? (smallBtnWidth * 3 + spacing * 2) : (90 * 2 + spacing);
        int row2StartX = (this.width - row2Width) / 2;

        if (isPublished) {
            // Published: Show Update, Unpublish, Hide buttons
            Button updateBtn = Button.builder(Component.literal("Update"), btn -> {
                if (selectedRewardIndex >= 0 && selectedRewardIndex < rewardEntries.size()) {
                    RewardEntry entry = rewardEntries.get(selectedRewardIndex);
                    updateRewardOnTwitch(entry);
                }
            })
                    .pos(row2StartX, row2Y)
                    .size(smallBtnWidth, 20)
                    .tooltip(Tooltip.create(Component.literal("Update cost/cooldown on Twitch")))
                    .build();
            updateBtn.active = hasToken && hasSelection;
            this.addRenderableWidget(updateBtn);

            Button unpublishBtn = Button.builder(Component.literal("Unpublish"), btn -> {
                if (selectedRewardIndex >= 0 && selectedRewardIndex < rewardEntries.size()) {
                    RewardEntry entry = rewardEntries.get(selectedRewardIndex);
                    unpublishReward(entry);
                }
            })
                    .pos(row2StartX + smallBtnWidth + spacing, row2Y)
                    .size(smallBtnWidth, 20)
                    .tooltip(Tooltip.create(Component.literal("Remove from Twitch")))
                    .build();
            unpublishBtn.active = hasToken && hasSelection;
            this.addRenderableWidget(unpublishBtn);

            // Hide button for published
            boolean isHiddenPub = selectedEntry.action.isHidden();
            String hideTextPub = isHiddenPub ? "Unhide" : "Hide";
            Button hideBtnPub = Button.builder(Component.literal(hideTextPub), btn -> {
                if (selectedRewardIndex >= 0 && selectedRewardIndex < rewardEntries.size()) {
                    RewardEntry entry = rewardEntries.get(selectedRewardIndex);
                    boolean wasHidden = entry.action.isHidden();
                    entry.action.setHidden(!wasHidden);
                    ModConfig.get().setReward(entry.name, entry.action);
                    if (!showHiddenRewards && !wasHidden) {
                        selectedRewardIndex = -1;
                        selectedRewardName = null;
                    }
                    rebuildWidgets();
                }
            })
                    .pos(row2StartX + (smallBtnWidth + spacing) * 2, row2Y)
                    .size(smallBtnWidth, 20)
                    .tooltip(Tooltip.create(Component.literal(isHiddenPub ?
                            "Show in list" : "Hide from list")))
                    .build();
            hideBtnPub.active = hasSelection;
            this.addRenderableWidget(hideBtnPub);
        } else {
            // Not published: Show Publish, Hide buttons
            Button publishBtn = Button.builder(Component.literal("Publish"), btn -> {
                if (selectedRewardIndex >= 0 && selectedRewardIndex < rewardEntries.size()) {
                    RewardEntry entry = rewardEntries.get(selectedRewardIndex);
                    publishReward(entry);
                }
            })
                    .pos(row2StartX, row2Y)
                    .size(90, 20)
                    .tooltip(Tooltip.create(Component.literal("Create this reward on Twitch")))
                    .build();
            publishBtn.active = hasToken && hasSelection && canPublish;
            this.addRenderableWidget(publishBtn);

            // Hide/Show button
            boolean isHidden = selectedEntry != null && selectedEntry.action.isHidden();
            String hideText = isHidden ? "Unhide" : "Hide";
            Button hideBtn = Button.builder(Component.literal(hideText), btn -> {
                // Look up the current selection at click time
                if (selectedRewardIndex >= 0 && selectedRewardIndex < rewardEntries.size()) {
                    RewardEntry entry = rewardEntries.get(selectedRewardIndex);
                    boolean wasHidden = entry.action.isHidden();
                    entry.action.setHidden(!wasHidden);
                    ModConfig.get().setReward(entry.name, entry.action);
                    if (!showHiddenRewards && !wasHidden) {
                        // If we just hid it and we're not showing hidden, deselect
                        selectedRewardIndex = -1;
                        selectedRewardName = null;
                    }
                    rebuildWidgets();
                }
            })
                    .pos(row2StartX + 90 + spacing, row2Y)
                    .size(90, 20)
                    .tooltip(Tooltip.create(Component.literal(isHidden ?
                            "Show this reward in the list" : "Hide this reward (not for use with mod)")))
                    .build();
            hideBtn.active = hasSelection;
            this.addRenderableWidget(hideBtn);
        }
    }

    @Override
    public void tick() {
        super.tick();
        // Auto-refresh when connection status changes (for Settings tab)
        if (currentTab == Tab.SETTINGS) {
            boolean currentStatus = TwitchEventSub.isConnected();
            if (currentStatus != lastKnownConnectionStatus) {
                lastKnownConnectionStatus = currentStatus;
                rebuildWidgets();
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        // Title
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);

        // Tab-specific content
        if (currentTab == Tab.REWARDS) {
            renderRewardsList(graphics, mouseX, mouseY);
        }
    }

    private void renderRewardsList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listWidth = this.width - 60;
        int listX = 30;
        int listHeight = this.height - 165;  // More space for bottom buttons (2 rows + Done + margins)
        int visibleEntries = listHeight / REWARD_ENTRY_HEIGHT;

        // Sync status message (fade out after 3 seconds) - centered below the top buttons
        if (syncStatus != null) {
            long elapsed = System.currentTimeMillis() - syncStatusTime;
            if (elapsed < 3000) {
                int alpha = elapsed < 2500 ? 255 : (int) (255 * (3000 - elapsed) / 500);
                int color = (alpha << 24) | 0x55FF55;
                if (syncStatus.contains("Failed")) {
                    color = (alpha << 24) | 0xFF5555;
                } else if (syncStatus.equals("Syncing...")) {
                    color = (alpha << 24) | 0xFFFF55;
                }
                // Draw centered above the list
                graphics.drawCenteredString(this.font, syncStatus, this.width / 2, REWARDS_LIST_TOP - 10, color);
            } else {
                syncStatus = null;
            }
        }

        // List background
        graphics.fill(listX - 2, REWARDS_LIST_TOP - 2, listX + listWidth + 2, REWARDS_LIST_TOP + listHeight + 2, 0x80000000);

        if (rewardEntries.isEmpty()) {
            graphics.drawCenteredString(this.font, "No rewards configured", this.width / 2, REWARDS_LIST_TOP + 20, 0xFFAAAAAA);
            graphics.drawCenteredString(this.font, "Click 'Sync from Twitch' or 'Add' to create rewards", this.width / 2, REWARDS_LIST_TOP + 35, 0xFF888888);
            return;
        }

        // Render visible entries
        int startIndex = rewardScrollOffset;
        int endIndex = Math.min(startIndex + visibleEntries, rewardEntries.size());

        for (int i = startIndex; i < endIndex; i++) {
            RewardEntry entry = rewardEntries.get(i);
            int relIndex = i - startIndex;
            int entryY = REWARDS_LIST_TOP + relIndex * REWARD_ENTRY_HEIGHT;

            boolean isSelected = i == selectedRewardIndex;
            boolean isHovered = mouseX >= listX && mouseX < listX + listWidth
                    && mouseY >= entryY && mouseY < entryY + REWARD_ENTRY_HEIGHT;
            // Only show as unconfigured (red) if it's a synced Twitch reward without an action
            // New rewards created via Add Reward shouldn't show red
            boolean needsValue = entry.action.getEffectiveType() != RewardAction.ActionType.RANDOM_MOB_SAME &&
                                 entry.action.getEffectiveType() != RewardAction.ActionType.RANDOM_MOB_EACH;
            boolean hasNoValue = entry.action.getValue() == null || entry.action.getValue().isEmpty();
            boolean isUnconfigured = entry.action.hasTwitchReward() && hasNoValue && needsValue;
            boolean isHidden = entry.action.isHidden();

            // Background
            if (isSelected) {
                graphics.fill(listX, entryY, listX + listWidth, entryY + REWARD_ENTRY_HEIGHT - 2, 0x80FFFF00);
            } else if (isHovered) {
                graphics.fill(listX, entryY, listX + listWidth, entryY + REWARD_ENTRY_HEIGHT - 2, 0x40FFFFFF);
            }

            // Hidden indicator - draw a strikethrough/dimmed background
            if (isHidden) {
                graphics.fill(listX, entryY, listX + listWidth, entryY + REWARD_ENTRY_HEIGHT - 2, 0x40FF0000);
            }

            // Reward name (gray if hidden, red if unconfigured, white otherwise)
            int nameColor;
            if (isHidden) {
                nameColor = 0xFF888888;  // Gray for hidden
            } else if (isUnconfigured) {
                nameColor = 0xFFFF6666;  // Red for unconfigured
            } else {
                nameColor = 0xFFFFFFFF;  // White for normal
            }

            // Add [HIDDEN] prefix for hidden items
            String displayName = isHidden ? "[HIDDEN] " + entry.name : entry.name;
            graphics.drawString(this.font, displayName, listX + 5, entryY + 3, nameColor);

            // Action description
            String actionText = getActionDescription(entry.action);
            int actionColor;
            if (isHidden) {
                actionColor = 0xFF666666;  // Dark gray for hidden
            } else if (isUnconfigured) {
                actionColor = 0xFFAA4444;  // Dark red for unconfigured
            } else {
                actionColor = 0xFFAAAAAA;  // Light gray for normal
            }
            graphics.drawString(this.font, actionText, listX + 5, entryY + 14, actionColor);

            // Status indicators on right side
            int rightX = listX + listWidth - 5;

            // Published status indicator
            boolean isPublished = entry.action.hasTwitchReward();
            String statusIcon = isPublished ? "\u2713" : "\u25CB";  // Checkmark or circle
            int statusColor = isHidden ? 0xFF666666 : (isPublished ? 0xFF55FF55 : 0xFF888888);
            int statusWidth = this.font.width(statusIcon);
            graphics.drawString(this.font, statusIcon, rightX - statusWidth, entryY + 3, statusColor);
            rightX -= statusWidth + 5;

            // Cost
            if (entry.action.getCost() > 0) {
                String costText = entry.action.getCost() + " pts";
                int costWidth = this.font.width(costText);
                int costColor = isHidden ? 0xFF886600 : 0xFFFFAA00;  // Dimmer for hidden
                graphics.drawString(this.font, costText, rightX - costWidth, entryY + 3, costColor);
            }
        }

        // Scroll indicator - draw on the right side next to scroll buttons
        if (rewardEntries.size() > visibleEntries) {
            String scrollText = (startIndex + 1) + "-" + endIndex + "/" + rewardEntries.size();
            int scrollTextX = listX + listWidth + 8;
            int scrollTextY = REWARDS_LIST_TOP + listHeight / 2 - 4;
            graphics.drawString(this.font, scrollText, scrollTextX, scrollTextY, 0xFF888888);
        }
    }

    private String getActionDescription(RewardAction action) {
        String value = action.getValue();
        RewardAction.ActionType effectiveType = action.getEffectiveType();

        // Random mob types don't need a value
        boolean needsValue = effectiveType != RewardAction.ActionType.RANDOM_MOB_SAME &&
                            effectiveType != RewardAction.ActionType.RANDOM_MOB_EACH;

        if ((value == null || value.isEmpty()) && needsValue) {
            return "Not configured - click Edit to set up";
        }

        // Build count string (with range if applicable)
        String countStr;
        if (action.hasRandomCount()) {
            countStr = action.getCount() + "-" + action.getMaxCount() + "x";
        } else {
            countStr = action.getCount() + "x";
        }

        return switch (effectiveType) {
            case SPAWN_MOB -> "Spawn " + countStr + " " + value;
            case RANDOM_MOB_SAME -> "Spawn " + countStr + " Random Mob (same)";
            case RANDOM_MOB_EACH -> "Spawn " + countStr + " Random Mobs (each different)";
            case GIVE_ITEM -> "Give " + countStr + " " + value;
            case EXECUTE_COMMAND -> "Command: " + truncate(value, 50);
        };
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private void syncFromTwitch() {
        // Check if in test mode - sync won't work with mock server
        if (TwitchEventSub.isTestMode()) {
            syncStatus = "Sync unavailable in test mode";
            syncStatusTime = System.currentTimeMillis();
            return;
        }

        syncStatus = "Syncing...";
        syncStatusTime = System.currentTimeMillis();

        TwitchAPI.getRewards(rewards -> {
            if (this.minecraft == null) return;

            this.minecraft.execute(() -> {
                if (rewards == null) {
                    syncStatus = "Failed to fetch rewards";
                    syncStatusTime = System.currentTimeMillis();
                    return;
                }

                int imported = 0;
                Map<String, RewardAction> existingRewards = ModConfig.get().getRewards();

                for (int i = 0; i < rewards.size(); i++) {
                    JsonObject reward = rewards.get(i).getAsJsonObject();
                    String title = reward.get("title").getAsString();
                    String rewardId = reward.get("id").getAsString();
                    int cost = reward.get("cost").getAsInt();

                    // Parse cooldown from Twitch response
                    int cooldown = 0;
                    if (reward.has("global_cooldown_setting") && !reward.get("global_cooldown_setting").isJsonNull()) {
                        JsonObject cooldownSettings = reward.getAsJsonObject("global_cooldown_setting");
                        if (cooldownSettings.has("is_enabled") && cooldownSettings.get("is_enabled").getAsBoolean()) {
                            cooldown = cooldownSettings.get("global_cooldown_seconds").getAsInt();
                        }
                    }

                    // Check if we already have a reward with this name
                    if (!existingRewards.containsKey(title)) {
                        // Create a blank reward entry (no action configured yet)
                        RewardAction action = new RewardAction();
                        action.setType(RewardAction.ActionType.EXECUTE_COMMAND);
                        action.setValue("");  // Blank - user needs to configure
                        action.setCount(1);
                        action.setTwitchRewardId(rewardId);
                        action.setCost(cost);
                        action.setCooldownSeconds(cooldown);

                        ModConfig.get().setReward(title, action);
                        imported++;
                    } else {
                        // Update existing reward with Twitch ID, cost, and cooldown if not set
                        RewardAction existing = existingRewards.get(title);
                        if (!existing.hasTwitchReward()) {
                            existing.setTwitchRewardId(rewardId);
                        }
                        if (existing.getCost() == 0) {
                            existing.setCost(cost);
                        }
                        if (existing.getCooldownSeconds() == 0 && cooldown > 0) {
                            existing.setCooldownSeconds(cooldown);
                        }
                        ModConfig.get().setReward(title, existing);
                    }
                }

                if (imported > 0) {
                    syncStatus = "Imported " + imported + " reward" + (imported > 1 ? "s" : "");
                } else {
                    syncStatus = "All rewards already synced";
                }
                syncStatusTime = System.currentTimeMillis();
                rebuildWidgets();
            });
        });
    }

    private void publishReward(RewardEntry entry) {
        // Check if in test mode - publish won't work with mock server
        if (TwitchEventSub.isTestMode()) {
            syncStatus = "Publish unavailable in test mode";
            syncStatusTime = System.currentTimeMillis();
            return;
        }

        syncStatus = "Publishing...";
        syncStatusTime = System.currentTimeMillis();

        int cost = entry.action.getCost() > 0 ? entry.action.getCost() : 100;  // Default to 100 if not set
        int cooldown = entry.action.getCooldownSeconds();

        TwitchAPI.createReward(entry.name, cost, cooldown, rewardId -> {
            if (this.minecraft == null) return;

            this.minecraft.execute(() -> {
                if (rewardId != null) {
                    // Success - update the local reward with the Twitch ID
                    entry.action.setTwitchRewardId(rewardId);
                    ModConfig.get().setReward(entry.name, entry.action);
                    syncStatus = "Published: " + entry.name;
                } else {
                    syncStatus = "Failed to publish";
                }
                syncStatusTime = System.currentTimeMillis();
                rebuildWidgets();
            });
        });
    }

    private void unpublishReward(RewardEntry entry) {
        // Check if in test mode - unpublish won't work with mock server
        if (TwitchEventSub.isTestMode()) {
            syncStatus = "Unpublish unavailable in test mode";
            syncStatusTime = System.currentTimeMillis();
            return;
        }

        if (!entry.action.hasTwitchReward()) {
            syncStatus = "Reward not published";
            syncStatusTime = System.currentTimeMillis();
            return;
        }

        syncStatus = "Removing from Twitch...";
        syncStatusTime = System.currentTimeMillis();

        String rewardId = entry.action.getTwitchRewardId();

        TwitchAPI.deleteReward(rewardId, success -> {
            if (this.minecraft == null) return;

            this.minecraft.execute(() -> {
                if (success) {
                    // Success - clear the Twitch ID from local reward
                    entry.action.setTwitchRewardId(null);
                    ModConfig.get().setReward(entry.name, entry.action);
                    syncStatus = "Unpublished: " + entry.name;
                } else {
                    syncStatus = "Failed to unpublish";
                }
                syncStatusTime = System.currentTimeMillis();
                rebuildWidgets();
            });
        });
    }

    private void updateRewardOnTwitch(RewardEntry entry) {
        // Check if in test mode
        if (TwitchEventSub.isTestMode()) {
            syncStatus = "Update unavailable in test mode";
            syncStatusTime = System.currentTimeMillis();
            return;
        }

        if (!entry.action.hasTwitchReward()) {
            syncStatus = "Reward not published";
            syncStatusTime = System.currentTimeMillis();
            return;
        }

        syncStatus = "Updating on Twitch...";
        syncStatusTime = System.currentTimeMillis();

        String rewardId = entry.action.getTwitchRewardId();
        int cost = entry.action.getCost() > 0 ? entry.action.getCost() : 100;
        int cooldown = entry.action.getCooldownSeconds();

        TwitchAPI.updateReward(rewardId, cost, cooldown, success -> {
            if (this.minecraft == null) return;

            this.minecraft.execute(() -> {
                if (success) {
                    syncStatus = "Updated: " + entry.name;
                } else {
                    syncStatus = "Failed to update";
                }
                syncStatusTime = System.currentTimeMillis();
                rebuildWidgets();
            });
        });
    }

    // Track settings content height for scrolling
    private int settingsContentHeight = 0;
    private int settingsContentStartY = 0;
    private int settingsContentEndY = 0;

    private void initSettingsTab(int centerX, int startY) {
        int buttonWidth = 200;
        int buttonHeight = 20;
        int buttonSpacing = 26;

        // Calculate available height for scrollable content (between tabs and Done button)
        settingsContentStartY = startY + 10;
        settingsContentEndY = this.height - 40;  // Leave room for Done button
        int availableHeight = settingsContentEndY - settingsContentStartY;

        // Apply scroll offset - but widgets outside visible area will be hidden via Y position
        int currentY = settingsContentStartY - settingsScrollOffset;

        boolean hasToken = ModConfig.get().hasValidToken();
        boolean isConnected = TwitchEventSub.isConnected();

        // Check channel points status when logged in (only once, for warning display)
        if (hasToken && !channelPointsChecked && !TwitchEventSub.isTestMode()) {
            channelPointsChecked = true;
            TwitchAPI.checkChannelPointsEnabled(result -> {
                if (this.minecraft != null) {
                    this.minecraft.execute(() -> {
                        channelPointsEnabled = result;
                    });
                }
            });
        }

        // Connection Settings collapsible group (at top)
        String connectionStatus = !hasToken ? " (Not logged in)" : (isConnected ? " (Connected)" : " (Disconnected)");
        String expandIcon = connectionSettingsExpanded ? "\u25BC" : "\u25B6";
        Button connectionGroupBtn = Button.builder(
                Component.literal(expandIcon + " Twitch Connection" + connectionStatus),
                btn -> {
                    connectionSettingsExpanded = !connectionSettingsExpanded;
                    rebuildWidgets();
                })
                .pos(centerX - buttonWidth / 2, currentY)
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.literal("Login and connect to Twitch")))
                .build();
        addSettingsWidget(connectionGroupBtn);
        currentY += buttonSpacing;

        // Connection sub-settings (only shown when expanded)
        if (connectionSettingsExpanded) {
            int indentedWidth = buttonWidth - 20;
            int indentedX = centerX - buttonWidth / 2 + 20;

            if (!hasToken) {
                // Not logged in - show login button
                Button loginBtn = Button.builder(Component.literal("Login with Twitch"), btn -> {
                    TwitchAuth.startAuthFlow(success -> {
                        if (this.minecraft != null) {
                            this.minecraft.execute(this::rebuildWidgets);
                        }
                    });
                })
                        .pos(indentedX, currentY)
                        .size(indentedWidth, buttonHeight)
                        .build();
                addSettingsWidget(loginBtn);
                currentY += buttonSpacing;
            } else {
                // Logged in - show connect/disconnect and other options
                String channelName = ModConfig.get().getChannelName();
                if (!channelName.isEmpty()) {
                    // Show channel name as disabled button (label)
                    Button channelLabel = Button.builder(
                            Component.literal("Channel: " + channelName),
                            btn -> {})
                            .pos(indentedX, currentY)
                            .size(indentedWidth, buttonHeight)
                            .build();
                    channelLabel.active = false;
                    addSettingsWidget(channelLabel);
                    currentY += buttonSpacing;
                }

                // Connect/Disconnect button
                if (!isConnected) {
                    Button connectBtn = Button.builder(Component.literal("Connect"), btn -> {
                        TwitchEventSub.connect(redemption -> {
                            RewardHandler.handleRedemption(redemption);
                        });
                        // Don't rebuild immediately - tick() will handle it when connection is established
                    })
                            .pos(indentedX, currentY)
                            .size(indentedWidth, buttonHeight)
                            .build();
                    addSettingsWidget(connectBtn);
                } else {
                    Button disconnectBtn = Button.builder(Component.literal("Disconnect"), btn -> {
                        TwitchEventSub.disconnect();
                        rebuildWidgets();
                    })
                            .pos(indentedX, currentY)
                            .size(indentedWidth, buttonHeight)
                            .build();
                    addSettingsWidget(disconnectBtn);
                }
                currentY += buttonSpacing;

                // Re-login and Logout buttons side by side
                int smallBtnWidth = (indentedWidth - 5) / 2;
                Button reLoginBtn = Button.builder(Component.literal("Re-login"), btn -> {
                    TwitchAuth.startAuthFlow(success -> {
                        if (this.minecraft != null) {
                            this.minecraft.execute(this::rebuildWidgets);
                        }
                    });
                })
                        .pos(indentedX, currentY)
                        .size(smallBtnWidth, buttonHeight)
                        .build();
                addSettingsWidget(reLoginBtn);

                Button logoutBtn = Button.builder(Component.literal("Logout"), btn -> {
                    TwitchEventSub.disconnect();
                    ModConfig.get().setAccessToken("");
                    ModConfig.get().setChannelId("");
                    ModConfig.get().setChannelName("");
                    rebuildWidgets();
                })
                        .pos(indentedX + smallBtnWidth + 5, currentY)
                        .size(smallBtnWidth, buttonHeight)
                        .build();
                addSettingsWidget(logoutBtn);
                currentY += buttonSpacing;
            }
        }

        // Chat messages toggle
        boolean chatMessagesEnabled = ModConfig.get().isShowChatMessages();
        Button chatMsgBtn = Button.builder(
                Component.literal("Chat Messages: " + (chatMessagesEnabled ? "ON" : "OFF")),
                btn -> {
                    ModConfig.get().setShowChatMessages(!ModConfig.get().isShowChatMessages());
                    rebuildWidgets();
                })
                .pos(centerX - buttonWidth / 2, currentY)
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.literal("Show messages in chat when rewards are redeemed")))
                .build();
        addSettingsWidget(chatMsgBtn);
        currentY += buttonSpacing;

        // Test mode toggle
        boolean testMode = TwitchEventSub.isTestMode();
        Button testModeBtn = Button.builder(
                Component.literal("Test Mode: " + (testMode ? "ON" : "OFF")),
                btn -> {
                    TwitchEventSub.setTestMode(!TwitchEventSub.isTestMode());
                    rebuildWidgets();
                })
                .pos(centerX - buttonWidth / 2, currentY)
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.literal("For mod developers only. Uses local mock server (localhost:8080) instead of Twitch.")))
                .build();
        addSettingsWidget(testModeBtn);
        currentY += buttonSpacing;

        // HUD Settings collapsible group
        String hudExpandIcon = hudSettingsExpanded ? "\u25BC" : "\u25B6";  // Down arrow or right arrow
        Button hudGroupBtn = Button.builder(
                Component.literal(hudExpandIcon + " HUD Settings"),
                btn -> {
                    hudSettingsExpanded = !hudSettingsExpanded;
                    rebuildWidgets();
                })
                .pos(centerX - buttonWidth / 2, currentY)
                .size(buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.literal("Configure the redemption notification HUD")))
                .build();
        addSettingsWidget(hudGroupBtn);
        currentY += buttonSpacing;

        // HUD sub-settings (only shown when expanded)
        if (hudSettingsExpanded) {
            int indentedWidth = buttonWidth - 20;
            int indentedX = centerX - buttonWidth / 2 + 20;

            // HUD Position setting
            ModConfig.HudPosition hudPosition = ModConfig.get().getHudPosition();
            Button hudPositionBtn = Button.builder(
                    Component.literal("Position: " + hudPosition.getDisplayName()),
                    btn -> {
                        ModConfig.get().setHudPosition(ModConfig.get().getHudPosition().next());
                        rebuildWidgets();
                    })
                    .pos(indentedX, currentY)
                    .size(indentedWidth, buttonHeight)
                    .tooltip(Tooltip.create(Component.literal("Position of the redemption HUD on screen")))
                    .build();
            addSettingsWidget(hudPositionBtn);
            currentY += buttonSpacing;

            // HUD Max Entries setting (with +/- buttons)
            int maxEntries = ModConfig.get().getHudMaxEntries();
            int smallBtnWidth = 25;
            int labelWidth = indentedWidth - smallBtnWidth * 2 - 10;

            // Decrease button
            Button decreaseBtn = Button.builder(Component.literal("-"), btn -> {
                ModConfig.get().setHudMaxEntries(ModConfig.get().getHudMaxEntries() - 1);
                rebuildWidgets();
            })
                    .pos(indentedX, currentY)
                    .size(smallBtnWidth, buttonHeight)
                    .build();
            decreaseBtn.active = maxEntries > 1;
            addSettingsWidget(decreaseBtn);

            // Label
            Button maxEntriesLabel = Button.builder(
                    Component.literal("Max Messages: " + maxEntries),
                    btn -> {})
                    .pos(indentedX + smallBtnWidth + 5, currentY)
                    .size(labelWidth, buttonHeight)
                    .build();
            maxEntriesLabel.active = false;
            addSettingsWidget(maxEntriesLabel);

            // Increase button
            Button increaseBtn = Button.builder(Component.literal("+"), btn -> {
                ModConfig.get().setHudMaxEntries(ModConfig.get().getHudMaxEntries() + 1);
                rebuildWidgets();
            })
                    .pos(indentedX + indentedWidth - smallBtnWidth, currentY)
                    .size(smallBtnWidth, buttonHeight)
                    .build();
            increaseBtn.active = maxEntries < 10;
            addSettingsWidget(increaseBtn);
            currentY += buttonSpacing;

            // HUD Scale setting (with +/- buttons)
            float scale = ModConfig.get().getHudScale();
            String scaleText = String.format("%.1fx", scale);

            // Decrease scale button
            Button decreaseScaleBtn = Button.builder(Component.literal("-"), btn -> {
                ModConfig.get().setHudScale(ModConfig.get().getHudScale() - 0.1f);
                rebuildWidgets();
            })
                    .pos(indentedX, currentY)
                    .size(smallBtnWidth, buttonHeight)
                    .build();
            decreaseScaleBtn.active = scale > 0.5f;
            addSettingsWidget(decreaseScaleBtn);

            // Scale label
            Button scaleLabel = Button.builder(
                    Component.literal("Scale: " + scaleText),
                    btn -> {})
                    .pos(indentedX + smallBtnWidth + 5, currentY)
                    .size(labelWidth, buttonHeight)
                    .build();
            scaleLabel.active = false;
            addSettingsWidget(scaleLabel);

            // Increase scale button
            Button increaseScaleBtn = Button.builder(Component.literal("+"), btn -> {
                ModConfig.get().setHudScale(ModConfig.get().getHudScale() + 0.1f);
                rebuildWidgets();
            })
                    .pos(indentedX + indentedWidth - smallBtnWidth, currentY)
                    .size(smallBtnWidth, buttonHeight)
                    .build();
            increaseScaleBtn.active = scale < 2.0f;
            addSettingsWidget(increaseScaleBtn);
            currentY += buttonSpacing;
        }

        // Calculate total content height (for scrolling)
        settingsContentHeight = (currentY + settingsScrollOffset) - settingsContentStartY;
    }

    // Helper to add settings button only if within visible bounds
    private void addSettingsWidget(Button button) {
        int btnTop = button.getY();
        int btnBottom = btnTop + button.getHeight();
        // Only add widget if it's at least partially visible in the scroll area
        if (btnBottom > settingsContentStartY && btnTop < settingsContentEndY) {
            this.addRenderableWidget(button);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (currentTab == Tab.REWARDS && !rewardEntries.isEmpty()) {
            int listHeight = this.height - 165;
            int visibleEntries = listHeight / REWARD_ENTRY_HEIGHT;
            int maxScroll = Math.max(0, rewardEntries.size() - visibleEntries);

            int oldOffset = rewardScrollOffset;
            rewardScrollOffset = Math.max(0, Math.min(maxScroll, rewardScrollOffset - (int) verticalAmount));

            if (oldOffset != rewardScrollOffset) {
                rebuildWidgets();
                return true;
            }
        } else if (currentTab == Tab.SETTINGS) {
            int availableHeight = settingsContentEndY - settingsContentStartY;
            int maxScroll = Math.max(0, settingsContentHeight - availableHeight);

            int oldOffset = settingsScrollOffset;
            int scrollAmount = (int)(verticalAmount * 26);  // Scroll by button spacing amount
            settingsScrollOffset = Math.max(0, Math.min(maxScroll, settingsScrollOffset - scrollAmount));

            if (oldOffset != settingsScrollOffset) {
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private enum Tab {
        REWARDS,
        SETTINGS
    }

    private record RewardEntry(String name, RewardAction action) {}
}
