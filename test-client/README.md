# Testing GoomayChannelPoints

## Quick Test (No External Tools)

The simplest way to test reward actions:

```
/twitch testmode on
/twitch reward add "Spawn Creeper" 100 spawn minecraft:creeper 3
/twitch test "Spawn Creeper" TestViewer
```

This creates a reward locally and simulates a redemption without needing Twitch.

## Full Test with Twitch CLI

Use the official Twitch CLI to simulate real EventSub WebSocket events.

### 1. Install Twitch CLI

**Windows (with Scoop):**
```powershell
scoop bucket add twitch https://github.com/twitchdev/scoop-bucket.git
scoop install twitch-cli
```

**Windows (manual):**
Download from: https://github.com/twitchdev/twitch-cli/releases

### 2. Configure Twitch CLI

```batch
.\configure-cli.bat
```

### 3. Start the Mock Server

```batch
.\start-server.bat
```

Or manually:
```
twitch event websocket start-server
```

### 4. Connect from Minecraft

```
/twitch testmode on
/twitch reward add "Spawn Creeper" 100 spawn minecraft:creeper 3
/twitch connect
```

### 5. Send Test Events

```batch
.\test-redemption.bat "Spawn Creeper" TestViewer
```

Or manually:
```
twitch event trigger channel.channel_points_custom_reward_redemption.add --transport=websocket -r "Spawn Creeper" -u "TestViewer"
```

## Scripts

- `configure-cli.bat` - Configure Twitch CLI with dummy credentials
- `start-server.bat` - Start the EventSub WebSocket mock server
- `test-redemption.bat` - Send a test redemption event
