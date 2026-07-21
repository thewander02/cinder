package dev.thewander02.cinder.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mojang.blaze3d.shaders.ShaderType;
import dev.thewander02.cinder.pack.PreparedPack;
import java.nio.file.Path;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class PackPipelineTest {
    @Test
    void dispatchesOnlyItsHashIdentifiedVertexAndFragmentSources() {
        PreparedPack pack = new PreparedPack(
                "fixture",
                Path.of("fixture"),
                "vertex-source",
                "fragment-source",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        );

        PackPipeline pipeline = PackPipeline.create(pack);
        Identifier shaderId = pipeline.pipeline().getVertexShader();

        assertEquals("vertex-source", pipeline.shaderSource().get(shaderId, ShaderType.VERTEX));
        assertEquals("fragment-source", pipeline.shaderSource().get(shaderId, ShaderType.FRAGMENT));
        assertNull(pipeline.shaderSource().get(Identifier.fromNamespaceAndPath("cinder", "other"), ShaderType.VERTEX));
    }
}
