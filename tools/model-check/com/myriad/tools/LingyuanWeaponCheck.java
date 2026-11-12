package com.myriad.tools;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.resources.model.cuboid.*;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

/** Uses the installed MC26.2 parser and real display transform, with no running client required. */
public final class LingyuanWeaponCheck {
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    public static void main(String[] args)throws Exception{
        Path assets=Path.of(args[0]),art=Path.of(args[1]);
        var json=JsonParser.parseString(Files.readString(assets.resolve("models/item/lingyuan_sword.json"))).getAsJsonObject();
        var spec=JsonParser.parseString(Files.readString(art.resolve("lingyuan_jade_sword_v3.spec.json"))).getAsJsonObject();
        String sha=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(art.resolve("lingyuan_jade_sword_v3.spec.json"))));
        require(json.get("myriad_source_sha256").getAsString().equals(sha),"Held weapon must come from the approved V3, not a proxy");
        require(json.getAsJsonArray("elements").size()==186,"All source cubes retained");
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
        int rotations=0;
        var source=spec.getAsJsonArray("parts");var cubes=json.getAsJsonArray("elements");
        for(int i=0;i<cubes.size();i++){
            var part=source.get(i).getAsJsonObject();var cube=cubes.get(i).getAsJsonObject();
            var actual=CuboidModel.GSON.fromJson(cube,CuboidModelElement.class);
            for(int axis=0;axis<3;axis++){
                double offset=axis==1?10.5:0,lo=8+(part.getAsJsonArray("pos").get(axis).getAsDouble()+offset)*.2;
                double hi=lo+part.getAsJsonArray("size").get(axis).getAsDouble()*.2;
                require(Math.abs(actual.from().get(axis)-lo)<2E-6&&Math.abs(actual.to().get(axis)-hi)<2E-6,"Exact source cuboid dimensions: "+part.get("id"));
            }
            if(actual.rotation()!=null){rotations++;require(actual.rotation().value() instanceof CuboidRotation.EulerXYZRotation,"Native Euler rotation preserves compound wrap and claw angles");}
            for(var face:cube.getAsJsonObject("faces").entrySet()){
                var uv=face.getValue().getAsJsonObject().getAsJsonArray("uv");var original=part.getAsJsonObject("faces").getAsJsonObject(face.getKey()).getAsJsonArray("uv");
                for(int n=0;n<4;n++)require(Math.abs(uv.get(n).getAsDouble()*8-original.get(n).getAsDouble())<1E-8,"Atlas UV preserved without whole-atlas repetition");
            }
        }
        require(rotations>10,"Source rotated microgeometry retained");
        var texture=ImageIO.read(assets.resolve("textures/item/lingyuan_jade_sword.png").toFile());
        require(texture.getWidth()==128&&texture.getHeight()==128,"Held atlas has native resolution");
        System.out.println("[MYRIAD-LINGYUAN-HELD] PASS: actual MC26.2 model parser, 186 source parts, native Euler rotations, source UVs, 7 contexts, centred grip and texture");
    }
}
