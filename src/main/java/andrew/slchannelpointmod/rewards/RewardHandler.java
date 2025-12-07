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
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class RewardHandler {
    private static MinecraftServer server;
    private static final Random random = new Random();

    // Default hostile mobs list
    public static final List<String> DEFAULT_HOSTILE_MOBS = List.of(
            "minecraft:zombie", "minecraft:skeleton", "minecraft:creeper", "minecraft:spider",
            "minecraft:enderman", "minecraft:witch", "minecraft:slime", "minecraft:phantom",
            "minecraft:drowned", "minecraft:husk", "minecraft:stray", "minecraft:cave_spider",
            "minecraft:silverfish", "minecraft:endermite", "minecraft:vindicator", "minecraft:pillager",
            "minecraft:ravager", "minecraft:vex", "minecraft:evoker", "minecraft:blaze",
            "minecraft:ghast", "minecraft:magma_cube", "minecraft:hoglin", "minecraft:zoglin",
            "minecraft:piglin", "minecraft:piglin_brute", "minecraft:zombified_piglin",
            "minecraft:wither_skeleton", "minecraft:guardian", "minecraft:elder_guardian",
            "minecraft:shulker", "minecraft:warden", "minecraft:breeze"
    );

    // Default passive mobs list
    public static final List<String> DEFAULT_PASSIVE_MOBS = List.of(
            "minecraft:pig", "minecraft:cow", "minecraft:sheep", "minecraft:chicken",
            "minecraft:rabbit", "minecraft:horse", "minecraft:donkey", "minecraft:mule",
            "minecraft:llama", "minecraft:cat", "minecraft:wolf", "minecraft:fox",
            "minecraft:ocelot", "minecraft:parrot", "minecraft:turtle", "minecraft:dolphin",
            "minecraft:squid", "minecraft:glow_squid", "minecraft:cod", "minecraft:salmon",
            "minecraft:tropical_fish", "minecraft:pufferfish", "minecraft:axolotl",
            "minecraft:bee", "minecraft:goat", "minecraft:frog", "minecraft:allay",
            "minecraft:camel", "minecraft:sniffer", "minecraft:armadillo", "minecraft:mooshroom",
            "minecraft:panda", "minecraft:polar_bear", "minecraft:strider"
    );

    // Callback for client-side HUD notifications (userName, rewardTitle, count)
    @FunctionalInterface
    public interface RedemptionCallback {
        void accept(String userName, String rewardTitle, int count);
    }

    private static RedemptionCallback redemptionCallback = null;

    public static void setRedemptionCallback(RedemptionCallback callback) {
        redemptionCallback = callback;
    }

    public static void setServer(MinecraftServer minecraftServer) {
        server = minecraftServer;
    }

    /**
     * Simulate a redemption for testing purposes.
     * Can be called from client GUI - will execute on server thread.
     */
    public static void simulateRedemption(String rewardName, String userName) {
        TwitchEventSub.ChannelPointRedemption redemption = new TwitchEventSub.ChannelPointRedemption(rewardName, userName, "");
        handleRedemption(redemption);
    }

    // Get mob pool for a specific reward action
    private static List<EntityType<?>> getMobPoolForAction(RewardAction action) {
        List<EntityType<?>> result = new ArrayList<>();
        List<String> mobIds;

        if (action.hasCustomMobPool()) {
            // Use per-reward custom pool
            mobIds = action.getMobPool();
        } else {
            // Default to hostile mobs if no custom pool
            mobIds = DEFAULT_HOSTILE_MOBS;
        }

        for (String mobId : mobIds) {
            try {
                ResourceLocation mobLocation = ResourceLocation.parse(mobId);
                Optional<EntityType<?>> entityTypeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(mobLocation);
                entityTypeOpt.ifPresent(result::add);
            } catch (Exception e) {
                SLChannelPointMod.LOGGER.warn("Invalid mob ID in pool: " + mobId);
            }
        }

        return result;
    }

    private static EntityType<?> getRandomMob(RewardAction action) {
        List<EntityType<?>> mobs = getMobPoolForAction(action);
        if (mobs.isEmpty()) {
            return EntityType.ZOMBIE;  // Fallback
        }
        return mobs.get(random.nextInt(mobs.size()));
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

        // Calculate actual count (random if range is set)
        int count = action.getCount();
        if (action.hasRandomCount()) {
            count = action.getCount() + random.nextInt(action.getMaxCount() - action.getCount() + 1);
        }
        final int finalCount = count;

        // Notify the client HUD about this redemption (with actual count)
        if (redemptionCallback != null) {
            redemptionCallback.accept(redemption.userName(), redemption.rewardTitle(), finalCount);
        }

        server.execute(() -> {
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) {
                SLChannelPointMod.LOGGER.warn("No players online to apply reward");
                return;
            }

            ServerPlayer player = players.get(0);

            // Only show chat message if enabled in settings
            if (ModConfig.get().isShowChatMessages()) {
                String countSuffix = finalCount > 1 ? " (x" + finalCount + ")" : "";
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("\u00A7d[Twitch] \u00A7f" + redemption.userName() + " \u00A7eredeemed \u00A7b" + redemption.rewardTitle() + countSuffix),
                        false
                );
            }

            RewardAction.ActionType effectiveType = action.getEffectiveType();
            switch (effectiveType) {
                case SPAWN_MOB -> spawnMob(player, action, finalCount, false, false);
                case RANDOM_MOB_SAME -> spawnMob(player, action, finalCount, true, true);
                case RANDOM_MOB_EACH -> spawnMob(player, action, finalCount, true, false);
                case GIVE_ITEM -> giveItem(player, action.getValue(), finalCount);
                case EXECUTE_COMMAND -> executeCommand(action.getValue(), redemption.userName(), player);
            }
        });
    }

    private static void spawnMob(ServerPlayer player, RewardAction action, int count, boolean useRandomMob, boolean sameRandomMob) {
        try {
            ServerLevel level = (ServerLevel) player.level();
            String mobId = action.getValue();

            // If using random mob and "same random" option, pick one random mob for all spawns
            EntityType<?> sharedRandomMob = null;
            if (useRandomMob && sameRandomMob) {
                sharedRandomMob = getRandomMob(action);
                SLChannelPointMod.LOGGER.info("Random mob selected for all spawns: " + BuiltInRegistries.ENTITY_TYPE.getKey(sharedRandomMob));
            }

            List<String> spawnedTypes = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                EntityType<?> entityType;

                if (useRandomMob) {
                    if (sameRandomMob) {
                        entityType = sharedRandomMob;
                    } else {
                        // Each spawn gets a different random mob
                        entityType = getRandomMob(action);
                        SLChannelPointMod.LOGGER.debug("Random mob for spawn " + i + ": " + BuiltInRegistries.ENTITY_TYPE.getKey(entityType));
                    }
                } else {
                    // Use the configured mob
                    ResourceLocation mobLocation = ResourceLocation.parse(mobId);
                    Optional<EntityType<?>> entityTypeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(mobLocation);
                    if (entityTypeOpt.isEmpty()) {
                        SLChannelPointMod.LOGGER.error("Unknown mob type: " + mobId);
                        return;
                    }
                    entityType = entityTypeOpt.get();
                }

                double x = player.getX() + (Math.random() - 0.5) * 10;
                double z = player.getZ() + (Math.random() - 0.5) * 10;
                double y = player.getY();

                entityType.spawn(level, BlockPos.containing(x, y, z), EntitySpawnReason.COMMAND);

                String typeName = BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString();
                if (!spawnedTypes.contains(typeName)) {
                    spawnedTypes.add(typeName);
                }
            }

            if (useRandomMob) {
                if (sameRandomMob) {
                    SLChannelPointMod.LOGGER.info("Spawned " + count + " random mobs (all " + spawnedTypes.get(0) + ") near " + player.getName().getString());
                } else {
                    SLChannelPointMod.LOGGER.info("Spawned " + count + " random mobs (" + spawnedTypes.size() + " different types) near " + player.getName().getString());
                }
            } else {
                SLChannelPointMod.LOGGER.info("Spawned " + count + " " + mobId + " near " + player.getName().getString());
            }
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to spawn mob", e);
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

            // Use player's command source so relative coordinates (~) work relative to player position
            server.getCommands().performPrefixedCommand(
                    player.createCommandSourceStack().withPermission(2),
                    processedCommand
            );

            SLChannelPointMod.LOGGER.info("Executed command: " + processedCommand);
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to execute command: " + command, e);
        }
    }
}
