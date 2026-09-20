package io.github.ruok0214.bridge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaptureOptionsTest {
    @Test void allOptimizationFlagsRoundTrip() {
        var original=new CaptureOptions(true,true,false,true,true,true,true,true,true);
        assertEquals(original,CaptureOptions.parse(original.encode()));
    }

    @Test void legacyDefaultsRemainReadable() {
        var options=CaptureOptions.parse("");
        assertTrue(options.ignoreHopperCooldown());
        assertFalse(options.paletteFormat());
        assertFalse(options.structureEntities());
        assertFalse(options.timelineEntities());
    }

    @Test void unknownFlagsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->CaptureOptions.parse("unknown"));
    }
}
