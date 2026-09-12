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
    private final String initialStructure;
    private final BlockState[] previousStates;
    private final Map<Integer,CompoundTag> previousNbt=new HashMap<>();
    private final StringBuilder text;
    private int tick,changes;
    private boolean stopped, noticeSent;
    private String stopNotice;
    private final int maxTicks, maxChanges, maxChars;

    TickRecorder(ServerLevel level,Region region) {
        this(level,region,true);
    }
    TickRecorder(ServerLevel level,Region region,boolean ignoreHopperCooldown) {
        this(level,region,ignoreHopperCooldown,MAX_TICKS,MAX_CHANGES,Script.MAX_TIMELINE_CHARS);
    }
    // Bounded limits also let game tests exercise every stop condition cheaply.
    TickRecorder(ServerLevel level,Region region,boolean ignoreHopperCooldown,int maxTicks,int maxChanges,int maxChars) {
        if(maxTicks<1||maxTicks>MAX_TICKS||maxChanges<1||maxChanges>MAX_CHANGES
            ||maxChars<512||maxChars>Script.MAX_TIMELINE_CHARS)throw new IllegalArgumentException("Invalid recording limits");
        this.maxTicks=maxTicks;this.maxChanges=maxChanges;this.maxChars=maxChars;
        this.ignoreHopperCooldown=ignoreHopperCooldown;
        this.region=region;
        // Both snapshots run on the server thread before another tick can advance.
        this.initialStructure=BridgeServer.exportRegion(level,region);
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
    RecordingBundle bundle(){return new RecordingBundle(initialStructure,result());}
    void capture(ServerLevel level) {
        if(stopped)return;
        tick++;
        try { BridgeServer.validateRegion(level,region); }
        catch(Exception ex) { finish("Recording stopped: region unavailable.","unavailable");return; }
        StringBuilder changed=new StringBuilder();
        int count=0,index=0;
        for(BlockPos p:positions()) {
            BlockState state=level.getBlockState(p);
            CompoundTag nbt=readNbt(level,p,state);
            CompoundTag oldNbt=previousStates[index].hasBlockEntity()?previousNbt.get(index):null;
            if(!state.equals(previousStates[index]) || !Objects.equals(nbt,oldNbt)) {
                count++;
                if(changes+count>maxChanges) { finish("Entry limit: the final tick was omitted rather than partially recorded.","entries_partial",maxChanges);return; }
                append(changed,new BridgeServer.Cell(p,state,nbt));
                if((long)text.length()+changed.length()>maxChars-288) {
                    finish("Character limit: the final tick was omitted rather than partially recorded.","size_partial",maxChars);return;
                }
                previousStates[index]=state;
                if(nbt==null)previousNbt.remove(index);else previousNbt.put(index,nbt);
            }
            index++;
        }
        if(count>0) {
            String section="\n@tick "+tick+'\n'+changed;
            if(changes+count>maxChanges) { finish("Entry limit: the final tick was omitted rather than partially recorded.","entries_partial",maxChanges);return; }
            if((long)text.length()+section.length()>maxChars-256) { finish("Character limit: the final tick was omitted rather than partially recorded.","size_partial",maxChars);return; }
            text.append(section);changes+=count;
        }
        if(changes>=maxChanges)finish("Reached the "+maxChanges+"-entry limit.","entries",maxChanges);
        else if(tick>=maxTicks)finish("Reached the "+maxTicks+"-tick recording limit.","time",maxTicks);
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
    private void finish(String reason,String key,Object... args) {
        stopped=true;stopNotice=Messages.text("ai_block_bridge.recording.stop."+key,args);
        text.append("\n# ").append(reason).append('\n');
    }
    boolean stopped() { return stopped; }
    String takeStopNotice() {
        if(!stopped||noticeSent)return null;
        noticeSent=true;return stopNotice;
    }
    String result() {
        return text.toString()+"\n# recorded ticks: "+tick+" / changed entries: "+changes+'\n';
    }
}
