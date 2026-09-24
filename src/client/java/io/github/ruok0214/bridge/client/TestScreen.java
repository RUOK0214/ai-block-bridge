package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.BridgePacket;
import io.github.ruok0214.bridge.Messages;
import io.github.ruok0214.bridge.Region;
import io.github.ruok0214.bridge.ScriptKind;
import io.github.ruok0214.bridge.TestScript;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;

/** Editor for test scripts that drive inputs and check outputs. */
public final class TestScreen extends Screen {
    private final List<Button> actions = new ArrayList<>();
    private MultiLineEditBox editor;
    private Button currentTab;
    private Button results;
    private boolean syncing;
    private int editorHeight;
    private int errorLine;
    private String lastStatus="";

    public TestScreen() { super(Messages.component(Messages.text("ai_block_bridge.test.title"))); }

    private Button button(String title, int x, int y, int w, Runnable action) {
        Button b = addRenderableWidget(Button.builder(Messages.component(title), btn -> action.run()).bounds(x, y, w, 20).build());
        actions.add(b);
        return b;
    }

    @Override protected void init() {
        actions.clear();
        button(Messages.text("ai_block_bridge.button.close"), width - 66, 4, 58, this::onClose);
        int nav = (width - 32) / 5;
        button(Messages.text("ai_block_bridge.menu.structure"), 8, 24, nav, () -> minecraft.gui.setScreen(new BridgeScreen()));
        button(Messages.text("ai_block_bridge.menu.timeline"), 12 + nav, 24, nav, () -> minecraft.gui.setScreen(new TimelineScreen()));
        currentTab = button(Messages.text("ai_block_bridge.menu.test"), 16 + nav * 2, 24, nav, () -> {});
        button(Messages.text("ai_block_bridge.prompt.open"), 20 + nav * 3, 24, nav, () -> minecraft.gui.setScreen(new AiPromptScreen(this)));
        results = button(Messages.text("ai_block_bridge.menu.test_results"), 24 + nav * 4, 24, nav,
            () -> minecraft.gui.setScreen(ReportScreen.test(this)));

        editorHeight = Math.max(12, height - 142);
        editor = MultiLineEditBox.builder().setX(8).setY(64).setShowDecorations(false)
            .build(font, width - 16, editorHeight, Messages.component(Messages.text("ai_block_bridge.menu.test_editor")));
        editor.setCharacterLimit(TestScript.MAX_CHARS);
        editor.setValue(BridgeClient.testScript);
        editor.setValueListener(value -> {
            if (syncing) return;
            BridgeClient.testScript = value;
            errorLine = 0;
        });
        addRenderableWidget(editor);

        int w = (width - 28) / 4;
        int bottom = height - 48;
        button(Messages.text("ai_block_bridge.button.import"), 8, bottom, w, this::importFile);
        button(Messages.text("ai_block_bridge.button.export"), 12 + w, bottom, w, this::exportFile);
        button(Messages.text("ai_block_bridge.button.copy"), 16 + w * 2, bottom, w, () -> {
            minecraft.keyboardHandler.setClipboard(BridgeClient.testScript);
            BridgeClient.status = Messages.text("ai_block_bridge.copied");
        });
        button(Messages.text("ai_block_bridge.test.run"), 20 + w * 3, bottom, w, this::run);
        currentTab.active = false;
    }

    private void run() {
        try {
            String misplaced = ScriptKind.misplaced(BridgeClient.testScript, ScriptKind.TEST);
            if (misplaced != null) { BridgeClient.status = misplaced; return; }
            Region r = BridgeClient.region();
            int cases = TestScript.parse(BridgeClient.testScript, r).size();
            confirm(Messages.text("ai_block_bridge.confirm.test_title"), Messages.text("ai_block_bridge.confirm.test", r.description(), cases),
                () -> BridgeClient.send(BridgePacket.RUN_TEST));
        }
        catch (Exception ex) { BridgeClient.status = ex.getMessage(); }
    }

    private void confirm(String title, String message, Runnable yes) {
        minecraft.gui.setScreen(new ConfirmScreen(accepted -> {
            minecraft.gui.setScreen(this);
            if (accepted) yes.run();
        }, Messages.component(title), Messages.component(message)));
    }

    public void syncText() {
        if (editor != null && !editor.getValue().equals(BridgeClient.testScript)) {
            syncing = true;
            editor.setValue(BridgeClient.testScript);
            syncing = false;
        }
    }

    private void importFile() {
        try {
            Path file = ScriptFiles.choose(false);
            if (file == null) return;
            String text = ScriptFiles.read(file);
            String misplaced = ScriptKind.misplaced(text, ScriptKind.TEST);
            if (misplaced != null) { BridgeClient.status = misplaced; return; }
            confirm(Messages.text("ai_block_bridge.button.import"), Messages.text("ai_block_bridge.confirm.import", file.getFileName()), () -> {
                BridgeClient.testScript = text;
                syncText();
                BridgeClient.status = Messages.text("ai_block_bridge.loaded", file.getFileName());
            });
        }
        catch (Exception ex) { BridgeClient.status = Messages.text("ai_block_bridge.load_failed", ex.getMessage()); }
    }

    private void exportFile() {
        try {
            Path file = ScriptFiles.choose(true);
            if (file == null) return;
            Runnable save = () -> {
                try {
                    ScriptFiles.write(file, BridgeClient.testScript);
                    BridgeClient.status = Messages.text("ai_block_bridge.saved", file.getFileName());
                }
                catch (Exception ex) { BridgeClient.status = Messages.text("ai_block_bridge.save_failed", ex.getMessage()); }
            };
            if (Files.exists(file)) confirm(Messages.text("ai_block_bridge.confirm.overwrite_title"),
                Messages.text("ai_block_bridge.confirm.overwrite", file.getFileName()), save);
            else save.run();
        }
        catch (Exception ex) { BridgeClient.status = Messages.text("ai_block_bridge.save_failed", ex.getMessage()); }
    }

    @Override public void tick() {
        boolean enabled = !BridgeClient.busy();
        for (Button button : actions) button.active = enabled;
        currentTab.active = false;
        results.active = enabled && !BridgeClient.testReport.isEmpty();
        editor.active = enabled;
        if (lastStatus.equals(BridgeClient.status)) return;
        lastStatus = BridgeClient.status;
        errorLine = Messages.lineNumber(lastStatus);
        if (errorLine > 0) EditorErrorMarker.scrollTo(font, editor, BridgeClient.testScript, errorLine, width - 16, editorHeight);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        String region;
        super.extractRenderState(g, mx, my, delta);
        g.text(font, title, 8, 9, 0xFFFFFFFF, true);
        try { region = BridgeClient.region().description(); }
        catch (Exception ex) { region = ex.getMessage(); }
        g.text(font, font.plainSubstrByWidth(Messages.display(region), width - 16), 8, 50, 0xFFCCCCCC, false);
        EditorErrorMarker.extract(g, font, editor, BridgeClient.testScript, errorLine, 8, 64, width - 16, editorHeight);
        g.text(font, font.plainSubstrByWidth(Messages.display(BridgeClient.status), width - 16), 8, height - 22, 0xFFFFDF8D, false);
        g.text(font, font.plainSubstrByWidth(Messages.display(Messages.text("ai_block_bridge.test.hint")), width - 16),
            8, height - 10, 0xFFAAAAAA, false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
