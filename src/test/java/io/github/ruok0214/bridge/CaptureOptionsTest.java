package io.github.ruok0214.bridge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaptureOptionsTest {
    @Test void legacyFlagsKeepTheirMeaning() {
        assertEquals(new CaptureOptions(false,false,true),CaptureOptions.parse(""));
        assertEquals(new CaptureOptions(false,false,false),CaptureOptions.parse("include-cooldown"));
    }
    @Test void optionsAreIndependentAndRoundTrip() {
        for(boolean structure:new boolean[]{false,true})for(boolean timeline:new boolean[]{false,true})
            for(boolean cooldown:new boolean[]{false,true})for(boolean noise:new boolean[]{false,true}) {
                var options=new CaptureOptions(structure,timeline,cooldown,noise);
                assertEquals(options,CaptureOptions.parse(options.encode()));
            }
        assertThrows(IllegalArgumentException.class,()->CaptureOptions.parse("entities-maybe"));
    }
    @Test void observationCommentsDoNotChangeBlockPlacement() {
        Region region=Region.of(0,0,0,1,1,1);
        String blocks="0 0 0 | minecraft:stone\n";
        String entity="# @entity initial 00000000-0000-0000-0000-000000000001 | 0.5 0.5 0.5 | minecraft:item | {CustomName:\"x|y\"}\n";
        assertEquals(Script.parse(blocks,region),Script.parse(blocks+entity,region));
    }
}
