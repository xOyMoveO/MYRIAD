package com.myriad.tools;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.resources.model.cuboid.*;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

/** Offline actual MC26.2 parser/transform check, not a live hand-render acceptance. */
public final class FenjueChangdaoCheck {
    static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
    public static void main(String[] args)throws Exception{
        Path assets=Path.of(args[0]),art=Path.of(args[1]);
        Path source=art.resolve("fenjue_flame_changdao_v1.spec.json");
        var spec=JsonParser.parseString(Files.readString(source)).getAsJsonObject();
        var json=JsonParser.parseString(Files.readString(assets.resolve("models/item/lieyanjian.json"))).getAsJsonObject();
        var bb=JsonParser.parseString(Files.readString(art.resolve("fenjue_flame_changdao_v1.bbmodel"))).getAsJsonObject();
        String sha=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)));
        require(json.get("myriad_source_sha256").getAsString().equals(sha),"Held item uses this generated source");
        var parts=spec.getAsJsonArray("parts");var elements=json.getAsJsonArray("elements");
        require(elements.size()==423&&parts.size()==423&&bb.getAsJsonArray("elements").size()==423,"All source parts preserved");
        var cubes=new ArrayList<CuboidModelElement>();
        for(int i=0;i<parts.size();i++){
            var p=parts.get(i).getAsJsonObject();var e=elements.get(i).getAsJsonObject();
            var cube=CuboidModel.GSON.fromJson(e,CuboidModelElement.class);cubes.add(cube);
            for(int axis=0;axis<3;axis++){
                float from=cube.from().get(axis),to=cube.to().get(axis);
                require(from<to&&from>=-16&&to<=32,"Positive dimensions in vanilla bounds");
                double expected=8+p.getAsJsonArray("pos").get(axis).getAsDouble()/16;
                require(Math.abs(from-expected)<1E-6&&Math.abs(to-expected-p.getAsJsonArray("size").get(axis).getAsDouble()/16)<1E-6,"Source dimensions preserved");
            }
            for(var face:e.getAsJsonObject("faces").entrySet())for(var n:face.getValue().getAsJsonObject().getAsJsonArray("uv"))
                require(n.getAsDouble()>=0&&n.getAsDouble()<=16,"UV bounds");
        }
        var item=JsonParser.parseString(Files.readString(assets.resolve("items/lieyanjian.json"))).getAsJsonObject();
        require(item.getAsJsonObject("model").get("model").getAsString().equals("myriad:item/lieyanjian"),"Registered item routes to replacement in every context");
        byte[] png=Files.readAllBytes(assets.resolve("textures/item/fenjue_flame_changdao.png"));
        require(Arrays.equals(png,Files.readAllBytes(art.resolve("fenjue_flame_changdao_v1.png"))),"Source/exported atlas identical");
        String embedded=bb.getAsJsonArray("textures").get(0).getAsJsonObject().get("source").getAsString();
        require(embedded.startsWith("data:image/png;base64,"),"Portable embedded BBModel texture");
        require(Arrays.equals(png,Base64.getDecoder().decode(embedded.substring(embedded.indexOf(',')+1))),"BBModel texture matches game");
        var texture=ImageIO.read(new java.io.ByteArrayInputStream(png));
        require(texture.getWidth()==256&&texture.getHeight()==256,"256px atlas");
        for(var raw:spec.getAsJsonObject("atlas").getAsJsonArray("regions")){
            var region=raw.getAsJsonObject();var rect=region.getAsJsonArray("rect");
            int x=rect.get(0).getAsInt(),y=rect.get(1).getAsInt(),w=rect.get(2).getAsInt(),h=rect.get(3).getAsInt();
            int color=texture.getRGB(x,y);
            for(int v=y;v<y+h;v++)for(int u=x;u<x+w;u++)require(texture.getRGB(u,v)==color,"No random material noise");
        }
        var connected=new HashSet<Integer>();connected.add(0);
        boolean changed=true;while(changed){changed=false;
            for(int i=0;i<cubes.size();i++)if(!connected.contains(i)){
                var a=cubes.get(i);
                for(int j:new ArrayList<>(connected)){
                    var b=cubes.get(j);boolean touch=true;
                    for(int axis=0;axis<3;axis++)touch&=a.from().get(axis)<=b.to().get(axis)+1E-6&&b.from().get(axis)<=a.to().get(axis)+1E-6;
                    if(touch){connected.add(i);changed=true;break;}
                }
            }
        }
        require(connected.size()==cubes.size(),"All layers connected to grip");
        require(Math.abs(cubes.getFirst().to().x()-cubes.getFirst().from().x()-.8125)<1E-6&&Math.abs(cubes.getFirst().to().z()-cubes.getFirst().from().z()-.625)<1E-6,"Slim centred grip");
        try(var reader=Files.newBufferedReader(assets.resolve("models/item/lieyanjian.json"))){
            var model=CuboidModel.fromStream(reader);require(model.geometry()!=null,"MC26.2 accepts complete model");
            for(var context:List.of(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                    ItemDisplayContext.GUI,ItemDisplayContext.GROUND,ItemDisplayContext.FIXED)){
                var transform=model.transforms().getTransform(context);require(transform!=ItemTransform.NO_TRANSFORM,"Explicit display: "+context);
                // GUI, ground and item frame do not use left-hand mirroring.
                boolean hand=context==ItemDisplayContext.FIRST_PERSON_RIGHT_HAND||context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                        ||context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND||context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                for(boolean left:hand?new boolean[]{false,true}:new boolean[]{false}){
                    var pose=new PoseStack.Pose();transform.apply(left,pose);
                    var point=pose.pose().transformPosition(new Vector3f(.5f));
                    var expected=new Vector3f(transform.translation());if(left)expected.x=-expected.x;
                    require(point.isFinite()&&point.distance(expected)<1E-6&&pose.pose().determinant3x3()>0,"Centred grip/no reflection");
                    if(context==ItemDisplayContext.GUI||context==ItemDisplayContext.FIXED)for(var cube:cubes)for(int corner=0;corner<8;corner++){
                        var v=new Vector3f();for(int axis=0;axis<3;axis++)v.setComponent(axis,((corner&(1<<axis))==0?cube.from():cube.to()).get(axis)/16);
                        pose.pose().transformPosition(v);
                        require(Math.abs(v.x)<.48&&Math.abs(v.y)<.48,"Icon tip/guard safely inside frame: "+context+" left="+left+" vertex="+v+" translation="+transform.translation());
                    }
                }
            }
        }
        System.out.println("[MYRIAD-FENJUE-CHANGDAO] PASS: real MC26.2 parser; 423 connected positive source cubes; embedded/exported clean atlas; seven contexts; centred slim grip; GUI padding; actual LIEYANJIAN route");
    }
}
