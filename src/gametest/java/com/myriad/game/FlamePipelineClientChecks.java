package com.myriad.game;

import com.myriad.client.particle.FlameTransparentLayer;
import net.minecraft.client.renderer.RenderPipelines;

/** The original pipeline assertions require Minecraft's physical client classes. */
final class FlamePipelineClientChecks {
    static void verify(ClientAssertions h) {
        var base=RenderPipelines.TRANSLUCENT_PARTICLE;var flame=FlameTransparentLayer.LAYER.pipeline();
        h.assertTrue(base.getDepthStencilState().writeDepth()&&!flame.getDepthStencilState().writeDepth(),"Only Fenjue's new layered sheets stop writing depth; vanilla pipeline stays unchanged");
        h.assertTrue(flame.getDepthStencilState().depthTest()==base.getDepthStencilState().depthTest(),"Depth testing is retained: fire never becomes an x-ray overlay through terrain");
        var ground=FlameTransparentLayer.GROUND.pipeline();
        h.assertTrue(!ground.getDepthStencilState().writeDepth()&&ground.getDepthStencilState().depthTest()==base.getDepthStencilState().depthTest()
                &&ground.getDepthStencilState().depthBiasConstant()>0&&flame.getDepthStencilState().depthBiasConstant()==0,
                "Only ground decals receive a positive reverse-Z raster bias against terrain; vertical fire keeps its original depth");
        h.assertTrue(flame.getShaders().equals(base.getShaders())
                &&flame.getBindGroupLayouts().equals(base.getBindGroupLayouts())&&flame.getShaderDefines().equals(base.getShaderDefines())
                &&flame.getVertexFormatBindings().equals(base.getVertexFormatBindings())
                &&flame.getColorTargetStates().equals(base.getColorTargetStates())&&flame.isCull()==base.isCull(),"Native shader/atlas/layout/blending/culling are preserved");
    }
}
