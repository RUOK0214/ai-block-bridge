package io.github.ruok0214.bridge;

import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/** Captures end-of-server-tick state changes without modifying the world. */
final class TickRecorder {
    static final int MAX_TICKS=1200;
    private final Region region;
    private List<BridgeServer.Cell> previous;
    private final StringBuilder text;
    private int tick,changes;
    private boolean stopped;

    TickRecorder(ServerLevel level,Region region) {
        this.region=region;
        this.previous=snapshot(level);
        this.text=new StringBuilder("# AI Block Bridge Timeline v1\n# size: "+region.sizeX()+" "+region.sizeY()+" "+region.sizeZ()+
            "\n# origin: "+region.x()+" "+region.y()+" "+region.z()+
            "\n# Only changes after recording started. @tick is a server-tick offset.\n");
    }
    void capture(ServerLevel level) {
        if(stopped)return;
        tick++;
        try { BridgeServer.validateRegion(level,region); }
        catch(Exception ex) { finish("영역을 불러올 수 없어 기록을 종료했습니다.");return; }
        List<BridgeServer.Cell> current=snapshot(level);
        StringBuilder changed=new StringBuilder();
        int count=0;
        for(int i=0;i<current.size();i++) if(!current.get(i).equals(previous.get(i))) {
            append(changed,current.get(i));count++;
        }
        if(count>0) {
            String section="\n@tick "+tick+'\n'+changed;
            if((long)text.length()+section.length()>Script.MAX_CHARS) { finish("스크립트가 2,000,000자를 넘어 기록을 종료했습니다.");return; }
            text.append(section);changes+=count;
        }
        previous=current;
        if(tick>=MAX_TICKS)finish("최대 기록 시간 1,200틱에 도달했습니다.");
    }
    private List<BridgeServer.Cell> snapshot(ServerLevel level) {
        var cells=new ArrayList<BridgeServer.Cell>(region.volume());
        for(BlockPos p:BlockPos.betweenClosed(region.x(),region.y(),region.z(),region.maxX(),region.maxY(),region.maxZ()))
            cells.add(BridgeServer.snapshot(level,p));
        return List.copyOf(cells);
    }
    private void append(StringBuilder out,BridgeServer.Cell cell) {
        BlockPos p=cell.pos();
        out.append(p.getX()-region.x()).append(' ').append(p.getY()-region.y()).append(' ').append(p.getZ()-region.z())
            .append(" | ").append(BlockStateParser.serialize(cell.state()));
        if(cell.nbt()!=null) {
            CompoundTag tag=cell.nbt().copy();tag.remove("x");tag.remove("y");tag.remove("z");
            out.append(" | ").append(tag);
        }
        out.append('\n');
    }
    private void finish(String reason) {
        stopped=true;text.append("\n# ").append(reason).append('\n');
    }
    boolean stopped() { return stopped; }
    String result() {
        text.append("\n# recorded ticks: ").append(tick).append(" / changed entries: ").append(changes).append('\n');
        return text.toString();
    }
}
