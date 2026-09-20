/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.loader.api.FabricLoader
 *  org.lwjgl.util.tinyfd.TinyFileDialogs
 *  org.slf4j.LoggerFactory
 */
package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.Messages;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.LoggerFactory;

public final class ScriptFiles {
    private static Path lastRecordingFolder;

    private static Path recordingFolderPreference() {
        return FabricLoader.getInstance().getConfigDir().resolve("ai-block-bridge-last-recording.txt");
    }

    public static void rememberRecordingFolder(Path folder) {
        lastRecordingFolder = folder.toAbsolutePath().normalize();
        try {
            Files.createDirectories(ScriptFiles.recordingFolderPreference().getParent(), new FileAttribute[0]);
            ScriptFiles.write(ScriptFiles.recordingFolderPreference(), lastRecordingFolder.toString());
        }
        catch (IOException ex) {
            LoggerFactory.getLogger((String)"ai_block_bridge").warn("Could not remember recording folder", (Throwable)ex);
        }
    }

    public static Path recordingFolder() throws IOException {
        if (lastRecordingFolder == null) {
            try {
                Path preference = ScriptFiles.recordingFolderPreference();
                if (Files.isRegularFile(preference, new LinkOption[0])) {
                    lastRecordingFolder = Path.of(ScriptFiles.read(preference, 8192).strip(), new String[0]).toAbsolutePath().normalize();
                }
            }
            catch (IOException | InvalidPathException ex) {
                LoggerFactory.getLogger((String)"ai_block_bridge").warn("Could not read recording folder preference", (Throwable)ex);
            }
        }
        if (lastRecordingFolder != null && Files.isDirectory(lastRecordingFolder, new LinkOption[0])) {
            return lastRecordingFolder;
        }
        Path fallback = FabricLoader.getInstance().getGameDir().resolve("ai-block-bridge").toAbsolutePath();
        Files.createDirectories(fallback, new FileAttribute[0]);
        return fallback;
    }

    public static Path choose(boolean save) throws IOException {
        return ScriptFiles.choose(save, "structure.txt");
    }

    public static Path choose(boolean save, String defaultName) throws IOException {
        Path directory = FabricLoader.getInstance().getGameDir().resolve("ai-block-bridge");
        Files.createDirectories(directory, new FileAttribute[0]);
        String initial = directory.resolve(defaultName).toAbsolutePath().toString();
        String chosen = save ? TinyFileDialogs.tinyfd_saveFileDialog((CharSequence)Messages.display(Messages.text("ai_block_bridge.dialog.export", new Object[0])), (CharSequence)initial, null, (CharSequence)Messages.display(Messages.text("ai_block_bridge.dialog.text", new Object[0]))) : TinyFileDialogs.tinyfd_openFileDialog((CharSequence)Messages.display(Messages.text("ai_block_bridge.dialog.import", new Object[0])), (CharSequence)initial, null, (CharSequence)Messages.display(Messages.text("ai_block_bridge.dialog.text", new Object[0])), (boolean)false);
        return chosen == null ? null : Path.of(chosen, new String[0]);
    }

    public static Path chooseDirectory() throws IOException {
        Path directory = FabricLoader.getInstance().getGameDir().resolve("ai-block-bridge").toAbsolutePath();
        Files.createDirectories(directory, new FileAttribute[0]);
        String chosen = TinyFileDialogs.tinyfd_selectFolderDialog((CharSequence)Messages.display(Messages.text("ai_block_bridge.bundle.directory", new Object[0])), (CharSequence)directory.toString());
        return chosen == null ? null : Path.of(chosen, new String[0]);
    }

    public static String read(Path file) throws IOException {
        return ScriptFiles.read(file, 2000000);
    }

    public static String read(Path file, int limit) throws IOException {
        try (InputStream stream = Files.newInputStream(file, new OpenOption[0]);){
            byte[] bytes = stream.readNBytes(limit * 4 + 1);
            if (bytes.length > limit * 4) {
                throw new IOException(Messages.text("ai_block_bridge.error.file_large", new Object[0]));
            }
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            if (text.length() > limit) {
                throw new IOException(Messages.text("ai_block_bridge.error.file_limit", limit));
            }
            String string = text;
            return string;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void write(Path file, String text) throws IOException {
        Path absolute = file.toAbsolutePath();
        Path temp = Files.createTempFile(absolute.getParent(), ".ai-block-bridge-", ".tmp", new FileAttribute[0]);
        try {
            Files.writeString(temp, (CharSequence)text, StandardCharsets.UTF_8, new OpenOption[0]);
            try {
                Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally {
            Files.deleteIfExists(temp);
        }
    }
}


