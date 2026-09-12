package io.github.ruok0214.bridge;

import java.nio.file.*;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RecordingBundleTest {
    @TempDir Path directory;
    @Test void chunkedUnicodeBundlePreservesBothFiles() {
        var original=new RecordingBundle("# 한글 🧱\n".repeat(1000),"# @tick 1\n{value:\"a\\nb\"}\n".repeat(1000));
        var packet=new BridgePacket(3,BridgePacket.STOP_RECORD,0,1,"minecraft:overworld",0,0,0,1,1,1,"");
        var packets=new ArrayList<BridgePacket>();
        packet.chunks(original.encode(),BridgePacket.RECORDING_BUNDLE,packets::add);
        var assembly=new Assembly(packets.getFirst());
        String received=null;
        for(var part:packets)received=assembly.append(part);
        assertEquals(original,RecordingBundle.decode(received));
    }
    @Test void malformedLengthsAreRejected() {
        for(String body:new String[]{"-1\na","999999999\na","1","abc\na"})
            assertThrows(IllegalArgumentException.class,()->RecordingBundle.decode(body));
    }
    @Test void repeatedSavesPublishCompleteIndependentFolders() throws Exception {
        var original=new RecordingBundle("# 구조\nstone\n","# 변화\n@tick 1\nair\n");
        Path first=RecordingFiles.save(directory,original),second=RecordingFiles.save(directory,original);
        assertNotEquals(first,second);
        for(Path folder:new Path[]{first,second}) {
            assertEquals(original.structure(),Files.readString(folder.resolve("structure.txt")));
            assertEquals(original.timeline(),Files.readString(folder.resolve("timeline.txt")));
            try(var files=Files.list(folder)){assertEquals(2,files.count());}
        }
        try(var files=Files.list(directory)){assertEquals(2,files.count());}
    }
    @Test void invalidParentDoesNotDamageExistingFile() throws Exception {
        Path file=directory.resolve("existing.txt");Files.writeString(file,"keep");
        assertThrows(java.io.IOException.class,()->RecordingFiles.save(file,new RecordingBundle("s","t")));
        assertEquals("keep",Files.readString(file));
    }
}
