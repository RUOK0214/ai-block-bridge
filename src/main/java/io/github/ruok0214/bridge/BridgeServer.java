/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.StringReader
 *  net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
 *  net.minecraft.commands.arguments.blocks.BlockStateParser
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.HolderLookup
 *  net.minecraft.core.HolderLookup$Provider
 *  net.minecraft.core.registries.Registries
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.nbt.TagParser
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.server.permissions.Permissions
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.EntityBlock
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.state.BlockState
 *  org.slf4j.LoggerFactory
 */
package io.github.ruok0214.bridge;

import com.mojang.brigadier.StringReader;
import io.github.ruok0214.bridge.Assembly;
import io.github.ruok0214.bridge.BridgePacket;
import io.github.ruok0214.bridge.CaptureOptions;
import io.github.ruok0214.bridge.EntitySnapshot;
import io.github.ruok0214.bridge.Messages;
import io.github.ruok0214.bridge.Region;
import io.github.ruok0214.bridge.Script;
import io.github.ruok0214.bridge.TickRecorder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.LoggerFactory;

public final class BridgeServer {
    private static final Map<UUID, Assembly> incoming = new HashMap<UUID, Assembly>();
    private static final Map<UUID, Undo> undos = new HashMap<UUID, Undo>();
    private static final Map<UUID, Recording> recordings = new HashMap<UUID, Recording>();
    private static final Map<UUID, Settle> settles = new HashMap<UUID, Settle>();
    private static final Map<UUID, TestRun> tests = new HashMap<UUID, TestRun>();
    private static final Map<UUID, Long> lastRequest = new HashMap<UUID, Long>();
    /** Repeaters and comparators need a few ticks before the circuit stops changing. */
    static final int SETTLE_TICKS = 10;

    public static void clear(UUID id) {
        incoming.remove(id);
        undos.remove(id);
        recordings.remove(id);
        settles.remove(id);
        tests.remove(id);
        lastRequest.remove(id);
    }

    public static void clearAll() {
        incoming.clear();
        undos.clear();
        recordings.clear();
        settles.clear();
        tests.clear();
        lastRequest.clear();
    }

    public static void tick(MinecraftServer server) {
        for (Map.Entry<UUID, Recording> entry : recordings.entrySet()) {
            String notice;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Recording recording = entry.getValue();
            if (player == null || !recording.dimension.equals(player.level().dimension().identifier().toString())) continue;
            if (!recording.recorder.stopped()) {
                recording.recorder.capture(player.level());
            }
            if ((notice = recording.recorder.takeStopNotice()) == null) continue;
            recording.request.chunks(notice, 8, p -> ServerPlayNetworking.send((ServerPlayer)player, (CustomPacketPayload)p));
        }
        BridgeServer.tickSettles(server);
        BridgeServer.tickTests(server);
    }

