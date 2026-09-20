/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.nbt.ListTag
 *  net.minecraft.nbt.StringTag
 *  net.minecraft.nbt.Tag
 */
package io.github.ruok0214.bridge;

import io.github.ruok0214.bridge.EntitySnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

final class EntityTimelineEncoder {
    private final boolean delta;
    private final boolean shortIds;
    private final Map<UUID, Integer> ids = new HashMap<UUID, Integer>();
    private final Map<UUID, EntitySnapshot.State> emitted = new HashMap<UUID, EntitySnapshot.State>();

    EntityTimelineEncoder(boolean delta, boolean shortIds) {
        this.delta = delta;
        this.shortIds = shortIds;
    }

    String header() {
        return EntitySnapshot.header().replace("UUID | relative x y z | type | full SNBT", "ID | relative x y z | type | SNBT (see compact rules below)") + "# entity delta: " + this.delta + " / short entity IDs: " + this.shortIds + "\n# @entity-id E<number> = UUID; IDs are recording-local and never reused.\n# initial/enter: full SNBT. patch: relative x y z | type | {set:{...},remove:[keys]}.\n# Apply remove then replace each top-level set key; nested compounds/lists replace whole values.\n# Patches refer to last emitted state. leave carries a final full state or patch, then ends membership.\n";
    }

    static CompoundTag diff(CompoundTag before, CompoundTag after) {
        CompoundTag set = new CompoundTag();
        ListTag remove = new ListTag();
        for (String key : after.keySet()) {
            if (Objects.equals(before.get(key), after.get(key))) continue;
            set.put(key, after.get(key).copy());
        }
        for (String key : before.keySet()) {
            if (after.contains(key)) continue;
            remove.add(StringTag.valueOf(key));
        }
        CompoundTag patch = new CompoundTag();
        patch.put("set", (Tag)set);
        patch.put("remove", (Tag)remove);
        return patch;
    }

    String line(String event, UUID uuid, EntitySnapshot.State state) {
        String nbt;
        boolean patch;
        String prefix = "";
        String id = uuid.toString();
        if (this.shortIds) {
            Integer number = this.ids.get(uuid);
            if (number == null) {
                number = this.ids.size() + 1;
                this.ids.put(uuid, number);
                prefix = "# @entity-id E" + number + " = " + String.valueOf(uuid) + "\n";
            }
            id = "E" + number;
        }
        EntitySnapshot.State before = this.emitted.get(uuid);
        boolean bl = patch = this.delta && before != null && !event.equals("initial") && !event.equals("enter");
        String kind = patch ? (event.equals("leave") ? "leave-patch" : "patch") : event;
        String string = nbt = patch ? EntityTimelineEncoder.diff(before.tag(), state.tag()).toString() : state.nbt();
        if (event.equals("leave")) {
            this.emitted.remove(uuid);
        } else {
            this.emitted.put(uuid, state);
        }
        return prefix + "# @entity " + kind + " " + id + " | " + state.x() + " " + state.y() + " " + state.z() + " | " + state.type() + " | " + nbt + "\n";
    }
}

