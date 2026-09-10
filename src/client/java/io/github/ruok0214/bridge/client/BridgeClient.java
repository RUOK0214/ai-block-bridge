package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

public final class BridgeClient implements ClientModInitializer {
    public static BlockPos a,b;
    public static String dimension="", script="# AI Block Bridge Script v1\n# x y z | block[state] | {NBT}\n0 0 0 | minecraft:stone\n";
    public static String status="모서리 1·2를 선택하세요. 기본 키: [ / ] / 설정창: B";
    public static String exportUndo;
    public static final TextHistory history=new TextHistory();
    private static Assembly response;
    private static int requestId, pending=-1;
    private static long sentAt;
    private static String exportBefore;
    public static boolean busy() { return pending!=-1; }
    private static KeyMapping key(String name,int code,KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ai_block_bridge."+name,InputConstants.Type.KEYSYM,code,category));
    }
    public void onInitializeClient() {
        var category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("ai_block_bridge","controls"));
        var open=key("open",GLFW.GLFW_KEY_B,category);
        var first=key("first",GLFW.GLFW_KEY_LEFT_BRACKET,category);
        var second=key("second",GLFW.GLFW_KEY_RIGHT_BRACKET,category);
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(mc.level==null)return;
            String current=mc.level.dimension().identifier().toString();
            if(!dimension.equals(current)) { a=null;b=null;dimension=current;pending=-1;response=null; }
            if(busy() && System.nanoTime()-sentAt>30_000_000_000L) {
                pending=-1;response=null;status="응답 시간 초과. 월드 상태를 확인한 뒤 다시 시도하세요.";
            }
            if(mc.gui.screen()==null) {
                while(first.consumeClick()) select(mc,true);
                while(second.consumeClick()) select(mc,false);
                while(open.consumeClick()) mc.gui.setScreen(new BridgeScreen());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->{a=null;b=null;dimension="";pending=-1;response=null;});
        ClientPlayNetworking.registerGlobalReceiver(BridgePacket.TYPE,(packet,ctx)->{
            if(packet.request()!=pending)return;
            try {
                if(packet.index()==0)response=new Assembly(packet);
                if(response==null)return;
                String body=response.append(packet);
                if(body==null)return;
                if(packet.action()==BridgePacket.SCRIPT) {
                    exportUndo=exportBefore;
                    replace(body);status="스크립트화 완료. 공기를 포함한 전체 영역입니다.";
                } else status=body;
                pending=-1;response=null;
                if(ctx.client().gui.screen() instanceof BridgeScreen screen) screen.syncText();
            }catch(Exception ex){pending=-1;response=null;status=ex.getMessage();}
        });
    }
    private static void select(Minecraft mc,boolean first) {
        if(busy())return;
        if(mc.hitResult instanceof BlockHitResult hit && hit.getType()==HitResult.Type.BLOCK) {
            if(first)a=hit.getBlockPos().immutable();else b=hit.getBlockPos().immutable();
            status="모서리 "+(first?1:2)+": "+hit.getBlockPos().toShortString();
        }else status="블록을 바라본 상태에서 선택 키를 누르세요.";
        if(mc.player!=null)mc.player.displayClientMessage(Component.literal(status),true);
    }
    public static Region region() {
        if(a==null||b==null)throw new IllegalArgumentException("모서리 1과 2를 모두 선택하세요.");
        return Region.of(a.getX(),a.getY(),a.getZ(),b.getX(),b.getY(),b.getZ());
    }
    public static void replace(String value) {
        if(!value.equals(script)){history.remember(script);script=value;}
    }
    public static void send(int action) {
        if(busy())return;
        try {
            if(!ClientPlayNetworking.canSend(BridgePacket.TYPE))throw new IllegalArgumentException("서버에 AI Block Bridge 모드가 필요합니다.");
            Region r=action==BridgePacket.UNDO?Region.of(0,0,0,0,0,0):region();
            pending=++requestId;sentAt=System.nanoTime();response=null;
            exportBefore=script;
            var packet=new BridgePacket(pending,action,0,1,dimension,r.x(),r.y(),r.z(),r.maxX(),r.maxY(),r.maxZ(),"");
            packet.chunks(action==BridgePacket.PASTE?script:"",action,ClientPlayNetworking::send);
            status="서버에서 처리 중…";
        }catch(Exception ex){pending=-1;status=ex.getMessage();}
    }
}
