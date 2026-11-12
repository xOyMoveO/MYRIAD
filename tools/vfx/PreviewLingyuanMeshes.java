import com.google.gson.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Review the actual Java runtime quads/UV2D, not an in-game screenshot or frame-rate test. */
public final class PreviewLingyuanMeshes {
    static final Map<String,BufferedImage> TINT=new HashMap<>();
    static BufferedImage[] textures;
    static double[] point(JsonObject q,String key){return PreviewNightMeshes.point(q.getAsJsonObject(key));}
    static boolean visible(JsonObject q){
        if(q.get("material").getAsInt()!=43)return true;
        double[] a=point(q,"a"),b=point(q,"b"),c=point(q,"c");
        double x=b[0]-a[0],y=b[1]-a[1],z=b[2]-a[2],X=c[0]-a[0],Y=c[1]-a[1],Z=c[2]-a[2];
        return (y*Z-z*Y)*.462+(z*X-x*Z)*.555+(x*Y-y*X)*.693>1E-10;
    }
    static BufferedImage tint(BufferedImage source,int rgb){
        var result=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<source.getHeight();y++)for(int x=0;x<source.getWidth();x++){
            int p=source.getRGB(x,y),r=(p>>16&255)*(rgb>>16&255)/255,g=(p>>8&255)*(rgb>>8&255)/255,b=(p&255)*(rgb&255)/255;
            result.setRGB(x,y,(p&0xFF000000)|(r<<16)|(g<<8)|b);
        }return result;
    }
    static BufferedImage render(JsonObject shot,boolean light){
        return render(shot,light,null);
    }
    static BufferedImage render(JsonObject shot,boolean light,double[] fixedBounds){
        int w=800,h=650;var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(light?new Color(165,178,161):new Color(30,44,48));g.fillRect(0,0,w,h);
        var list=new ArrayList<JsonObject>();for(var e:shot.getAsJsonArray("mesh"))if(visible(e.getAsJsonObject()))list.add(e.getAsJsonObject());
        list.sort(Comparator.comparingDouble(PreviewNightMeshes::depth));
        double minX=1E20,minY=1E20,maxX=-1E20,maxY=-1E20;
        for(var q:list)for(String k:new String[]{"a","b","c","d"}){
            var p=PreviewNightMeshes.project(point(q,k));minX=Math.min(minX,p[0]);maxX=Math.max(maxX,p[0]);minY=Math.min(minY,p[1]);maxY=Math.max(maxY,p[1]);
        }
        if(fixedBounds!=null){minX=fixedBounds[0];minY=fixedBounds[1];maxX=fixedBounds[2];maxY=fixedBounds[3];}
        double scale=Math.min((w-100)/Math.max(1,maxX-minX),(h-100)/Math.max(1,maxY-minY));
        double ox=w*.5-(minX+maxX)*.5*scale,oy=h*.5+12-(minY+maxY)*.5*scale;
        for(var q:list){
            var v=new double[4][];String[] keys={"a","b","c","d"};
            for(int i=0;i<4;i++){v[i]=PreviewNightMeshes.project(point(q,keys[i]));v[i][0]=v[i][0]*scale+ox;v[i][1]=v[i][1]*scale+oy;}
            int rgb=q.get("color").getAsInt(),mat=q.get("material").getAsInt();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,q.get("alpha").getAsFloat()));
            if(mat==0){var p=new Path2D.Double();p.moveTo(v[0][0],v[0][1]);for(int i=1;i<4;i++)p.lineTo(v[i][0],v[i][1]);p.closePath();g.setColor(new Color(rgb));g.fill(p);continue;}
            var tex=TINT.computeIfAbsent(mat+":"+rgb,k->tint(textures[mat],rgb));
            double u0=q.get("startU").getAsDouble()*tex.getWidth(),u1=q.get("endU").getAsDouble()*tex.getWidth();
            double v0=q.get("startV").getAsDouble()*tex.getHeight(),v1=q.get("endV").getAsDouble()*tex.getHeight();
            if(q.get("reverseV").getAsBoolean()){double swap=v0;v0=v1;v1=swap;}
            double[][] uv={{u0,v1},{u1,v1},{u1,v0},{u0,v0}};
            PreviewNightMeshes.triangle(g,tex,new double[][]{v[0],v[1],v[2]},new double[][]{uv[0],uv[1],uv[2]});
            PreviewNightMeshes.triangle(g,tex,new double[][]{v[0],v[2],v[3]},new double[][]{uv[0],uv[2],uv[3]});
        }
        g.setComposite(AlphaComposite.SrcOver);g.setColor(new Color(16,25,30));g.fillRect(0,0,w,36);g.setColor(Color.WHITE);
        String label=shot.has("label")?shot.get("label").getAsString():"kind="+shot.get("kind");
        g.drawString(label+" / adaptive scale / OFFLINE, NOT IN-GAME",12,24);g.dispose();return image;
    }
    public static void main(String[] args)throws Exception{
        var shots=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();
        var names=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject().getAsJsonArray("textures");textures=new BufferedImage[names.size()];
        for(int i=1;i<names.size();i++)textures[i]=ImageIO.read(Path.of(args[2],names.get(i).getAsString().split(":",2)[1]+".png").toFile());
        Path out=Path.of(args[3]);Files.createDirectories(out);
        var board=new BufferedImage(1600,650*((shots.size()+1)/2),BufferedImage.TYPE_INT_RGB);var g=board.createGraphics();int index=0;
        for(var e:shots){var shot=e.getAsJsonObject();var cell=render(shot,index%2==1);g.drawImage(cell,index%2*800,index/2*650,null);
            String name=shot.has("variant")?"runtime-skill-"+shot.get("variant").getAsInt():"runtime-kind-"+shot.get("kind").getAsInt();
            ImageIO.write(cell,"png",out.resolve(name+".png").toFile());index++;}
        g.dispose();ImageIO.write(board,"png",out.resolve("runtime-review.png").toFile());
    }
}
