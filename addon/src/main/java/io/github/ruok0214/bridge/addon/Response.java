package io.github.ruok0214.bridge.addon;

import com.google.gson.JsonObject;

/**
 * What the add-on writes back for one request. Reports stay as plain text so an agent can read them
 * as-is, and the cost of the attempt is reported so it can tell a slow request from a stuck one.
 */
public record Response(String id, String status, int placed, int ticks, long ms,
                       String settle, String test, String error) {
    public static final String DONE = "done", ERROR = "error";

    public static Response done(String id, int placed, int ticks, long ms, String settle, String test) {
        return new Response(id, DONE, placed, ticks, ms, settle, test, null);
    }

    public static Response error(String id, String message) {
        return new Response(id, ERROR, 0, 0, 0, "", "", message == null ? "unknown error" : message);
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("id", id);
        root.addProperty("status", status);
        if (error != null) {
            root.addProperty("error", error);
            return root + "\n";
        }
        root.addProperty("placed", placed);
        root.addProperty("ticks", ticks);
        root.addProperty("ms", ms);
        root.addProperty("settle", settle);
        root.addProperty("test", test);
        return root + "\n";
    }
}
