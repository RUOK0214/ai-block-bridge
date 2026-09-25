package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.Messages;
import io.github.ruok0214.bridge.Region;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared navigation, region controls and responsive action rows for the three editors. */
public abstract class WorkspaceScreen extends Screen {
    private final Button[] tabs = new Button[4];
    private Button area;
    private Button format;
    private int selected;

    protected WorkspaceScreen(Component title) { super(title); }

    protected void initWorkspace(int selected, boolean showFormat) {
        this.selected = selected;
        control("ai_block_bridge.button.help", width - 128, 4, 58, () -> minecraft.gui.setScreen(new ConfirmScreen(
            ok -> minecraft.gui.setScreen(this), Messages.component(Messages.text("ai_block_bridge.help.title")),
            Messages.component(Messages.text("ai_block_bridge.help.body", Region.MAX_BLOCKS_TEXT)))));
        control("ai_block_bridge.button.close", width - 66, 4, 58, this::onClose);
        String[] keys = {"ai_block_bridge.menu.structure", "ai_block_bridge.menu.timeline", "ai_block_bridge.menu.test", "ai_block_bridge.prompt.open"};
        Runnable[] open = {() -> minecraft.gui.setScreen(new BridgeScreen()), () -> minecraft.gui.setScreen(new TimelineScreen()),
            () -> minecraft.gui.setScreen(new TestScreen()), () -> minecraft.gui.setScreen(new AiPromptScreen(this))};
        for (int i = 0; i < tabs.length; i++) tabs[i] = control(keys[i], columnX(i, 4), 26, columnWidth(4), open[i]);
        area = control("ai_block_bridge.menu.area", width - 100, 50, 92, () -> minecraft.gui.setScreen(new RegionScreen(this)));
        format = showFormat ? addRenderableWidget(Button.builder(Messages.component(formatLabel()), b -> {
            BridgeClient.paletteFormat = !BridgeClient.paletteFormat;
            b.setMessage(Messages.component(formatLabel()));
        }).bounds(width - 218, 50, 114, 20).build()) : null;
        if (format != null) format.setTooltip(Tooltip.create(Messages.component(Messages.text("ai_block_bridge.ui.format_hint"))));
        tickWorkspace();
    }

    private Button control(String key, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(Messages.component(Messages.text(key)), b -> action.run()).bounds(x, y, w, 20).build());
    }

    protected int columnWidth(int count) { return (width - 16 - 4 * (count - 1)) / count; }
    protected int columnX(int index, int count) { return 8 + index * (columnWidth(count) + 4); }
    protected int executionTop() { return height - (width < 520 ? 100 : 76); }
    protected int executionX(int index) { return columnX(index % (width < 520 ? 3 : 5), width < 520 ? 3 : 5); }
    protected int executionY(int index) { return executionTop() + (width < 520 ? index / 3 * 24 : 0); }
    protected int executionWidth() { return columnWidth(width < 520 ? 3 : 5); }
    protected int structureEditorHeight() { return Math.max(12, executionTop() - 112); }

    protected void tickWorkspace() {
        boolean idle = !BridgeClient.busy();
        for (int i = 0; i < tabs.length; i++) tabs[i].active = idle && i != selected;
        area.active = idle && !BridgeClient.recording && !BridgeClient.recordingAvailable;
        if (format != null) {
            format.active = area.active;
            format.setMessage(Messages.component(formatLabel()));
        }
    }

    public static String formatLabel() {
        return Messages.text("ai_block_bridge.ui.format", Messages.text(BridgeClient.paletteFormat
            ? "ai_block_bridge.ui.format.coded" : "ai_block_bridge.ui.format.plain"));
    }

    protected void drawWorkspace(GuiGraphicsExtractor g, String editorKey) {
        g.text(font, font.plainSubstrByWidth("AI BLOCK BRIDGE", width - 144), 8, 9, 0xFFFFFFFF, true);
        int x = columnX(selected, 4);
        g.fill(x, 46, x + columnWidth(4), 48, 0xFF82D9CF);
        String region;
        try { region = BridgeClient.region().description(); }
        catch (Exception ex) { region = ex.getMessage(); }
        int available = width - (format == null ? 116 : 234);
        g.text(font, font.plainSubstrByWidth(Messages.display(region), Math.max(1, available)), 8, 56, 0xFFCCCCCC, false);
        g.text(font, font.plainSubstrByWidth(Messages.display(Messages.text(editorKey)), width - 16), 8, 95, 0xFF82D9CF, false);
    }
}
