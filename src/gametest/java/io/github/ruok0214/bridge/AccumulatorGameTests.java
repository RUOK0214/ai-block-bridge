package io.github.ruok0214.bridge;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class AccumulatorGameTests {
    private record Op(int input,boolean reset,int heldTicks) {}
    @GameTest(structure="ai_block_bridge_test:accumulator_empty",maxTicks=22000,skyAccess=true)
    public void accumulatorAddsHoldsResetsAndCarries(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        Region region=Region.of(origin.getX(),origin.getY(),origin.getZ(),origin.getX()+138,origin.getY()+21,origin.getZ()+129);
        String script;
        try(var stream=AccumulatorGameTests.class.getResourceAsStream("/accumulator_4bit.txt.gz")) {
            if(stream==null)throw new IllegalStateException("Missing accumulator fixture");
            script=new String(new GZIPInputStream(stream).readAllBytes(),StandardCharsets.UTF_8);
        }
        BridgeServer.validateRegion(level,region);
        var cells=BridgeServer.prepare(level,region,script);BridgeServer.apply(level,cells);
        // Changing levers alone must not change the saved value.
        h.runAtTickTime(1,()->setInputs(h,origin,15));
        h.runAtTickTime(190,()->check(h,origin,0,false,"idle input change"));
        var ops=new ArrayList<Op>();
        ops.add(new Op(15,true,20));
        for(int i=0;i<6;i++)ops.add(new Op(3,false,20));
        ops.add(new Op(0,false,20));
        ops.add(new Op(15,true,20));
        for(int i=0;i<16;i++)ops.add(new Op(1,false,20));
        for(int i=0;i<16;i++)ops.add(new Op(i,false,20));
        ops.add(new Op(15,false,80)); // Sustained input must still commit only once.
        ops.add(new Op(15,true,20));
        int q=0;
        for(int index=0;index<ops.size();index++) {
            Op op=ops.get(index);int before=q;int total=op.reset?0:q+op.input;
            q=total&15;final int expected=q;final boolean carry=total>15;
            int start=201+index*450;final int step=index;
            BlockPos button=origin.offset(op.reset?114:106,1,0);
            h.runAtTickTime(start,()->setInputs(h,origin,op.input));
            h.runAtTickTime(start+20,()->setControl(h,button,true));
            h.runAtTickTime(start+20+op.heldTicks,()->setControl(h,button,false));
            h.runAtTickTime(start+410,()->{
                check(h,origin,expected,carry,"operation "+step+" input="+op.input+" reset="+op.reset);
                h.assertTrue(!level.getBlockState(origin.offset(99,3,0)).getValue(BlockStateProperties.LIT),"Busy lamp still lit at operation "+step);
                System.out.println("AIACC ENGINE PASS step="+step+" before="+before+" input="+op.input+" reset="+op.reset+" result="+expected+" carry="+carry);
            });
        }
        h.runAtTickTime(201+ops.size()*450+200,()->{
            check(h,origin,0,false,"final idle hold");
            for(var cell:cells)h.assertTrue(level.getBlockState(cell.pos()).is(cell.state().getBlock()),"Accumulator block disappeared: "+cell.pos()+" "+cell.state());
            h.succeed();
        });
    }
    private static void setControl(GameTestHelper h,BlockPos p,boolean on) {
        var l=h.getLevel();var s=l.getBlockState(p);
        h.assertTrue(s.is(Blocks.STONE_BUTTON),"Missing button "+p);
        l.setBlock(p,s.setValue(BlockStateProperties.POWERED,on),Block.UPDATE_ALL);
        l.updateNeighborsAt(p.below(),Blocks.STONE_BUTTON);
    }
    private static void setInputs(GameTestHelper h,BlockPos o,int value) {
        var l=h.getLevel();
        for(int bit=0;bit<4;bit++) {
            BlockPos p=o.offset(76+4*bit,1,0);var s=l.getBlockState(p);
            h.assertTrue(s.is(Blocks.LEVER),"Missing lever "+p);
            l.setBlock(p,s.setValue(BlockStateProperties.POWERED,(value&(1<<bit))!=0),Block.UPDATE_ALL);
            l.updateNeighborsAt(p.below(),Blocks.LEVER);
        }
    }
    private static void check(GameTestHelper h,BlockPos o,int expected,boolean carry,String context) {
        var l=h.getLevel();
        String[] glyphs={"abcdef","bc","abdeg","abcdg","bcfg","acdfg","acdefg","abc","abcdefg","abcdfg","abcefg","cdefg","adef","bcdeg","adefg","aefg"};
        int[][] centers={{48,9,4},{46,7,4},{46,3,4},{48,1,4},{50,3,4},{50,7,4},{48,5,4}};
        for(int bit=0;bit<5;bit++) {
            boolean wanted=bit==4?carry:(expected&(1<<bit))!=0;
            var s=l.getBlockState(o.offset(15+22*bit,11,92));
            h.assertTrue(s.is(Blocks.REPEATER)&&s.getValue(BlockStateProperties.POWERED)==wanted,context+" register bit="+bit+" expected="+wanted+" actual="+s);
        }
        for(int segment=0;segment<7;segment++) {
            char letter=(char)('a'+segment);boolean wanted=glyphs[expected].indexOf(letter)>=0;
            for(int offset=-1;offset<=1;offset++) {
                int[] c=centers[segment];boolean horizontal=letter=='a'||letter=='d'||letter=='g';
                BlockPos p=o.offset(c[0]+(horizontal?offset:0),c[1]+(horizontal?0:offset),c[2]);var s=l.getBlockState(p);
                h.assertTrue(s.is(Blocks.REDSTONE_LAMP)&&s.getValue(BlockStateProperties.LIT)==wanted,context+" segment="+letter+" actual="+s);
            }
        }
        h.assertTrue(l.getBlockState(o.offset(95,3,0)).getValue(BlockStateProperties.LIT)==carry,context+" carry lamp");
    }
}
