/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.EditBox
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.core.BlockPos
 *  net.minecraft.network.chat.Component
 */
package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.Messages;
import io.github.ruok0214.bridge.Region;
import io.github.ruok0214.bridge.client.BridgeClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class RegionScreen
extends Screen {
    private final Screen parent;
    private final EditBox[] fields = new EditBox[6];
    private Button apply;
    private String notice = "";

    public RegionScreen(Screen parent) {
        super(Messages.component(Messages.text("ai_block_bridge.menu.area", new Object[0])));
        this.parent = parent;
    }

    private void button(String key, int x, int y, int w, Runnable action) {
        this.addRenderableWidget(Button.builder(Messages.component(Messages.text(key, new Object[0])), b -> action.run()).bounds(x, y, w, 20).build());
    }

    protected void init() {
        int fieldWidth = (this.width - 112) / 3;
        for (int row = 0; row < 2; ++row) {
            int[] nArray;
            BlockPos p;
            BlockPos blockPos = p = row == 0 ? BridgeClient.a : BridgeClient.b;
            if (p == null) {
                int[] nArray2 = new int[3];
                nArray2[0] = 0;
                nArray2[1] = 0;
                nArray = nArray2;
                nArray2[2] = 0;
            } else {
                int[] nArray3 = new int[3];
                nArray3[0] = p.getX();
                nArray3[1] = p.getY();
                nArray = nArray3;
                nArray3[2] = p.getZ();
            }
            int[] initial = nArray;
            for (int col = 0; col < 3; ++col) {
                int i = row * 3 + col;
                String value = this.fields[i] == null ? Integer.toString(initial[col]) : this.fields[i].getValue();
                EditBox f = new EditBox(this.font, 40 + col * (fieldWidth + 3), 52 + row * 32, fieldWidth, 20, Messages.component(Messages.text("ai_block_bridge.corner_field", row + 1, Character.valueOf("XYZ".charAt(col)))));
                f.setMaxLength(11);
                f.setValue(value);
                this.fields[i] = this.addRenderableWidget(f);
            }
            int r = row;
            this.button("ai_block_bridge.button.position", this.width - 57, 52 + row * 32, 49, () -> {
                if (this.minecraft.player == null) {
                    return;
                }
                BlockPos here = this.minecraft.player.blockPosition();
                this.fields[r * 3].setValue("" + here.getX());
                this.fields[r * 3 + 1].setValue("" + here.getY());
                this.fields[r * 3 + 2].setValue("" + here.getZ());
            });
        }
        this.apply = this.addRenderableWidget(Button.builder(Messages.component(Messages.text("ai_block_bridge.button.apply", new Object[0])), b -> this.apply()).bounds(8, 128, (this.width - 20) / 2, 20).build());
        this.button("ai_block_bridge.menu.back", 12 + (this.width - 20) / 2, 128, this.width - 20 - (this.width - 20) / 2, this::onClose);
    }

    private void apply() {
        if (BridgeClient.busy() || BridgeClient.recording || BridgeClient.recordingAvailable) {
            return;
        }
        try {
            int[] v = new int[6];
            for (int i = 0; i < 6; ++i) {
                v[i] = Integer.parseInt(this.fields[i].getValue());
            }
            Region r = Region.of(v[0], v[1], v[2], v[3], v[4], v[5]);
            BridgeClient.a = new BlockPos(v[0], v[1], v[2]);
            BridgeClient.b = new BlockPos(v[3], v[4], v[5]);
            BridgeClient.status = BridgeClient.timelineStatus = Messages.text("ai_block_bridge.applied", r.description());
            this.onClose();
        }
        catch (Exception ex) {
            this.notice = Messages.text("ai_block_bridge.coordinates_failed", ex.getMessage());
        }
    }

    public void tick() {
        this.apply.active = !BridgeClient.busy() && !BridgeClient.recording && !BridgeClient.recordingAvailable;
        for (EditBox f : this.fields) {
            f.setEditable(this.apply.active);
        }
    }

    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.text(this.font, this.title, 8, 8, -1, true);
        g.text(this.font, this.font.plainSubstrByWidth(Messages.display(Messages.text("ai_block_bridge.menu.area_hint", new Object[0])), this.width - 16), 8, 30, -3355444, false);
        g.text(this.font, "1 XYZ", 8, 58, -6431745, false);
        g.text(this.font, "2 XYZ", 8, 90, -12414, false);
        g.text(this.font, this.font.plainSubstrByWidth(Messages.display(this.notice), this.width - 16), 8, 158, -8307, false);
    }

    public boolean isPauseScreen() {
        return false;
    }
}

