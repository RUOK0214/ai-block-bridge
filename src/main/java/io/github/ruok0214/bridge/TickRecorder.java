/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.commands.arguments.blocks.BlockStateParser
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.HolderLookup$Provider
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.state.BlockState
 */
package io.github.ruok0214.bridge;

import io.github.ruok0214.bridge.BlockTimelineEncoder;
import io.github.ruok0214.bridge.BridgeServer;
import io.github.ruok0214.bridge.CaptureOptions;
import io.github.ruok0214.bridge.EntitySnapshot;
import io.github.ruok0214.bridge.EntityTimelineEncoder;
import io.github.ruok0214.bridge.Messages;
import io.github.ruok0214.bridge.RecordingBundle;
import io.github.ruok0214.bridge.Region;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

final class TickRecorder {
    static final int MAX_TICKS = 6000;
    static final int MAX_CHANGES = 500000;
    private final boolean ignoreHopperCooldown;
    private final boolean includeEntities;
    private final boolean ignoreEntityAgeMotion;
    private final EntityTimelineEncoder entityEncoder;
    private final BlockTimelineEncoder blockEncoder;
    private final boolean paletteFormat;
    private Map<UUID, EntitySnapshot.State> previousEntities = Map.of();
    private final Region region;
    private final String initialStructure;
    private final BlockState[] previousStates;
    private final Map<Integer, CompoundTag> previousNbt = new HashMap<Integer, CompoundTag>();
    private final StringBuilder text;
    private int tick;
    private int changes;
    private boolean stopped;
    private boolean noticeSent;
    private String stopNotice;
    private final int maxTicks;
    private final int maxChanges;
    private final int maxChars;

    TickRecorder(ServerLevel level, Region region) {
        this(level, region, true);
    }

    TickRecorder(ServerLevel level, Region region, boolean ignoreHopperCooldown) {
        this(level, region, ignoreHopperCooldown, 6000, 500000, 20000000);
    }

    TickRecorder(ServerLevel level, Region region, CaptureOptions options) {
        this(level, region, options, 6000, 500000, 20000000);
    }

    TickRecorder(ServerLevel level, Region region, boolean ignoreHopperCooldown, int maxTicks, int maxChanges, int maxChars) {
        this(level, region, new CaptureOptions(false, false, ignoreHopperCooldown), maxTicks, maxChanges, maxChars);
    }

    TickRecorder(ServerLevel level, Region region, CaptureOptions options, int maxTicks, int maxChanges, int maxChars) {
        if (maxTicks < 1 || maxTicks > 6000 || maxChanges < 1 || maxChanges > 500000 || maxChars < 512 || maxChars > 20000000) {
            throw new IllegalArgumentException("Invalid recording limits");
        }
        this.maxTicks = maxTicks;
        this.maxChanges = maxChanges;
        this.maxChars = maxChars;
        this.ignoreHopperCooldown = options.ignoreHopperCooldown();
        this.includeEntities = options.timelineEntities();
        this.ignoreEntityAgeMotion = options.ignoreEntityAgeMotion();
        this.blockEncoder = new BlockTimelineEncoder(options.shortBlockStates(), options.blockNbtDelta());
        this.paletteFormat=options.paletteFormat();
        this.entityEncoder = new EntityTimelineEncoder(options.entityDelta(), options.shortEntityIds());
        this.region = region;
        this.initialStructure = BridgeServer.exportRegion(level, region, options.structureEntities());
        this.previousStates = new BlockState[region.volume()];
        int index = 0;
        for (BlockPos blockPos : this.positions()) {
            BlockState state;
            this.previousStates[index] = state = level.getBlockState(blockPos);
            CompoundTag nbt = this.readNbt(level, blockPos, state);
            if (nbt != null) {
                this.previousNbt.put(index, nbt);
            }
            ++index;
        }
        this.text = new StringBuilder("# AI Block Bridge Timeline v1\n# size: " + region.sizeX() + " " + region.sizeY() + " " + region.sizeZ() + "\n# origin: " + region.x() + " " + region.y() + " " + region.z() + "\n# Only changes after recording started. @tick is a server-tick offset.\n# ignore hopper TransferCooldown: " + this.ignoreHopperCooldown + "\n");
        this.text.append(this.blockEncoder.header());
        if (this.includeEntities) {
            this.text.append("# include entities: true\n").append(this.entityEncoder.header());
            this.text.append("# ignore entity Age/Motion-only updates: ").append(this.ignoreEntityAgeMotion).append("\n");
            if (this.ignoreEntityAgeMotion) {
                this.text.append("# Age and Motion alone do not trigger updates. Emitted states retain current SNBT (apply patches when enabled).\n");
            }
            if (this.text.length() > maxChars - 288) {
                throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.export_large", new Object[0]));
            }
            this.previousEntities = EntitySnapshot.capture(level, region, maxChars, this.ignoreEntityAgeMotion);
            if (this.previousEntities.size() > maxChanges) {
                throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.entity_limit", maxChanges));
            }
            if (!this.previousEntities.isEmpty()) {
                this.text.append("\n@tick 0\n");
            }
            for (Map.Entry entry : this.previousEntities.entrySet()) {
                String line = this.entityEncoder.line("initial", (UUID)entry.getKey(), (EntitySnapshot.State)entry.getValue());
                if ((long)this.text.length() + (long)line.length() > (long)(maxChars - 288)) {
                    throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.export_large", new Object[0]));
                }
                this.text.append(line);
                ++this.changes;
            }
        }
    }

