package andrew.slchannelpointmod.rewards;

import andrew.slchannelpointmod.SLChannelPointMod;
import andrew.slchannelpointmod.config.ModConfig;
import andrew.slchannelpointmod.config.RewardAction;
import andrew.slchannelpointmod.twitch.TwitchEventSub;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        // Increment redemption count and save
        action.incrementRedemptionCount();
        ModConfig.get().setReward(redemption.rewardTitle(), action);

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

            RewardAction.ActionType type = action.getType();
            RewardAction.RandomMode randomMode = action.getRandomMode();

            switch (type) {
                case SPAWN_MOB -> {
                    boolean useRandom = randomMode != RewardAction.RandomMode.NONE;
                    boolean allSame = randomMode != RewardAction.RandomMode.EACH_DIFFERENT;
                    spawnMob(player, action, finalCount, useRandom, allSame);
                }
                case GIVE_ITEM -> {
                    if (randomMode == RewardAction.RandomMode.NONE) {
                        giveItem(player, action.getValue(), finalCount);
                    } else {
                        boolean allSame = randomMode == RewardAction.RandomMode.ALL_SAME;
                        giveRandomItem(player, action, finalCount, allSame);
                    }
                }
                case EXECUTE_COMMAND -> executeCommand(action.getValue(), redemption.userName(), player);
                case APPLY_EFFECT -> applyEffect(player, action);
                case PLAY_SOUND -> playSound(player, action);
                case SPECIAL -> executeSpecialAction(player, action);
            }
        });
    }

    private static void spawnMob(ServerPlayer player, RewardAction action, int count, boolean useRandomMob, boolean sameRandomMob) {
        try {
            String mobId = action.getValue();

            // If using random mob and "same random" option, pick one random mob for all spawns
            EntityType<?> sharedRandomMobType = null;
            if (useRandomMob && sameRandomMob) {
                sharedRandomMobType = getRandomMob(action);
                SLChannelPointMod.LOGGER.info("Random mob selected for all spawns: " + BuiltInRegistries.ENTITY_TYPE.getKey(sharedRandomMobType));
            }

            List<String> spawnedTypes = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                EntityType<?> entityType;

                if (useRandomMob) {
                    if (sameRandomMob) {
                        entityType = sharedRandomMobType;
                    } else {
                        // Each spawn gets a different random mob
                        entityType = getRandomMob(action);
                    }
                } else {
                    // Validate the configured mob exists
                    ResourceLocation mobLocation = ResourceLocation.parse(mobId);
                    Optional<EntityType<?>> entityTypeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(mobLocation);
                    if (entityTypeOpt.isEmpty()) {
                        SLChannelPointMod.LOGGER.error("Unknown mob type: " + mobId);
                        return;
                    }
                    entityType = entityTypeOpt.get();
                }

                // Calculate random offset from player position
                double offsetX = (random.nextDouble() - 0.5) * 10;
                double offsetZ = (random.nextDouble() - 0.5) * 10;
                double spawnX = player.getX() + offsetX;
                double spawnY = player.getY();
                double spawnZ = player.getZ() + offsetZ;

                // Get the entity type ID for the summon command
                String entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString();

                // Use summon command with DeathLootTable to prevent drops
                String command = String.format(
                        "summon %s %.2f %.2f %.2f {DeathLootTable:\"minecraft:empty\"}",
                        entityTypeId, spawnX, spawnY, spawnZ
                );

                server.getCommands().performPrefixedCommand(
                        player.createCommandSourceStack().withPermission(2).withSuppressedOutput(),
                        command
                );

                if (!spawnedTypes.contains(entityTypeId)) {
                    spawnedTypes.add(entityTypeId);
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

    private static void giveRandomItem(ServerPlayer player, RewardAction action, int count, boolean sameItem) {
        try {
            // Get all items in the game
            List<Item> allItems = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                // Skip air
                if (item == Items.AIR) continue;
                allItems.add(item);
            }

            if (allItems.isEmpty()) {
                SLChannelPointMod.LOGGER.error("No items available");
                return;
            }

            List<String> givenItems = new ArrayList<>();

            if (sameItem) {
                // Pick one random item and give 'count' of it
                Item randomItem = allItems.get(random.nextInt(allItems.size()));
                String randomItemId = BuiltInRegistries.ITEM.getKey(randomItem).toString();
                giveItem(player, randomItemId, count);
                givenItems.add(randomItemId);
            } else {
                // Pick 'count' different random items (each quantity 1, no duplicates)
                List<Item> availableItems = new ArrayList<>(allItems);
                int itemsToGive = Math.min(count, availableItems.size());

                for (int i = 0; i < itemsToGive; i++) {
                    int randomIndex = random.nextInt(availableItems.size());
                    Item randomItem = availableItems.remove(randomIndex);
                    String randomItemId = BuiltInRegistries.ITEM.getKey(randomItem).toString();
                    giveItem(player, randomItemId, 1);
                    givenItems.add(randomItemId);
                }
            }

            if (sameItem) {
                SLChannelPointMod.LOGGER.info("Gave " + count + " random items (all " + givenItems.get(0) + ") to " + player.getName().getString());
            } else {
                SLChannelPointMod.LOGGER.info("Gave " + givenItems.size() + " different random items to " + player.getName().getString());
            }
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to give random item", e);
        }
    }

    private static void executeCommand(String command, String redeemerName, ServerPlayer player) {
        try {
            String processedCommand = command
                    .replace("{player}", player.getName().getString())
                    .replace("{redeemer}", redeemerName);

            // Process {random:min:max} placeholders - generates random float between min and max
            processedCommand = processRandomPlaceholders(processedCommand);

            // Use player's command source so relative coordinates (~) work relative to player position
            // withSuppressedOutput() prevents "Applied effect" and similar feedback messages
            server.getCommands().performPrefixedCommand(
                    player.createCommandSourceStack().withPermission(2).withSuppressedOutput(),
                    processedCommand
            );

            SLChannelPointMod.LOGGER.info("Executed command: " + processedCommand);
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to execute command: " + command, e);
        }
    }

    private static String processRandomPlaceholders(String command) {
        // Match {random:min:max} pattern, e.g. {random:-1.0:1.0}
        Pattern pattern = Pattern.compile("\\{random:(-?[\\d.]+):(-?[\\d.]+)\\}");
        Matcher matcher = pattern.matcher(command);

        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            try {
                double min = Double.parseDouble(matcher.group(1));
                double max = Double.parseDouble(matcher.group(2));
                double randomValue = min + (random.nextDouble() * (max - min));
                // Format to 2 decimal places
                String replacement = String.format("%.2f", randomValue);
                matcher.appendReplacement(result, replacement);
            } catch (NumberFormatException e) {
                // If parsing fails, leave the placeholder as-is
                matcher.appendReplacement(result, matcher.group(0));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static void applyEffect(ServerPlayer player, RewardAction action) {
        try {
            String effectId;

            // Check if using random effect pool
            if (action.isRandomEffectEnabled()) {
                List<String> effectPool = action.getEffectPool();
                if (effectPool == null || effectPool.isEmpty()) {
                    SLChannelPointMod.LOGGER.warn("Random effect enabled but pool is empty");
                    return;
                }
                effectId = effectPool.get(random.nextInt(effectPool.size()));
            } else {
                effectId = action.getValue();
                if (effectId == null || effectId.isEmpty()) {
                    SLChannelPointMod.LOGGER.warn("No effect specified for apply effect action");
                    return;
                }
            }

            ResourceLocation effectLocation = ResourceLocation.parse(effectId);
            var effectOpt = BuiltInRegistries.MOB_EFFECT.getOptional(effectLocation);

            if (effectOpt.isEmpty()) {
                SLChannelPointMod.LOGGER.error("Unknown effect: " + effectId);
                return;
            }

            var effect = effectOpt.get();

            int durationSeconds = action.getEffectDuration();
            int durationTicks = durationSeconds * 20; // Convert seconds to ticks

            int amplifier = action.getEffectAmplifier();

            // Create effect instance with holder
            var effectHolder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
            net.minecraft.world.effect.MobEffectInstance effectInstance =
                new net.minecraft.world.effect.MobEffectInstance(effectHolder, durationTicks, amplifier);

            player.addEffect(effectInstance);
            SLChannelPointMod.LOGGER.info("Applied effect " + effectId + " (duration: " + durationSeconds + "s, amplifier: " + amplifier + ") to " + player.getName().getString());
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to apply effect", e);
        }
    }

    private static void playSound(ServerPlayer player, RewardAction action) {
        try {
            String soundId = action.getValue();
            if (soundId == null || soundId.isEmpty()) {
                SLChannelPointMod.LOGGER.warn("No sound specified for play sound action");
                return;
            }

            ResourceLocation soundLocation = ResourceLocation.parse(soundId);
            var soundOpt = BuiltInRegistries.SOUND_EVENT.getOptional(soundLocation);

            if (soundOpt.isEmpty()) {
                SLChannelPointMod.LOGGER.error("Unknown sound: " + soundId);
                return;
            }

            var sound = soundOpt.get();
            float volume = action.getSoundVolume();
            float pitch = action.getSoundPitch();

            // Play sound at player location
            player.level().playSound(
                null, // null = play to all nearby players
                player.getX(),
                player.getY(),
                player.getZ(),
                sound,
                net.minecraft.sounds.SoundSource.MASTER,
                volume,
                pitch
            );
            SLChannelPointMod.LOGGER.info("Played sound " + soundId + " (volume: " + volume + ", pitch: " + pitch + ") at " + player.getName().getString());
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to play sound", e);
        }
    }

    private static void executeSpecialAction(ServerPlayer player, RewardAction action) {
        String specialType = action.getValue();
        if (specialType == null || specialType.isEmpty()) {
            specialType = "SHUFFLE_INVENTORY";
        }

        try {
            RewardAction.SpecialActionType type = RewardAction.SpecialActionType.valueOf(specialType);
            switch (type) {
                case SHUFFLE_INVENTORY -> shuffleInventory(player);
                case DROP_HELD_ITEM -> dropHeldItem(player);
                case DROP_INVENTORY -> dropInventory(player);
            }
        } catch (IllegalArgumentException e) {
            SLChannelPointMod.LOGGER.error("Unknown special action type: " + specialType);
        }
    }

    private static void shuffleInventory(ServerPlayer player) {
        try {
            net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
            List<ItemStack> allItems = new ArrayList<>();

            // Collect all items from main inventory (slots 0-35)
            // Inventory slots: 0-8 = hotbar, 9-35 = main inventory
            for (int i = 0; i < 36; i++) {
                allItems.add(inventory.getItem(i).copy());
            }

            // Shuffle the main inventory items
            java.util.Collections.shuffle(allItems, random);

            // Put items back in shuffled order
            for (int i = 0; i < 36; i++) {
                inventory.setItem(i, allItems.get(i));
            }

            SLChannelPointMod.LOGGER.info("Shuffled inventory for " + player.getName().getString());
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to shuffle inventory", e);
        }
    }

    private static void dropHeldItem(ServerPlayer player) {
        try {
            ItemStack heldItem = player.getMainHandItem();
            if (!heldItem.isEmpty()) {
                dropItemWithRandomDirection(player, heldItem.copy());
                heldItem.setCount(0);
                SLChannelPointMod.LOGGER.info("Dropped held item for " + player.getName().getString());
            }
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to drop held item", e);
        }
    }

    private static void dropInventory(ServerPlayer player) {
        try {
            net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
            int droppedCount = 0;

            // Drop all items from main inventory (slots 0-35)
            for (int i = 0; i < 36; i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty()) {
                    dropItemWithRandomDirection(player, stack.copy());
                    inventory.setItem(i, ItemStack.EMPTY);
                    droppedCount++;
                }
            }

            // Drop armor (slots 36-39 in getItem: feet, legs, chest, head)
            for (int i = 36; i < 40; i++) {
                ItemStack armor = inventory.getItem(i);
                if (!armor.isEmpty()) {
                    dropItemWithRandomDirection(player, armor.copy());
                    inventory.setItem(i, ItemStack.EMPTY);
                    droppedCount++;
                }
            }

            // Drop offhand (slot 40)
            ItemStack offhand = inventory.getItem(40);
            if (!offhand.isEmpty()) {
                dropItemWithRandomDirection(player, offhand.copy());
                inventory.setItem(40, ItemStack.EMPTY);
                droppedCount++;
            }

            SLChannelPointMod.LOGGER.info("Dropped " + droppedCount + " item stacks for " + player.getName().getString());
        } catch (Exception e) {
            SLChannelPointMod.LOGGER.error("Failed to drop inventory", e);
        }
    }

    private static void dropItemWithRandomDirection(ServerPlayer player, ItemStack stack) {
        net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
                player.level(),
                player.getX(),
                player.getY() + 1.0,
                player.getZ(),
                stack
        );

        // Random horizontal direction and speed
        double angle = random.nextDouble() * Math.PI * 2;
        double speed = 0.3 + random.nextDouble() * 0.3;
        double vx = Math.cos(angle) * speed;
        double vz = Math.sin(angle) * speed;
        double vy = 0.2 + random.nextDouble() * 0.2;

        itemEntity.setDeltaMovement(vx, vy, vz);
        itemEntity.setPickUpDelay(40); // 2 second pickup delay
        player.level().addFreshEntity(itemEntity);
    }
}
