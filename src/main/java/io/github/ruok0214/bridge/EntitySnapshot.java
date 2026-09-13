package io.github.ruok0214.bridge;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;

/** Observation only: never creates, moves, loads, or deletes a world entity. */
final class EntitySnapshot {
    static final int MAX_ENTITIES=4096;
    record State(String type,double x,double y,double z,String nbt) {
        String line(String event,UUID id) {
            return "# @entity "+event+" "+id+" | "+x+" "+y+" "+z+" | "+type+" | "+nbt+'\n';
        }
    }
    static String header() {
        return "# Entity records are observation comments; block paste does not spawn entities.\n"
            +"# @entity initial/enter/update/leave UUID | relative x y z | type | full SNBT\n"
            +"# Relative position uses the selection origin; coordinates inside SNBT remain world coordinates.\n"
            +"# Players excluded. Membership uses entity position in [min,max+1). leave does not imply death.\n";
    }
    static Map<UUID,State> capture(ServerLevel level,Region r,int maxChars) {
        var entities=level.getEntities((net.minecraft.world.entity.Entity)null,new AABB(r.x(),r.y(),r.z(),(double)r.maxX()+1,(double)r.maxY()+1,(double)r.maxZ()+1),
            e->!(e instanceof Player)&&!e.isRemoved()
                &&e.getX()>=r.x()&&e.getX()<(double)r.maxX()+1
                &&e.getY()>=r.y()&&e.getY()<(double)r.maxY()+1
                &&e.getZ()>=r.z()&&e.getZ()<(double)r.maxZ()+1);
        if(entities.size()>MAX_ENTITIES)throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.entity_limit",MAX_ENTITIES));
        var result=new TreeMap<UUID,State>();
        long length=0;
        for(var entity:entities) {
            var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());
            entity.saveWithoutId(output);
            String nbt=output.buildResult().toString();
            length+=nbt.length()+256L;
            if(length>maxChars)throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.export_large"));
            result.put(entity.getUUID(),new State(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                entity.getX()-r.x(),entity.getY()-r.y(),entity.getZ()-r.z(),nbt));
        }
        return result;
    }
}
