package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.Messages;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class BlockRecordingOptionsScreen extends Screen {
    private final Screen parent;private Button states,nbt;
    public BlockRecordingOptionsScreen(Screen parent){super(Messages.component(Messages.text("ai_block_bridge.record_options.blocks")));this.parent=parent;}
    private Button toggle(String key,int y,java.util.function.BooleanSupplier value,Runnable change){
        java.util.function.Supplier<net.minecraft.network.chat.Component> label=()->Messages.component(Messages.text(key,Messages.text(value.getAsBoolean()?"ai_block_bridge.on":"ai_block_bridge.off")));
        Button b=addRenderableWidget(Button.builder(label.get(),button->{change.run();button.setMessage(label.get());}).bounds(8,y,width-16,20).build());
        b.setTooltip(Tooltip.create(Messages.component(Messages.text(key+"_hint"))));return b;
    }
    @Override protected void init(){
        states=toggle("ai_block_bridge.record_options.block_states",40,()->BridgeClient.shortBlockStates,()->BridgeClient.shortBlockStates=!BridgeClient.shortBlockStates);
        nbt=toggle("ai_block_bridge.record_options.block_nbt",68,()->BridgeClient.blockNbtDelta,()->BridgeClient.blockNbtDelta=!BridgeClient.blockNbtDelta);
        addRenderableWidget(Button.builder(Messages.component(Messages.text("ai_block_bridge.menu.back")),b->onClose()).bounds(8,height-28,width-16,20).build());
    }
    @Override public void tick(){states.active=nbt.active=!BridgeClient.busy()&&!BridgeClient.recording&&!BridgeClient.recordingAvailable;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){super.extractRenderState(g,mx,my,d);g.text(font,title,8,8,0xFFFFFFFF,true);g.text(font,font.plainSubstrByWidth(Messages.display(Messages.text("ai_block_bridge.record_options.hint")),width-16),8,104,0xFFCCCCCC,false);}
    @Override public boolean isPauseScreen(){return false;}
}
