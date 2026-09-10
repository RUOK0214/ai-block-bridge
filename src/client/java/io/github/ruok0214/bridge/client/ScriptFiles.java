package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.Script;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;

public final class ScriptFiles {
    public static Path choose(boolean save) throws IOException {
        return choose(save,"structure.txt");
    }
    public static Path choose(boolean save,String defaultName) throws IOException {
        Path directory=FabricLoader.getInstance().getGameDir().resolve("ai-block-bridge");
        Files.createDirectories(directory);
        String initial=directory.resolve(defaultName).toAbsolutePath().toString();
        String chosen=save?TinyFileDialogs.tinyfd_saveFileDialog("Export script (.txt)",initial,null,"UTF-8 text")
            :TinyFileDialogs.tinyfd_openFileDialog("Import script (.txt)",initial,null,"UTF-8 text",false);
        return chosen==null?null:Path.of(chosen);
    }
    public static String read(Path file) throws IOException {
        // Bound the actual read, not just the stat (the file can grow between the two).
        try(var stream=Files.newInputStream(file)) {
            byte[] bytes=stream.readNBytes(Script.MAX_CHARS*4+1);
            if(bytes.length>Script.MAX_CHARS*4)throw new IOException("파일이 너무 큽니다.");
            String text=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            if(text.length()>Script.MAX_CHARS)throw new IOException("최대 2,000,000자입니다.");
            return text;
        }
    }
    public static void write(Path file,String text) throws IOException {
        Path absolute=file.toAbsolutePath();
        Path temp=Files.createTempFile(absolute.getParent(),".ai-block-bridge-",".tmp");
        try {
            Files.writeString(temp,text,StandardCharsets.UTF_8);
            try { Files.move(temp,absolute,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException ex) { Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING); }
        }finally { Files.deleteIfExists(temp); }
    }
}
