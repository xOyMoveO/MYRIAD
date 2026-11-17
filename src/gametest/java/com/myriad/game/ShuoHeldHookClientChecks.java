package com.myriad.game;

import com.myriad.client.firstperson.ShuoBladeLight;

/** Original third-person native item display transform assertions. */
final class ShuoHeldHookClientChecks {
    static void verify(ClientAssertions h){
        // Resolve the actual native item model and display transforms on the physical client.
        try(var in=ShuoHeldHookClientChecks.class.getResourceAsStream("/assets/myriad/models/item/shuo_sword.json");var reader=new java.io.InputStreamReader(in)){
            var model=net.minecraft.client.resources.model.cuboid.CuboidModel.fromStream(reader);
            for(boolean left:new boolean[]{false,true}){
                var stack=new com.mojang.blaze3d.vertex.PoseStack();
                model.transforms().getTransform(left?net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND:net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).apply(left,stack.last());
                for(double f:new double[]{0,.25,.75,1}){
                    var edge=ShuoBladeLight.point(f);var p=stack.last().pose().transformPosition(edge);
                    h.assertTrue(p.isFinite(),"Third-person edge uses the same packaged display transform as the real item");
                }
            }
        }catch(Exception e){throw new IllegalStateException("Third-person model transform",e);}
    }
}
