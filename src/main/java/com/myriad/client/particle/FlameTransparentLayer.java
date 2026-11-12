package com.myriad.client.particle;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

/** Fenjue's translucent sheets depth-test against the world without occluding one another. */
public final class FlameTransparentLayer {
    public static final SingleQuadParticle.Layer LAYER=new SingleQuadParticle.Layer(true,TextureAtlas.LOCATION_PARTICLES,
            new Pipeline(RenderPipelines.TRANSLUCENT_PARTICLE,false));
    public static final SingleQuadParticle.Layer GROUND=new SingleQuadParticle.Layer(true,TextureAtlas.LOCATION_PARTICLES,
            new Pipeline(RenderPipelines.TRANSLUCENT_PARTICLE,true));
    private FlameTransparentLayer(){}
    private static final class Pipeline extends RenderPipeline {
        private Pipeline(RenderPipeline base,boolean ground){
            super(Identifier.fromNamespaceAndPath("myriad",ground?"pipeline/fenjue_ground":"pipeline/fenjue_translucent"),
                    base.getVertexShader(),base.getFragmentShader(),base.getShaderDefines(),base.getBindGroupLayouts(),
                    base.getColorTargetStates().clone(),new DepthStencilState(base.getDepthStencilState().depthTest(),false,0,ground?2:0),
                    base.getPolygonMode(),base.isCull(),base.getVertexFormatBindings().clone(),base.getPrimitiveTopology(),base.getSortKey()+(ground?4097:4096));
        }
    }
}
