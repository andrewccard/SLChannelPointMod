package andrew.slchannelpointmod.commands;

import andrew.slchannelpointmod.config.ModConfig;
import andrew.slchannelpointmod.config.RewardAction;
import andrew.slchannelpointmod.rewards.RewardHandler;
import andrew.slchannelpointmod.twitch.TwitchAPI;
import andrew.slchannelpointmod.twitch.TwitchAuth;
import andrew.slchannelpointmod.twitch.TwitchEventSub;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class TwitchCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("twitch")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("help")
                        .executes(TwitchCommands::showHelp))
                .then(Commands.literal("setup")
                        .executes(TwitchCommands::showSetup))
                .then(Commands.literal("login")
                        .executes(TwitchCommands::login))
                .then(Commands.literal("status")
                        .executes(TwitchCommands::status))
                .then(Commands.literal("connect")
                        .executes(TwitchCommands::connect))
                .then(Commands.literal("disconnect")
                        .executes(TwitchCommands::disconnect))
                .then(Commands.literal("reconnect")
                        .executes(TwitchCommands::reconnect))
                .then(Commands.literal("logout")
                        .executes(TwitchCommands::logout))
                .then(Commands.literal("test")
                        .then(Commands.argument("reward", StringArgumentType.string())
                                .executes(ctx -> testRedemption(ctx, "TestUser"))
                                .then(Commands.argument("username", StringArgumentType.string())
                                        .executes(ctx -> testRedemption(ctx, StringArgumentType.getString(ctx, "username"))))))
                .then(Commands.literal("testmode")
                        .executes(TwitchCommands::showTestMode)
                        .then(Commands.literal("on")
                                .executes(ctx -> setTestMode(ctx, true)))
                        .then(Commands.literal("off")
                                .executes(ctx -> setTestMode(ctx, false))))
                .then(Commands.literal("reward")
                        .then(Commands.literal("add")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .then(Commands.argument("cost", IntegerArgumentType.integer(1))
                                                .then(Commands.literal("spawn")
                                                        .then(Commands.argument("mob", StringArgumentType.string())
                                                                .executes(ctx -> createSpawnReward(ctx, 1))
                                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                                                        .executes(ctx -> createSpawnReward(ctx, IntegerArgumentType.getInteger(ctx, "count"))))))
                                                .then(Commands.literal("give")
                                                        .then(Commands.argument("item", StringArgumentType.string())
                                                                .executes(ctx -> createGiveReward(ctx, 1))
                                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                                        .executes(ctx -> createGiveReward(ctx, IntegerArgumentType.getInteger(ctx, "count"))))))
                                                .then(Commands.literal("command")
                                                        .then(Commands.argument("command", StringArgumentType.greedyString())
                                                                .executes(TwitchCommands::createCommandReward))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(TwitchCommands::deleteReward)))
                        .then(Commands.literal("list")
                                .executes(TwitchCommands::listRewards)))
        );
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7e=== Twitch Commands ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a/twitch help \u00A77- Show this help message"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a/twitch setup \u00A77- Show setup instructions"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a/twitch login \u00A77- Authenticate with Twitch"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a/twitch logout \u00A77- Clear authentication"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a/twitch status \u00A77- Show connection status"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a/twitch connect \u00A77- Connect to Twitch EventSub"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a/twitch disconnect \u00A77- Disconnect from EventSub"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a/twitch reconnect \u00A77- Reconnect to EventSub"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a/twitch reward add \u00A77- Create a channel point reward"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a/twitch reward remove \u00A77- Delete a channel point reward"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a/twitch reward list \u00A77- List configured rewards"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a/twitch test \u00A77- Simulate a reward redemption"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a/twitch testmode \u00A77- Toggle local testing mode"), false);

        return 1;
    }

    private static int showSetup(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7e=== SLChannelPointMod Setup ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7f1. \u00A77Authenticate with Twitch:"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a   /twitch login"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7f2. \u00A77Connect to receive redemptions:"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a   /twitch connect"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7f3. \u00A77Create rewards (creates on Twitch automatically):"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a   /twitch reward add \"Name\" <cost> spawn minecraft:creeper 3"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a   /twitch reward add \"Name\" <cost> give minecraft:diamond 5"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a   /twitch reward add \"Name\" <cost> command say Hello {redeemer}!"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7f4. \u00A77Test rewards without Twitch:"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a   /twitch test \"Reward Name\" [username]"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A77Use \u00A7a/twitch status \u00A77to check connection status."), false);

        return 1;
    }

    private static int showTestMode(CommandContext<CommandSourceStack> ctx) {
        boolean enabled = TwitchEventSub.isTestMode();
        if (enabled) {
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A7eTest mode is \u00A7aON \u00A77(using local server ws://127.0.0.1:8080/ws)"), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A7eTest mode is \u00A7cOFF \u00A77(using Twitch servers)"), false);
        }
        return 1;
    }

    private static int setTestMode(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        TwitchEventSub.setTestMode(enabled);

        if (enabled) {
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aTest mode enabled!"), false);
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A77Will connect to: ws://127.0.0.1:8080/ws"), false);
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A77Run 'twitch event websocket start-server' first."), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aTest mode disabled!"), false);
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A77Will connect to Twitch production servers."), false);
        }

        if (TwitchEventSub.isConnected()) {
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A7eUse /twitch reconnect to apply changes."), false);
        }

        return 1;
    }

    private static int testRedemption(CommandContext<CommandSourceStack> ctx, String username) {
        String rewardName = StringArgumentType.getString(ctx, "reward");

        RewardAction action = ModConfig.get().getReward(rewardName);
        if (action == null) {
            ctx.getSource().sendFailure(Component.literal("\u00A7cNo reward configured with name: " + rewardName));
            ctx.getSource().sendFailure(Component.literal("\u00A77Use /twitch reward list to see configured rewards."));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7eSimulating redemption: \u00A7b" + rewardName + " \u00A7eby \u00A7f" + username), false);

        TwitchEventSub.ChannelPointRedemption redemption = new TwitchEventSub.ChannelPointRedemption(rewardName, username, "");
        RewardHandler.handleRedemption(redemption);

        return 1;
    }

    private static int login(CommandContext<CommandSourceStack> ctx) {
        if (!TwitchAuth.hasValidClientId()) {
            ctx.getSource().sendFailure(Component.literal("\u00A7cClient ID not configured in the mod."));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7eStarting Twitch authentication..."), false);
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A77A browser window should open. Please authorize the application."), false);

        String authUrl = TwitchAuth.getAuthUrl();
        if (authUrl != null) {
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A77If browser doesn't open, visit:"), false);
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A7b" + authUrl), false);
        }

        TwitchAuth.startAuthFlow(success -> {
            if (success) {
                ctx.getSource().getServer().execute(() -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aAuthentication successful!"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("\u00A77Logged in as: \u00A7b" + ModConfig.get().getChannelName()), false);
                });
            } else {
                ctx.getSource().getServer().execute(() -> {
                    ctx.getSource().sendFailure(Component.literal("\u00A7cAuthentication failed. Check the logs for details."));
                });
            }
        });

        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ModConfig config = ModConfig.get();

        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7e=== Twitch Status ==="), false);

        if (config.hasValidToken()) {
            boolean tokenValid = TwitchAuth.validateToken();
            if (tokenValid) {
                ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aToken: Valid"), false);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal("\u00A7cToken: Expired (use /twitch login)"), false);
            }

            String channelName = config.getChannelName();
            if (channelName != null && !channelName.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal("\u00A77Channel: \u00A7b" + channelName), false);
            }
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A7cNot authenticated (use /twitch login)"), false);
        }

        if (TwitchEventSub.isConnected()) {
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aEventSub: Connected"), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A7cEventSub: Disconnected"), false);
        }

        int rewardCount = config.getRewards().size();
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A77Configured rewards: \u00A7f" + rewardCount), false);

        return 1;
    }

    private static int connect(CommandContext<CommandSourceStack> ctx) {
        if (!ModConfig.get().hasValidToken()) {
            ctx.getSource().sendFailure(Component.literal("\u00A7cNot authenticated. Use /twitch login first."));
            return 0;
        }

        if (TwitchEventSub.isConnected()) {
            ctx.getSource().sendFailure(Component.literal("\u00A7cAlready connected to Twitch EventSub."));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7eConnecting to Twitch EventSub..."), false);

        TwitchEventSub.connect(redemption -> {
            RewardHandler.handleRedemption(redemption);
        });

        return 1;
    }

    private static int disconnect(CommandContext<CommandSourceStack> ctx) {
        if (!TwitchEventSub.isConnected()) {
            ctx.getSource().sendFailure(Component.literal("\u00A7cNot connected to Twitch EventSub."));
            return 0;
        }

        TwitchEventSub.disconnect();
        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aDisconnected from Twitch EventSub."), false);

        return 1;
    }

    private static int reconnect(CommandContext<CommandSourceStack> ctx) {
        if (!ModConfig.get().hasValidToken()) {
            ctx.getSource().sendFailure(Component.literal("\u00A7cNot authenticated. Use /twitch login first."));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7eReconnecting to Twitch EventSub..."), false);

        if (TwitchEventSub.isConnected()) {
            TwitchEventSub.disconnect();
        }

        TwitchEventSub.connect(redemption -> {
            RewardHandler.handleRedemption(redemption);
        });

        return 1;
    }

    private static int logout(CommandContext<CommandSourceStack> ctx) {
        if (TwitchEventSub.isConnected()) {
            TwitchEventSub.disconnect();
        }

        ModConfig.get().setAccessToken("");
        ModConfig.get().setChannelId("");
        ModConfig.get().setChannelName("");

        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aLogged out from Twitch. Token and channel info cleared."), false);

        return 1;
    }

    private static int createSpawnReward(CommandContext<CommandSourceStack> ctx, int count) {
        if (!TwitchEventSub.isTestMode() && !ModConfig.get().hasValidToken()) {
            ctx.getSource().sendFailure(Component.literal("\u00A7cNot authenticated. Use /twitch login first."));
            return 0;
        }

        String name = StringArgumentType.getString(ctx, "name");
        int cost = IntegerArgumentType.getInteger(ctx, "cost");
        String mob = StringArgumentType.getString(ctx, "mob");

        if (TwitchEventSub.isTestMode()) {
            RewardAction action = new RewardAction(RewardAction.ActionType.SPAWN_MOB, mob, count, "test-" + System.currentTimeMillis(), cost);
            ModConfig.get().setReward(name, action);
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a[Test] Reward created locally: \u00A7f" + name + " \u00A77(" + cost + " points) -> spawn " + count + "x " + mob), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7eCreating reward on Twitch..."), false);

        TwitchAPI.createReward(name, cost, rewardId -> {
            ctx.getSource().getServer().execute(() -> {
                if (rewardId != null) {
                    RewardAction action = new RewardAction(RewardAction.ActionType.SPAWN_MOB, mob, count, rewardId, cost);
                    ModConfig.get().setReward(name, action);
                    ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aReward created on Twitch: \u00A7f" + name + " \u00A77(" + cost + " points) -> spawn " + count + "x " + mob), false);
                } else {
                    ctx.getSource().sendFailure(Component.literal("\u00A7cFailed to create reward on Twitch. Check logs for details."));
                }
            });
        });

        return 1;
    }

    private static int createGiveReward(CommandContext<CommandSourceStack> ctx, int count) {
        if (!TwitchEventSub.isTestMode() && !ModConfig.get().hasValidToken()) {
            ctx.getSource().sendFailure(Component.literal("\u00A7cNot authenticated. Use /twitch login first."));
            return 0;
        }

        String name = StringArgumentType.getString(ctx, "name");
        int cost = IntegerArgumentType.getInteger(ctx, "cost");
        String item = StringArgumentType.getString(ctx, "item");

        if (TwitchEventSub.isTestMode()) {
            RewardAction action = new RewardAction(RewardAction.ActionType.GIVE_ITEM, item, count, "test-" + System.currentTimeMillis(), cost);
            ModConfig.get().setReward(name, action);
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a[Test] Reward created locally: \u00A7f" + name + " \u00A77(" + cost + " points) -> give " + count + "x " + item), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7eCreating reward on Twitch..."), false);

        TwitchAPI.createReward(name, cost, rewardId -> {
            ctx.getSource().getServer().execute(() -> {
                if (rewardId != null) {
                    RewardAction action = new RewardAction(RewardAction.ActionType.GIVE_ITEM, item, count, rewardId, cost);
                    ModConfig.get().setReward(name, action);
                    ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aReward created on Twitch: \u00A7f" + name + " \u00A77(" + cost + " points) -> give " + count + "x " + item), false);
                } else {
                    ctx.getSource().sendFailure(Component.literal("\u00A7cFailed to create reward on Twitch. Check logs for details."));
                }
            });
        });

        return 1;
    }

    private static int createCommandReward(CommandContext<CommandSourceStack> ctx) {
        if (!TwitchEventSub.isTestMode() && !ModConfig.get().hasValidToken()) {
            ctx.getSource().sendFailure(Component.literal("\u00A7cNot authenticated. Use /twitch login first."));
            return 0;
        }

        String name = StringArgumentType.getString(ctx, "name");
        int cost = IntegerArgumentType.getInteger(ctx, "cost");
        String command = StringArgumentType.getString(ctx, "command");

        if (TwitchEventSub.isTestMode()) {
            RewardAction action = new RewardAction(RewardAction.ActionType.EXECUTE_COMMAND, command, 1, "test-" + System.currentTimeMillis(), cost);
            ModConfig.get().setReward(name, action);
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a[Test] Reward created locally: \u00A7f" + name + " \u00A77(" + cost + " points) -> command: " + command), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7eCreating reward on Twitch..."), false);

        TwitchAPI.createReward(name, cost, rewardId -> {
            ctx.getSource().getServer().execute(() -> {
                if (rewardId != null) {
                    RewardAction action = new RewardAction(RewardAction.ActionType.EXECUTE_COMMAND, command, 1, rewardId, cost);
                    ModConfig.get().setReward(name, action);
                    ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aReward created on Twitch: \u00A7f" + name + " \u00A77(" + cost + " points) -> command: " + command), false);
                } else {
                    ctx.getSource().sendFailure(Component.literal("\u00A7cFailed to create reward on Twitch. Check logs for details."));
                }
            });
        });

        return 1;
    }

    private static int deleteReward(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        RewardAction action = ModConfig.get().getReward(name);

        if (action == null) {
            ctx.getSource().sendFailure(Component.literal("\u00A7cReward not found: " + name));
            return 0;
        }

        if (TwitchEventSub.isTestMode()) {
            ModConfig.get().removeReward(name);
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A7a[Test] Reward removed locally: \u00A7f" + name), false);
            return 1;
        }

        if (!ModConfig.get().hasValidToken()) {
            ctx.getSource().sendFailure(Component.literal("\u00A7cNot authenticated. Use /twitch login first."));
            return 0;
        }

        if (!action.hasTwitchReward()) {
            ModConfig.get().removeReward(name);
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aReward removed: \u00A7f" + name), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7eDeleting reward from Twitch..."), false);

        TwitchAPI.deleteReward(action.getTwitchRewardId(), success -> {
            ctx.getSource().getServer().execute(() -> {
                if (success) {
                    ModConfig.get().removeReward(name);
                    ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aReward deleted from Twitch and removed: \u00A7f" + name), false);
                } else {
                    ctx.getSource().sendFailure(Component.literal("\u00A7cFailed to delete reward from Twitch. Check logs for details."));
                }
            });
        });

        return 1;
    }

    private static int listRewards(CommandContext<CommandSourceStack> ctx) {
        Map<String, RewardAction> rewards = ModConfig.get().getRewards();

        if (rewards.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A77No rewards configured. Use /twitch reward add to add rewards."), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7e=== Configured Rewards ==="), false);

        for (Map.Entry<String, RewardAction> entry : rewards.entrySet()) {
            String name = entry.getKey();
            RewardAction action = entry.getValue();

            String actionStr = switch (action.getType()) {
                case SPAWN_MOB -> "spawn " + action.getCount() + "x " + action.getValue();
                case GIVE_ITEM -> "give " + action.getCount() + "x " + action.getValue();
                case EXECUTE_COMMAND -> "command: " + action.getValue();
            };

            String twitchInfo = action.hasTwitchReward() ? " \u00A7d[" + action.getCost() + " pts]" : "";
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A7f" + name + twitchInfo + " \u00A77-> " + actionStr), false);
        }

        return 1;
    }
}
