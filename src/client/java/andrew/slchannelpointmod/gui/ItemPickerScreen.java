package andrew.slchannelpointmod.gui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class ItemPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> callback;

    private EditBox searchField;
    private List<Item> filteredItems = new ArrayList<>();
    private int scrollOffset = 0;
    private String selectedItem = null;
    private long lastClickTime = 0;

    private static final int ITEMS_PER_ROW = 9;
    private static final int ITEM_SIZE = 18;
    private static final int GRID_PADDING = 4;

    public ItemPickerScreen(Screen parent, Consumer<String> callback) {
        super(Component.literal("Select Item"));
        this.parent = parent;
        this.callback = callback;
    }

    @Override
    protected void init() {
        // Register mouse click handler using Fabric API
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

        // Search field
        this.searchField = new EditBox(this.font, this.width / 2 - 100, 22, 200, 18,
                Component.literal("Search"));
        this.searchField.setHint(Component.literal("Search items..."));
        this.searchField.setResponder(this::updateSearch);
        this.addRenderableWidget(this.searchField);

        populateItems("");

        // Select button
        Button selectBtn = Button.builder(Component.literal("Select"), btn -> {
            if (selectedItem != null) {
                callback.accept(selectedItem);
                onClose();
            }
        })
                .pos(this.width / 2 - 105, this.height - 48)
                .size(100, 20)
                .build();
        this.addRenderableWidget(selectBtn);

        // Cancel button
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onClose())
                .pos(this.width / 2 + 5, this.height - 48)
                .size(100, 20)
                .build());
    }

    private void updateSearch(String query) {
        scrollOffset = 0;
        populateItems(query.toLowerCase());
    }

    private void populateItems(String filter) {
        filteredItems.clear();

        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;

            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            String name = item.getName(new ItemStack(item)).getString();
            String idStr = id.toString();

            if (filter.isEmpty() || name.toLowerCase().contains(filter) || idStr.contains(filter)) {
                filteredItems.add(item);
            }
        }

        // Sort by name
        filteredItems.sort(Comparator.comparing(i -> i.getName(new ItemStack(i)).getString()));
    }

    private int getGridStartX() {
        return (this.width - ITEMS_PER_ROW * ITEM_SIZE - (ITEMS_PER_ROW - 1) * GRID_PADDING) / 2;
    }

    private int getGridStartY() {
        return 50;
    }

    private int getRowsVisible() {
        int gridHeight = this.height - 120;
        return gridHeight / (ITEM_SIZE + GRID_PADDING);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        // Draw item grid
        int gridStartX = getGridStartX();
        int gridStartY = getGridStartY();
        int rowsVisible = getRowsVisible();

        // Background for grid area
        graphics.fill(gridStartX - 5, gridStartY - 5,
                gridStartX + ITEMS_PER_ROW * (ITEM_SIZE + GRID_PADDING),
                gridStartY + rowsVisible * (ITEM_SIZE + GRID_PADDING),
                0x80000000);

        int startIndex = scrollOffset * ITEMS_PER_ROW;
        int endIndex = Math.min(startIndex + rowsVisible * ITEMS_PER_ROW, filteredItems.size());

        String hoveredItemName = null;
        String hoveredItemId = null;

        for (int i = startIndex; i < endIndex; i++) {
            Item item = filteredItems.get(i);
            int relIndex = i - startIndex;
            int row = relIndex / ITEMS_PER_ROW;
            int col = relIndex % ITEMS_PER_ROW;

            int x = gridStartX + col * (ITEM_SIZE + GRID_PADDING);
            int y = gridStartY + row * (ITEM_SIZE + GRID_PADDING);

            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            boolean isSelected = id.toString().equals(selectedItem);
            boolean isHovered = mouseX >= x && mouseX < x + ITEM_SIZE && mouseY >= y && mouseY < y + ITEM_SIZE;

            // Selection/hover background
            if (isSelected) {
                graphics.fill(x - 1, y - 1, x + ITEM_SIZE + 1, y + ITEM_SIZE + 1, 0xFFFFFF00);
            } else if (isHovered) {
                graphics.fill(x - 1, y - 1, x + ITEM_SIZE + 1, y + ITEM_SIZE + 1, 0x80FFFFFF);
                hoveredItemName = item.getName(new ItemStack(item)).getString();
                hoveredItemId = id.toString();
            }

            // Draw item
            graphics.renderItem(new ItemStack(item), x, y);
        }

        // Show hovered item info
        if (hoveredItemName != null) {
            int infoY = this.height - 70;
            graphics.drawCenteredString(this.font, hoveredItemName, this.width / 2, infoY, 0xFFFFFF);
            graphics.drawCenteredString(this.font, hoveredItemId, this.width / 2, infoY + 12, 0x888888);
        } else if (selectedItem != null) {
            // Show selected item info
            Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(selectedItem));
            if (itemOpt.isPresent() && itemOpt.get() != Items.AIR) {
                Item item = itemOpt.get();
                int infoY = this.height - 70;
                graphics.drawCenteredString(this.font, item.getName(new ItemStack(item)).getString(), this.width / 2, infoY, 0xFFFF55);
                graphics.drawCenteredString(this.font, selectedItem, this.width / 2, infoY + 12, 0xAAAA00);
            }
        }

        // Scroll indicator
        int totalRows = (filteredItems.size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
        if (totalRows > rowsVisible) {
            String scrollText = "Row " + (scrollOffset + 1) + " of " + (totalRows - rowsVisible + 1) + " (scroll to see more)";
            graphics.drawCenteredString(this.font, scrollText, this.width / 2, gridStartY + rowsVisible * (ITEM_SIZE + GRID_PADDING) + 5, 0x888888);
        }
    }

    private void handleGridClick(double mouseX, double mouseY) {
        int gridStartX = getGridStartX();
        int gridStartY = getGridStartY();
        int rowsVisible = getRowsVisible();
        int startIndex = scrollOffset * ITEMS_PER_ROW;

        for (int row = 0; row < rowsVisible; row++) {
            for (int col = 0; col < ITEMS_PER_ROW; col++) {
                int index = startIndex + row * ITEMS_PER_ROW + col;
                if (index >= filteredItems.size()) break;

                int x = gridStartX + col * (ITEM_SIZE + GRID_PADDING);
                int y = gridStartY + row * (ITEM_SIZE + GRID_PADDING);

                if (mouseX >= x && mouseX < x + ITEM_SIZE && mouseY >= y && mouseY < y + ITEM_SIZE) {
                    Item item = filteredItems.get(index);
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                    selectedItem = id.toString();

                    // Double click to select
                    if (System.currentTimeMillis() - lastClickTime < 300) {
                        callback.accept(selectedItem);
                        onClose();
                    }
                    lastClickTime = System.currentTimeMillis();
                    return;
                }
            }
        }
    }

    private void handleScroll(double verticalAmount) {
        int rowsVisible = getRowsVisible();
        int totalRows = (filteredItems.size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
        int maxScroll = Math.max(0, totalRows - rowsVisible);

        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) verticalAmount));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
