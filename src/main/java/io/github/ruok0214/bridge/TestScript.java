package io.github.ruok0214.bridge;

import java.util.ArrayList;
import java.util.List;

/** Data, never executable code. Block states stay opaque here and are parsed by the server. */
public final class TestScript {
    public static final int MAX_CHARS = 200_000;
    public static final int MAX_CASES = 256;
    public static final int MAX_STEPS = 4096;
    public static final int MAX_TOTAL_TICKS = 6000;
    public static final String SET = "set", EXPECT = "expect", WAIT = "wait";

    /** {@code state} is empty for WAIT; {@code ticks} is 0 for SET and EXPECT. */
    public record Step(String kind, int x, int y, int z, String state, int ticks, int line) {}
    public record Case(String name, List<Step> steps) {}
    private TestScript() {}

    public static List<Case> parse(String text, Region region) {
        if (text.length() > MAX_CHARS) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.test_limit", MAX_CHARS));
        var cases = new ArrayList<Case>();
        var steps = new ArrayList<Step>();
        String name = null;
        int stepCount = 0, totalTicks = 0;
        String[] lines = text.replace("﻿", "").split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            try {
                if (line.startsWith("@case")) {
                    if (name != null) cases.add(new Case(name, List.copyOf(steps)));
                    name = line.substring(5).strip();
                    if (name.isEmpty()) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.test_case_name"));
                    if (cases.size() + 1 > MAX_CASES) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.test_case_limit", MAX_CASES));
                    steps.clear();
                    continue;
                }
                if (name == null) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.test_case_required"));
                if (++stepCount > MAX_STEPS) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.test_step_limit", MAX_STEPS));
                String[] words = line.split("\\s+", 2);
                String kind = words[0];
                if (kind.equals(WAIT)) {
                    if (words.length < 2) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.test_syntax"));
                    int ticks = Integer.parseInt(words[1].strip());
                    if (ticks < 1) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.test_wait"));
                    totalTicks += ticks;
                    if (totalTicks > MAX_TOTAL_TICKS) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.test_tick_limit", MAX_TOTAL_TICKS));
                    steps.add(new Step(WAIT, 0, 0, 0, "", ticks, i + 1));
                    continue;
                }
                if (!kind.equals(SET) && !kind.equals(EXPECT)) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.test_keyword", kind));
                if (words.length < 2) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.test_syntax"));
                String[] fields = words[1].split("\\|", 2);
                if (fields.length < 2) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.test_syntax"));
                String[] xyz = fields[0].strip().split("[\\s,]+");
                if (xyz.length != 3) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.xyz"));
                int x = Integer.parseInt(xyz[0]), y = Integer.parseInt(xyz[1]), z = Integer.parseInt(xyz[2]);
                if (!region.containsLocal(x, y, z)) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.bounds"));
                String state = fields[1].strip();
                if (state.isEmpty()) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.block_id"));
                steps.add(new Step(kind, x, y, z, state, 0, i + 1));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.line", i + 1, ex.getMessage()));
            }
        }
        if (name != null) cases.add(new Case(name, List.copyOf(steps)));
        if (cases.isEmpty()) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.test_empty"));
        return List.copyOf(cases);
    }
}
