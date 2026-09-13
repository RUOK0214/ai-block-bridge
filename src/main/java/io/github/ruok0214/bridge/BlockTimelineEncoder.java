package io.github.ruok0214.bridge;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.commands.arguments.blocks.BlockStateParser;

/** Observation format. First change at each position is self-contained. */
final class BlockTimelineEncoder {
    private final boolean shortStates,delta;
    private final Map<String,Integer> palette=new HashMap<>();
    private final Map<Integer,BlockState> states=new HashMap<>();
    private final Map<Integer,CompoundTag> tags=new HashMap<>();
    BlockTimelineEncoder(boolean shortStates,boolean delta){this.shortStates=shortStates;this.delta=delta;}
    boolean enabled(){return shortStates||delta;}
    String header(){return !enabled()?"":"# Block encoding v2 (observation comments; not paste instructions).\n"
        +"# short block states: "+shortStates+" / block NBT delta: "+delta+"\n"
        +"# @block-state B<number> = full block state; palette IDs never reused.\n"
        +"# @block full/patch x y z | state or B<number> | SNBT or none\n"
        +"# First change per position and block-type replacement are full. none clears NBT.\n"
        +"# patch applies remove, then top-level set; nested values replace whole.\n"
        +"# Optional slots patch: remove slot numbers, then replace slots from set list (Slot identifies each item).\n"
        +"# slots patches update Items only; missing slots field leaves Items unchanged. Empty inventory is Items:[].\n";}
    static CompoundTag clean(CompoundTag tag){if(tag==null)return null;var t=tag.copy();t.remove("x");t.remove("y");t.remove("z");return t;}
    private static Map<Integer,CompoundTag> inventory(Tag tag){
        if(!(tag instanceof ListTag list))return null;
        var result=new TreeMap<Integer,CompoundTag>();
        for(Tag value:list){
            if(!(value instanceof CompoundTag item)||!(item.get("Slot") instanceof NumericTag slot))return null;
            if(result.put(slot.intValue(),item)!=null)return null;
        }
        return result;
    }
    static CompoundTag diff(CompoundTag before,CompoundTag after){
        var a=before.copy();var b=after.copy();
        var oldSlots=inventory(a.get("Items"));var newSlots=inventory(b.get("Items"));
        CompoundTag slotPatch=null;
        if(oldSlots!=null&&newSlots!=null){
            a.remove("Items");b.remove("Items");
            ListTag set=new ListTag(),remove=new ListTag();
            for(var e:newSlots.entrySet())if(!Objects.equals(oldSlots.get(e.getKey()),e.getValue()))set.add(e.getValue().copy());
            for(var key:oldSlots.keySet())if(!newSlots.containsKey(key))remove.add(IntTag.valueOf(key));
            if(!set.isEmpty()||!remove.isEmpty()){slotPatch=new CompoundTag();slotPatch.put("set",set);slotPatch.put("remove",remove);}
        }
        var patch=EntityTimelineEncoder.diff(a,b);if(slotPatch!=null)patch.put("slots",slotPatch);return patch;
    }
    String line(int index,String position,BlockState state,CompoundTag raw){
        var tag=clean(raw);var before=tags.get(index);var oldState=states.get(index);
        String prefix="",name=BlockStateParser.serialize(state);
        if(shortStates){Integer id=palette.get(name);if(id==null){id=palette.size()+1;palette.put(name,id);prefix="# @block-state B"+id+" = "+name+'\n';}name="B"+id;}
        boolean patch=delta&&oldState!=null&&oldState.getBlock()==state.getBlock()&&before!=null&&tag!=null;
        String payload=tag==null?"none":patch?diff(before,tag).toString():tag.toString();
        states.put(index,state);if(tag==null)tags.remove(index);else tags.put(index,tag);
        return prefix+"# @block "+(patch?"patch":"full")+" "+position+" | "+name+" | "+payload+'\n';
    }
}
