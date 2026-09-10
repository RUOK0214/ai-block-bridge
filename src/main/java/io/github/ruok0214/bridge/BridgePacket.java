package io.github.ruok0214.bridge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Bounded chunks keep every C2S packet below Minecraft's payload limit. */
public record BridgePacket(int request, int action, int index, int total, String dimension,
                           int ax,int ay,int az,int bx,int by,int bz,String text) implements CustomPacketPayload {
    public static final int EXPORT=0, PASTE=1, UNDO=2, RESULT=3, SCRIPT=4;
    // UTF-8 can take 3 bytes per UTF-16 code unit. Leave room for metadata within 32 KiB C2S.
    public static final int CHUNK=7000, MAX_CHUNKS=Script.MAX_CHARS/(CHUNK-1)+1;
    public static final Type<BridgePacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ai_block_bridge","message"));
    public static final StreamCodec<RegistryFriendlyByteBuf,BridgePacket> CODEC = new StreamCodec<>() {
        public BridgePacket decode(RegistryFriendlyByteBuf b) {
            return new BridgePacket(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readUtf(256),
                b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readUtf(CHUNK));
        }
        public void encode(RegistryFriendlyByteBuf b,BridgePacket p) {
            b.writeVarInt(p.request);b.writeVarInt(p.action);b.writeVarInt(p.index);b.writeVarInt(p.total);b.writeUtf(p.dimension,256);
            b.writeInt(p.ax);b.writeInt(p.ay);b.writeInt(p.az);b.writeInt(p.bx);b.writeInt(p.by);b.writeInt(p.bz);b.writeUtf(p.text,CHUNK);
        }
    };
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public Region region() { return Region.of(ax,ay,az,bx,by,bz); }
    public void chunks(String body, int operation, java.util.function.Consumer<BridgePacket> send) {
        if(body.length()>Script.MAX_CHARS) throw new IllegalArgumentException("스크립트 크기 제한을 초과했습니다.");
        var parts=new java.util.ArrayList<String>();
        for(int start=0;start<body.length();) {
            int end=Math.min(body.length(),start+CHUNK);
            if(end<body.length()&&Character.isHighSurrogate(body.charAt(end-1)))end--;
            parts.add(body.substring(start,end));start=end;
        }
        if(parts.isEmpty())parts.add("");
        for(int i=0;i<parts.size();i++) send.accept(new BridgePacket(request,operation,i,parts.size(),dimension,ax,ay,az,bx,by,bz,parts.get(i)));
    }
}
