package io.github.ruok0214.bridge;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import java.util.List;

public class BridgeGameTests {
    @GameTest
    public void hopperCooldownFilterPreservesItems(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        BlockPos p=h.absolutePos(new BlockPos(2,3,2));
        Region r=Region.of(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ());
        var original=BridgeServer.snapshot(level,p);
        BridgeServer.apply(level,BridgeServer.prepare(level,r,"0 0 0 | minecraft:hopper | {TransferCooldown:8,Items:[]}"));
        var filtered=new TickRecorder(level,r,true);
        var full=new TickRecorder(level,r,false);
        BridgeServer.apply(level,BridgeServer.prepare(level,r,"0 0 0 | minecraft:hopper | {TransferCooldown:7,Items:[]}"));
        filtered.capture(level);full.capture(level);
        h.assertTrue(!filtered.result().contains("@tick"+" 1"),"Cooldown-only change was recorded");
        h.assertTrue(full.result().contains("@tick 1"),"Full mode lost cooldown change");
        BridgeServer.apply(level,BridgeServer.prepare(level,r,"0 0 0 | minecraft:hopper | {TransferCooldown:6,Items:[{Slot:0b,id:\"minecraft:diamond\",count:1}]}"));
        filtered.capture(level);
        h.assertTrue(filtered.result().contains("minecraft:diamond"),"Filter lost item change");
        BridgeServer.apply(level,List.of(original));h.succeed();
    }
    @GameTest
    public void barrelNbtRoundtrip(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        BlockPos p=h.absolutePos(new BlockPos(1,2,1));
        Region r=Region.of(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ());
        var original=BridgeServer.snapshot(level,p);
        String script="0 0 0 | minecraft:barrel[facing=up,open=false] | {Items:[{Slot:0b,id:\"minecraft:diamond\",count:3}]}";
        BridgeServer.apply(level,BridgeServer.prepare(level,r,script));
        String exported=BridgeServer.exportRegion(level,r);
        h.assertTrue(exported.contains("minecraft:diamond"),"Barrel items were not preserved: "+exported);
        var first=BridgeServer.snapshot(level,p);
        BridgeServer.apply(level,BridgeServer.prepare(level,r,"0 0 0 | minecraft:air"));
        BridgeServer.apply(level,BridgeServer.prepare(level,r,exported));
        h.assertTrue(first.equals(BridgeServer.snapshot(level,p)),"Export/import changed block state or NBT");
        BridgeServer.apply(level,List.of(original));
        h.assertTrue(original.equals(BridgeServer.snapshot(level,p)),"Undo snapshot did not restore original");
        h.succeed();
    }
    @GameTest
    public void rejectsBadInputBeforeWriting(GameTestHelper h) throws Exception {
        var level=h.getLevel();BlockPos p=h.absolutePos(new BlockPos(2,2,2));
        Region r=Region.of(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ());
        var before=BridgeServer.snapshot(level,p);
        String[] bad={"0 0 0 | minecraft:stone junk","0 0 0 | minecraft:nonexistent_block", "0 0 0 | minecraft:stone | {}", "0 0 0 | minecraft:barrel | {broken", "0 0 0 | minecraft:oak_stairs[facing=invalid]", "0 0 0 | minecraft:stone\n1 0 0 | minecraft:nonexistent_block"};
        for(String text:bad) {
            boolean rejected=false;
            try{BridgeServer.prepare(level,r,text);}catch(Exception ex){rejected=true;}
            h.assertTrue(rejected,"Accepted invalid input: "+text);
            h.assertTrue(before.equals(BridgeServer.snapshot(level,p)),"Validation changed the world");
        }
        h.succeed();
    }
    @GameTest
    public void exportOmitsAir(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        BlockPos p=h.absolutePos(new BlockPos(1,3,1));
        Region r=Region.of(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ());
        List<BridgeServer.Cell> original=List.of(BridgeServer.snapshot(level,p),BridgeServer.snapshot(level,p.east()));
        BridgeServer.apply(level,BridgeServer.prepare(level,r,"0 0 0 | minecraft:stone\n1 0 0 | minecraft:air"));
        String exported=BridgeServer.exportRegion(level,r);
        h.assertTrue(Script.parse(exported,r).size()==1,"Export should contain only the non-air block: "+exported);
        h.assertTrue(!exported.contains("minecraft:air"),"Export contained an air block: "+exported);
        BridgeServer.apply(level,original);
        h.succeed();
    }
    @GameTest
    public void recordsOnlyChangedTicksIncludingAir(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        BlockPos p=h.absolutePos(new BlockPos(3,3,3));
        Region r=Region.of(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ());
        List<BridgeServer.Cell> original=List.of(BridgeServer.snapshot(level,p),BridgeServer.snapshot(level,p.east()));
        BridgeServer.apply(level,BridgeServer.prepare(level,r,"0 0 0 | minecraft:air\n1 0 0 | minecraft:air"));
        TickRecorder recorder=new TickRecorder(level,r);
        BridgeServer.apply(level,BridgeServer.prepare(level,r,"0 0 0 | minecraft:stone"));
        recorder.capture(level);
        recorder.capture(level);
        BridgeServer.apply(level,BridgeServer.prepare(level,r,"0 0 0 | minecraft:air"));
        recorder.capture(level);
        String timeline=recorder.result();
        h.assertTrue(timeline.contains("@tick 1\n0 0 0 | minecraft:stone"),"Missing first-tick placement: "+timeline);
        h.assertTrue(!timeline.contains("@tick 2"),"Unchanged tick should be omitted: "+timeline);
        h.assertTrue(timeline.contains("@tick 3\n0 0 0 | minecraft:air"),"Missing removal as air: "+timeline);
        h.assertTrue(!timeline.contains("1 0 0 |"),"Unchanged neighboring coordinate was recorded: "+timeline);
        BridgeServer.apply(level,original);
        h.succeed();
    }
}
