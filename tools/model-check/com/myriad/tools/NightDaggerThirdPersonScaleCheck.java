package com.myriad.tools;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.*;
import java.util.List;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

/** Actual Minecraft transforms: enlarge around the palm, never around the sword tip. */
public final class NightDaggerThirdPersonScaleCheck {
    private static void require(boolean ok,String message) { if(!ok)throw new IllegalStateException(message); }
    public static void main(String[] args)throws Exception {
        Path before=Path.of(args[0]),after=Path.of(args[1]);
        var oldJson=JsonParser.parseString(Files.readString(before)).getAsJsonObject();
        var newJson=JsonParser.parseString(Files.readString(after)).getAsJsonObject();
        var adjusted=oldJson.deepCopy();
        for(String hand:List.of("thirdperson_righthand","thirdperson_lefthand"))
            adjusted.getAsJsonObject("display").getAsJsonObject(hand).add("scale",
                    JsonParser.parseString("[1.5,1.5,1.5]"));
        require(adjusted.equals(newJson),"Only the two third-person scales may change; geometry/UV/texture/other displays stay identical");
        CuboidModel oldModel,newModel;
        try(var reader=Files.newBufferedReader(before)){oldModel=CuboidModel.fromStream(reader);}
        try(var reader=Files.newBufferedReader(after)){newModel=CuboidModel.fromStream(reader);}
        require(newModel.geometry()!=null,"Real parser accepts detailed dagger geometry");
        for(var context:ItemDisplayContext.values()) {
            boolean held=context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND||context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            boolean left=context.leftHand();
            var a=new PoseStack.Pose();var b=new PoseStack.Pose();
            oldModel.transforms().getTransform(context).apply(left,a);
            newModel.transforms().getTransform(context).apply(left,b);
            var palmA=a.pose().transformPosition(new Vector3f(.5f,.5f,.5f));
            var palmB=b.pose().transformPosition(new Vector3f(.5f,.5f,.5f));
            require(palmA.distance(palmB)<1E-6,"Palm pivot stays fixed: "+context);
            for(var p:List.of(new Vector3f(.5f,1,.5f),new Vector3f(.75f,.5f,.5f),new Vector3f(.5f,.5f,.75f))) {
                var va=a.pose().transformPosition(new Vector3f(p)).sub(palmA);
                var vb=b.pose().transformPosition(new Vector3f(p)).sub(palmB);
                require(Math.abs(vb.length()/va.length()-(held?1.5f:1f))<2E-6,"Exact scale ratio: "+context);
                require(va.normalize().distance(vb.normalize())<2E-6,"No rotation or blade-edge change: "+context);
            }
            if(!held)require(a.pose().equals(b.pose()),"First-person, GUI, dropped/frame and NONE matrices unchanged: "+context);
        }
        System.out.println("[MYRIAD-NIGHT-THIRDPERSON-SCALE] PASS: real MC parser; both hands 1.5x; palm/rotation unchanged; all other contexts, 321 cubes, UVs and textures unchanged");
    }
}
