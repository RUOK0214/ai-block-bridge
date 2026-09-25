package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.Messages;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;

/** Read-back for settle and test results. Copying never sends data. */
public final class ReportScreen extends Screen {
    private final Screen parent;
    private final Supplier<String> body;
    private final String hintKey;
    private final String copiedKey;
    private String status = "";

    public ReportScreen(Screen parent, String titleKey, String hintKey, String copiedKey, Supplier<String> body) {
        super(Messages.component(Messages.text(titleKey)));
        this.parent = parent;
        this.body = body;
        this.hintKey = hintKey;
        this.copiedKey = copiedKey;
    }

    public static ReportScreen settle(Screen parent) {
        return new ReportScreen(parent, "ai_block_bridge.settle.title", "ai_block_bridge.settle.hint",
            "ai_block_bridge.settle.copied", () -> BridgeClient.settleReport);
    }

    public static ReportScreen test(Screen parent) {
        return new ReportScreen(parent, "ai_block_bridge.test.title", "ai_block_bridge.test.hint",
            "ai_block_bridge.test.copied", () -> BridgeClient.testReport);
    }

    @Override protected void init() {
        MultiLineEditBox editor = MultiLineEditBox.builder().setX(8).setY(29).setShowDecorations(true)
            .build(font, width - 16, Math.max(30, height - 93), title);
        editor.setCharacterLimit(2000000);
        editor.setValue(body.get());
        addRenderableWidget(editor);
        addRenderableWidget(Button.builder(Messages.component(Messages.text("ai_block_bridge.button.copy")), b -> {
            minecraft.keyboardHandler.setClipboard(body.get());
            status = Messages.text(copiedKey);
        }).bounds(8, height - 56, (width - 20) / 2, 20).build());
        addRenderableWidget(Button.builder(Messages.component(Messages.text("ai_block_bridge.menu.back")),
            b -> onClose()).bounds(12 + (width - 20) / 2, height - 56, (width - 20) / 2, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.text(font, title, 8, 9, 0xFFFFFFFF, true);
        String hint = status.isEmpty() ? Messages.text(hintKey) : status;
        g.text(font, font.plainSubstrByWidth(Messages.display(hint), width - 16), 8, height - 24, 0xFFFFDF8D, false);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
