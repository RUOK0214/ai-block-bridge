package io.github.ruok0214.bridge.addon;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import io.github.ruok0214.bridge.TestScript;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The folder side of the add-on: a request file in, a reply file out. Engine tests cover the circuit
 * work; this covers the wiring around it, which nothing else executes.
 *
 * <p>One test rather than several: the watcher is a single object per server and game tests run at
 * the same time, so separate tests would switch it on and off under each other.
 */
public class WatcherGameTests {
    @GameTest(structure = "ai_block_bridge_2_test:empty")
    public void cancellationRestoresSpeedAndArchivesOnlyAfterReply(GameTestHelper h) throws Exception {
        Watcher isolated = new Watcher(Files.createTempDirectory("abb2-cancel-test-"));
        isolated.enable(true);
        submit(isolated, "cancel-speed", request(h, "cancel-speed", ",\"tickRate\":200"));
        var server = h.getLevel().getServer();
        float original = server.tickRateManager().tickrate();
        try {
            isolated.tick(server);
            h.assertTrue(server.tickRateManager().tickrate() == 200, "Speed override missing");
            h.assertTrue(Files.exists(isolated.inbox().resolveSibling("processing").resolve("cancel-speed.json")), "Request not claimed");
            h.assertTrue(!Files.exists(isolated.inbox().resolveSibling("done").resolve("cancel-speed.json")), "Archived before reply");
            isolated.cancel(server);
            h.assertTrue(server.tickRateManager().tickrate() == original, "Speed was not restored");
            h.assertTrue(Files.readString(reply(isolated, "cancel-speed")).contains("Cancelled"), "Missing cancellation reply");
            h.assertTrue(Files.exists(isolated.inbox().resolveSibling("done").resolve("cancel-speed.json")), "Missing completed archive");
            h.succeed();
        } finally {
            isolated.cancel(server);
            server.tickRateManager().setTickRate(original);
        }
    }
    private static String request(GameTestHelper h, String id, String extra) {
        BlockPos o = h.absolutePos(new BlockPos(1, 1, 1));
        return "{\"id\":\"" + id + "\",\"dimension\":\"" + h.getLevel().dimension().identifier() + "\","
            + "\"region\":[" + o.getX() + "," + o.getY() + "," + o.getZ() + ","
            + (o.getX() + 1) + "," + (o.getY() + 1) + "," + o.getZ() + "],"
            + "\"script\":\"0 0 0 | minecraft:stone\\n0 1 0 | minecraft:redstone_wire\\n\"" + extra + "}";
    }

