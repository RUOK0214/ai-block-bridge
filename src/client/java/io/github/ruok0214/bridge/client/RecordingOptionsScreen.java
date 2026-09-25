package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;

/** All recording preferences in one window, grouped by what they affect. */
public class RecordingOptionsScreen extends Screen {
    private final Screen parent;
    private final List<Button> options = new ArrayList<>();
    private final List<Button> entityDetails = new ArrayList<>();
    private int page;

    public RecordingOptionsScreen(Screen parent) { this(parent, 0); }
    protected RecordingOptionsScreen(Screen parent, int page) {
        super(Messages.component(Messages.text("ai_block_bridge.record_options.title")));
        this.parent = parent;
        this.page = page;
    }

    private int contentWidth() { return Math.min(560, width - 16); }
    private int left() { return (width - contentWidth()) / 2; }

    private Button toggle(String key, String hint, int row, BooleanSupplier value, Runnable change) {
        Button button = addRenderableWidget(Button.builder(Messages.component(label(key, value.getAsBoolean())), b -> {
            if (!editable()) return;
            change.run();
            b.setMessage(Messages.component(label(key, value.getAsBoolean())));
            tick();
        }).bounds(left(), 66 + row * 24, contentWidth(), 20).build());
        button.setTooltip(Tooltip.create(Messages.component(Messages.text(hint))));
        options.add(button);
        return button;
    }

    private String label(String key, boolean value) {
        return Messages.text(key, Messages.text(value ? "ai_block_bridge.on" : "ai_block_bridge.off"));
    }

    private boolean editable() {
        return !BridgeClient.busy() && !BridgeClient.recording && !BridgeClient.recordingAvailable;
    }

    @Override protected void init() {
        options.clear();
        entityDetails.clear();
        String[] pages = {"general", "blocks", "entities"};
        int w = (contentWidth() - 8) / 3;
        for (int i = 0; i < pages.length; i++) {
            int target = i;
            Button tab = addRenderableWidget(Button.builder(Messages.component(Messages.text("ai_block_bridge.ui.options." + pages[i])),
                b -> { page = target; rebuildWidgets(); }).bounds(left() + i * (w + 4), 30, w, 20).build());
            tab.active = i != page;
        }
        if (page == 0) {
            Button format = addRenderableWidget(Button.builder(Messages.component(WorkspaceScreen.formatLabel()), b -> {
                if (!editable()) return;
                BridgeClient.paletteFormat = !BridgeClient.paletteFormat;
                b.setMessage(Messages.component(WorkspaceScreen.formatLabel()));
            }).bounds(left(), 66, contentWidth(), 20).build());
            format.setTooltip(Tooltip.create(Messages.component(Messages.text("ai_block_bridge.ui.format_hint"))));
            options.add(format);
            toggle("ai_block_bridge.ui.structure_entities", "ai_block_bridge.entities.structure_hint", 1,
                () -> BridgeClient.includeStructureEntities, () -> BridgeClient.includeStructureEntities = !BridgeClient.includeStructureEntities);
            toggle("ai_block_bridge.ui.timeline_entities", "ai_block_bridge.entities.timeline_hint", 2,
                () -> BridgeClient.includeTimelineEntities, () -> BridgeClient.includeTimelineEntities = !BridgeClient.includeTimelineEntities);
        } else if (page == 1) {
            toggle("ai_block_bridge.menu.cooldown", "ai_block_bridge.menu.cooldown_hint", 0,
                () -> BridgeClient.ignoreHopperCooldown, () -> BridgeClient.ignoreHopperCooldown = !BridgeClient.ignoreHopperCooldown);
            toggle("ai_block_bridge.record_options.block_states", "ai_block_bridge.record_options.block_states_hint", 1,
                () -> BridgeClient.shortBlockStates, () -> BridgeClient.shortBlockStates = !BridgeClient.shortBlockStates);
            toggle("ai_block_bridge.record_options.block_nbt", "ai_block_bridge.record_options.block_nbt_hint", 2,
                () -> BridgeClient.blockNbtDelta, () -> BridgeClient.blockNbtDelta = !BridgeClient.blockNbtDelta);
        } else {
            toggle("ai_block_bridge.ui.timeline_entities", "ai_block_bridge.entities.timeline_hint", 0,
                () -> BridgeClient.includeTimelineEntities, () -> BridgeClient.includeTimelineEntities = !BridgeClient.includeTimelineEntities);
            entityDetails.add(toggle("ai_block_bridge.record_options.noise", "ai_block_bridge.record_options.noise_hint", 1,
                () -> BridgeClient.ignoreEntityAgeMotion, () -> BridgeClient.ignoreEntityAgeMotion = !BridgeClient.ignoreEntityAgeMotion));
            entityDetails.add(toggle("ai_block_bridge.record_options.delta", "ai_block_bridge.record_options.delta_hint", 2,
                () -> BridgeClient.entityDelta, () -> BridgeClient.entityDelta = !BridgeClient.entityDelta));
            entityDetails.add(toggle("ai_block_bridge.record_options.ids", "ai_block_bridge.record_options.ids_hint", 3,
                () -> BridgeClient.shortEntityIds, () -> BridgeClient.shortEntityIds = !BridgeClient.shortEntityIds));
        }
        addRenderableWidget(Button.builder(Messages.component(Messages.text("ai_block_bridge.menu.back")), b -> onClose())
            .bounds(left(), height - 28, contentWidth(), 20).build());
        tick();
    }

    @Override public void tick() {
        boolean enabled = editable();
        for (Button option : options) option.active = enabled;
        for (Button option : entityDetails) option.active = enabled && BridgeClient.includeTimelineEntities;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.text(font, title, left(), 9, 0xFFFFFFFF, true);
        String scope = page == 0 ? "ai_block_bridge.ui.options.general_hint" : "ai_block_bridge.ui.options.timeline_hint";
        g.text(font, font.plainSubstrByWidth(Messages.display(Messages.text(scope)), contentWidth()), left(), 55, 0xFF82D9CF, false);
        String hint = editable() ? "ai_block_bridge.ui.options.next_capture" : "ai_block_bridge.ui.options.locked";
        g.text(font, font.plainSubstrByWidth(Messages.display(Messages.text(hint)), contentWidth()), left(), height - 43, 0xFFCCCCCC, false);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
