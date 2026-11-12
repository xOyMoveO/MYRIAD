import com.google.gson.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Auto-fit offline material/geometry projection; deliberately labelled NOT an in-game capture. */
public class PreviewElementMeshes {
    static BufferedImage[] textures;
    static final HashMap<String,BufferedImage> TINT=new HashMap<>();
    static BufferedImage cell(JsonObject shot,boolean light){
        int w=360,h=300;var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(light?new Color(103,119,99):new Color(43,56,57));g.fillRect(0,0,w,h);
        var list=new ArrayList<JsonObject>();shot.getAsJsonArray("mesh").forEach(q->list.add(q.getAsJsonObject()));
        list.sort(Comparator.comparingDouble(PreviewNightMeshes::depth));
        double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minY=minX,maxY=maxX;
        for(var q:list)for(String key:new String[]{"a","b","c","d"}){
            double[] point=PreviewNightMeshes.project(PreviewNightMeshes.point(q.getAsJsonObject(key)));
            minX=Math.min(minX,point[0]);maxX=Math.max(maxX,point[0]);minY=Math.min(minY,point[1]);maxY=Math.max(maxY,point[1]);
        }
        double scale=Math.min(1.2,Math.min((w-45)/Math.max(1,maxX-minX),(h-65)/Math.max(1,maxY-minY)));
        double ox=w/2.0-(minX+maxX)*.5*scale,oy=h/2.0+10-(minY+maxY)*.5*scale;
        g.setColor(new Color(255,255,255,30));for(int k=-8;k<=8;k++){
            double[] a=PreviewNightMeshes.project(new double[]{k,-.2,-8}),b=PreviewNightMeshes.project(new double[]{k,-.2,8});
            g.drawLine((int)(ox+a[0]*scale),(int)(oy+a[1]*scale),(int)(ox+b[0]*scale),(int)(oy+b[1]*scale));
        }
        g.clipRect(1,27,w-2,h-30);
        for(var q:list){
            var vertices=new double[4][];String[] keys={"a","b","c","d"};
            for(int i=0;i<4;i++){vertices[i]=PreviewNightMeshes.project(PreviewNightMeshes.point(q.getAsJsonObject(keys[i])));vertices[i][0]=vertices[i][0]*scale+ox;vertices[i][1]=vertices[i][1]*scale+oy;}
            int rgb=q.get("color").getAsInt(),material=q.get("material").getAsInt();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,q.get("alpha").getAsFloat()));
            if(material==0){var p=new Path2D.Double();p.moveTo(vertices[0][0],vertices[0][1]);for(int i=1;i<4;i++)p.lineTo(vertices[i][0],vertices[i][1]);p.closePath();g.setColor(new Color(rgb));g.fill(p);continue;}
            var texture=TINT.computeIfAbsent(material+":"+rgb,k->PreviewNightMeshes.tint(textures[material],rgb));
            boolean reverse=q.get("reverseV").getAsBoolean();double v0=reverse?256:0,v1=reverse?0:256;
            double u0=q.get("startU").getAsDouble()*256,u1=q.get("endU").getAsDouble()*256;double[][] uv={{u0,v1},{u1,v1},{u1,v0},{u0,v0}};
            PreviewNightMeshes.triangle(g,texture,new double[][]{vertices[0],vertices[1],vertices[2]},new double[][]{uv[0],uv[1],uv[2]});
            PreviewNightMeshes.triangle(g,texture,new double[][]{vertices[0],vertices[2],vertices[3]},new double[][]{uv[0],uv[2],uv[3]});
        }
        g.setClip(null);g.setComposite(AlphaComposite.SrcOver);g.setColor(new Color(19,25,32));g.fillRect(0,0,w,25);
        g.setColor(Color.WHITE);g.drawString(shot.get("label").getAsString(),9,17);g.dispose();return image;
    }
    static BufferedImage board(int rows){var result=new BufferedImage(1440,rows*300+35,BufferedImage.TYPE_INT_RGB);var g=result.createGraphics();g.setColor(new Color(19,25,32));g.fillRect(0,0,1440,result.getHeight());g.setColor(Color.WHITE);g.drawString("Actual tested mesh + material / auto-fit offline projection / NOT in-game / no bloom",12,22);g.dispose();return result;}
    public static void main(String[] args)throws Exception{
        var shots=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();
        var names=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject().getAsJsonArray("textures");
        textures=new BufferedImage[names.size()];for(int i=1;i<names.size();i++){
            String name=names.get(i).getAsString().split(":",2)[1];textures[i]=ImageIO.read(Path.of(args[2],name+".png").toFile());
        }
        Path out=Path.of(args[3]);Files.createDirectories(out);
        String[] roles={"thunder","flame","ink","wind"};var overview=board(4);var og=overview.createGraphics();
        for(int theme=1;theme<=4;theme++){
            var roleBoard=board(2);var g=roleBoard.createGraphics();int n=0;
            int[] chosen=theme==1?new int[]{0,2,4,7}:theme==2?new int[]{1,2,3,4}:theme==3?new int[]{0,2,3,4}:new int[]{0,2,3,7};
            for(var raw:shots){var shot=raw.getAsJsonObject();if(shot.get("theme").getAsInt()!=theme)continue;
                var cell=cell(shot,n%2==1);g.drawImage(cell,n%4*360,n/4*300+35,null);
                for(int column=0;column<chosen.length;column++)if(n==chosen[column])og.drawImage(cell,column*360,(theme-1)*300+35,null);
                n++;
            }
            g.dispose();ImageIO.write(roleBoard,"png",out.resolve(roles[theme-1]+"-review.png").toFile());
        }
        og.dispose();ImageIO.write(overview,"png",out.resolve("mesh-review.png").toFile());
    }
}
