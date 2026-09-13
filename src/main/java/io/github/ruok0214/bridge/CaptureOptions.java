package io.github.ruok0214.bridge;

import java.util.Set;
import java.util.HashSet;

/** Text options preserve the existing packet layout and the legacy cooldown flag. */
public record CaptureOptions(boolean structureEntities, boolean timelineEntities, boolean ignoreHopperCooldown) {
    public static CaptureOptions parse(String body) {
        Set<String> flags=new HashSet<>();
        for(String token:body.strip().split("\\s+")) {
            if(token.isEmpty())continue;
            if(!Set.of("structure-entities","timeline-entities","include-cooldown").contains(token))
                throw new IllegalArgumentException("Unknown capture option: "+token);
            flags.add(token);
        }
        return new CaptureOptions(flags.contains("structure-entities"),flags.contains("timeline-entities"),!flags.contains("include-cooldown"));
    }
    public String encode() {
        return (structureEntities?"structure-entities\n":"")+(timelineEntities?"timeline-entities\n":"")
            +(ignoreHopperCooldown?"":"include-cooldown\n");
    }
}
