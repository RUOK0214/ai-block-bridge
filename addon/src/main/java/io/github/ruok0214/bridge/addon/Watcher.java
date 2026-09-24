package io.github.ruok0214.bridge.addon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import io.github.ruok0214.bridge.Messages;
import io.github.ruok0214.bridge.TestScript;
import java.util.ArrayList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watches a folder for requests and runs them one at a time.
 *
 * <p>Off until someone turns it on in game, because a request places blocks with operator rights.
 * Nothing here opens a socket: the only channel is the file system.
 */
public final class Watcher {
    private static final Logger LOG = LoggerFactory.getLogger(AddonMod.ID);
    private static final int POLL_TICKS = 10;
    /**
     * Twice the longest wait a test script may ask for, so the budget follows that limit instead of
     * being a number of its own. Only a stuck attempt reaches it, and without it one bad request
     * would block the queue for good.
     */
    private static final int MAX_REQUEST_TICKS = TestScript.MAX_TOTAL_TICKS * 2;
    /** Agents write "name.json.tmp" and rename, so a half-written file is never picked up. */
    private static final String SUFFIX = ".json";

    private final Path in, out, done, processing;
    private Path activeFile;
    private Response pendingResponse;
    private boolean enabled;
    private int countdown;
    private Engine engine;
    private Request current;
    private ServerLevel level;
    private long startedAt;
    private float previousTickRate;

    public Watcher(Path gameDirectory) {
        Path root = gameDirectory.resolve("ai-block-bridge-2");
        this.in = root.resolve("in");
        this.out = root.resolve("out");
        this.done = root.resolve("done");
        this.processing = root.resolve("processing");
    }

    public boolean enabled() { return enabled; }
    public Path inbox() { return in; }

    public void enable(boolean value) throws IOException {
        if (value) {
            Files.createDirectories(in);
            Files.createDirectories(out);
            Files.createDirectories(done);
            Files.createDirectories(processing);
        }
        enabled = value;
        LOG.info("Folder watching {}", value ? "enabled at " + in : "disabled");
    }

    public void tick(MinecraftServer server) {
        if (pendingResponse != null) {
            if (write(pendingResponse) && archive(activeFile)) {
                pendingResponse = null;
                activeFile = null;
            }
            return;
        }
        if (!enabled && engine != null) { cancel(server); return; }
        if (!enabled) return;
        if (engine != null) { advance(server); return; }
        if (countdown-- > 0) return;
        countdown = POLL_TICKS;
        Path file = next();
        if (file != null) start(server, file);
    }

    private Path next() {
        var found = new ArrayList<Path>();
        try (var stream = Files.newDirectoryStream(in, "*" + SUFFIX)) {
            for (Path path : stream) if (Files.isRegularFile(path)) found.add(path);
        }
        catch (IOException ex) { LOG.warn("Cannot read {}: {}", in, ex.toString()); return null; }
        // Oldest name first, so an agent can order attempts by naming them.
        return found.stream().min(Path::compareTo).orElse(null);
    }

    private void start(MinecraftServer server, Path file) {
        // A request that fails to parse still needs a reply the agent can find, so name it after the file.
        String name = file.getFileName().toString();
        String id = name.endsWith(SUFFIX) ? name.substring(0, name.length() - SUFFIX.length()) : name;
        try {
            Path claimed = processing.resolve(name);
            Files.move(file, claimed);
            activeFile = claimed;
        } catch (IOException ex) {
            enabled = false;
            LOG.warn("Cannot claim request; watching disabled: {}", ex.toString());
            return;
        }
        try {
            // Claim before editing; interrupted files remain here for manual inspection, never auto-replay.
            if (Files.size(activeFile) > 16_000_000L) throw new IllegalArgumentException("Request file too large");
            current = Request.parse(Files.readString(activeFile, StandardCharsets.UTF_8));
            id = current.id();
            startedAt = System.nanoTime();
            level = levelOf(server, current.dimension());
            engine = new Engine(level, current.region(), current.script(), current.test(), current.clear());
            applySpeed(server);
        }
        catch (Exception ex) {
            String reason = readable(ex);
            LOG.warn("Request {} failed: {}", id, reason);
            pendingResponse = Response.error(id, reason);
            finish(server);
        }
    }