    RecordingBundle bundle() {
        String structure=this.initialStructure;
        String timeline=this.result();
        if(this.paletteFormat){structure=PaletteFormat.encode(structure);timeline=PaletteFormat.encode(timeline);}
        return new RecordingBundle(structure,timeline);
    }

    void capture(ServerLevel level) {
        if (this.stopped) {
            return;
        }
        ++this.tick;
        try {
            BridgeServer.validateRegion(level, this.region);
        }
        catch (Exception ex) {
            this.finish("Recording stopped: region unavailable.", "unavailable", new Object[0]);
            return;
        }
        StringBuilder changed = new StringBuilder();
        int count = 0;
        int index = 0;
        for (BlockPos p : this.positions()) {
            CompoundTag oldNbt;
            BlockState state = level.getBlockState(p);
            CompoundTag nbt = this.readNbt(level, p, state);
            CompoundTag compoundTag = oldNbt = this.previousStates[index].hasBlockEntity() ? this.previousNbt.get(index) : null;
            if (!state.equals(this.previousStates[index]) || !Objects.equals(nbt, oldNbt)) {
                if (this.changes + ++count > this.maxChanges) {
                    this.finish("Entry limit: the final tick was omitted rather than partially recorded.", "entries_partial", this.maxChanges);
                    return;
                }
                if (this.blockEncoder.enabled()) {
                    changed.append(this.blockEncoder.line(index, p.getX() - this.region.x() + " " + (p.getY() - this.region.y()) + " " + (p.getZ() - this.region.z()), state, nbt));
                } else {
                    this.append(changed, new BridgeServer.Cell(p, state, nbt));
                }
                if ((long)this.text.length() + (long)changed.length() > (long)(this.maxChars - 288)) {
                    this.finish("Character limit: the final tick was omitted rather than partially recorded.", "size_partial", this.maxChars);
                    return;
                }
                this.previousStates[index] = state;
                if (nbt == null) {
                    this.previousNbt.remove(index);
                } else {
                    this.previousNbt.put(index, nbt);
                }
            }
            ++index;
        }
        Map<UUID, EntitySnapshot.State> currentEntities = this.previousEntities;
        if (this.includeEntities) {
            try {
                currentEntities = EntitySnapshot.capture(level, this.region, this.maxChars, this.ignoreEntityAgeMotion);
            }
            catch (RuntimeException ex) {
                this.finish("Entity snapshot failed or exceeded limits; the final tick was omitted.", "entities", new Object[0]);
                return;
            }
            TreeSet<UUID> ids = new TreeSet<UUID>(this.previousEntities.keySet());
            ids.addAll(currentEntities.keySet());
            for (UUID id : ids) {
                String event;
                EntitySnapshot.State before = this.previousEntities.get(id);
                EntitySnapshot.State after = currentEntities.get(id);
                if (before != null && before.sameRecordedState(after)) continue;
                event = after == null ? "leave" : (before == null ? "enter" : "update");
                if (this.changes + ++count > this.maxChanges) {
                    this.finish("Entry limit: the final tick was omitted rather than partially recorded.", "entries_partial", this.maxChanges);
                    return;
                }
                String line = this.entityEncoder.line(event, id, after == null ? before : after);
                if ((long)this.text.length() + (long)changed.length() + (long)line.length() > (long)(this.maxChars - 288)) {
                    this.finish("Character limit: the final tick was omitted rather than partially recorded.", "size_partial", this.maxChars);
                    return;
                }
                changed.append(line);
            }
        }
        if (count > 0) {
            String section = "\n@tick " + this.tick + "\n" + String.valueOf(changed);
            if (this.changes + count > this.maxChanges) {
                this.finish("Entry limit: the final tick was omitted rather than partially recorded.", "entries_partial", this.maxChanges);
                return;
            }
            if ((long)this.text.length() + (long)section.length() > (long)(this.maxChars - 256)) {
                this.finish("Character limit: the final tick was omitted rather than partially recorded.", "size_partial", this.maxChars);
                return;
            }
            this.text.append(section);
            this.changes += count;
        }
        this.previousEntities = currentEntities;
        if (this.changes >= this.maxChanges) {
            this.finish("Reached the " + this.maxChanges + "-entry limit.", "entries", this.maxChanges);
        } else if (this.tick >= this.maxTicks) {
            this.finish("Reached the " + this.maxTicks + "-tick recording limit.", "time", this.maxTicks);
        }
    }

