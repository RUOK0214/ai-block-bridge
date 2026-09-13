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
    private final List<Button> actions=new ArrayList<>();
    private MultiLineEditBox editor;
    private Button exportUndoButton,entities,currentTab;
    private boolean syncing;
    public BridgeScreen() { super(Messages.component("AI Block Bridge")); }
    private Button button(String title,int x,int y,int w,Runnable action) {
        Button b=addRenderableWidget(Button.builder(Messages.component(title),btn->action.run()).bounds(x,y,w,20).build());
        actions.add(b);return b;
    }
    @Override protected void init() {
        actions.clear();
        button(Messages.text("ai_block_bridge.button.close"),width-66,4,58,this::onClose);
        int nav=(width-28)/4;
        currentTab=button(Messages.text("ai_block_bridge.menu.structure"),8,24,nav,()->{});
        button(Messages.text("ai_block_bridge.menu.timeline"),12+nav,24,nav,()->minecraft.gui.setScreen(new TimelineScreen()));
        button(Messages.text("ai_block_bridge.prompt.open"),16+nav*2,24,nav,()->minecraft.gui.setScreen(new AiPromptScreen(this)));
        button(Messages.text("ai_block_bridge.button.help"),20+nav*3,24,nav,()->confirm(Messages.text("ai_block_bridge.help.title"),Messages.text("ai_block_bridge.help.body",Region.MAX_BLOCKS_TEXT),()->{}));
        button(Messages.text("ai_block_bridge.menu.area"),width-100,48,92,()->minecraft.gui.setScreen(new RegionScreen(this)));
        int tools=(width-24)/3;
        button(Messages.text("ai_block_bridge.button.capture"),8,72,tools,()->{
            if(checkRegion())confirm(Messages.text("ai_block_bridge.confirm.capture_title"),Messages.text("ai_block_bridge.confirm.capture"),()->BridgeClient.send(BridgePacket.EXPORT));
        });
        exportUndoButton=button(Messages.text("ai_block_bridge.button.undo_capture"),12+tools,72,tools,()->{
            if(BridgeClient.exportUndo==null)return;
            confirm(Messages.text("ai_block_bridge.button.undo_capture"),Messages.text("ai_block_bridge.confirm.undo_capture"),()->{
                BridgeClient.replace(BridgeClient.exportUndo);BridgeClient.exportUndo=null;syncText();BridgeClient.status=Messages.text("ai_block_bridge.capture_undone");
            });
        });
        entities=button(entityLabel(),16+tools*2,72,tools,()->{
            BridgeClient.includeStructureEntities=!BridgeClient.includeStructureEntities;
            entities.setMessage(Messages.component(entityLabel()));
        });
        entities.setTooltip(Tooltip.create(Messages.component(Messages.text("ai_block_bridge.entities.structure_hint"))));
        editor=MultiLineEditBox.builder().setX(8).setY(104).setShowDecorations(false)
            .build(font,width-16,Math.max(12,height-182),Messages.component(Messages.text("ai_block_bridge.editor.script")));
        editor.setCharacterLimit(Script.MAX_CHARS);
        editor.setValue(BridgeClient.script);
        editor.setValueListener(value->{if(!syncing)BridgeClient.replace(value);});
        addRenderableWidget(editor);
        int w=(width-28)/4, bottom=height-48;
        button(Messages.text("ai_block_bridge.button.import"),8,bottom,w,this::importFile);
        button(Messages.text("ai_block_bridge.button.export"),12+w,bottom,w,this::exportFile);
        button(Messages.text("ai_block_bridge.button.copy"),16+w*2,bottom,w,()->{
            minecraft.keyboardHandler.setClipboard(BridgeClient.script);BridgeClient.status=Messages.text("ai_block_bridge.copied");
        });
        button(Messages.text("ai_block_bridge.button.undo_text"),20+w*3,bottom,w,this::undoText);
        int world=(width-24)/3;
        button(Messages.text("ai_block_bridge.button.validate"),8,height-72,world,()->{
            try { BridgeClient.status=Messages.text("ai_block_bridge.validated",Script.parse(BridgeClient.script,BridgeClient.region()).size()); }
            catch(Exception ex){BridgeClient.status=ex.getMessage();}
        });
        button(Messages.text("ai_block_bridge.button.paste"),12+world,height-72,world,()->{
            try {
                Region r=BridgeClient.region();int count=Script.parse(BridgeClient.script,r).size();
                confirm(Messages.text("ai_block_bridge.confirm.paste_title"),Messages.text("ai_block_bridge.confirm.paste",r.description(),count),()->BridgeClient.send(BridgePacket.PASTE));
            }catch(Exception ex){BridgeClient.status=ex.getMessage();}
        });
        button(Messages.text("ai_block_bridge.button.undo_paste"),16+world*2,height-72,world,()->confirm(Messages.text("ai_block_bridge.confirm.undo_title"),Messages.text("ai_block_bridge.confirm.undo"),()->BridgeClient.send(BridgePacket.UNDO)));
        currentTab.active=false;

    }
    private String entityLabel(){return Messages.text("ai_block_bridge.entities.structure",Messages.text(BridgeClient.includeStructureEntities?"ai_block_bridge.on":"ai_block_bridge.off"));}
    private boolean checkRegion() {
        try { BridgeClient.region();return true; }
        catch(Exception ex){BridgeClient.status=ex.getMessage();return false;}
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
        currentTab.active=false;
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
        String region;
        try{region=BridgeClient.region().description();}catch(Exception ex){region=ex.getMessage();}
        g.text(font,font.plainSubstrByWidth(Messages.display(region),width-116),8,54,0xFFCCCCCC,false);
        g.text(font,Messages.component(Messages.text("ai_block_bridge.menu.structure_editor")),8,95,0xFF9DDBFF,false);
        g.text(font,font.plainSubstrByWidth(Messages.display(BridgeClient.status),width-16),8,height-22,0xFFFFDF8D,false);
        g.text(font,font.plainSubstrByWidth(Messages.display(Messages.text("ai_block_bridge.editor.hint")),width-16),8,height-10,0xFFAAAAAA,false);
    }
    @Override public boolean isPauseScreen(){return false;}
}
