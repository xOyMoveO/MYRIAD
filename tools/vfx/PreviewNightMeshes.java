import com.google.gson.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Offline projection of actual tested quads+UVs; not an in-game screenshot or FPS test. */
public class PreviewNightMeshes {
    static double[] point(JsonObject v){return new double[]{v.get("x").getAsDouble(),v.get("y").getAsDouble(),v.get("z").getAsDouble()};}
    static double[] project(double[] p){return new double[]{(.832*p[0]-.555*p[2])*96,(-.308*p[0]+.832*p[1]-.462*p[2])*-96};}
    static double depth(JsonObject q){double d=0;for(String k:new String[]{"a","b","c","d"}){double[] p=point(q.getAsJsonObject(k));d+=.462*p[0]+.555*p[1]+.693*p[2];}return d/4;}
    static void triangle(Graphics2D g,BufferedImage image,double[][] dest,double[][] src){
        double sx=src[1][0]-src[0][0],sy=src[1][1]-src[0][1],tx=src[2][0]-src[0][0],ty=src[2][1]-src[0][1];
        double determinant=sx*ty-tx*sy;if(Math.abs(determinant)<1E-8)return;
        double dx=dest[1][0]-dest[0][0],dy=dest[1][1]-dest[0][1],ex=dest[2][0]-dest[0][0],ey=dest[2][1]-dest[0][1];
        double a=(dx*ty-ex*sy)/determinant,b=(dy*ty-ey*sy)/determinant,c=(ex*sx-dx*tx)/determinant,d=(ey*sx-dy*tx)/determinant;
        var transform=new AffineTransform(a,b,c,d,dest[0][0]-a*src[0][0]-c*src[0][1],dest[0][1]-b*src[0][0]-d*src[0][1]);
        var path=new Path2D.Double();path.moveTo(dest[0][0],dest[0][1]);path.lineTo(dest[1][0],dest[1][1]);path.lineTo(dest[2][0],dest[2][1]);path.closePath();
        var child=(Graphics2D)g.create();child.clip(path);child.drawImage(image,transform,null);child.dispose();
    }
    static BufferedImage tint(BufferedImage source,int rgb){
        var image=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<256;y++)for(int x=0;x<256;x++){
            int pixel=source.getRGB(x,y);int r=((pixel>>16)&255)*((rgb>>16)&255)/255,g=((pixel>>8)&255)*((rgb>>8)&255)/255,b=(pixel&255)*(rgb&255)/255;
            image.setRGB(x,y,(pixel&0xFF000000)|(r<<16)|(g<<8)|b);
        }return image;
    }
    public static void main(String[] args)throws Exception{
        var shots=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();
        String[] files={"","night_blade","night_trail","night_smoke","night_slash","night_impact","night_seal"};
        BufferedImage[] textures=new BufferedImage[7];textures[0]=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);
        var white=textures[0].createGraphics();white.setColor(Color.WHITE);white.fillRect(0,0,256,256);white.dispose();
        for(int i=1;i<7;i++)textures[i]=ImageIO.read(Path.of(args[1],files[i]+".png").toFile());
        var cache=new HashMap<String,BufferedImage>();int w=380,h=260;
        var board=new BufferedImage(4*w,3*h+40,BufferedImage.TYPE_INT_RGB);var g=board.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(new Color(15,20,30));g.fillRect(0,0,board.getWidth(),board.getHeight());g.setColor(Color.WHITE);g.drawString("Actual mesh + texture offline projection / NOT an in-game capture",16,24);
        String[] labels={"Shadow dagger - release","Shadow dagger - flight","Shadow dagger - decay","Throw cut","Hit fractures","Vanish veil","Return anchor","Execution mark","Knife shield","Execution windup","Assault afterimage","Return streak"};
        for(int index=0;index<shots.size();index++){
            int x=index%4*w,y=index/4*h+40;g.setColor(index%2==0?new Color(62,88,44):new Color(53,62,76));g.fillRect(x+1,y+1,w-2,h-2);
            g.setColor(new Color(255,255,255,35));for(int grid=-3;grid<=3;grid++){
                double[] a=project(new double[]{grid,-.2,-3}),b=project(new double[]{grid,-.2,3});g.drawLine(x+w/2+(int)a[0],y+175+(int)a[1],x+w/2+(int)b[0],y+175+(int)b[1]);
            }
            JsonObject shot=shots.get(index).getAsJsonObject();var sorted=new ArrayList<JsonObject>();shot.getAsJsonArray("mesh").forEach(q->sorted.add(q.getAsJsonObject()));
            sorted.sort(Comparator.comparingDouble(PreviewNightMeshes::depth));
            var cell=(Graphics2D)g.create();cell.clipRect(x+1,y+26,w-2,h-28);
            for(var q:sorted){
                var verts=new double[4][];String[] keys={"a","b","c","d"};for(int i=0;i<4;i++){verts[i]=project(point(q.getAsJsonObject(keys[i])));verts[i][0]+=x+155;verts[i][1]+=y+170;}
                int color=q.get("color").getAsInt(),mat=q.get("material").getAsInt();float alpha=q.get("alpha").getAsFloat();
                cell.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,alpha));
                if(mat==0){var poly=new Path2D.Double();poly.moveTo(verts[0][0],verts[0][1]);for(int i=1;i<4;i++)poly.lineTo(verts[i][0],verts[i][1]);poly.closePath();cell.setColor(new Color(color));cell.fill(poly);continue;}
                BufferedImage texture=cache.computeIfAbsent(mat+":"+color,key->tint(textures[mat],color));
                boolean reverse=q.get("reverseV").getAsBoolean();double v0=reverse?256:0,v1=reverse?0:256;
                double u0=q.get("startU").getAsDouble()*256,u1=q.get("endU").getAsDouble()*256;
                double[][] uv={{u0,v1},{u1,v1},{u1,v0},{u0,v0}};
                triangle(cell,texture,new double[][]{verts[0],verts[1],verts[2]},new double[][]{uv[0],uv[1],uv[2]});
                triangle(cell,texture,new double[][]{verts[0],verts[2],verts[3]},new double[][]{uv[0],uv[2],uv[3]});
            }
            cell.dispose();g.setColor(Color.WHITE);g.drawString(labels[index]+" | t="+shot.get("age"),x+10,y+18);
        }
        g.dispose();ImageIO.write(board,"png",Path.of(args[2]).toFile());
    }
}
