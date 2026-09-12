package io.github.ruok0214.bridge;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import java.util.List;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class BridgeGameTests {
    @GameTest(structure="ai_block_bridge_test:large_empty")
    public void recordingBundleKeepsInitialStructure(GameTestHelper h) {
        var level=h.getLevel();
        BlockPos p=h.absolutePos(new BlockPos(1,1,1));
        level.setBlock(p,Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
        Region region=Region.of(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ());
        TickRecorder recorder=new TickRecorder(level,region,true,1,100,4096);
        level.setBlock(p,Blocks.GOLD_BLOCK.defaultBlockState(),Block.UPDATE_ALL);
        recorder.capture(level);
        RecordingBundle bundle=recorder.bundle();
        h.assertTrue(bundle.structure().contains("minecraft:stone")&&!bundle.structure().contains("minecraft:gold_block"),"Initial snapshot changed");
        h.assertTrue(bundle.timeline().contains("@tick 1")&&bundle.timeline().contains("minecraft:gold_block"),"Timeline lost changes");
        h.assertTrue(recorder.stopped(),"Limit should stop recording");
        level.setBlock(p,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
        recorder.capture(level);
        h.assertTrue(bundle.equals(recorder.bundle()),"Stopped recording bundle changed");
        h.succeed();
    }

    @GameTest(structure="ai_block_bridge_test:large_empty")
    public void recordingLimitNoticesPreserveCompletedTicks(GameTestHelper h) {
        var level=h.getLevel();
        BlockPos p=h.absolutePos(new BlockPos(1,1,1));
        Region region=Region.of(p.getX(),p.getY(),p.getZ(),p.getX()+11,p.getY(),p.getZ());
        TickRecorder timed=new TickRecorder(level,region,true,2,100,4096);
        timed.capture(level);
        h.assertTrue(timed.takeStopNotice()==null,"Premature time-limit notice");
        timed.capture(level);
        h.assertTrue(timed.stopped()&&timed.takeStopNotice().contains("stop.time"),"Missing time-limit notice");
        String finished=timed.result();
        timed.capture(level);
        h.assertTrue(timed.takeStopNotice()==null&&finished.equals(timed.result()),"Stopped recorder changed or notified twice");

        TickRecorder entries=new TickRecorder(level,region,true,10,2,4096);
        level.setBlock(p,Blocks.GOLD_BLOCK.defaultBlockState(),Block.UPDATE_ALL);
        entries.capture(level);
        level.setBlock(p.offset(1,0,0),Blocks.GOLD_BLOCK.defaultBlockState(),Block.UPDATE_ALL);
        level.setBlock(p.offset(2,0,0),Blocks.GOLD_BLOCK.defaultBlockState(),Block.UPDATE_ALL);
        entries.capture(level);
        h.assertTrue(entries.takeStopNotice().contains("stop.entries_partial"),"Missing entry-limit notice");
        h.assertTrue(entries.result().contains("@tick 1")&&!entries.result().contains("@tick 2"),"Partial tick must not be retained");

        TickRecorder exact=new TickRecorder(level,region,true,10,1,4096);
        level.setBlock(p,Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
        exact.capture(level);
        h.assertTrue(exact.takeStopNotice().contains("stop.entries")&&exact.result().contains("@tick 1"),"Exact entry limit must retain the full tick");

        TickRecorder size=new TickRecorder(level,region,true,10,100,1024);
        level.setBlock(p,Blocks.DIAMOND_BLOCK.defaultBlockState(),Block.UPDATE_ALL);
        size.capture(level);
        for(int i=0;i<12;i++)level.setBlock(p.offset(i,0,0),Blocks.OAK_STAIRS.defaultBlockState(),Block.UPDATE_ALL);
        size.capture(level);
        h.assertTrue(size.takeStopNotice().contains("stop.size_partial"),"Missing character-limit notice");
        h.assertTrue(size.result().contains("@tick 1")&&!size.result().contains("@tick 2"),"Size limit discarded completed ticks or retained a partial tick");
        h.succeed();
    }
    @GameTest(structure="ai_block_bridge_test:large_empty",maxTicks=2800,skyAccess=true)
    public void sevenSegmentAllInputs(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        Region region=Region.of(origin.getX(),origin.getY(),origin.getZ(),origin.getX()+83,origin.getY()+14,origin.getZ()+122);
        String script;
        try(var stream=BridgeGameTests.class.getResourceAsStream("/seven_segment_4bit.txt.gz")) {
            if(stream==null)throw new IllegalStateException("Missing seven-segment fixture");
            script=new String(new java.util.zip.GZIPInputStream(stream).readAllBytes(),StandardCharsets.UTF_8);
        }
        BridgeServer.validateRegion(level,region);
        BridgeServer.apply(level,BridgeServer.prepare(level,region,script));
        // Exercise the expanded recorder across the whole sparse cuboid, including
        // an initially empty cell at the highest relative coordinate.
        TickRecorder recorder=new TickRecorder(level,region);
        BlockPos far=origin.offset(83,14,122);
        level.setBlock(far,Blocks.GOLD_BLOCK.defaultBlockState(),Block.UPDATE_ALL);
        recorder.capture(level);
        h.assertTrue(recorder.result().contains("83 14 122 | minecraft:gold_block"),"Large recorder missed far air-to-block change");
        level.setBlock(far,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
        recorder.capture(level);
        h.assertTrue(recorder.result().contains("83 14 122 | minecraft:air"),"Large recorder missed far block removal");
        String[] glyphs={"abcdef","bc","abdeg","abcdg","bcfg","acdfg","acdefg","abc", "abcdefg","abcdfg","abcefg","cdefg","adef","bcdeg","adefg","aefg"};
        int[][] centers={{41,14,4},{38,11,4},{38,5,4},{41,2,4},{44,5,4},{44,11,4},{41,8,4}};
        for(int step=0;step<=16;step++) {
            final int value=step%16;
            h.runAtTickTime(1+step*160,()->{
                for(int bit=0;bit<4;bit++) {
                    BlockPos p=origin.offset(26-8*bit,1,0);
                    var state=level.getBlockState(p);
                    h.assertTrue(state.is(Blocks.LEVER),"Missing input lever: "+p);
                    level.setBlock(p,state.setValue(BlockStateProperties.POWERED,(value&(8>>bit))!=0),Block.UPDATE_ALL);
                    level.updateNeighborsAt(p.below(),Blocks.LEVER);
                }
            });
            h.runAtTickTime(150+step*160,()->{
                for(int segment=0;segment<7;segment++) {
                    char letter=(char)('a'+segment);boolean expected=glyphs[value].indexOf(letter)>=0;
                    for(int offset=-1;offset<=1;offset++) {
                        int[] c=centers[segment];boolean horizontal=letter=='a'||letter=='d'||letter=='g';
                        BlockPos p=origin.offset(c[0]+(horizontal?offset:0),c[1]+(horizontal?0:offset),c[2]);
                        var state=level.getBlockState(p);
                        h.assertTrue(state.is(Blocks.REDSTONE_LAMP)&&state.getValue(BlockStateProperties.LIT)==expected,
                            "Input "+value+" segment "+letter+" at "+p+" expected lit="+expected+" actual="+state);
                    }
                }
                System.out.println("AI7SEG ENGINE PASS input="+value);
            });
        }
        h.runAtTickTime(2720,h::succeed);
    }
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
