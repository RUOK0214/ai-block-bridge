package io.github.ruok0214.bridge;

import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class CompactDisplayGameTests {
    @GameTest(structure="ai_block_bridge_test:compact_empty",maxTicks=3400,skyAccess=true)
    public void compactHexBinaryAndGrayInputs(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        Region region=Region.of(origin.getX(),origin.getY(),origin.getZ(),origin.getX()+21,origin.getY()+9,origin.getZ()+37);
        String text;
        try(var stream=CompactDisplayGameTests.class.getResourceAsStream("/seven_segment_compact.txt.gz")) {
            if(stream==null)throw new IllegalStateException("Missing compact display fixture");
            text=new String(new GZIPInputStream(stream).readAllBytes(),StandardCharsets.UTF_8);
        }
        BridgeServer.validateRegion(level,region);
        var cells=BridgeServer.prepare(level,region,text);
        BridgeServer.apply(level,cells);
        String[] glyphs={"abcdef","bc","abdeg","abcdg","bcfg","acdfg","acdefg","abc",
            "abcdefg","abcdfg","abcefg","cdefg","adef","bcdeg","adefg","aefg"};
        int[][] centers={{2,9,0},{0,7,0},{0,3,0},{2,1,0},{4,3,0},{4,7,0},{2,5,0}};
        // Binary counting exercises multi-bit changes; Gray counting exercises
        // individual lever changes. Finish at zero and check every block survived.
        for(int step=0;step<33;step++) {
            final int value=step<16?step:step<32?((step-16)^((step-16)>>1)):0;
            h.runAtTickTime(1+100*step,()->{
                for(int bit=0;bit<4;bit++) {
                    BlockPos p=origin.offset(16-5*bit,3,8);
                    var state=level.getBlockState(p);
                    h.assertTrue(state.is(Blocks.LEVER),"Missing compact input lever "+p);
                    level.setBlock(p,state.setValue(BlockStateProperties.POWERED,(value&(8>>bit))!=0),Block.UPDATE_ALL);
                    level.updateNeighborsAt(p.below(),Blocks.LEVER);
                }
            });
            h.runAtTickTime(90+100*step,()->{
                for(int segment=0;segment<7;segment++) {
                    char letter=(char)('a'+segment);boolean expected=glyphs[value].indexOf(letter)>=0;
                    for(int offset=-1;offset<=1;offset++) {
                        int[] c=centers[segment];boolean horizontal=letter=='a'||letter=='d'||letter=='g';
                        BlockPos p=origin.offset(c[0]+(horizontal?offset:0),c[1]+(horizontal?0:offset),c[2]);
                        var state=level.getBlockState(p);
                        h.assertTrue(state.is(Blocks.REDSTONE_LAMP)&&state.getValue(BlockStateProperties.LIT)==expected,
                            "Compact input "+value+" segment "+letter+" at "+p+" expected="+expected+" actual="+state);
                    }
                }
                System.out.println("AI7SEG COMPACT ENGINE PASS input="+value);
            });
        }
        h.runAtTickTime(3310,()->{
            for(var cell:cells)h.assertTrue(level.getBlockState(cell.pos()).is(cell.state().getBlock()),"Compact block disappeared: "+cell.pos()+" "+cell.state());
            h.succeed();
        });
    }
}
