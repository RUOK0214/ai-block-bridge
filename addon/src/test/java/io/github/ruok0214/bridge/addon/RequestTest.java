package io.github.ruok0214.bridge.addon;

import io.github.ruok0214.bridge.Region;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestTest {
    @Test void nonFiniteTickRateIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> Request.parse(json("\"script\":\"0 0 0 | minecraft:stone\",\"tickRate\":\"NaN\"")));
    }
    private static final String SCRIPT="0 0 0 | minecraft:stone\\n1 0 0 | minecraft:stone";

    private static String json(String body) {
        return "{\"id\":\"a1\",\"region\":[0,64,0,7,66,0],"+body+"}";
    }

    @Test void readsEveryField() {
        var request=Request.parse("{\"id\":\"attempt-1\",\"dimension\":\"minecraft:the_nether\","
            +"\"region\":[0,64,0,7,66,0],\"script\":\""+SCRIPT+"\",\"test\":\"@case a\\nwait 1\","
            +"\"clear\":false,\"sprint\":200}");
        assertEquals("attempt-1",request.id());
        assertEquals("minecraft:the_nether",request.dimension());
        assertEquals(Region.of(0,64,0,7,66,0),request.region());
        assertTrue(request.script().startsWith("0 0 0 | minecraft:stone"));
        assertTrue(request.test().startsWith("@case a"));
        assertFalse(request.clear());
        assertEquals(200,request.sprint());
    }

    @Test void defaultsAreSafe() {
        var request=Request.parse(json("\"script\":\""+SCRIPT+"\""));
        assertEquals("minecraft:overworld",request.dimension());
        assertTrue(request.clear(),"A fresh attempt should start from an empty region");
        assertEquals(0,request.sprint(),"Sprinting must be asked for, not assumed");
        assertEquals("",request.test());
    }

    /**
     * Only syntax and coordinates can be judged here; block names need the game's registry, so the
     * engine resolves those before it clears anything. See EngineGameTests.
     */
    @Test void aBadScriptIsRejectedBeforeAnythingIsPlaced() {
        assertThrows(IllegalArgumentException.class,()->Request.parse(json("\"script\":\"99 0 0 | minecraft:stone\"")));
        assertThrows(IllegalArgumentException.class,()->Request.parse(json("\"script\":\"0 0 0\"")));
        assertThrows(IllegalArgumentException.class,()->Request.parse(json("\"script\":\"0 0 0 | \"")));
    }

    @Test void aBadTestScriptIsRejectedToo() {
        assertThrows(IllegalArgumentException.class,()->Request.parse(json("\"test\":\"wait 1\"")));
        assertThrows(IllegalArgumentException.class,()->Request.parse(json("\"test\":\"@case a\\njump 1\"")));
    }

    @Test void identifiersStayFileSafe() {
        for(String bad:new String[]{"","  ","../escape","a/b","a\\\\b","a b"})
            assertThrows(IllegalArgumentException.class,
                ()->Request.parse("{\"id\":\""+bad+"\",\"region\":[0,0,0,1,1,1],\"script\":\"0 0 0 | minecraft:stone\"}"),bad);
    }

    @Test void regionMustBeSixNumbers() {
        assertThrows(IllegalArgumentException.class,()->Request.parse("{\"id\":\"a\",\"script\":\"0 0 0 | minecraft:stone\"}"));
        assertThrows(IllegalArgumentException.class,
            ()->Request.parse("{\"id\":\"a\",\"region\":[0,0,0],\"script\":\"0 0 0 | minecraft:stone\"}"));
        assertThrows(IllegalArgumentException.class,
            ()->Request.parse("{\"id\":\"a\",\"region\":\"0 0 0 1 1 1\",\"script\":\"0 0 0 | minecraft:stone\"}"));
    }

    @Test void emptyRequestsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->Request.parse(json("\"script\":\"\"")));
        assertThrows(IllegalArgumentException.class,()->Request.parse("not json"));
        assertThrows(IllegalArgumentException.class,()->Request.parse("[]"));
    }

    @Test void sprintIsBounded() {
        assertEquals(Request.MAX_SPRINT,Request.parse(json("\"script\":\""+SCRIPT+"\",\"sprint\":"+Request.MAX_SPRINT)).sprint());
        assertThrows(IllegalArgumentException.class,()->Request.parse(json("\"script\":\""+SCRIPT+"\",\"sprint\":-1")));
        assertThrows(IllegalArgumentException.class,
            ()->Request.parse(json("\"script\":\""+SCRIPT+"\",\"sprint\":"+(Request.MAX_SPRINT+1))));
    }

    @Test void repliesCarryReportsAndErrorsApart() {
        String done=Response.done("a1",14,58,2915,"# settle","# test").toJson();
        assertTrue(done.contains("\"status\":\"done\""));
        assertTrue(done.contains("\"placed\":14"));
        assertFalse(done.contains("\"error\""));
        String failed=Response.error("a1","boom").toJson();
        assertTrue(failed.contains("\"status\":\"error\""));
        assertTrue(failed.contains("boom"));
        assertFalse(failed.contains("\"placed\""));
    }

    /** An agent needs to tell a slow attempt from a stuck one, and to see what sprinting bought it. */
    @Test void repliesReportWhatTheAttemptCost() {
        String done=Response.done("a1",14,58,2915,"# settle","# test").toJson();
        assertTrue(done.contains("\"ticks\":58"));
        assertTrue(done.contains("\"ms\":2915"));
    }
}
