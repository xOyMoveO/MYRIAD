import com.google.gson.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Fixed camera/scale for actual runtime geometry; no per-frame auto-centering. */
public final class PreviewLingyuanAnimation {
    public static void main(String[] args)throws Exception{
        var shots=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();
        var names=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject().getAsJsonArray("textures");
        PreviewLingyuanMeshes.textures=new BufferedImage[names.size()];
        for(int i=1;i<names.size();i++)PreviewLingyuanMeshes.textures[i]=ImageIO.read(Path.of(args[2],names.get(i).getAsString().split(":",2)[1]+".png").toFile());
        double[][] bounds=new double[3][];
        for(int i=0;i<3;i++)bounds[i]=new double[]{1E20,1E20,-1E20,-1E20};
        for(var e:shots){var s=e.getAsJsonObject();var b=bounds[s.get("sequence").getAsInt()];
            for(var q:s.getAsJsonArray("mesh"))if(PreviewLingyuanMeshes.visible(q.getAsJsonObject()))for(String k:new String[]{"a","b","c","d"}){
                var p=PreviewNightMeshes.project(PreviewLingyuanMeshes.point(q.getAsJsonObject(),k));
                b[0]=Math.min(b[0],p[0]);b[1]=Math.min(b[1],p[1]);b[2]=Math.max(b[2],p[0]);b[3]=Math.max(b[3],p[1]);
            }
        }
        Path out=Path.of(args[3]);Files.createDirectories(out);
        for(int frame=0;frame<32;frame++){
            var image=new BufferedImage(1200,325,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            for(int seq=0;seq<3;seq++){
                var shot=shots.get(frame*3+seq).getAsJsonObject();
                g.drawImage(PreviewLingyuanMeshes.render(shot,seq==1,bounds[seq]),seq*400,0,400,325,null);
            }
            g.dispose();ImageIO.write(image,"png",out.resolve(String.format("frame_%02d.png",frame)).toFile());
        }
    }
}
