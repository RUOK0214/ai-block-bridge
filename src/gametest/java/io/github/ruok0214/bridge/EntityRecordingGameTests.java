package io.github.ruok0214.bridge;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class EntityRecordingGameTests {
    private Region region(BlockPos p) { return Region.of(p.getX(),p.getY(),p.getZ(),p.getX()+3,p.getY()+3,p.getZ()+3); }
    private ItemEntity item(GameTestHelper h,BlockPos p) {
        var entity=new ItemEntity(h.getLevel(),p.getX()+0.25,p.getY()+0.5,p.getZ()+0.75,new ItemStack(Items.DIAMOND,3));
        h.getLevel().addFreshEntity(entity);return entity;
    }
    @GameTest(structure="ai_block_bridge_test:large_empty")
    public void independentOptionsAndEntityComments(GameTestHelper h) {
        var p=h.absolutePos(new BlockPos(2,2,2));var r=region(p);var level=h.getLevel();
        level.setBlock(p,Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
        var entity=item(h,p);
        h.assertTrue(!BridgeServer.exportRegion(level,r).contains("# @entity"),"Legacy export includes entities");
        for(boolean structure:new boolean[]{false,true})for(boolean timeline:new boolean[]{false,true}) {
            var recorder=new TickRecorder(level,r,new CaptureOptions(structure,timeline,true));
            var bundle=recorder.bundle();String marker="# @entity initial "+entity.getUUID();
            h.assertTrue(bundle.structure().contains(marker)==structure,"Structure flag is not independent");
            h.assertTrue(bundle.timeline().contains(marker)==timeline,"Timeline flag is not independent");
            h.assertTrue(Script.parse(bundle.structure(),r).size()==1,"Observation changed block parse");
            if(timeline)h.assertTrue(bundle.timeline().contains("@tick 0")&&bundle.timeline().contains("0.25 0.5 0.75"),"Missing initial relative coordinates");
        }
        entity.discard();h.succeed();
    }
    @GameTest(structure="ai_block_bridge_test:large_empty")
    public void enterMoveNbtLeaveAndReenter(GameTestHelper h) {
        var p=h.absolutePos(new BlockPos(2,2,2));var r=region(p);var level=h.getLevel();
        var recorder=new TickRecorder(level,r,new CaptureOptions(false,true,true));
        var entity=item(h,p);String id=entity.getUUID().toString();
        recorder.capture(level);
        h.assertTrue(recorder.result().contains("# @entity enter "+id),"Missing entry");
        recorder.capture(level);
        h.assertTrue(!recorder.result().contains("@tick 2\n"),"Unchanged entity was recorded again");
        entity.setPos(p.getX()+1.25,p.getY()+0.5,p.getZ()+0.75);entity.setItem(new ItemStack(Items.EMERALD,7));
        recorder.capture(level);
        h.assertTrue(recorder.result().contains("# @entity update "+id+" | 1.25 0.5 0.75")&&recorder.result().contains("minecraft:emerald"),"Missing movement or item NBT");
        entity.setPos(r.maxX()+1.0,p.getY()+0.5,p.getZ()+0.75);recorder.capture(level);
        h.assertTrue(recorder.result().contains("@tick 4\n# @entity leave "+id),"Upper boundary must be outside even when hitbox overlaps");
        entity.setPos(p.getX()+0.25,p.getY()+0.5,p.getZ()+0.75);recorder.capture(level);
        h.assertTrue(recorder.result().contains("@tick 5\n# @entity enter "+id),"Re-entry lost identity");
        entity.discard();recorder.capture(level);
        h.assertTrue(recorder.result().contains("@tick 6\n# @entity leave "+id),"Removed entity not recorded");
        h.succeed();
    }
    @GameTest(structure="ai_block_bridge_test:large_empty")
    public void fallingBlockAndWholeTickLimit(GameTestHelper h) {
        var p=h.absolutePos(new BlockPos(2,2,2));var r=region(p);var level=h.getLevel();
        level.setBlock(p,Blocks.SAND.defaultBlockState(),Block.UPDATE_ALL);
        var falling=FallingBlockEntity.fall(level,p,Blocks.SAND.defaultBlockState());
        String snapshot=BridgeServer.exportRegion(level,r,true);
        h.assertTrue(snapshot.contains("minecraft:falling_block")&&snapshot.contains("minecraft:sand"),"Missing falling block state");
        falling.discard();
        var recorder=new TickRecorder(level,r,new CaptureOptions(false,true,true),10,1,16000);
        var a=item(h,p);var b=item(h,p);
        recorder.capture(level);
        h.assertTrue(recorder.stopped()&&!recorder.result().contains("@tick 1\n"),"Entity limit retained partial tick");
        h.assertTrue(recorder.takeStopNotice().contains("entries_partial"),"Missing limit notice");
        a.discard();b.discard();h.succeed();
    }
}
