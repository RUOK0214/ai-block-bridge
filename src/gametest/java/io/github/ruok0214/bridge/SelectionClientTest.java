package io.github.ruok0214.bridge;

import io.github.ruok0214.bridge.client.BridgeClient;
import io.github.ruok0214.bridge.client.BridgeScreen;
import io.github.ruok0214.bridge.client.AiPromptScreen;
import io.github.ruok0214.bridge.client.RegionScreen;
import io.github.ruok0214.bridge.client.ReportScreen;
import io.github.ruok0214.bridge.client.TestScreen;
import io.github.ruok0214.bridge.client.TimelineScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

/** Actual rendered screenshots: normal, behind a wall, hidden, and partial selection. */
public final class SelectionClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280,720);
        try(var world=context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode spectator @a");
            world.getServer().runCommand("tp @a 12 7 15");
            world.getServer().runCommand("fill -2 0 -2 8 0 8 minecraft:stone");
            world.getServer().runCommand("fill 0 1 0 4 3 4 minecraft:bricks");
            context.waitTicks(10);
            context.getInput().lookAt(new BlockPos(2,2,2));
            context.runOnClient(mc->{
                BridgeClient.a=new BlockPos(4,1,0);
                BridgeClient.b=new BlockPos(0,4,5);
                BridgeClient.dimension=mc.level.dimension().identifier().toString();
                BridgeClient.showSelection=true;
            });
            world.getConnection().waitForChunksRender();
            context.waitTicks(5);
            context.takeScreenshot("selection-visible");
            world.getServer().runCommand("fill -2 0 8 14 9 8 minecraft:stone");
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            context.takeScreenshot("selection-through-wall");
            context.getInput().pressKey(GLFW.GLFW_KEY_BACKSLASH);
            context.waitTicks(3);
            context.runOnClient(mc->{if(BridgeClient.showSelection)throw new AssertionError("Overlay hotkey did not hide selection");});
            context.takeScreenshot("selection-hidden");
            context.getInput().pressKey(GLFW.GLFW_KEY_BACKSLASH);
            context.runOnClient(mc->{
                if(!BridgeClient.showSelection)throw new AssertionError("Overlay hotkey did not restore selection");
                BridgeClient.b=null;
            });
            context.waitTicks(3);
            context.takeScreenshot("selection-first-corner");
            context.runOnClient(mc->{
                BridgeClient.timeline="# AI Block Bridge Timeline v1\n\n@tick 1\n0 0 0 | minecraft:lever[powered=true]\n";
                BridgeClient.timelineStatus=Messages.text("ai_block_bridge.timeline.complete");
                String error=Messages.text("ai_block_bridge.error", Messages.text("ai_block_bridge.error.line", 3,
                    Messages.text("ai_block_bridge.error.duplicate")));
                if(!Messages.display(error).equals("Error: Line 3: Duplicate coordinates."))
                    throw new AssertionError("Nested server error was not localized: "+Messages.display(error));
                mc.gui.setScreen(new TimelineScreen());
            });
            context.waitForScreen(TimelineScreen.class);
            context.takeScreenshot("timeline-editor");
            context.setScreen(BridgeScreen::new);
            context.waitForScreen(BridgeScreen.class);
            context.takeScreenshot("script-editor-english");
            context.runOnClient(mc->{
                for(String mode:new String[]{"explain","design","fix"}) {
                    String prompt=AiPromptScreen.createPrompt(mode);
                    if(!prompt.contains("minecraft:air")||!prompt.contains("@tick")||!prompt.contains("Attachments:"))
                        throw new AssertionError("Prompt missing syntax or attachment instructions: "+mode);
                    if(!prompt.contains("@case")||!prompt.contains("expect 1 1 0")||!prompt.contains("settle check"))
                        throw new AssertionError("Prompt missing test syntax or settle guidance: "+mode);
                    if(prompt.contains("AI_BLOCK_BRIDGE_MESSAGE:")||prompt.contains("ai_block_bridge.prompt."))
                        throw new AssertionError("Untranslated prompt: "+mode);
                }
                mc.gui.setScreen(new AiPromptScreen(new BridgeScreen()));
            });
            context.waitForScreen(AiPromptScreen.class);
            context.takeScreenshot("ai-request-english");
            context.runOnClient(mc->{
                BridgeClient.recording=true;
                String prior=BridgeClient.timeline;
                BridgeClient.recordingStopped(Messages.text("ai_block_bridge.recording.stop.time",6000));
                if(BridgeClient.recording||!BridgeClient.recordingAvailable||!BridgeClient.timeline.equals(prior))
                    throw new AssertionError("Stop notice lost data or left the recorder running");
                mc.gui.setScreen(new TimelineScreen());
            });
            context.waitTicks(2);
            context.takeScreenshot("recording-limit-notice");
            context.runOnClient(mc->{
                BridgeClient.testScript="# AI Block Bridge Test v1\n@case lamp on\n"
                    +"set 0 1 0 | minecraft:lever[powered=true]\nwait 10\n"
                    +"expect 1 1 0 | minecraft:redstone_lamp[lit=true]\n";
                BridgeClient.testReport=TestReport.render(Region.of(0,0,0,4,4,4),java.util.List.of(
                    new TestReport.Result("lamp on",java.util.List.of()),
                    new TestReport.Result("lamp off",java.util.List.of(new TestReport.Failure(1,1,0,
                        "minecraft:redstone_lamp[lit=false]","minecraft:redstone_lamp[lit=true]",8)))),null);
                mc.gui.setScreen(new TestScreen());
            });
            context.waitForScreen(TestScreen.class);
            context.takeScreenshot("test-editor");
            // The reported line number is only useful if the editor also shows which line it means.
            context.runOnClient(mc->{
                BridgeClient.testScript="# AI Block Bridge Script v1\n# size: 8 3 1\n#\n# a placement script\n"
                    +"# loaded into the test tab by mistake\n#\n0 0 0 | minecraft:stone\n1 0 0 | minecraft:stone\n";
                BridgeClient.status=Messages.text("ai_block_bridge.error.line",7,
                    Messages.text("ai_block_bridge.error.test_case_required"));
                mc.gui.setScreen(new TestScreen());
            });
            context.waitTicks(3);
            context.takeScreenshot("test-error-marker");
            // A placement script in the test editor should name the right screen, not a line number.
            context.runOnClient(mc->{
                String placement="# AI Block Bridge Script v1\n# size: 8 3 1\n0 0 0 | minecraft:stone\n";
                String message=ScriptKind.misplaced(placement,ScriptKind.TEST);
                if(message==null)throw new AssertionError("A placement script was not recognised as misplaced");
                String shown=Messages.display(message);
                if(shown.contains("ai_block_bridge."))throw new AssertionError("Untranslated notice: "+shown);
                if(!shown.contains("Structure"))throw new AssertionError("Notice does not name the structure screen: "+shown);
                BridgeClient.testScript=placement;
                BridgeClient.status=message;
                mc.gui.setScreen(new TestScreen());
            });
            context.waitTicks(3);
            context.takeScreenshot("test-wrong-file");
            context.runOnClient(mc->{
                BridgeClient.a=new BlockPos(4,1,0);
                BridgeClient.b=new BlockPos(0,4,5);
                mc.gui.setScreen(new RegionScreen(new BridgeScreen()));
            });
            context.waitForScreen(RegionScreen.class);
            context.takeScreenshot("region-before-move");
            context.runOnClient(mc->mc.gui.setScreen(ReportScreen.test(null)));
            context.waitForScreen(ReportScreen.class);
            context.takeScreenshot("test-report");
            context.setScreen(()->null);
        }
        context.runOnClient(mc->{
            if(BridgeClient.a!=null||BridgeClient.b!=null)throw new AssertionError("Selection must clear on disconnect");
            if(BridgeClient.recordingAvailable)throw new AssertionError("Retained-recording flag must clear on disconnect");
        });
    }
}
