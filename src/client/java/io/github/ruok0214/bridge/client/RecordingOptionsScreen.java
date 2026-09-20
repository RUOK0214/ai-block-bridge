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
import io.github.ruok0214.bridge.client.BlockRecordingOptionsScreen;
import io.github.ruok0214.bridge.client.BridgeClient;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class RecordingOptionsScreen
extends Screen {
    private final Screen parent;
    private Button entities;
    private Button noise;
    private Button cooldown;
    private Button delta;
    private Button ids;
    private Button blocks;

    public RecordingOptionsScreen(Screen parent) {
        super(Messages.component(Messages.text("ai_block_bridge.record_options.title", new Object[0])));
        this.parent = parent;
    }

    private String label(String key, boolean on) {
        return Messages.text(key, Messages.text(on ? "ai_block_bridge.on" : "ai_block_bridge.off", new Object[0]));
    }

    private Button toggle(String key, String hint, int y, BooleanSupplier value, Runnable change) {
        Button b = (Button)this.addRenderableWidget((GuiEventListener)Button.builder((Component)Messages.component(this.label(key, value.getAsBoolean())), button -> {
            change.run();
            button.setMessage(Messages.component(this.label(key, value.getAsBoolean())));
        }).bounds(8, y, this.width - 16, 20).build());
        b.setTooltip(Tooltip.create((Component)Messages.component(Messages.text(hint, new Object[0]))));
        return b;
    }

    protected void init() {
        this.entities = this.toggle("ai_block_bridge.entities.timeline", "ai_block_bridge.entities.timeline_hint", 32, () -> BridgeClient.includeTimelineEntities, () -> {
            BridgeClient.includeTimelineEntities = !BridgeClient.includeTimelineEntities;
        });
        this.noise = this.toggle("ai_block_bridge.record_options.noise", "ai_block_bridge.record_options.noise_hint", 56, () -> BridgeClient.ignoreEntityAgeMotion, () -> {
            BridgeClient.ignoreEntityAgeMotion = !BridgeClient.ignoreEntityAgeMotion;
        });
        this.cooldown = this.toggle("ai_block_bridge.menu.cooldown", "ai_block_bridge.menu.cooldown_hint", 80, () -> BridgeClient.ignoreHopperCooldown, () -> {
            BridgeClient.ignoreHopperCooldown = !BridgeClient.ignoreHopperCooldown;
        });
        this.delta = this.toggle("ai_block_bridge.record_options.delta", "ai_block_bridge.record_options.delta_hint", 104, () -> BridgeClient.entityDelta, () -> {
            BridgeClient.entityDelta = !BridgeClient.entityDelta;
        });
        this.ids = this.toggle("ai_block_bridge.record_options.ids", "ai_block_bridge.record_options.ids_hint", 128, () -> BridgeClient.shortEntityIds, () -> {
            BridgeClient.shortEntityIds = !BridgeClient.shortEntityIds;
        });
        this.blocks = (Button)this.addRenderableWidget((GuiEventListener)Button.builder((Component)Messages.component(Messages.text("ai_block_bridge.record_options.blocks", new Object[0])), b -> this.minecraft.gui.setScreen((Screen)new BlockRecordingOptionsScreen(this))).bounds(8, 152, this.width - 16, 20).build());
        this.addRenderableWidget((GuiEventListener)Button.builder((Component)Messages.component(Messages.text("ai_block_bridge.menu.back", new Object[0])), b -> this.onClose()).bounds(8, this.height - 28, this.width - 16, 20).build());
    }

    public void tick() {
        boolean idle;
        this.blocks.active = idle = !BridgeClient.busy() && !BridgeClient.recording && !BridgeClient.recordingAvailable;
        this.entities.active = idle;
        this.noise.active = idle && BridgeClient.includeTimelineEntities;
        this.cooldown.active = idle;
        this.delta.active = this.noise.active;
        this.ids.active = this.noise.active;
    }

    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.text(this.font, this.title, 8, 8, -1, true);
        g.text(this.font, this.font.plainSubstrByWidth(Messages.display(Messages.text("ai_block_bridge.record_options.hint", new Object[0])), this.width - 16), 8, 184, -3355444, false);
    }

    public boolean isPauseScreen() {
        return false;
    }
}


