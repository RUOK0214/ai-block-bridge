/*
 * Decompiled with CFR 0.152.
 */
package io.github.ruok0214.bridge;

import java.util.HashSet;
import java.util.Set;

public record CaptureOptions(boolean structureEntities, boolean timelineEntities, boolean ignoreHopperCooldown, boolean ignoreEntityAgeMotion, boolean entityDelta, boolean shortEntityIds, boolean shortBlockStates, boolean blockNbtDelta, boolean paletteFormat) {
    public CaptureOptions(boolean s, boolean t, boolean h, boolean n, boolean d, boolean i) {
        this(s, t, h, n, d, i, false, false, false);
    }

    public CaptureOptions(boolean s, boolean t, boolean h, boolean n) {
        this(s, t, h, n, false, false, false, false, false);
    }

    public CaptureOptions(boolean structureEntities, boolean timelineEntities, boolean ignoreHopperCooldown) {
        this(structureEntities, timelineEntities, ignoreHopperCooldown, false, false, false, false, false, false);
    }

    public static CaptureOptions parse(String body) {
        HashSet<String> flags = new HashSet<String>();
        for (String token : body.strip().split("\\s+")) {
            if (token.isEmpty()) continue;
            if (!Set.of("structure-entities", "timeline-entities", "include-cooldown", "ignore-entity-age-motion", "entity-delta", "short-entity-ids", "short-block-states", "block-nbt-delta", "palette-format").contains(token)) {
                throw new IllegalArgumentException("Unknown capture option: " + token);
            }
            flags.add(token);
        }
        return new CaptureOptions(flags.contains("structure-entities"), flags.contains("timeline-entities"), !flags.contains("include-cooldown"), flags.contains("ignore-entity-age-motion"), flags.contains("entity-delta"), flags.contains("short-entity-ids"), flags.contains("short-block-states"), flags.contains("block-nbt-delta"), flags.contains("palette-format"));
    }

    public String encode() {
        return (this.structureEntities ? "structure-entities\n" : "") + (this.timelineEntities ? "timeline-entities\n" : "") + (this.ignoreHopperCooldown ? "" : "include-cooldown\n") + (this.ignoreEntityAgeMotion ? "ignore-entity-age-motion\n" : "") + (this.entityDelta ? "entity-delta\n" : "") + (this.shortEntityIds ? "short-entity-ids\n" : "") + (this.shortBlockStates ? "short-block-states\n" : "") + (this.blockNbtDelta ? "block-nbt-delta\n" : "") + (this.paletteFormat ? "palette-format\n" : "");
    }
}

