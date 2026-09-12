package io.github.ruok0214.bridge;

/** Immutable original recording: edits in either editor cannot change its pairing. */
public record RecordingBundle(String structure,String timeline) {
    public static final int MAX_CHARS=Script.MAX_CHARS+Script.MAX_TIMELINE_CHARS+32;
    public RecordingBundle {
        if(structure==null||timeline==null||structure.length()>Script.MAX_CHARS||timeline.length()>Script.MAX_TIMELINE_CHARS)
            throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.packet_limit"));
    }
    public String encode(){return structure.length()+"\n"+structure+timeline;}
    public static RecordingBundle decode(String body){
        int end=body.indexOf('\n');
        if(end<1||end>10||body.length()>MAX_CHARS)throw new IllegalArgumentException("Invalid recording bundle");
        int length=Integer.parseInt(body.substring(0,end));
        int start=end+1;
        if(length<0||length>Script.MAX_CHARS||length>body.length()-start)throw new IllegalArgumentException("Invalid recording structure length");
        return new RecordingBundle(body.substring(start,start+length),body.substring(start+length));
    }
}
