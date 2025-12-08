package andrew.slchannelpointmod.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfirmationScreen extends Screen {
    private final Screen parent;
    private final String message;
    private final String subMessage;
    private final Runnable onConfirm;

    public ConfirmationScreen(Screen parent, String title, String message, String subMessage, Runnable onConfirm) {
        super(Component.literal(title));
        this.parent = parent;
        this.message = message;
        this.subMessage = subMessage;
        this.onConfirm = onConfirm;
    }

    public ConfirmationScreen(Screen parent, String title, String message, Runnable onConfirm) {
        this(parent, title, message, null, onConfirm);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Confirm button
        this.addRenderableWidget(Button.builder(Component.literal("Confirm"), btn -> {
            onConfirm.run();
            this.minecraft.setScreen(parent);
        })
                .pos(centerX - 105, centerY + 20)
                .size(100, 20)
                .build());

        // Cancel button
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> {
            this.minecraft.setScreen(parent);
        })
                .pos(centerX + 5, centerY + 20)
                .size(100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Title
        graphics.drawCenteredString(this.font, this.title, centerX, centerY - 40, 0xFFFFFFFF);

        // Message
        graphics.drawCenteredString(this.font, this.message, centerX, centerY - 20, 0xFFAAAAAA);

        // Sub-message (if provided)
        if (subMessage != null && !subMessage.isEmpty()) {
            graphics.drawCenteredString(this.font, this.subMessage, centerX, centerY - 5, 0xFFFF6666);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
