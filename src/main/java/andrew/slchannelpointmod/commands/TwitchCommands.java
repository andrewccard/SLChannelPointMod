package andrew.slchannelpointmod.commands;

import andrew.slchannelpointmod.SLChannelPointMod;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Registers the /channelpoints command to open the mod's configuration GUI.
 */
public class TwitchCommands {

    // Flag to signal that the GUI should be opened on the client
    private static boolean openGuiRequested = false;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("channelpoints")
                .executes(ctx -> {
                    // Set the flag - the client will check this and open the GUI
                    openGuiRequested = true;
                    SLChannelPointMod.LOGGER.info("Channel points GUI requested");
                    return 1;
                })
        );
    }

    /**
     * Check if a GUI open has been requested (called from client tick)
     */
    public static boolean isOpenGuiRequested() {
        return openGuiRequested;
    }

    /**
     * Clear the GUI open request (called after opening the GUI)
     */
    public static void clearOpenGuiRequest() {
        openGuiRequested = false;
    }
}
