package andrew.slchannelpointmod.gui;

import andrew.slchannelpointmod.config.ModConfig;
import andrew.slchannelpointmod.config.ModConfig.HudPosition;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RedemptionHud implements HudRenderCallback {
    private static final List<RedemptionEntry> entries = new ArrayList<>();
    private static final long DISPLAY_TIME_MS = 5000;  // 5 seconds
    private static final long FADE_TIME_MS = 500;      // 0.5 second fade out
    private static final int ENTRY_HEIGHT = 14;
    private static final int PADDING = 10;

    public static void addRedemption(String userName, String rewardTitle, int count) {
        // Schedule on main client thread to be safe
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                synchronized (entries) {
                    // Add count suffix if more than 1
                    String displayTitle = count > 1 ? rewardTitle + " (x" + count + ")" : rewardTitle;

                    // Add new entry at the beginning
                    entries.add(0, new RedemptionEntry(userName, displayTitle, System.currentTimeMillis()));

                    // Remove oldest if we exceed max (get from config)
                    int maxEntries = ModConfig.get().getHudMaxEntries();
                    while (entries.size() > maxEntries) {
                        entries.remove(entries.size() - 1);
                    }
                }
            });
        }
    }

    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        synchronized (entries) {
            if (entries.isEmpty()) return;

            long currentTime = System.currentTimeMillis();

            // Remove expired entries
            Iterator<RedemptionEntry> iter = entries.iterator();
            while (iter.hasNext()) {
                RedemptionEntry entry = iter.next();
                if (currentTime - entry.timestamp > DISPLAY_TIME_MS + FADE_TIME_MS) {
                    iter.remove();
                }
            }

            if (entries.isEmpty()) return;

            // Get settings
            HudPosition position = ModConfig.get().getHudPosition();
            int maxEntries = ModConfig.get().getHudMaxEntries();
            float scale = ModConfig.get().getHudScale();

            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();

            // Calculate starting Y position based on position setting
            boolean isTop = (position == HudPosition.TOP_LEFT || position == HudPosition.TOP_RIGHT);
            boolean isLeft = (position == HudPosition.TOP_LEFT || position == HudPosition.BOTTOM_LEFT);

            // Apply scale using Matrix3x2fStack
            Matrix3x2fStack poseStack = graphics.pose();
            poseStack.pushMatrix();

            // Determine the anchor point for scaling
            float anchorX = isLeft ? 0 : screenWidth;
            float anchorY = isTop ? 0 : screenHeight;

            // Translate to anchor, scale, translate back
            poseStack.translate(anchorX, anchorY);
            poseStack.scale(scale, scale);
            poseStack.translate(-anchorX / scale, -anchorY / scale);

            int scaledEntryHeight = ENTRY_HEIGHT;
            int y;
            if (isTop) {
                y = PADDING;
            } else {
                // For bottom positions, start from bottom and work up
                int totalHeight = Math.min(entries.size(), maxEntries) * scaledEntryHeight;
                y = (int)(screenHeight / scale) - PADDING - totalHeight + scaledEntryHeight - 10;
            }

            int entryCount = 0;
            for (RedemptionEntry entry : entries) {
                if (entryCount >= maxEntries) break;

                long elapsed = currentTime - entry.timestamp;

                // Calculate alpha for fade effect
                int alpha = 255;
                if (elapsed > DISPLAY_TIME_MS) {
                    // Fading out
                    float fadeProgress = (elapsed - DISPLAY_TIME_MS) / (float) FADE_TIME_MS;
                    alpha = (int) (255 * (1 - fadeProgress));
                }
                alpha = Math.max(0, Math.min(255, alpha));

                if (alpha > 0) {
                    String text = "\u00A7d[Twitch] \u00A7f" + entry.userName + " \u00A7eredeemed \u00A7b" + entry.rewardTitle;
                    int textWidth = mc.font.width(text);

                    // Calculate X position based on position setting
                    int x;
                    if (isLeft) {
                        x = PADDING;
                    } else {
                        x = (int)(screenWidth / scale) - textWidth - PADDING;
                    }

                    // Background with alpha
                    int bgAlpha = (int) (alpha * 0.6);
                    int bgColor = (bgAlpha << 24) | 0x000000;
                    graphics.fill(x - 4, y - 2, x + textWidth + 4, y + 10, bgColor);

                    // Text with alpha
                    int textAlpha = alpha << 24;
                    graphics.drawString(mc.font, text, x, y, 0xFFFFFF | textAlpha, false);

                    y += scaledEntryHeight;
                    entryCount++;
                }
            }

            poseStack.popMatrix();
        }
    }

    private record RedemptionEntry(String userName, String rewardTitle, long timestamp) {}
}
