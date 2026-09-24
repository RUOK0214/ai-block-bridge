package io.github.ruok0214.bridge;

import java.util.List;

/** Diff between what a script asked for and what the world settled to. Text only, never executable. */
public final class SettleReport {
    public static final int MAX_ROWS = 1000;
    public static final int MAX_CHARS = 500_000;
    public static final class Rows extends java.util.ArrayList<Row> {
        public int mismatched, removed;
        @Override public boolean add(Row row) {
            mismatched++;
            if (row.actual().equals("minecraft:air")) removed++;
            return size() < MAX_ROWS && super.add(row);
        }
    }
    public record Row(int x, int y, int z, String requested, String actual) {}
    private SettleReport() {}

    public static String render(Region region, int ticks, int requested, List<Row> rows) {
        int removed = 0;
        for (Row row : rows) if (row.actual().equals("minecraft:air")) removed++;
        int mismatched = rows instanceof Rows bounded ? bounded.mismatched : rows.size();
        if (rows instanceof Rows bounded) removed = bounded.removed;
        StringBuilder text = new StringBuilder("# AI Block Bridge Settle Report v1\n# size: "
            + region.sizeX() + " " + region.sizeY() + " " + region.sizeZ()
            + "\n# origin: " + region.x() + " " + region.y() + " " + region.z()
            + "\n# settled after " + ticks + " ticks | requested " + requested
            + " | mismatched " + mismatched + " | removed " + removed
            + "\n# Observation only; these lines are not paste instructions.\n"
            + "# The world recomputed wire connections, power and support. 'actual' is what Minecraft settled on.\n");
        if (mismatched == 0) {
            text.append("# Every requested block matches the settled world.\n");
            return text.toString();
        }
        int shown = 0;
        for (Row row : rows) {
            if (shown >= MAX_ROWS || text.length() + row.requested().length() + row.actual().length() + 100 > MAX_CHARS) break;
            shown++;
            text.append(row.x()).append(' ').append(row.y()).append(' ').append(row.z())
                .append(" | requested ").append(row.requested())
                .append(" | actual ").append(row.actual()).append('\n');
        }
        if (shown < mismatched) text.append("# showing first ").append(shown).append(" of ").append(mismatched).append(" mismatches\n");
        return text.toString();
    }
}
