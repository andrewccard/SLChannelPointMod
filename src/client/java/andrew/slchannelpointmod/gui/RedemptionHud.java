package andrew.slchannelpointmod.gui;

import andrew.slchannelpointmod.config.ModConfig;
import andrew.slchannelpointmod.config.ModConfig.HudPosition;
import andrew.slchannelpointmod.twitch.TwitchEventSub;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RedemptionHud {
    private static final List<RedemptionEntry> entries = new ArrayList<>();
    private static final long DISPLAY_TIME_MS = 5000;  // 5 seconds
    private static final long FADE_TIME_MS = 500;      // 0.5 second fade out
    private static final int ENTRY_HEIGHT = 14;
    private static final int PADDING = 10;

    // Connection indicator state
    private static long connectedTimestamp = 0;
    private static boolean wasConnected = false;
    private static final long CONNECTED_DISPLAY_TIME_MS = 2000; // Show for 2 seconds after connecting

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

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        // Render connection indicator
        renderConnectionIndicator(graphics, mc);

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

    private static void renderConnectionIndicator(GuiGraphics graphics, Minecraft mc) {
        // Only show if user has a token configured
        if (!ModConfig.get().hasValidToken()) return;

        boolean isConnected = TwitchEventSub.isConnected();
        boolean isTestMode = TwitchEventSub.isTestMode();

        // Track when we became connected
        if (isConnected && !wasConnected) {
            connectedTimestamp = System.currentTimeMillis();
        }
        wasConnected = isConnected;

        // If connected (and not test mode), hide after the display time
        if (isConnected && !isTestMode) {
            long elapsed = System.currentTimeMillis() - connectedTimestamp;
            if (elapsed > CONNECTED_DISPLAY_TIME_MS) {
                return; // Don't render - hide the indicator
            }
        }

        String statusText;
        int statusColor;

        if (isTestMode) {
            statusText = "\u25CF Test Mode";
            statusColor = 0xFFFFAA00; // Orange
        } else if (isConnected) {
            statusText = "\u25CF Twitch Connected";
            statusColor = 0xFF55FF55; // Green
        } else {
            statusText = "\u25CF Twitch Disconnected";
            statusColor = 0xFFFF5555; // Red
        }

        // Get HUD position setting
        HudPosition position = ModConfig.get().getHudPosition();
        float scale = ModConfig.get().getHudScale();

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Place indicator in the same corner as redemptions, but at the very edge
        boolean isTop = (position == HudPosition.TOP_LEFT || position == HudPosition.TOP_RIGHT);
        boolean isLeft = (position == HudPosition.TOP_LEFT || position == HudPosition.BOTTOM_LEFT);

        int textWidth = mc.font.width(statusText);

        // Apply scale
        Matrix3x2fStack poseStack = graphics.pose();
        poseStack.pushMatrix();

        float anchorX = isLeft ? 0 : screenWidth;
        float anchorY = isTop ? 0 : screenHeight;

        poseStack.translate(anchorX, anchorY);
        poseStack.scale(scale, scale);
        poseStack.translate(-anchorX / scale, -anchorY / scale);

        int x, y;
        if (isLeft) {
            x = PADDING;
        } else {
            x = (int)(screenWidth / scale) - textWidth - PADDING;
        }

        if (isTop) {
            // At very top, above redemptions
            y = 2;
        } else {
            // At very bottom
            y = (int)(screenHeight / scale) - 12;
        }

        // Semi-transparent background
        graphics.fill(x - 2, y - 1, x + textWidth + 2, y + 9, 0x80000000);

        // Status text
        graphics.drawString(mc.font, statusText, x, y, statusColor, false);

        poseStack.popMatrix();
    }

    private record RedemptionEntry(String userName, String rewardTitle, long timestamp) {}
}
