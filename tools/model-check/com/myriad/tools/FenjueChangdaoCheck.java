package com.myriad.tools;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.resources.model.cuboid.*;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

/** Offline actual Minecraft parser/transform check, not a live hand-render acceptance. */
public final class FenjueChangdaoCheck {
    static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
    public static void main(String[] args)throws Exception{
        Path input=Path.of(args.length==0?".":args[0]);
        boolean repository=Files.isDirectory(input.resolve("src/main/resources/assets/myriad"));
        Path assets=repository?input.resolve("src/main/resources/assets/myriad"):input;
        Path art=repository?input.resolve("art_source/fenjue"):Path.of(args[1]);
        Path source=art.resolve("fenjue_flame_changdao.spec.json");
        var spec=JsonParser.parseString(Files.readString(source)).getAsJsonObject();
        var json=JsonParser.parseString(Files.readString(assets.resolve("models/item/lieyanjian.json"))).getAsJsonObject();
        var bb=JsonParser.parseString(Files.readString(art.resolve("fenjue_flame_changdao.bbmodel"))).getAsJsonObject();
        String sha=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)));
        require(json.get("myriad_source_sha256").getAsString().equals(sha),"Held item uses this generated source");
        require(json.get("display").equals(spec.get("display")) && json.get("display").equals(bb.get("display")),
                "Source, editable model and game share every display transform");
        var parts=bb.getAsJsonArray("elements");
        var elements=json.getAsJsonArray("elements");
        require(parts.size()==elements.size(),"All visible editable parts exported");
        var cubes=new ArrayList<CuboidModelElement>();
        var byName=new HashMap<String,CuboidModelElement>();
        var resolution=bb.getAsJsonObject("resolution");
        double uvScale=16.0/resolution.get("width").getAsDouble();
        for(int i=0;i<parts.size();i++){
            var p=parts.get(i).getAsJsonObject();
            var e=elements.get(i).getAsJsonObject();
            var cube=MinecraftModelParser.element(e);
            cubes.add(cube);
            var name=e.get("name").getAsString();
            require(byName.put(name,cube)==null && p.get("name").getAsString().equals(name),"Unique ordered source parts");
            require(e.get("from").equals(p.get("from")) && e.get("to").equals(p.get("to")),"Editable bounds match "+name);
            for(int axis=0;axis<3;axis++){
                float from=cube.from().get(axis),to=cube.to().get(axis);
                require(from<to && from>=-16 && to<=32,"Positive dimensions in vanilla bounds");
            }
            for(var face:e.getAsJsonObject("faces").entrySet()){
                var uv=face.getValue().getAsJsonObject().getAsJsonArray("uv");
                var expected=p.getAsJsonObject("faces").getAsJsonObject(face.getKey()).getAsJsonArray("uv");
                require(uv.get(0).getAsDouble()!=uv.get(2).getAsDouble() && uv.get(1).getAsDouble()!=uv.get(3).getAsDouble(),"Face samples an area");
                for(int n=0;n<4;n++){
                    double coordinate=uv.get(n).getAsDouble();
                    require(coordinate>=0 && coordinate<=16 && Math.abs(coordinate-expected.get(n).getAsDouble()*uvScale)<1E-7,"UV matches source");
                }
            }
        }
        // Physical anchors used by the held weapon and its blade-following trails.
        var grip=byName.get("grip");
        require(grip.from().equals(new Vector3f(7.59375f,6.25f,7.6875f)) &&
                grip.to().equals(new Vector3f(8.40625f,9.75f,8.3125f)),"Centred slim grip");
        var guard=byName.get("guard");
        require(guard.from().equals(new Vector3f(7.25f,10f,7.875f)) &&
                guard.to().equals(new Vector3f(8.75f,10.75f,8.125f)),"Guard stays on the same grip joint");
        var tip=byName.get("point_45");
        require(tip.from().equals(new Vector3f(8.84375f,30.125f,7.875f)) &&
                tip.to().equals(new Vector3f(8.90625f,30.28125f,8.125f)),"Positive-X cutting edge and original blade tip");
        var item=JsonParser.parseString(Files.readString(assets.resolve("items/lieyanjian.json"))).getAsJsonObject();
        require(item.getAsJsonObject("model").get("model").getAsString().equals("myriad:item/lieyanjian"),"Registered item routes to replacement in every context");
        byte[] png=Files.readAllBytes(assets.resolve("textures/item/fenjue_flame_changdao.png"));
        String embedded=bb.getAsJsonArray("textures").get(0).getAsJsonObject().get("source").getAsString();
        require(embedded.startsWith("data:image/png;base64,"),"Portable embedded BBModel texture");
        require(Arrays.equals(png,Base64.getDecoder().decode(embedded.substring(embedded.indexOf(',')+1))),"BBModel texture matches game");
        var texture=ImageIO.read(new java.io.ByteArrayInputStream(png));
        require(texture.getWidth()==resolution.get("width").getAsInt() && texture.getHeight()==resolution.get("height").getAsInt(),
                "Atlas dimensions match model UV dimensions");
        // Sample the actual North-face islands to verify visible steel, braid, and gem detail.
        for(String name:List.of("grip","fold_08","ember_facet_3_front")){
            var part=java.util.stream.StreamSupport.stream(parts.spliterator(),false).map(JsonElement::getAsJsonObject)
                    .filter(p->p.get("name").getAsString().equals(name)).findFirst().orElseThrow();
            var uv=part.getAsJsonObject("faces").getAsJsonObject("north").getAsJsonArray("uv");
            int x1=uv.get(0).getAsInt(),x2=uv.get(2).getAsInt(),y1=uv.get(1).getAsInt(),y2=uv.get(3).getAsInt();
            var colors=new HashSet<Integer>();
            for(int y=Math.min(y1,y2);y<Math.max(y1,y2);y++)
                for(int x=Math.min(x1,x2);x<Math.max(x1,x2);x++)colors.add(texture.getRGB(x,y));
            require(colors.size()>=3,"Bound material detail on "+name);
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
        try(var reader=Files.newBufferedReader(assets.resolve("models/item/lieyanjian.json"))){
            var model=CuboidModel.fromStream(reader);require(model.geometry()!=null,"current Minecraft accepts complete model");
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
        System.out.println("[MYRIAD-FENJUE-CHANGDAO] PASS: real current Minecraft parser; "+cubes.size()+" connected positive cubes; bound steel, braid and gem texture; seven contexts; centred slim grip; GUI padding; actual LIEYANJIAN route");
    }
}