    private void advance(MinecraftServer server) {
        try {
            engine.tick(level);
            if (engine.ticks() > MAX_REQUEST_TICKS) {
                LOG.warn("Request {} gave up after {} ticks", current.id(), engine.ticks());
                pendingResponse = Response.error(current.id(), "Gave up after " + MAX_REQUEST_TICKS + " ticks");
                finish(server);
                return;
            }
            if (engine.stage() != Engine.Stage.DONE) return;
            long ms = (System.nanoTime() - startedAt) / 1_000_000L;
            LOG.info("Request {} finished in {} ticks / {} ms", current.id(), engine.ticks(), ms);
            pendingResponse = Response.done(current.id(), engine.placed(), engine.ticks(), ms,
                engine.settleReport(), engine.testReport());
        }
        catch (Exception ex) {
            String reason = readable(ex);
            LOG.warn("Request {} failed while running: {}", current.id(), reason);
            pendingResponse = Response.error(current.id(), reason);
        }
        finish(server);
    }

    /** Clears the slot and takes the next request on the following tick rather than after a poll gap. */
    private void finish(MinecraftServer server) {
        restoreSpeed(server);
        engine = null;
        current = null;
        level = null;
        countdown = 0;
    }

    /** Cancelling does not undo edits; it always releases our speed override. */
    public void cancel(MinecraftServer server) {
        enabled = false;
        if (current != null) pendingResponse = Response.error(current.id(), "Cancelled; world edits are not rolled back");
        finish(server);
        if (pendingResponse != null && write(pendingResponse) && archive(activeFile)) {
            pendingResponse = null;
            activeFile = null;
        }
    }

    /**
     * Asks the server to finish the attempt sooner. Both levers are reported because sprinting is
     * accepted but ignored on some setups, and a silent no-op looks identical to a slow circuit.
     */
    private void applySpeed(MinecraftServer server) {
        var manager = server.tickRateManager();
        previousTickRate = manager.tickrate();
        if (current.sprint() > 0) {
            // The return value reports whether an earlier sprint was cut short, not whether this one took.
            boolean interrupted = manager.requestGameToSprint(current.sprint());
            LOG.info("Request {}: sprint {} ticks -> sprinting={} (interrupted an earlier sprint: {})",
                current.id(), current.sprint(), manager.isSprinting(), interrupted);
        }
        if (current.tickRate() > 0) {
            manager.setTickRate(current.tickRate());
            LOG.info("Request {}: tick rate {} -> now {}", current.id(), current.tickRate(), manager.tickrate());
        }
    }

    /** Never leave a world running fast after the attempt that asked for it. */
    private void restoreSpeed(MinecraftServer server) {
        var manager = server.tickRateManager();
        if (current != null && current.tickRate() > 0 && previousTickRate > 0) manager.setTickRate(previousTickRate);
        if (current != null && current.sprint() > 0 && manager.isSprinting()) manager.stopSprinting();
        previousTickRate = 0;
    }

    /** The base mod reports errors as encoded translation keys; an agent needs the sentence. */
    private static String readable(Exception ex) {
        String message = ex.getMessage();
        return message == null ? ex.getClass().getSimpleName() : Messages.display(message);
    }

    private static ServerLevel levelOf(MinecraftServer server, String dimension) {
        for (ServerLevel candidate : server.getAllLevels()) {
            if (candidate.dimension().identifier().toString().equals(dimension)) return candidate;
        }
        throw new IllegalArgumentException("No such dimension: " + dimension);
    }

    /** Writes beside the target then renames, so an agent never reads a partial reply. */
    private boolean write(Response response) {
        Path target = out.resolve(response.id() + SUFFIX);
        Path temporary = out.resolve(response.id() + SUFFIX + ".tmp");
        try {
            Files.writeString(temporary, response.toJson(), StandardCharsets.UTF_8);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        }
        catch (IOException ex) { LOG.warn("Cannot write {}: {}", target, ex.toString()); return false; }
    }

    private boolean archive(Path file) {
        if (file == null) return true;
        try { Files.move(file, done.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING); return true; }
        catch (IOException ex) { LOG.warn("Cannot archive {}: {}", file, ex.toString()); return false; }
    }
}
