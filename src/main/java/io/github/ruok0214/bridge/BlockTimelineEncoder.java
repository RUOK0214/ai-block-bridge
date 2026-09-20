/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.commands.arguments.blocks.BlockStateParser
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.nbt.IntTag
 *  net.minecraft.nbt.ListTag
 *  net.minecraft.nbt.NumericTag
 *  net.minecraft.nbt.Tag
 *  net.minecraft.world.level.block.state.BlockState
 */
package io.github.ruok0214.bridge;

import io.github.ruok0214.bridge.EntityTimelineEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

final class BlockTimelineEncoder {
    private final boolean shortStates;
    private final boolean delta;
    private final Map<String, Integer> palette = new HashMap<String, Integer>();
    private final Map<Integer, BlockState> states = new HashMap<Integer, BlockState>();
    private final Map<Integer, CompoundTag> tags = new HashMap<Integer, CompoundTag>();

    BlockTimelineEncoder(boolean shortStates, boolean delta) {
        this.shortStates = shortStates;
        this.delta = delta;
    }

    boolean enabled() {
        return this.shortStates || this.delta;
    }

    String header() {
        return !this.enabled() ? "" : "# Block encoding v2 (observation comments; not paste instructions).\n# short block states: " + this.shortStates + " / block NBT delta: " + this.delta + "\n# @block-state B<number> = full block state; palette IDs never reused.\n# @block full/patch x y z | state or B<number> | SNBT or none\n# First change per position and block-type replacement are full. none clears NBT.\n# patch applies remove, then top-level set; nested values replace whole.\n# Optional slots patch: remove slot numbers, then replace slots from set list (Slot identifies each item).\n# slots patches update Items only; missing slots field leaves Items unchanged. Empty inventory is Items:[].\n";
    }

    static CompoundTag clean(CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        CompoundTag t = tag.copy();
        t.remove("x");
        t.remove("y");
        t.remove("z");
        return t;
    }

    private static Map<Integer, CompoundTag> inventory(Tag tag) {
        if (!(tag instanceof ListTag)) {
            return null;
        }
        ListTag list = (ListTag)tag;
        TreeMap<Integer, CompoundTag> result = new TreeMap<Integer, CompoundTag>();
        for (Tag value : list) {
            CompoundTag item;
            Tag tag2;
            if (!(value instanceof CompoundTag) || !((tag2 = (item = (CompoundTag)value).get("Slot")) instanceof NumericTag)) {
                return null;
            }
            NumericTag slot = (NumericTag)tag2;
            if (result.put(slot.intValue(), item) == null) continue;
            return null;
        }
        return result;
    }

    static CompoundTag diff(CompoundTag before, CompoundTag after) {
        CompoundTag a = before.copy();
        CompoundTag b = after.copy();
        Map<Integer, CompoundTag> oldSlots = BlockTimelineEncoder.inventory(a.get("Items"));
        Map<Integer, CompoundTag> newSlots = BlockTimelineEncoder.inventory(b.get("Items"));
        CompoundTag slotPatch = null;
        if (oldSlots != null && newSlots != null) {
            a.remove("Items");
            b.remove("Items");
            ListTag set = new ListTag();
            ListTag remove = new ListTag();
            for (Map.Entry<Integer, CompoundTag> e : newSlots.entrySet()) {
                if (Objects.equals(oldSlots.get(e.getKey()), e.getValue())) continue;
                set.add(e.getValue().copy());
            }
            for (Integer key : oldSlots.keySet()) {
                if (newSlots.containsKey(key)) continue;
                remove.add(IntTag.valueOf(key));
            }
            if (!set.isEmpty() || !remove.isEmpty()) {
                slotPatch = new CompoundTag();
                slotPatch.put("set", (Tag)set);
                slotPatch.put("remove", (Tag)remove);
            }
        }
        CompoundTag patch = EntityTimelineEncoder.diff(a, b);
        if (slotPatch != null) {
            patch.put("slots", slotPatch);
        }
        return patch;
    }

    String line(int index, String position, BlockState state, CompoundTag raw) {
        boolean patch;
        CompoundTag tag = BlockTimelineEncoder.clean(raw);
        CompoundTag before = this.tags.get(index);
        BlockState oldState = this.states.get(index);
        String prefix = "";
        String name = BlockStateParser.serialize(state);
        if (this.shortStates) {
            Integer id = this.palette.get(name);
            if (id == null) {
                id = this.palette.size() + 1;
                this.palette.put(name, id);
                prefix = "# @block-state B" + id + " = " + name + "\n";
            }
            name = "B" + id;
        }
        boolean bl = patch = this.delta && oldState != null && oldState.getBlock() == state.getBlock() && before != null && tag != null;
        String payload = tag == null ? "none" : (patch ? BlockTimelineEncoder.diff(before, tag).toString() : tag.toString());
        this.states.put(index, state);
        if (tag == null) {
            this.tags.remove(index);
        } else {
            this.tags.put(index, tag);
        }
        return prefix + "# @block " + (patch ? "patch" : "full") + " " + position + " | " + name + " | " + payload + "\n";
    }
}

