package io.github.ruok0214.bridge;

/** Inclusive, normalized integer cuboid. Independent of Minecraft for testing. */
public record Region(int x, int y, int z, int maxX, int maxY, int maxZ) {
    public static final int MAX_BLOCKS = 4096;
    public static Region of(int ax, int ay, int az, int bx, int by, int bz) {
        return new Region(Math.min(ax,bx), Math.min(ay,by), Math.min(az,bz), Math.max(ax,bx), Math.max(ay,by), Math.max(az,bz));
    }
    public Region {
        if (x > maxX || y > maxY || z > maxZ) throw new IllegalArgumentException("잘못된 영역입니다.");
        long sx = (long)maxX-x+1, sy = (long)maxY-y+1, sz = (long)maxZ-z+1;
        if (sx > MAX_BLOCKS || sy > MAX_BLOCKS || sz > MAX_BLOCKS || sx*sy*sz > MAX_BLOCKS)
            throw new IllegalArgumentException("영역은 최대 4,096블록입니다.");
    }
    public int sizeX() { return maxX-x+1; }
    public int sizeY() { return maxY-y+1; }
    public int sizeZ() { return maxZ-z+1; }
    public int volume() { return sizeX()*sizeY()*sizeZ(); }
    public boolean containsLocal(int lx, int ly, int lz) { return lx>=0 && ly>=0 && lz>=0 && lx<sizeX() && ly<sizeY() && lz<sizeZ(); }
    public String description() { return "원점: " + x+", "+y+", "+z+" / 크기: "+sizeX()+" × "+sizeY()+" × "+sizeZ()+" ("+volume()+"블록)"; }
}
