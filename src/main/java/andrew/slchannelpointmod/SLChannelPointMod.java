package andrew.slchannelpointmod;

import andrew.slchannelpointmod.commands.TwitchCommands;
import andrew.slchannelpointmod.config.ModConfig;
import andrew.slchannelpointmod.rewards.RewardHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SLChannelPointMod implements ModInitializer {
	public static final String MOD_ID = "slchannelpointmod";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("SLChannelPointMod initializing...");

		// Load config
		ModConfig.load();

		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			TwitchCommands.register(dispatcher);
		});

		// Set up server lifecycle events
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			RewardHandler.setServer(server);
			andrew.slchannelpointmod.twitch.TwitchEventSub.setServer(server);
			LOGGER.info("SLChannelPointMod ready! Use /twitch login to authenticate.");
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			andrew.slchannelpointmod.twitch.TwitchEventSub.disconnect();
			LOGGER.info("SLChannelPointMod shutting down.");
		});

		LOGGER.info("SLChannelPointMod initialized!");
	}
}
