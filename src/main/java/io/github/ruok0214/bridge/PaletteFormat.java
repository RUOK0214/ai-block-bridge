package io.github.ruok0214.bridge;

import java.util.*;

/** Token-saving, file-local aliases for repeated block states. */
public final class PaletteFormat {
    private static final String PREFIX="# palette ";
    private PaletteFormat() {}

    public static String encode(String readable) {
        String[] lines=readable.split("\\R",-1);
        Map<String,Integer> palette=new LinkedHashMap<>();
        for(String raw:lines) {
            String line=raw.strip();
            if(line.isEmpty()||line.startsWith("#")||line.startsWith("@tick"))continue;
            String[] fields=line.split("\\|",3);
            if(fields.length>=2)palette.computeIfAbsent(fields[1].strip(),ignored->palette.size());
        }
        if(palette.isEmpty())return readable;

        StringBuilder out=new StringBuilder(readable.length());
        int insert=0;
        while(insert<lines.length&&(lines[insert].isBlank()||lines[insert].strip().startsWith("#"))) {
            out.append(lines[insert++]).append('\n');
        }
        out.append("# encoding: local palette (data block field is a palette number)\n");
        for(var entry:palette.entrySet())out.append(PREFIX).append(entry.getValue()).append(" = ").append(entry.getKey()).append('\n');
        for(;insert<lines.length;insert++) {
            String raw=lines[insert], line=raw.strip();
            if(line.isEmpty()||line.startsWith("#")||line.startsWith("@tick"))out.append(raw);
            else {
                String[] fields=raw.split("\\|",3);
                if(fields.length<2)out.append(raw);
                else {
                    out.append(fields[0].strip()).append(" | ").append(palette.get(fields[1].strip()));
                    if(fields.length==3)out.append(" | ").append(fields[2].strip());
                }
            }
            if(insert<lines.length-1)out.append('\n');
        }
        return out.toString();
    }

    static Map<String,String> read(String[] lines) {
        Map<String,String> palette=new HashMap<>();
        for(String raw:lines) {
            String line=raw.strip();
            if(!line.startsWith(PREFIX))continue;
            int equals=line.indexOf('=',PREFIX.length());
            if(equals<0)throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.palette_syntax"));
            String id=line.substring(PREFIX.length(),equals).strip();
            String state=line.substring(equals+1).strip();
            if(!id.matches("\\d+")||state.isEmpty()||palette.putIfAbsent(id,state)!=null)
                throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.palette_syntax"));
        }
        return palette;
    }
}
