package io.github.ruok0214.bridge;

import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.LoggerFactory;

public final class BridgeServer {
    record Cell(BlockPos pos,BlockState state,CompoundTag nbt) {}
    private record Undo(String dimension,List<Cell> before,List<Cell> after) {}
    private static final Map<UUID,Assembly> incoming=new HashMap<>();
    private static final Map<UUID,Undo> undos=new HashMap<>();
    private static final Map<UUID,Recording> recordings=new HashMap<>();
    private static final Map<UUID,Long> lastRequest=new HashMap<>();
    private record Recording(String dimension,TickRecorder recorder,BridgePacket request) {}
    public static void clear(UUID id) { incoming.remove(id);undos.remove(id);recordings.remove(id);lastRequest.remove(id); }
    public static void clearAll() { incoming.clear();undos.clear();recordings.clear();lastRequest.clear(); }
    public static void tick(MinecraftServer server) {
        for(var entry:recordings.entrySet()) {
            ServerPlayer player=server.getPlayerList().getPlayer(entry.getKey());
            Recording recording=entry.getValue();
            if(player!=null && recording.dimension.equals(player.level().dimension().identifier().toString())) {
                if(!recording.recorder.stopped())recording.recorder.capture(player.level());
                String notice=recording.recorder.takeStopNotice();
                if(notice!=null)recording.request.chunks(notice,BridgePacket.RECORD_STOPPED,p->ServerPlayNetworking.send(player,p));
            }
        }
    }
    public static void receive(ServerPlayer player,BridgePacket packet) {
        try {
            // Full NBT can contain command blocks; use owner/operator level 4, not merely creative mode.
            if(!player.permissions().hasPermission(Permissions.COMMANDS_OWNER))
                throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.permission"));
            if(packet.action()!=BridgePacket.EXPORT && packet.action()!=BridgePacket.PASTE && packet.action()!=BridgePacket.UNDO
                && packet.action()!=BridgePacket.START_RECORD && packet.action()!=BridgePacket.STOP_RECORD)
                throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.operation"));
            if(!player.level().dimension().identifier().toString().equals(packet.dimension()))
                throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.dimension"));
            UUID id=player.getUUID();
            if(packet.index()==0) {
                long now=System.nanoTime();
                if(now-lastRequest.getOrDefault(id,0L)<1_000_000_000L) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.rate_limit"));
                lastRequest.put(id,now);
                incoming.put(id,new Assembly(packet));
            }
            Assembly assembly=incoming.get(id);
            if(assembly==null) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.restart_transfer"));
            String body=assembly.append(packet);
            if(body==null)return;
            incoming.remove(id);
            ServerLevel level=player.level();
            if(packet.action()==BridgePacket.UNDO) { undo(player,packet,level);return; }
            if(packet.action()==BridgePacket.STOP_RECORD) { stopRecording(player,packet);return; }
            Region region=packet.region();
            validateRegion(level,region);
            if(packet.action()==BridgePacket.START_RECORD) { startRecording(player,packet,level,region);return; }
            if(packet.action()==BridgePacket.EXPORT) {
                String result=exportRegion(level,region);
                packet.chunks(result,BridgePacket.SCRIPT,p->ServerPlayNetworking.send(player,p));
            } else paste(player,packet,level,region,body);
        } catch(Exception ex) {
            incoming.remove(player.getUUID());
            reply(player,packet,Messages.text("ai_block_bridge.error", safeMessage(ex)));
        }
    }
    private static void startRecording(ServerPlayer player,BridgePacket packet,ServerLevel level,Region region) {
        if(recordings.containsKey(player.getUUID())) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.already_recording"));
        recordings.put(player.getUUID(),new Recording(packet.dimension(),new TickRecorder(level,region,!packet.text().equals("include-cooldown")),packet));
        reply(player,packet,Messages.text("ai_block_bridge.timeline.started"));
    }
    private static void stopRecording(ServerPlayer player,BridgePacket packet) {
        Recording recording=recordings.remove(player.getUUID());
        if(recording==null) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.not_recording"));
        String result=recording.recorder.bundle().encode();
        packet.chunks(result,BridgePacket.RECORDING_BUNDLE,p->ServerPlayNetworking.send(player,p));
    }
    private static String safeMessage(Exception ex) {
        String s=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
        return Messages.isEncoded(s)?s:s.substring(0,Math.min(s.length(),1000));
    }
    private static void reply(ServerPlayer p,BridgePacket request,String text) {
        request.chunks(text,BridgePacket.RESULT,msg->ServerPlayNetworking.send(p,msg));
    }
    private static void validatePosition(ServerLevel level,BlockPos pos) {
        if(level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos))
            throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.world_bounds"));
        if(!level.hasChunkAt(pos)) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.unloaded"));
    }
    static void validateRegion(ServerLevel level,Region r) {
        // Height and the rectangular world border need only the opposite corners;
        // chunk availability needs one check per intersecting chunk, not per block.
        validatePosition(level,new BlockPos(r.x(),r.y(),r.z()));
        validatePosition(level,new BlockPos(r.maxX(),r.maxY(),r.maxZ()));
        for(int cx=r.x()>>4;cx<=(r.maxX()>>4);cx++)
            for(int cz=r.z()>>4;cz<=(r.maxZ()>>4);cz++)
                if(!level.hasChunkAt(new BlockPos(Math.max(r.x(),cx<<4),r.y(),Math.max(r.z(),cz<<4))))
                    throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.unloaded"));
    }
    static Cell snapshot(ServerLevel level,BlockPos pos) {
        BlockEntity be=level.getBlockEntity(pos);
        return new Cell(pos.immutable(),level.getBlockState(pos),be==null?null:be.saveWithFullMetadata(level.registryAccess()));
    }
    static String exportRegion(ServerLevel level,Region r) {
        StringBuilder text=new StringBuilder("# AI Block Bridge Script v1\n# size: "+r.sizeX()+" "+r.sizeY()+" "+r.sizeZ()+
            "\n# origin: "+r.x()+" "+r.y()+" "+r.z()+"\n# Air is omitted. Unlisted coordinates are unchanged on paste.\n");
        for(BlockPos p:BlockPos.betweenClosed(r.x(),r.y(),r.z(),r.maxX(),r.maxY(),r.maxZ())) {
            if(level.getBlockState(p).isAir()) continue;
            Cell cell=snapshot(level,p);
            if(cell.state.isAir()) continue;
            text.append(p.getX()-r.x()).append(' ').append(p.getY()-r.y()).append(' ').append(p.getZ()-r.z())
                .append(" | ").append(BlockStateParser.serialize(cell.state));
            if(cell.nbt!=null) {
                CompoundTag tag=cell.nbt.copy();tag.remove("x");tag.remove("y");tag.remove("z");
                text.append(" | ").append(tag);
            }
            text.append('\n');
            if(text.length()>Script.MAX_CHARS) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.export_large"));
        }
        return text.toString();
    }
    static List<Cell> prepare(ServerLevel level,Region r,String body) throws Exception {
        var cells=new ArrayList<Cell>();
        for(Script.Entry e:Script.parse(body,r)) {
            try {
                var reader=new com.mojang.brigadier.StringReader(e.state());
                BlockState state=BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK),reader,false).blockState();
                reader.skipWhitespace();
                if(reader.canRead())throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.trailing_state"));
                BlockPos pos=new BlockPos(r.x()+e.x(),r.y()+e.y(),r.z()+e.z());
                CompoundTag tag=null;
                if(!e.nbt().isEmpty()) {
                    if(!(state.getBlock() instanceof EntityBlock eb)) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.unsupported_nbt"));
                    BlockEntity prototype=eb.newBlockEntity(pos,state);
                    if(prototype==null) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.block_entity_create"));
                    tag=TagParser.parseCompoundFully(e.nbt());
                    CompoundTag metadata=prototype.saveWithFullMetadata(level.registryAccess());
                    tag.put("id",metadata.get("id"));
                    tag.putInt("x",pos.getX());tag.putInt("y",pos.getY());tag.putInt("z",pos.getZ());
                    if(BlockEntity.loadStatic(pos,state,tag,level.registryAccess())==null) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.nbt_load"));
                }
                cells.add(new Cell(pos,state,tag));
            } catch(Exception ex) { throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.line", e.line(), safeMessage(ex))); }
        }
        return cells;
    }
    private static void paste(ServerPlayer p,BridgePacket packet,ServerLevel level,Region r,String body) throws Exception {
        List<Cell> target=prepare(level,r,body);
        List<Cell> before=target.stream().map(c->snapshot(level,c.pos)).toList();
        checkSnapshotSize(before);
        List<Cell> after;
        try {
            apply(level,target);
            after=target.stream().map(c->snapshot(level,c.pos)).toList();
            checkSnapshotSize(after);
        }
        catch(Exception ex) {
            try { apply(level,before); }
            catch(Exception rollback) {
                undos.put(p.getUUID(),new Undo(packet.dimension(),before,null));
                LoggerFactory.getLogger("ai_block_bridge").error("Rollback failed; retained recovery snapshot",rollback);
                throw new IllegalStateException(Messages.text("ai_block_bridge.error.rollback"),ex);
            }
            throw new IllegalStateException(Messages.text("ai_block_bridge.error.paste_restored", safeMessage(ex)));
        }
        undos.put(p.getUUID(),new Undo(packet.dimension(),before,after));
        reply(p,packet,Messages.text("ai_block_bridge.pasted", target.size()));
    }
    private static void checkSnapshotSize(List<Cell> cells) {
        long count=0;
        for(Cell c:cells) {
            count+=c.nbt==null?0:c.nbt.toString().length();
            if(count>Script.MAX_CHARS) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.undo_large"));
        }
    }
    static void apply(ServerLevel level,List<Cell> cells) {
        // Suppress drops and shape/neighbor updates during bulk editing. Do not clear a container into the world.
        int flags=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS
            |Block.UPDATE_SKIP_ON_PLACE|Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
        for(Cell c:cells) {
            level.removeBlockEntity(c.pos);
            level.setBlock(c.pos,c.state,flags);
            if(!level.getBlockState(c.pos).equals(c.state)) throw new IllegalStateException(Messages.text("ai_block_bridge.error.place", c.pos));
            if(c.state.getBlock() instanceof EntityBlock eb) {
                BlockEntity be=c.nbt==null?eb.newBlockEntity(c.pos,c.state):BlockEntity.loadStatic(c.pos,c.state,c.nbt.copy(),level.registryAccess());
                if(be==null) throw new IllegalStateException(Messages.text("ai_block_bridge.error.block_entity_restore", c.pos));
                level.setBlockEntity(be);level.blockEntityChanged(c.pos);
            }
            level.sendBlockUpdated(c.pos,c.state,c.state,Block.UPDATE_CLIENTS);
        }
    }
    private static void undo(ServerPlayer player,BridgePacket packet,ServerLevel level) {
        Undo undo=undos.get(player.getUUID());
        if(undo==null) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.no_undo"));
        if(!undo.dimension.equals(packet.dimension())) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.undo_dimension"));
        for(Cell c:undo.before) validatePosition(level,c.pos);
        if(undo.after!=null) for(Cell expected:undo.after) {
            if(!snapshot(level,expected.pos).equals(expected))
                throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.undo_changed"));
        }
        try { apply(level,undo.before); }
        catch(Exception ex) {
            undos.put(player.getUUID(),new Undo(undo.dimension,undo.before,null));
            throw new IllegalStateException(Messages.text("ai_block_bridge.error.undo_retry", safeMessage(ex)));
        }
        undos.remove(player.getUUID());
        reply(player,packet,Messages.text("ai_block_bridge.undone", undo.before.size()));
    }
}
