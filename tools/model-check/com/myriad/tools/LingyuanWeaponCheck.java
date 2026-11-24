package com.myriad.tools;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.resources.model.cuboid.*;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

/** Uses the installed current Minecraft parser and real display transform, with no running client required. */
public final class LingyuanWeaponCheck {
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    public static void main(String[] args)throws Exception{
        Path assets=Path.of(args[0]),art=Path.of(args[1]);
        var json=JsonParser.parseString(Files.readString(assets.resolve("models/item/lingyuan_sword.json"))).getAsJsonObject();
        var spec=JsonParser.parseString(Files.readString(art.resolve("lingyuan_returning_jian.spec.json"))).getAsJsonObject();
        require(json.getAsJsonArray("elements").size()==122,"Every authored cuboid contributes at least one visible face");
        try(var reader=Files.newBufferedReader(assets.resolve("models/item/lingyuan_sword.json"))){
            var model=CuboidModel.fromStream(reader);require(model.geometry()!=null,"Real cuboid model parser accepts the weapon");
            for(var context:List.of(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,ItemDisplayContext.THIRD_PERSON_LEFT_HAND,ItemDisplayContext.GUI,
                    ItemDisplayContext.GROUND,ItemDisplayContext.FIXED)){
                var transform=model.transforms().getTransform(context);require(transform!=null&&transform!=ItemTransform.NO_TRANSFORM,"Explicit display context: "+context);
                for(boolean left:new boolean[]{false,true}){
                    var pose=new PoseStack.Pose();transform.apply(left,pose);
                    var actual=pose.pose().transformPosition(new Vector3f(.5F,.5F,.5F));
                    var expected=new Vector3f(transform.translation());if(left)expected.x=-expected.x;
                    require(actual.isFinite()&&actual.distance(expected)<1E-6,"Actual parser keeps the handle at the hand pivot, without blade-centre drift");
                }
            }
        }
        var source=new HashMap<String,JsonObject>();
        for(var e:spec.getAsJsonArray("parts")){var part=e.getAsJsonObject();source.put(part.get("id").getAsString(),part);}
        var texture=ImageIO.read(assets.resolve("textures/item/lingyuan_jade_sword.png").toFile());
        require(texture.getWidth()==256&&texture.getHeight()==256,"Held atlas has native resolution");
        double tip=Double.NEGATIVE_INFINITY;
        int faces=0;
        for(var e:json.getAsJsonArray("elements")){
            var cube=e.getAsJsonObject();var part=source.remove(cube.get("name").getAsString());
            require(part!=null,"Runtime model cuboid has a source");
            var actual=MinecraftModelParser.element(cube);
            tip=Math.max(tip,actual.to().y());
            for(int axis=0;axis<3;axis++){
                double fit=axis==1?.25*(27.3-8)/(31.25-8):.25,lo=8+part.getAsJsonArray("pos").get(axis).getAsDouble()*fit;
                double hi=lo+part.getAsJsonArray("size").get(axis).getAsDouble()*fit;
                require(hi>lo&&Math.abs(actual.from().get(axis)-lo)<3E-6&&Math.abs(actual.to().get(axis)-hi)<3E-6,"Exact fitted source cuboid dimensions: "+part.get("id"));
                if(part.get("id").getAsString().equals("wrapped_grip"))require(Math.abs((lo+hi)/2-8)<1E-6,"Grip centre (8,8,8)");
            }
            for(var face:cube.getAsJsonObject("faces").entrySet()){
                var uv=face.getValue().getAsJsonObject().getAsJsonArray("uv");var original=part.getAsJsonObject("faces").getAsJsonObject(face.getKey()).getAsJsonArray("uv");
                int[] px=new int[4];
                for(int n=0;n<4;n++){
                    px[n]=original.get(n).getAsInt();
                    require(Math.abs(uv.get(n).getAsDouble()*16-px[n])<1E-8,"Atlas UV preserved without whole-atlas repetition");
                }
                for(int y=px[1];y<px[3];y++)for(int x=px[0];x<px[2];x++)
                    require((texture.getRGB(x,y)>>>24)==255,"Held model face has no alpha defects");
                faces++;
            }
        }
        require(source.isEmpty()&&Math.abs(tip-27.3)<3E-6,"Visible blade tip stays at the authored first-person trail anchor");
        System.out.println("[MYRIAD-LINGYUAN-HELD] PASS: actual Minecraft model parser, 122 source cuboids / "+faces+" faces, opaque UVs, 7 display contexts, original grip and tip");
    }
}
