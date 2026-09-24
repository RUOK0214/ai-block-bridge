package io.github.ruok0214.bridge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NbtPaletteTest {
    private final Region region=Region.of(0,0,0,15,7,15);
    private static final String BOOK="{components:{\"minecraft:written_book_content\":{author:\"RUOK0214\","
        +"pages:[{raw:\"0001\"},{raw:\"0010\"},{raw:\"0011\"},{raw:\"0100\"},{raw:\"0101\"},{raw:\"0110\"},"
        +"{raw:\"0111\"},{raw:\"1000\"}],resolved:1b,title:{raw:\"16\"}}},count:1,id:\"minecraft:written_book\"}";

    /** Three lecterns sharing one book but sitting on different pages, as the real recording had. */
    private static String lecterns() {
        StringBuilder text=new StringBuilder("# AI Block Bridge Script v1\n");
        int[] pages={8,4,9};
        for(int i=0;i<pages.length;i++) {
            text.append(i).append(" 0 0 | minecraft:lectern[facing=east,has_book=true,powered=false] | {Book:")
                .append(BOOK).append(",Page:").append(pages[i]).append(",components:{},id:\"minecraft:lectern\"}\n");
        }
        return text.toString();
    }

    private static java.util.List<String> normalized(String text, Region region) {
        return Script.parse(text,region).stream().map(e->e.x()+","+e.y()+","+e.z()+"|"+e.state()+"|"+e.nbt()).toList();
    }

    @Test void sharedFieldValueIsDeclaredOnceAndRoundTrips() {
        String readable=lecterns();
        String coded=PaletteFormat.encode(readable);
        assertTrue(coded.contains("# nbt 0 = "+BOOK),"Book value was not palettised:\n"+coded);
        assertEquals(1,coded.split("\\Qpages:\\E",-1).length-1,"Book body still repeats:\n"+coded);
        assertTrue(coded.contains("{Book:$0,Page:8,"),"Reference not written:\n"+coded);
        assertEquals(normalized(readable,region),normalized(coded,region));
        assertTrue(coded.length()<readable.length()*0.7,"Expected a clear saving: "+readable.length()+" -> "+coded.length());
    }

    @Test void valuesUsedOnceAreLeftAlone() {
        String readable="# h\n0 0 0 | minecraft:barrel[facing=up] | {Items:[{Slot:0b,id:\"minecraft:diamond\",count:3}],id:\"minecraft:barrel\"}\n";
        String coded=PaletteFormat.encode(readable);
        assertFalse(coded.contains("# nbt "));
        assertEquals(normalized(readable,region),normalized(coded,region));
    }

    @Test void shortRepeatsAreNotWorthAReference() {
        String readable="# h\n0 0 0 | minecraft:barrel | {id:\"a\"}\n1 0 0 | minecraft:barrel | {id:\"a\"}\n";
        String coded=PaletteFormat.encode(readable);
        assertFalse(coded.contains("# nbt "),coded);
        assertEquals(normalized(readable,region),normalized(coded,region));
    }

    @Test void separatorsInsideStringsDoNotSplitFields() {
        String tricky="{Text:\"a,b:c{d}e[f]\\\"g\",Other:'h,i:j',Long:"+BOOK+"}";
        String readable="# h\n0 0 0 | minecraft:barrel | "+tricky+"\n1 0 0 | minecraft:barrel | "+tricky+"\n";
        String coded=PaletteFormat.encode(readable);
        assertEquals(normalized(readable,region),normalized(coded,region));
        assertTrue(coded.contains("# nbt "),"Long shared value should still be shared:\n"+coded);
    }

    @Test void unknownReferenceIsRejected() {
        assertThrows(IllegalArgumentException.class,
            ()->Script.parse("0 0 0 | minecraft:barrel | {Book:$3}",region));
    }

    @Test void malformedDeclarationIsRejected() {
        assertThrows(IllegalArgumentException.class,
            ()->Script.parse("# nbt x = {a:1}\n0 0 0 | minecraft:stone",region));
        assertThrows(IllegalArgumentException.class,
            ()->Script.parse("# nbt 0 = {a:1}\n# nbt 0 = {b:2}\n0 0 0 | minecraft:stone",region));
    }

    @Test void blockStatePaletteStillWorksAlongside() {
        String coded=PaletteFormat.encode(lecterns());
        assertTrue(coded.contains("# palette 0 = minecraft:lectern[facing=east,has_book=true,powered=false]"));
        assertTrue(coded.contains("0 0 0 | 0 | "));
    }
}
