package com.myriad.game;

import com.myriad.client.firstperson.ShuoArmModel;

/** Original player-model vertex and UV assertions, executed on the physical client. */
final class ShuoArmClientChecks {
    static void verify(ClientAssertions h) {
        for(boolean slim:new boolean[]{false,true})for(boolean left:new boolean[]{false,true}){
            var hand=ShuoArmModel.bake(slim,left);int[] cubes={0};
            hand.visit(new com.mojang.blaze3d.vertex.PoseStack(),(pose,path,index,cube)->{
                cubes[0]++;
                for(var face:cube.polygons)for(var v:face.vertices()){
                    h.assertTrue(v.y()>=ShuoArmModel.CUT_Y&&v.y()<=10.3f,"Only rigid fist/short straight forearm; no elbow continuation");
                    h.assertTrue(v.u()>=0&&v.u()<=1&&v.v()>=0&&v.v()<=1,"Original player-skin UV remains bounded");
                }
            });h.assertTrue(cubes[0]==2,"One rigid hand plus sleeve, no segmented elbow mesh");
        }
    }
}
