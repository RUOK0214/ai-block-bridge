package io.github.ruok0214.bridge;

import io.github.ruok0214.bridge.client.BridgeClient;
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
        }
        context.runOnClient(mc->{
            if(BridgeClient.a!=null||BridgeClient.b!=null)throw new AssertionError("Selection must clear on disconnect");
        });
    }
}
