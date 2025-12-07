package andrew.slchannelpointmod.gui;

import andrew.slchannelpointmod.config.ModConfig;
import andrew.slchannelpointmod.config.RewardAction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class RewardListScreen extends Screen {
    private final Screen parent;
    private RewardList rewardList;

    public RewardListScreen(Screen parent) {
        super(Component.literal("Manage Rewards"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Reward list widget
        this.rewardList = new RewardList(this.minecraft, this.width, this.height - 96, 32, 24);
        this.addRenderableWidget(this.rewardList);

        // Populate list
        for (Map.Entry<String, RewardAction> entry : ModConfig.get().getRewards().entrySet()) {
            this.rewardList.addEntry(new RewardEntry(entry.getKey(), entry.getValue()));
        }

        int buttonWidth = 100;
        int buttonY = this.height - 52;
        int spacing = 5;
        int totalWidth = buttonWidth * 4 + spacing * 3;
        int startX = (this.width - totalWidth) / 2;

        // Add button
        this.addRenderableWidget(Button.builder(Component.literal("Add"), btn -> {
            this.minecraft.setScreen(new RewardEditorScreen(this, null, null));
        })
                .pos(startX, buttonY)
                .size(buttonWidth, 20)
                .build());

        // Edit button
        Button editBtn = Button.builder(Component.literal("Edit"), btn -> {
            RewardEntry selected = this.rewardList.getSelected();
            if (selected != null) {
                this.minecraft.setScreen(new RewardEditorScreen(this, selected.name, selected.action));
            }
        })
                .pos(startX + buttonWidth + spacing, buttonY)
                .size(buttonWidth, 20)
                .build();
        this.addRenderableWidget(editBtn);

        // Delete button
        Button deleteBtn = Button.builder(Component.literal("Delete"), btn -> {
            RewardEntry selected = this.rewardList.getSelected();
            if (selected != null) {
                ModConfig.get().removeReward(selected.name);
                rebuildWidgets();
            }
        })
                .pos(startX + (buttonWidth + spacing) * 2, buttonY)
                .size(buttonWidth, 20)
                .build();
        this.addRenderableWidget(deleteBtn);

        // Test button
        Button testBtn = Button.builder(Component.literal("Test"), btn -> {
            RewardEntry selected = this.rewardList.getSelected();
            if (selected != null && this.minecraft.player != null) {
                this.minecraft.player.connection.sendCommand("twitch test \"" + selected.name + "\" TestUser");
            }
        })
                .pos(startX + (buttonWidth + spacing) * 3, buttonY)
                .size(buttonWidth, 20)
                .build();
        this.addRenderableWidget(testBtn);

        // Done button
        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .pos(this.width / 2 - 50, this.height - 28)
                .size(100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);

        if (ModConfig.get().getRewards().isEmpty()) {
            graphics.drawCenteredString(this.font, "No rewards configured", this.width / 2, this.height / 2, 0xFFAAAAAA);
            graphics.drawCenteredString(this.font, "Click 'Add' to create a new reward", this.width / 2, this.height / 2 + 15, 0xFF888888);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private class RewardList extends ObjectSelectionList<RewardEntry> {
        public RewardList(net.minecraft.client.Minecraft minecraft, int width, int height, int top, int itemHeight) {
            super(minecraft, width, height, top, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return this.width - 40;
        }

        @Override
        protected int scrollBarX() {
            return this.width - 10;
        }

        // Expose addEntry as public
        @Override
        public int addEntry(RewardEntry entry) {
            return super.addEntry(entry);
        }
    }

    private class RewardEntry extends ObjectSelectionList.Entry<RewardEntry> {
        private final String name;
        private final RewardAction action;

        public RewardEntry(String name, RewardAction action) {
            this.name = name;
            this.action = action;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int index, int top, boolean hovered, float delta) {
            int left = rewardList.getRowLeft();
            int width = rewardList.getRowWidth();

            // Reward name (ARGB format for MC 1.21+)
            graphics.drawString(font, name, left + 5, top + 2, 0xFFFFFFFF);

            // Action type and value
            String actionText = getActionDescription();
            graphics.drawString(font, actionText, left + 5, top + 12, 0xFFAAAAAA);

            // Cost (if set)
            if (action.getCost() > 0) {
                String costText = action.getCost() + " pts";
                int costWidth = font.width(costText);
                graphics.drawString(font, costText, left + width - costWidth - 5, top + 2, 0xFFFFAA00);
            }
        }

        private String getActionDescription() {
            return switch (action.getEffectiveType()) {
                case SPAWN_MOB -> "Spawn " + action.getCount() + "x " + action.getValue();
                case RANDOM_MOB_SAME -> "Spawn " + action.getCount() + "x Random Mob (same)";
                case RANDOM_MOB_EACH -> "Spawn " + action.getCount() + "x Random Mobs (each different)";
                case GIVE_ITEM -> "Give " + action.getCount() + "x " + action.getValue();
                case EXECUTE_COMMAND -> "Command: " + truncate(action.getValue(), 40);
            };
        }

        private String truncate(String text, int maxLength) {
            if (text.length() <= maxLength) return text;
            return text.substring(0, maxLength - 3) + "...";
        }

        @Override
        public Component getNarration() {
            return Component.literal(name);
        }

        public boolean handleClick() {
            rewardList.setSelected(this);
            return true;
        }
    }
}
