/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.HolderLookup$Provider
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.util.ProblemReporter
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.storage.TagValueOutput
 *  net.minecraft.world.level.storage.ValueOutput
 *  net.minecraft.world.phys.AABB
 */
package io.github.ruok0214.bridge;

import io.github.ruok0214.bridge.Messages;
import io.github.ruok0214.bridge.Region;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

final class EntitySnapshot {
    static final int MAX_ENTITIES = 4096;

    EntitySnapshot() {
    }

    static String header() {
        return "# Entity records are observation comments; block paste does not spawn entities.\n# @entity initial/enter/update/leave UUID | relative x y z | type | full SNBT\n# Relative position uses the selection origin; coordinates inside SNBT remain world coordinates.\n# Players excluded. Membership uses entity position in [min,max+1). leave does not imply death.\n";
    }

    static Map<UUID, State> capture(ServerLevel level, Region r, int maxChars) {
        return EntitySnapshot.capture(level, r, maxChars, false);
    }

    static Map<UUID, State> capture(ServerLevel level, Region r, int maxChars, boolean ignoreAgeMotion) {
        List<Entity> entities = level.getEntities((Entity)null, new AABB((double)r.x(), (double)r.y(), (double)r.z(), (double)r.maxX() + 1.0, (double)r.maxY() + 1.0, (double)r.maxZ() + 1.0), e -> !(e instanceof Player) && !e.isRemoved() && e.getX() >= (double)r.x() && e.getX() < (double)r.maxX() + 1.0 && e.getY() >= (double)r.y() && e.getY() < (double)r.maxY() + 1.0 && e.getZ() >= (double)r.z() && e.getZ() < (double)r.maxZ() + 1.0);
        if (entities.size() > 4096) {
            throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.entity_limit", 4096));
        }
        TreeMap<UUID, State> result = new TreeMap<UUID, State>();
        long length = 0L;
        for (Entity entity : entities) {
            String comparisonNbt;
            TagValueOutput output = TagValueOutput.createWithContext((ProblemReporter)ProblemReporter.DISCARDING, (HolderLookup.Provider)level.registryAccess());
            entity.saveWithoutId((ValueOutput)output);
            CompoundTag tag = output.buildResult();
            CompoundTag fullTag = tag.copy();
            String nbt = tag.toString();
            if (ignoreAgeMotion) {
                tag = tag.copy();
                tag.remove("Age");
                tag.remove("Motion");
            }
            comparisonNbt = ignoreAgeMotion ? tag.toString() : nbt;
            if ((length += (long)nbt.length() + 256L) > (long)maxChars) {
                throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.export_large", new Object[0]));
            }
            result.put(entity.getUUID(), new State(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(), entity.getX() - (double)r.x(), entity.getY() - (double)r.y(), entity.getZ() - (double)r.z(), nbt, comparisonNbt, fullTag));
        }
        return result;
    }

    record State(String type, double x, double y, double z, String nbt, String comparisonNbt, CompoundTag tag) {
        boolean sameRecordedState(State other) {
            return other != null && this.type.equals(other.type) && Double.compare(this.x, other.x) == 0 && Double.compare(this.y, other.y) == 0 && Double.compare(this.z, other.z) == 0 && this.comparisonNbt.equals(other.comparisonNbt);
        }

        String line(String event, UUID id) {
            return "# @entity " + event + " " + String.valueOf(id) + " | " + this.x + " " + this.y + " " + this.z + " | " + this.type + " | " + this.nbt + "\n";
        }
    }
}
