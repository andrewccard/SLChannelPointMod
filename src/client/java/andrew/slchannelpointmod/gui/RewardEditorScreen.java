package andrew.slchannelpointmod.gui;

import andrew.slchannelpointmod.config.ModConfig;
import andrew.slchannelpointmod.config.RewardAction;
import andrew.slchannelpointmod.config.RewardAction.ActionType;
import andrew.slchannelpointmod.config.RewardAction.QuantityMode;
import andrew.slchannelpointmod.config.RewardAction.RandomMode;
import andrew.slchannelpointmod.config.RewardAction.SpecialActionType;
import andrew.slchannelpointmod.rewards.RewardHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

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
    private EditBox maxCountField;
    private EditBox valueField;

    // Selections
    private ActionType selectedActionType = ActionType.SPAWN_MOB;
    private QuantityMode selectedQuantityMode = QuantityMode.FIXED;
    private RandomMode selectedRandomMode = RandomMode.NONE;
    private SpecialActionType selectedSpecialType = SpecialActionType.SHUFFLE_INVENTORY;

    // Buttons
    private Button actionTypeBtn;
    private Button quantityModeBtn;
    private Button randomModeBtn;
    private Button specialTypeBtn;
    private Button pickValueBtn;
    private Button configurePoolBtn;
    private Button saveButton;
    private Button testButton;

    // Preserved values
    private String preservedName = "";
    private String preservedCost = "";
    private String preservedCooldown = "";
    private String preservedCount = "";
    private String preservedMaxCount = "";
    private String preservedValue = "";
    private List<String> preservedMobPool = null;
    private List<String> preservedEffectPool = null;
    private String preservedEffectDuration = "";
    private String preservedEffectAmplifier = "";
    private String preservedSoundVolume = "";
    private String preservedSoundPitch = "";
    private boolean valuesPreserved = false;

    private String saveBlockedReason = null;

    // Additional fields for effect/sound
    private EditBox effectDurationField;
    private EditBox effectAmplifierField;
    private EditBox soundVolumeField;
    private EditBox soundPitchField;
    private Button effectPoolBtn;

    private static final ActionType[] SELECTABLE_ACTION_TYPES = {
            ActionType.SPAWN_MOB,
            ActionType.GIVE_ITEM,
            ActionType.EXECUTE_COMMAND,
            ActionType.APPLY_EFFECT,
            ActionType.PLAY_SOUND,
            ActionType.SPECIAL
    };

    private static final int FIELD_WIDTH = 250;

    public RewardEditorScreen(Screen parent, String name, RewardAction action) {
        super(Component.literal(name == null ? "Create Reward" : "Edit Reward"));
        this.parent = parent;
        this.originalName = name;
        this.originalAction = action;
        this.isEditing = name != null;

        if (action != null) {
            this.selectedActionType = action.getType();
            this.selectedQuantityMode = action.getQuantityMode();
            this.selectedRandomMode = action.getRandomMode();
            if (action.getMobPool() != null) {
                this.preservedMobPool = new java.util.ArrayList<>(action.getMobPool());
            }
            if (action.getEffectPool() != null) {
                this.preservedEffectPool = new java.util.ArrayList<>(action.getEffectPool());
            }
            if (action.getType() == ActionType.SPECIAL && action.getValue() != null) {
                try {
                    this.selectedSpecialType = SpecialActionType.valueOf(action.getValue());
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public void setPickedValue(String value) {
        this.preservedValue = value;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int leftX = centerX - FIELD_WIDTH / 2;
        int halfWidth = (FIELD_WIDTH - 10) / 2;

        int currentY = 30;
        int rowHeight = 45;

        // === ROW 1: Name ===
        this.nameField = new EditBox(this.font, leftX, currentY + 12, FIELD_WIDTH, 20, Component.literal("Name"));
        this.nameField.setMaxLength(64);
        this.nameField.setHint(Component.literal("e.g. Spawn Zombie"));
        if (valuesPreserved) {
            this.nameField.setValue(preservedName);
        } else if (originalName != null) {
            this.nameField.setValue(originalName);
        }
        this.addRenderableWidget(this.nameField);
        currentY += rowHeight;

        // === ROW 2: Cost + Cooldown ===
        this.costField = new EditBox(this.font, leftX, currentY + 12, halfWidth, 20, Component.literal("Cost"));
        this.costField.setMaxLength(10);
        this.costField.setHint(Component.literal("100"));
        this.costField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        if (valuesPreserved) {
            this.costField.setValue(preservedCost);
        } else if (originalAction != null && originalAction.getCost() > 0) {
            this.costField.setValue(String.valueOf(originalAction.getCost()));
        }
        this.addRenderableWidget(this.costField);

        this.cooldownField = new EditBox(this.font, leftX + halfWidth + 10, currentY + 12, halfWidth, 20, Component.literal("Cooldown"));
        this.cooldownField.setMaxLength(6);
        this.cooldownField.setHint(Component.literal("0"));
        this.cooldownField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        if (valuesPreserved) {
            this.cooldownField.setValue(preservedCooldown);
        } else if (originalAction != null && originalAction.getCooldownSeconds() > 0) {
            this.cooldownField.setValue(String.valueOf(originalAction.getCooldownSeconds()));
        }
        this.addRenderableWidget(this.cooldownField);
        currentY += rowHeight;

        // === ROW 3: Action Type ===
        actionTypeBtn = Button.builder(
                Component.literal(selectedActionType.getDisplayName() + " \u25BC"),
                btn -> openActionTypeDropdown())
                .pos(leftX, currentY + 12)
                .size(FIELD_WIDTH, 20)
                .build();
        this.addRenderableWidget(actionTypeBtn);
        currentY += rowHeight;

        // === TYPE-SPECIFIC ROWS ===
        initTypeSpecificFields(leftX, currentY, halfWidth);

        // === BOTTOM BUTTONS ===
        int bottomY = this.height - 30;
        int btnWidth = 70;
        int btnSpacing = 10;
        int totalBtnWidth = btnWidth * 3 + btnSpacing * 2;
        int btnStartX = centerX - totalBtnWidth / 2;

        saveButton = Button.builder(Component.literal("Save"), btn -> saveReward())
                .pos(btnStartX, bottomY)
                .size(btnWidth, 20)
                .build();
        this.addRenderableWidget(saveButton);

        testButton = Button.builder(Component.literal("Test"), btn -> testReward())
                .pos(btnStartX + btnWidth + btnSpacing, bottomY)
                .size(btnWidth, 20)
                .build();
        this.addRenderableWidget(testButton);

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onClose())
                .pos(btnStartX + (btnWidth + btnSpacing) * 2, bottomY)
                .size(btnWidth, 20)
                .build());

        updateButtonStates();
    }

    private void initTypeSpecificFields(int leftX, int startY, int halfWidth) {
        int currentY = startY;
        int rowHeight = 45;

        // Initialize dummy fields first (will be replaced if needed)
        this.countField = new EditBox(this.font, 0, 0, 1, 1, Component.literal(""));
        this.countField.visible = false;
        this.addRenderableWidget(this.countField);

        this.maxCountField = new EditBox(this.font, 0, 0, 1, 1, Component.literal(""));
        this.maxCountField.visible = false;
        this.addRenderableWidget(this.maxCountField);

        this.valueField = new EditBox(this.font, 0, 0, 1, 1, Component.literal(""));
        this.valueField.visible = false;
        this.addRenderableWidget(this.valueField);

        this.effectDurationField = new EditBox(this.font, 0, 0, 1, 1, Component.literal(""));
        this.effectDurationField.visible = false;
        this.addRenderableWidget(this.effectDurationField);

        this.effectAmplifierField = new EditBox(this.font, 0, 0, 1, 1, Component.literal(""));
        this.effectAmplifierField.visible = false;
        this.addRenderableWidget(this.effectAmplifierField);

        this.soundVolumeField = new EditBox(this.font, 0, 0, 1, 1, Component.literal(""));
        this.soundVolumeField.visible = false;
        this.addRenderableWidget(this.soundVolumeField);

        this.soundPitchField = new EditBox(this.font, 0, 0, 1, 1, Component.literal(""));
        this.soundPitchField.visible = false;
        this.addRenderableWidget(this.soundPitchField);

        switch (selectedActionType) {
            case SPAWN_MOB, GIVE_ITEM -> initMobItemFields(leftX, currentY, halfWidth, rowHeight);
            case EXECUTE_COMMAND -> initCommandFields(leftX, currentY);
            case APPLY_EFFECT -> initEffectFields(leftX, currentY, halfWidth, rowHeight);
            case PLAY_SOUND -> initSoundFields(leftX, currentY, halfWidth, rowHeight);
            case SPECIAL -> initSpecialFields(leftX, currentY);
        }
    }

    private void initMobItemFields(int leftX, int startY, int halfWidth, int rowHeight) {
        int currentY = startY;

        // Row: Selection Mode
        randomModeBtn = Button.builder(
                Component.literal(selectedRandomMode.getDisplayName() + " \u25BC"),
                btn -> openRandomModeDropdown())
                .pos(leftX, currentY + 12)
                .size(FIELD_WIDTH, 20)
                .build();
        this.addRenderableWidget(randomModeBtn);
        currentY += rowHeight;

        // Row: Amount
        quantityModeBtn = Button.builder(
                Component.literal(selectedQuantityMode.getDisplayName()),
                btn -> cycleQuantityMode())
                .pos(leftX, currentY + 12)
                .size(halfWidth, 20)
                .build();
        this.addRenderableWidget(quantityModeBtn);

        int countWidth = selectedQuantityMode == QuantityMode.RANGE ? (halfWidth / 2 - 3) : halfWidth;
        this.countField = new EditBox(this.font, leftX + halfWidth + 10, currentY + 12, countWidth, 20, Component.literal("Count"));
        this.countField.setMaxLength(5);
        this.countField.setHint(Component.literal("1"));
        this.countField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        if (valuesPreserved) {
            this.countField.setValue(preservedCount);
        } else if (originalAction != null) {
            this.countField.setValue(String.valueOf(originalAction.getCount()));
        }
        this.countField.visible = true;
        this.addRenderableWidget(this.countField);

        this.maxCountField = new EditBox(this.font, leftX + halfWidth + 10 + countWidth + 6, currentY + 12, countWidth, 20, Component.literal("Max"));
        this.maxCountField.setMaxLength(5);
        this.maxCountField.setHint(Component.literal("max"));
        this.maxCountField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        if (valuesPreserved) {
            this.maxCountField.setValue(preservedMaxCount);
        } else if (originalAction != null && originalAction.getMaxCount() > 0) {
            this.maxCountField.setValue(String.valueOf(originalAction.getMaxCount()));
        }
        this.maxCountField.visible = selectedQuantityMode == QuantityMode.RANGE;
        this.addRenderableWidget(this.maxCountField);
        currentY += rowHeight;

        // Row: Value or Pool
        if (selectedRandomMode == RandomMode.NONE) {
            this.valueField = new EditBox(this.font, leftX, currentY + 12, FIELD_WIDTH - 30, 20, Component.literal("Value"));
            this.valueField.setMaxLength(256);
            this.valueField.setHint(Component.literal(selectedActionType == ActionType.SPAWN_MOB ? "minecraft:zombie" : "minecraft:diamond"));
            if (valuesPreserved) {
                this.valueField.setValue(preservedValue);
            } else if (originalAction != null && originalAction.getValue() != null) {
                this.valueField.setValue(originalAction.getValue());
            }
            this.valueField.visible = true;
            this.addRenderableWidget(this.valueField);

            pickValueBtn = Button.builder(Component.literal("..."), btn -> openPicker())
                    .pos(leftX + FIELD_WIDTH - 25, currentY + 12)
                    .size(25, 20)
                    .build();
            this.addRenderableWidget(pickValueBtn);
        } else {
            // Only show mob pool for SPAWN_MOB, not for GIVE_ITEM
            if (selectedActionType == ActionType.SPAWN_MOB) {
                String poolBtnText = (preservedMobPool != null && !preservedMobPool.isEmpty())
                        ? "Configure Mob Pool (" + preservedMobPool.size() + ")"
                        : "Configure Mob Pool (default)";
                configurePoolBtn = Button.builder(Component.literal(poolBtnText), btn -> openPoolEditor())
                        .pos(leftX, currentY + 12)
                        .size(FIELD_WIDTH, 20)
                        .build();
                this.addRenderableWidget(configurePoolBtn);
            }
            // For GIVE_ITEM with random mode, we use the default item pool (no custom pool editor yet)
        }
    }

    private void initCommandFields(int leftX, int startY) {
        this.valueField = new EditBox(this.font, leftX, startY + 12, FIELD_WIDTH, 20, Component.literal("Command"));
        this.valueField.setMaxLength(256);
        this.valueField.setHint(Component.literal("say Hello {player}!"));
        if (valuesPreserved) {
            this.valueField.setValue(preservedValue);
        } else if (originalAction != null && originalAction.getValue() != null) {
            this.valueField.setValue(originalAction.getValue());
        }
        this.valueField.visible = true;
        this.addRenderableWidget(this.valueField);
    }

    private void initEffectFields(int leftX, int startY, int halfWidth, int rowHeight) {
        int currentY = startY;
        boolean hasEffectPool = preservedEffectPool != null && !preservedEffectPool.isEmpty();

        // Row: Effect + Level OR "Using pool" message
        if (!hasEffectPool) {
            int effectWidth = FIELD_WIDTH - 80;
            this.valueField = new EditBox(this.font, leftX, currentY + 12, effectWidth, 20, Component.literal("Effect"));
            this.valueField.setMaxLength(256);
            this.valueField.setHint(Component.literal("minecraft:speed"));
            if (valuesPreserved) {
                this.valueField.setValue(preservedValue);
            } else if (originalAction != null && originalAction.getValue() != null) {
                this.valueField.setValue(originalAction.getValue());
            }
            this.valueField.visible = true;
            this.addRenderableWidget(this.valueField);

            Button effectPickBtn = Button.builder(Component.literal("..."), btn -> {
                preserveCurrentValues();
                this.minecraft.setScreen(new EffectPickerScreen(this, e -> preservedValue = e));
            })
                    .pos(leftX + effectWidth + 5, currentY + 12)
                    .size(25, 20)
                    .build();
            this.addRenderableWidget(effectPickBtn);

            this.effectAmplifierField = new EditBox(this.font, leftX + FIELD_WIDTH - 40, currentY + 12, 40, 20, Component.literal("Lvl"));
            this.effectAmplifierField.setMaxLength(2);
            this.effectAmplifierField.setHint(Component.literal("1"));
            this.effectAmplifierField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
            if (valuesPreserved && !preservedEffectAmplifier.isEmpty()) {
                this.effectAmplifierField.setValue(preservedEffectAmplifier);
            } else if (originalAction != null && originalAction.getEffectAmplifier() >= 0) {
                this.effectAmplifierField.setValue(String.valueOf(originalAction.getEffectAmplifier() + 1));
            }
            this.effectAmplifierField.visible = true;
            this.addRenderableWidget(this.effectAmplifierField);
        } else {
            // Just level field when using pool
            this.effectAmplifierField = new EditBox(this.font, leftX + FIELD_WIDTH - 40, currentY + 12, 40, 20, Component.literal("Lvl"));
            this.effectAmplifierField.setMaxLength(2);
            this.effectAmplifierField.setHint(Component.literal("1"));
            this.effectAmplifierField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
            if (valuesPreserved && !preservedEffectAmplifier.isEmpty()) {
                this.effectAmplifierField.setValue(preservedEffectAmplifier);
            } else if (originalAction != null && originalAction.getEffectAmplifier() >= 0) {
                this.effectAmplifierField.setValue(String.valueOf(originalAction.getEffectAmplifier() + 1));
            }
            this.effectAmplifierField.visible = true;
            this.addRenderableWidget(this.effectAmplifierField);
        }
        currentY += rowHeight;

        // Row: Duration + Pool button
        this.effectDurationField = new EditBox(this.font, leftX, currentY + 12, 60, 20, Component.literal("Duration"));
        this.effectDurationField.setMaxLength(5);
        this.effectDurationField.setHint(Component.literal("10"));
        this.effectDurationField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        if (valuesPreserved && !preservedEffectDuration.isEmpty()) {
            this.effectDurationField.setValue(preservedEffectDuration);
        } else if (originalAction != null && originalAction.getEffectDuration() > 0) {
            this.effectDurationField.setValue(String.valueOf(originalAction.getEffectDuration()));
        }
        this.effectDurationField.visible = true;
        this.addRenderableWidget(this.effectDurationField);

        String effectPoolBtnText = hasEffectPool
                ? "Random Pool (" + preservedEffectPool.size() + " effects)"
                : "Random Pool (off)";
        effectPoolBtn = Button.builder(Component.literal(effectPoolBtnText), btn -> openEffectPoolEditor())
                .pos(leftX + 75, currentY + 12)
                .size(FIELD_WIDTH - 75, 20)
                .build();
        this.addRenderableWidget(effectPoolBtn);
    }

    private void initSoundFields(int leftX, int startY, int halfWidth, int rowHeight) {
        int currentY = startY;

        // Row: Sound
        this.valueField = new EditBox(this.font, leftX, currentY + 12, FIELD_WIDTH - 30, 20, Component.literal("Sound"));
        this.valueField.setMaxLength(256);
        this.valueField.setHint(Component.literal("minecraft:entity.wolf.howl"));
        if (valuesPreserved) {
            this.valueField.setValue(preservedValue);
        } else if (originalAction != null && originalAction.getValue() != null) {
            this.valueField.setValue(originalAction.getValue());
        }
        this.valueField.visible = true;
        this.addRenderableWidget(this.valueField);

        pickValueBtn = Button.builder(Component.literal("..."), btn -> openPicker())
                .pos(leftX + FIELD_WIDTH - 25, currentY + 12)
                .size(25, 20)
                .build();
        this.addRenderableWidget(pickValueBtn);
        currentY += rowHeight;

        // Row: Volume + Pitch
        this.soundVolumeField = new EditBox(this.font, leftX, currentY + 12, halfWidth, 20, Component.literal("Volume"));
        this.soundVolumeField.setMaxLength(4);
        this.soundVolumeField.setHint(Component.literal("1.0"));
        if (valuesPreserved && !preservedSoundVolume.isEmpty()) {
            this.soundVolumeField.setValue(preservedSoundVolume);
        } else if (originalAction != null) {
            this.soundVolumeField.setValue(String.valueOf(originalAction.getSoundVolume()));
        }
        this.soundVolumeField.visible = true;
        this.addRenderableWidget(this.soundVolumeField);

        this.soundPitchField = new EditBox(this.font, leftX + halfWidth + 10, currentY + 12, halfWidth, 20, Component.literal("Pitch"));
        this.soundPitchField.setMaxLength(4);
        this.soundPitchField.setHint(Component.literal("1.0"));
        if (valuesPreserved && !preservedSoundPitch.isEmpty()) {
            this.soundPitchField.setValue(preservedSoundPitch);
        } else if (originalAction != null) {
            this.soundPitchField.setValue(String.valueOf(originalAction.getSoundPitch()));
        }
        this.soundPitchField.visible = true;
        this.addRenderableWidget(this.soundPitchField);
    }

    private void initSpecialFields(int leftX, int startY) {
        specialTypeBtn = Button.builder(
                Component.literal(selectedSpecialType.getDisplayName() + " \u25BC"),
                btn -> openSpecialTypeDropdown())
                .pos(leftX, startY + 12)
                .size(FIELD_WIDTH, 20)
                .build();
        this.addRenderableWidget(specialTypeBtn);
    }

    private boolean isCommandType() {
        return selectedActionType == ActionType.EXECUTE_COMMAND;
    }

    private boolean isSpecialType() {
        return selectedActionType == ActionType.SPECIAL;
    }

    private boolean isEffectType() {
        return selectedActionType == ActionType.APPLY_EFFECT;
    }

    private boolean isSoundType() {
        return selectedActionType == ActionType.PLAY_SOUND;
    }

    private boolean isRandomMobEnabled() {
        return selectedActionType == ActionType.SPAWN_MOB && selectedRandomMode != RandomMode.NONE;
    }

    private boolean needsValueField() {
        if (isSpecialType()) return false;
        if (isCommandType()) return true;
        if (isSoundType()) return true;
        if (isEffectType()) {
            // Don't need value field if using random effect pool
            boolean hasEffectPool = preservedEffectPool != null && !preservedEffectPool.isEmpty();
            return !hasEffectPool;
        }
        return selectedRandomMode == RandomMode.NONE;
    }

    private void openActionTypeDropdown() {
        preserveCurrentValues();
        this.minecraft.setScreen(new DropdownScreen<>(
                this,
                "Select Action Type",
                SELECTABLE_ACTION_TYPES,
                selectedActionType,
                ActionType::getDisplayName,
                type -> {
                    selectedActionType = type;
                    if (isCommandType() || isSpecialType() || isEffectType() || isSoundType()) {
                        selectedRandomMode = RandomMode.NONE;
                    }
                }
        ));
    }

    private void openSpecialTypeDropdown() {
        preserveCurrentValues();
        this.minecraft.setScreen(new DropdownScreen<>(
                this,
                "Select Special Action",
                SpecialActionType.values(),
                selectedSpecialType,
                SpecialActionType::getDisplayName,
                type -> selectedSpecialType = type
        ));
    }

    private void openRandomModeDropdown() {
        preserveCurrentValues();
        this.minecraft.setScreen(new DropdownScreen<>(
                this,
                "Select Mode",
                RandomMode.values(),
                selectedRandomMode,
                RandomMode::getDisplayName,
                mode -> selectedRandomMode = mode
        ));
    }

    private void cycleQuantityMode() {
        preserveCurrentValues();
        QuantityMode[] modes = QuantityMode.values();
        selectedQuantityMode = modes[(selectedQuantityMode.ordinal() + 1) % modes.length];
        rebuildWidgets();
    }

    private boolean isFormValid() {
        saveBlockedReason = null;
        if (nameField == null || nameField.getValue().trim().isEmpty()) {
            saveBlockedReason = "Reward name is required";
            return false;
        }
        String name = nameField.getValue().trim();
        if (!isEditing || !name.equalsIgnoreCase(originalName)) {
            if (ModConfig.get().getReward(name) != null) {
                saveBlockedReason = "A reward with this name already exists";
                return false;
            }
        }
        if (needsValueField() && (valueField == null || valueField.getValue().trim().isEmpty())) {
            saveBlockedReason = "Value is required";
            return false;
        }
        return true;
    }

    private void updateButtonStates() {
        boolean valid = isFormValid();
        if (saveButton != null) {
            saveButton.active = valid;
            saveButton.setTooltip(!valid && saveBlockedReason != null ? Tooltip.create(Component.literal(saveBlockedReason)) : null);
        }
        if (testButton != null) {
            testButton.active = valid;
        }
    }

    private void openPicker() {
        preserveCurrentValues();
        switch (selectedActionType) {
            case SPAWN_MOB -> this.minecraft.setScreen(new EntityPickerScreen(this, e -> preservedValue = e));
            case GIVE_ITEM -> this.minecraft.setScreen(new ItemPickerScreen(this, i -> preservedValue = i));
            case APPLY_EFFECT -> this.minecraft.setScreen(new EffectPickerScreen(this, e -> preservedValue = e));
            case PLAY_SOUND -> this.minecraft.setScreen(new SoundPickerScreen(this, s -> preservedValue = s));
            default -> {}
        }
    }

    private void openPoolEditor() {
        preserveCurrentValues();
        this.minecraft.setScreen(new MobPoolEditorScreen(this, preservedMobPool, p -> preservedMobPool = p));
    }

    private void openEffectPoolEditor() {
        preserveCurrentValues();
        this.minecraft.setScreen(new EffectPoolEditorScreen(this, preservedEffectPool, p -> preservedEffectPool = p));
    }

    private void preserveCurrentValues() {
        if (nameField != null) preservedName = nameField.getValue();
        if (costField != null) preservedCost = costField.getValue();
        if (cooldownField != null) preservedCooldown = cooldownField.getValue();
        if (countField != null) preservedCount = countField.getValue();
        if (maxCountField != null) preservedMaxCount = maxCountField.getValue();
        if (valueField != null) preservedValue = valueField.getValue();
        if (effectDurationField != null) preservedEffectDuration = effectDurationField.getValue();
        if (effectAmplifierField != null) preservedEffectAmplifier = effectAmplifierField.getValue();
        if (soundVolumeField != null) preservedSoundVolume = soundVolumeField.getValue();
        if (soundPitchField != null) preservedSoundPitch = soundPitchField.getValue();
        valuesPreserved = true;
    }

    private void saveReward() {
        String name = nameField.getValue().trim();
        if (name.isEmpty()) return;
        String value = valueField != null ? valueField.getValue().trim() : "";
        if (value.isEmpty() && needsValueField()) return;

        if (isSpecialType()) {
            value = selectedSpecialType.name();
        }

        int cost = 100, count = 1, maxCount = 0, cooldown = 0;
        try { if (costField != null && !costField.getValue().isEmpty()) cost = Integer.parseInt(costField.getValue()); } catch (NumberFormatException ignored) {}
        try { if (countField != null && !countField.getValue().isEmpty()) count = Integer.parseInt(countField.getValue()); } catch (NumberFormatException ignored) {}
        try { if (maxCountField != null && !maxCountField.getValue().isEmpty()) maxCount = Integer.parseInt(maxCountField.getValue()); } catch (NumberFormatException ignored) {}
        try { if (cooldownField != null && !cooldownField.getValue().isEmpty()) cooldown = Integer.parseInt(cooldownField.getValue()); } catch (NumberFormatException ignored) {}

        if (isEditing && !name.equalsIgnoreCase(originalName)) {
            ModConfig.get().removeReward(originalName);
        }

        String twitchRewardId = originalAction != null ? originalAction.getTwitchRewardId() : null;
        RewardAction action = new RewardAction(selectedActionType, value, count, twitchRewardId, cost);
        action.setQuantityMode(selectedQuantityMode);
        action.setMaxCount(maxCount);
        action.setCooldownSeconds(cooldown);
        action.setRandomMode(selectedRandomMode);

        if (isRandomMobEnabled() && preservedMobPool != null && !preservedMobPool.isEmpty()) {
            action.setMobPool(new java.util.ArrayList<>(preservedMobPool));
        }

        if (isEffectType()) {
            try {
                if (effectDurationField != null && !effectDurationField.getValue().isEmpty()) {
                    action.setEffectDuration(Integer.parseInt(effectDurationField.getValue()));
                }
            } catch (NumberFormatException ignored) {}
            try {
                if (effectAmplifierField != null && !effectAmplifierField.getValue().isEmpty()) {
                    int level = Integer.parseInt(effectAmplifierField.getValue());
                    action.setEffectAmplifier(Math.max(0, level - 1)); // Convert user level (1=I) to amplifier (0=I)
                }
            } catch (NumberFormatException ignored) {}
            if (preservedEffectPool != null && !preservedEffectPool.isEmpty()) {
                action.setEffectPool(new java.util.ArrayList<>(preservedEffectPool));
            }
        }

        if (isSoundType()) {
            try {
                if (soundVolumeField != null && !soundVolumeField.getValue().isEmpty()) {
                    action.setSoundVolume(Float.parseFloat(soundVolumeField.getValue()));
                }
            } catch (NumberFormatException ignored) {}
            try {
                if (soundPitchField != null && !soundPitchField.getValue().isEmpty()) {
                    action.setSoundPitch(Float.parseFloat(soundPitchField.getValue()));
                }
            } catch (NumberFormatException ignored) {}
        }

        ModConfig.get().setReward(name, action);
        MainConfigScreen.selectReward(name);
        onClose();
    }

    private void testReward() {
        preserveCurrentValues();
        saveReward();
        String name = nameField.getValue().trim();
        if (!name.isEmpty()) {
            RewardHandler.simulateRedemption(name, "TestUser");
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateButtonStates();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int leftX = centerX - FIELD_WIDTH / 2;
        int halfWidth = (FIELD_WIDTH - 10) / 2;

        int currentY = 30;
        int rowHeight = 45;

        graphics.drawCenteredString(this.font, this.title, centerX, 12, 0xFFFFFF);

        // Row 1: Name
        graphics.drawString(this.font, "Reward Name:", leftX, currentY, 0xFFFFFFFF);
        currentY += rowHeight;

        // Row 2: Cost + Cooldown
        graphics.drawString(this.font, "Cost:", leftX, currentY, 0xFFFFFFFF);
        graphics.drawString(this.font, "Cooldown (sec):", leftX + halfWidth + 10, currentY, 0xFFFFFFFF);
        currentY += rowHeight;

        // Row 3: Action Type
        graphics.drawString(this.font, "Action Type:", leftX, currentY, 0xFFFFFFFF);
        currentY += rowHeight;

        // Type-specific labels
        switch (selectedActionType) {
            case SPAWN_MOB, GIVE_ITEM -> {
                graphics.drawString(this.font, "Selection Mode:", leftX, currentY, 0xFFFFFFFF);
                currentY += rowHeight;
                graphics.drawString(this.font, "Amount:", leftX, currentY, 0xFFFFFFFF);
                currentY += rowHeight;
                if (selectedRandomMode == RandomMode.NONE) {
                    graphics.drawString(this.font, selectedActionType == ActionType.SPAWN_MOB ? "Mob:" : "Item:", leftX, currentY, 0xFFFFFFFF);
                }
            }
            case EXECUTE_COMMAND -> {
                graphics.drawString(this.font, "Command:", leftX, currentY, 0xFFFFFFFF);
                graphics.drawString(this.font, "Use {player}, {redeemer}, {random:min:max}", leftX, currentY + 35, 0xFF888888);
            }
            case APPLY_EFFECT -> {
                boolean hasPool = preservedEffectPool != null && !preservedEffectPool.isEmpty();
                if (hasPool) {
                    graphics.drawString(this.font, "Using " + preservedEffectPool.size() + " random effects", leftX, currentY, 0xFFFFFFFF);
                } else {
                    graphics.drawString(this.font, "Effect:", leftX, currentY, 0xFFFFFFFF);
                }
                graphics.drawString(this.font, "Lvl:", leftX + FIELD_WIDTH - 55, currentY, 0xFFFFFFFF);
                currentY += rowHeight;
                graphics.drawString(this.font, "Duration (sec):", leftX, currentY, 0xFFFFFFFF);
            }
            case PLAY_SOUND -> {
                graphics.drawString(this.font, "Sound:", leftX, currentY, 0xFFFFFFFF);
                currentY += rowHeight;
                graphics.drawString(this.font, "Volume:", leftX, currentY, 0xFFFFFFFF);
                graphics.drawString(this.font, "Pitch:", leftX + halfWidth + 10, currentY, 0xFFFFFFFF);
                graphics.drawString(this.font, "Volume 0.0-1.0, Pitch 0.5-2.0", leftX, currentY + 35, 0xFF888888);
            }
            case SPECIAL -> {
                graphics.drawString(this.font, "Special Action:", leftX, currentY, 0xFFFFFFFF);
                String desc = switch (selectedSpecialType) {
                    case SHUFFLE_INVENTORY -> "Randomizes inventory item positions";
                    case DROP_HELD_ITEM -> "Drops the held item";
                    case DROP_INVENTORY -> "Drops entire inventory";
                };
                graphics.drawString(this.font, desc, leftX, currentY + 35, 0xFF888888);
            }
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
