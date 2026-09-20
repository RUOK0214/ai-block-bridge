/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.MultiLineEditBox
 *  net.minecraft.client.gui.components.Tooltip
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.ConfirmScreen
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.input.CharacterEvent
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.network.chat.Component
 */
package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.Messages;
import io.github.ruok0214.bridge.Region;
import io.github.ruok0214.bridge.Script;
import io.github.ruok0214.bridge.client.AiPromptScreen;
import io.github.ruok0214.bridge.client.BridgeClient;
import io.github.ruok0214.bridge.client.RegionScreen;
import io.github.ruok0214.bridge.client.ScriptFiles;
import io.github.ruok0214.bridge.client.TimelineScreen;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class BridgeScreen
extends Screen {
    private final List<Button> actions = new ArrayList<Button>();
    private MultiLineEditBox editor;
    private Button exportUndoButton;
    private Button entities;
    private Button currentTab;
    private boolean syncing;

    public BridgeScreen() {
        super(Messages.component("AI Block Bridge"));
    }

    private Button button(String title, int x, int y, int w, Runnable action) {
        Button b = this.addRenderableWidget(Button.builder(Messages.component(title), btn -> action.run()).bounds(x, y, w, 20).build());
        this.actions.add(b);
        return b;
    }

    protected void init() {
        this.actions.clear();
        this.button(Messages.text("ai_block_bridge.button.close", new Object[0]), this.width - 66, 4, 58, () -> ((BridgeScreen)this).onClose());
        int nav = (this.width - 28) / 4;
        this.currentTab = this.button(Messages.text("ai_block_bridge.menu.structure", new Object[0]), 8, 24, nav, () -> {});
        this.button(Messages.text("ai_block_bridge.menu.timeline", new Object[0]), 12 + nav, 24, nav, () -> this.minecraft.gui.setScreen((Screen)new TimelineScreen()));
        this.button(Messages.text("ai_block_bridge.prompt.open", new Object[0]), 16 + nav * 2, 24, nav, () -> this.minecraft.gui.setScreen((Screen)new AiPromptScreen(this)));
        this.button(Messages.text("ai_block_bridge.button.help", new Object[0]), 20 + nav * 3, 24, nav, () -> this.confirm(Messages.text("ai_block_bridge.help.title", new Object[0]), Messages.text("ai_block_bridge.help.body", Region.MAX_BLOCKS_TEXT), () -> {}));
        this.button(Messages.text("ai_block_bridge.menu.area", new Object[0]), this.width - 100, 48, 92, () -> this.minecraft.gui.setScreen((Screen)new RegionScreen(this)));
        int tools = (this.width - 24) / 3;
        this.button(Messages.text("ai_block_bridge.button.capture", new Object[0]), 8, 72, tools, () -> {
            if (this.checkRegion()) {
                this.confirm(Messages.text("ai_block_bridge.confirm.capture_title", new Object[0]), Messages.text("ai_block_bridge.confirm.capture", new Object[0]), () -> BridgeClient.send(0));
            }
        });
        this.exportUndoButton = this.button(Messages.text("ai_block_bridge.button.undo_capture", new Object[0]), 12 + tools, 72, tools, () -> {
            if (BridgeClient.exportUndo == null) {
                return;
            }
            this.confirm(Messages.text("ai_block_bridge.button.undo_capture", new Object[0]), Messages.text("ai_block_bridge.confirm.undo_capture", new Object[0]), () -> {
                BridgeClient.replace(BridgeClient.exportUndo);
                BridgeClient.exportUndo = null;
                this.syncText();
                BridgeClient.status = Messages.text("ai_block_bridge.capture_undone", new Object[0]);
            });
        });
        this.entities = this.button(this.entityLabel(), 16 + tools * 2, 72, tools, () -> {
            BridgeClient.includeStructureEntities = !BridgeClient.includeStructureEntities;
            this.entities.setMessage(Messages.component(this.entityLabel()));
        });
        this.entities.setTooltip(Tooltip.create((Component)Messages.component(Messages.text("ai_block_bridge.entities.structure_hint", new Object[0]))));
        this.editor = MultiLineEditBox.builder().setX(8).setY(104).setShowDecorations(false).build(this.font, this.width - 16, Math.max(12, this.height - 182), Messages.component(Messages.text("ai_block_bridge.editor.script", new Object[0])));
        this.editor.setCharacterLimit(2000000);
        this.editor.setValue(BridgeClient.script);
        this.editor.setValueListener(value -> {
            if (!this.syncing) {
                BridgeClient.replace(value);
            }
        });
        this.addRenderableWidget(this.editor);
        int w = (this.width - 28) / 4;
        int bottom = this.height - 48;
        this.button(Messages.text("ai_block_bridge.button.import", new Object[0]), 8, bottom, w, this::importFile);
        this.button(Messages.text("ai_block_bridge.button.export", new Object[0]), 12 + w, bottom, w, this::exportFile);
        this.button(Messages.text("ai_block_bridge.button.copy", new Object[0]), 16 + w * 2, bottom, w, () -> {
            this.minecraft.keyboardHandler.setClipboard(BridgeClient.script);
            BridgeClient.status = Messages.text("ai_block_bridge.copied", new Object[0]);
        });
        this.button(Messages.text("ai_block_bridge.button.undo_text", new Object[0]), 20 + w * 3, bottom, w, this::undoText);
        int world = (this.width - 24) / 3;
        this.button(Messages.text("ai_block_bridge.button.validate", new Object[0]), 8, this.height - 72, world, () -> {
            try {
                BridgeClient.status = Messages.text("ai_block_bridge.validated", Script.parse(BridgeClient.script, BridgeClient.region()).size());
            }
            catch (Exception ex) {
                BridgeClient.status = ex.getMessage();
            }
        });
        this.button(Messages.text("ai_block_bridge.button.paste", new Object[0]), 12 + world, this.height - 72, world, () -> {
            try {
                Region r = BridgeClient.region();
                int count = Script.parse(BridgeClient.script, r).size();
                this.confirm(Messages.text("ai_block_bridge.confirm.paste_title", new Object[0]), Messages.text("ai_block_bridge.confirm.paste", r.description(), count), () -> BridgeClient.send(1));
            }
            catch (Exception ex) {
                BridgeClient.status = ex.getMessage();
            }
        });
        this.button(Messages.text("ai_block_bridge.button.undo_paste", new Object[0]), 16 + world * 2, this.height - 72, world, () -> this.confirm(Messages.text("ai_block_bridge.confirm.undo_title", new Object[0]), Messages.text("ai_block_bridge.confirm.undo", new Object[0]), () -> BridgeClient.send(2)));
        this.currentTab.active = false;
    }

    private String entityLabel() {
        return Messages.text("ai_block_bridge.entities.structure", Messages.text(BridgeClient.includeStructureEntities ? "ai_block_bridge.on" : "ai_block_bridge.off", new Object[0]));
    }

    private boolean checkRegion() {
        try {
            BridgeClient.region();
            return true;
        }
        catch (Exception ex) {
            BridgeClient.status = ex.getMessage();
            return false;
        }
    }

    private void confirm(String title, String message, Runnable yes) {
        this.minecraft.gui.setScreen((Screen)new ConfirmScreen(accepted -> {
            this.minecraft.gui.setScreen((Screen)this);
            if (accepted) {
                yes.run();
            }
        }, Messages.component(title), Messages.component(message)));
    }

    public void syncText() {
        if (this.editor != null && !this.editor.getValue().equals(BridgeClient.script)) {
            this.syncing = true;
            this.editor.setValue(BridgeClient.script);
            this.syncing = false;
        }
    }

    private void undoText() {
        BridgeClient.script = BridgeClient.history.undo(BridgeClient.script);
        this.syncText();
    }

    private void importFile() {
        try {
            Path file = ScriptFiles.choose(false);
            if (file == null) {
                return;
            }
            String text = ScriptFiles.read(file);
            this.confirm(Messages.text("ai_block_bridge.button.import", new Object[0]), Messages.text("ai_block_bridge.confirm.import", file.getFileName()), () -> {
                BridgeClient.replace(text);
                this.syncText();
                BridgeClient.status = Messages.text("ai_block_bridge.loaded", file.getFileName());
            });
        }
        catch (Exception ex) {
            BridgeClient.status = Messages.text("ai_block_bridge.load_failed", ex.getMessage());
        }
    }

    private void exportFile() {
        try {
            Path file = ScriptFiles.choose(true);
            if (file == null) {
                return;
            }
            Runnable save = () -> {
                try {
                    ScriptFiles.write(file, BridgeClient.script);
                    BridgeClient.status = Messages.text("ai_block_bridge.saved", file.getFileName());
                }
                catch (Exception ex) {
                    BridgeClient.status = Messages.text("ai_block_bridge.save_failed", ex.getMessage());
                }
            };
            if (Files.exists(file, new LinkOption[0])) {
                this.confirm(Messages.text("ai_block_bridge.confirm.overwrite_title", new Object[0]), Messages.text("ai_block_bridge.confirm.overwrite", file.getFileName()), save);
            } else {
                save.run();
            }
        }
        catch (Exception ex) {
            BridgeClient.status = Messages.text("ai_block_bridge.save_failed", ex.getMessage());
        }
    }

    public void tick() {
        boolean enabled = !BridgeClient.busy();
        for (Button button : this.actions) {
            button.active = enabled;
        }
        this.exportUndoButton.active = enabled && BridgeClient.exportUndo != null;
        this.currentTab.active = false;
        this.editor.active = enabled;
    }

    public boolean keyPressed(KeyEvent event) {
        if (this.editor.isFocused()) {
            if (BridgeClient.busy() && event.key() != 256) {
                return true;
            }
            if ((event.modifiers() & 0xA) != 0 && event.key() == 90) {
                this.undoText();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        if (BridgeClient.busy()) {
            return true;
        }
        return super.charTyped(event);
    }

    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        String region;
        super.extractRenderState(g, mx, my, delta);
        g.text(this.font, "AI BLOCK BRIDGE \u00b7 26.2", 8, 7, -1, true);
        try {
            region = BridgeClient.region().description();
        }
        catch (Exception ex) {
            region = ex.getMessage();
        }
        g.text(this.font, this.font.plainSubstrByWidth(Messages.display(region), this.width - 116), 8, 54, -3355444, false);
        g.text(this.font, Messages.component(Messages.text("ai_block_bridge.menu.structure_editor", new Object[0])), 8, 95, -6431745, false);
        g.text(this.font, this.font.plainSubstrByWidth(Messages.display(BridgeClient.status), this.width - 16), 8, this.height - 22, -8307, false);
        g.text(this.font, this.font.plainSubstrByWidth(Messages.display(Messages.text("ai_block_bridge.editor.hint", new Object[0])), this.width - 16), 8, this.height - 10, -5592406, false);
    }

    public boolean isPauseScreen() {
        return false;
    }
}

