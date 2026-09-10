package io.github.ruok0214.bridge;

import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class QuizBuzzerGameTests {
    @GameTest(structure="ai_block_bridge_test:quiz_buzzer_empty",maxTicks=27000,skyAccess=true)
    public void threePlayerQuizCapturePriorityAndReset(GameTestHelper h) throws Exception {
        var l=h.getLevel();BlockPos o=h.absolutePos(new BlockPos(1,1,1));
        Region region=Region.of(o.getX(),o.getY(),o.getZ(),o.getX()+34,o.getY()+6,o.getZ()+27);
        String script;
        try(var stream=QuizBuzzerGameTests.class.getResourceAsStream("/quiz_buzzer_3p.txt.gz")) {
            if(stream==null)throw new IllegalStateException("Missing quiz buzzer fixture");
            script=new String(new GZIPInputStream(stream).readAllBytes(),StandardCharsets.UTF_8);
        }
        BridgeServer.validateRegion(l,region);var cells=BridgeServer.prepare(l,region,script);BridgeServer.apply(l,cells);
        h.runAtTickTime(90,()->check(h,o,0,"initial idle"));
        int start=101;
        for(int reverse=0;reverse<2;reverse++)for(int mask=1;mask<8;mask++) {
            final int m=mask,order=reverse,wanted=Integer.numberOfTrailingZeros(mask)+1,t=start;
            resetRound(h,o,t);
            h.runAtTickTime(t+60,()->{
                for(int j=0;j<3;j++){int player=order==0?j+1:3-j;if((m&(1<<(player-1)))!=0)button(h,o,player,true);}
            });
            h.runAtTickTime(t+80,()->{for(int p1=1;p1<=3;p1++)button(h,o,p1,false);});
            for(int tick=t+61;tick<t+140;tick++)h.runAtTickTime(tick,()->{
                int observed=winner(h,o);h.assertTrue(observed==0||observed==wanted,"Transient wrong winner mask="+m+" order="+order+" observed="+observed);
            });
            h.runAtTickTime(t+140,()->check(h,o,wanted,"capture mask="+m+" order="+order));
            h.runAtTickTime(t+160,()->{for(int p1=1;p1<=3;p1++)button(h,o,p1,true);});
            h.runAtTickTime(t+180,()->{for(int p1=1;p1<=3;p1++)button(h,o,p1,false);});
            h.runAtTickTime(t+230,()->{
                check(h,o,wanted,"late inputs mask="+m);
                for(int x:new int[]{15,10,5})h.assertTrue(l.getBlockState(o.offset(x,4,10)).getValue(BlockStateProperties.POWERED),"Memory not locked");
                System.out.println("AIQUIZ BASIC PASS mask="+m+" reverse="+order+" winner="+wanted);
            });
            start+=260;
        }
        for(int first=1;first<=3;first++)for(int second=1;second<=3;second++) {
            if(first==second)continue;
            for(int gap=0;gap<=16;gap++) {
                final int a=first,b=second,g=gap,t=start;
                resetRound(h,o,t);
                h.runAtTickTime(t+60,()->button(h,o,a,true));
                h.runAtTickTime(t+60+g,()->button(h,o,b,true));
                h.runAtTickTime(t+80,()->button(h,o,a,false));
                h.runAtTickTime(t+80+g,()->button(h,o,b,false));
                h.runAtTickTime(t+145,()->{
                    int got=winner(h,o);h.assertTrue(got==a||got==b,"No valid exclusive winner");
                    if(g==0)h.assertTrue(got==Math.min(a,b),"Same-tick priority failed");
                    if(g==16)h.assertTrue(got==a,"First arrival with 16-tick lead lost");
                    System.out.println("AIQUIZ TIMING PASS first="+a+" second="+b+" gap="+g+" winner="+got);
                });
                start+=201; // Both game-tick parities occur in this sweep.
            }
        }
        final int end=start;
        resetRound(h,o,end);
        h.runAtTickTime(end+100,()->{
            check(h,o,0,"final reset");
            for(var cell:cells)h.assertTrue(l.getBlockState(cell.pos()).is(cell.state().getBlock()),"Quiz block disappeared: "+cell.pos()+" "+cell.state());
            h.succeed();
        });
    }
    private static void resetRound(GameTestHelper h,BlockPos o,int t) {
        h.runAtTickTime(t,()->button(h,o,0,true));
        h.runAtTickTime(t+20,()->button(h,o,0,false));
        h.runAtTickTime(t+50,()->check(h,o,0,"reset"));
    }
    private static void button(GameTestHelper h,BlockPos o,int player,boolean on) {
        BlockPos p=player==0?o.offset(31,2,0):o.offset(19-5*player,4,0);
        var l=h.getLevel();var s=l.getBlockState(p);
        h.assertTrue(s.is(Blocks.STONE_BUTTON),"Missing button "+player+" at "+p);
        l.setBlock(p,s.setValue(BlockStateProperties.POWERED,on),Block.UPDATE_ALL);l.updateNeighborsAt(p.below(),Blocks.STONE_BUTTON);
    }
    private static int winner(GameTestHelper h,BlockPos o) {
        int winner=0;
        for(int p=1;p<=3;p++){
            var s=h.getLevel().getBlockState(o.offset(22,2,20+2*p));
            h.assertTrue(s.is(Blocks.REDSTONE_LAMP),"Missing lamp "+p);
            if(s.getValue(BlockStateProperties.LIT)){h.assertTrue(winner==0,"Multiple winner lamps");winner=p;}
        }
        return winner;
    }
    private static void check(GameTestHelper h,BlockPos o,int expected,String why) {
        int got=winner(h,o);h.assertTrue(got==expected,why+" expected="+expected+" actual="+got);
    }
}
