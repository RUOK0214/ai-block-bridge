package io.github.ruok0214.bridge;

import java.util.*;

/** Data, never executable code. The third field is opaque SNBT and may contain '|'. */
public final class Script {
    public static final int MAX_CHARS = 2_000_000;
    public record Entry(int x, int y, int z, String state, String nbt, int line) {}
    public static List<Entry> parse(String text, Region region) {
        if (text.length()>MAX_CHARS) throw new IllegalArgumentException("스크립트는 최대 2,000,000자입니다.");
        var entries = new ArrayList<Entry>();
        Set<String> occupied = new HashSet<>();
        String[] lines = text.replace("\uFEFF", "").split("\\R", -1);
        for (int i=0; i<lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            try {
                String[] fields = line.split("\\|",3);
                if (fields.length<2) throw new IllegalArgumentException("x y z | 블록ID[상태] | {NBT} 형식이 필요합니다.");
                String[] xyz = fields[0].strip().split("[\\s,]+");
                if (xyz.length!=3) throw new IllegalArgumentException("좌표 3개가 필요합니다.");
                int x=Integer.parseInt(xyz[0]), y=Integer.parseInt(xyz[1]), z=Integer.parseInt(xyz[2]);
                if (!region.containsLocal(x,y,z)) throw new IllegalArgumentException("선택 영역 밖의 상대좌표입니다.");
                if (!occupied.add(x+","+y+","+z)) throw new IllegalArgumentException("좌표가 중복되었습니다.");
                String state=fields[1].strip(), nbt=fields.length==3?fields[2].strip():"";
                if (state.isEmpty()) throw new IllegalArgumentException("블록 ID가 없습니다.");
                if (fields.length==3 && nbt.isEmpty()) throw new IllegalArgumentException("NBT 필드가 비어 있습니다. 필드를 제거하거나 {}를 사용하세요.");
                entries.add(new Entry(x,y,z,state,nbt,i+1));
            } catch (RuntimeException ex) { throw new IllegalArgumentException((i+1)+"행: "+ex.getMessage()); }
        }
        if (entries.isEmpty()) throw new IllegalArgumentException("스크립트에 블록이 없습니다.");
        return List.copyOf(entries);
    }
}
