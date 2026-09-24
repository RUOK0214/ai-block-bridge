package io.github.ruok0214.bridge;

import java.util.*;

/** Data, never executable code. The third field is opaque SNBT and may contain '|'. */
public final class Script {
    public static final int MAX_CHARS = 2_000_000;
    public static final int MAX_TIMELINE_CHARS = 20_000_000;
    public record Entry(int x, int y, int z, String state, String nbt, int line) {}
    public static List<Entry> parse(String text, Region region) {
        if (text.length()>MAX_CHARS) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.script_limit"));
        var entries = new ArrayList<Entry>();
        Set<String> occupied = new HashSet<>();
        String[] lines = text.replace("\uFEFF", "").split("\\R", -1);
        Map<String,String> palette=PaletteFormat.read(lines);
        Map<String,String> nbtValues=PaletteFormat.readNbt(lines);
        for (int i=0; i<lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            try {
                String[] fields = line.split("\\|",3);
                if (fields.length<2) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.syntax"));
                String[] xyz = fields[0].strip().split("[\\s,]+");
                if (xyz.length!=3) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.xyz"));
                int x=Integer.parseInt(xyz[0]), y=Integer.parseInt(xyz[1]), z=Integer.parseInt(xyz[2]);
                if (!region.containsLocal(x,y,z)) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.bounds"));
                if (!occupied.add(x+","+y+","+z)) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.duplicate"));
                String state=fields[1].strip(), nbt=fields.length==3?fields[2].strip():"";
                if (state.isEmpty()) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.block_id"));
                if(state.matches("\\d+")) {
                    String resolved=palette.get(state);
                    if(resolved==null)throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.palette_missing",state));
                    state=resolved;
                }
                if (fields.length==3 && nbt.isEmpty()) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.empty_nbt"));
                if (!nbt.isEmpty()) nbt=PaletteFormat.expand(nbt,nbtValues);
                entries.add(new Entry(x,y,z,state,nbt,i+1));
            } catch (RuntimeException ex) { throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.line", i+1, ex.getMessage())); }
        }
        if (entries.isEmpty()) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.empty_script"));
        return List.copyOf(entries);
    }
}
