package io.github.ruok0214.bridge;

/** Inclusive, normalized integer cuboid. Independent of Minecraft for testing. */
public record Region(int x, int y, int z, int maxX, int maxY, int maxZ) {
    public static final int MAX_BLOCKS = 1_048_576;
    public static final String MAX_BLOCKS_TEXT = String.format(java.util.Locale.ROOT,"%,d",MAX_BLOCKS);
    public static Region of(int ax, int ay, int az, int bx, int by, int bz) {
        return new Region(Math.min(ax,bx), Math.min(ay,by), Math.min(az,bz), Math.max(ax,bx), Math.max(ay,by), Math.max(az,bz));
    }
    public Region {
        if (x > maxX || y > maxY || z > maxZ) throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.region"));
        long sx = (long)maxX-x+1, sy = (long)maxY-y+1, sz = (long)maxZ-z+1;
        if (sx > MAX_BLOCKS || sy > MAX_BLOCKS || sz > MAX_BLOCKS || sx*sy*sz > MAX_BLOCKS)
            throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.region_limit", MAX_BLOCKS_TEXT));
    }
    public int sizeX() { return maxX-x+1; }
    public int sizeY() { return maxY-y+1; }
    public int sizeZ() { return maxZ-z+1; }
    public int volume() { return sizeX()*sizeY()*sizeZ(); }
    public boolean containsLocal(int lx, int ly, int lz) { return lx>=0 && ly>=0 && lz>=0 && lx<sizeX() && ly<sizeY() && lz<sizeZ(); }
    /** Same size, with local 0 0 0 sitting at the given world position. */
    public Region movedTo(int nx, int ny, int nz) {
        long ex=(long)nx+sizeX()-1, ey=(long)ny+sizeY()-1, ez=(long)nz+sizeZ()-1;
        if (ex>Integer.MAX_VALUE || ey>Integer.MAX_VALUE || ez>Integer.MAX_VALUE)
            throw new IllegalArgumentException(Messages.text("ai_block_bridge.error.region"));
        return new Region(nx, ny, nz, (int)ex, (int)ey, (int)ez);
    }
    public String description() { return Messages.text("ai_block_bridge.region", x, y, z, sizeX(), sizeY(), sizeZ(), volume()); }
}
