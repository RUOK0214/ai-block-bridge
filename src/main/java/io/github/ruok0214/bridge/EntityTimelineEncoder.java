package io.github.ruok0214.bridge;

import java.util.*;
import net.minecraft.nbt.*;

/** Patches use the last emitted state, never the last sampled state. */
final class EntityTimelineEncoder {
    private final boolean delta, shortIds;
    private final Map<UUID,Integer> ids=new HashMap<>();
    private final Map<UUID,EntitySnapshot.State> emitted=new HashMap<>();
    EntityTimelineEncoder(boolean delta,boolean shortIds){this.delta=delta;this.shortIds=shortIds;}
    String header(){
        return EntitySnapshot.header().replace("UUID | relative x y z | type | full SNBT", "ID | relative x y z | type | SNBT (see compact rules below)")
            +"# entity delta: "+delta+" / short entity IDs: "+shortIds+"\n"
            +"# @entity-id E<number> = UUID; IDs are recording-local and never reused.\n"
            +"# initial/enter: full SNBT. patch: relative x y z | type | {set:{...},remove:[keys]}.\n"
            +"# Apply remove then replace each top-level set key; nested compounds/lists replace whole values.\n"
            +"# Patches refer to last emitted state. leave carries a final full state or patch, then ends membership.\n";
    }
    static CompoundTag diff(CompoundTag before,CompoundTag after){
        CompoundTag set=new CompoundTag();ListTag remove=new ListTag();
        for(String key:after.keySet())if(!Objects.equals(before.get(key),after.get(key)))set.put(key,after.get(key).copy());
        for(String key:before.keySet())if(!after.contains(key))remove.add(StringTag.valueOf(key));
        CompoundTag patch=new CompoundTag();patch.put("set",set);patch.put("remove",remove);return patch;
    }
    String line(String event,UUID uuid,EntitySnapshot.State state){
        String prefix="",id=uuid.toString();
        if(shortIds){
            Integer number=ids.get(uuid);
            if(number==null){number=ids.size()+1;ids.put(uuid,number);prefix="# @entity-id E"+number+" = "+uuid+'\n';}
            id="E"+number;
        }
        var before=emitted.get(uuid);
        boolean patch=delta&&before!=null&&!event.equals("initial")&&!event.equals("enter");
        String kind=patch?(event.equals("leave")?"leave-patch":"patch"):event;
        String nbt=patch?diff(before.tag(),state.tag()).toString():state.nbt();
        if(event.equals("leave"))emitted.remove(uuid);else emitted.put(uuid,state);
        return prefix+"# @entity "+kind+" "+id+" | "+state.x()+" "+state.y()+" "+state.z()+" | "+state.type()+" | "+nbt+'\n';
    }
}
