package andrew.slchannelpointmod;

import andrew.slchannelpointmod.commands.TwitchCommands;
import andrew.slchannelpointmod.gui.MainConfigScreen;
import andrew.slchannelpointmod.gui.RedemptionHud;
import andrew.slchannelpointmod.rewards.RewardHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.platform.InputConstants;

public class SLChannelPointModClient implements ClientModInitializer {
	private static KeyMapping openConfigKey;

	@Override
	public void onInitializeClient() {
		// Register the redemption HUD
		HudRenderCallback.EVENT.register(new RedemptionHud());

		// Register callback to receive redemption notifications for the HUD
		RewardHandler.setRedemptionCallback(RedemptionHud::addRedemption);

		// Create a category for our keybindings
		KeyMapping.Category category = new KeyMapping.Category(
				ResourceLocation.fromNamespaceAndPath("slchannelpointmod", "main")
		);

		openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.slchannelpointmod.open_config",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_K,
				category
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openConfigKey.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new MainConfigScreen(null));
				}
			}

			// Check if /channelpoints command was used
			if (TwitchCommands.isOpenGuiRequested()) {
				TwitchCommands.clearOpenGuiRequest();
				if (client.screen == null) {
					client.setScreen(new MainConfigScreen(null));
				}
			}
		});
	}

	public static void openConfigScreen() {
		Minecraft client = Minecraft.getInstance();
		client.setScreen(new MainConfigScreen(null));
	}
}
