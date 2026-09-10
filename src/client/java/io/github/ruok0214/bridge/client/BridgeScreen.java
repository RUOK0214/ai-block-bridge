package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;

public final class BridgeScreen extends Screen {
    private final EditBox[] coordinates=new EditBox[6];
    private final List<Button> actions=new ArrayList<>();
    private MultiLineEditBox editor;
    private Button exportUndoButton;
    private boolean syncing;
    public BridgeScreen() { super(Component.literal("AI Block Bridge")); }
    private Button button(String title,int x,int y,int w,Runnable action) {
        Button b=addRenderableWidget(Button.builder(Component.literal(title),btn->action.run()).bounds(x,y,w,20).build());
        actions.add(b);return b;
    }
    @Override protected void init() {
        actions.clear();
        int fieldWidth=(width-112)/3;
        for(int row=0;row<2;row++) {
            BlockPos pos=row==0?BridgeClient.a:BridgeClient.b;
            int[] xyz=pos==null?new int[]{0,0,0}:new int[]{pos.getX(),pos.getY(),pos.getZ()};
            for(int col=0;col<3;col++) {
                EditBox field=new EditBox(font,40+col*(fieldWidth+3),27+row*24,fieldWidth,20,Component.literal("모서리 "+(row+1)+" "+"XYZ".charAt(col)));
                field.setMaxLength(11);field.setValue(Integer.toString(xyz[col]));
                field.setFilter(s->s.matches("-?\\d*"));
                coordinates[row*3+col]=addRenderableWidget(field);
            }
            int r=row;
            button("내 위치",width-57,27+row*24,49,()->{
                if(minecraft.player==null)return;
                BlockPos p=minecraft.player.blockPosition();
                coordinates[r*3].setValue(""+p.getX());coordinates[r*3+1].setValue(""+p.getY());coordinates[r*3+2].setValue(""+p.getZ());
            });
        }
        button("좌표 적용",8,78,80,this::applyCoordinates);
        button("문법 검사",92,78,80,()->{
            try { BridgeClient.status=Script.parse(BridgeClient.script,BridgeClient.region()).size()+"블록: 좌표·형식 검사 통과 (블록/NBT는 서버에서 최종 검증)"; }
            catch(Exception ex){BridgeClient.status=ex.getMessage();}
        });
        button("도움말",176,78,64,()->confirm("스크립트 형식", "x y z | minecraft:block[상태] | {NBT}. 공기는 minecraft:air. 생략한 좌표는 유지됩니다. 영역은 최대 4096블록. 취소 기록은 접속 중에만 유지됩니다.",()->{}));
        button("닫기",width-66,78,58,this::onClose);
        editor=MultiLineEditBox.builder().setX(8).setY(116).setShowDecorations(true)
            .build(font,width-16,Math.max(30,height-203),Component.literal("블록 스크립트"));
        editor.setCharacterLimit(Script.MAX_CHARS);
        editor.setValue(BridgeClient.script);
        editor.setValueListener(value->{if(!syncing)BridgeClient.replace(value);});
        addRenderableWidget(editor);
        int w=(width-28)/4, bottom=height-81;
        button("파일 불러오기",8,bottom,w,this::importFile);
        button("파일 내보내기",12+w,bottom,w,this::exportFile);
        button("전체 복사",16+w*2,bottom,w,()->{
            minecraft.keyboardHandler.setClipboard(BridgeClient.script);BridgeClient.status="스크립트 전체를 클립보드에 복사했습니다.";
        });
        button("편집 취소",20+w*3,bottom,w,this::undoText);
        button("영역 → 스크립트",8,bottom+24,w,()->confirm("스크립트화", "현재 스크립트를 선택 영역의 블록 데이터로 바꿉니다. 공기도 포함됩니다.",()->BridgeClient.send(BridgePacket.EXPORT)));
        exportUndoButton=button("스크립트화 취소",12+w,bottom+24,w,()->{
            if(BridgeClient.exportUndo==null)return;
            confirm("스크립트화 취소", "현재 편집 내용을 스크립트화 직전 텍스트로 되돌립니다.",()->{
                BridgeClient.replace(BridgeClient.exportUndo);BridgeClient.exportUndo=null;syncText();BridgeClient.status="스크립트화 직전 텍스트로 복원했습니다.";
            });
        });
        button("월드에 붙여넣기",16+w*2,bottom+24,w,()->{
            try {
                Region r=BridgeClient.region();
                int count=Script.parse(BridgeClient.script,r).size();
                confirm("월드 블록 덮어쓰기",r.description()+" / "+count+"블록과 해당 통 내용물이 교체됩니다. 테스트 월드에서 먼저 확인하세요.",()->BridgeClient.send(BridgePacket.PASTE));
            }catch(Exception ex){BridgeClient.status=ex.getMessage();}
        });
        button("붙여넣기 취소",20+w*3,bottom+24,w,()->confirm("직전 붙여넣기 취소", "직전 작업의 블록과 NBT를 복원합니다. 이후 변경된 블록이 있으면 중단합니다.",()->BridgeClient.send(BridgePacket.UNDO)));
    }
    private void applyCoordinates() {
        try {
            int[] v=new int[6];for(int i=0;i<6;i++)v[i]=Integer.parseInt(coordinates[i].getValue());
            Region r=Region.of(v[0],v[1],v[2],v[3],v[4],v[5]);
            BridgeClient.a=new BlockPos(v[0],v[1],v[2]);BridgeClient.b=new BlockPos(v[3],v[4],v[5]);
            BridgeClient.status="적용: "+r.description();
        }catch(Exception ex){BridgeClient.status="좌표 적용 실패: "+ex.getMessage();}
    }
    private void confirm(String title,String message,Runnable yes) {
        minecraft.gui.setScreen(new ConfirmScreen(accepted->{minecraft.gui.setScreen(this);if(accepted)yes.run();},Component.literal(title),Component.literal(message)));
    }
    public void syncText() {
        if(editor!=null&&!editor.getValue().equals(BridgeClient.script)) {
            syncing=true;editor.setValue(BridgeClient.script);syncing=false;
        }
    }
    private void undoText() { BridgeClient.script=BridgeClient.history.undo(BridgeClient.script);syncText(); }
    private void importFile() {
        try {
            Path file=ScriptFiles.choose(false);if(file==null)return;
            String text=ScriptFiles.read(file);
            confirm("파일 불러오기",file.getFileName()+" 파일로 현재 스크립트를 바꿀까요?",()->{
                BridgeClient.replace(text);syncText();BridgeClient.status="불러옴: "+file.getFileName();
            });
        }catch(Exception ex){BridgeClient.status="불러오기 실패: "+ex.getMessage();}
    }
    private void exportFile() {
        try {
            Path file=ScriptFiles.choose(true);if(file==null)return;
            Runnable save=()->{
                try{ScriptFiles.write(file,BridgeClient.script);BridgeClient.status="저장: "+file.getFileName();}
                catch(Exception ex){BridgeClient.status="저장 실패: "+ex.getMessage();}
            };
            if(Files.exists(file))confirm("파일 덮어쓰기",file.getFileName()+" 파일을 덮어쓸까요?",save);else save.run();
        }catch(Exception ex){BridgeClient.status="저장 실패: "+ex.getMessage();}
    }
    @Override public void tick() {
        boolean enabled=!BridgeClient.busy();
        for(Button button:actions)button.active=enabled;
        exportUndoButton.active=enabled&&BridgeClient.exportUndo!=null;
        for(EditBox field:coordinates)field.setEditable(enabled);
        editor.active=enabled;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if(editor.isFocused()) {
            if(BridgeClient.busy()&&event.key()!=GLFW.GLFW_KEY_ESCAPE)return true;
            if((event.modifiers()&(GLFW.GLFW_MOD_CONTROL|GLFW.GLFW_MOD_SUPER))!=0 && event.key()==GLFW.GLFW_KEY_Z) { undoText();return true; }
        }
        return super.keyPressed(event);
    }
    @Override public boolean charTyped(CharacterEvent event) {
        if(BridgeClient.busy())return true;
        return super.charTyped(event);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        super.extractRenderState(g,mx,my,delta);
        g.text(font,"AI BLOCK BRIDGE · 26.2",8,7,0xFFFFFFFF,true);
        g.text(font,"1 XYZ",8,33,0xFF9DDBFF,false);g.text(font,"2 XYZ",8,57,0xFFFFCF82,false);
        String region;
        try{region=BridgeClient.region().description();}catch(Exception ex){region=ex.getMessage();}
        g.text(font,font.plainSubstrByWidth(region,width-16),8,103,0xFFCCCCCC,false);
        g.text(font,font.plainSubstrByWidth(BridgeClient.status==null?"":BridgeClient.status,width-16),8,height-29,0xFFFFDF8D,false);
        g.text(font,"Ctrl+A/C/V/X/Z · 입력은 창을 닫아도 유지 · 파일 저장은 내보내기",8,height-15,0xFFAAAAAA,false);
    }
    @Override public boolean isPauseScreen(){return false;}
}
