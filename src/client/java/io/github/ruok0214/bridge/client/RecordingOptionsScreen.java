package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.Messages;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class RecordingOptionsScreen extends Screen {
    private final Screen parent;
    private Button entities,noise,cooldown;
    public RecordingOptionsScreen(Screen parent){super(Messages.component(Messages.text("ai_block_bridge.record_options.title")));this.parent=parent;}
    private String label(String key,boolean on){return Messages.text(key,Messages.text(on?"ai_block_bridge.on":"ai_block_bridge.off"));}
    private Button toggle(String key,String hint,int y,java.util.function.BooleanSupplier value,Runnable change){
        Button b=addRenderableWidget(Button.builder(Messages.component(label(key,value.getAsBoolean())),button->{change.run();button.setMessage(Messages.component(label(key,value.getAsBoolean())));}).bounds(8,y,width-16,20).build());
        b.setTooltip(Tooltip.create(Messages.component(Messages.text(hint))));return b;
    }
    @Override protected void init(){
        entities=toggle("ai_block_bridge.entities.timeline","ai_block_bridge.entities.timeline_hint",40,()->BridgeClient.includeTimelineEntities,()->BridgeClient.includeTimelineEntities=!BridgeClient.includeTimelineEntities);
        noise=toggle("ai_block_bridge.record_options.noise","ai_block_bridge.record_options.noise_hint",68,()->BridgeClient.ignoreEntityAgeMotion,()->BridgeClient.ignoreEntityAgeMotion=!BridgeClient.ignoreEntityAgeMotion);
        cooldown=toggle("ai_block_bridge.menu.cooldown","ai_block_bridge.menu.cooldown_hint",96,()->BridgeClient.ignoreHopperCooldown,()->BridgeClient.ignoreHopperCooldown=!BridgeClient.ignoreHopperCooldown);
        addRenderableWidget(Button.builder(Messages.component(Messages.text("ai_block_bridge.menu.back")),b->onClose()).bounds(8,height-28,width-16,20).build());
    }
    @Override public void tick(){
        boolean idle=!BridgeClient.busy()&&!BridgeClient.recording&&!BridgeClient.recordingAvailable;
        entities.active=idle;noise.active=idle&&BridgeClient.includeTimelineEntities;cooldown.active=idle;
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);g.text(font,title,8,8,0xFFFFFFFF,true);
        g.text(font,font.plainSubstrByWidth(Messages.display(Messages.text("ai_block_bridge.record_options.hint")),width-16),8,132,0xFFCCCCCC,false);
    }
    @Override public boolean isPauseScreen(){return false;}
}
