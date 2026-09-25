/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.MultiLineEditBox
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.ConfirmScreen
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.input.CharacterEvent
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.network.chat.Component
 *  net.minecraft.util.Util
 */
package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.Messages;
import io.github.ruok0214.bridge.RecordingBundle;
import io.github.ruok0214.bridge.RecordingFiles;
import io.github.ruok0214.bridge.Region;
import io.github.ruok0214.bridge.client.AiPromptScreen;
import io.github.ruok0214.bridge.client.BridgeClient;
import io.github.ruok0214.bridge.client.BridgeScreen;
import io.github.ruok0214.bridge.client.RecordingOptionsScreen;
import io.github.ruok0214.bridge.client.RegionScreen;
import io.github.ruok0214.bridge.client.ScriptFiles;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class TimelineScreen
extends WorkspaceScreen {
    private MultiLineEditBox editor;
    private Button start;
    private Button stop;
    private Button load;
    private Button save;
    private Button copy;
    private Button undo;
    private Button bundle;
    private Button options;
    private Button entities;
    private boolean syncing;

    public TimelineScreen() {
        super(Messages.component(Messages.text("ai_block_bridge.timeline.title", new Object[0])));
    }

    private Button button(String title, int x, int y, int w, Runnable action) {
        Button button = this.addRenderableWidget(Button.builder(Messages.component(title), b -> action.run()).bounds(x, y, w, 20).build());
        button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Messages.component(title)));
        return button;
    }

    protected void init() {
        int w = (this.width - 28) / 4;
        initWorkspace(1, true);
        this.start = this.button(Messages.text("ai_block_bridge.button.record_start", new Object[0]), 8, 72, w, () -> this.confirm(Messages.text("ai_block_bridge.confirm.record_title", new Object[0]), Messages.text("ai_block_bridge.confirm.record", new Object[0]), () -> {
            BridgeClient.send(5);
            if (BridgeClient.busy()) {
                this.minecraft.gui.setScreen(null);
            }
        }));
        this.stop = this.button(Messages.text("ai_block_bridge.button.record_stop", new Object[0]), 12 + w, 72, w, () -> BridgeClient.send(6));
        this.options = this.button(Messages.text("ai_block_bridge.record_options.title", new Object[0]), 20 + w * 3, 72, w, () -> this.minecraft.gui.setScreen((Screen)new RecordingOptionsScreen(this)));
        this.entities = this.button(entityLabel(), 16 + w * 2, 72, w, () -> {
            BridgeClient.includeTimelineEntities = !BridgeClient.includeTimelineEntities;
            this.entities.setMessage(Messages.component(entityLabel()));
        });
        this.editor = MultiLineEditBox.builder().setX(8).setY(104).setShowDecorations(false).build(this.font, this.width - 16, Math.max(12, this.height - 182), Messages.component(Messages.text("ai_block_bridge.editor.timeline", new Object[0])));
        this.editor.setCharacterLimit(20000000);
        this.editor.setValue(BridgeClient.timeline);
        this.editor.setValueListener(value -> {
            if (!this.syncing) {
                BridgeClient.replaceTimeline(value);
            }
        });
        this.addRenderableWidget(this.editor);
        this.bundle = this.button(Messages.text("ai_block_bridge.bundle.save", new Object[0]), 8, this.height - 72, (this.width - 20) / 2, this::exportBundle);
        this.bundle.active = BridgeClient.recordingBundle != null;
        this.button(Messages.text("ai_block_bridge.bundle.open_folder", new Object[0]), 12 + (this.width - 20) / 2, this.height - 72, this.width - 20 - (this.width - 20) / 2, this::openRecordingFolder);
        int bottom = this.height - 48;
        this.load = this.button(Messages.text("ai_block_bridge.button.import", new Object[0]), 8, bottom, w, this::importFile);
        this.save = this.button(Messages.text("ai_block_bridge.button.export", new Object[0]), 12 + w, bottom, w, this::exportFile);
        this.copy = this.button(Messages.text("ai_block_bridge.button.copy", new Object[0]), 16 + w * 2, bottom, w, () -> {
            this.minecraft.keyboardHandler.setClipboard(BridgeClient.timeline);
            BridgeClient.timelineStatus = Messages.text("ai_block_bridge.timeline.copied", new Object[0]);
        });
        this.undo = this.button(Messages.text("ai_block_bridge.button.undo_text", new Object[0]), 20 + w * 3, bottom, w, this::undoText);
        tick();
    }

    private void confirm(String title, String message, Runnable yes) {
        this.minecraft.gui.setScreen((Screen)new ConfirmScreen(ok -> {
            this.minecraft.gui.setScreen((Screen)this);
            if (ok) {
                yes.run();
            }
        }, Messages.component(title), Messages.component(message)));
    }

    public void syncText() {
        if (this.editor != null && !this.editor.getValue().equals(BridgeClient.timeline)) {
            this.syncing = true;
            this.editor.setValue(BridgeClient.timeline);
            this.syncing = false;
        }
    }

    private void undoText() {
        BridgeClient.timeline = BridgeClient.timelineHistory.undo(BridgeClient.timeline);
        this.syncText();
    }

    private void importFile() {
        try {
            Path file = ScriptFiles.choose(false, "timeline.txt");
            if (file == null) {
                return;
            }
            String value = ScriptFiles.read(file, 20000000);
            this.confirm(Messages.text("ai_block_bridge.timeline.import_title", new Object[0]), Messages.text("ai_block_bridge.confirm.import_timeline", file.getFileName()), () -> {
                BridgeClient.replaceTimeline(value);
                this.syncText();
                BridgeClient.timelineStatus = Messages.text("ai_block_bridge.loaded", file.getFileName());
            });
        }
        catch (Exception ex) {
            BridgeClient.timelineStatus = Messages.text("ai_block_bridge.load_failed", ex.getMessage());
        }
    }

    private void exportBundle() {
        try {
            RecordingBundle original = BridgeClient.recordingBundle;
            if (original == null) {
                return;
            }
            Path parent = ScriptFiles.chooseDirectory();
            if (parent == null) {
                return;
            }
            Path folder = RecordingFiles.save(parent, original);
            ScriptFiles.rememberRecordingFolder(folder);
            BridgeClient.timelineStatus = Messages.text("ai_block_bridge.saved", folder);
        }
        catch (Exception ex) {
            BridgeClient.timelineStatus = Messages.text("ai_block_bridge.save_failed", ex.getMessage());
        }
    }

    private void openRecordingFolder() {
        try {
            Util.getPlatform().openFile(ScriptFiles.recordingFolder().toFile());
        }
        catch (Exception ex) {
            BridgeClient.timelineStatus = Messages.text("ai_block_bridge.bundle.open_failed", ex.getMessage());
        }
    }

    private void exportFile() {
        try {
            Path file = ScriptFiles.choose(true, "timeline.txt");
            if (file == null) {
                return;
            }
            Runnable write = () -> {
                try {
                    ScriptFiles.write(file, BridgeClient.timeline);
                    BridgeClient.timelineStatus = Messages.text("ai_block_bridge.saved", file.getFileName());
                }
                catch (Exception ex) {
                    BridgeClient.timelineStatus = Messages.text("ai_block_bridge.save_failed", ex.getMessage());
                }
            };
            if (Files.exists(file, new LinkOption[0])) {
                this.confirm(Messages.text("ai_block_bridge.confirm.overwrite_title", new Object[0]), Messages.text("ai_block_bridge.confirm.overwrite", file.getFileName()), write);
            } else {
                write.run();
            }
        }
        catch (Exception ex) {
            BridgeClient.timelineStatus = Messages.text("ai_block_bridge.save_failed", ex.getMessage());
        }
    }

    public void tick() {
        boolean editable;
        tickWorkspace();
        this.bundle.active = !BridgeClient.busy() && !BridgeClient.recording && BridgeClient.recordingBundle != null;
        this.options.active = !BridgeClient.busy() && !BridgeClient.recording && !BridgeClient.recordingAvailable;
        this.entities.active = this.options.active;
        boolean idle = !BridgeClient.busy();
        this.start.active = idle && !BridgeClient.recording && !BridgeClient.recordingAvailable;
        this.stop.active = idle && (BridgeClient.recording || BridgeClient.recordingAvailable);
        this.stop.setMessage(Messages.component(Messages.text(BridgeClient.recordingAvailable ? "ai_block_bridge.recording.retrieve" : "ai_block_bridge.button.record_stop", new Object[0])));
        this.editor.active = editable = idle && !BridgeClient.recording;
        this.load.active = editable;
        this.save.active = idle;
        this.copy.active = idle;
        this.undo.active = editable;
    }

    private String entityLabel() {
        return Messages.text("ai_block_bridge.entities.timeline", Messages.text(BridgeClient.includeTimelineEntities ? "ai_block_bridge.on" : "ai_block_bridge.off", new Object[0]));
    }

    private String filterLabel() {
        return Messages.text("ai_block_bridge.menu.cooldown", Messages.text(BridgeClient.ignoreHopperCooldown ? "ai_block_bridge.on" : "ai_block_bridge.off", new Object[0]));
    }

    public boolean keyPressed(KeyEvent event) {
        if (!BridgeClient.busy() && !BridgeClient.recording && this.editor.isFocused() && (event.modifiers() & 0xA) != 0 && event.key() == 90) {
            this.undoText();
            return true;
        }
        return super.keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        return BridgeClient.recording || BridgeClient.busy() || super.charTyped(event);
    }

    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        drawWorkspace(g, BridgeClient.recording ? "ai_block_bridge.timeline.recording" : "ai_block_bridge.menu.timeline_editor");
        g.outline(7, 103, width - 14, Math.max(12, height - 182) + 2, 0xFF888888);
        g.text(this.font, this.font.plainSubstrByWidth(Messages.display(BridgeClient.timelineStatus), this.width - 16), 8, this.height - 22, -8307, false);
        g.text(this.font, this.font.plainSubstrByWidth(Messages.display(Messages.text("ai_block_bridge.timeline.hint", new Object[0])), this.width - 16), 8, this.height - 10, -5592406, false);
    }

    public boolean isPauseScreen() {
        return false;
    }

}
