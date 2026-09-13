package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;

/** Coordinate edits are committed together only when Apply is pressed. */
public final class RegionScreen extends Screen {
    private final Screen parent;
    private final EditBox[] fields=new EditBox[6];
    private Button apply;
    private String notice="";
    public RegionScreen(Screen parent){super(Messages.component(Messages.text("ai_block_bridge.menu.area")));this.parent=parent;}
    private void button(String key,int x,int y,int w,Runnable action){
        addRenderableWidget(Button.builder(Messages.component(Messages.text(key)),b->action.run()).bounds(x,y,w,20).build());
    }
    @Override protected void init(){
        int fieldWidth=(width-112)/3;
        for(int row=0;row<2;row++){
            BlockPos p=row==0?BridgeClient.a:BridgeClient.b;
            int[] initial=p==null?new int[]{0,0,0}:new int[]{p.getX(),p.getY(),p.getZ()};
            for(int col=0;col<3;col++){
                int i=row*3+col;
                String value=fields[i]==null?Integer.toString(initial[col]):fields[i].getValue();
                EditBox f=new EditBox(font,40+col*(fieldWidth+3),52+row*32,fieldWidth,20,Messages.component(Messages.text("ai_block_bridge.corner_field",row+1,"XYZ".charAt(col))));
                f.setMaxLength(11);f.setValue(value);fields[i]=addRenderableWidget(f);
            }
            int r=row;
            button("ai_block_bridge.button.position",width-57,52+row*32,49,()->{
                if(minecraft.player==null)return;
                BlockPos here=minecraft.player.blockPosition();
                fields[r*3].setValue(""+here.getX());fields[r*3+1].setValue(""+here.getY());fields[r*3+2].setValue(""+here.getZ());
            });
        }
        apply=addRenderableWidget(Button.builder(Messages.component(Messages.text("ai_block_bridge.button.apply")),b->apply()).bounds(8,128,(width-20)/2,20).build());
        button("ai_block_bridge.menu.back",12+(width-20)/2,128,width-20-(width-20)/2,this::onClose);
    }
    private void apply(){
        if(BridgeClient.busy()||BridgeClient.recording||BridgeClient.recordingAvailable)return;
        try{
            int[] v=new int[6];for(int i=0;i<6;i++)v[i]=Integer.parseInt(fields[i].getValue());
            Region r=Region.of(v[0],v[1],v[2],v[3],v[4],v[5]);
            BridgeClient.a=new BlockPos(v[0],v[1],v[2]);BridgeClient.b=new BlockPos(v[3],v[4],v[5]);
            BridgeClient.status=BridgeClient.timelineStatus=Messages.text("ai_block_bridge.applied",r.description());
            onClose();
        }catch(Exception ex){notice=Messages.text("ai_block_bridge.coordinates_failed",ex.getMessage());}
    }
    @Override public void tick(){
        apply.active=!BridgeClient.busy()&&!BridgeClient.recording&&!BridgeClient.recordingAvailable;
        for(EditBox f:fields)f.setEditable(apply.active);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);
        g.text(font,title,8,8,0xFFFFFFFF,true);
        g.text(font,font.plainSubstrByWidth(Messages.display(Messages.text("ai_block_bridge.menu.area_hint")),width-16),8,30,0xFFCCCCCC,false);
        g.text(font,"1 XYZ",8,58,0xFF9DDBFF,false);g.text(font,"2 XYZ",8,90,0xFFFFCF82,false);
        g.text(font,font.plainSubstrByWidth(Messages.display(notice),width-16),8,158,0xFFFFDF8D,false);
    }
    @Override public boolean isPauseScreen(){return false;}
}
