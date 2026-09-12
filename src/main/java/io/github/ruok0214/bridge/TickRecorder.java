package io.github.ruok0214.bridge;

import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Captures end-of-server-tick state changes without modifying the world. */
final class TickRecorder {
    static final int MAX_TICKS=6000, MAX_CHANGES=100_000;
    private final boolean ignoreHopperCooldown;
    private final Region region;
    private final BlockState[] previousStates;
    private final Map<Integer,CompoundTag> previousNbt=new HashMap<>();
    private final StringBuilder text;
    private int tick,changes;
    private boolean stopped;

    TickRecorder(ServerLevel level,Region region) {
        this(level,region,true);
    }
    TickRecorder(ServerLevel level,Region region,boolean ignoreHopperCooldown) {
        this.ignoreHopperCooldown=ignoreHopperCooldown;
        this.region=region;
        this.previousStates=new BlockState[region.volume()];
        int index=0;
        for(BlockPos p:positions()) {
            BlockState state=level.getBlockState(p);
            previousStates[index]=state;
            CompoundTag nbt=readNbt(level,p,state);
            if(nbt!=null)previousNbt.put(index,nbt);
            index++;
        }
        this.text=new StringBuilder("# AI Block Bridge Timeline v1\n# size: "+region.sizeX()+" "+region.sizeY()+" "+region.sizeZ()+
            "\n# origin: "+region.x()+" "+region.y()+" "+region.z()+
            "\n# Only changes after recording started. @tick is a server-tick offset.\n# ignore hopper TransferCooldown: "+ignoreHopperCooldown+"\n");
    }
    void capture(ServerLevel level) {
        if(stopped)return;
        tick++;
        try { BridgeServer.validateRegion(level,region); }
        catch(Exception ex) { finish("Recording stopped: region unavailable.");return; }
        StringBuilder changed=new StringBuilder();
        int count=0,index=0;
        for(BlockPos p:positions()) {
            BlockState state=level.getBlockState(p);
            CompoundTag nbt=readNbt(level,p,state);
            CompoundTag oldNbt=previousStates[index].hasBlockEntity()?previousNbt.get(index):null;
            if(!state.equals(previousStates[index]) || !Objects.equals(nbt,oldNbt)) {
                count++;
                if(changes+count>MAX_CHANGES) { finish("100,000-entry limit: the final tick was omitted rather than partially recorded.");return; }
                append(changed,new BridgeServer.Cell(p,state,nbt));
                if((long)text.length()+changed.length()>Script.MAX_TIMELINE_CHARS-288) {
                    finish("20,000,000-character limit: the final tick was omitted rather than partially recorded.");return;
                }
                previousStates[index]=state;
                if(nbt==null)previousNbt.remove(index);else previousNbt.put(index,nbt);
            }
            index++;
        }
        if(count>0) {
            String section="\n@tick "+tick+'\n'+changed;
            if(changes+count>MAX_CHANGES) { finish("100,000-entry limit: the final tick was omitted rather than partially recorded.");return; }
            if((long)text.length()+section.length()>Script.MAX_TIMELINE_CHARS-256) { finish("Reached the 20,000,000-character limit.");return; }
            text.append(section);changes+=count;
        }
        if(changes>=MAX_CHANGES)finish("Reached the 100,000-entry limit.");
        else if(tick>=MAX_TICKS)finish("Reached the 6,000-tick recording limit.");
    }
    private Iterable<BlockPos> positions() {
        return BlockPos.betweenClosed(region.x(),region.y(),region.z(),region.maxX(),region.maxY(),region.maxZ());
    }
    private CompoundTag readNbt(ServerLevel level,BlockPos p,BlockState state) {
        if(!state.hasBlockEntity())return null;
        var be=level.getBlockEntity(p);
        if(be==null)return null;
        CompoundTag tag=be.saveWithFullMetadata(level.registryAccess());
        if(ignoreHopperCooldown && state.is(net.minecraft.world.level.block.Blocks.HOPPER))tag.remove("TransferCooldown");
        return tag;
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
