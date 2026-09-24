package io.github.ruok0214.bridge.addon;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ruok0214.bridge.Region;
import io.github.ruok0214.bridge.Script;
import io.github.ruok0214.bridge.TestScript;

/**
 * One attempt an agent asks for. Data only: the fields are read, never executed.
 *
 * <pre>
 * {"id":"attempt-1","dimension":"minecraft:overworld","region":[0,64,0,7,66,0],
 *  "script":"0 0 0 | minecraft:stone\n","test":"@case a\nwait 1\n","clear":true,"sprint":200}
 * </pre>
 */
public record Request(String id, String dimension, Region region, String script, String test,
                      boolean clear, int sprint, float tickRate) {
    /** Enough to cover the longest test a script may ask for, without letting one request hang the server. */
    public static final int MAX_SPRINT = 20_000;
    /** Raising ticks per second is the other way to finish sooner; 0 leaves the world alone. */
    public static final float MAX_TICK_RATE = 1000f;

    public static Request parse(String json) {
        JsonObject root;
        try { root = JsonParser.parseString(json).getAsJsonObject(); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Not a JSON object"); }

        String id = string(root, "id", null);
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        if (!id.matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException("id must be 1-64 chars of A-Z a-z 0-9 . _ -");

        String dimension = string(root, "dimension", "minecraft:overworld");
        String script = string(root, "script", "");
        String test = string(root, "test", "");
        if (script.isBlank() && test.isBlank()) throw new IllegalArgumentException("script or test is required");

        Region region = region(root);
        // Fail here rather than half-way through a world edit.
        if (!script.isBlank()) Script.parse(script, region);
        if (!test.isBlank()) TestScript.parse(test, region);

        boolean clear = !root.has("clear") || root.get("clear").getAsBoolean();
        int sprint = root.has("sprint") ? root.get("sprint").getAsInt() : 0;
        if (sprint < 0 || sprint > MAX_SPRINT) throw new IllegalArgumentException("sprint must be 0.." + MAX_SPRINT);
        float tickRate = root.has("tickRate") ? root.get("tickRate").getAsFloat() : 0f;
        if (!Float.isFinite(tickRate) || tickRate < 0 || tickRate > MAX_TICK_RATE) throw new IllegalArgumentException("tickRate must be 0.." + MAX_TICK_RATE);
        return new Request(id, dimension, region, script, test, clear, sprint, tickRate);
    }

    private static String string(JsonObject root, String key, String fallback) {
        if (!root.has(key) || root.get(key).isJsonNull()) return fallback;
        return root.get(key).getAsString();
    }

    private static Region region(JsonObject root) {
        if (!root.has("region")) throw new IllegalArgumentException("region is required");
        JsonArray values;
        try { values = root.getAsJsonArray("region"); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("region must be an array"); }
        if (values.size() != 6) throw new IllegalArgumentException("region needs 6 numbers: x1 y1 z1 x2 y2 z2");
        int[] v = new int[6];
        for (int i = 0; i < 6; i++) {
            try { v[i] = values.get(i).getAsInt(); }
            catch (RuntimeException ex) { throw new IllegalArgumentException("region values must be whole numbers"); }
        }
        return Region.of(v[0], v[1], v[2], v[3], v[4], v[5]);
    }
}
