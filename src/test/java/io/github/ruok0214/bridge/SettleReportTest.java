package io.github.ruok0214.bridge;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettleReportTest {
    @Test void largeReportsKeepCountsButBoundStorageAndOutput() {
        var rows = new SettleReport.Rows();
        for (int i = 0; i < 20_000; i++)
            rows.add(new SettleReport.Row(i, 0, 0, "minecraft:stone", "minecraft:air"));
        assertEquals(SettleReport.MAX_ROWS, rows.size());
        String report = SettleReport.render(REGION, 10, 20_000, rows);
        assertTrue(report.contains("mismatched 20000 | removed 20000"));
        assertTrue(report.contains("showing first 1000 of 20000"));
        assertTrue(report.length() <= SettleReport.MAX_CHARS);
    }
    private static final Region REGION=Region.of(1,2,12,13,7,20);

    @Test void cleanRunSaysNothingDiffers() {
        String report=SettleReport.render(REGION,10,113,List.of());
        assertTrue(report.startsWith("# AI Block Bridge Settle Report v1\n"));
        assertTrue(report.contains("mismatched 0 | removed 0"));
        assertTrue(report.contains("Every requested block matches"));
    }

    @Test void mismatchLinesUseScriptCoordinateSyntax() {
        var rows=List.of(new SettleReport.Row(10,1,4,
            "minecraft:redstone_wire[east=side,north=none,power=0,south=side,west=none]",
            "minecraft:redstone_wire[east=side,north=side,power=0,south=side,west=none]"));
        String report=SettleReport.render(REGION,10,113,rows);
        assertTrue(report.contains("\n10 1 4 | requested minecraft:redstone_wire[east=side,north=none,"
            +"power=0,south=side,west=none] | actual minecraft:redstone_wire[east=side,north=side,"
            +"power=0,south=side,west=none]\n"));
        assertTrue(report.contains("mismatched 1 | removed 0"));
        assertFalse(report.contains("Every requested block matches"));
    }

    @Test void blocksThatVanishedCountAsRemoved() {
        var rows=List.of(new SettleReport.Row(0,1,0,"minecraft:redstone_wire[east=none,north=none,"
                +"power=0,south=none,west=none]","minecraft:air"),
            new SettleReport.Row(2,0,0,"minecraft:repeater[delay=2,facing=north,locked=false,powered=false]",
                "minecraft:air"),
            new SettleReport.Row(3,0,0,"minecraft:stone","minecraft:cobblestone"));
        String report=SettleReport.render(REGION,10,50,rows);
        assertTrue(report.contains("mismatched 3 | removed 2"));
    }

    @Test void headerCarriesRegionSoCoordinatesAreUnambiguous() {
        String report=SettleReport.render(REGION,10,1,List.of());
        assertTrue(report.contains("# size: 13 6 9"));
        assertTrue(report.contains("# origin: 1 2 12"));
        assertTrue(report.contains("settled after 10 ticks"));
    }
}
