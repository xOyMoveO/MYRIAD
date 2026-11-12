package com.myriad.client;
import com.myriad.game.LaneMinionEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
public final class MinionRenderer extends MobRenderer<LaneMinionEntity,MinionRenderState,MinionModel> {
    public MinionRenderer(EntityRendererProvider.Context context){super(context,new MinionModel(),.4F);}
    @Override public MinionRenderState createRenderState(){return new MinionRenderState();}
    @Override public Identifier getTextureLocation(MinionRenderState state){
        String kind=switch(state.style%3){case 1->"ranged";case 2->"magic";default->"melee";};
        return Identifier.parse("myriad:textures/item/minion_"+kind+"_"+(state.style<3?"red":"blue")+".png");
    }
    @Override public void extractRenderState(LaneMinionEntity entity,MinionRenderState state,float partial){
        super.extractRenderState(entity,state,partial);state.style=entity.style();state.moving=entity.moving();state.attackTicks=Math.max(0,entity.attackTicks()-partial);
    }
}