    private Iterable<BlockPos> positions() {
        return BlockPos.betweenClosed((int)this.region.x(), (int)this.region.y(), (int)this.region.z(), (int)this.region.maxX(), (int)this.region.maxY(), (int)this.region.maxZ());
    }

    private CompoundTag readNbt(ServerLevel level, BlockPos p, BlockState state) {
        if (!state.hasBlockEntity()) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(p);
        if (be == null) {
            return null;
        }
        CompoundTag tag = be.saveWithFullMetadata((HolderLookup.Provider)level.registryAccess());
        if (this.ignoreHopperCooldown && state.is(Blocks.HOPPER)) {
            tag.remove("TransferCooldown");
        }
        return tag;
    }

    private void append(StringBuilder out, BridgeServer.Cell cell) {
        BlockPos p = cell.pos();
        out.append(p.getX() - this.region.x()).append(' ').append(p.getY() - this.region.y()).append(' ').append(p.getZ() - this.region.z()).append(" | ").append(BlockStateParser.serialize((BlockState)cell.state()));
        if (cell.nbt() != null) {
            CompoundTag tag = cell.nbt().copy();
            tag.remove("x");
            tag.remove("y");
            tag.remove("z");
            out.append(" | ").append(tag);
        }
        out.append('\n');
    }

    private void finish(String reason, String key, Object ... args) {
        this.stopped = true;
        this.stopNotice = Messages.text("ai_block_bridge.recording.stop." + key, args);
        this.text.append("\n# ").append(reason).append('\n');
    }

    boolean stopped() {
        return this.stopped;
    }

    String takeStopNotice() {
        if (!this.stopped || this.noticeSent) {
            return null;
        }
        this.noticeSent = true;
        return this.stopNotice;
    }

    String result() {
        return this.text.toString() + "\n# recorded ticks: " + this.tick + " / changed entries: " + this.changes + "\n";
    }
}
