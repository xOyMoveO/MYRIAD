package com.myriad.client;
import com.myriad.game.LaneMinionEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
public final class SiegeMinionRenderer extends MobRenderer<LaneMinionEntity,MinionRenderState,SiegeMinionModel>{
 public SiegeMinionRenderer(EntityRendererProvider.Context context){super(context,new SiegeMinionModel(),.65F);}
 @Override public MinionRenderState createRenderState(){return new MinionRenderState();}
 @Override public Identifier getTextureLocation(MinionRenderState s){return Identifier.parse("myriad:textures/entity/siege_minion_"+(s.style<3?"red":"blue")+".png");}
 @Override public void extractRenderState(LaneMinionEntity e,MinionRenderState s,float partial){super.extractRenderState(e,s,partial);s.style=e.style();s.moving=e.moving();s.attackTicks=Math.max(0,e.attackTicks()-partial);}
}
