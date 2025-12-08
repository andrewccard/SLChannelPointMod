package andrew.slchannelpointmod.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EffectPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> onSelect;

    private EditBox searchBox;
    private int scrollOffset = 0;
    private String searchFilter = "";
    private List<EffectEntry> filteredEffects = new ArrayList<>();

    private static final int ENTRY_HEIGHT = 20;
    private static final int LIST_WIDTH = 250;

    public EffectPickerScreen(Screen parent, Consumer<String> onSelect) {
        super(Component.literal("Select Effect"));
        this.parent = parent;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int listX = centerX - LIST_WIDTH / 2;

        // Search box
        this.searchBox = new EditBox(this.font, centerX - 100, 30, 200, 20, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search effects..."));
        this.searchBox.setResponder(text -> {
            this.searchFilter = text.toLowerCase();
            this.scrollOffset = 0;
            updateFilteredEffects();
            rebuildList();
        });
        this.addRenderableWidget(this.searchBox);

        // Build initial effect list
        updateFilteredEffects();
        rebuildList();

        // Done button
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onClose())
                .pos(centerX - 50, this.height - 30)
                .size(100, 20)
                .build());
    }

    private void updateFilteredEffects() {
        filteredEffects.clear();

        for (MobEffect effect : BuiltInRegistries.MOB_EFFECT) {
            ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            if (id == null) continue;

            String idStr = id.toString();
            String name = getEffectDisplayName(effect);

            if (searchFilter.isEmpty() ||
                idStr.toLowerCase().contains(searchFilter) ||
                name.toLowerCase().contains(searchFilter)) {
                filteredEffects.add(new EffectEntry(idStr, name));
            }
        }

        // Sort alphabetically by display name
        filteredEffects.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
    }

    private String getEffectDisplayName(MobEffect effect) {
        try {
            return Component.translatable(effect.getDescriptionId()).getString();
        } catch (Exception e) {
            ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            return id != null ? id.getPath() : "Unknown";
        }
    }

    private void rebuildList() {
        // Remove old effect buttons (keep search and cancel)
        this.clearWidgets();
        this.addRenderableWidget(this.searchBox);

        int centerX = this.width / 2;
        int listX = centerX - LIST_WIDTH / 2;
        int listTop = 60;
        int listHeight = this.height - 100;
        int visibleEntries = listHeight / ENTRY_HEIGHT;

        int startIndex = scrollOffset;
        int endIndex = Math.min(startIndex + visibleEntries, filteredEffects.size());

        for (int i = startIndex; i < endIndex; i++) {
            EffectEntry entry = filteredEffects.get(i);
            int relIndex = i - startIndex;
            int entryY = listTop + relIndex * ENTRY_HEIGHT;

            Button btn = Button.builder(
                    Component.literal(entry.displayName),
                    b -> {
                        onSelect.accept(entry.id);
                        this.minecraft.setScreen(parent);
                    })
                    .pos(listX, entryY)
                    .size(LIST_WIDTH, 18)
                    .build();
            this.addRenderableWidget(btn);
        }

        // Cancel button
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onClose())
                .pos(centerX - 50, this.height - 30)
                .size(100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);

        // Show count
        String countText = filteredEffects.size() + " effects";
        graphics.drawCenteredString(this.font, countText, this.width / 2, this.height - 45, 0xFF888888);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int listHeight = this.height - 100;
        int visibleEntries = listHeight / ENTRY_HEIGHT;
        int maxScroll = Math.max(0, filteredEffects.size() - visibleEntries);

        if (verticalAmount > 0 && scrollOffset > 0) {
            scrollOffset--;
            rebuildList();
            return true;
        } else if (verticalAmount < 0 && scrollOffset < maxScroll) {
            scrollOffset++;
            rebuildList();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private record EffectEntry(String id, String displayName) {}
}
