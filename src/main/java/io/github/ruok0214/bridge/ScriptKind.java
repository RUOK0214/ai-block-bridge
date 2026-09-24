package io.github.ruok0214.bridge;

/** Which editor a text belongs to, read from the header the mod writes. */
public enum ScriptKind {
    SCRIPT("# AI Block Bridge Script v1", "ai_block_bridge.menu.structure"),
    TIMELINE("# AI Block Bridge Timeline v1", "ai_block_bridge.menu.timeline"),
    TEST("# AI Block Bridge Test v1", "ai_block_bridge.menu.test"),
    SETTLE_REPORT("# AI Block Bridge Settle Report v1", "ai_block_bridge.settle.title"),
    TEST_REPORT("# AI Block Bridge Test Report v1", "ai_block_bridge.test.title"),
    /** No header, so the text is taken at face value rather than refused. */
    UNKNOWN(null, null);

    private static final int HEADER_LINES = 5;
    private final String header;
    private final String labelKey;

    ScriptKind(String header, String labelKey) { this.header = header; this.labelKey = labelKey; }

    public String label() { return labelKey == null ? "" : Messages.text(labelKey); }

    public static ScriptKind of(String text) {
        String[] lines = text.replace("﻿", "").split("\\R", -1);
        for (int i = 0, checked = 0; i < lines.length && checked < HEADER_LINES; i++) {
            String line = lines[i].strip();
            if (line.isEmpty()) continue;
            checked++;
            for (ScriptKind kind : values()) {
                if (kind.header != null && kind.header.equals(line)) return kind;
            }
        }
        return UNKNOWN;
    }

    /** Message naming where this text belongs, or null when it is already in the right editor. */
    public static String misplaced(String text, ScriptKind expected) {
        ScriptKind actual = of(text);
        if (actual == expected || actual == UNKNOWN) return null;
        return Messages.text("ai_block_bridge.error.wrong_editor", actual.label());
    }
}
