package andrew.slchannelpointmod.gui;

import andrew.slchannelpointmod.config.ModConfig;
import andrew.slchannelpointmod.config.RewardAction;
import andrew.slchannelpointmod.config.RewardAction.ActionType;
import andrew.slchannelpointmod.config.RewardAction.RandomMode;
import andrew.slchannelpointmod.rewards.RewardHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class TemplateSelectionScreen extends Screen {
    private final Screen parent;
    private int scrollOffset = 0;
    private static final int ENTRY_HEIGHT = 50;

    // Preset templates - empty for now, will be populated later
    private static final List<RewardTemplate> TEMPLATES = new ArrayList<>();

    static {

        // Random passive mobs (spawns 3 random passive mobs)
        TEMPLATES.add(new RewardTemplate(
                "Random Passive Mobs",
                "Spawns 3 random passive mobs near the player",
                ActionType.SPAWN_MOB,
                RandomMode.EACH_DIFFERENT,
                "",
                3, 100, 0, 0,
                new ArrayList<>(RewardHandler.DEFAULT_PASSIVE_MOBS)
        ));

        // Random hostile mobs (spawns 3 random hostile mobs)
        TEMPLATES.add(new RewardTemplate(
                "Random Hostile Mobs",
                "Spawns 3 random hostile mobs near the player",
                ActionType.SPAWN_MOB,
                RandomMode.EACH_DIFFERENT,
                "",
                3, 200, 0, 0,
                new ArrayList<>(RewardHandler.DEFAULT_HOSTILE_MOBS)
        ));

        // Wind charge with random direction
        TEMPLATES.add(new RewardTemplate(
                "Wind Charge",
                "Fires a wind charge at the player from a random direction",
                ActionType.EXECUTE_COMMAND,
                RandomMode.NONE,
                "summon minecraft:wind_charge ~ ~0.5 ~ {Motion:[{random:-0.5:0.5},{random:-0.8:-0.3},{random:-0.5:0.5}]}",
                1, 100, 5
        ));

        // Cobweb trap
        TEMPLATES.add(new RewardTemplate(
                "Cobweb Trap",
                "Traps the player in cobwebs",
                ActionType.EXECUTE_COMMAND,
                RandomMode.NONE,
                "fill ~-1 ~ ~-1 ~1 ~2 ~1 minecraft:cobweb keep",
                1, 200, 30
        ));

        // Blindness effect
        TEMPLATES.add(new RewardTemplate(
                "Blindness",
                "Blinds the player for 10 seconds",
                ActionType.EXECUTE_COMMAND,
                RandomMode.NONE,
                "effect give {player} minecraft:blindness 10 0",
                1, 150, 60
        ));

        // Chorus fruit teleport effect
        TEMPLATES.add(new RewardTemplate(
                "Chorus Teleport",
                "Randomly teleports the player nearby",
                ActionType.EXECUTE_COMMAND,
                RandomMode.NONE,
                "spreadplayers ~ ~ 1 8 false @p",
                1, 100, 30
        ));
    }

    public TemplateSelectionScreen(Screen parent) {
        super(Component.literal("Reward Templates"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int listWidth = Math.min(350, this.width - 40);
        int listX = (this.width - listWidth) / 2;
        int listTop = 40;
        int listHeight = this.height - 80;
        int visibleEntries = listHeight / ENTRY_HEIGHT;

        // Create buttons for visible templates
        int startIndex = scrollOffset;
        int endIndex = Math.min(startIndex + visibleEntries, TEMPLATES.size());

        for (int i = startIndex; i < endIndex; i++) {
            final RewardTemplate template = TEMPLATES.get(i);
            int relIndex = i - startIndex;
            int entryY = listTop + relIndex * ENTRY_HEIGHT;

            Button addBtn = Button.builder(
                    Component.literal("+ Add"),
                    btn -> {
                        addTemplate(template);
                        refresh();
                    })
                    .pos(listX + listWidth - 55, entryY + 15)
                    .size(50, 20)
                    .build();
            this.addRenderableWidget(addBtn);
        }

        // Scroll buttons
        if (TEMPLATES.size() > visibleEntries) {
            Button scrollUpBtn = Button.builder(Component.literal("\u25B2"), btn -> {
                if (scrollOffset > 0) {
                    scrollOffset--;
                    refresh();
                }
            })
                    .pos(listX + listWidth + 5, listTop)
                    .size(20, 20)
                    .build();
            scrollUpBtn.active = scrollOffset > 0;
            this.addRenderableWidget(scrollUpBtn);

            int maxScroll = Math.max(0, TEMPLATES.size() - visibleEntries);
            Button scrollDownBtn = Button.builder(Component.literal("\u25BC"), btn -> {
                int max = Math.max(0, TEMPLATES.size() - visibleEntries);
                if (scrollOffset < max) {
                    scrollOffset++;
                    refresh();
                }
            })
                    .pos(listX + listWidth + 5, listTop + listHeight - 20)
                    .size(20, 20)
                    .build();
            scrollDownBtn.active = scrollOffset < maxScroll;
            this.addRenderableWidget(scrollDownBtn);
        }

        // Done button
        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> {
            this.minecraft.setScreen(parent);
        })
                .pos(this.width / 2 - 50, this.height - 30)
                .size(100, 20)
                .build());
    }

    private void addTemplate(RewardTemplate template) {
        RewardAction action = new RewardAction();
        action.setType(template.type);
        action.setRandomMode(template.randomMode);
        action.setValue(template.value);
        action.setCount(template.count);
        action.setCost(template.cost);
        action.setCooldownSeconds(template.cooldown);
        // Templates are NOT hidden by default (only Twitch-synced rewards are auto-hidden)

        // Set random range if applicable
        if (template.maxCount > template.count) {
            action.setQuantityMode(RewardAction.QuantityMode.RANGE);
            action.setMaxCount(template.maxCount);
        }

        // Set custom mob pool if provided (only used when randomMode is not NONE for SPAWN_MOB)
        if (template.mobPool != null && !template.mobPool.isEmpty()) {
            action.setMobPool(new ArrayList<>(template.mobPool));
        }

        ModConfig.get().setReward(template.name, action);
        MainConfigScreen.selectReward(template.name);

        // Open the editor for the new reward
        this.minecraft.setScreen(new RewardEditorScreen(parent, template.name, action));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int listWidth = Math.min(350, this.width - 40);
        int listX = (this.width - listWidth) / 2;
        int listTop = 40;
        int listHeight = this.height - 80;
        int visibleEntries = listHeight / ENTRY_HEIGHT;

        // Draw list background BEFORE super.render so buttons appear on top
        graphics.fill(listX - 2, listTop - 2, listX + listWidth + 2, listTop + listHeight + 2, 0x80000000);

        // Render entry backgrounds
        int startIndex = scrollOffset;
        int endIndex = Math.min(startIndex + visibleEntries, TEMPLATES.size());

        for (int i = startIndex; i < endIndex; i++) {
            int relIndex = i - startIndex;
            int entryY = listTop + relIndex * ENTRY_HEIGHT;

            // Entry background (alternating)
            if (i % 2 == 0) {
                graphics.fill(listX, entryY, listX + listWidth, entryY + ENTRY_HEIGHT - 2, 0x20FFFFFF);
            }
        }

        // Call super.render to draw buttons on top of backgrounds
        super.render(graphics, mouseX, mouseY, delta);

        // Title
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFFFF);

        // Empty state message
        if (TEMPLATES.isEmpty()) {
            graphics.drawCenteredString(this.font, "No templates available yet", this.width / 2, listTop + 20, 0xFFAAAAAA);
            graphics.drawCenteredString(this.font, "Templates will be added in a future update", this.width / 2, listTop + 35, 0xFF888888);
            return;
        }

        // Render template text (on top of everything)
        for (int i = startIndex; i < endIndex; i++) {
            RewardTemplate template = TEMPLATES.get(i);
            int relIndex = i - startIndex;
            int entryY = listTop + relIndex * ENTRY_HEIGHT;

            // Template name
            graphics.drawString(this.font, template.name, listX + 5, entryY + 5, 0xFFFFFFFF);

            // Description
            graphics.drawString(this.font, template.description, listX + 5, entryY + 17, 0xFFAAAAAA);

            // Type and cost info
            String infoText = getTypeLabel(template.type, template.randomMode) + " | " + template.cost + " pts";
            if (template.cooldown > 0) {
                infoText += " | " + template.cooldown + "s cd";
            }
            graphics.drawString(this.font, infoText, listX + 5, entryY + 29, 0xFF888888);
        }

        // Scroll indicator
        if (TEMPLATES.size() > visibleEntries) {
            String scrollText = (startIndex + 1) + "-" + endIndex + "/" + TEMPLATES.size();
            int scrollTextX = listX + listWidth + 8;
            int scrollTextY = listTop + listHeight / 2 - 4;
            graphics.drawString(this.font, scrollText, scrollTextX, scrollTextY, 0xFF888888);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int listHeight = this.height - 80;
        int visibleEntries = listHeight / ENTRY_HEIGHT;
        int maxScroll = Math.max(0, TEMPLATES.size() - visibleEntries);

        if (verticalAmount > 0 && scrollOffset > 0) {
            scrollOffset--;
            refresh();
            return true;
        } else if (verticalAmount < 0 && scrollOffset < maxScroll) {
            scrollOffset++;
            refresh();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private String getTypeLabel(ActionType type, RandomMode randomMode) {
        return switch (type) {
            case SPAWN_MOB -> randomMode != RandomMode.NONE ? "Random Mob" : "Mob";
            case GIVE_ITEM -> randomMode != RandomMode.NONE ? "Random Item" : "Item";
            case EXECUTE_COMMAND -> "Command";
            case APPLY_EFFECT -> "Effect";
            case PLAY_SOUND -> "Sound";
            case SPECIAL -> "Special";
        };
    }

    private void refresh() {
        this.clearWidgets();
        this.init();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    // Template data class
    private static class RewardTemplate {
        final String name;
        final String description;
        final ActionType type;
        final RandomMode randomMode;
        final String value;
        final int count;
        final int cost;
        final int cooldown;
        final int maxCount;
        final List<String> mobPool;

        RewardTemplate(String name, String description, ActionType type, RandomMode randomMode, String value, int count, int cost, int cooldown) {
            this(name, description, type, randomMode, value, count, cost, cooldown, 0, null);
        }

        RewardTemplate(String name, String description, ActionType type, RandomMode randomMode, String value, int count, int cost, int cooldown, int maxCount) {
            this(name, description, type, randomMode, value, count, cost, cooldown, maxCount, null);
        }

        RewardTemplate(String name, String description, ActionType type, RandomMode randomMode, String value, int count, int cost, int cooldown, int maxCount, List<String> mobPool) {
            this.name = name;
            this.description = description;
            this.type = type;
            this.randomMode = randomMode;
            this.value = value;
            this.count = count;
            this.cost = cost;
            this.cooldown = cooldown;
            this.maxCount = maxCount;
            this.mobPool = mobPool;
        }
    }
}
