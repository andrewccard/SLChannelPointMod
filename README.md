# SLChannelPointMod

A Minecraft Fabric mod that integrates Twitch channel point redemptions with in-game actions. When viewers redeem channel points on your Twitch stream, the mod automatically triggers actions in your Minecraft world.

## Features

- **Twitch Integration** - Connects to Twitch EventSub to receive real-time channel point redemptions
- **Automatic Reward Creation** - Creates channel point rewards directly on Twitch from in-game commands
- **Multiple Action Types**:
  - **Spawn Mobs** - Spawn any mob near the player
  - **Give Items** - Give items to the player
  - **Run Commands** - Execute any server command
- **Test Mode** - Test rewards locally without needing a Twitch connection
- **Persistent Config** - Rewards are saved and persist across server restarts

## Requirements

- Minecraft 1.21.10
- Fabric Loader 0.18.1+
- Fabric API
- Java 21+

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/)
2. Download [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download the latest release of SLChannelPointMod
4. Place both mods in your `mods` folder

## Setup

### 1. Authenticate with Twitch

Run this command in-game (requires operator permissions):

```
/twitch login
```

A browser window will open. Log in to Twitch and authorize the application.

### 2. Connect to Twitch

```
/twitch connect
```

You'll see a confirmation message when connected.

### 3. Create Rewards

Rewards are automatically created on your Twitch channel:

```
/twitch reward add "Spawn Creeper" 100 spawn minecraft:creeper 3
/twitch reward add "Free Diamonds" 500 give minecraft:diamond 5
/twitch reward add "Lightning Strike" 1000 command summon lightning_bolt ~ ~ ~
```

That's it! When viewers redeem these channel points, the actions will trigger in-game.

## Commands

| Command | Description |
|---------|-------------|
| `/twitch help` | Show all commands |
| `/twitch setup` | Show setup instructions |
| `/twitch login` | Authenticate with Twitch |
| `/twitch logout` | Clear authentication |
| `/twitch status` | Show connection status |
| `/twitch connect` | Connect to Twitch EventSub |
| `/twitch disconnect` | Disconnect from EventSub |
| `/twitch reconnect` | Reconnect to EventSub |
| `/twitch reward add <name> <cost> <action>` | Create a reward |
| `/twitch reward remove <name>` | Delete a reward |
| `/twitch reward list` | List all configured rewards |
| `/twitch test <reward> [username]` | Simulate a redemption |
| `/twitch testmode [on/off]` | Toggle local testing mode |

## Reward Actions

### Spawn Mob
Spawns mobs near a random player.

```
/twitch reward add "Spawn Zombies" 100 spawn minecraft:zombie 5
```

### Give Item
Gives items to a random player.

```
/twitch reward add "Free Food" 50 give minecraft:cooked_beef 16
```

### Execute Command
Runs a server command. Use `{player}` for the target player's name and `{redeemer}` for the Twitch username.

```
/twitch reward add "Announce" 10 command say {redeemer} says hello to {player}!
/twitch reward add "Smite" 500 command execute at @r run summon lightning_bolt ~ ~ ~
```

## Testing Without Twitch

You can test rewards without connecting to Twitch:

```
/twitch testmode on
/twitch reward add "Test Reward" 100 spawn minecraft:pig 1
/twitch test "Test Reward" TestViewer
```

### Testing with Twitch CLI

For more realistic testing, use the official [Twitch CLI](https://github.com/twitchdev/twitch-cli):

1. Install Twitch CLI
2. Start the mock server:
   ```
   twitch event websocket start-server
   ```
3. In Minecraft:
   ```
   /twitch testmode on
   /twitch connect
   ```
4. Send test events:
   ```
   twitch event trigger channel.channel_points_custom_reward_redemption.add --transport=websocket -r "Spawn Creeper" -u "TestViewer"
   ```

## Configuration

Config is stored in `.minecraft/config/slchannelpointmod.json` and includes:
- Twitch authentication tokens
- Channel information
- Configured rewards and their actions

## Permissions

The mod requires these Twitch permissions (requested during login):
- `channel:read:redemptions` - To receive channel point redemption events
- `channel:manage:redemptions` - To create and delete channel point rewards

## Troubleshooting

### "Not authenticated" error
Run `/twitch login` and complete the browser authentication.

### "Failed to connect" error
- Check that your Twitch token is valid with `/twitch status`
- Try `/twitch login` again if the token expired
- Make sure you have an active internet connection

### Rewards not triggering
- Verify you're connected with `/twitch status`
- Check that the reward name matches exactly (case-insensitive)
- Use `/twitch reward list` to see configured rewards

### Browser doesn't open during login
Copy the URL from the chat/logs and open it manually in your browser.

## Building from Source

```bash
git clone https://github.com/andrewccard/SLChannelPointMod.git
cd SLChannelPointMod
./gradlew build
```

The built JAR will be in `build/libs/`.

## License

CC0-1.0
