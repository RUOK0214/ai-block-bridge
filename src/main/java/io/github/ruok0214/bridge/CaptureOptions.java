package io.github.ruok0214.bridge;

import java.util.Set;
import java.util.HashSet;

/** Text options preserve the existing packet layout and the legacy cooldown flag. */
public record CaptureOptions(boolean structureEntities, boolean timelineEntities, boolean ignoreHopperCooldown, boolean ignoreEntityAgeMotion, boolean entityDelta, boolean shortEntityIds) {
    public CaptureOptions(boolean s,boolean t,boolean h,boolean n){this(s,t,h,n,false,false);}
    public CaptureOptions(boolean structureEntities,boolean timelineEntities,boolean ignoreHopperCooldown){
        this(structureEntities,timelineEntities,ignoreHopperCooldown,false);
    }
    public static CaptureOptions parse(String body) {
        Set<String> flags=new HashSet<>();
        for(String token:body.strip().split("\\s+")) {
            if(token.isEmpty())continue;
            if(!Set.of("structure-entities","timeline-entities","include-cooldown","ignore-entity-age-motion","entity-delta","short-entity-ids").contains(token))
                throw new IllegalArgumentException("Unknown capture option: "+token);
            flags.add(token);
        }
        return new CaptureOptions(flags.contains("structure-entities"),flags.contains("timeline-entities"),!flags.contains("include-cooldown"),flags.contains("ignore-entity-age-motion"),flags.contains("entity-delta"),flags.contains("short-entity-ids"));
    }
    public String encode() {
        return (structureEntities?"structure-entities\n":"")+(timelineEntities?"timeline-entities\n":"")
            +(ignoreHopperCooldown?"":"include-cooldown\n")+(ignoreEntityAgeMotion?"ignore-entity-age-motion\n":"")+(entityDelta?"entity-delta\n":"")+(shortEntityIds?"short-entity-ids\n":"");
    }
}
