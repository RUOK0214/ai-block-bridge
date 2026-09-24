package io.github.ruok0214.bridge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestScriptTest {
    private static final Region REGION=Region.of(0,0,0,4,4,4);

    @Test void parsesCasesStepsAndIgnoresComments() {
        var cases=TestScript.parse("# header\n\n@case lamp on\n"
            +"set 0 1 0 | minecraft:lever[powered=true]\n"
            +"wait 10\n"
            +"expect 1 1 0 | minecraft:redstone_lamp[lit=true]\n"
            +"\n@case second\nwait 1\n",REGION);
        assertEquals(2,cases.size());
        assertEquals("lamp on",cases.get(0).name());
        assertEquals(3,cases.get(0).steps().size());
        var set=cases.get(0).steps().get(0);
        assertEquals(TestScript.SET,set.kind());
        assertEquals("minecraft:lever[powered=true]",set.state());
        assertEquals(0,set.x());
        assertEquals(1,set.y());
        assertEquals(10,cases.get(0).steps().get(1).ticks());
        assertEquals("second",cases.get(1).name());
    }

    @Test void commaSeparatedCoordinatesAreAccepted() {
        var cases=TestScript.parse("@case c\nexpect 1, 2, 3 | minecraft:stone",REGION);
        var step=cases.get(0).steps().getFirst();
        assertEquals(1,step.x());
        assertEquals(2,step.y());
        assertEquals(3,step.z());
    }

    @Test void stepsMustFollowACaseName() {
        assertThrows(IllegalArgumentException.class,()->TestScript.parse("wait 5",REGION));
        assertThrows(IllegalArgumentException.class,()->TestScript.parse("@case\nwait 5",REGION));
    }

    @Test void rejectsMalformedSteps() {
        String[] bad={"@case c\njump 1 1 1 | minecraft:stone",
                      "@case c\nwait 0",
                      "@case c\nwait -1",
                      "@case c\nwait",
                      "@case c\nset 0 0 0",
                      "@case c\nset 0 0 0 | ",
                      "@case c\nset 0 0 | minecraft:stone",
                      "@case c\nexpect 9 9 9 | minecraft:stone"};
        for(String text:bad) assertThrows(IllegalArgumentException.class,()->TestScript.parse(text,REGION),text);
    }

    @Test void emptyScriptIsRejected() {
        assertThrows(IllegalArgumentException.class,()->TestScript.parse("# only comments\n",REGION));
    }

    @Test void totalWaitIsCapped() {
        String within="@case c\nwait "+TestScript.MAX_TOTAL_TICKS;
        assertEquals(1,TestScript.parse(within,REGION).size());
        String over="@case c\nwait "+TestScript.MAX_TOTAL_TICKS+"\nwait 1";
        assertThrows(IllegalArgumentException.class,()->TestScript.parse(over,REGION));
    }

    @Test void oversizedScriptIsRejected() {
        String huge="@case c\n"+"wait 1\n".repeat(TestScript.MAX_CHARS);
        assertThrows(IllegalArgumentException.class,()->TestScript.parse(huge,REGION));
    }
}