    /** Agents are told to write beside the target and rename; the test does the same. */
    private static void submit(Watcher watcher, String id, String json) throws Exception {
        Path temporary = watcher.inbox().resolve(id + ".json.tmp");
        Files.writeString(temporary, json, StandardCharsets.UTF_8);
        Files.move(temporary, watcher.inbox().resolve(id + ".json"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static Path reply(Watcher watcher, String id) {
        return watcher.inbox().resolveSibling("out").resolve(id + ".json");
    }

    private static void unchecked(GameTestHelper h, ThrowingStep step) {
        try { step.run(); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }

    private interface ThrowingStep { void run() throws Exception; }

    private static void command(GameTestHelper h, String line) {
        var server = h.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), line);
    }

    @GameTest(structure = "ai_block_bridge_2_test:empty", maxTicks = 7000)
    public void requestsAreAnsweredOnlyWhileWatching(GameTestHelper h) throws Exception {
        Watcher watcher = AddonMod.watcher();
        h.assertTrue(watcher != null, "No watcher on a running server");
        h.assertTrue(!watcher.enabled(), "Watching must start off");

        // Asking is not enough: a command typed out of habit must not switch automation on.
        command(h, "abb2 watch on");
        h.assertTrue(!watcher.enabled(), "Watching turned on without confirmation");
        command(h, "abb2 watch confirm");
        h.assertTrue(watcher.enabled(), "Confirming did not turn watching on");
        // The run directory survives between runs, so never trust what an earlier run left behind.
        for (String id : new String[]{"wt-ok", "wt-bad", "wt-off", "wt-q1", "wt-q2", "wt-q3"}) {
            Files.deleteIfExists(reply(watcher, id));
            Files.deleteIfExists(watcher.inbox().resolve(id + ".json"));
            Files.deleteIfExists(watcher.inbox().resolveSibling("done").resolve(id + ".json"));
        }
        submit(watcher, "wt-ok", request(h, "wt-ok", ",\"sprint\":300"));

        // Whether sprinting is still running by any given tick races with the request finishing, and
        // this server already runs flat out. SprintClientTest measures the effect where it is visible.

        h.runAtTickTime(120, () -> unchecked(h, () -> {
            Path out = reply(watcher, "wt-ok");
            h.assertTrue(Files.exists(out), "No reply was written to " + out);
            String body = Files.readString(out, StandardCharsets.UTF_8);
            h.assertTrue(body.contains("\"status\":\"done\""), "Unexpected reply: " + body);
            h.assertTrue(body.contains("\"placed\":2"), "Wrong block count: " + body);
            h.assertTrue(body.contains("Settle Report"), "Reply carried no settle report: " + body);
            h.assertTrue(body.contains("\"ticks\":") && body.contains("\"ms\":"), "Reply did not report its cost: " + body);
            h.assertTrue(!Files.exists(watcher.inbox().resolve("wt-ok.json")), "Request was not archived");
            submit(watcher, "wt-bad", "{\"id\":\"wt-bad\",\"region\":[0,0,0,1,1,1],\"script\":\"nonsense\"}");
        }));

        // An agent iterating drops many requests; they must all be answered, not just the first.
        h.runAtTickTime(160, () -> unchecked(h, () -> {
            for (String id : new String[]{"wt-q1", "wt-q2", "wt-q3"}) {
                Files.deleteIfExists(reply(watcher, id));
                submit(watcher, id, request(h, id, ""));
            }
        }));

        h.runAtTickTime(230, () -> unchecked(h, () -> {
            for (String id : new String[]{"wt-q1", "wt-q2", "wt-q3"}) {
                Path out = reply(watcher, id);
                h.assertTrue(Files.exists(out), "Queued request " + id + " was never answered");
                h.assertTrue(Files.readString(out, StandardCharsets.UTF_8).contains("\"status\":\"done\""),
                    "Queued request " + id + " did not succeed");
                h.assertTrue(!Files.exists(watcher.inbox().resolve(id + ".json")), id + " was left in the inbox");
            }
        }));

        // The longest wait the format allows must still finish; the stuck-request budget is meant to
        // catch hangs, not long circuits, and ABB1's own tests run far past ten thousand ticks.
        h.runAtTickTime(240, () -> unchecked(h, () -> {
            Files.deleteIfExists(reply(watcher, "wt-long"));
            submit(watcher, "wt-long", request(h, "wt-long", "").replace("\"script\"",
                "\"test\":\"@case the longest legal wait\\nwait " + TestScript.MAX_TOTAL_TICKS + "\\n\",\"script\""));
        }));

        h.runAtTickTime(TestScript.MAX_TOTAL_TICKS + 400, () -> unchecked(h, () -> {
            Path out = reply(watcher, "wt-long");
            h.assertTrue(Files.exists(out), "The longest legal request was never answered");
            String body = Files.readString(out, StandardCharsets.UTF_8);
            h.assertTrue(body.contains("\"status\":\"done\""), "A legal long request was cut off: " + body);
            h.assertTrue(!body.contains("Gave up"), "The stuck-request budget fired on a legal request: " + body);
        }));

        h.runAtTickTime(TestScript.MAX_TOTAL_TICKS + 500, () -> unchecked(h, () -> {
            String body = Files.readString(reply(watcher, "wt-bad"), StandardCharsets.UTF_8);
            h.assertTrue(body.contains("\"status\":\"error\""), "Bad request was not reported: " + body);
            // An agent reads this, so it must be a sentence rather than the base mod's message envelope.
            h.assertTrue(!body.contains("AI_BLOCK_BRIDGE_MESSAGE"), "Error was not translated: " + body);
            h.assertTrue(body.contains("Line 1"), "Error lost the line number: " + body);
            command(h, "abb2 watch off");
            h.assertTrue(!watcher.enabled(), "Watching did not turn off");
            // A stale confirmation must not switch it back on later.
            command(h, "abb2 watch confirm");
            h.assertTrue(!watcher.enabled(), "A confirmation with nothing pending turned watching on");
            Files.writeString(watcher.inbox().resolve("wt-off.json"), request(h, "wt-off", ""), StandardCharsets.UTF_8);
        }));

        h.runAtTickTime(TestScript.MAX_TOTAL_TICKS + 700, () -> unchecked(h, () -> {
            h.assertTrue(Files.exists(watcher.inbox().resolve("wt-off.json")), "A disabled watcher consumed a request");
            h.assertTrue(!Files.exists(reply(watcher, "wt-off")), "A disabled watcher answered a request");
            Files.deleteIfExists(watcher.inbox().resolve("wt-off.json"));
            h.succeed();
        }));
    }
}
