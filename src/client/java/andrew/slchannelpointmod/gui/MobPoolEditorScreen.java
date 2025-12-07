package andrew.slchannelpointmod.gui;

import andrew.slchannelpointmod.rewards.RewardHandler;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.*;
import java.util.function.Consumer;

public class MobPoolEditorScreen extends Screen {
    private final Screen parent;
    private final Consumer<List<String>> onSave;
    private final List<String> initialPool;

    private EditBox searchField;
    private List<EntityType<?>> filteredEntities = new ArrayList<>();
    private Set<String> selectedMobs = new HashSet<>();
    private int scrollOffset = 0;

    // Grid layout constants
    private static final int CELL_WIDTH = 90;
    private static final int CELL_HEIGHT = 40;
    private static final int GRID_PADDING = 10;

    public MobPoolEditorScreen(Screen parent, List<String> currentPool, Consumer<List<String>> onSave) {
        super(Component.literal("Configure Mob Pool"));
        this.parent = parent;
        this.onSave = onSave;
        this.initialPool = currentPool;

        // Load current pool
        if (currentPool != null && !currentPool.isEmpty()) {
            selectedMobs.addAll(currentPool);
        } else {
            // Default to hostile mobs
            selectedMobs.addAll(RewardHandler.DEFAULT_HOSTILE_MOBS);
        }
    }

