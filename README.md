# SLChannelPointMod

A Minecraft Fabric mod that integrates Twitch channel point redemptions with in-game actions. When viewers redeem channel points on your Twitch stream, the mod automatically triggers actions in your Minecraft world.

## Features

- **Twitch Integration** - Connects to Twitch EventSub to receive real-time channel point redemptions
- **Easy GUI Configuration** - All setup done through an intuitive in-game menu
- **Multiple Action Types**:
  - **Spawn Mobs** - Spawn any mob near the player
  - **Random Mobs** - Spawn random mobs from a configurable pool
  - **Give Items** - Give items to the player
  - **Random Items** - Give random items to the player
  - **Apply Effects** - Apply potion effects to the player
  - **Random Effects** - Apply random effects from a configurable pool
  - **Play Sounds** - Play any sound effect with adjustable volume and pitch
  - **Run Commands** - Execute any server command with placeholders
  - **Special Actions** - Shuffle inventory, drop held item, drop entire inventory
- **Sync from Twitch** - Import existing channel point rewards from your Twitch channel
- **Publish to Twitch** - Create new channel point rewards directly from the mod
- **On-Screen HUD** - See redemptions as they happen with customizable position and scale
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
4. Place all mods in your `mods` folder

## Setup

### 1. Open the Configuration GUI

There are two ways to open the configuration GUI:
- Press **K** (default keybind) while in-game
- Type `/channelpoints` in chat

### 2. Login to Twitch

1. Go to the **Settings** tab
2. Expand **Twitch Connection**
3. Click **Login with Twitch**
4. A browser window will open - authorize the application
5. Return to the game once authenticated

### 3. Connect to Twitch

After logging in, click **Connect** to start receiving channel point redemptions.

### 4. Configure Rewards

**Option A: Sync existing rewards from Twitch**
1. Go to the **Rewards** tab
2. Click **Sync from Twitch** to import your existing channel point rewards
3. Select a reward and click **Edit** to configure what action it triggers

**Option B: Create new rewards**
1. Click **+ Add Reward** to create a new reward
2. Enter a name, cost, and configure the action
3. Click **Save**
4. Optionally click **Publish** to create the reward on your Twitch channel

That's it! When viewers redeem channel points, the configured actions will trigger in-game.

## Action Types

### Spawn Mob
Spawns mobs near a random player.
- Select the mob type using the browse button
- Set the count (or use random range)

### Random Mob (Same/Each)
Spawns random mobs from the configured mob pool.
- **Same**: All spawned mobs are the same random type
- **Each**: Each mob is randomly selected individually
- Configure a custom mob pool per reward or use the default hostile/passive lists

### Give Item
Gives items to a random player.
- Select the item using the browse button
- Set the quantity

### Random Item (Same/Each)
Gives random items to the player from all available items.
- **Same**: All items given are the same random type
- **Each**: Each item is randomly selected individually

### Apply Effect
Applies a potion effect to the player.
- Select the effect using the browse button
- Set the level (1 = Level I, 2 = Level II, etc.)
- Set the duration in seconds

### Random Effect
Applies a random potion effect from a configurable pool.
- Configure effect pool with presets (harmful, beneficial) or custom selection
- Set the level and duration

### Play Sound
Plays a sound effect at the player's location.
- Select from all Minecraft sounds with a preview button
- Adjust volume (0.0 - 1.0) and pitch (0.5 - 2.0)

### Execute Command
Runs a server command with placeholder support.
- `{player}` - Replaced with the target player's name
- `{redeemer}` - Replaced with the Twitch username who redeemed
- `{random:min:max}` - Replaced with a random number in the range

Example: `say {redeemer} says hello to {player}!`
Example: `tp {player} ~ ~{random:5:20} ~`

### Special Actions
Pre-configured special actions:
- **Shuffle Inventory** - Randomizes all items in the player's inventory
- **Drop Held Item** - Drops the item the player is currently holding
- **Drop Inventory** - Drops the player's entire inventory

## HUD Settings

The mod displays on-screen notifications when rewards are redeemed. Customize in Settings > HUD Settings:
- **Position** - Top-left, top-right, bottom-left, or bottom-right
- **Max Messages** - How many redemptions to show at once (1-10)
- **Scale** - Size of the HUD text (0.5x - 2.0x)

## Configuration

Config is stored in `.minecraft/config/slchannelpointmod.json` and includes:
- Twitch authentication tokens
- Channel information
- Configured rewards and their actions
- HUD settings
- Random mob pool

## Permissions

The mod requires these Twitch permissions (requested during login):
- `channel:read:redemptions` - To receive channel point redemption events
- `channel:manage:redemptions` - To create and delete channel point rewards

## Troubleshooting

### Can't login / Browser doesn't open
Copy the URL from the chat/logs and open it manually in your browser.

### Rewards not triggering
- Verify you're connected (check the Twitch Connection status in Settings)
- Make sure the reward has an action configured (not showing as red/unconfigured)
- Try disconnecting and reconnecting

### Token expired
Go to Settings > Twitch Connection and click **Re-login**.

## Building from Source

```bash
git clone https://github.com/andrewccard/SLChannelPointMod.git
cd SLChannelPointMod
./gradlew build
```

The built JAR will be in `build/libs/`.

## License

CC0-1.0
