package io.github.ruok0214.bridge;

import io.github.ruok0214.bridge.client.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;

/** Exercises menu navigation and settings on the real client, including compact GUI layouts. */
public final class MenuClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 720);
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(mc -> {
                BridgeClient.a = BlockPos.ZERO;
                BridgeClient.b = new BlockPos(3, 3, 3);
                BridgeClient.script = "0 0 0 | minecraft:stone\n";
                BridgeClient.timeline = "# retained timeline\n";
                BridgeClient.testScript = "@case retained test\nwait 1\n";
                BridgeClient.recording = BridgeClient.recordingAvailable = false;
                BridgeClient.paletteFormat = false;
                mc.gui.setScreen(new BridgeScreen());
                checkBounds(mc.gui.screen());
                click(find(mc.gui.screen(), WorkspaceScreen.formatLabel()));
                if (!BridgeClient.paletteFormat || !BridgeClient.script.contains("minecraft:stone"))
                    throw new AssertionError("Format toggle must affect future captures, not the draft");
                click(find(mc.gui.screen(), Messages.text("ai_block_bridge.menu.timeline")));
                if (!(mc.gui.screen() instanceof TimelineScreen)) throw new AssertionError("Timeline navigation");
                checkBounds(mc.gui.screen());
                click(find(mc.gui.screen(), Messages.text("ai_block_bridge.menu.test")));
                if (!(mc.gui.screen() instanceof TestScreen)) throw new AssertionError("Test navigation from timeline");
                checkBounds(mc.gui.screen());
                click(find(mc.gui.screen(), Messages.text("ai_block_bridge.menu.area")));
                if (!(mc.gui.screen() instanceof RegionScreen)) throw new AssertionError("Region access from test");
                mc.gui.screen().onClose();
                if (!(mc.gui.screen() instanceof TestScreen)) throw new AssertionError("Region return target");
                click(find(mc.gui.screen(), Messages.text("ai_block_bridge.record_options.title")));
                if (!(mc.gui.screen() instanceof RecordingOptionsScreen)) throw new AssertionError("Options navigation");
                checkBounds(mc.gui.screen());
                click(find(mc.gui.screen(), Messages.text("ai_block_bridge.ui.options.entities")));
                BridgeClient.includeTimelineEntities = false;
                mc.gui.screen().tick();
                String delta = Messages.text("ai_block_bridge.record_options.delta", Messages.text(BridgeClient.entityDelta ? "ai_block_bridge.on" : "ai_block_bridge.off"));
                if (find(mc.gui.screen(), delta).active) throw new AssertionError("Entity details must be disabled");
                click(find(mc.gui.screen(), Messages.text("ai_block_bridge.ui.options.general")));
                BridgeClient.recordingAvailable = true;
                mc.gui.screen().tick();
                if (find(mc.gui.screen(), WorkspaceScreen.formatLabel()).active) throw new AssertionError("Retained recording must lock options");
                BridgeClient.recordingAvailable = false;
                mc.gui.screen().tick();
                mc.gui.screen().onClose();
                click(find(mc.gui.screen(), Messages.text("ai_block_bridge.menu.structure")));
                if (!BridgeClient.script.equals("0 0 0 | minecraft:stone\n") || !BridgeClient.timeline.equals("# retained timeline\n")
                    || !BridgeClient.testScript.equals("@case retained test\nwait 1\n")) throw new AssertionError("Navigation lost editor drafts");
            });
            context.takeScreenshot("menu-structure-compact");
            context.setScreen(() -> new RecordingOptionsScreen(new BridgeScreen()));
            context.takeScreenshot("menu-options-general");
            context.runOnClient(mc -> click(find(mc.gui.screen(), Messages.text("ai_block_bridge.ui.options.entities"))));
            context.takeScreenshot("menu-options-entities");
            context.getInput().resizeWindow(1600, 900);
            context.setScreen(BridgeScreen::new);
            context.runOnClient(mc -> checkBounds(mc.gui.screen()));
            context.takeScreenshot("menu-structure-wide");
            context.setScreen(() -> null);
        }
    }

    private static Button find(Screen screen, String label) {
        String shown = Messages.display(label);
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
            .filter(b -> b.getMessage().getString().equals(shown)).findFirst()
            .orElseThrow(() -> new AssertionError("Missing menu button: " + shown));
    }

    private static void click(Button button) {
        if (!button.active) throw new AssertionError("Inactive menu button: " + button.getMessage());
        // Button's activation signature varies across Minecraft minor releases.
        try {
            for (var method : Button.class.getMethods()) if (method.getName().equals("onPress") && method.getParameterCount() <= 1) {
                method.invoke(button, new Object[method.getParameterCount()]);
                return;
            }
            throw new AssertionError("No button activation method");
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }

    private static void checkBounds(Screen screen) {
        var widgets = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
        for (int i = 0; i < widgets.size(); i++) {
            var a = widgets.get(i);
            if (a.getX() < 0 || a.getY() < 0 || a.getX() + a.getWidth() > screen.width || a.getY() + a.getHeight() > screen.height)
                throw new AssertionError("Widget outside screen: " + a.getMessage());
            for (int j = i + 1; j < widgets.size(); j++) {
                var b = widgets.get(j);
                if (a.getX() < b.getX() + b.getWidth() && a.getX() + a.getWidth() > b.getX()
                    && a.getY() < b.getY() + b.getHeight() && a.getY() + a.getHeight() > b.getY())
                    throw new AssertionError("Overlapping widgets: " + a.getMessage() + " / " + b.getMessage());
            }
        }
    }
}
