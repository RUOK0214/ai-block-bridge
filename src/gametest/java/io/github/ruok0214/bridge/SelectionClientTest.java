package io.github.ruok0214.bridge;

import io.github.ruok0214.bridge.client.BridgeClient;
import io.github.ruok0214.bridge.client.BridgeScreen;
import io.github.ruok0214.bridge.client.AiPromptScreen;
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
            context.setScreen(()->new io.github.ruok0214.bridge.client.RegionScreen(new BridgeScreen()));
            context.waitForScreen(io.github.ruok0214.bridge.client.RegionScreen.class);
            context.takeScreenshot("selection-area-settings");
            context.setScreen(BridgeScreen::new);
            context.waitForScreen(BridgeScreen.class);
            context.runOnClient(mc->{
                for(String mode:new String[]{"explain","design","fix"}) {
                    String prompt=AiPromptScreen.createPrompt(mode);
                    if(!prompt.contains("minecraft:air")||!prompt.contains("@tick")||!prompt.contains("Attachments:"))
                        throw new AssertionError("Prompt missing syntax or attachment instructions: "+mode);
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
            context.setScreen(()->null);
        }
        context.runOnClient(mc->{
            if(BridgeClient.a!=null||BridgeClient.b!=null)throw new AssertionError("Selection must clear on disconnect");
            if(BridgeClient.recordingAvailable)throw new AssertionError("Retained-recording flag must clear on disconnect");
        });
    }
}
