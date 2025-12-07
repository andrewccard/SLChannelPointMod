package andrew.slchannelpointmod.gui;

import andrew.slchannelpointmod.config.ModConfig;
import andrew.slchannelpointmod.config.RewardAction;
import andrew.slchannelpointmod.config.RewardAction.ActionType;
import andrew.slchannelpointmod.config.RewardAction.QuantityMode;
import andrew.slchannelpointmod.rewards.RewardHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RewardEditorScreen extends Screen {
    private final Screen parent;
    private final String originalName;
    private final RewardAction originalAction;
    private final boolean isEditing;

    // Form fields
    private EditBox nameField;
    private EditBox costField;
    private EditBox cooldownField;
    private EditBox countField;
    private EditBox maxCountField;  // For random quantity range
    private EditBox valueField;

    // Dropdown selections
    private ActionType selectedActionType = ActionType.SPAWN_MOB;
    private QuantityMode selectedQuantityMode = QuantityMode.FIXED;

    // Dropdown buttons
    private Button actionTypeBtn;
    private Button quantityModeBtn;
    private Button pickValueBtn;
    private Button configurePoolBtn;
    private Button saveButton;
    private Button testButton;

    // Dropdown state
    private boolean actionDropdownOpen = false;

    // Preserved values (for when returning from picker screens)
    private String preservedName = "";
    private String preservedCost = "";
    private String preservedCooldown = "";
    private String preservedCount = "";
    private String preservedMaxCount = "";
    private String preservedValue = "";
    private java.util.List<String> preservedMobPool = null;
    private boolean valuesPreserved = false;

    // Validation state
    private String saveBlockedReason = null;

    public RewardEditorScreen(Screen parent, String name, RewardAction action) {
        super(Component.literal(name == null ? "Create Reward" : "Edit Reward"));
        this.parent = parent;
        this.originalName = name;
        this.originalAction = action;
        this.isEditing = name != null;

        if (action != null) {
            // Use effective type for backwards compatibility
            this.selectedActionType = action.getEffectiveType();
            this.selectedQuantityMode = action.getQuantityMode();
            // Preserve existing mob pool
            if (action.getMobPool() != null) {
                this.preservedMobPool = new java.util.ArrayList<>(action.getMobPool());
            }
        }
    }

    // Called by picker screens to set the selected value
    public void setPickedValue(String value) {
        this.preservedValue = value;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int fieldWidth = 200;

        // Row 1: Name field (Y = 30)
        int nameY = 30;
        this.nameField = new EditBox(this.font, centerX - fieldWidth / 2, nameY, fieldWidth, 20,
                Component.literal("Reward Name"));
        this.nameField.setMaxLength(64);
        this.nameField.setHint(Component.literal("e.g. Spawn Zombie"));
        if (valuesPreserved) {
            this.nameField.setValue(preservedName);
        } else if (originalName != null) {
            this.nameField.setValue(originalName);
        }
        this.addRenderableWidget(this.nameField);

        // Row 2: Cost and Cooldown fields (Y = 75)
        int costY = 75;
        int halfFieldWidth = fieldWidth / 2 - 5;

        this.costField = new EditBox(this.font, centerX - fieldWidth / 2, costY, halfFieldWidth, 20,
                Component.literal("Cost"));
        this.costField.setMaxLength(10);
        this.costField.setHint(Component.literal("100"));
        this.costField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        if (valuesPreserved) {
            this.costField.setValue(preservedCost);
        } else if (originalAction != null && originalAction.getCost() > 0) {
            this.costField.setValue(String.valueOf(originalAction.getCost()));
        }
        this.addRenderableWidget(this.costField);

        // Cooldown field (same row as cost)
        this.cooldownField = new EditBox(this.font, centerX + 5, costY, halfFieldWidth, 20,
                Component.literal("Cooldown"));
        this.cooldownField.setMaxLength(6);
        this.cooldownField.setHint(Component.literal("0"));
        this.cooldownField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        if (valuesPreserved) {
            this.cooldownField.setValue(preservedCooldown);
        } else if (originalAction != null && originalAction.getCooldownSeconds() > 0) {
            this.cooldownField.setValue(String.valueOf(originalAction.getCooldownSeconds()));
        }
        this.addRenderableWidget(this.cooldownField);

        // Row 3: Quantity Mode dropdown and fields (Y = 120)
        int qtyY = 120;
        quantityModeBtn = Button.builder(
                Component.literal(selectedQuantityMode.getDisplayName()),
                btn -> cycleQuantityMode())
                .pos(centerX - fieldWidth / 2, qtyY)
                .size(halfFieldWidth, 20)
                .tooltip(Tooltip.create(Component.literal("Click to cycle: Fixed or Random Range")))
                .build();
        quantityModeBtn.visible = !isCommandType();
        this.addRenderableWidget(quantityModeBtn);

        // Quantity fields - shown to the right of quantity mode dropdown
        int qtyFieldWidth = halfFieldWidth;
        this.countField = new EditBox(this.font, centerX + 5, qtyY, qtyFieldWidth / 2 - 5, 20,
                Component.literal("Count"));
        this.countField.setMaxLength(5);
        this.countField.setHint(Component.literal("1"));
        this.countField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        if (valuesPreserved) {
            this.countField.setValue(preservedCount);
        } else if (originalAction != null) {
            this.countField.setValue(String.valueOf(originalAction.getCount()));
        }
        this.countField.visible = !isCommandType();
        this.addRenderableWidget(this.countField);

        // Max count field for random range
        this.maxCountField = new EditBox(this.font, centerX + 5 + qtyFieldWidth / 2 + 5, qtyY, qtyFieldWidth / 2 - 5, 20,
                Component.literal("Max"));
        this.maxCountField.setMaxLength(5);
        this.maxCountField.setHint(Component.literal("(max)"));
        this.maxCountField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        if (valuesPreserved) {
            this.maxCountField.setValue(preservedMaxCount);
        } else if (originalAction != null && originalAction.getMaxCount() > 0) {
            this.maxCountField.setValue(String.valueOf(originalAction.getMaxCount()));
        }
        this.maxCountField.visible = !isCommandType() && selectedQuantityMode == QuantityMode.RANGE;
        this.addRenderableWidget(this.maxCountField);

        // Row 4: Action Type dropdown (Y = 165)
        int actionBtnY = 165;
        String dropdownIcon = actionDropdownOpen ? "\u25B2" : "\u25BC";  // Up or down arrow
        actionTypeBtn = Button.builder(
                Component.literal(selectedActionType.getDisplayName() + " " + dropdownIcon),
                btn -> toggleActionDropdown())
                .pos(centerX - fieldWidth / 2, actionBtnY)
                .size(fieldWidth, 20)
                .tooltip(Tooltip.create(Component.literal("Click to select action type")))
                .build();
        this.addRenderableWidget(actionTypeBtn);

        // Dropdown options (shown when expanded)
        if (actionDropdownOpen) {
            ActionType[] types = ActionType.values();
            for (int i = 0; i < types.length; i++) {
                final ActionType type = types[i];
                int optionY = actionBtnY + 20 + (i * 20);
                boolean isSelected = type == selectedActionType;
                String prefix = isSelected ? "> " : "  ";
                Button optionBtn = Button.builder(
                        Component.literal(prefix + type.getDisplayName()),
                        btn -> selectActionType(type))
                        .pos(centerX - fieldWidth / 2, optionY)
                        .size(fieldWidth, 20)
                        .build();
                this.addRenderableWidget(optionBtn);
            }
        }

        // Row 5: Value field (Y = 210) - hidden for random mob types or when dropdown is open
        int valueY = 210;
        boolean showValueField = needsValueField() && !actionDropdownOpen;
        this.valueField = new EditBox(this.font, centerX - fieldWidth / 2, valueY, fieldWidth - 30, 20,
                Component.literal("Value"));
        this.valueField.setMaxLength(256);
        if (valuesPreserved) {
            this.valueField.setValue(preservedValue);
        } else if (originalAction != null && originalAction.getValue() != null) {
            this.valueField.setValue(originalAction.getValue());
        }
        this.valueField.visible = showValueField;
        this.addRenderableWidget(this.valueField);
        updateValueFieldHint();

        // Pick button (for mob/item selection)
        pickValueBtn = Button.builder(Component.literal("..."), btn -> openPicker())
                .pos(centerX + fieldWidth / 2 - 25, valueY)
                .size(25, 20)
                .tooltip(Tooltip.create(Component.literal("Browse...")))
                .build();
        pickValueBtn.visible = showValueField && !isCommandType();
        this.addRenderableWidget(pickValueBtn);

        // Configure Pool button (for random mob types only)
        boolean showPoolBtn = isRandomMobType() && !actionDropdownOpen;
        String poolBtnText = (preservedMobPool != null && !preservedMobPool.isEmpty())
                ? "Configure Pool (" + preservedMobPool.size() + ")"
                : "Configure Pool (default)";
        configurePoolBtn = Button.builder(Component.literal(poolBtnText), btn -> openPoolEditor())
                .pos(centerX - fieldWidth / 2, valueY)
                .size(fieldWidth, 20)
                .tooltip(Tooltip.create(Component.literal("Select which mobs can spawn for this reward")))
                .build();
        configurePoolBtn.visible = showPoolBtn;
        this.addRenderableWidget(configurePoolBtn);

        // Bottom buttons
        int bottomY = this.height - 30;
        int bottomBtnWidth = 80;

        // Save button
        saveButton = Button.builder(Component.literal("Save"), btn -> saveReward())
                .pos(centerX - bottomBtnWidth - 50, bottomY)
                .size(bottomBtnWidth, 20)
                .build();
        this.addRenderableWidget(saveButton);

        // Test button
        testButton = Button.builder(Component.literal("Test"), btn -> testReward())
                .pos(centerX - bottomBtnWidth / 2, bottomY)
                .size(bottomBtnWidth, 20)
                .tooltip(Tooltip.create(Component.literal("Test this reward without saving")))
                .build();
        this.addRenderableWidget(testButton);

        // Cancel button
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onClose())
                .pos(centerX + 50, bottomY)
                .size(bottomBtnWidth, 20)
                .build());

        // Initial validation
        updateButtonStates();
    }

    private boolean isCommandType() {
        return selectedActionType == ActionType.EXECUTE_COMMAND;
    }

    private boolean isRandomMobType() {
        return selectedActionType == ActionType.RANDOM_MOB_SAME ||
               selectedActionType == ActionType.RANDOM_MOB_EACH;
    }

    private boolean needsValueField() {
        // Random mob types don't need a value field
        return !isRandomMobType();
    }

    private void toggleActionDropdown() {
        preserveCurrentValues();
        actionDropdownOpen = !actionDropdownOpen;
        rebuildWidgets();
    }

    private void selectActionType(ActionType type) {
        preserveCurrentValues();
        selectedActionType = type;
        actionDropdownOpen = false;
        rebuildWidgets();
    }

    private boolean isFormValid() {
        saveBlockedReason = null;

        // Name is required
        if (nameField == null || nameField.getValue().trim().isEmpty()) {
            saveBlockedReason = "Reward name is required";
            return false;
        }

        String name = nameField.getValue().trim();

        // Check for duplicate name (case-insensitive)
        // Only check if this is a new reward or we're changing the name
        if (!isEditing || !name.equalsIgnoreCase(originalName)) {
            if (ModConfig.get().getReward(name) != null) {
                saveBlockedReason = "A reward with this name already exists";
                return false;
            }
        }

        // Value is required for non-random-mob types
        if (needsValueField()) {
            if (valueField == null || valueField.getValue().trim().isEmpty()) {
                saveBlockedReason = "Value is required for this action type";
                return false;
            }
        }

        return true;
    }

    private void updateButtonStates() {
        boolean valid = isFormValid();
        if (saveButton != null) {
            saveButton.active = valid;
            // Show tooltip explaining why save is blocked
            if (!valid && saveBlockedReason != null) {
                saveButton.setTooltip(Tooltip.create(Component.literal(saveBlockedReason)));
            } else {
                saveButton.setTooltip(null);
            }
        }
        if (testButton != null) {
            testButton.active = valid;
            // Show same reason for test button
            if (!valid && saveBlockedReason != null) {
                testButton.setTooltip(Tooltip.create(Component.literal(saveBlockedReason)));
            } else {
                testButton.setTooltip(Tooltip.create(Component.literal("Test this reward without saving")));
            }
        }
    }

    private void cycleQuantityMode() {
        preserveCurrentValues();
        QuantityMode[] modes = QuantityMode.values();
        int currentIndex = selectedQuantityMode.ordinal();
        selectedQuantityMode = modes[(currentIndex + 1) % modes.length];
        rebuildWidgets();
    }

    private void updateValueFieldHint() {
        if (valueField == null) return;

        switch (selectedActionType) {
            case SPAWN_MOB -> valueField.setHint(Component.literal("minecraft:zombie"));
            case GIVE_ITEM -> valueField.setHint(Component.literal("minecraft:diamond"));
            case EXECUTE_COMMAND -> valueField.setHint(Component.literal("say Hello {player}!"));
            default -> valueField.setHint(Component.literal(""));
        }
    }

    private void openPicker() {
        // Save current field values before opening picker
        preserveCurrentValues();

        switch (selectedActionType) {
            case SPAWN_MOB -> this.minecraft.setScreen(new EntityPickerScreen(this, entity -> {
                preservedValue = entity;
            }));
            case GIVE_ITEM -> this.minecraft.setScreen(new ItemPickerScreen(this, item -> {
                preservedValue = item;
            }));
            default -> {}
        }
    }

    private void openPoolEditor() {
        preserveCurrentValues();
        // Pass current mob pool (or null for default)
        this.minecraft.setScreen(new MobPoolEditorScreen(this, preservedMobPool, pool -> {
            preservedMobPool = pool;
        }));
    }

    private void preserveCurrentValues() {
        preservedName = nameField.getValue();
        preservedCost = costField.getValue();
        preservedCooldown = cooldownField.getValue();
        preservedCount = countField.getValue();
        preservedMaxCount = maxCountField.getValue();
        preservedValue = valueField.getValue();
        valuesPreserved = true;
    }

    private void saveReward() {
        String name = nameField.getValue().trim();
        if (name.isEmpty()) {
            return;
        }

        String value = valueField.getValue().trim();
        // Allow empty value for random mob modes
        if (value.isEmpty() && needsValueField()) {
            return;
        }

        int cost = 100;
        try {
            if (!costField.getValue().isEmpty()) {
                cost = Integer.parseInt(costField.getValue());
            }
        } catch (NumberFormatException ignored) {}

        int count = 1;
        try {
            if (!countField.getValue().isEmpty()) {
                count = Integer.parseInt(countField.getValue());
            }
        } catch (NumberFormatException ignored) {}

        int maxCount = 0;
        try {
            if (!maxCountField.getValue().isEmpty()) {
                maxCount = Integer.parseInt(maxCountField.getValue());
            }
        } catch (NumberFormatException ignored) {}

        int cooldown = 0;
        try {
            if (!cooldownField.getValue().isEmpty()) {
                cooldown = Integer.parseInt(cooldownField.getValue());
            }
        } catch (NumberFormatException ignored) {}

        // Remove old reward if we're renaming
        if (isEditing && !name.equalsIgnoreCase(originalName)) {
            ModConfig.get().removeReward(originalName);
        }

        // Get existing Twitch reward ID if editing
        String twitchRewardId = null;
        if (originalAction != null) {
            twitchRewardId = originalAction.getTwitchRewardId();
        }

        RewardAction action = new RewardAction(selectedActionType, value, count, twitchRewardId, cost);
        action.setQuantityMode(selectedQuantityMode);
        action.setMaxCount(maxCount);
        action.setCooldownSeconds(cooldown);
        // Set mob pool for random mob types
        if (isRandomMobType() && preservedMobPool != null && !preservedMobPool.isEmpty()) {
            action.setMobPool(new java.util.ArrayList<>(preservedMobPool));
        }
        ModConfig.get().setReward(name, action);

        // Set the selection so it persists after we return to the main screen
        MainConfigScreen.selectReward(name);

        onClose();
    }

    private void testReward() {
        // First save the reward temporarily so it can be tested
        String name = nameField.getValue().trim();
        if (name.isEmpty()) {
            return;
        }

        String value = valueField.getValue().trim();
        // Allow empty value for random mob modes
        if (value.isEmpty() && needsValueField()) {
            return;
        }

        int cost = 100;
        try {
            if (!costField.getValue().isEmpty()) {
                cost = Integer.parseInt(costField.getValue());
            }
        } catch (NumberFormatException ignored) {}

        int count = 1;
        try {
            if (!countField.getValue().isEmpty()) {
                count = Integer.parseInt(countField.getValue());
            }
        } catch (NumberFormatException ignored) {}

        int maxCount = 0;
        try {
            if (!maxCountField.getValue().isEmpty()) {
                maxCount = Integer.parseInt(maxCountField.getValue());
            }
        } catch (NumberFormatException ignored) {}

        int cooldown = 0;
        try {
            if (!cooldownField.getValue().isEmpty()) {
                cooldown = Integer.parseInt(cooldownField.getValue());
            }
        } catch (NumberFormatException ignored) {}

        // Temporarily save the reward config
        String twitchRewardId = originalAction != null ? originalAction.getTwitchRewardId() : null;
        RewardAction action = new RewardAction(selectedActionType, value, count, twitchRewardId, cost);
        action.setQuantityMode(selectedQuantityMode);
        action.setMaxCount(maxCount);
        action.setCooldownSeconds(cooldown);
        // Set mob pool for random mob types
        if (isRandomMobType() && preservedMobPool != null && !preservedMobPool.isEmpty()) {
            action.setMobPool(new java.util.ArrayList<>(preservedMobPool));
        }
        ModConfig.get().setReward(name, action);

        // Simulate a redemption to test the reward
        RewardHandler.simulateRedemption(name, "TestUser");
    }

    @Override
    public void tick() {
        super.tick();
        // Update button states as user types
        updateButtonStates();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        // Title
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        int centerX = this.width / 2;
        int fieldWidth = 200;
        int labelX = centerX - fieldWidth / 2;

        // Labels
        // Name label (field at Y=30)
        graphics.drawString(this.font, "Reward Name:", labelX, 20, 0xFFFFFFFF);

        // Cost label (field at Y=75)
        graphics.drawString(this.font, "Cost:", labelX, 63, 0xFFFFFFFF);

        // Cooldown label (field at Y=75, right side)
        graphics.drawString(this.font, "Cooldown (sec):", centerX + 5, 63, 0xFFFFFFFF);

        // Quantity field labels (Y=120) - hidden for commands
        if (!isCommandType()) {
            if (selectedQuantityMode == QuantityMode.RANGE) {
                graphics.drawString(this.font, "Amount (min - max):", labelX, 108, 0xFFFFFFFF);
                // Draw "-" separator between min and max fields
                int qtyFieldWidth = (fieldWidth - 20) / 2;
                graphics.drawCenteredString(this.font, "-", labelX + qtyFieldWidth + 10, 125, 0xFFAAAAAA);
            } else {
                graphics.drawString(this.font, "Amount:", labelX, 108, 0xFFFFFFFF);
            }
        }

        // Action Type label (button at Y=165)
        graphics.drawString(this.font, "Action Type:", labelX, 153, 0xFFFFFFFF);

        // Value field label (field at Y=210) - hidden when dropdown is open
        if (!actionDropdownOpen) {
            if (needsValueField()) {
                String valueLabel = switch (selectedActionType) {
                    case SPAWN_MOB -> "Mob to Spawn:";
                    case GIVE_ITEM -> "Item to Give:";
                    case EXECUTE_COMMAND -> "Command:";
                    default -> "Value:";
                };
                graphics.drawString(this.font, valueLabel, labelX, 198, 0xFFFFFFFF);
            } else {
                // Show info text for random mob types (the button is shown above)
                String poolInfo = (preservedMobPool != null && !preservedMobPool.isEmpty())
                        ? "Custom pool: " + preservedMobPool.size() + " mobs"
                        : "Using default hostile mobs pool";
                graphics.drawString(this.font, poolInfo, labelX, 232, 0xFF888888);
            }

            // Command help text
            if (selectedActionType == ActionType.EXECUTE_COMMAND) {
                int helpY = 235;
                graphics.drawString(this.font, "Placeholders:", labelX, helpY, 0xFF888888);
                graphics.drawString(this.font, "{player} - Target player name", labelX, helpY + 12, 0xFF666666);
                graphics.drawString(this.font, "{redeemer} - Twitch username", labelX, helpY + 24, 0xFF666666);
            }
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
