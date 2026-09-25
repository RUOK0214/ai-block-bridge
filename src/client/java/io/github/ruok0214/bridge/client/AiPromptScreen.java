package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.Messages;
import io.github.ruok0214.bridge.Region;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;

/** Editable request templates. Copying never sends data or attaches files. */
public final class AiPromptScreen extends Screen {
    private final Screen parent;
    private MultiLineEditBox editor;
    private String draft;
    private String status = "";

    public AiPromptScreen(Screen parent) {
        super(Messages.component(Messages.text("ai_block_bridge.prompt.title")));
        this.parent = parent;
        this.draft = createPrompt("explain");
    }

    public static String createPrompt(String mode) {
        if (!mode.equals("explain") && !mode.equals("design") && !mode.equals("fix"))
            throw new IllegalArgumentException("Unknown prompt mode");
        String version = FabricLoader.getInstance().getModContainer("minecraft")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("26.2");
        String region;
        try { region = Messages.display(BridgeClient.region().description()); }
        catch (IllegalArgumentException ex) { region = Messages.display(Messages.text("ai_block_bridge.prompt.no_region")); }
        return Messages.display(Messages.text("ai_block_bridge.prompt.context", version, region)) + "\n\n"
            + Messages.display(Messages.text("ai_block_bridge.prompt." + mode)) + "\n\n"
            + Messages.display(Messages.text("ai_block_bridge.prompt.format", Region.MAX_BLOCKS_TEXT)) + "\n\n"
            + Messages.display(Messages.text("ai_block_bridge.prompt.testing")) + "\n\n"
            + Messages.display(Messages.text("ai_block_bridge.prompt.attach"));
    }

    private void button(String key, int x, int y, int w, Runnable action) {
        addRenderableWidget(Button.builder(Messages.component(Messages.text(key)), b -> action.run())
            .bounds(x, y, w, 20).build());
    }

    @Override protected void init() {
        int w = (width - 24) / 3;
        String[] modes = {"explain", "design", "fix"};
        for (int i = 0; i < modes.length; i++) {
            String mode = modes[i];
            button("ai_block_bridge.prompt.button." + mode, 8 + i * (w + 4), 29, w, () -> {
                draft = createPrompt(mode); editor.setValue(draft); status = "";
            });
        }
        editor = MultiLineEditBox.builder().setX(8).setY(58).setShowDecorations(true)
            .build(font, width - 16, Math.max(30, height - 122), title);
        editor.setCharacterLimit(32000);
        editor.setValue(draft);
        editor.setValueListener(value -> draft = value);
        addRenderableWidget(editor);
        button("ai_block_bridge.prompt.copy", 8, height - 56, (width - 20) / 2, () -> {
            minecraft.keyboardHandler.setClipboard(draft);
            status = Messages.text("ai_block_bridge.prompt.copied");
        });
        button("ai_block_bridge.menu.back", 12 + (width - 20) / 2, height - 56, (width - 20) / 2, this::onClose);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.text(font, title, 8, 9, 0xFFFFFFFF, true);
        String hint = status.isEmpty() ? Messages.text("ai_block_bridge.prompt.hint") : status;
        g.text(font, font.plainSubstrByWidth(Messages.display(hint), width - 16), 8, height - 24, 0xFFFFDF8D, false);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
