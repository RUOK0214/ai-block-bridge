package io.github.ruok0214.bridge.client;

import io.github.ruok0214.bridge.Region;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** CUI-style client-only overlay, using immutable selection data for each frame. */
public final class SelectionOverlay {
    private static final int EDGE=0xFFFFD166, GRID=0x607FDBFF;
    private static final int FIRST=0xFF68CFFF, SECOND=0xFFFFAA55, ORIGIN=0xFF66FF99;
    private static final RenderStateDataKey<Selection> KEY=RenderStateDataKey.create(()->"AI Block Bridge selection");
    private record Selection(BlockPos a,BlockPos b) {}

    public static void register() {
        LevelExtractionEvents.END_EXTRACTION.register(context->{
            boolean visible=BridgeClient.showSelection && BridgeClient.dimension.equals(context.level().dimension().identifier().toString());
            context.levelState().setData(KEY,visible?new Selection(BridgeClient.a,BridgeClient.b):null);
        });
        LevelRenderEvents.BEFORE_GIZMOS.register(context->{
            Selection selection=context.levelState().getData(KEY);
            if(selection!=null) {
                try(var ignored=context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
                    draw(selection);
                }
            }
        });
    }

    private static void draw(Selection s) {
        if(s.a()!=null)marker(s.a(),FIRST,"1",0.22);
        if(s.b()!=null)marker(s.b(),SECOND,"2",0.55);
        if(s.a()==null||s.b()==null)return;
        // Cast before max+1: selected block coordinates are inclusive, box faces are exclusive.
        double x=Math.min(s.a().getX(),s.b().getX()), y=Math.min(s.a().getY(),s.b().getY()), z=Math.min(s.a().getZ(),s.b().getZ());
        double X=(double)Math.max(s.a().getX(),s.b().getX())+1, Y=(double)Math.max(s.a().getY(),s.b().getY())+1, Z=(double)Math.max(s.a().getZ(),s.b().getZ())+1;
        double sx=X-x,sy=Y-y,sz=Z-z;
        boolean valid=sx*sy*sz<=Region.MAX_BLOCKS;
        Gizmos.cuboid(new AABB(x,y,z,X,Y,Z).inflate(0.003),GizmoStyle.stroke(valid?EDGE:0xFFFF5555,2.5f)).setAlwaysOnTop();
        // At most 63 internal planes per axis, even for an oversized selection.
        // A plane consists of four face lines; normal small selections show every block.
        double step=Math.max(1,Math.ceil(Math.max(sx,Math.max(sy,sz))/64));
        for(double n=x+step;n<X;n+=step) {
            line(n,y,z,n,Y,z);line(n,y,Z,n,Y,Z);line(n,y,z,n,y,Z);line(n,Y,z,n,Y,Z);
        }
        for(double n=y+step;n<Y;n+=step) {
            line(x,n,z,X,n,z);line(x,n,Z,X,n,Z);line(x,n,z,x,n,Z);line(X,n,z,X,n,Z);
        }
        for(double n=z+step;n<Z;n+=step) {
            line(x,y,n,X,y,n);line(x,Y,n,X,Y,n);line(x,y,n,x,Y,n);line(X,y,n,X,Y,n);
        }
        // The origin is the independent minimum of all three axes, not necessarily corner 1.
        Gizmos.cuboid(new AABB(x+0.12,y+0.12,z+0.12,x+0.88,y+0.88,z+0.88),GizmoStyle.stroke(ORIGIN,3)).setAlwaysOnTop();
        text("0,0,0",new Vec3(x+0.5,y+0.5,z+0.5),ORIGIN);
        String size="%d x %d x %d".formatted((long)sx,(long)sy,(long)sz);
        text(size+(valid?"":" / >4096"),new Vec3((x+X)/2,Y+0.85,(z+Z)/2),valid?EDGE:0xFFFF5555);
    }

    private static void marker(BlockPos p,int color,String label,double labelOffset) {
        Gizmos.cuboid(new AABB(p).inflate(0.012),GizmoStyle.stroke(color,3)).setAlwaysOnTop();
        text(label+": "+p.toShortString(),new Vec3(p.getX()+0.5,p.getY()+1+labelOffset,p.getZ()+0.5),color);
    }
    private static void line(double x,double y,double z,double X,double Y,double Z) {
        Gizmos.line(new Vec3(x,y,z),new Vec3(X,Y,Z),GRID,1).setAlwaysOnTop();
    }
    private static void text(String value,Vec3 at,int color) {
        Gizmos.billboardText(value,at,TextGizmo.Style.forColorAndCentered(color)).setAlwaysOnTop();
    }
    private SelectionOverlay() {}
}
