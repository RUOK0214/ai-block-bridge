package io.github.ruok0214.bridge.addon;

import io.github.ruok0214.bridge.BridgeAutomation;
import io.github.ruok0214.bridge.Region;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/** The whole add-on loop: clear, place, settle, test, report -- with no human in it. */
public class EngineGameTests {
    /** Lever, three bare wires, a repeater, a wire written wrong, and a lamp. */
    private static final String CIRCUIT="0 0 0 | minecraft:stone\n1 0 0 | minecraft:stone\n2 0 0 | minecraft:stone\n"
        +"3 0 0 | minecraft:stone\n4 0 0 | minecraft:stone\n5 0 0 | minecraft:stone\n6 0 0 | minecraft:stone\n"
        +"0 1 0 | minecraft:lever[face=floor,facing=north,powered=false]\n"
        +"1 1 0 | minecraft:redstone_wire\n2 1 0 | minecraft:redstone_wire\n3 1 0 | minecraft:redstone_wire\n"
        +"4 1 0 | minecraft:repeater[delay=1,facing=west]\n"
        +"5 1 0 | minecraft:redstone_wire[east=none,north=none,power=0,south=none,west=none]\n"
        +"6 1 0 | minecraft:redstone_lamp\n";
    private static final String TESTS="@case lamp on\nset 0 1 0 | minecraft:lever[powered=true]\nwait 10\n"
        +"expect 6 1 0 | minecraft:redstone_lamp[lit=true]\n"
        +"@case lamp off\nset 0 1 0 | minecraft:lever[powered=false]\nwait 10\n"
        +"expect 6 1 0 | minecraft:redstone_lamp[lit=false]\n";

    private static Region region(GameTestHelper h) {
        BlockPos o=h.absolutePos(new BlockPos(1,1,1));
        return Region.of(o.getX(),o.getY(),o.getZ(),o.getX()+7,o.getY()+2,o.getZ());
    }

    @GameTest(structure="ai_block_bridge_2_test:empty",maxTicks=300)
    public void oneAttemptSettlesThenJudgesItself(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        Region r=region(h);
        Engine engine=new Engine(level,r,CIRCUIT,TESTS);
        for(int i=1;i<=200;i++)h.runAtTickTime(i,()->{
            try { engine.tick(level); } catch(Exception ex) { throw new RuntimeException(ex); }
        });
        h.runAtTickTime(250,()->{
            h.assertTrue(engine.stage()==Engine.Stage.DONE,"Engine did not finish: "+engine.stage());
            String settle=engine.settleReport();
            h.assertTrue(settle.contains("# AI Block Bridge Settle Report v1"),"No settle report:\n"+settle);
            h.assertTrue(settle.contains("east=side"),"Bare wires were not connected by settling:\n"+settle);
            String tests=engine.testReport();
            h.assertTrue(tests.contains("cases 2 | passed 2 | failed 0"),"Circuit did not pass its own tests:\n"+tests);
            h.succeed();
        });
    }

    @GameTest(structure="ai_block_bridge_2_test:empty",maxTicks=300)
    public void aSecondAttemptStartsFromAnEmptyRegion(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        Region r=region(h);
        new Engine(level,r,CIRCUIT,null);
        // Local (1,1,0) is the first wire, so absolute is the region origin offset by one east and one up.
        h.runAtTickTime(20,()->{
            h.assertTrue(level.getBlockState(h.absolutePos(new BlockPos(2,2,1))).is(Blocks.REDSTONE_WIRE),"First attempt was not placed");
            // A different circuit must not inherit leftovers from the first one.
            try { new Engine(level,r,"0 0 0 | minecraft:gold_block\n",null); }
            catch(Exception ex) { throw new RuntimeException(ex); }
        });
        h.runAtTickTime(40,()->{
            h.assertTrue(level.getBlockState(h.absolutePos(new BlockPos(1,1,1))).is(Blocks.GOLD_BLOCK),"Second attempt was not placed");
            h.assertTrue(level.getBlockState(h.absolutePos(new BlockPos(2,1,1))).isAir(),"Leftover stone from the first attempt");
            h.assertTrue(level.getBlockState(h.absolutePos(new BlockPos(1,2,1))).isAir(),"Leftover lever from the first attempt");
            h.assertTrue(level.getBlockState(h.absolutePos(new BlockPos(2,2,1))).isAir(),"Leftover wire from the first attempt");
            h.succeed();
        });
    }

    /** A typo in a block name must not cost the caller the contents of their region. */
    @GameTest(structure="ai_block_bridge_2_test:empty")
    public void anUnknownBlockLeavesTheRegionAlone(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        Region r=region(h);
        BridgeAutomation.clear(level,r);
        BridgeAutomation.place(level,r,"0 0 0 | minecraft:gold_block\n");
        boolean rejected=false;
        try { new Engine(level,r,"0 0 0 | minecraft:stone\n1 0 0 | minecraft:definitely_not_a_block\n",null); }
        catch(Exception ex) { rejected=true; }
        h.assertTrue(rejected,"An unknown block should be refused");
        h.assertTrue(level.getBlockState(h.absolutePos(new BlockPos(1,1,1))).is(Blocks.GOLD_BLOCK),
            "The failed attempt cleared the region anyway");
        h.succeed();
    }

    @GameTest(structure="ai_block_bridge_2_test:empty")
    public void capturingReadsBackWhatWasPlaced(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        Region r=region(h);
        BridgeAutomation.clear(level,r);
        BridgeAutomation.place(level,r,"0 0 0 | minecraft:stone\n1 0 0 | minecraft:gold_block\n");
        String captured=BridgeAutomation.capture(level,r);
        h.assertTrue(captured.contains("0 0 0 | minecraft:stone"),"Capture lost the stone:\n"+captured);
        h.assertTrue(captured.contains("1 0 0 | minecraft:gold_block"),"Capture lost the gold:\n"+captured);
        h.succeed();
    }
}
