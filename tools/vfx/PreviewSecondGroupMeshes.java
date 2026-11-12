import com.google.gson.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Actual server-generated mesh + atlas UVs; review projection, never a client screenshot. */
public class PreviewSecondGroupMeshes {
 public static void main(String[] args)throws Exception{
  var shots=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();
  var names=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject().getAsJsonArray("textures");
  PreviewElementMeshes.textures=new BufferedImage[names.size()];
  for(int i=1;i<names.size();i++){String n=names.get(i).getAsString().split(":",2)[1];PreviewElementMeshes.textures[i]=ImageIO.read(Path.of(args[2],n+".png").toFile());}
  Path out=Path.of(args[3]);Files.createDirectories(out);var overview=PreviewElementMeshes.board(5);var og=overview.createGraphics();
  for(int theme=5;theme<10;theme++){
   var board=PreviewElementMeshes.board(4);var g=board.createGraphics();int n=0;
   for(var raw:shots){var s=raw.getAsJsonObject();if(s.get("theme").getAsInt()!=theme)continue;
    var cell=PreviewElementMeshes.cell(s,n%2==0);g.drawImage(cell,n%4*360,n/4*300+35,null);
    int v=s.get("variant").getAsInt(),kind=s.get("kind").getAsInt(),column=kind==9&&v==2?0:kind==2&&v==6?1:kind==7&&v==4?2:kind==3&&v==7?3:-1;
    if(column>=0)og.drawImage(cell,column*360,(theme-5)*300+35,null);n++;
   }g.dispose();ImageIO.write(board,"png",out.resolve(new String[]{"sword","ice","herb","war","spirit"}[theme-5]+"-review.png").toFile());
  }og.dispose();ImageIO.write(overview,"png",out.resolve("mesh-review.png").toFile());
 }
}
