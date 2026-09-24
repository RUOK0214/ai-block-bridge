package io.github.ruok0214.bridge;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RedstoneSide;

public class SettleGameTests {
    @GameTest(structure="ai_block_bridge_test:large_empty")
    public void settleRewiresDustConnections(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        Region r=Region.of(origin.getX(),origin.getY(),origin.getZ(),origin.getX()+1,origin.getY()+1,origin.getZ());
        // A script that claims two neighbouring dusts touch nothing, as a model often writes it.
        String script="0 0 0 | minecraft:stone\n1 0 0 | minecraft:stone\n"
            +"0 1 0 | minecraft:redstone_wire[east=none,north=none,power=0,south=none,west=none]\n"
            +"1 1 0 | minecraft:redstone_wire[east=none,north=none,power=0,south=none,west=none]";
        var cells=BridgeServer.prepare(level,r,script);
        BridgeServer.apply(level,cells);
        BlockPos west=origin.offset(0,1,0), east=origin.offset(1,1,0);
        h.assertTrue(level.getBlockState(west).getValue(BlockStateProperties.EAST_REDSTONE)==RedstoneSide.NONE,
            "Paste should preserve the scripted state verbatim");

        BridgeServer.runUpdates(level,cells);

        h.assertTrue(level.getBlockState(west).getValue(BlockStateProperties.EAST_REDSTONE)==RedstoneSide.SIDE,
            "Settle did not reconnect the west dust: "+level.getBlockState(west));
        h.assertTrue(level.getBlockState(east).getValue(BlockStateProperties.WEST_REDSTONE)==RedstoneSide.SIDE,
            "Settle did not reconnect the east dust: "+level.getBlockState(east));
        h.succeed();
    }

    /** The README tells authors they may write the block alone and let the settle pass derive the rest. */
    @GameTest(structure="ai_block_bridge_test:large_empty")
    public void bareBlockNamesGetTheirDerivedStateFromSettling(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        Region r=Region.of(origin.getX(),origin.getY(),origin.getZ(),origin.getX()+1,origin.getY()+1,origin.getZ());
        var cells=BridgeServer.prepare(level,r,"0 0 0 | minecraft:stone\n1 0 0 | minecraft:stone\n"
            +"0 1 0 | minecraft:redstone_wire\n1 1 0 | minecraft:redstone_wire");
        BridgeServer.apply(level,cells);
        BridgeServer.runUpdates(level,cells);
        BlockPos west=origin.offset(0,1,0), east=origin.offset(1,1,0);
        h.assertTrue(level.getBlockState(west).getValue(BlockStateProperties.EAST_REDSTONE)==RedstoneSide.SIDE,
            "Bare dust did not connect eastward: "+level.getBlockState(west));
        h.assertTrue(level.getBlockState(east).getValue(BlockStateProperties.WEST_REDSTONE)==RedstoneSide.SIDE,
            "Bare dust did not connect westward: "+level.getBlockState(east));
        h.succeed();
    }

    @GameTest(structure="ai_block_bridge_test:large_empty")
    public void settleDropsUnsupportedDust(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        Region r=Region.of(origin.getX(),origin.getY(),origin.getZ(),origin.getX(),origin.getY()+1,origin.getZ());
        String script="0 0 0 | minecraft:air\n"
            +"0 1 0 | minecraft:redstone_wire[east=none,north=none,power=0,south=none,west=none]";
        var cells=BridgeServer.prepare(level,r,script);
        BridgeServer.apply(level,cells);
        BlockPos dust=origin.offset(0,1,0);
        h.assertTrue(level.getBlockState(dust).is(Blocks.REDSTONE_WIRE),"Paste should place dust even without support");

        BridgeServer.runUpdates(level,cells);

        h.assertTrue(level.getBlockState(dust).isAir(),
            "Settle left unsupported dust in place: "+level.getBlockState(dust));
        h.succeed();
    }

    /** Nothing was pasted beside this one, so only its own support check can catch it. */
    @GameTest(structure="ai_block_bridge_test:large_empty")
    public void settleDropsIsolatedUnsupportedBlocks(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        Region r=Region.of(origin.getX(),origin.getY(),origin.getZ(),origin.getX(),origin.getY()+2,origin.getZ());
        var cells=BridgeServer.prepare(level,r,"0 2 0 | minecraft:redstone_wire");
        BridgeServer.apply(level,cells);
        BlockPos dust=origin.offset(0,2,0);
        h.assertTrue(level.getBlockState(dust).is(Blocks.REDSTONE_WIRE),"Paste should place the floating dust");

        BridgeServer.runUpdates(level,cells);

        h.assertTrue(level.getBlockState(dust).isAir(),
            "Isolated dust with no support survived: "+level.getBlockState(dust));
        h.succeed();
    }

    @GameTest(structure="ai_block_bridge_test:large_empty")
    public void reportListsOnlyMismatches(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        Region r=Region.of(origin.getX(),origin.getY(),origin.getZ(),origin.getX()+1,origin.getY()+1,origin.getZ());
        String script="0 0 0 | minecraft:stone\n1 0 0 | minecraft:stone\n"
            +"0 1 0 | minecraft:redstone_wire[east=none,north=none,power=0,south=none,west=none]\n"
            +"1 1 0 | minecraft:redstone_wire[east=none,north=none,power=0,south=none,west=none]";
        var cells=BridgeServer.prepare(level,r,script);
        BridgeServer.apply(level,cells);
        BridgeServer.runUpdates(level,cells);

        var rows=new java.util.ArrayList<SettleReport.Row>();
        for(var cell:cells) {
            var actual=level.getBlockState(cell.pos());
            if(actual.equals(cell.state()))continue;
            rows.add(new SettleReport.Row(cell.pos().getX()-r.x(),cell.pos().getY()-r.y(),cell.pos().getZ()-r.z(),
                net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(cell.state()),
                net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(actual)));
        }
        String report=SettleReport.render(r,BridgeServer.SETTLE_TICKS,cells.size(),rows);
        h.assertTrue(rows.size()==2,"Expected exactly the two dust rows, got "+rows.size()+": "+report);
        h.assertTrue(report.contains("mismatched 2")&&report.contains("removed 0"),"Bad counts: "+report);
        h.assertTrue(report.contains("0 1 0 | requested ")&&report.contains(" | actual "),"Missing diff line: "+report);
        h.assertTrue(!report.contains("0 0 0 |"),"Matching stone should not be listed: "+report);
        h.succeed();
    }

    @GameTest(structure="ai_block_bridge_test:large_empty")
    public void reportIsCleanWhenNothingChanges(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        Region r=Region.of(origin.getX(),origin.getY(),origin.getZ(),origin.getX(),origin.getY(),origin.getZ());
        var cells=BridgeServer.prepare(level,r,"0 0 0 | minecraft:stone");
        BridgeServer.apply(level,cells);
        BridgeServer.runUpdates(level,cells);
        String report=SettleReport.render(r,BridgeServer.SETTLE_TICKS,cells.size(),List.of());
        h.assertTrue(report.contains("Every requested block matches"),"Clean report missing: "+report);
        h.assertTrue(report.contains("mismatched 0"),"Clean report counts wrong: "+report);
        h.succeed();
    }
}