    private static void tickTests(MinecraftServer server) {
        for (Map.Entry<UUID, TestRun> entry : List.copyOf(tests.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            TestRun run = entry.getValue();
            if (player == null || !run.dimension.equals(player.level().dimension().identifier().toString())) {
                tests.remove(entry.getKey());
                continue;
            }
            try {
                run.runner.tick(player.level());
            }
            catch (Exception ex) {
                run.runner.stop(BridgeServer.safeMessage(ex));
            }
            if (!run.runner.done()) continue;
            tests.remove(entry.getKey());
            run.request.chunks(run.runner.report(), BridgePacket.TEST_REPORT, p -> ServerPlayNetworking.send((ServerPlayer)player, (CustomPacketPayload)p));
        }
    }

    private static void tickSettles(MinecraftServer server) {
        for (Map.Entry<UUID, Settle> entry : List.copyOf(settles.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Settle settle = entry.getValue();
            if (player == null || !settle.dimension.equals(player.level().dimension().identifier().toString())) {
                settles.remove(entry.getKey());
                continue;
            }
            if (--settle.remaining > 0) continue;
            settles.remove(entry.getKey());
            try {
                BridgeServer.report(player, settle);
            }
            catch (Exception ex) {
                BridgeServer.reply(player, settle.request, Messages.text("ai_block_bridge.error", BridgeServer.safeMessage(ex)));
            }
        }
    }

    private static void report(ServerPlayer player, Settle settle) {
        ServerLevel level = player.level();
        SettleReport.Rows rows = new SettleReport.Rows();
        for (Cell cell : settle.target) {
            BlockState actual = level.getBlockState(cell.pos);
            if (actual.equals((Object)cell.state)) continue;
            rows.add(new SettleReport.Row(cell.pos.getX() - settle.region.x(), cell.pos.getY() - settle.region.y(),
                cell.pos.getZ() - settle.region.z(), BlockStateParser.serialize((BlockState)cell.state),
                BlockStateParser.serialize((BlockState)actual)));
        }
        // Settling moved the world past the snapshot taken at paste time, which undo verifies against.
        Undo undo = undos.get(player.getUUID());
        if (undo != null && undo.after != null && undo.dimension.equals(settle.dimension)) {
            undos.put(player.getUUID(), new Undo(undo.dimension, undo.before,
                undo.before.stream().map(c -> BridgeServer.snapshot(level, c.pos)).toList()));
        }
        String text = SettleReport.render(settle.region, SETTLE_TICKS, settle.target.size(), rows);
        settle.request.chunks(text, BridgePacket.SETTLE_REPORT, p -> ServerPlayNetworking.send((ServerPlayer)player, (CustomPacketPayload)p));
    }

    public static void receive(ServerPlayer player, BridgePacket packet) {
        try {
            Assembly assembly;
            if (!player.permissions().hasPermission(Permissions.COMMANDS_OWNER)) {
                throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.permission", new Object[0]));
            }
            if (packet.action() != 0 && packet.action() != 1 && packet.action() != 2 && packet.action() != 5 && packet.action() != 6 && packet.action() != BridgePacket.SETTLE && packet.action() != BridgePacket.RUN_TEST) {
                throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.operation", new Object[0]));
            }
            if (!player.level().dimension().identifier().toString().equals(packet.dimension())) {
                throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.dimension", new Object[0]));
            }
            UUID id = player.getUUID();
            if (packet.index() == 0) {
                long now = System.nanoTime();
                if (now - lastRequest.getOrDefault(id, 0L) < 1000000000L) {
                    throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.rate_limit", new Object[0]));
                }
                lastRequest.put(id, now);
                incoming.put(id, new Assembly(packet));
            }
            if ((assembly = incoming.get(id)) == null) {
                throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.restart_transfer", new Object[0]));
            }
            String body = assembly.append(packet);
            if (body == null) {
                return;
            }
            incoming.remove(id);
            ServerLevel level = player.level();
            if (packet.action() == 2) {
                BridgeServer.undo(player, packet, level);
                return;
            }
            if (packet.action() == 6) {
                BridgeServer.stopRecording(player, packet);
                return;
            }
            Region region = packet.region();
            BridgeServer.validateRegion(level, region);
            if (packet.action() == 5) {
                BridgeServer.startRecording(player, packet, level, region, body);
                return;
            }
            if (packet.action() == BridgePacket.SETTLE) {
                BridgeServer.settle(player, packet, level, region, body);
                return;
            }
            if (packet.action() == BridgePacket.RUN_TEST) {
                BridgeServer.startTest(player, packet, level, region, body);
                return;
            }
            if (packet.action() == 0) {
                CaptureOptions options=CaptureOptions.parse(body);
                String result = BridgeServer.exportRegion(level, region, options.structureEntities());
                if(options.paletteFormat())result=PaletteFormat.encode(result);
                packet.chunks(result, 4, p -> ServerPlayNetworking.send((ServerPlayer)player, (CustomPacketPayload)p));
            } else {
                BridgeServer.paste(player, packet, level, region, body);
            }
        }
        catch (Exception ex) {
            incoming.remove(player.getUUID());
            BridgeServer.reply(player, packet, Messages.text("ai_block_bridge.error", BridgeServer.safeMessage(ex)));
        }
    }

    private static void startRecording(ServerPlayer player, BridgePacket packet, ServerLevel level, Region region, String body) {
        if (recordings.containsKey(player.getUUID())) {
            throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.already_recording", new Object[0]));
        }
        CaptureOptions options = CaptureOptions.parse(body);
        recordings.put(player.getUUID(), new Recording(packet.dimension(), new TickRecorder(level, region, options), packet));
        BridgeServer.reply(player, packet, Messages.text("ai_block_bridge.timeline.started", new Object[0]));
    }

    /** Lets the world recompute wire connections, power and support that a script had to spell out by hand. */
    static void runUpdates(ServerLevel level, List<Cell> cells) {
        // Shape updates fix connection properties; block updates drive power and support checks.
        for (Cell cell : cells) {
            level.getBlockState(cell.pos).updateNeighbourShapes(level, cell.pos, Block.UPDATE_ALL);
        }
        for (Cell cell : cells) {
            level.updateNeighborsAt(cell.pos, level.getBlockState(cell.pos).getBlock());
        }
        // A block only pops on its own neighbour update, so one with nothing pasted beside it
        // would otherwise survive without support.
        for (Cell cell : cells) {
            BlockState state = level.getBlockState(cell.pos);
            if (state.isAir() || state.canSurvive(level, cell.pos)) continue;
            level.destroyBlock(cell.pos, true, null, 512);
        }
    }

    private static void settle(ServerPlayer player, BridgePacket packet, ServerLevel level, Region region, String body) throws Exception {
        List<Cell> target = BridgeServer.prepare(level, region, body);
        BridgeServer.runUpdates(level, target);
        settles.put(player.getUUID(), new Settle(packet.dimension(), region, target, packet, SETTLE_TICKS));
    }

    private static void startTest(ServerPlayer player, BridgePacket packet, ServerLevel level, Region region, String body) {
        if (tests.containsKey(player.getUUID())) {
            throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.test_running", new Object[0]));
        }
        TestRunner runner = new TestRunner(level, region, TestScript.parse(body, region));
        tests.put(player.getUUID(), new TestRun(packet.dimension(), runner, packet));
    }

    private static void stopRecording(ServerPlayer player, BridgePacket packet) {
        Recording recording = recordings.remove(player.getUUID());
        if (recording == null) {
            throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.not_recording", new Object[0]));
        }
        String result = recording.recorder.bundle().encode();
        packet.chunks(result, 9, p -> ServerPlayNetworking.send((ServerPlayer)player, (CustomPacketPayload)p));
    }

    private static String safeMessage(Exception ex) {
        String s = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return Messages.isEncoded(s) ? s : s.substring(0, Math.min(s.length(), 1000));
    }

    private static void reply(ServerPlayer p, BridgePacket request, String text) {
        request.chunks(text, 3, msg -> ServerPlayNetworking.send((ServerPlayer)p, (CustomPacketPayload)msg));
    }

    private static void validatePosition(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.world_bounds", new Object[0]));
        }
        if (!level.hasChunkAt(pos)) {
            throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.unloaded", new Object[0]));
        }
    }

    static void validateRegion(ServerLevel level, Region r) {
        BridgeServer.validatePosition(level, new BlockPos(r.x(), r.y(), r.z()));
        BridgeServer.validatePosition(level, new BlockPos(r.maxX(), r.maxY(), r.maxZ()));
        for (int cx = r.x() >> 4; cx <= r.maxX() >> 4; ++cx) {
            for (int cz = r.z() >> 4; cz <= r.maxZ() >> 4; ++cz) {
                if (level.hasChunkAt(new BlockPos(Math.max(r.x(), cx << 4), r.y(), Math.max(r.z(), cz << 4)))) continue;
                throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.unloaded", new Object[0]));
            }
        }
    }

    static Cell snapshot(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return new Cell(pos.immutable(), level.getBlockState(pos), be == null ? null : be.saveWithFullMetadata((HolderLookup.Provider)level.registryAccess()));
    }

    static String exportRegion(ServerLevel level, Region r) {
        return BridgeServer.exportRegion(level, r, false);
    }

    static String exportRegion(ServerLevel level, Region r, boolean includeEntities) {
        StringBuilder text = new StringBuilder("# AI Block Bridge Script v1\n# size: " + r.sizeX() + " " + r.sizeY() + " " + r.sizeZ() + "\n# origin: " + r.x() + " " + r.y() + " " + r.z() + "\n# Air is omitted. Unlisted coordinates are unchanged on paste.\n");
        for (BlockPos blockPos : BlockPos.betweenClosed((int)r.x(), (int)r.y(), (int)r.z(), (int)r.maxX(), (int)r.maxY(), (int)r.maxZ())) {
            if (level.getBlockState(blockPos).isAir()) continue;
            Cell cell = BridgeServer.snapshot(level, blockPos);
            if (cell.state.isAir()) continue;
            text.append(blockPos.getX() - r.x()).append(' ').append(blockPos.getY() - r.y()).append(' ').append(blockPos.getZ() - r.z()).append(" | ").append(BlockStateParser.serialize((BlockState)cell.state));
            if (cell.nbt != null) {
                CompoundTag tag = cell.nbt.copy();
                tag.remove("x");
                tag.remove("y");
                tag.remove("z");
                text.append(" | ").append(tag);
            }
            text.append('\n');
            if (text.length() <= 2000000) continue;
            throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.export_large", new Object[0]));
        }
        if (includeEntities) {
            text.append("# include entities: true\n").append(EntitySnapshot.header());
            if (text.length() > 2000000) {
                throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.export_large", new Object[0]));
            }
            for (Map.Entry entry : EntitySnapshot.capture(level, r, 2000000).entrySet()) {
                String line = ((EntitySnapshot.State)entry.getValue()).line("initial", (UUID)entry.getKey());
                if ((long)text.length() + (long)line.length() > 2000000L) {
                    throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.export_large", new Object[0]));
                }
                text.append(line);
            }
        }
        return text.toString();
    }

    static List<Cell> prepare(ServerLevel level, Region r, String body) throws Exception {
        ArrayList<Cell> cells = new ArrayList<Cell>();
        for (Script.Entry e : Script.parse(body, r)) {
            try {
                StringReader reader = new StringReader(e.state());
                BlockState state = BlockStateParser.parseForBlock((HolderLookup)level.registryAccess().lookupOrThrow(Registries.BLOCK), (StringReader)reader, (boolean)false).blockState();
                reader.skipWhitespace();
                if (reader.canRead()) {
                    throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.trailing_state", new Object[0]));
                }
                BlockPos pos = new BlockPos(r.x() + e.x(), r.y() + e.y(), r.z() + e.z());
                CompoundTag tag = null;
                if (!e.nbt().isEmpty()) {
                    Block block = state.getBlock();
                    if (!(block instanceof EntityBlock)) {
                        throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.unsupported_nbt", new Object[0]));
                    }
                    EntityBlock eb = (EntityBlock)block;
                    BlockEntity prototype = eb.newBlockEntity(pos, state);
                    if (prototype == null) {
                        throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.block_entity_create", new Object[0]));
                    }
                    tag = TagParser.parseCompoundFully((String)e.nbt());
                    CompoundTag metadata = prototype.saveWithFullMetadata((HolderLookup.Provider)level.registryAccess());
                    tag.put("id", metadata.get("id"));
                    tag.putInt("x", pos.getX());
                    tag.putInt("y", pos.getY());
                    tag.putInt("z", pos.getZ());
                    if (BlockEntity.loadStatic((BlockPos)pos, (BlockState)state, (CompoundTag)tag, (HolderLookup.Provider)level.registryAccess()) == null) {
                        throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.nbt_load", new Object[0]));
                    }
                }
                cells.add(new Cell(pos, state, tag));
            }
            catch (Exception ex) {
                throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.line", e.line(), BridgeServer.safeMessage(ex)));
            }
        }
        return cells;
    }

    record PastePlan(List<Cell> target, List<Cell> before, int listed) {}

    /** Complete validation and recovery data before the first world write. */
    static PastePlan planPaste(ServerLevel level, Region r, String body) throws Exception {
        List<Cell> target = new ArrayList<>(prepare(level, r, body));
        int listed = target.size();
        if (Script.unlisted(body) == Script.Unlisted.CLEAR) {
            var occupied = new java.util.HashSet<BlockPos>();
            for (Cell c : target) occupied.add(c.pos);
            for (BlockPos pos : BlockPos.betweenClosed(r.x(), r.y(), r.z(), r.maxX(), r.maxY(), r.maxZ())) {
                if (!occupied.contains(pos) && !level.getBlockState(pos).isAir())
                    target.add(new Cell(pos.immutable(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), null));
            }
        }
        // Includes original air at listed positions, so undo removes newly placed blocks too.
        List<Cell> before = new ArrayList<>();
        long snapshotChars = 0;
        for (Cell c : target) {
            Cell old = snapshot(level, c.pos);
            snapshotChars += old.nbt == null ? 0 : old.nbt.toString().length();
            if (snapshotChars > 2_000_000L)
                throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.undo_large"));
            before.add(old);
        }
        checkSnapshotSize(target);
        return new PastePlan(target, before, listed);
    }

    private static void paste(ServerPlayer p, BridgePacket packet, ServerLevel level, Region r, String body) throws Exception {
        List<Cell> after;
        PastePlan plan = planPaste(level, r, body);
        List<Cell> target = plan.target;
        List<Cell> before = plan.before;
        try {
            BridgeServer.apply(level, target);
            after = target.stream().map(c -> BridgeServer.snapshot(level, c.pos)).toList();
            BridgeServer.checkSnapshotSize(after);
        }
        catch (Exception ex) {
            try {
                BridgeServer.apply(level, before);
            }
            catch (Exception rollback) {
                undos.put(p.getUUID(), new Undo(packet.dimension(), before, null));
                LoggerFactory.getLogger((String)"ai_block_bridge").error("Rollback failed; retained recovery snapshot", (Throwable)rollback);
                throw new IllegalStateException(Messages.text("ai_block_bridge.error.rollback", new Object[0]), ex);
            }
            throw new IllegalStateException(Messages.text("ai_block_bridge.error.paste_restored", BridgeServer.safeMessage(ex)));
        }
        undos.put(p.getUUID(), new Undo(packet.dimension(), before, after));
        BridgeServer.reply(p, packet, Messages.text("ai_block_bridge.pasted", target.size()));
    }

    private static void checkSnapshotSize(List<Cell> cells) {
        long count = 0L;
        for (Cell c : cells) {
            if ((count += c.nbt == null ? 0L : (long)c.nbt.toString().length()) <= 2000000L) continue;
            throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.undo_large", new Object[0]));
        }
    }

    static void apply(ServerLevel level, List<Cell> cells) {
        int flags = 818;
        for (Cell c : cells) {
            level.removeBlockEntity(c.pos);
            level.setBlock(c.pos, c.state, flags);
            if (!level.getBlockState(c.pos).equals((Object)c.state)) {
                throw new IllegalStateException(Messages.text("ai_block_bridge.error.place", c.pos));
            }
            Block block = c.state.getBlock();
            if (block instanceof EntityBlock) {
                BlockEntity be;
                EntityBlock eb = (EntityBlock)block;
                BlockEntity blockEntity = be = c.nbt == null ? eb.newBlockEntity(c.pos, c.state) : BlockEntity.loadStatic((BlockPos)c.pos, (BlockState)c.state, (CompoundTag)c.nbt.copy(), (HolderLookup.Provider)level.registryAccess());
                if (be == null) {
                    throw new IllegalStateException(Messages.text("ai_block_bridge.error.block_entity_restore", c.pos));
                }
                level.setBlockEntity(be);
                level.blockEntityChanged(c.pos);
            }
            level.sendBlockUpdated(c.pos, c.state, c.state, 2);
        }
    }

    private static void undo(ServerPlayer player, BridgePacket packet, ServerLevel level) {
        Undo undo = undos.get(player.getUUID());
        if (undo == null) {
            throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.no_undo", new Object[0]));
        }
        if (!undo.dimension.equals(packet.dimension())) {
            throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.undo_dimension", new Object[0]));
        }
        for (Cell c : undo.before) {
            BridgeServer.validatePosition(level, c.pos);
        }
        if (undo.after != null) {
            for (Cell expected : undo.after) {
                if (BridgeServer.snapshot(level, expected.pos).equals(expected)) continue;
                throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.undo_changed", new Object[0]));
            }
        }
        try {
            BridgeServer.apply(level, undo.before);
        }
        catch (Exception ex) {
            undos.put(player.getUUID(), new Undo(undo.dimension, undo.before, null));
            throw new IllegalStateException(Messages.text("ai_block_bridge.error.undo_retry", BridgeServer.safeMessage(ex)));
        }
        undos.remove(player.getUUID());
        BridgeServer.reply(player, packet, Messages.text("ai_block_bridge.undone", undo.before.size()));
    }

    private record Recording(String dimension, TickRecorder recorder, BridgePacket request) {
    }

    private record TestRun(String dimension, TestRunner runner, BridgePacket request) {
    }

    private static final class Settle {
        final String dimension;
        final Region region;
        final List<Cell> target;
        final BridgePacket request;
        int remaining;

        Settle(String dimension, Region region, List<Cell> target, BridgePacket request, int remaining) {
            this.dimension = dimension;
            this.region = region;
            this.target = target;
            this.request = request;
            this.remaining = remaining;
        }
    }

    record Cell(BlockPos pos, BlockState state, CompoundTag nbt) {
    }

    private record Undo(String dimension, List<Cell> before, List<Cell> after) {
    }
}
