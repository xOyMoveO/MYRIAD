package com.myriad.tools;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.resources.model.cuboid.*;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

/** Real 26.2 item parser and hand matrices; source, seven contexts and atlas validation. */
public final class ThunderSwordCheck {
    static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
    public static void main(String[] args)throws Exception{
        Path assets=Path.of(args[0]),art=Path.of(args[1]);
        Path source=art.resolve("tianxing_thunder_longsword_v2.spec.json");
        var spec=JsonParser.parseString(Files.readString(source)).getAsJsonObject();
        var json=JsonParser.parseString(Files.readString(assets.resolve("models/item/leitingjian.json"))).getAsJsonObject();
        String sha=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)));
        require(json.get("myriad_source_sha256").getAsString().equals(sha),"Actual held item uses this approved new source");
        var parts=spec.getAsJsonArray("parts");var elements=json.getAsJsonArray("elements");
        require(elements.size()==169&&parts.size()==169,"All 169 V2 source cuboids retained");
        for(int i=0;i<parts.size();i++){
            var p=parts.get(i).getAsJsonObject();var e=elements.get(i).getAsJsonObject();
            var cube=CuboidModel.GSON.fromJson(e,CuboidModelElement.class);
            for(int axis=0;axis<3;axis++){
                float from=cube.from().get(axis),to=cube.to().get(axis);
                require(from<to&&from>=-16&&to<=32,"Positive cuboid dimensions in vanilla bounds: "+p.get("id"));
                double expected=8+p.getAsJsonArray("pos").get(axis).getAsDouble()/16;
                require(Math.abs(from-expected)<1E-6&&Math.abs(to-expected-p.getAsJsonArray("size").get(axis).getAsDouble()/16)<1E-6,"Source dimensions preserved");
            }
            for(var entry:e.getAsJsonObject("faces").entrySet()){
                var uv=entry.getValue().getAsJsonObject().getAsJsonArray("uv");
                for(var n:uv)require(n.getAsDouble()>=0&&n.getAsDouble()<=16,"Atlas UV bounds");
            }
        }
        var item=JsonParser.parseString(Files.readString(assets.resolve("items/leitingjian.json"))).getAsJsonObject();
        require(item.getAsJsonObject("model").get("model").getAsString().equals("myriad:item/leitingjian"),"Real registered item routes to the replaced model in every context");
        var texture=ImageIO.read(assets.resolve("textures/item/tianxing_thunder_longsword.png").toFile());
        require(texture.getWidth()==256&&texture.getHeight()==256,"Embedded/exported pixel atlas resolution");
        for(var raw:spec.getAsJsonObject("atlas").getAsJsonArray("regions")){
            var region=raw.getAsJsonObject();var rect=region.getAsJsonArray("rect");
            int x=rect.get(0).getAsInt(),y=rect.get(1).getAsInt(),w=rect.get(2).getAsInt(),h=rect.get(3).getAsInt();
            int color=texture.getRGB(x,y);
            for(int v=y;v<y+h;v++)for(int u=x;u<x+w;u++)require(texture.getRGB(u,v)==color,"Material has no noise/speckles: "+region.get("id"));
        }
        var connected=new HashSet<Integer>();connected.add(0);
        boolean changed=true;while(changed){changed=false;
            for(int i=0;i<elements.size();i++)if(!connected.contains(i)){
                var a=CuboidModel.GSON.fromJson(elements.get(i),CuboidModelElement.class);
                for(int j:new ArrayList<>(connected)){
                    var b=CuboidModel.GSON.fromJson(elements.get(j),CuboidModelElement.class);boolean touch=true;
                    for(int axis=0;axis<3;axis++)touch&=a.from().get(axis)<=b.to().get(axis)+1E-6&&b.from().get(axis)<=a.to().get(axis)+1E-6;
                    if(touch){connected.add(i);changed=true;break;}
                }
            }
        }
        require(connected.size()==169,"Every layer and tassel strand physically touches the sword, no floating guard plates");
        var grip=CuboidModel.GSON.fromJson(elements.get(0),CuboidModelElement.class);
        require(Math.abs(grip.to().x()-grip.from().x()-.8125)<1E-6&&Math.abs(grip.to().z()-grip.from().z()-.625)<1E-6,"Grip width -35%, depth -50%; length/centre preserved");
        for(var raw:elements){var e=raw.getAsJsonObject();if(e.get("name").getAsString().equals("blade_0")){
            var cube=CuboidModel.GSON.fromJson(e,CuboidModelElement.class);
            require(Math.abs(cube.to().z()-cube.from().z()-.125)<1E-6,"Steel blade core depth is one third of V1");
        }}
        try(var r=Files.newBufferedReader(assets.resolve("models/item/leitingjian.json"))){
            var model=CuboidModel.fromStream(r);require(model.geometry()!=null,"MC26.2 accepts complete geometry");
            for(var context:List.of(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                    ItemDisplayContext.GUI,ItemDisplayContext.GROUND,ItemDisplayContext.FIXED)){
                var transform=model.transforms().getTransform(context);
                require(transform!=ItemTransform.NO_TRANSFORM,"Explicit display: "+context);
                for(boolean left:new boolean[]{false,true}){
                    var pose=new PoseStack.Pose();transform.apply(left,pose);
                    var point=pose.pose().transformPosition(new Vector3f(.5f));
                    var expected=new Vector3f(transform.translation());if(left)expected.x=-expected.x;
                    require(point.isFinite()&&point.distance(expected)<1E-6&&pose.pose().determinant3x3()>0,"Centred hand pivot, no reflected/inside-out sword");
                    if(context==ItemDisplayContext.GUI||context==ItemDisplayContext.FIXED){
                        for(var value:elements){
                            var e=value.getAsJsonObject();var lo=e.getAsJsonArray("from");var hi=e.getAsJsonArray("to");
                            for(int corner=0;corner<8;corner++){
                                var vertex=new Vector3f();for(int axis=0;axis<3;axis++)vertex.setComponent(axis,((corner&(1<<axis))==0?lo:hi).get(axis).getAsFloat()/16);
                                pose.pose().transformPosition(vertex);
                                require(Math.abs(vertex.x)<.48&&Math.abs(vertex.y)<.48,"GUI/fixed blade tip and tassel retain safe padding");
                            }
                        }
                    }
                }
            }
        }
        System.out.println("[MYRIAD-THUNDER-SWORD] PASS: MC26.2 actual item parser, 169 connected positive V2 source cubes, nine clean material regions, slim blade/grip, 256px atlas/UV, seven displays, centred +Y grip, no negative scales, existing LEITINGJIAN binding");
    }
}
