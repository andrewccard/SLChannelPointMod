package andrew.slchannelpointmod.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SoundPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> onSelect;

    private EditBox searchBox;
    private int scrollOffset = 0;
    private String searchFilter = "";
    private List<SoundEntry> filteredSounds = new ArrayList<>();

    private static final int ENTRY_HEIGHT = 20;
    private static final int LIST_WIDTH = 300;

    // Common/popular sounds for quick access
    private static final String[] POPULAR_SOUNDS = {
            "minecraft:entity.creeper.primed",
            "minecraft:entity.wither.spawn",
            "minecraft:entity.ender_dragon.growl",
            "minecraft:entity.ghast.scream",
            "minecraft:entity.lightning_bolt.thunder",
            "minecraft:entity.wolf.howl",
            "minecraft:entity.cat.hiss",
            "minecraft:entity.enderman.scream",
            "minecraft:entity.player.levelup",
            "minecraft:ui.toast.challenge_complete",
            "minecraft:block.anvil.land",
            "minecraft:block.bell.use",
            "minecraft:entity.villager.no",
            "minecraft:entity.villager.yes",
            "minecraft:entity.experience_orb.pickup"
    };

    public SoundPickerScreen(Screen parent, Consumer<String> onSelect) {
        super(Component.literal("Select Sound"));
        this.parent = parent;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        // Search box
        this.searchBox = new EditBox(this.font, centerX - 100, 30, 200, 20, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search sounds..."));
        this.searchBox.setResponder(text -> {
            this.searchFilter = text.toLowerCase();
            this.scrollOffset = 0;
            updateFilteredSounds();
            rebuildList();
        });
        this.addRenderableWidget(this.searchBox);

        // Build initial sound list
        updateFilteredSounds();
        rebuildList();
    }

    private void updateFilteredSounds() {
        filteredSounds.clear();

        // If search is empty, show popular sounds first
        if (searchFilter.isEmpty()) {
            for (String soundId : POPULAR_SOUNDS) {
                ResourceLocation loc = ResourceLocation.parse(soundId);
                var soundOpt = BuiltInRegistries.SOUND_EVENT.getOptional(loc);
                if (soundOpt.isPresent()) {
                    filteredSounds.add(new SoundEntry(soundId, formatSoundName(soundId), true));
                }
            }
        }

        // Add all matching sounds
        for (SoundEvent sound : BuiltInRegistries.SOUND_EVENT) {
            ResourceLocation id = BuiltInRegistries.SOUND_EVENT.getKey(sound);
            if (id == null) continue;

            String idStr = id.toString();

            // Skip if already in popular list (when no filter)
            if (searchFilter.isEmpty()) {
                boolean isPopular = false;
                for (String pop : POPULAR_SOUNDS) {
                    if (pop.equals(idStr)) {
                        isPopular = true;
                        break;
                    }
                }
                if (isPopular) continue;
            }

            if (searchFilter.isEmpty() || idStr.toLowerCase().contains(searchFilter)) {
                filteredSounds.add(new SoundEntry(idStr, formatSoundName(idStr), false));
            }
        }

        // Sort non-popular alphabetically
        if (!searchFilter.isEmpty()) {
            filteredSounds.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
        }
    }

    private String formatSoundName(String soundId) {
        // Convert minecraft:entity.creeper.primed to "Entity Creeper Primed"
        String path = soundId.contains(":") ? soundId.split(":")[1] : soundId;
        String[] parts = path.split("[._]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private void rebuildList() {
        this.clearWidgets();
        this.addRenderableWidget(this.searchBox);

        int centerX = this.width / 2;
        int listX = centerX - LIST_WIDTH / 2;
        int listTop = 60;
        int listHeight = this.height - 100;
        int visibleEntries = listHeight / ENTRY_HEIGHT;

        int startIndex = scrollOffset;
        int endIndex = Math.min(startIndex + visibleEntries, filteredSounds.size());

        int previewBtnWidth = 25;
        int selectBtnWidth = LIST_WIDTH - previewBtnWidth - 2;

        for (int i = startIndex; i < endIndex; i++) {
            SoundEntry entry = filteredSounds.get(i);
            int relIndex = i - startIndex;
            int entryY = listTop + relIndex * ENTRY_HEIGHT;

            String label = entry.isPopular ? "\u2605 " + entry.displayName : entry.displayName;

            // Preview button
            Button previewBtn = Button.builder(
                    Component.literal("\u25B6"),
                    b -> playPreview(entry.id))
                    .pos(listX, entryY)
                    .size(previewBtnWidth, 18)
                    .build();
            this.addRenderableWidget(previewBtn);

            // Select button
            Button btn = Button.builder(
                    Component.literal(label),
                    b -> {
                        onSelect.accept(entry.id);
                        this.minecraft.setScreen(parent);
                    })
                    .pos(listX + previewBtnWidth + 2, entryY)
                    .size(selectBtnWidth, 18)
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
        String countText = filteredSounds.size() + " sounds" + (searchFilter.isEmpty() ? " (\u2605 = popular)" : "");
        graphics.drawCenteredString(this.font, countText, this.width / 2, this.height - 45, 0xFF888888);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int listHeight = this.height - 100;
        int visibleEntries = listHeight / ENTRY_HEIGHT;
        int maxScroll = Math.max(0, filteredSounds.size() - visibleEntries);

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

    private void playPreview(String soundId) {
        try {
            ResourceLocation loc = ResourceLocation.parse(soundId);
            var soundOpt = BuiltInRegistries.SOUND_EVENT.getOptional(loc);
            if (soundOpt.isPresent()) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(soundOpt.get(), 1.0f, 1.0f));
            }
        } catch (Exception e) {
            // Ignore invalid sounds
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private record SoundEntry(String id, String displayName, boolean isPopular) {}
}
