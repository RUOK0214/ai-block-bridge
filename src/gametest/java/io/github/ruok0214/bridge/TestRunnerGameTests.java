package io.github.ruok0214.bridge;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

public class TestRunnerGameTests {
    /** Stone, a floor lever on top of it, and a lamp beside the lever. */
    private static final String CIRCUIT="0 0 0 | minecraft:stone\n"
        +"0 1 0 | minecraft:lever[face=floor,facing=north,powered=false]\n"
        +"1 1 0 | minecraft:redstone_lamp[lit=false]";

    private static Region build(GameTestHelper h, BlockPos origin) throws Exception {
        Region r=Region.of(origin.getX(),origin.getY(),origin.getZ(),origin.getX()+1,origin.getY()+1,origin.getZ());
        BridgeServer.apply(h.getLevel(),BridgeServer.prepare(h.getLevel(),r,CIRCUIT));
        return r;
    }

    private static void drive(GameTestHelper h, TestRunner runner, int ticks) {
        ServerLevel level=h.getLevel();
        for(int i=1;i<=ticks;i++)h.runAtTickTime(i,()->{ if(!runner.done())runner.tick(level); });
    }

    @GameTest(structure="ai_block_bridge_test:large_empty",maxTicks=120)
    public void passingCasesDriveAndObserveTheCircuit(GameTestHelper h) throws Exception {
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        Region r=build(h,origin);
        String script="@case lamp on\n"
            +"set 0 1 0 | minecraft:lever[powered=true]\n"
            +"wait 10\n"
            +"expect 1 1 0 | minecraft:redstone_lamp[lit=true]\n"
            +"@case lamp off\n"
            +"set 0 1 0 | minecraft:lever[powered=false]\n"
            +"wait 10\n"
            +"expect 1 1 0 | minecraft:redstone_lamp[lit=false]";
        TestRunner runner=new TestRunner(h.getLevel(),r,TestScript.parse(script,r));
        drive(h,runner,60);
        h.runAtTickTime(70,()->{
            h.assertTrue(runner.done(),"Runner did not finish");
            String report=runner.report();
            h.assertTrue(report.contains("cases 2 | passed 2 | failed 0"),"Expected both cases to pass: "+report);
            h.assertTrue(report.contains("@case lamp on : PASS")&&report.contains("@case lamp off : PASS"),report);
            h.succeed();
        });
    }

    @GameTest(structure="ai_block_bridge_test:large_empty",maxTicks=120)
    public void failingExpectationReportsExpectedAndActual(GameTestHelper h) throws Exception {
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        Region r=build(h,origin);
        // The lever is switched on, so expecting an unlit lamp must fail.
        String script="@case wrong guess\n"
            +"set 0 1 0 | minecraft:lever[powered=true]\n"
            +"wait 10\n"
            +"expect 1 1 0 | minecraft:redstone_lamp[lit=false]";
        TestRunner runner=new TestRunner(h.getLevel(),r,TestScript.parse(script,r));
        drive(h,runner,40);
        h.runAtTickTime(50,()->{
            String report=runner.report();
            h.assertTrue(report.contains("cases 1 | passed 0 | failed 1"),"Expected a failure: "+report);
            h.assertTrue(report.contains("@case wrong guess : FAIL"),report);
            h.assertTrue(report.contains("expected minecraft:redstone_lamp[lit=false]"),"Missing expectation: "+report);
            h.assertTrue(report.contains("actual   minecraft:redstone_lamp[lit=true]"),"Missing actual state: "+report);
            h.succeed();
        });
    }

    @GameTest(structure="ai_block_bridge_test:large_empty")
    public void unwrittenPropertiesAreIgnoredOnBothSides(GameTestHelper h) throws Exception {
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        Region r=build(h,origin);
        // Neither line names face or facing, so the lever keeps the orientation it was pasted with.
        String script="@case partial properties\n"
            +"set 0 1 0 | minecraft:lever[powered=true]\n"
            +"expect 0 1 0 | minecraft:lever[powered=true]";
        TestRunner runner=new TestRunner(h.getLevel(),r,TestScript.parse(script,r));
        runner.tick(h.getLevel());
        h.assertTrue(runner.done()&&runner.report().contains("passed 1"),"Partial property handling failed: "+runner.report());
        var lever=h.getLevel().getBlockState(origin.offset(0,1,0));
        h.assertTrue(lever.toString().contains("face=floor")&&lever.toString().contains("facing=north"),
            "Unwritten properties were overwritten: "+lever);
        h.succeed();
    }

    @GameTest(structure="ai_block_bridge_test:large_empty")
    public void badStateIsRejectedBeforeTouchingTheWorld(GameTestHelper h) throws Exception {
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        Region r=build(h,origin);
        var before=BridgeServer.snapshot(h.getLevel(),origin.offset(0,1,0));
        for(String bad:new String[]{"@case x\nset 0 1 0 | minecraft:nonexistent_block",
                                    "@case x\nexpect 0 1 0 | minecraft:lever[powered=maybe]"}) {
            boolean rejected=false;
            try{new TestRunner(h.getLevel(),r,TestScript.parse(bad,r));}catch(Exception ex){rejected=true;}
            h.assertTrue(rejected,"Accepted invalid test script: "+bad);
        }
        h.assertTrue(before.equals(BridgeServer.snapshot(h.getLevel(),origin.offset(0,1,0))),"Validation changed the world");
        h.succeed();
    }
}
