package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.Script;
import io.github.ruok0214.bridge.Messages;
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
        String chosen=save?TinyFileDialogs.tinyfd_saveFileDialog(Messages.display(Messages.text("ai_block_bridge.dialog.export")),initial,null,Messages.display(Messages.text("ai_block_bridge.dialog.text")))
            :TinyFileDialogs.tinyfd_openFileDialog(Messages.display(Messages.text("ai_block_bridge.dialog.import")),initial,null,Messages.display(Messages.text("ai_block_bridge.dialog.text")),false);
        return chosen==null?null:Path.of(chosen);
    }
    public static Path chooseDirectory() throws IOException {
        Path directory=FabricLoader.getInstance().getGameDir().resolve("ai-block-bridge").toAbsolutePath();
        Files.createDirectories(directory);
        String chosen=TinyFileDialogs.tinyfd_selectFolderDialog(Messages.display(Messages.text("ai_block_bridge.bundle.directory")),directory.toString());
        return chosen==null?null:Path.of(chosen);
    }
    public static String read(Path file) throws IOException {
        return read(file,Script.MAX_CHARS);
    }
    public static String read(Path file,int limit) throws IOException {
        // Bound the actual read, not just the stat (the file can grow between the two).
        try(var stream=Files.newInputStream(file)) {
            byte[] bytes=stream.readNBytes(limit*4+1);
            if(bytes.length>limit*4)throw new IOException(Messages.text("ai_block_bridge.error.file_large"));
            String text=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            if(text.length()>limit)throw new IOException(Messages.text("ai_block_bridge.error.file_limit", limit));
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
