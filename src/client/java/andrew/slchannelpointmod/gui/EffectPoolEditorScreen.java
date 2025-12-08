package andrew.slchannelpointmod.gui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class EffectPoolEditorScreen extends Screen {
    private final Screen parent;
    private final Consumer<List<String>> onSave;
    private final List<String> initialPool;

    private EditBox searchField;
    private List<MobEffect> filteredEffects = new ArrayList<>();
    private Set<String> selectedEffects = new HashSet<>();
    private int scrollOffset = 0;

    private static final int CELL_WIDTH = 110;
    private static final int CELL_HEIGHT = 24;
    private static final int GRID_PADDING = 10;

    // Common effect presets
    private static final List<String> HARMFUL_EFFECTS = List.of(
            "minecraft:poison", "minecraft:wither", "minecraft:slowness",
            "minecraft:mining_fatigue", "minecraft:instant_damage", "minecraft:nausea",
            "minecraft:blindness", "minecraft:hunger", "minecraft:weakness",
            "minecraft:levitation", "minecraft:bad_omen", "minecraft:darkness"
    );

    private static final List<String> BENEFICIAL_EFFECTS = List.of(
            "minecraft:speed", "minecraft:haste", "minecraft:strength",
            "minecraft:instant_health", "minecraft:jump_boost", "minecraft:regeneration",
            "minecraft:resistance", "minecraft:fire_resistance", "minecraft:water_breathing",
            "minecraft:invisibility", "minecraft:night_vision", "minecraft:health_boost",
            "minecraft:absorption", "minecraft:saturation", "minecraft:luck",
            "minecraft:slow_falling", "minecraft:conduit_power", "minecraft:dolphins_grace",
            "minecraft:hero_of_the_village"
    );

    public EffectPoolEditorScreen(Screen parent, List<String> currentPool, Consumer<List<String>> onSave) {
        super(Component.literal("Configure Effect Pool"));
        this.parent = parent;
        this.onSave = onSave;
        this.initialPool = currentPool;

        if (currentPool != null && !currentPool.isEmpty()) {
            selectedEffects.addAll(currentPool);
        }
    }

    @Override
    protected void init() {
        ScreenMouseEvents.afterMouseClick(this).register((screen, click, consumed) -> {
            if (click.button() == 0 && !consumed) {
                handleGridClick(click.x(), click.y());
            }
            return consumed;
        });

        ScreenMouseEvents.afterMouseScroll(this).register((screen, mouseX, mouseY, horizontalAmount, verticalAmount, consumed) -> {
            if (!consumed) {
                handleScroll(verticalAmount);
            }
            return consumed;
        });

        populateList("");

        // Search field
        this.searchField = new EditBox(this.font, this.width / 2 - 100, 22, 200, 18,
                Component.literal("Search"));
        this.searchField.setHint(Component.literal("Search effects..."));
        this.searchField.setResponder(this::updateSearch);
        this.addRenderableWidget(this.searchField);

        // Quick selection buttons - row 1
        int btnY = this.height - 76;
        int btnWidth = 70;
        int totalWidth = btnWidth * 4 + 15;
        int startX = (this.width - totalWidth) / 2;

        this.addRenderableWidget(Button.builder(Component.literal("All"), btn -> {
            for (MobEffect effect : filteredEffects) {
                ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
                if (id != null) selectedEffects.add(id.toString());
            }
        })
                .pos(startX, btnY)
                .size(btnWidth, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("None"), btn -> {
            for (MobEffect effect : filteredEffects) {
                ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
                if (id != null) selectedEffects.remove(id.toString());
            }
        })
                .pos(startX + btnWidth + 5, btnY)
                .size(btnWidth, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Harmful"), btn -> {
            selectedEffects.clear();
            selectedEffects.addAll(HARMFUL_EFFECTS);
        })
                .pos(startX + (btnWidth + 5) * 2, btnY)
                .size(btnWidth, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Beneficial"), btn -> {
            selectedEffects.clear();
            selectedEffects.addAll(BENEFICIAL_EFFECTS);
        })
                .pos(startX + (btnWidth + 5) * 3, btnY)
                .size(btnWidth, 20)
                .build());

        // Bottom buttons - row 2
        int bottomY = this.height - 52;
        int btnWidth2 = 90;
        int totalWidth2 = btnWidth2 * 2 + 5;
        int startX2 = (this.width - totalWidth2) / 2;

        this.addRenderableWidget(Button.builder(Component.literal("Both"), btn -> {
            selectedEffects.clear();
            selectedEffects.addAll(HARMFUL_EFFECTS);
            selectedEffects.addAll(BENEFICIAL_EFFECTS);
        })
                .pos(startX2, bottomY)
                .size(btnWidth2, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Undo"), btn -> {
            selectedEffects.clear();
            if (initialPool != null && !initialPool.isEmpty()) {
                selectedEffects.addAll(initialPool);
            }
        })
                .pos(startX2 + btnWidth2 + 5, bottomY)
                .size(btnWidth2, 20)
                .build());

        // Save/Cancel buttons
        int saveY = this.height - 28;
        this.addRenderableWidget(Button.builder(Component.literal("Save"), btn -> saveAndClose())
                .pos(this.width / 2 - 105, saveY)
                .size(100, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onClose())
                .pos(this.width / 2 + 5, saveY)
                .size(100, 20)
                .build());
    }

    private void saveAndClose() {
        List<String> result = new ArrayList<>(selectedEffects);
        onSave.accept(result.isEmpty() ? null : result);
        onClose();
    }

    private void updateSearch(String query) {
        scrollOffset = 0;
        populateList(query.toLowerCase());
    }

    private void populateList(String filter) {
        filteredEffects.clear();

        for (MobEffect effect : BuiltInRegistries.MOB_EFFECT) {
            ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            if (id == null) continue;

            String name = getEffectDisplayName(effect);
            String idStr = id.toString();

            if (filter.isEmpty() || name.toLowerCase().contains(filter) || idStr.contains(filter)) {
                filteredEffects.add(effect);
            }
        }

        filteredEffects.sort(Comparator.comparing(this::getEffectDisplayName));
    }

    private String getEffectDisplayName(MobEffect effect) {
        try {
            return Component.translatable(effect.getDescriptionId()).getString();
        } catch (Exception e) {
            ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            return id != null ? id.getPath() : "Unknown";
        }
    }

    private int getGridStartY() { return 48; }
    private int getGridHeight() { return this.height - 160; }
    private int getGridWidth() { return this.width - GRID_PADDING * 2; }
    private int getColumns() { return Math.max(1, getGridWidth() / CELL_WIDTH); }
    private int getVisibleRows() { return getGridHeight() / CELL_HEIGHT; }
    private int getGridStartX() {
        int cols = getColumns();
        int totalGridWidth = cols * CELL_WIDTH;
        return (this.width - totalGridWidth) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        int gridStartX = getGridStartX();
        int gridStartY = getGridStartY();
        int cols = getColumns();
        int visibleRows = getVisibleRows();

        int gridWidth = cols * CELL_WIDTH;
        int gridHeight = visibleRows * CELL_HEIGHT;
        graphics.fill(gridStartX - 2, gridStartY - 2,
                gridStartX + gridWidth + 2,
                gridStartY + gridHeight + 2,
                0x80000000);

        int itemsPerPage = cols * visibleRows;
        int startIndex = scrollOffset * cols;
        int endIndex = Math.min(startIndex + itemsPerPage, filteredEffects.size());

        String hoveredEffectName = null;

        for (int i = startIndex; i < endIndex; i++) {
            MobEffect effect = filteredEffects.get(i);
            ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            String displayName = getEffectDisplayName(effect);
            String idStr = id != null ? id.toString() : "";

            int relIndex = i - startIndex;
            int col = relIndex % cols;
            int row = relIndex / cols;

            int cellX = gridStartX + col * CELL_WIDTH;
            int cellY = gridStartY + row * CELL_HEIGHT;

            boolean isSelected = selectedEffects.contains(idStr);
            boolean isHovered = mouseX >= cellX && mouseX < cellX + CELL_WIDTH
                    && mouseY >= cellY && mouseY < cellY + CELL_HEIGHT;

            if (isSelected) {
                graphics.fill(cellX, cellY, cellX + CELL_WIDTH - 2, cellY + CELL_HEIGHT - 2, 0x8055FF55);
            } else if (isHovered) {
                graphics.fill(cellX, cellY, cellX + CELL_WIDTH - 2, cellY + CELL_HEIGHT - 2, 0x40FFFFFF);
            }

            if (isHovered) {
                hoveredEffectName = displayName + " (" + idStr + ")";
            }

            // Border
            int cellRight = cellX + CELL_WIDTH - 2;
            int cellBottom = cellY + CELL_HEIGHT - 2;
            int borderColor = isSelected ? 0xFF55FF55 : 0x40FFFFFF;
            graphics.fill(cellX, cellY, cellRight, cellY + 1, borderColor);
            graphics.fill(cellX, cellBottom - 1, cellRight, cellBottom, borderColor);
            graphics.fill(cellX, cellY, cellX + 1, cellBottom, borderColor);
            graphics.fill(cellRight - 1, cellY, cellRight, cellBottom, borderColor);

            // Checkbox
            int checkX = cellX + 2;
            int checkY = cellY + (CELL_HEIGHT - 8) / 2;
            graphics.fill(checkX, checkY, checkX + 8, checkY + 8, 0xFF333333);
            if (isSelected) {
                graphics.fill(checkX + 1, checkY + 1, checkX + 7, checkY + 7, 0xFF55FF55);
            }

            // Name with category color
            String truncatedName = truncateName(displayName, CELL_WIDTH - 16);
            int nameColor = isSelected ? 0xFFFFFFFF : getCategoryColor(effect.getCategory());
            graphics.drawString(this.font, truncatedName, cellX + 14, cellY + (CELL_HEIGHT - 8) / 2, nameColor);
        }

        // Hovered info
        if (hoveredEffectName != null) {
            graphics.drawCenteredString(this.font, hoveredEffectName, this.width / 2, gridStartY + gridHeight + 4, 0xFFAAAAAA);
        }

        // Count
        graphics.drawCenteredString(this.font, selectedEffects.size() + " effects selected", this.width / 2, gridStartY + gridHeight + 16, 0xFFAAAAAA);

        // Scroll indicator
        int totalRows = (int) Math.ceil((double) filteredEffects.size() / cols);
        if (totalRows > visibleRows) {
            String scrollText = "Row " + (scrollOffset + 1) + "-" + Math.min(scrollOffset + visibleRows, totalRows) + " of " + totalRows;
            graphics.drawCenteredString(this.font, scrollText, this.width / 2, gridStartY + gridHeight + 28, 0xFF888888);
        }
    }

    private String truncateName(String name, int maxWidth) {
        if (this.font.width(name) <= maxWidth) return name;
        String ellipsis = "...";
        int availableWidth = maxWidth - this.font.width(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (this.font.width(sb.toString() + c) > availableWidth) break;
            sb.append(c);
        }
        return sb + ellipsis;
    }

    private int getCategoryColor(MobEffectCategory category) {
        return switch (category) {
            case HARMFUL -> 0xFFFF5555;
            case BENEFICIAL -> 0xFF55FF55;
            case NEUTRAL -> 0xFFAAAAFF;
        };
    }

    private void handleGridClick(double mouseX, double mouseY) {
        int gridStartX = getGridStartX();
        int gridStartY = getGridStartY();
        int cols = getColumns();
        int visibleRows = getVisibleRows();
        int itemsPerPage = cols * visibleRows;
        int startIndex = scrollOffset * cols;
        int endIndex = Math.min(startIndex + itemsPerPage, filteredEffects.size());

        for (int i = startIndex; i < endIndex; i++) {
            int relIndex = i - startIndex;
            int col = relIndex % cols;
            int row = relIndex / cols;
            int cellX = gridStartX + col * CELL_WIDTH;
            int cellY = gridStartY + row * CELL_HEIGHT;

            if (mouseX >= cellX && mouseX < cellX + CELL_WIDTH
                    && mouseY >= cellY && mouseY < cellY + CELL_HEIGHT) {
                MobEffect effect = filteredEffects.get(i);
                ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
                if (id == null) return;
                String idStr = id.toString();

                if (selectedEffects.contains(idStr)) {
                    selectedEffects.remove(idStr);
                } else {
                    selectedEffects.add(idStr);
                }
                return;
            }
        }
    }

    private void handleScroll(double verticalAmount) {
        int cols = getColumns();
        int visibleRows = getVisibleRows();
        int totalRows = (int) Math.ceil((double) filteredEffects.size() / cols);
        int maxScroll = Math.max(0, totalRows - visibleRows);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) verticalAmount));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
