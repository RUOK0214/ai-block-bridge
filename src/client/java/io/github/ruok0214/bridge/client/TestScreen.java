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
public final class TestScreen extends WorkspaceScreen {
    private final List<Button> actions = new ArrayList<>();
    private MultiLineEditBox editor;
    private static final io.github.ruok0214.bridge.TextHistory history = new io.github.ruok0214.bridge.TextHistory();
    private Button options;
    private Button results;
    private boolean syncing;
    private int editorHeight;
    private int errorLine;
    private String lastStatus="";

    public TestScreen() { super(Messages.component(Messages.text("ai_block_bridge.test.title"))); }

    private Button button(String title, int x, int y, int w, Runnable action) {
        Button b = addRenderableWidget(Button.builder(Messages.component(title), btn -> action.run()).bounds(x, y, w, 20).build());
        b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Messages.component(title)));
        actions.add(b);
        return b;
    }

    @Override protected void init() {
        actions.clear();
        initWorkspace(2, false);
        int toolWidth = columnWidth(4);
        button(Messages.text("ai_block_bridge.button.validate"), columnX(0, 4), 72, toolWidth, this::validate);
        button(Messages.text("ai_block_bridge.test.run"), columnX(1, 4), 72, toolWidth, this::run);
        results = button(Messages.text("ai_block_bridge.menu.test_results"), columnX(2, 4), 72, toolWidth,
            () -> minecraft.gui.setScreen(ReportScreen.test(this)));
        options = button(Messages.text("ai_block_bridge.record_options.title"), columnX(3, 4), 72, toolWidth,
            () -> minecraft.gui.setScreen(new RecordingOptionsScreen(this)));
        editorHeight = Math.max(12, height - 158);
        editor = MultiLineEditBox.builder().setX(8).setY(104).setShowDecorations(true)
            .build(font, width - 16, editorHeight, Messages.component(Messages.text("ai_block_bridge.menu.test_editor")));
        editor.setCharacterLimit(TestScript.MAX_CHARS);
        editor.setValue(BridgeClient.testScript);
        editor.setValueListener(value -> {
            if (syncing) return;
            history.remember(BridgeClient.testScript);
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
        button(Messages.text("ai_block_bridge.button.undo_text"), 20 + w * 3, bottom, w, this::undoText);
        tick();
    }

    private void undoText() {
        BridgeClient.testScript = history.undo(BridgeClient.testScript);
        syncText();
    }

    private void validate() {
        try {
            String misplaced = ScriptKind.misplaced(BridgeClient.testScript, ScriptKind.TEST);
            if (misplaced != null) { BridgeClient.status = misplaced; return; }
            int cases = TestScript.parse(BridgeClient.testScript, BridgeClient.region()).size();
            BridgeClient.status = Messages.text("ai_block_bridge.ui.test_validated", cases);
        } catch (Exception ex) { BridgeClient.status = ex.getMessage(); }
    }

    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (editor.isFocused()) {
            if (BridgeClient.busy() && event.key() != 256) return true;
            if ((event.modifiers() & 0xA) != 0 && event.key() == 90) { undoText(); return true; }
        }
        return super.keyPressed(event);
    }

    @Override public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        return BridgeClient.busy() || super.charTyped(event);
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
                history.remember(BridgeClient.testScript);
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
        tickWorkspace();
        options.active = enabled && !BridgeClient.recording && !BridgeClient.recordingAvailable;
        results.active = enabled && !BridgeClient.testReport.isEmpty();
        editor.active = enabled;
        if (lastStatus.equals(BridgeClient.status)) return;
        lastStatus = BridgeClient.status;
        errorLine = Messages.lineNumber(lastStatus);
        if (errorLine > 0) EditorErrorMarker.scrollTo(font, editor, BridgeClient.testScript, errorLine, width - 16, editorHeight);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        drawWorkspace(g, "ai_block_bridge.menu.test_editor");
        EditorErrorMarker.extract(g, font, editor, BridgeClient.testScript, errorLine, 8, 104, width - 16, editorHeight);
        g.text(font, font.plainSubstrByWidth(Messages.display(BridgeClient.status), width - 16), 8, height - 22, 0xFFFFDF8D, false);
        g.text(font, font.plainSubstrByWidth(Messages.display(Messages.text("ai_block_bridge.test.hint")), width - 16),
            8, height - 10, 0xFFAAAAAA, false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
