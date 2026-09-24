package io.github.ruok0214.bridge;

import java.util.*;

/** Token-saving, file-local aliases for repeated block states and repeated NBT field values. */
public final class PaletteFormat {
    private static final String PREFIX="# palette ";
    private static final String NBT_PREFIX="# nbt ";
    /** Below this a reference costs more than it saves once the declaration is counted. */
    private static final int MIN_NBT_VALUE=32;
    private PaletteFormat() {}

    /** Start offsets and ends of each top-level field value inside an SNBT compound. */
    private static List<int[]> valueSpans(String snbt) {
        var spans=new ArrayList<int[]>();
        if(snbt.length()<2||snbt.charAt(0)!='{'||snbt.charAt(snbt.length()-1)!='}')return spans;
        int depth=0, start=-1;
        char quote=0;
        boolean escaped=false, key=true;
        for(int i=1;i<snbt.length()-1;i++) {
            char c=snbt.charAt(i);
            if(quote!=0) {
                if(escaped)escaped=false;
                else if(c=='\\')escaped=true;
                else if(c==quote)quote=0;
                continue;
            }
            if(c=='"'||c=='\'') { quote=c; continue; }
            if(c=='{'||c=='[') { depth++; continue; }
            if(c=='}'||c==']') { depth--; continue; }
            if(depth!=0)continue;
            if(key&&c==':') { key=false; start=i+1; }
            else if(!key&&c==',') { spans.add(new int[]{start,i}); key=true; }
        }
        if(!key&&start>=0)spans.add(new int[]{start,snbt.length()-1});
        return spans;
    }

    private static String rewrite(String snbt, Map<String,Integer> ids) {
        var spans=valueSpans(snbt);
        StringBuilder out=new StringBuilder(snbt.length());
        int copied=0;
        for(int[] span:spans) {
            Integer id=ids.get(snbt.substring(span[0],span[1]));
            if(id==null)continue;
            out.append(snbt,copied,span[0]).append('$').append(id);
            copied=span[1];
        }
        return out.append(snbt,copied,snbt.length()).toString();
    }

    /** Puts back every {@code $n} written by {@link #rewrite}. */
    static String expand(String snbt, Map<String,String> values) {
        if(snbt.indexOf('$')<0)return snbt;
        var spans=valueSpans(snbt);
        StringBuilder out=new StringBuilder(snbt.length());
        int copied=0;
        for(int[] span:spans) {
            String value=snbt.substring(span[0],span[1]);
            if(value.length()<2||value.charAt(0)!='$')continue;
            String replacement=values.get(value.substring(1));
            if(replacement==null)throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.nbt_palette_missing",value));
            out.append(snbt,copied,span[0]).append(replacement);
            copied=span[1];
        }
        return out.append(snbt,copied,snbt.length()).toString();
    }

    static Map<String,String> readNbt(String[] lines) {
        Map<String,String> values=new HashMap<>();
        for(String raw:lines) {
            String line=raw.strip();
            if(!line.startsWith(NBT_PREFIX))continue;
            int equals=line.indexOf('=',NBT_PREFIX.length());
            if(equals<0)throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.nbt_palette_syntax"));
            String id=line.substring(NBT_PREFIX.length(),equals).strip();
            String value=line.substring(equals+1).strip();
            if(!id.matches("\\d+")||value.isEmpty()||values.putIfAbsent(id,value)!=null)
                throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.nbt_palette_syntax"));
        }
        return values;
    }

    public static String encode(String readable) {
        String[] lines=readable.split("\\R",-1);
        Map<String,Integer> palette=new LinkedHashMap<>();
        Map<String,Integer> counts=new LinkedHashMap<>();
        for(String raw:lines) {
            String line=raw.strip();
            if(line.isEmpty()||line.startsWith("#")||line.startsWith("@tick"))continue;
            String[] fields=line.split("\\|",3);
            if(fields.length<2)continue;
            palette.computeIfAbsent(fields[1].strip(),ignored->palette.size());
            if(fields.length<3)continue;
            String nbt=fields[2].strip();
            for(int[] span:valueSpans(nbt)) {
                String value=nbt.substring(span[0],span[1]);
                if(value.length()>=MIN_NBT_VALUE)counts.merge(value,1,Integer::sum);
            }
        }
        if(palette.isEmpty())return readable;
        Map<String,Integer> nbtIds=new LinkedHashMap<>();
        for(var entry:counts.entrySet())if(entry.getValue()>1)nbtIds.put(entry.getKey(),nbtIds.size());
        if(!roundTrips(lines,nbtIds))nbtIds.clear();

        StringBuilder out=new StringBuilder(readable.length());
        int insert=0;
        while(insert<lines.length&&(lines[insert].isBlank()||lines[insert].strip().startsWith("#"))) {
            out.append(lines[insert++]).append('\n');
        }
        out.append("# encoding: local palette (data block field is a palette number)\n");
        for(var entry:palette.entrySet())out.append(PREFIX).append(entry.getValue()).append(" = ").append(entry.getKey()).append('\n');
        if(!nbtIds.isEmpty())out.append("# encoding: repeated NBT field values are written as $n\n");
        for(var entry:nbtIds.entrySet())out.append(NBT_PREFIX).append(entry.getValue()).append(" = ").append(entry.getKey()).append('\n');
        for(;insert<lines.length;insert++) {
            String raw=lines[insert], line=raw.strip();
            if(line.isEmpty()||line.startsWith("#")||line.startsWith("@tick"))out.append(raw);
            else {
                String[] fields=raw.split("\\|",3);
                if(fields.length<2)out.append(raw);
                else {
                    out.append(fields[0].strip()).append(" | ").append(palette.get(fields[1].strip()));
                    if(fields.length==3)out.append(" | ").append(rewrite(fields[2].strip(),nbtIds));
                }
            }
            if(insert<lines.length-1)out.append('\n');
        }
        return out.toString();
    }

    /** Refuses the NBT palette unless every rewritten row expands back to exactly what it replaced. */
    private static boolean roundTrips(String[] lines, Map<String,Integer> nbtIds) {
        if(nbtIds.isEmpty())return false;
        Map<String,String> values=new HashMap<>();
        for(var entry:nbtIds.entrySet())values.put(String.valueOf(entry.getValue()),entry.getKey());
        for(String raw:lines) {
            String line=raw.strip();
            if(line.isEmpty()||line.startsWith("#")||line.startsWith("@tick"))continue;
            String[] fields=line.split("\\|",3);
            if(fields.length<3)continue;
            String nbt=fields[2].strip();
            try { if(!expand(rewrite(nbt,nbtIds),values).equals(nbt))return false; }
            catch(RuntimeException ex) { return false; }
        }
        return true;
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
