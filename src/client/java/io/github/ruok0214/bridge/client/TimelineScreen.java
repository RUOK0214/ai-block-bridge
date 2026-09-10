package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.*;
import java.nio.file.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Editor for recorded tick order. It is intentionally separate from the placement script. */
public final class TimelineScreen extends Screen {
    private MultiLineEditBox editor;
    private Button start,stop,load,save,copy,undo,filter;
    private boolean syncing;
    public TimelineScreen(){super(Component.literal("AI Block Bridge · 틱 기록"));}
    private Button button(String title,int x,int y,int w,Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(title),b->action.run()).bounds(x,y,w,20).build());
    }
    @Override protected void init() {
        int w=(width-28)/4;
        start=button("기록 시작",8,28,w,()->confirm("틱 기록 시작","현재 선택 영역에서 이후 틱의 변화만 기록합니다. 시작 후 창이 닫힙니다.",()->{
            BridgeClient.send(BridgePacket.START_RECORD);if(BridgeClient.busy())minecraft.gui.setScreen(null);
        }));
        stop=button("기록 중지",12+w,28,w,()->BridgeClient.send(BridgePacket.STOP_RECORD));
        button("배치 스크립트",16+w*2,28,w,()->minecraft.gui.setScreen(new BridgeScreen()));
        button("닫기",20+w*3,28,w,this::onClose);
        filter=button(filterLabel(),8,72,width-16,()->{
            BridgeClient.ignoreHopperCooldown=!BridgeClient.ignoreHopperCooldown;
            filter.setMessage(Component.literal(filterLabel()));
        });
        editor=MultiLineEditBox.builder().setX(8).setY(98).setShowDecorations(true)
            .build(font,width-16,Math.max(40,height-173),Component.literal("틱 기록 스크립트"));
        editor.setCharacterLimit(Script.MAX_TIMELINE_CHARS);editor.setValue(BridgeClient.timeline);
        editor.setValueListener(value->{if(!syncing)BridgeClient.replaceTimeline(value);});addRenderableWidget(editor);
        int bottom=height-67;
        load=button("파일 불러오기",8,bottom,w,this::importFile);
        save=button("파일 내보내기",12+w,bottom,w,this::exportFile);
        copy=button("전체 복사",16+w*2,bottom,w,()->{
            minecraft.keyboardHandler.setClipboard(BridgeClient.timeline);BridgeClient.timelineStatus="기록 스크립트를 클립보드에 복사했습니다.";
        });
        undo=button("편집 취소",20+w*3,bottom,w,this::undoText);
    }
    private void confirm(String title,String message,Runnable yes) {
        minecraft.gui.setScreen(new ConfirmScreen(ok->{minecraft.gui.setScreen(this);if(ok)yes.run();},Component.literal(title),Component.literal(message)));
    }
    public void syncText(){if(editor!=null&&!editor.getValue().equals(BridgeClient.timeline)){syncing=true;editor.setValue(BridgeClient.timeline);syncing=false;}}
    private void undoText(){BridgeClient.timeline=BridgeClient.timelineHistory.undo(BridgeClient.timeline);syncText();}
    private void importFile(){try{
        Path file=ScriptFiles.choose(false,"timeline.txt");if(file==null)return;String value=ScriptFiles.read(file,Script.MAX_TIMELINE_CHARS);
        confirm("기록 스크립트 불러오기",file.getFileName()+" 파일로 현재 기록을 바꿀까요?",()->{BridgeClient.replaceTimeline(value);syncText();BridgeClient.timelineStatus="불러옴: "+file.getFileName();});
    }catch(Exception ex){BridgeClient.timelineStatus="불러오기 실패: "+ex.getMessage();}}
    private void exportFile(){try{
        Path file=ScriptFiles.choose(true,"timeline.txt");if(file==null)return;Runnable write=()->{try{ScriptFiles.write(file,BridgeClient.timeline);BridgeClient.timelineStatus="저장: "+file.getFileName();}catch(Exception ex){BridgeClient.timelineStatus="저장 실패: "+ex.getMessage();}};
        if(Files.exists(file))confirm("파일 덮어쓰기",file.getFileName()+" 파일을 덮어쓸까요?",write);else write.run();
    }catch(Exception ex){BridgeClient.timelineStatus="저장 실패: "+ex.getMessage();}}
    @Override public void tick(){
        filter.active=!BridgeClient.busy()&&!BridgeClient.recording;
        boolean idle=!BridgeClient.busy();start.active=idle&&!BridgeClient.recording;stop.active=idle&&BridgeClient.recording;
        boolean editable=idle&&!BridgeClient.recording;editor.active=editable;load.active=editable;save.active=idle;copy.active=idle;undo.active=editable;
    }
    private String filterLabel(){return "호퍼 쿨다운 제외: "+(BridgeClient.ignoreHopperCooldown?"켜짐":"꺼짐")+" · 최대 6,000틱 / 10만 항목";}
    @Override public boolean keyPressed(KeyEvent event){
        if(editor.isFocused()&&(event.modifiers()&(GLFW.GLFW_MOD_CONTROL|GLFW.GLFW_MOD_SUPER))!=0&&event.key()==GLFW.GLFW_KEY_Z){undoText();return true;}
        return super.keyPressed(event);
    }
    @Override public boolean charTyped(CharacterEvent event){return BridgeClient.recording||BridgeClient.busy()||super.charTyped(event);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);
        g.text(font,"틱 변화 기록 · 단축키 N",8,8,0xFFFFFFFF,true);
        String region;try{region=BridgeClient.region().description();}catch(Exception ex){region=ex.getMessage();}
        g.text(font,font.plainSubstrByWidth((BridgeClient.recording?"● 기록 중 · ":"")+region,width-16),8,56,BridgeClient.recording?0xFFFF7777:0xFFCCCCCC,false);
        g.text(font,font.plainSubstrByWidth(BridgeClient.timelineStatus,width-16),8,height-39,0xFFFFDF8D,false);
        g.text(font,"@tick별 변경 좌표 · 이 문서는 월드 붙여넣기에 사용되지 않습니다.",8,height-15,0xFFAAAAAA,false);
    }
    @Override public boolean isPauseScreen(){return false;}
}
