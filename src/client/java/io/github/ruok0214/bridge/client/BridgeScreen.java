package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
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
    public BridgeScreen() { super(Messages.component("AI Block Bridge")); }
    private Button button(String title,int x,int y,int w,Runnable action) {
        Button b=addRenderableWidget(Button.builder(Messages.component(title),btn->action.run()).bounds(x,y,w,20).build());
        actions.add(b);return b;
    }
    @Override protected void init() {
        actions.clear();
        int fieldWidth=(width-112)/3;
        for(int row=0;row<2;row++) {
            BlockPos pos=row==0?BridgeClient.a:BridgeClient.b;
            int[] xyz=pos==null?new int[]{0,0,0}:new int[]{pos.getX(),pos.getY(),pos.getZ()};
            for(int col=0;col<3;col++) {
                EditBox field=new EditBox(font,40+col*(fieldWidth+3),27+row*24,fieldWidth,20,Messages.component(Messages.text("ai_block_bridge.corner_field", row+1, "XYZ".charAt(col))));
                field.setMaxLength(11);field.setValue(Integer.toString(xyz[col]));
                coordinates[row*3+col]=addRenderableWidget(field);
            }
            int r=row;
            button(Messages.text("ai_block_bridge.button.position"),width-57,27+row*24,49,()->{
                if(minecraft.player==null)return;
                BlockPos p=minecraft.player.blockPosition();
                coordinates[r*3].setValue(""+p.getX());coordinates[r*3+1].setValue(""+p.getY());coordinates[r*3+2].setValue(""+p.getZ());
            });
        }
        button(Messages.text("ai_block_bridge.button.apply"),8,78,80,this::applyCoordinates);
        button(Messages.text("ai_block_bridge.button.validate"),92,78,80,()->{
            try { BridgeClient.status=Messages.text("ai_block_bridge.validated", Script.parse(BridgeClient.script,BridgeClient.region()).size()); }
            catch(Exception ex){BridgeClient.status=ex.getMessage();}
        });
        button(Messages.text("ai_block_bridge.button.help"),176,78,64,()->confirm(Messages.text("ai_block_bridge.help.title"), Messages.text("ai_block_bridge.help.body", Region.MAX_BLOCKS_TEXT),()->{}));
        button(Messages.text("ai_block_bridge.button.timeline"),244,78,70,()->minecraft.gui.setScreen(new TimelineScreen()));
        button(Messages.text("ai_block_bridge.button.close"),width-66,78,58,this::onClose);
        editor=MultiLineEditBox.builder().setX(8).setY(116).setShowDecorations(true)
            .build(font,width-16,Math.max(30,height-203),Messages.component(Messages.text("ai_block_bridge.editor.script")));
        editor.setCharacterLimit(Script.MAX_CHARS);
        editor.setValue(BridgeClient.script);
        editor.setValueListener(value->{if(!syncing)BridgeClient.replace(value);});
        addRenderableWidget(editor);
        int w=(width-28)/4, bottom=height-81;
        button(Messages.text("ai_block_bridge.button.import"),8,bottom,w,this::importFile);
        button(Messages.text("ai_block_bridge.button.export"),12+w,bottom,w,this::exportFile);
        button(Messages.text("ai_block_bridge.button.copy"),16+w*2,bottom,w,()->{
            minecraft.keyboardHandler.setClipboard(BridgeClient.script);BridgeClient.status=Messages.text("ai_block_bridge.copied");
        });
        button(Messages.text("ai_block_bridge.button.undo_text"),20+w*3,bottom,w,this::undoText);
        button(Messages.text("ai_block_bridge.button.capture"),8,bottom+24,w,()->{
            if(applyCoordinates())confirm(Messages.text("ai_block_bridge.confirm.capture_title"), Messages.text("ai_block_bridge.confirm.capture"),()->BridgeClient.send(BridgePacket.EXPORT));
        });
        exportUndoButton=button(Messages.text("ai_block_bridge.button.undo_capture"),12+w,bottom+24,w,()->{
            if(BridgeClient.exportUndo==null)return;
            confirm(Messages.text("ai_block_bridge.button.undo_capture"), Messages.text("ai_block_bridge.confirm.undo_capture"),()->{
                BridgeClient.replace(BridgeClient.exportUndo);BridgeClient.exportUndo=null;syncText();BridgeClient.status=Messages.text("ai_block_bridge.capture_undone");
            });
        });
        button(Messages.text("ai_block_bridge.button.paste"),16+w*2,bottom+24,w,()->{
            try {
                if(!applyCoordinates())return;
                Region r=BridgeClient.region();
                int count=Script.parse(BridgeClient.script,r).size();
                confirm(Messages.text("ai_block_bridge.confirm.paste_title"),Messages.text("ai_block_bridge.confirm.paste", r.description(), count),()->BridgeClient.send(BridgePacket.PASTE));
            }catch(Exception ex){BridgeClient.status=ex.getMessage();}
        });
        button(Messages.text("ai_block_bridge.button.undo_paste"),20+w*3,bottom+24,w,()->confirm(Messages.text("ai_block_bridge.confirm.undo_title"), Messages.text("ai_block_bridge.confirm.undo"),()->BridgeClient.send(BridgePacket.UNDO)));
    }
    private boolean applyCoordinates() {
        try {
            int[] v=new int[6];for(int i=0;i<6;i++)v[i]=Integer.parseInt(coordinates[i].getValue());
            Region r=Region.of(v[0],v[1],v[2],v[3],v[4],v[5]);
            BridgeClient.a=new BlockPos(v[0],v[1],v[2]);BridgeClient.b=new BlockPos(v[3],v[4],v[5]);
            BridgeClient.status=Messages.text("ai_block_bridge.applied", r.description());
            return true;
        }catch(Exception ex){BridgeClient.status=Messages.text("ai_block_bridge.coordinates_failed", ex.getMessage());return false;}
    }
    private void confirm(String title,String message,Runnable yes) {
        minecraft.gui.setScreen(new ConfirmScreen(accepted->{minecraft.gui.setScreen(this);if(accepted)yes.run();},Messages.component(title),Messages.component(message)));
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
            confirm(Messages.text("ai_block_bridge.button.import"),Messages.text("ai_block_bridge.confirm.import", file.getFileName()),()->{
                BridgeClient.replace(text);syncText();BridgeClient.status=Messages.text("ai_block_bridge.loaded", file.getFileName());
            });
        }catch(Exception ex){BridgeClient.status=Messages.text("ai_block_bridge.load_failed", ex.getMessage());}
    }
    private void exportFile() {
        try {
            Path file=ScriptFiles.choose(true);if(file==null)return;
            Runnable save=()->{
                try{ScriptFiles.write(file,BridgeClient.script);BridgeClient.status=Messages.text("ai_block_bridge.saved", file.getFileName());}
                catch(Exception ex){BridgeClient.status=Messages.text("ai_block_bridge.save_failed", ex.getMessage());}
            };
            if(Files.exists(file))confirm(Messages.text("ai_block_bridge.confirm.overwrite_title"),Messages.text("ai_block_bridge.confirm.overwrite", file.getFileName()),save);else save.run();
        }catch(Exception ex){BridgeClient.status=Messages.text("ai_block_bridge.save_failed", ex.getMessage());}
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
        g.text(font,font.plainSubstrByWidth(Messages.display(region),width-16),8,103,0xFFCCCCCC,false);
        g.text(font,font.plainSubstrByWidth(Messages.display(BridgeClient.status),width-16),8,height-29,0xFFFFDF8D,false);
        g.text(font,font.plainSubstrByWidth(Messages.display(Messages.text("ai_block_bridge.editor.hint")),width-16),8,height-15,0xFFAAAAAA,false);
    }
    @Override public boolean isPauseScreen(){return false;}
}
