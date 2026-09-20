/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.InputConstants$Type
 *  net.fabricmc.api.ClientModInitializer
 *  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
 *  net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
 *  net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
 *  net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
 *  net.minecraft.client.KeyMapping
 *  net.minecraft.client.KeyMapping$Category
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.core.BlockPos
 *  net.minecraft.resources.Identifier
 *  net.minecraft.world.phys.BlockHitResult
 *  net.minecraft.world.phys.HitResult
 *  net.minecraft.world.phys.HitResult$Type
 */
package io.github.ruok0214.bridge.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.ruok0214.bridge.Assembly;
import io.github.ruok0214.bridge.BridgePacket;
import io.github.ruok0214.bridge.CaptureOptions;
import io.github.ruok0214.bridge.Messages;
import io.github.ruok0214.bridge.RecordingBundle;
import io.github.ruok0214.bridge.Region;
import io.github.ruok0214.bridge.TextHistory;
import io.github.ruok0214.bridge.client.BridgeScreen;
import io.github.ruok0214.bridge.client.SelectionOverlay;
import io.github.ruok0214.bridge.client.TimelineScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class BridgeClient
implements ClientModInitializer {
    public static BlockPos a;
    public static BlockPos b;
    public static String dimension;
    public static String script;
    public static String status;
    public static String exportUndo;
    public static String timeline;
    public static String timelineStatus;
    public static RecordingBundle recordingBundle;
    public static boolean recording;
    public static boolean recordingAvailable;
    private static String recordingStopReason;
    public static boolean ignoreHopperCooldown;
    public static boolean includeStructureEntities;
    public static boolean includeTimelineEntities;
    public static boolean ignoreEntityAgeMotion;
    public static boolean entityDelta;
    public static boolean shortEntityIds;
    public static boolean shortBlockStates;
    public static boolean blockNbtDelta;
    public static boolean paletteFormat;
    public static boolean showSelection;
    public static final TextHistory history;
    public static final TextHistory timelineHistory;
    private static Assembly response;
    private static int requestId;
    private static int pending;
    private static int pendingAction;
    private static long sentAt;
    private static String exportBefore;

    public static boolean busy() {
        return pending != -1;
    }

    private static KeyMapping key(String name, int code, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping((KeyMapping)new KeyMapping("key.ai_block_bridge." + name, InputConstants.Type.KEYSYM, code, category));
    }

    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register((Identifier)Identifier.fromNamespaceAndPath((String)"ai_block_bridge", (String)"controls"));
        KeyMapping open = BridgeClient.key("open", 66, category);
        KeyMapping first = BridgeClient.key("first", 91, category);
        KeyMapping second = BridgeClient.key("second", 93, category);
        KeyMapping overlay = BridgeClient.key("overlay", 92, category);
        KeyMapping record = BridgeClient.key("record", 78, category);
        SelectionOverlay.register();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.level == null) {
                return;
            }
            String current = mc.level.dimension().identifier().toString();
            if (!dimension.equals(current)) {
                a = null;
                b = null;
                dimension = current;
                pending = -1;
                pendingAction = -1;
                response = null;
                recording = false;
                recordingAvailable = false;
                recordingStopReason = null;
            }
            if (BridgeClient.busy() && System.nanoTime() - sentAt > 30000000000L) {
                pending = -1;
                pendingAction = -1;
                response = null;
                status = timelineStatus = Messages.text("ai_block_bridge.error.timeout", new Object[0]);
            }
            if (mc.gui.screen() == null) {
                while (first.consumeClick()) {
                    BridgeClient.select(mc, true);
                }
                while (second.consumeClick()) {
                    BridgeClient.select(mc, false);
                }
                while (overlay.consumeClick()) {
                    boolean bl = showSelection = !showSelection;
                    if (mc.player == null) continue;
                    mc.player.sendSystemMessage(Messages.component(Messages.text("ai_block_bridge.overlay", Messages.text(showSelection ? "ai_block_bridge.on" : "ai_block_bridge.off", new Object[0]))));
                }
                while (record.consumeClick()) {
                    BridgeClient.send(recording || recordingAvailable ? 6 : 5);
                }
                while (open.consumeClick()) {
                    mc.gui.setScreen((Screen)new BridgeScreen());
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            a = null;
            b = null;
            dimension = "";
            pending = -1;
            pendingAction = -1;
            response = null;
            recording = false;
            recordingAvailable = false;
            recordingStopReason = null;
        });
        ClientPlayNetworking.registerGlobalReceiver(BridgePacket.TYPE, (packet, ctx) -> {
            if (packet.action() == 8) {
                if (packet.index() == 0 && packet.total() == 1) {
                    BridgeClient.recordingStopped(packet.text());
                    if (ctx.client().player != null) {
                        ctx.client().player.sendSystemMessage(Messages.component(timelineStatus));
                    }
                    if (ctx.client().player != null) {
                        ctx.client().player.sendOverlayMessage(Messages.component(Messages.text("ai_block_bridge.recording.banner", new Object[0])));
                    }
                }
                return;
            }
            if (packet.request() != pending) {
                return;
            }
            try {
                Screen patt1$temp;
                Screen screen;
                if (packet.index() == 0) {
                    response = new Assembly((BridgePacket)packet);
                }
                if (response == null) {
                    return;
                }
                String body = response.append((BridgePacket)packet);
                if (body == null) {
                    return;
                }
                if (packet.action() == 4) {
                    exportUndo = exportBefore;
                    BridgeClient.replace(body);
                    status = Messages.text("ai_block_bridge.captured", new Object[0]);
                } else if (packet.action() == 7 || packet.action() == 9) {
                    RecordingBundle received = packet.action() == 9 ? RecordingBundle.decode(body) : null;
                    BridgeClient.replaceTimeline(received == null ? body : received.timeline());
                    recordingBundle = received;
                    recording = false;
                    recordingAvailable = false;
                    timelineStatus = recordingStopReason == null ? Messages.text("ai_block_bridge.timeline.complete", new Object[0]) : Messages.text("ai_block_bridge.recording.retrieved", recordingStopReason);
                    status = timelineStatus;
                } else {
                    status = timelineStatus = body;
                    if (pendingAction == 5 && !Messages.isError(body) && !recordingAvailable) {
                        recording = true;
                    }
                    if (pendingAction == 6 && Messages.isError(body)) {
                        recording = false;
                    }
                }
                pending = -1;
                pendingAction = -1;
                response = null;
                Screen patt0$temp = ctx.client().gui.screen();
                if (patt0$temp instanceof BridgeScreen) {
                    screen = (BridgeScreen)patt0$temp;
                    screen.syncText();
                }
                if ((patt1$temp = ctx.client().gui.screen()) instanceof TimelineScreen) {
                    screen = (TimelineScreen)patt1$temp;
                    screen.syncText();
                } else if ((packet.action() == 7 || packet.action() == 9) && ctx.client().gui.screen() == null) {
                    ctx.client().gui.setScreen((Screen)new TimelineScreen());
                }
            }
            catch (Exception ex) {
                pending = -1;
                pendingAction = -1;
                response = null;
                status = timelineStatus = ex.getMessage();
            }
        });
    }

    public static void recordingStopped(String reason) {
        recording = false;
        recordingAvailable = true;
        recordingStopReason = reason;
        status = timelineStatus = Messages.text("ai_block_bridge.recording.available", reason);
    }

    private static void select(Minecraft mc, boolean first) {
        BlockHitResult hit;
        if (BridgeClient.busy()) {
            return;
        }
        HitResult hitResult = mc.hitResult;
        if (hitResult instanceof BlockHitResult && (hit = (BlockHitResult)hitResult).getType() == HitResult.Type.BLOCK) {
            if (first) {
                a = hit.getBlockPos().immutable();
            } else {
                b = hit.getBlockPos().immutable();
            }
            status = Messages.text("ai_block_bridge.corner_selected", first ? 1 : 2, hit.getBlockPos().toShortString());
        } else {
            status = Messages.text("ai_block_bridge.error.target", new Object[0]);
        }
        if (mc.player != null) {
            mc.player.sendSystemMessage(Messages.component(status));
        }
    }

    public static Region region() {
        if (a == null || b == null) {
            throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.corners", new Object[0]));
        }
        return Region.of(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
    }

    public static void replace(String value) {
        if (!value.equals(script)) {
            history.remember(script);
            script = value;
        }
    }

    public static void replaceTimeline(String value) {
        if (!value.equals(timeline)) {
            timelineHistory.remember(timeline);
            timeline = value;
        }
    }

    public static void send(int action) {
        if (BridgeClient.busy()) {
            return;
        }
        try {
            Region r;
            if (!ClientPlayNetworking.canSend(BridgePacket.TYPE)) {
                throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.server_mod", new Object[0]));
            }
            Region region = r = action == 2 || action == 6 ? Region.of(0, 0, 0, 0, 0, 0) : BridgeClient.region();
            if (action == 5) {
                if (recordingAvailable) {
                    throw new IllegalArgumentException(Messages.text("ai_block_bridge.recording.retrieve_first", new Object[0]));
                }
                recordingStopReason = null;
            }
            pending = ++requestId;
            pendingAction = action;
            sentAt = System.nanoTime();
            response = null;
            exportBefore = script;
            BridgePacket packet = new BridgePacket(pending, action, 0, 1, dimension, r.x(), r.y(), r.z(), r.maxX(), r.maxY(), r.maxZ(), "");
            String body = action == 1 ? script : (action == 0 ? new CaptureOptions(includeStructureEntities, false, true, false, false, false, false, false, paletteFormat).encode() : (action == 5 ? new CaptureOptions(includeStructureEntities, includeTimelineEntities, ignoreHopperCooldown, ignoreEntityAgeMotion, entityDelta, shortEntityIds, shortBlockStates, blockNbtDelta, paletteFormat).encode() : ""));
            packet.chunks(body, action, ClientPlayNetworking::send);
            status = Messages.text("ai_block_bridge.processing", new Object[0]);
        }
        catch (Exception ex) {
            pending = -1;
            pendingAction = -1;
            status = timelineStatus = ex.getMessage();
        }
    }

    static {
        dimension = "";
        script = "# AI Block Bridge Script v1\n# x y z | block[state] | {NBT}\n0 0 0 | minecraft:stone\n";
        status = Messages.text("ai_block_bridge.select_hint", new Object[0]);
        timeline = "# AI Block Bridge Timeline v1\n# Changes after recording starts are listed in @tick order.\n";
        timelineStatus = Messages.text("ai_block_bridge.timeline.ready", new Object[0]);
        ignoreHopperCooldown = true;
        includeStructureEntities = false;
        includeTimelineEntities = false;
        ignoreEntityAgeMotion = true;
        entityDelta = true;
        shortEntityIds = true;
        shortBlockStates = true;
        blockNbtDelta = true;
        paletteFormat = false;
        showSelection = true;
        history = new TextHistory();
        timelineHistory = new TextHistory();
        pending = -1;
        pendingAction = -1;
    }
}

