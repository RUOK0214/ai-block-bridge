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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

public final class BridgeClient implements ClientModInitializer {
    public static BlockPos a,b;
    public static String dimension="", script="# AI Block Bridge Script v1\n# x y z | block[state] | {NBT}\n0 0 0 | minecraft:stone\n";
    public static String status=Messages.text("ai_block_bridge.select_hint");
    public static String exportUndo;
    public static String timeline="# AI Block Bridge Timeline v1\n# Changes after recording starts are listed in @tick order.\n";
    public static String timelineStatus=Messages.text("ai_block_bridge.timeline.ready");
    public static RecordingBundle recordingBundle;
    public static boolean recording;
    public static boolean recordingAvailable;
    private static String recordingStopReason;
    public static boolean ignoreHopperCooldown=true;
    public static boolean showSelection=true;
    public static final TextHistory history=new TextHistory();
    public static final TextHistory timelineHistory=new TextHistory();
    private static Assembly response;
    private static int requestId, pending=-1, pendingAction=-1;
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
        var overlay=key("overlay",GLFW.GLFW_KEY_BACKSLASH,category);
        var record=key("record",GLFW.GLFW_KEY_N,category);
        SelectionOverlay.register();
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(mc.level==null)return;
            String current=mc.level.dimension().identifier().toString();
            if(!dimension.equals(current)) { a=null;b=null;dimension=current;pending=-1;pendingAction=-1;response=null;recording=false;recordingAvailable=false;recordingStopReason=null; }
            if(busy() && System.nanoTime()-sentAt>30_000_000_000L) {
                pending=-1;pendingAction=-1;response=null;status=timelineStatus=Messages.text("ai_block_bridge.error.timeout");
            }
            if(mc.gui.screen()==null) {
                while(first.consumeClick()) select(mc,true);
                while(second.consumeClick()) select(mc,false);
                while(overlay.consumeClick()) {
                    showSelection=!showSelection;
                    if(mc.player!=null)mc.player.sendSystemMessage(Messages.component(Messages.text("ai_block_bridge.overlay", Messages.text(showSelection?"ai_block_bridge.on":"ai_block_bridge.off"))));
                }
                while(record.consumeClick()) send(recording||recordingAvailable?BridgePacket.STOP_RECORD:BridgePacket.START_RECORD);
                while(open.consumeClick()) mc.gui.setScreen(new BridgeScreen());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->{a=null;b=null;dimension="";pending=-1;pendingAction=-1;response=null;recording=false;recordingAvailable=false;recordingStopReason=null;});
        ClientPlayNetworking.registerGlobalReceiver(BridgePacket.TYPE,(packet,ctx)->{
            // Server-pushed notices are independent of the currently pending request.
            if(packet.action()==BridgePacket.RECORD_STOPPED) {
                if(packet.index()==0&&packet.total()==1) {
                    recordingStopped(packet.text());
                    if(ctx.client().player!=null)ctx.client().player.sendSystemMessage(Messages.component(timelineStatus));
                    if(ctx.client().player!=null)ctx.client().player.sendOverlayMessage(Messages.component(Messages.text("ai_block_bridge.recording.banner")));
                }
                return;
            }
            if(packet.request()!=pending)return;
            try {
                if(packet.index()==0)response=new Assembly(packet);
                if(response==null)return;
                String body=response.append(packet);
                if(body==null)return;
                if(packet.action()==BridgePacket.SCRIPT) {
                    exportUndo=exportBefore;
                    replace(body);status=Messages.text("ai_block_bridge.captured");
                } else if(packet.action()==BridgePacket.TIMELINE||packet.action()==BridgePacket.RECORDING_BUNDLE) {
                    RecordingBundle received=packet.action()==BridgePacket.RECORDING_BUNDLE?RecordingBundle.decode(body):null;
                    replaceTimeline(received==null?body:received.timeline());recordingBundle=received;recording=false;recordingAvailable=false;
                    status=timelineStatus=recordingStopReason==null?Messages.text("ai_block_bridge.timeline.complete")
                        :Messages.text("ai_block_bridge.recording.retrieved",recordingStopReason);
                } else {
                    status=timelineStatus=body;
                    if(pendingAction==BridgePacket.START_RECORD&&!Messages.isError(body)&&!recordingAvailable)recording=true;
                    if(pendingAction==BridgePacket.STOP_RECORD&&Messages.isError(body))recording=false;
                }
                pending=-1;pendingAction=-1;response=null;
                if(ctx.client().gui.screen() instanceof BridgeScreen screen) screen.syncText();
                if(ctx.client().gui.screen() instanceof TimelineScreen screen) screen.syncText();
                else if((packet.action()==BridgePacket.TIMELINE||packet.action()==BridgePacket.RECORDING_BUNDLE)&&ctx.client().gui.screen()==null)ctx.client().gui.setScreen(new TimelineScreen());
            }catch(Exception ex){pending=-1;pendingAction=-1;response=null;status=timelineStatus=ex.getMessage();}
        });
    }
    public static void recordingStopped(String reason) {
        recording=false;recordingAvailable=true;recordingStopReason=reason;
        status=timelineStatus=Messages.text("ai_block_bridge.recording.available",reason);
    }
    private static void select(Minecraft mc,boolean first) {
        if(busy())return;
        if(mc.hitResult instanceof BlockHitResult hit && hit.getType()==HitResult.Type.BLOCK) {
            if(first)a=hit.getBlockPos().immutable();else b=hit.getBlockPos().immutable();
            status=Messages.text("ai_block_bridge.corner_selected", first?1:2, hit.getBlockPos().toShortString());
        }else status=Messages.text("ai_block_bridge.error.target");
        if(mc.player!=null)mc.player.sendSystemMessage(Messages.component(status));
    }
    public static Region region() {
        if(a==null||b==null)throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.corners"));
        return Region.of(a.getX(),a.getY(),a.getZ(),b.getX(),b.getY(),b.getZ());
    }
    public static void replace(String value) {
        if(!value.equals(script)){history.remember(script);script=value;}
    }
    public static void replaceTimeline(String value) {
        if(!value.equals(timeline)){timelineHistory.remember(timeline);timeline=value;}
    }
    public static void send(int action) {
        if(busy())return;
        try {
            if(!ClientPlayNetworking.canSend(BridgePacket.TYPE))throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.server_mod"));
            Region r=(action==BridgePacket.UNDO||action==BridgePacket.STOP_RECORD)?Region.of(0,0,0,0,0,0):region();
            if(action==BridgePacket.START_RECORD) {
                if(recordingAvailable)throw new IllegalArgumentException(Messages.text("ai_block_bridge.recording.retrieve_first"));
                recordingStopReason=null;
            }
            pending=++requestId;pendingAction=action;sentAt=System.nanoTime();response=null;
            exportBefore=script;
            var packet=new BridgePacket(pending,action,0,1,dimension,r.x(),r.y(),r.z(),r.maxX(),r.maxY(),r.maxZ(),"");
            packet.chunks(action==BridgePacket.PASTE?script:action==BridgePacket.START_RECORD&&!ignoreHopperCooldown?"include-cooldown":"",action,ClientPlayNetworking::send);
            status=Messages.text("ai_block_bridge.processing");
        }catch(Exception ex){pending=-1;pendingAction=-1;status=timelineStatus=ex.getMessage();}
    }
}
