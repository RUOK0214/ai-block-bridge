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
    private static final Map<UUID,Long> lastRequest=new HashMap<>();
    public static void clear(UUID id) { incoming.remove(id);undos.remove(id);lastRequest.remove(id); }
    public static void clearAll() { incoming.clear();undos.clear();lastRequest.clear(); }
    public static void receive(ServerPlayer player,BridgePacket packet) {
        try {
            // Full NBT can contain command blocks; use owner/operator level 4, not merely creative mode.
            if(!player.permissions().hasPermission(Permissions.COMMANDS_OWNER))
                throw new IllegalArgumentException("치트 허용 싱글플레이 또는 OP 4 권한이 필요합니다.");
            if(packet.action()<BridgePacket.EXPORT || packet.action()>BridgePacket.UNDO)
                throw new IllegalArgumentException("알 수 없는 작업입니다.");
            if(!player.level().dimension().identifier().toString().equals(packet.dimension()))
                throw new IllegalArgumentException("차원이 변경되었습니다. 영역을 다시 선택하세요.");
            UUID id=player.getUUID();
            if(packet.index()==0) {
                long now=System.nanoTime();
                if(now-lastRequest.getOrDefault(id,0L)<1_000_000_000L) throw new IllegalArgumentException("1초 후 다시 시도하세요.");
                lastRequest.put(id,now);
                incoming.put(id,new Assembly(packet));
            }
            Assembly assembly=incoming.get(id);
            if(assembly==null) throw new IllegalArgumentException("전송을 다시 시작하세요.");
            String body=assembly.append(packet);
            if(body==null)return;
            incoming.remove(id);
            ServerLevel level=player.level();
            if(packet.action()==BridgePacket.UNDO) { undo(player,packet,level);return; }
            Region region=packet.region();
            validateRegion(level,region);
            if(packet.action()==BridgePacket.EXPORT) {
                String result=exportRegion(level,region);
                packet.chunks(result,BridgePacket.SCRIPT,p->ServerPlayNetworking.send(player,p));
            } else paste(player,packet,level,region,body);
        } catch(Exception ex) {
            incoming.remove(player.getUUID());
            reply(player,packet,"오류: "+safeMessage(ex));
        }
    }
    private static String safeMessage(Exception ex) {
        String s=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
        return s.substring(0,Math.min(s.length(),1000));
    }
    private static void reply(ServerPlayer p,BridgePacket request,String text) {
        request.chunks(text,BridgePacket.RESULT,msg->ServerPlayNetworking.send(p,msg));
    }
    private static void validatePosition(ServerLevel level,BlockPos pos) {
        if(level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos))
            throw new IllegalArgumentException("월드 높이 또는 월드 경계를 벗어났습니다.");
        if(!level.hasChunkAt(pos)) throw new IllegalArgumentException("불러오지 않은 청크입니다. 영역 가까이 이동하세요.");
    }
    private static void validateRegion(ServerLevel level,Region r) {
        for(BlockPos p:BlockPos.betweenClosed(r.x(),r.y(),r.z(),r.maxX(),r.maxY(),r.maxZ())) validatePosition(level,p);
    }
    static Cell snapshot(ServerLevel level,BlockPos pos) {
        BlockEntity be=level.getBlockEntity(pos);
        return new Cell(pos.immutable(),level.getBlockState(pos),be==null?null:be.saveWithFullMetadata(level.registryAccess()));
    }
    static String exportRegion(ServerLevel level,Region r) {
        StringBuilder text=new StringBuilder("# AI Block Bridge Script v1\n# size: "+r.sizeX()+" "+r.sizeY()+" "+r.sizeZ()+
            "\n# origin: "+r.x()+" "+r.y()+" "+r.z()+"\n# Includes air. Unlisted coordinates are unchanged on paste.\n");
        for(BlockPos p:BlockPos.betweenClosed(r.x(),r.y(),r.z(),r.maxX(),r.maxY(),r.maxZ())) {
            Cell cell=snapshot(level,p);
            text.append(p.getX()-r.x()).append(' ').append(p.getY()-r.y()).append(' ').append(p.getZ()-r.z())
                .append(" | ").append(BlockStateParser.serialize(cell.state));
            if(cell.nbt!=null) {
                CompoundTag tag=cell.nbt.copy();tag.remove("x");tag.remove("y");tag.remove("z");
                text.append(" | ").append(tag);
            }
            text.append('\n');
            if(text.length()>Script.MAX_CHARS) throw new IllegalArgumentException("NBT를 포함한 스크립트가 너무 큽니다. 영역을 줄이세요.");
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
                if(reader.canRead())throw new IllegalArgumentException("블록 상태 뒤에 잘못된 문자가 있습니다.");
                BlockPos pos=new BlockPos(r.x()+e.x(),r.y()+e.y(),r.z()+e.z());
                CompoundTag tag=null;
                if(!e.nbt().isEmpty()) {
                    if(!(state.getBlock() instanceof EntityBlock eb)) throw new IllegalArgumentException("이 블록은 블록 엔티티 NBT를 지원하지 않습니다.");
                    BlockEntity prototype=eb.newBlockEntity(pos,state);
                    if(prototype==null) throw new IllegalArgumentException("블록 엔티티 생성 실패");
                    tag=TagParser.parseCompoundFully(e.nbt());
                    CompoundTag metadata=prototype.saveWithFullMetadata(level.registryAccess());
                    tag.put("id",metadata.get("id"));
                    tag.putInt("x",pos.getX());tag.putInt("y",pos.getY());tag.putInt("z",pos.getZ());
                    if(BlockEntity.loadStatic(pos,state,tag,level.registryAccess())==null) throw new IllegalArgumentException("NBT 로드 실패");
                }
                cells.add(new Cell(pos,state,tag));
            } catch(Exception ex) { throw new IllegalArgumentException(e.line()+"행: "+safeMessage(ex)); }
        }
        return cells;
    }
    private static void paste(ServerPlayer p,BridgePacket packet,ServerLevel level,Region r,String body) throws Exception {
        List<Cell> target=prepare(level,r,body);
        List<Cell> before=target.stream().map(c->snapshot(level,c.pos)).toList();
        checkSnapshotSize(before);
        try { apply(level,target); }
        catch(Exception ex) {
            try { apply(level,before); }
            catch(Exception rollback) {
                undos.put(p.getUUID(),new Undo(packet.dimension(),before,null));
                LoggerFactory.getLogger("ai_block_bridge").error("Rollback failed; retained recovery snapshot",rollback);
                throw new IllegalStateException("복원 중 오류가 발생했습니다. 월드를 종료하지 말고 붙여넣기 취소를 누르세요.",ex);
            }
            throw new IllegalStateException("붙여넣기 실패. 원래 블록으로 복원했습니다: "+safeMessage(ex));
        }
        List<Cell> after=target.stream().map(c->snapshot(level,c.pos)).toList();
        undos.put(p.getUUID(),new Undo(packet.dimension(),before,after));
        reply(p,packet,target.size()+"블록 붙여넣기 완료. 직전 작업을 취소할 수 있습니다.");
    }
    private static void checkSnapshotSize(List<Cell> cells) {
        long count=0;
        for(Cell c:cells) {
            count+=c.nbt==null?0:c.nbt.toString().length();
            if(count>Script.MAX_CHARS) throw new IllegalArgumentException("실행 취소용 NBT가 너무 큽니다. 영역을 줄이세요.");
        }
    }
    static void apply(ServerLevel level,List<Cell> cells) {
        // Suppress drops and shape/neighbor updates during bulk editing. Do not clear a container into the world.
        int flags=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
        for(Cell c:cells) {
            level.removeBlockEntity(c.pos);
            level.setBlock(c.pos,c.state,flags);
            if(!level.getBlockState(c.pos).equals(c.state)) throw new IllegalStateException("블록 설치 실패: "+c.pos);
            if(c.state.getBlock() instanceof EntityBlock eb) {
                BlockEntity be=c.nbt==null?eb.newBlockEntity(c.pos,c.state):BlockEntity.loadStatic(c.pos,c.state,c.nbt.copy(),level.registryAccess());
                if(be==null) throw new IllegalStateException("블록 엔티티 복원 실패: "+c.pos);
                level.setBlockEntity(be);be.setChanged();
            }
            level.sendBlockUpdated(c.pos,c.state,c.state,Block.UPDATE_CLIENTS);
        }
    }
    private static void undo(ServerPlayer player,BridgePacket packet,ServerLevel level) {
        Undo undo=undos.get(player.getUUID());
        if(undo==null) throw new IllegalArgumentException("취소할 붙여넣기가 없습니다.");
        if(!undo.dimension.equals(packet.dimension())) throw new IllegalArgumentException("붙여넣기했던 차원으로 돌아가세요.");
        for(Cell c:undo.before) validatePosition(level,c.pos);
        if(undo.after!=null) for(Cell expected:undo.after) {
            if(!snapshot(level,expected.pos).equals(expected))
                throw new IllegalArgumentException("붙여넣기 후 블록 또는 NBT가 변경되어 취소를 중단했습니다. 기존 변경을 덮어쓰지 않았습니다.");
        }
        apply(level,undo.before);
        undos.remove(player.getUUID());
        reply(player,packet,"붙여넣기 취소 완료: "+undo.before.size()+"블록 복원");
    }
}
