package io.github.ruok0214.bridge.addon;

import io.github.ruok0214.bridge.BridgeAutomation;
import io.github.ruok0214.bridge.Region;
import io.github.ruok0214.bridge.TestRunner;
import io.github.ruok0214.bridge.TestScript;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * One attempt at a circuit: clear, place, settle, then run the test script.
 * Runs across server ticks because redstone needs them; sprinting makes that cheap.
 */
public final class Engine {
    /** Repeaters and comparators need a few ticks before the circuit stops changing. */
    public static final int SETTLE_TICKS = 10;

    public enum Stage { SETTLING, TESTING, DONE }

    private final Region region;
    private final String script;
    private final String testScript;
    private Stage stage = Stage.SETTLING;
    private int wait = SETTLE_TICKS;
    private TestRunner runner;
    private int placed;
    private int ticks;
    private String settleReport = "";
    private String testReport = "";

    public Engine(ServerLevel level, Region region, String script, String testScript) throws Exception {
        this(level, region, script, testScript, true);
    }

    public Engine(ServerLevel level, Region region, String script, String testScript, boolean clear) throws Exception {
        this.region = region;
        this.script = script;
        this.testScript = testScript;
        BridgeAutomation.validate(level, region);
        // Resolve the script first: a bad block name must not cost the caller their region.
        boolean hasScript = script != null && !script.isBlank();
        if (hasScript) BridgeAutomation.check(level, region, script);
        if (testScript != null && !testScript.isBlank())
            runner = new TestRunner(level, region, TestScript.parse(testScript, region));
        if (clear) BridgeAutomation.clear(level, region);
        if (hasScript) {
            placed = BridgeAutomation.place(level, region, script);
            BridgeAutomation.settle(level, region, script);
        }
    }

    public Stage stage() { return stage; }
    public int placed() { return placed; }
    /** Server ticks this attempt consumed, which is what sprinting compresses. */
    public int ticks() { return ticks; }
    public String settleReport() { return settleReport; }
    public String testReport() { return testReport; }

    public void tick(ServerLevel level) throws Exception {
        if (stage != Stage.DONE) ticks++;
        switch (stage) {
            case SETTLING -> {
                if (--wait > 0) return;
                if (script != null && !script.isBlank()) {
                    settleReport = BridgeAutomation.settleReport(level, region, script, SETTLE_TICKS);
                }
                if (testScript == null || testScript.isBlank()) { stage = Stage.DONE; return; }
                stage = Stage.TESTING;
            }
            case TESTING -> {
                runner.tick(level);
                if (!runner.done()) return;
                testReport = runner.report();
                stage = Stage.DONE;
            }
            case DONE -> { }
        }
    }

    /** Runs the remaining ticks as fast as the machine allows, so an agent is not paced by 20 TPS. */
    public static void sprint(MinecraftServer server, int ticks) {
        server.tickRateManager().requestGameToSprint(ticks);
    }
}
