package dev.thewander02.cinder.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import dev.thewander02.cinder.pack.PreparedPack;
import net.minecraft.resources.Identifier;

record PackPipeline(PreparedPack pack, RenderPipeline pipeline, ShaderSource shaderSource) {
    static PackPipeline create(PreparedPack pack) {
        String hash = pack.contentHash();
        Identifier shaderId = Identifier.fromNamespaceAndPath("cinder", "runtime/" + hash + "/final");
        Identifier pipelineId = Identifier.fromNamespaceAndPath("cinder", "pipeline/" + hash + "/final");

        ShaderSource source = (requestedId, type) -> {
            if (!shaderId.equals(requestedId)) {
                return null;
            }
            if (type == ShaderType.VERTEX) {
                return pack.vertexSource();
            }
            if (type == ShaderType.FRAGMENT) {
                return pack.fragmentSource();
            }
            return null;
        };

        RenderPipeline pipeline = RenderPipeline.builder()
                .withLocation(pipelineId)
                .withVertexShader(shaderId)
                .withFragmentShader(shaderId)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withCull(false)
                .build();
        return new PackPipeline(pack, pipeline, source);
    }
}