    @Override
    protected void init() {
        // Register mouse event handlers using Fabric API
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
        this.searchField.setHint(Component.literal("Search mobs..."));
        this.searchField.setResponder(this::updateSearch);
        this.addRenderableWidget(this.searchField);

        // Quick selection buttons - row 1
        int btnY = this.height - 76;
        int btnWidth = 70;
        int totalWidth = btnWidth * 4 + 15;
        int startX = (this.width - totalWidth) / 2;

        // All button
        this.addRenderableWidget(Button.builder(Component.literal("All"), btn -> {
            for (EntityType<?> entityType : filteredEntities) {
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
                selectedMobs.add(id.toString());
            }
        })
                .pos(startX, btnY)
                .size(btnWidth, 20)
                .build());

        // None button
        this.addRenderableWidget(Button.builder(Component.literal("None"), btn -> {
            for (EntityType<?> entityType : filteredEntities) {
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
                selectedMobs.remove(id.toString());
            }
        })
                .pos(startX + btnWidth + 5, btnY)
                .size(btnWidth, 20)
                .build());

        // Hostile button
        this.addRenderableWidget(Button.builder(Component.literal("Hostile"), btn -> {
            selectedMobs.clear();
            selectedMobs.addAll(RewardHandler.DEFAULT_HOSTILE_MOBS);
        })
                .pos(startX + (btnWidth + 5) * 2, btnY)
                .size(btnWidth, 20)
                .build());

        // Passive button
        this.addRenderableWidget(Button.builder(Component.literal("Passive"), btn -> {
            selectedMobs.clear();
            selectedMobs.addAll(RewardHandler.DEFAULT_PASSIVE_MOBS);
        })
                .pos(startX + (btnWidth + 5) * 3, btnY)
                .size(btnWidth, 20)
                .build());

        // Bottom buttons - row 2
        int bottomY = this.height - 52;
        int btnWidth2 = 90;
        int totalWidth2 = btnWidth2 * 2 + 5;
        int startX2 = (this.width - totalWidth2) / 2;

        // Both (hostile + passive) button
        this.addRenderableWidget(Button.builder(Component.literal("Both"), btn -> {
            selectedMobs.clear();
            selectedMobs.addAll(RewardHandler.DEFAULT_HOSTILE_MOBS);
            selectedMobs.addAll(RewardHandler.DEFAULT_PASSIVE_MOBS);
        })
                .pos(startX2, bottomY)
                .size(btnWidth2, 20)
                .build());

        // Reset to original (undo changes)
        this.addRenderableWidget(Button.builder(Component.literal("Undo"), btn -> {
            selectedMobs.clear();
            if (initialPool != null && !initialPool.isEmpty()) {
                selectedMobs.addAll(initialPool);
            } else {
                selectedMobs.addAll(RewardHandler.DEFAULT_HOSTILE_MOBS);
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
        // Save the selected mobs
        List<String> result = new ArrayList<>(selectedMobs);
        onSave.accept(result.isEmpty() ? null : result);
        onClose();
    }

    private void updateSearch(String query) {
        scrollOffset = 0;
        populateList(query.toLowerCase());
    }

    private void populateList(String filter) {
        filteredEntities.clear();

        for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
            MobCategory category = entityType.getCategory();
            if (category == MobCategory.MISC) continue;

            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            String name = entityType.getDescription().getString();
            String idStr = id.toString();

            if (filter.isEmpty() || name.toLowerCase().contains(filter) || idStr.contains(filter)) {
                filteredEntities.add(entityType);
            }
        }

        filteredEntities.sort(Comparator.comparing(e -> e.getDescription().getString()));
    }

    private int getGridStartY() { return 48; }
    private int getGridHeight() { return this.height - 140; }
    private int getGridWidth() { return this.width - GRID_PADDING * 2; }
    private int getColumns() { return Math.max(1, getGridWidth() / CELL_WIDTH); }
    private int getVisibleRows() { return getGridHeight() / CELL_HEIGHT; }
    private int getGridStartX() {
        int cols = getColumns();
        int totalGridWidth = cols * CELL_WIDTH;
        return (this.width - totalGridWidth) / 2;
    }

    private ItemStack getSpawnEggForEntity(EntityType<?> entityType) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof SpawnEggItem spawnEgg) {
                ItemStack stack = new ItemStack(spawnEgg);
                try {
                    if (spawnEgg.getType(stack) == entityType) {
                        return stack;
                    }
                } catch (Exception e) {
                    // Skip
                }
            }
        }
        return new ItemStack(Items.EGG);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        int gridStartX = getGridStartX();
        int gridStartY = getGridStartY();
        int cols = getColumns();
        int visibleRows = getVisibleRows();

        // Background for grid
        int gridWidth = cols * CELL_WIDTH;
        int gridHeight = visibleRows * CELL_HEIGHT;
        graphics.fill(gridStartX - 2, gridStartY - 2,
                gridStartX + gridWidth + 2,
                gridStartY + gridHeight + 2,
                0x80000000);

        int itemsPerPage = cols * visibleRows;
        int startIndex = scrollOffset * cols;
        int endIndex = Math.min(startIndex + itemsPerPage, filteredEntities.size());

        String hoveredEntityName = null;

        for (int i = startIndex; i < endIndex; i++) {
            EntityType<?> entityType = filteredEntities.get(i);
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            String displayName = entityType.getDescription().getString();
            String idStr = id.toString();

            int relIndex = i - startIndex;
            int col = relIndex % cols;
            int row = relIndex / cols;

            int cellX = gridStartX + col * CELL_WIDTH;
            int cellY = gridStartY + row * CELL_HEIGHT;

            boolean isSelected = selectedMobs.contains(idStr);
            boolean isHovered = mouseX >= cellX && mouseX < cellX + CELL_WIDTH
                    && mouseY >= cellY && mouseY < cellY + CELL_HEIGHT;

            if (isSelected) {
                graphics.fill(cellX, cellY, cellX + CELL_WIDTH - 2, cellY + CELL_HEIGHT - 2, 0x8055FF55);
            } else if (isHovered) {
                graphics.fill(cellX, cellY, cellX + CELL_WIDTH - 2, cellY + CELL_HEIGHT - 2, 0x40FFFFFF);
            }

            if (isHovered) {
                hoveredEntityName = displayName + " (" + idStr + ")";
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
            int checkY = cellY + 2;
            graphics.fill(checkX, checkY, checkX + 8, checkY + 8, 0xFF333333);
            if (isSelected) {
                graphics.fill(checkX + 1, checkY + 1, checkX + 7, checkY + 7, 0xFF55FF55);
            }

            // Spawn egg icon
            ItemStack spawnEgg = getSpawnEggForEntity(entityType);
            graphics.renderItem(spawnEgg, cellX + (CELL_WIDTH - 16) / 2 - 1, cellY + 2);

            // Name
            String truncatedName = truncateName(displayName, CELL_WIDTH - 4);
            int nameWidth = this.font.width(truncatedName);
            int nameColor = isSelected ? 0xFFFFFFFF : getCategoryColor(entityType.getCategory());
            graphics.drawString(this.font, truncatedName, cellX + (CELL_WIDTH - nameWidth) / 2 - 1, cellY + 22, nameColor);
        }

        // Hovered info
        if (hoveredEntityName != null) {
            graphics.drawCenteredString(this.font, hoveredEntityName, this.width / 2, gridStartY + gridHeight + 4, 0xFFAAAAAA);
        }

        // Count
        graphics.drawCenteredString(this.font, selectedMobs.size() + " mobs selected", this.width / 2, gridStartY + gridHeight + 16, 0xFFAAAAAA);

        // Scroll indicator
        int totalRows = (int) Math.ceil((double) filteredEntities.size() / cols);
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

    private int getCategoryColor(MobCategory category) {
        return switch (category) {
            case MONSTER -> 0xFFFF5555;
            case CREATURE -> 0xFF55FF55;
            case AMBIENT -> 0xFFAAAAAA;
            case WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE -> 0xFF55AAFF;
            case AXOLOTLS -> 0xFFFFAA00;
            case MISC -> 0xFF888888;
        };
    }

    private void handleGridClick(double mouseX, double mouseY) {
        int gridStartX = getGridStartX();
        int gridStartY = getGridStartY();
        int cols = getColumns();
        int visibleRows = getVisibleRows();
        int itemsPerPage = cols * visibleRows;
        int startIndex = scrollOffset * cols;
        int endIndex = Math.min(startIndex + itemsPerPage, filteredEntities.size());

        for (int i = startIndex; i < endIndex; i++) {
            int relIndex = i - startIndex;
            int col = relIndex % cols;
            int row = relIndex / cols;
            int cellX = gridStartX + col * CELL_WIDTH;
            int cellY = gridStartY + row * CELL_HEIGHT;

            if (mouseX >= cellX && mouseX < cellX + CELL_WIDTH
                    && mouseY >= cellY && mouseY < cellY + CELL_HEIGHT) {
                EntityType<?> entityType = filteredEntities.get(i);
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
                String idStr = id.toString();

                if (selectedMobs.contains(idStr)) {
                    selectedMobs.remove(idStr);
                } else {
                    selectedMobs.add(idStr);
                }
                return;
            }
        }
    }

    private void handleScroll(double verticalAmount) {
        int cols = getColumns();
        int visibleRows = getVisibleRows();
        int totalRows = (int) Math.ceil((double) filteredEntities.size() / cols);
        int maxScroll = Math.max(0, totalRows - visibleRows);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) verticalAmount));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
