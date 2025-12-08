package andrew.slchannelpointmod.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Function;

public class DropdownScreen<T> extends Screen {
    private final Screen parent;
    private final T[] options;
    private final T currentSelection;
    private final Consumer<T> onSelect;
    private final Function<T, String> displayNameGetter;
    private final String title;

    private static final int ENTRY_HEIGHT = 24;
    private static final int ENTRY_WIDTH = 200;

    public DropdownScreen(Screen parent, String title, T[] options, T currentSelection,
                          Function<T, String> displayNameGetter, Consumer<T> onSelect) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.options = options;
        this.currentSelection = currentSelection;
        this.displayNameGetter = displayNameGetter;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 50;

        for (int i = 0; i < options.length; i++) {
            final T option = options[i];
            String displayName = displayNameGetter.apply(option);
            boolean isSelected = option == currentSelection;

            int btnY = startY + i * ENTRY_HEIGHT;
            Button btn = Button.builder(
                    Component.literal((isSelected ? "> " : "  ") + displayName),
                    b -> {
                        onSelect.accept(option);
                        this.minecraft.setScreen(parent);
                    })
                    .pos(centerX - ENTRY_WIDTH / 2, btnY)
                    .size(ENTRY_WIDTH, 20)
                    .build();
            this.addRenderableWidget(btn);
        }

        // Cancel button
        int cancelY = startY + options.length * ENTRY_HEIGHT + 10;
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onClose())
                .pos(centerX - 50, cancelY)
                .size(100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
