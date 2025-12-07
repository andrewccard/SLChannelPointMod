package andrew.slchannelpointmod.gui;

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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class EntityPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> callback;

    private EditBox searchField;
    private List<EntityType<?>> filteredEntities = new ArrayList<>();
    private int scrollOffset = 0;
    private String selectedEntity = null;
    private long lastClickTime = 0;

    // Grid layout constants
    private static final int CELL_WIDTH = 90;
    private static final int CELL_HEIGHT = 40;
    private static final int GRID_PADDING = 10;

    public EntityPickerScreen(Screen parent, Consumer<String> callback) {
        super(Component.literal("Select Mob"));
        this.parent = parent;
        this.callback = callback;
    }

    @Override
    protected void init() {
        // Populate list first
        populateList("");

        // Register mouse click handler using Fabric API
        ScreenMouseEvents.afterMouseClick(this).register((screen, click, consumed) -> {
            if (click.button() == 0 && !consumed) {
                handleListClick(click.x(), click.y());
            }
            return consumed;
        });

        ScreenMouseEvents.afterMouseScroll(this).register((screen, mouseX, mouseY, horizontalAmount, verticalAmount, consumed) -> {
            if (!consumed) {
                handleScroll(verticalAmount);
            }
            return consumed;
        });

        // Search field
        this.searchField = new EditBox(this.font, this.width / 2 - 100, 22, 200, 18,
                Component.literal("Search"));
        this.searchField.setHint(Component.literal("Search mobs..."));
        this.searchField.setResponder(this::updateSearch);
        this.addRenderableWidget(this.searchField);

        // Select button
        Button selectBtn = Button.builder(Component.literal("Select"), btn -> {
            if (selectedEntity != null) {
                callback.accept(selectedEntity);
                onClose();
            }
        })
                .pos(this.width / 2 - 105, this.height - 28)
                .size(100, 20)
                .build();
        this.addRenderableWidget(selectBtn);

        // Cancel button
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onClose())
                .pos(this.width / 2 + 5, this.height - 28)
                .size(100, 20)
                .build());
    }

    private void updateSearch(String query) {
        scrollOffset = 0;
        populateList(query.toLowerCase());
    }

    private void populateList(String filter) {
        filteredEntities.clear();

        for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
            // Skip non-living entities (like arrows, items, etc.) for better UX
            MobCategory category = entityType.getCategory();
            if (category == MobCategory.MISC) continue;

            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            String name = entityType.getDescription().getString();
            String idStr = id.toString();

            if (filter.isEmpty() || name.toLowerCase().contains(filter) || idStr.contains(filter)) {
                filteredEntities.add(entityType);
            }
        }

        // Sort by name
        filteredEntities.sort(Comparator.comparing(e -> e.getDescription().getString()));
    }

    private int getGridStartY() {
        return 48;
    }

    private int getGridHeight() {
        return this.height - 90;
    }

    private int getGridWidth() {
        return this.width - GRID_PADDING * 2;
    }

    private int getColumns() {
        return Math.max(1, getGridWidth() / CELL_WIDTH);
    }

    private int getVisibleRows() {
        return getGridHeight() / CELL_HEIGHT;
    }

    private int getGridStartX() {
        int cols = getColumns();
        int totalGridWidth = cols * CELL_WIDTH;
        return (this.width - totalGridWidth) / 2;
    }

    /**
     * Gets the spawn egg item for an entity type, or a placeholder if none exists
     */
    private ItemStack getSpawnEggForEntity(EntityType<?> entityType) {
        // Try to find a spawn egg for this entity
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof SpawnEggItem spawnEgg) {
                ItemStack stack = new ItemStack(spawnEgg);
                try {
                    if (spawnEgg.getType(stack) == entityType) {
                        return stack;
                    }
                } catch (Exception e) {
                    // Skip if getType fails
                }
            }
        }
        // Return a generic egg as fallback
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

        // Background for grid area
        int gridWidth = cols * CELL_WIDTH;
        int gridHeight = visibleRows * CELL_HEIGHT;
        graphics.fill(gridStartX - 2, gridStartY - 2,
                gridStartX + gridWidth + 2,
                gridStartY + gridHeight + 2,
                0x80000000);

        // Calculate visible items
        int itemsPerPage = cols * visibleRows;
        int startIndex = scrollOffset * cols;
        int endIndex = Math.min(startIndex + itemsPerPage, filteredEntities.size());

        String hoveredEntityName = null;
        int hoveredCategoryColor = 0;

        for (int i = startIndex; i < endIndex; i++) {
            EntityType<?> entityType = filteredEntities.get(i);
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            String displayName = entityType.getDescription().getString();

            int relIndex = i - startIndex;
            int col = relIndex % cols;
            int row = relIndex / cols;

            int cellX = gridStartX + col * CELL_WIDTH;
            int cellY = gridStartY + row * CELL_HEIGHT;

            boolean isSelected = id.toString().equals(selectedEntity);
            boolean isHovered = mouseX >= cellX && mouseX < cellX + CELL_WIDTH
                    && mouseY >= cellY && mouseY < cellY + CELL_HEIGHT;

            // Selection/hover background
            if (isSelected) {
                graphics.fill(cellX, cellY, cellX + CELL_WIDTH - 2, cellY + CELL_HEIGHT - 2, 0x80FFFF00);
            } else if (isHovered) {
                graphics.fill(cellX, cellY, cellX + CELL_WIDTH - 2, cellY + CELL_HEIGHT - 2, 0x40FFFFFF);
                hoveredEntityName = displayName + " (" + id.toString() + ")";
                hoveredCategoryColor = getCategoryColor(entityType.getCategory());
            }

            // Cell border (draw 4 lines)
            int cellRight = cellX + CELL_WIDTH - 2;
            int cellBottom = cellY + CELL_HEIGHT - 2;
            graphics.fill(cellX, cellY, cellRight, cellY + 1, 0x40FFFFFF); // top
            graphics.fill(cellX, cellBottom - 1, cellRight, cellBottom, 0x40FFFFFF); // bottom
            graphics.fill(cellX, cellY, cellX + 1, cellBottom, 0x40FFFFFF); // left
            graphics.fill(cellRight - 1, cellY, cellRight, cellBottom, 0x40FFFFFF); // right

            // Render spawn egg icon (centered at top of cell)
            ItemStack spawnEgg = getSpawnEggForEntity(entityType);
            int iconX = cellX + (CELL_WIDTH - 16) / 2 - 1;
            int iconY = cellY + 2;
            graphics.renderItem(spawnEgg, iconX, iconY);

            // Entity name (truncated if needed, centered below icon)
            String truncatedName = truncateName(displayName, CELL_WIDTH - 4);
            int nameWidth = this.font.width(truncatedName);
            int nameX = cellX + (CELL_WIDTH - nameWidth) / 2 - 1;
            int nameY = cellY + 22;

            // Category-based color for the name
            int nameColor = getCategoryColor(entityType.getCategory());
            graphics.drawString(this.font, truncatedName, nameX, nameY, nameColor);
        }

        // Show hovered entity info at bottom
        if (hoveredEntityName != null) {
            int infoY = gridStartY + gridHeight + 4;
            graphics.drawCenteredString(this.font, hoveredEntityName, this.width / 2, infoY, hoveredCategoryColor);
        }

        // Scroll indicator
        int totalRows = (int) Math.ceil((double) filteredEntities.size() / cols);
        if (totalRows > visibleRows) {
            String scrollText = "Row " + (scrollOffset + 1) + "-" + Math.min(scrollOffset + visibleRows, totalRows) + " of " + totalRows + " (scroll for more)";
            graphics.drawCenteredString(this.font, scrollText, this.width / 2, this.height - 48, 0x888888);
        }

        // If no entities found
        if (filteredEntities.isEmpty()) {
            graphics.drawCenteredString(this.font, "No mobs found", this.width / 2, gridStartY + 20, 0xAAAAAA);
        }
    }

    private String truncateName(String name, int maxWidth) {
        if (this.font.width(name) <= maxWidth) {
            return name;
        }
        String ellipsis = "...";
        int ellipsisWidth = this.font.width(ellipsis);
        int availableWidth = maxWidth - ellipsisWidth;

        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (this.font.width(sb.toString() + c) > availableWidth) {
                break;
            }
            sb.append(c);
        }
        return sb.toString() + ellipsis;
    }

    private int getCategoryColor(MobCategory category) {
        // Colors must be ARGB (with alpha) in MC 1.21+, not just RGB
        return switch (category) {
            case MONSTER -> 0xFFFF5555;      // Red for hostile
            case CREATURE -> 0xFF55FF55;     // Green for passive
            case AMBIENT -> 0xFFAAAAAA;      // Gray for ambient
            case WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE -> 0xFF55AAFF; // Blue for water
            case AXOLOTLS -> 0xFFFFAA00;     // Orange for axolotls
            case MISC -> 0xFF888888;         // Dark gray for misc
        };
    }

    private void handleListClick(double mouseX, double mouseY) {
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
                selectedEntity = id.toString();

                // Double click to select
                if (System.currentTimeMillis() - lastClickTime < 300) {
                    callback.accept(selectedEntity);
                    onClose();
                }
                lastClickTime = System.currentTimeMillis();
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
