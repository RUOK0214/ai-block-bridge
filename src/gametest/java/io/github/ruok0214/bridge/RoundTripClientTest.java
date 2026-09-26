package io.github.ruok0214.bridge;

import io.github.ruok0214.bridge.client.BridgeClient;
import io.github.ruok0214.bridge.client.BridgeScreen;
import io.github.ruok0214.bridge.client.ReportScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;

/**
 * Drives settle and test through the real path: client packet, server action, tick lifecycle
 * and the reply that fills the report screen. The server-side GameTests call the inner helpers
 * directly, so this is the only place the wiring between them runs.
 */
public final class RoundTripClientTest implements FabricClientGameTest {
    private static final String WIRE="minecraft:redstone_wire[east=none,north=none,power=0,south=none,west=none]";
    private static final int REQUEST_GAP=60;

    @Override public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280,720);
        // Placement, settling and tests all need operator level 4.
        try(var world=context.worldBuilder().adjustSettings(settings->settings.setAllowCommands(true)).create()) {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("tp @a 3 6 8");
            world.getServer().runCommand("fill -2 0 -2 10 0 10 minecraft:stone");
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);

            settleReportsWhatTheWorldCorrected(context);
            undoStillWorksAfterSettling(context);
            unlistedPolicyAndUndo(context, world.getServer()::runCommand);
            testScriptDrivesAndJudgesTheCircuit(context, world.getServer()::runCommand);
        }
    }

    private void unlistedPolicyAndUndo(ClientGameTestContext context, java.util.function.Consumer<String> command) {
        command.accept("fill 0 4 3 3 4 3 minecraft:air");
        command.accept("setblock 1 4 3 minecraft:barrel{Items:[{Slot:0b,id:\"minecraft:diamond\",count:3}]}");
        command.accept("setblock 3 4 3 minecraft:emerald_block");
        context.waitTicks(REQUEST_GAP);
        context.runOnClient(mc -> {
            BridgeClient.a = new BlockPos(0,4,3);
            BridgeClient.b = new BlockPos(2,4,3);
            BridgeClient.script = "0 0 0 | minecraft:gold_block";
            BridgeClient.send(1);
        });
        context.waitTicks(REQUEST_GAP);
        context.runOnClient(mc -> {
            if (Messages.isError(BridgeClient.status) || !mc.level.getBlockState(new BlockPos(1,4,3)).is(net.minecraft.world.level.block.Blocks.BARREL))
                throw new AssertionError("Absent unlisted header must preserve the omitted barrel");
            BridgeClient.send(2);
        });
        context.waitTicks(REQUEST_GAP);
        context.runOnClient(mc -> {
            BridgeClient.script = "# unlisted: clear\n0 0 0 | minecraft:gold_block";
            BridgeClient.send(1);
        });
        context.waitTicks(REQUEST_GAP);
        context.setScreen(BridgeScreen::new);
        context.takeScreenshot("unlisted-clear-mode");
        context.runOnClient(mc -> {
            if (Messages.isError(BridgeClient.status) || !mc.level.getBlockState(new BlockPos(1,4,3)).isAir())
                throw new AssertionError("Clear paste did not remove the omitted barrel");
            if (!mc.level.getBlockState(new BlockPos(3,4,3)).is(net.minecraft.world.level.block.Blocks.EMERALD_BLOCK))
                throw new AssertionError("Clear affected a block outside the region");
            BridgeClient.send(2);
        });
        context.waitTicks(REQUEST_GAP);
        context.runOnClient(mc -> {
            if (Messages.isError(BridgeClient.status) || !mc.level.getBlockState(new BlockPos(0,4,3)).isAir()
                || !mc.level.getBlockState(new BlockPos(1,4,3)).is(net.minecraft.world.level.block.Blocks.BARREL))
                throw new AssertionError("Real undo did not restore omitted blocks and original air");
            BridgeClient.script = "# unlisted: invalid\n0 0 0 | minecraft:gold_block";
            BridgeClient.send(1);
        });
        context.waitTicks(REQUEST_GAP);
        context.runOnClient(mc -> {
            if (!Messages.isError(BridgeClient.status) || !mc.level.getBlockState(new BlockPos(0,4,3)).isAir()
                || !mc.level.getBlockState(new BlockPos(1,4,3)).is(net.minecraft.world.level.block.Blocks.BARREL))
                throw new AssertionError("Invalid header was not rejected before world changes");
        });
        context.setScreen(() -> null);
    }

    private void settleReportsWhatTheWorldCorrected(ClientGameTestContext context) {
        context.runOnClient(mc->{
            BridgeClient.a=new BlockPos(0,1,0);
            BridgeClient.b=new BlockPos(1,2,0);
            BridgeClient.dimension=mc.level.dimension().identifier().toString();
            BridgeClient.script="0 0 0 | minecraft:stone\n1 0 0 | minecraft:stone\n"
                +"0 1 0 | "+WIRE+"\n1 1 0 | "+WIRE+"\n";
            BridgeClient.settleReport="";
            BridgeClient.send(1);
        });
        // The server rate-limits to one request per second, so each step waits well past that.
        context.waitTicks(REQUEST_GAP);
        context.runOnClient(mc->{
            if(Messages.isError(BridgeClient.status))
                throw new AssertionError("Paste failed: "+Messages.display(BridgeClient.status));
            BridgeClient.send(BridgePacket.SETTLE);
        });
        context.waitTicks(REQUEST_GAP);
        context.runOnClient(mc->{
            String report=BridgeClient.settleReport;
            if(report.isEmpty())throw new AssertionError("No settle report arrived. status="+Messages.display(BridgeClient.status));
            if(!report.startsWith("# AI Block Bridge Settle Report v1"))throw new AssertionError("Unexpected report:\n"+report);
            if(!report.contains("mismatched 2"))throw new AssertionError("Both dusts should have been corrected:\n"+report);
            if(!report.contains("0 1 0 | requested ")||!report.contains("east=side"))
                throw new AssertionError("Report lost the corrected connection:\n"+report);
        });
        context.waitForScreen(ReportScreen.class);
        context.takeScreenshot("settle-report-live");
    }

    private void undoStillWorksAfterSettling(ClientGameTestContext context) {
        // Settling moves the world past the snapshot taken at paste time; undo must still accept it.
        context.setScreen(()->null);
        context.runOnClient(mc->BridgeClient.send(2));
        context.waitTicks(REQUEST_GAP);
        context.runOnClient(mc->{
            if(Messages.isError(BridgeClient.status))
                throw new AssertionError("Undo after settling was refused: "+Messages.display(BridgeClient.status));
            if(!mc.level.getBlockState(new BlockPos(0,2,0)).isAir())
                throw new AssertionError("Undo left the dust behind: "+mc.level.getBlockState(new BlockPos(0,2,0)));
        });
    }

    private void testScriptDrivesAndJudgesTheCircuit(ClientGameTestContext context, java.util.function.Consumer<String> command) {
        command.accept("setblock 5 1 0 minecraft:stone");
        command.accept("setblock 5 2 0 minecraft:lever[face=floor,facing=north,powered=false]");
        command.accept("setblock 6 2 0 minecraft:redstone_lamp");
        context.waitTicks(REQUEST_GAP);
        // Launched from the structure screen, which is where a pasted circuit is checked from.
        context.setScreen(BridgeScreen::new);
        context.waitForScreen(BridgeScreen.class);
        context.runOnClient(mc->{
            BridgeClient.a=new BlockPos(5,1,0);
            BridgeClient.b=new BlockPos(6,2,0);
            BridgeClient.testReport="";
            BridgeClient.testScript="@case lamp on\n"
                +"set 0 1 0 | minecraft:lever[powered=true]\nwait 10\n"
                +"expect 1 1 0 | minecraft:redstone_lamp[lit=true]\n"
                +"@case lamp off\n"
                +"set 0 1 0 | minecraft:lever[powered=false]\nwait 10\n"
                +"expect 1 1 0 | minecraft:redstone_lamp[lit=false]\n"
                +"@case deliberately wrong\n"
                +"expect 1 1 0 | minecraft:redstone_lamp[lit=true]\n";
            BridgeClient.send(BridgePacket.RUN_TEST);
        });
        context.waitTicks(120);
        context.runOnClient(mc->{
            String report=BridgeClient.testReport;
            if(report.isEmpty())throw new AssertionError("No test report arrived. status="+Messages.display(BridgeClient.status));
            if(!report.contains("cases 3 | passed 2 | failed 1"))throw new AssertionError("Wrong verdict:\n"+report);
            if(!report.contains("@case lamp on : PASS")||!report.contains("@case lamp off : PASS"))
                throw new AssertionError("Driving the lever did not work:\n"+report);
            if(!report.contains("@case deliberately wrong : FAIL")||!report.contains("actual   minecraft:redstone_lamp[lit=false]"))
                throw new AssertionError("Failure was not reported with the actual state:\n"+report);
        });
        context.waitForScreen(ReportScreen.class);
        context.takeScreenshot("test-report-live");
        context.setScreen(()->null);
    }
}
