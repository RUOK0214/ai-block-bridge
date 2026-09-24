package io.github.ruok0214.bridge;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The surface an automation add-on drives. Everything else in this package stays internal, so the
 * base mod keeps one documented way in rather than leaking its engine.
 *
 * <p>Nothing here starts a network connection; callers supply the text and receive text back.
 */
public final class BridgeAutomation {
    /** Flags used by placement: keep the scripted state, suppress neighbour and shape updates. */
    private static final int QUIET = 818;
    private BridgeAutomation() {}

    public static void validate(ServerLevel level, Region region) {
        BridgeServer.validateRegion(level, region);
    }

    /**
     * Resolves every block and its NBT against the registry without touching the world, so a typo
     * can be refused before anything is cleared or placed. Returns the blocks the script would touch.
     */
    public static int check(ServerLevel level, Region region, String script) throws Exception {
        return BridgeServer.prepare(level, region, script).size();
    }

    /** Places the script exactly as written, without neighbour updates. Returns the blocks touched. */
    public static int place(ServerLevel level, Region region, String script) throws Exception {
        List<BridgeServer.Cell> cells = BridgeServer.prepare(level, region, script);
        BridgeServer.apply(level, cells);
        return cells.size();
    }

    /** Lets the world recompute wire connections, power and support. Give it a few ticks before reporting. */
    public static void settle(ServerLevel level, Region region, String script) throws Exception {
        BridgeServer.runUpdates(level, BridgeServer.prepare(level, region, script));
    }

    /** What the script asked for versus what the world settled on. */
    public static String settleReport(ServerLevel level, Region region, String script, int ticks) throws Exception {
        List<BridgeServer.Cell> requested = BridgeServer.prepare(level, region, script);
        var rows = new SettleReport.Rows();
        for (BridgeServer.Cell cell : requested) {
            BlockState actual = level.getBlockState(cell.pos());
            if (actual.equals(cell.state())) continue;
            rows.add(new SettleReport.Row(cell.pos().getX() - region.x(), cell.pos().getY() - region.y(),
                cell.pos().getZ() - region.z(), BlockStateParser.serialize(cell.state()),
                BlockStateParser.serialize(actual)));
        }
        return SettleReport.render(region, ticks, requested.size(), rows);
    }

    public static String capture(ServerLevel level, Region region) {
        return BridgeServer.exportRegion(level, region);
    }

    /** Empties the region so the next attempt starts from nothing. Returns the blocks removed. */
    public static int clear(ServerLevel level, Region region) {
        BlockState air = Blocks.AIR.defaultBlockState();
        int removed = 0;
        for (BlockPos pos : BlockPos.betweenClosed(region.x(), region.y(), region.z(),
                region.maxX(), region.maxY(), region.maxZ())) {
            if (level.getBlockState(pos).isAir()) continue;
            level.removeBlockEntity(pos);
            level.setBlock(pos, air, QUIET);
            removed++;
        }
        return removed;
    }
}
