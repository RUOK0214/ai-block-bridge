package io.github.ruok0214.bridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScriptTest {
    private final Region r=Region.of(9,-3,8,7,-1,9);
    @Test void originUsesIndependentMinimums(){assertEquals(new Region(7,-3,8,9,-1,9),r);assertEquals(18,r.volume());}
    @Test void singleBlockIsInclusive(){assertEquals(1,Region.of(1,2,3,1,2,3).volume());}
    @Test void rejectsHugeAndOverflow(){assertThrows(IllegalArgumentException.class,()->Region.of(Integer.MIN_VALUE,0,0,Integer.MAX_VALUE,0,0));}
    @Test void allowsBoundarySize(){assertEquals(Region.MAX_BLOCKS,Region.of(0,0,0,127,127,63).volume());}
    @Test void acceptsSevenSegmentBounds(){assertEquals(154_980,Region.of(0,0,0,83,14,122).volume());}
    @Test void rejectsOneAboveLimit(){assertThrows(IllegalArgumentException.class,()->Region.of(0,0,0,Region.MAX_BLOCKS,0,0));}
    @Test void allowsLongThinBoundary(){assertEquals(Region.MAX_BLOCKS,Region.of(0,0,0,Region.MAX_BLOCKS-1,0,0).volume());}
    @Test void rejectsProductAboveLimit(){assertThrows(IllegalArgumentException.class,()->Region.of(0,0,0,1024,1023,0));}
    @Test void parsesCommentsCommasAndNbtPipe(){
        var e=Script.parse("\uFEFF# comment\r\n0, 2, 1 | minecraft:barrel[facing=up] | {CustomName:'a|b'}\r\n",r).getFirst();
        assertEquals(2,e.y());assertEquals("{CustomName:'a|b'}",e.nbt());assertEquals(2,e.line());
    }
    @Test void rejectsNegative(){assertThrows(IllegalArgumentException.class,()->Script.parse("-1 0 0 | minecraft:stone",r));}
    @Test void rejectsOutOfRange(){assertThrows(IllegalArgumentException.class,()->Script.parse("3 0 0 | minecraft:stone",r));}
    @Test void rejectsDuplicate(){assertThrows(IllegalArgumentException.class,()->Script.parse("0 0 0 | a\n0 0 0 | b",r));}
    @Test void rejectsEmpty(){assertThrows(IllegalArgumentException.class,()->Script.parse("# only comment",r));}
    @Test void rejectsMissingBlock(){assertThrows(IllegalArgumentException.class,()->Script.parse("0 0 0 | ",r));}
    @Test void rejectsMissingNbt(){assertThrows(IllegalArgumentException.class,()->Script.parse("0 0 0 | a | ",r));}
    @Test void historyIsIndependent(){var h=new TextHistory();h.remember("a");h.remember("b");assertEquals("b",h.undo("c"));assertEquals("a",h.undo("b"));assertEquals("a",h.undo("a"));}
}
