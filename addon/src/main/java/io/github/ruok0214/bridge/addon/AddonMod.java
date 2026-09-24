package io.github.ruok0214.bridge.addon;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;

/**
 * Automation layer over AI Block Bridge.
 *
 * <p>Requests arrive as files and results leave as files; the mod never opens a connection.
 * Watching starts off and is turned on per session, because running a request places blocks.
 */
public final class AddonMod implements ModInitializer {
    public static final String ID = "ai_block_bridge_2";
    /** Long enough to read the warning, short enough that a forgotten prompt cannot be confirmed later. */
    private static final long CONFIRM_WINDOW_NANOS = 30_000_000_000L;

    private static Watcher watcher;
    private static long pendingSince;

    /** The live watcher, or null outside a running server. Exposed so tests can drive the real one. */
    public static Watcher watcher() { return watcher; }

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
            watcher = new Watcher(server.getServerDirectory()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { watcher = null; pendingSince = 0; });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { if (watcher != null) watcher.cancel(server); });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (watcher != null) watcher.tick(server);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
            dispatcher.register(command()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("abb2")
            // Same bar as pasting in the base mod: full operator rights.
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_OWNER))
            .then(Commands.literal("watch")
                .then(Commands.literal("on").executes(context -> ask(context.getSource())))
                .then(Commands.literal("confirm").executes(context -> confirm(context.getSource())))
                .then(Commands.literal("off").executes(context -> disable(context.getSource()))))
            .then(Commands.literal("status").executes(context -> {
                boolean on = watcher != null && watcher.enabled();
                reply(context.getSource(), on
                    ? Component.translatable(ID + ".watch.status_on", watcher.inbox().toString())
                    : Component.translatable(ID + ".watch.status_off"));
                return 1;
            }));
    }

    /** Names the world before anything is switched on, so a command typed out of habit is caught. */
    private static int ask(CommandSourceStack source) {
        if (watcher == null) { reply(source, Component.translatable(ID + ".watch.no_server")); return 0; }
        pendingSince = System.nanoTime();
        reply(source, Component.translatable(ID + ".watch.confirm", worldName(source.getServer())));
        return 1;
    }

    private static int confirm(CommandSourceStack source) {
        if (watcher == null) { reply(source, Component.translatable(ID + ".watch.no_server")); return 0; }
        if (pendingSince == 0) { reply(source, Component.translatable(ID + ".watch.nothing_pending")); return 0; }
        if (System.nanoTime() - pendingSince > CONFIRM_WINDOW_NANOS) {
            pendingSince = 0;
            reply(source, Component.translatable(ID + ".watch.expired"));
            return 0;
        }
        pendingSince = 0;
        try {
            watcher.enable(true);
            reply(source, Component.translatable(ID + ".watch.enabled",
                worldName(source.getServer()), watcher.inbox().toString()));
            return 1;
        }
        catch (Exception ex) { return failed(source, ex); }
    }

    private static int disable(CommandSourceStack source) {
        if (watcher == null) { reply(source, Component.translatable(ID + ".watch.no_server")); return 0; }
        pendingSince = 0;
        try {
            watcher.cancel(source.getServer());
            reply(source, Component.translatable(ID + ".watch.disabled"));
            return 1;
        }
        catch (Exception ex) { return failed(source, ex); }
    }

    private static int failed(CommandSourceStack source, Exception ex) {
        reply(source, Component.translatable(ID + ".watch.failed", String.valueOf(ex.getMessage())));
        return 0;
    }

    private static String worldName(MinecraftServer server) {
        return server.getWorldData().getLevelName();
    }

    private static void reply(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> Component.literal("[AI Block Bridge 2] ").append(message), false);
    }
}
