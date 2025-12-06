package andrew.slchannelpointmod.rewards;

import andrew.slchannelpointmod.SLChannelPointMod;
import andrew.slchannelpointmod.config.ModConfig;
import andrew.slchannelpointmod.config.RewardAction;
import andrew.slchannelpointmod.twitch.TwitchEventSub;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

public class RewardHandler {
    private static MinecraftServer server;

    public static void setServer(MinecraftServer minecraftServer) {
        server = minecraftServer;
    }

    public static void handleRedemption(TwitchEventSub.ChannelPointRedemption redemption) {
        if (server == null) {
            SLChannelPointMod.LOGGER.warn("Server not available, cannot handle redemption");
            return;
        }

        RewardAction action = ModConfig.get().getReward(redemption.rewardTitle());
        if (action == null) {
            SLChannelPointMod.LOGGER.info("No action configured for reward: " + redemption.rewardTitle());
            return;
        }

        server.execute(() -> {
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) {
                SLChannelPointMod.LOGGER.warn("No players online to apply reward");
                return;
            }

            ServerPlayer player = players.get(0);

            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("\u00A7d[Twitch] \u00A7f" + redemption.userName() + " \u00A7eredeemed \u00A7b" + redemption.rewardTitle()),
                    false
            );

            switch (action.getType()) {
                case SPAWN_MOB -> spawnMob(player, action.getValue(), action.getCount());
                case GIVE_ITEM -> giveItem(player, action.getValue(), action.getCount());
                case EXECUTE_COMMAND -> executeCommand(action.getValue(), redemption.userName(), player);
            }
        });
    }

    private static void spawnMob(ServerPlayer player, String mobId, int count) {
        try {
            ServerLevel level = (ServerLevel) player.level();
            ResourceLocation mobLocation = ResourceLocation.parse(mobId);

            Optional<EntityType<?>> entityTypeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(mobLocation);
            if (entityTypeOpt.isEmpty()) {
                SLChannelPointMod.LOGGER.error("Unknown mob type: " + mobId);
                return;
            }

            EntityType<?> entityType = entityTypeOpt.get();

            for (int i = 0; i < count; i++) {
                double x = player.getX() + (Math.random() - 0.5) * 10;
                double z = player.getZ() + (Math.random() - 0.5) * 10;
                double y = player.getY();

                entityType.spawn(level, BlockPos.containing(x, y, z), EntitySpawnReason.COMMAND);
            }

            SLChannelPointMod.LOGGER.info("Spawned " + count + " " + mobId + " near " + player.getName().getString());
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to spawn mob: " + mobId, e);
        }
    }

    private static void giveItem(ServerPlayer player, String itemId, int count) {
        try {
            ResourceLocation itemLocation = ResourceLocation.parse(itemId);

            Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(itemLocation);
            if (itemOpt.isEmpty()) {
                SLChannelPointMod.LOGGER.error("Unknown item: " + itemId);
                return;
            }

            ItemStack stack = new ItemStack(itemOpt.get(), count);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }

            SLChannelPointMod.LOGGER.info("Gave " + count + " " + itemId + " to " + player.getName().getString());
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to give item: " + itemId, e);
        }
    }

    private static void executeCommand(String command, String redeemerName, ServerPlayer player) {
        try {
            String processedCommand = command
                    .replace("{player}", player.getName().getString())
                    .replace("{redeemer}", redeemerName);

            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(),
                    processedCommand
            );

            SLChannelPointMod.LOGGER.info("Executed command: " + processedCommand);
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to execute command: " + command, e);
        }
    }
}
