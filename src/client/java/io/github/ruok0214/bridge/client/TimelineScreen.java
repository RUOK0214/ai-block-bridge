package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.*;
import java.nio.file.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.input.*;
import org.lwjgl.glfw.GLFW;

/** Editor for recorded tick order. It is intentionally separate from the placement script. */
public final class TimelineScreen extends Screen {
    private MultiLineEditBox editor;
    private Button start,stop,load,save,copy,undo,filter,bundle,entities;
    private boolean syncing;
    public TimelineScreen(){super(Messages.component(Messages.text("ai_block_bridge.timeline.title")));}
    private Button button(String title,int x,int y,int w,Runnable action) {
        return addRenderableWidget(Button.builder(Messages.component(title),b->action.run()).bounds(x,y,w,20).build());
    }
    @Override protected void init() {
        int w=(width-28)/4;
        start=button(Messages.text("ai_block_bridge.button.record_start"),8,28,w,()->confirm(Messages.text("ai_block_bridge.confirm.record_title"),Messages.text("ai_block_bridge.confirm.record"),()->{
            BridgeClient.send(BridgePacket.START_RECORD);if(BridgeClient.busy())minecraft.gui.setScreen(null);
        }));
        stop=button(Messages.text("ai_block_bridge.button.record_stop"),12+w,28,w,()->BridgeClient.send(BridgePacket.STOP_RECORD));
        button(Messages.text("ai_block_bridge.button.script"),16+w*2,28,w,()->minecraft.gui.setScreen(new BridgeScreen()));
        button(Messages.text("ai_block_bridge.prompt.open"),20+w*3,28,w,()->minecraft.gui.setScreen(new AiPromptScreen(this)));
        button(Messages.text("ai_block_bridge.button.close"),width-66,4,58,this::onClose);
        filter=button(filterLabel(),8,72,width-16,()->{
            BridgeClient.ignoreHopperCooldown=!BridgeClient.ignoreHopperCooldown;
            filter.setMessage(Messages.component(filterLabel()));
        });
        entities=button(entityLabel(),width-158,50,150,()->{
            BridgeClient.includeTimelineEntities=!BridgeClient.includeTimelineEntities;
            entities.setMessage(Messages.component(entityLabel()));
        });
        entities.setTooltip(Tooltip.create(Messages.component(Messages.text("ai_block_bridge.entities.timeline_hint"))));
        editor=MultiLineEditBox.builder().setX(8).setY(98).setShowDecorations(true)
            .build(font,width-16,Math.max(40,height-197),Messages.component(Messages.text("ai_block_bridge.editor.timeline")));
        editor.setCharacterLimit(Script.MAX_TIMELINE_CHARS);editor.setValue(BridgeClient.timeline);
        editor.setValueListener(value->{if(!syncing)BridgeClient.replaceTimeline(value);});addRenderableWidget(editor);
        bundle=button(Messages.text("ai_block_bridge.bundle.save"),8,height-91,(width-20)/2,this::exportBundle);
        bundle.active=BridgeClient.recordingBundle!=null;
        button(Messages.text("ai_block_bridge.bundle.open_folder"),12+(width-20)/2,height-91,width-20-(width-20)/2,this::openRecordingFolder);
        int bottom=height-67;
        load=button(Messages.text("ai_block_bridge.button.import"),8,bottom,w,this::importFile);
        save=button(Messages.text("ai_block_bridge.button.export"),12+w,bottom,w,this::exportFile);
        copy=button(Messages.text("ai_block_bridge.button.copy"),16+w*2,bottom,w,()->{
            minecraft.keyboardHandler.setClipboard(BridgeClient.timeline);BridgeClient.timelineStatus=Messages.text("ai_block_bridge.timeline.copied");
        });
        undo=button(Messages.text("ai_block_bridge.button.undo_text"),20+w*3,bottom,w,this::undoText);
    }
    private void confirm(String title,String message,Runnable yes) {
        minecraft.gui.setScreen(new ConfirmScreen(ok->{minecraft.gui.setScreen(this);if(ok)yes.run();},Messages.component(title),Messages.component(message)));
    }
    public void syncText(){if(editor!=null&&!editor.getValue().equals(BridgeClient.timeline)){syncing=true;editor.setValue(BridgeClient.timeline);syncing=false;}}
    private void undoText(){BridgeClient.timeline=BridgeClient.timelineHistory.undo(BridgeClient.timeline);syncText();}
    private void importFile(){try{
        Path file=ScriptFiles.choose(false,"timeline.txt");if(file==null)return;String value=ScriptFiles.read(file,Script.MAX_TIMELINE_CHARS);
        confirm(Messages.text("ai_block_bridge.timeline.import_title"),Messages.text("ai_block_bridge.confirm.import_timeline", file.getFileName()),()->{BridgeClient.replaceTimeline(value);syncText();BridgeClient.timelineStatus=Messages.text("ai_block_bridge.loaded", file.getFileName());});
    }catch(Exception ex){BridgeClient.timelineStatus=Messages.text("ai_block_bridge.load_failed", ex.getMessage());}}
    private void exportBundle(){try{
        RecordingBundle original=BridgeClient.recordingBundle;if(original==null)return;
        Path parent=ScriptFiles.chooseDirectory();if(parent==null)return;
        Path folder=RecordingFiles.save(parent,original);
        ScriptFiles.rememberRecordingFolder(folder);
        BridgeClient.timelineStatus=Messages.text("ai_block_bridge.saved",folder);
    }catch(Exception ex){BridgeClient.timelineStatus=Messages.text("ai_block_bridge.save_failed",ex.getMessage());}}
    private void openRecordingFolder(){try{
        net.minecraft.util.Util.getPlatform().openFile(ScriptFiles.recordingFolder().toFile());
    }catch(Exception ex){BridgeClient.timelineStatus=Messages.text("ai_block_bridge.bundle.open_failed",ex.getMessage());}}
    private void exportFile(){try{
        Path file=ScriptFiles.choose(true,"timeline.txt");if(file==null)return;Runnable write=()->{try{ScriptFiles.write(file,BridgeClient.timeline);BridgeClient.timelineStatus=Messages.text("ai_block_bridge.saved", file.getFileName());}catch(Exception ex){BridgeClient.timelineStatus=Messages.text("ai_block_bridge.save_failed", ex.getMessage());}};
        if(Files.exists(file))confirm(Messages.text("ai_block_bridge.confirm.overwrite_title"),Messages.text("ai_block_bridge.confirm.overwrite", file.getFileName()),write);else write.run();
    }catch(Exception ex){BridgeClient.timelineStatus=Messages.text("ai_block_bridge.save_failed", ex.getMessage());}}
    @Override public void tick(){
        bundle.active=!BridgeClient.busy()&&!BridgeClient.recording&&BridgeClient.recordingBundle!=null;
        entities.active=!BridgeClient.busy()&&!BridgeClient.recording&&!BridgeClient.recordingAvailable;
        filter.active=!BridgeClient.busy()&&!BridgeClient.recording;
        boolean idle=!BridgeClient.busy();start.active=idle&&!BridgeClient.recording&&!BridgeClient.recordingAvailable;stop.active=idle&&(BridgeClient.recording||BridgeClient.recordingAvailable);
        stop.setMessage(Messages.component(Messages.text(BridgeClient.recordingAvailable?"ai_block_bridge.recording.retrieve":"ai_block_bridge.button.record_stop")));
        boolean editable=idle&&!BridgeClient.recording;editor.active=editable;load.active=editable;save.active=idle;copy.active=idle;undo.active=editable;
    }
    private String entityLabel(){return Messages.text("ai_block_bridge.entities.timeline",Messages.text(BridgeClient.includeTimelineEntities?"ai_block_bridge.on":"ai_block_bridge.off"));}
    private String filterLabel(){return Messages.text("ai_block_bridge.filter", Messages.text(BridgeClient.ignoreHopperCooldown?"ai_block_bridge.on":"ai_block_bridge.off"));}
    @Override public boolean keyPressed(KeyEvent event){
        if(editor.isFocused()&&(event.modifiers()&(GLFW.GLFW_MOD_CONTROL|GLFW.GLFW_MOD_SUPER))!=0&&event.key()==GLFW.GLFW_KEY_Z){undoText();return true;}
        return super.keyPressed(event);
    }
    @Override public boolean charTyped(CharacterEvent event){return BridgeClient.recording||BridgeClient.busy()||super.charTyped(event);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);
        g.text(font,Messages.component(Messages.text("ai_block_bridge.timeline.heading")),8,8,0xFFFFFFFF,true);
        String region;try{region=BridgeClient.region().description();}catch(Exception ex){region=ex.getMessage();}
        g.text(font,font.plainSubstrByWidth((BridgeClient.recording?Messages.display(Messages.text("ai_block_bridge.timeline.recording")):"")+Messages.display(region),width-178),8,56,BridgeClient.recording?0xFFFF7777:0xFFCCCCCC,false);
        g.text(font,font.plainSubstrByWidth(Messages.display(BridgeClient.timelineStatus),width-16),8,height-39,0xFFFFDF8D,false);
        g.text(font,Messages.component(Messages.text("ai_block_bridge.timeline.hint")),8,height-15,0xFFAAAAAA,false);
    }
    @Override public boolean isPauseScreen(){return false;}
}
