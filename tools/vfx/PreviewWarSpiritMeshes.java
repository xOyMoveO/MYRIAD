import com.google.gson.*;
import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Map;
import javax.imageio.ImageIO;

import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Offline projection of the same mesh and atlas used by the client. */
public class PreviewWarSpiritMeshes {
    private record Shot(String name,int skill,int kind,float radius,int angle,int life,float age,Vec3 from,Vec3 to) {}
    private static final Vec3 ORIGIN=Vec3.ZERO, FORWARD=new Vec3(.7,0,1);

    public static void main(String[] args)throws Exception {
        Path repo=Path.of(args[0]),out=Path.of(args[1]);
        Files.createDirectories(out);
        var asset=repo.resolve("src/main/resources/assets/myriad");
        var names=JsonParser.parseString(Files.readString(asset.resolve("particles/ability_mesh.json"))).getAsJsonObject().getAsJsonArray("textures");
        PreviewElementMeshes.textures=new BufferedImage[names.size()];
        for(int i=1;i<names.size();i++) {
            String id=names.get(i).getAsString().split(":",2)[1];
            PreviewElementMeshes.textures[i]=ImageIO.read(asset.resolve("textures/particle/"+id+".png").toFile());
        }
        Vec3 chest=new Vec3(0,.65,0),end=FORWARD.add(chest);
        var war=new Shot[]{
            shot("Battle Intent / ready",0,SEAL,.43f,3,8,2),shot("Unbroken Will / armor plates",1,SHIELD,.65f,360,12,2),
            new Shot("Mountain Cleave / forged edge",2,ARC,6,100,8,2,chest,end),
            new Shot("Soul Pursuit / return trails",3,TRAIL,.18f,180,7,2,chest,new Vec3(1.75,.75,3.5)),
            shot("Crimson Parry / crossed guard",4,SHIELD,.8f,0,20,3),
            new Shot("Rending Wave / model + VFX",5,FLIGHT,.22f,0,3,.8f,chest,end),
            shot("Unreturning Leap / landing",6,BURST,4.5f,360,9,4),
            new Shot("War Blade / empowered cut",7,ARC,3,80,8,2,chest,end)
        };
        var spirit=new Shot[]{
            shot("Spirit Echo / contract pages",0,SHIELD,.65f,360,12,3),shot("Spirit Chime / echo pair",1,SEAL,1.3f,2,8,3),
            new Shot("Paper Crane / model + VFX",2,FLIGHT,.22f,0,3,.8f,chest,end),
            new Shot("Spirit Tether / tightening silk",3,BEAM,.09f,1,8,2,chest,new Vec3(1.75,.75,2.6)),
            shot("Echo Beacon / jade bell and border",4,SEAL,4,360,80,12),
            new Shot("Cloud Escort / jade twin wake",5,TRAIL,.18f,0,7,2,chest,new Vec3(1.75,.75,3.5)),
            shot("Converging Chorus / outward pulse",6,SEAL,6,360,5,3),shot("Covenant Shelter / allied contract",7,SHIELD,.65f,360,12,3)
        };
        board(out.resolve("chixiao-skill-vfx.png"),8,war);
        board(out.resolve("yunsheng-skill-vfx.png"),9,spirit);
    }

    private static Shot shot(String name,int skill,int kind,float radius,int angle,int life,float age) {
        return new Shot(name,skill,kind,radius,angle,life,age,ORIGIN,FORWARD);
    }

    private static void board(Path out,int theme,Shot[] shots)throws Exception {
        var image=PreviewElementMeshes.board(2);var g=image.createGraphics();var gson=new Gson();
        for(int i=0;i<shots.length;i++) {
            var s=shots[i];
            var p=new AbilityVfxPayload(1,-1,theme,s.skill,s.kind,s.from,s.to,s.radius,s.angle,s.life,100,false,5);
            var geometry=AbilityVfxGeometry.build(p,s.age,false);
            var cell=PreviewElementMeshes.cell(gson.toJsonTree(Map.of("label",s.name,"mesh",geometry)).getAsJsonObject(),i%2==0);
            g.drawImage(cell,i%4*360,i/4*300+35,null);
        }
        g.dispose();ImageIO.write(image,"png",out.toFile());
    }
}
