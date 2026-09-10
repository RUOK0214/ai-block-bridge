package io.github.ruok0214.bridge;

import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/** Captures end-of-server-tick state changes without modifying the world. */
final class TickRecorder {
    static final int MAX_TICKS=6000, MAX_CHANGES=100_000;
    private final boolean ignoreHopperCooldown;
    private final Region region;
    private List<BridgeServer.Cell> previous;
    private final StringBuilder text;
    private int tick,changes;
    private boolean stopped;

    TickRecorder(ServerLevel level,Region region) {
        this(level,region,true);
    }
    TickRecorder(ServerLevel level,Region region,boolean ignoreHopperCooldown) {
        this.ignoreHopperCooldown=ignoreHopperCooldown;
        this.region=region;
        this.previous=snapshot(level);
        this.text=new StringBuilder("# AI Block Bridge Timeline v1\n# size: "+region.sizeX()+" "+region.sizeY()+" "+region.sizeZ()+
            "\n# origin: "+region.x()+" "+region.y()+" "+region.z()+
            "\n# Only changes after recording started. @tick is a server-tick offset.\n# ignore hopper TransferCooldown: "+ignoreHopperCooldown+"\n");
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
            if(changes+count>MAX_CHANGES) { finish("변경 항목 100,000개 제한: 마지막 틱은 부분 기록하지 않았습니다.");return; }
            if((long)text.length()+section.length()>Script.MAX_TIMELINE_CHARS-256) { finish("기록 용량 20,000,000자 제한에 도달했습니다.");return; }
            text.append(section);changes+=count;
        }
        previous=current;
        if(changes>=MAX_CHANGES)finish("변경 항목 100,000개에 도달했습니다.");
        else if(tick>=MAX_TICKS)finish("최대 기록 시간 6,000틱에 도달했습니다.");
    }
    private List<BridgeServer.Cell> snapshot(ServerLevel level) {
        var cells=new ArrayList<BridgeServer.Cell>(region.volume());
        for(BlockPos p:BlockPos.betweenClosed(region.x(),region.y(),region.z(),region.maxX(),region.maxY(),region.maxZ())) {
            var cell=BridgeServer.snapshot(level,p);
            if(ignoreHopperCooldown && cell.state().is(net.minecraft.world.level.block.Blocks.HOPPER) && cell.nbt()!=null) {
                var tag=cell.nbt().copy();tag.remove("TransferCooldown");
                cell=new BridgeServer.Cell(cell.pos(),cell.state(),tag);
            }
            cells.add(cell);
        }
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
        return text.toString()+"\n# recorded ticks: "+tick+" / changed entries: "+changes+'\n';
    }
}
