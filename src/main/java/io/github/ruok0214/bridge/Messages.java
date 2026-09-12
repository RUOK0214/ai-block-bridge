package io.github.ruok0214.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;

/** Keeps translation keys intact across status storage, exceptions and network replies.
 * World scripts and timeline data must never pass through this codec.
 */
public final class Messages {
    private static final String PREFIX = "AI_BLOCK_BRIDGE_MESSAGE:";
    private static final String NAMESPACE = "ai_block_bridge.";
    private Messages() {}

    public static String text(String key, Object... args) {
        if (!key.startsWith(NAMESPACE)) throw new IllegalArgumentException("Invalid message key");
        JsonObject message = new JsonObject();
        message.addProperty("key", key);
        JsonArray values = new JsonArray();
        for (Object arg : args) values.add(String.valueOf(arg));
        message.add("args", values);
        return PREFIX + message;
    }

    private static JsonObject parse(String value) {
        if (value == null || !value.startsWith(PREFIX)) return null;
        try {
            JsonObject message = JsonParser.parseString(value.substring(PREFIX.length())).getAsJsonObject();
            if (!message.get("key").getAsString().startsWith(NAMESPACE)) return null;
            JsonArray args = message.getAsJsonArray("args");
            if (args.size() > 32) return null;
            for (var arg : args) if (!arg.isJsonPrimitive() || !arg.getAsJsonPrimitive().isString()) return null;
            return message;
        } catch (RuntimeException ex) { return null; }
    }

    public static boolean isEncoded(String value) { return parse(value) != null; }

    public static boolean isError(String value) {
        JsonObject message = parse(value);
        // Compatibility with replies from versions before localization.
        return message != null ? message.get("key").getAsString().equals(NAMESPACE + "error")
            : value != null && value.startsWith("오류:");
    }

    public static Component component(String value) { return component(value, 0); }

    private static Component component(String value, int depth) {
        JsonObject message = depth < 8 ? parse(value) : null;
        if (message == null) return Component.literal(value == null ? "" : value);
        JsonArray values = message.getAsJsonArray("args");
        Object[] args = new Object[values.size()];
        for (int i = 0; i < args.length; i++) args[i] = component(values.get(i).getAsString(), depth + 1);
        return Component.translatable(message.get("key").getAsString(), args);
    }

    public static String display(String value) { return component(value).getString(); }
}
