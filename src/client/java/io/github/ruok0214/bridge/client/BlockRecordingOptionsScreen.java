/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.Tooltip
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.network.chat.Component
 */
package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.Messages;
import io.github.ruok0214.bridge.client.BridgeClient;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class BlockRecordingOptionsScreen
extends Screen {
    private final Screen parent;
    private Button states;
    private Button nbt;
    private Button palette;

    public BlockRecordingOptionsScreen(Screen parent) {
        super(Messages.component(Messages.text("ai_block_bridge.record_options.blocks", new Object[0])));
        this.parent = parent;
    }

    private Button toggle(String key, int y, BooleanSupplier value, Runnable change) {
        Supplier<Component> label = () -> Messages.component(Messages.text(key, Messages.text(value.getAsBoolean() ? "ai_block_bridge.on" : "ai_block_bridge.off", new Object[0])));
        Button b = this.addRenderableWidget(Button.builder(label.get(), button -> {
            change.run();
            button.setMessage((Component)label.get());
        }).bounds(8, y, this.width - 16, 20).build());
        b.setTooltip(Tooltip.create((Component)Messages.component(Messages.text(key + "_hint", new Object[0]))));
        return b;
    }

    protected void init() {
        this.states = this.toggle("ai_block_bridge.record_options.block_states", 40, () -> BridgeClient.shortBlockStates, () -> {
            BridgeClient.shortBlockStates = !BridgeClient.shortBlockStates;
        });
        this.nbt = this.toggle("ai_block_bridge.record_options.block_nbt", 68, () -> BridgeClient.blockNbtDelta, () -> {
            BridgeClient.blockNbtDelta = !BridgeClient.blockNbtDelta;
        });
        this.palette = this.toggle("ai_block_bridge.format", 96, () -> BridgeClient.paletteFormat, () -> {
            BridgeClient.paletteFormat = !BridgeClient.paletteFormat;
        });
        this.addRenderableWidget(Button.builder(Messages.component(Messages.text("ai_block_bridge.menu.back", new Object[0])), b -> this.onClose()).bounds(8, this.height - 28, this.width - 16, 20).build());
    }

    public void tick() {
        this.nbt.active = !BridgeClient.busy() && !BridgeClient.recording && !BridgeClient.recordingAvailable;
        this.states.active = this.nbt.active;
        this.palette.active = this.nbt.active;
    }

    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float d) {
        super.extractRenderState(g, mx, my, d);
        g.text(this.font, this.title, 8, 8, -1, true);
        g.text(this.font, this.font.plainSubstrByWidth(Messages.display(Messages.text("ai_block_bridge.record_options.hint", new Object[0])), this.width - 16), 8, 132, -3355444, false);
    }

    public boolean isPauseScreen() {
        return false;
    }
}
