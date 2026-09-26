package io.github.ruok0214.bridge;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class TestRunnerGameTests {
    private record Mount(String state, int x, int y, int z, boolean torchOnSouth) {}
    private static final Mount[] MOUNTS = {
        new Mount("face=floor,facing=north", 0, 1, 0, false),
        new Mount("face=ceiling,facing=north", 0, -1, 0, false),
        new Mount("face=wall,facing=north", 0, 0, -1, false),
        new Mount("face=wall,facing=south", 0, 0, 1, false),
        new Mount("face=wall,facing=east", 1, 0, 0, true),
        new Mount("face=wall,facing=west", -1, 0, 0, true)
    };

    /** Each torch reads the support, and is diagonal (not adjacent) to its switch. */
    @GameTest(structure="ai_block_bridge_test:large_empty", maxTicks=220)
    public void leversAndButtonsNotifyTheirSupportInEveryMount(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(1, 1, 1));
        Region region = Region.of(origin.getX(), origin.getY(), origin.getZ(), origin.getX()+52, origin.getY()+8, origin.getZ()+16);
        StringBuilder circuit = new StringBuilder(), script = new StringBuilder();
        String[] switches = {"minecraft:lever", "minecraft:stone_button"};
        for (int type = 0; type < switches.length; type++) {
            for (int i = 0; i < MOUNTS.length; i++) {
                Mount mount = MOUNTS[i];
                int x = 4 + i * 8, y = 4, z = 4 + type * 8;
                String support = x + " " + y + " " + z;
                String input = (x+mount.x()) + " " + (y+mount.y()) + " " + (z+mount.z());
                String torch = (x+(mount.torchOnSouth()?0:1)) + " " + y + " " + (z+(mount.torchOnSouth()?1:0));
                circuit.append(support).append(" | minecraft:stone\n")
                    .append(input).append(" | ").append(switches[type]).append("[").append(mount.state()).append(",powered=false]\n")
                    .append(torch).append(" | minecraft:redstone_wall_torch[facing=").append(mount.torchOnSouth()?"south":"east").append(",lit=true]\n");
                script.append("@case ").append(switches[type]).append(" ").append(mount.state()).append("\n")
                    .append("expect ").append(torch).append(" | minecraft:redstone_wall_torch[lit=true]\n")
                    .append("set ").append(input).append(" | ").append(switches[type]).append("[powered=true]\nwait 6\n")
                    .append("expect ").append(torch).append(" | minecraft:redstone_wall_torch[lit=false]\n")
                    .append("set ").append(input).append(" | ").append(switches[type]).append("[powered=false]\nwait 6\n")
                    .append("expect ").append(torch).append(" | minecraft:redstone_wall_torch[lit=true]\n");
            }
        }
        var cells = BridgeServer.prepare(level, region, circuit.toString());
        BridgeServer.apply(level, cells);
        BridgeServer.runUpdates(level, cells);
        TestRunner runner = new TestRunner(level, region, TestScript.parse(script.toString(), region));
        drive(h, runner,180);
        h.runAtTickTime(190, () -> {
            h.assertTrue(runner.done() && runner.report().contains("cases 12 | passed 12 | failed 0"), runner.report());
            h.succeed();
        });
    }

    @GameTest(structure="ai_block_bridge_test:large_empty", maxTicks=100)
    public void rotatingAPoweredSwitchNotifiesOldAndNewSupports(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(1, 1, 1));
        Region r = Region.of(o.getX(), o.getY(), o.getZ(), o.getX()+6, o.getY()+5, o.getZ()+5);
        String circuit = "2 3 3 | minecraft:stone\n4 3 3 | minecraft:stone\n"
            + "2 3 2 | minecraft:redstone_wall_torch[facing=north,lit=true]\n"
            + "4 3 2 | minecraft:redstone_wall_torch[facing=north,lit=true]\n"
            + "3 3 3 | minecraft:lever[face=wall,facing=east,powered=false]\n";
        var cells = BridgeServer.prepare(level, r, circuit);
        BridgeServer.apply(level, cells);
        BridgeServer.runUpdates(level, cells);
        String script = "@case move the powered attachment\n"
            + "set 3 3 3 | minecraft:lever[powered=true]\nwait 6\n"
            + "expect 2 3 2 | minecraft:redstone_wall_torch[lit=false]\n"
            + "expect 4 3 2 | minecraft:redstone_wall_torch[lit=true]\n"
            + "set 3 3 3 | minecraft:lever[facing=west]\nwait 6\n"
            + "expect 2 3 2 | minecraft:redstone_wall_torch[lit=true]\n"
            + "expect 4 3 2 | minecraft:redstone_wall_torch[lit=false]\n"
            + "set 3 3 3 | minecraft:lever[powered=false]\nwait 6\n"
            + "expect 2 3 2 | minecraft:redstone_wall_torch[lit=true]\n"
            + "expect 4 3 2 | minecraft:redstone_wall_torch[lit=true]\n";
        TestRunner runner = new TestRunner(level, r, TestScript.parse(script, r));
        drive(h, runner, 40);
        h.runAtTickTime(50, () -> {
            h.assertTrue(runner.done() && runner.report().contains("passed 1 | failed 0"), runner.report());
            h.succeed();
        });
    }

    @GameTest(structure="ai_block_bridge_test:large_empty", maxTicks=60)
    public void switchUpdatesDoNotWakeUnrelatedNeighbours(GameTestHelper h) throws Exception {
        unrelatedTorchStaysUntouched(h, "minecraft:lever[face=floor,facing=north,powered=false]", "minecraft:lever[powered=true]");
    }

    @GameTest(structure="ai_block_bridge_test:large_empty", maxTicks=60)
    public void noOpSwitchSetDoesNotWakeItsSupport(GameTestHelper h) throws Exception {
        // This sentinel is on the actual support, so unconditional support notifications also fail.
        untouchedTorch(h, "minecraft:lever[face=floor,facing=north,powered=false]", "minecraft:lever[powered=false]", true);
    }

    @GameTest(structure="ai_block_bridge_test:large_empty", maxTicks=60)
    public void nonSwitchSetDoesNotFanOutUpdates(GameTestHelper h) throws Exception {
        unrelatedTorchStaysUntouched(h, "minecraft:note_block[note=0]", "minecraft:note_block[note=1]");
    }

    private static void unrelatedTorchStaysUntouched(GameTestHelper h, String input, String change) throws Exception {
        untouchedTorch(h, input, change, false);
    }

    private static void untouchedTorch(GameTestHelper h, String input, String change, boolean onSupport) throws Exception {
        var level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(1, 1, 1));
        Region r = Region.of(o.getX(), o.getY(), o.getZ(), o.getX()+6, o.getY()+5, o.getZ()+5);
        String support = onSupport ? "3 2 3" : "4 3 3";
        String torch = onSupport ? "4 2 3" : "5 3 3";
        String circuit = "3 2 3 | minecraft:stone\n" + (onSupport ? "" : support + " | minecraft:stone\n")
            + torch + " | minecraft:redstone_wall_torch[facing=east,lit=true]\n3 3 3 | " + input + "\n";
        var cells = BridgeServer.prepare(level, r, circuit);
        BridgeServer.apply(level, cells);
        BridgeServer.runUpdates(level, cells);
        BlockPos sentinel = o.offset(onSupport?4:5, onSupport?2:3, 3);
        TestRunner runner = new TestRunner(level, r, TestScript.parse("@case no unrelated update\nset 3 3 3 | " + change, r));
        h.runAtTickTime(10, () -> {
            // Deliberately stale state: only an unwanted neighbour notification can wake it.
            level.setBlock(sentinel, Blocks.REDSTONE_WALL_TORCH.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                .setValue(BlockStateProperties.LIT, false), Block.UPDATE_CLIENTS);
            runner.tick(level);
        });
        h.runAtTickTime(20, () -> {
            h.assertTrue(runner.done() && runner.report().contains("passed 1 | failed 0"), runner.report());
            h.assertTrue(!level.getBlockState(sentinel).getValue(BlockStateProperties.LIT), "Unwanted notification woke an unrelated torch");
            h.succeed();
        });
    }

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

    /**
     * A lever drives its support block, and a torch reading that block is a neighbour of the support
     * rather than of the lever. Without updating the support, the torch never notices and every
     * latch or flip-flop fails its own test while being perfectly correct.
     */
    @GameTest(structure="ai_block_bridge_test:large_empty",maxTicks=120)
    public void drivingASwitchReachesWhateverItsSupportFeeds(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        BlockPos o=h.absolutePos(new BlockPos(1,1,1));
        Region r=Region.of(o.getX(),o.getY(),o.getZ(),o.getX()+2,o.getY()+2,o.getZ());
        String circuit="0 0 0 | minecraft:stone\n1 0 0 | minecraft:stone\n2 0 0 | minecraft:stone\n"
            +"1 1 0 | minecraft:stone\n"
            +"2 1 0 | minecraft:redstone_wall_torch[facing=east]\n"
            +"1 2 0 | minecraft:lever[face=floor,facing=north,powered=false]\n";
        var cells=BridgeServer.prepare(level,r,circuit);
        BridgeServer.apply(level,cells);
        BridgeServer.runUpdates(level,cells);
        BlockPos torch=o.offset(2,1,0);
        h.assertTrue(level.getBlockState(torch).getValue(BlockStateProperties.LIT),"Torch should start lit");

        String script="@case flipping the lever puts the torch out\n"
            +"set 1 2 0 | minecraft:lever[powered=true]\nwait 6\n"
            +"expect 2 1 0 | minecraft:redstone_wall_torch[lit=false]\n";
        TestRunner runner=new TestRunner(level,r,TestScript.parse(script,r));
        drive(h,runner,40);
        h.runAtTickTime(60,()->{
            h.assertTrue(runner.report().contains("passed 1"),
                "Driving the lever never reached the torch:\n"+runner.report());
            h.succeed();
        });
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
