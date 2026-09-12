package io.github.ruok0214.bridge;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class RecordingFiles {
    private RecordingFiles(){}
    /** Publish a complete, unique folder; existing recordings are never overwritten. */
    public static Path save(Path parent,RecordingBundle bundle) throws IOException {
        Files.createDirectories(parent);
        String prefix="recording_"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))+"_";
        Path staging=Files.createTempDirectory(parent,".recording-");
        Path target=parent.resolve(prefix+staging.getFileName().toString().substring(".recording-".length()));
        try {
            Files.writeString(staging.resolve("structure.txt"),bundle.structure());
            Files.writeString(staging.resolve("timeline.txt"),bundle.timeline());
            Files.move(staging,target);
            return target;
        }finally {
            Files.deleteIfExists(staging.resolve("structure.txt"));
            Files.deleteIfExists(staging.resolve("timeline.txt"));
            Files.deleteIfExists(staging);
        }
    }
}
