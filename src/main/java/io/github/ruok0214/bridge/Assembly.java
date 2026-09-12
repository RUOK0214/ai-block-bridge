package io.github.ruok0214.bridge;

/** One ordered transfer per player; no client-declared large allocations. */
public final class Assembly {
    public final BridgePacket first;
    private final StringBuilder text=new StringBuilder();
    private int next;
    private final long started=System.nanoTime();
    public Assembly(BridgePacket first) { this.first=first; }
    public boolean expired() { return System.nanoTime()-started>30_000_000_000L; }
    public String append(BridgePacket p) {
        if(expired() || p.total()<1 || p.total()>BridgePacket.MAX_CHUNKS || p.index()!=next || p.total()!=first.total()
            || p.request()!=first.request() || p.action()!=first.action() || !p.dimension().equals(first.dimension())
            || !p.region().equals(first.region())) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.transfer"));
        text.append(p.text()); next++;
        if(text.length()>BridgePacket.textLimit(first.action())) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.assembly_limit"));
        return next==p.total()?text.toString():null;
    }
}
