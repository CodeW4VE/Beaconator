package xyz.w4ve.beaconator.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import java.util.Optional;
import java.util.OptionalDouble;

public final class Pipelines {
    private static final ByteBufferBuilder ARENA = new ByteBufferBuilder(256 * 1024);
    public static final RenderPipeline FACES = faces("faces", CompareOp.LESS_THAN_OR_EQUAL);
    public static final RenderPipeline FACES_SEE_THROUGH = faces("faces_see_through", CompareOp.ALWAYS_PASS);
    public static final RenderPipeline LINES = lines("lines", CompareOp.LESS_THAN_OR_EQUAL);
    public static final RenderPipeline LINES_SEE_THROUGH = lines("lines_see_through", CompareOp.ALWAYS_PASS);
    static { ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ARENA.close()); }
    private Pipelines() {}

    private static RenderPipeline.Builder common(String name, CompareOp depthTest) {
        return RenderPipeline.builder()
                .withLocation("pipeline/beaconator_" + name)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .withDepthStencilState(new DepthStencilState(depthTest, false));
    }
    private static RenderPipeline faces(String name, CompareOp depthTest) {
        return common(name, depthTest).withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS).build();
    }
    private static RenderPipeline lines(String name, CompareOp depthTest) {
        return common(name, depthTest).withVertexShader("core/rendertype_lines")
                .withFragmentShader("core/rendertype_lines")
                .withBindGroupLayout(BindGroupLayouts.FOG)
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
                .withPrimitiveTopology(PrimitiveTopology.LINES).build();
    }
    public static BufferBuilder begin(PrimitiveTopology topology, VertexFormat format) {
        return new BufferBuilder(ARENA, topology, format);
    }
    public static void draw(RenderPipeline pipeline, MeshData mesh) {
        try (mesh) {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            GpuBufferSlice vertices = encoder.transientMemory().uploadGpu(mesh.vertexBuffer(), 4, GpuBuffer.USAGE_VERTEX);
            MeshData.DrawState state = mesh.drawState();
            RenderSystem.AutoStorageIndexBuffer shared = RenderSystem.getSequentialBuffer(state.primitiveTopology());
            GpuBuffer indices = shared.getBuffer(state.indexCount());
            RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            try (RenderPass pass = encoder.createRenderPass(() -> "beaconator", target.getColorTextureView(),
                    Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
                ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
                if (scissor.enabled()) pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy()));
                pass.setVertexBuffer(0, vertices);
                pass.setIndexBuffer(indices, shared.type());
                // RenderPearl orders these as count, instances, first index, base vertex, first instance.
                pass.drawIndexed(state.indexCount(), 1, 0, 0, 0);
            }

        }
    }
}
