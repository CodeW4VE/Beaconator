package xyz.w4ve.beaconator.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import org.joml.Vector4f;

/**
 * How the mod draws, from 1.21.6 on. Same four pipelines as the 1.21.5 version and the same
 * reason for there being four: see through terrain is a second pipeline rather than a switch.
 *
 * <p>What moved in 1.21.6 is how a shader is fed. The matrices used to be loose uniforms set on
 * the pipeline; they are now a uniform buffer that the render pass is handed per draw. So the
 * pipelines declare two buffer uniforms instead of three matrix ones, and every draw writes the
 * current transform into vanilla's dynamic uniform buffer first. The render target is also
 * addressed by texture view rather than texture, and honours the overrides vanilla sets when it
 * is drawing somewhere other than the screen.
 */
public final class Pipelines {
	/** Translucent boxes: the coverage volumes, the beams and the gap strips. */
	public static final RenderPipeline FACES =
			faces("faces", DepthTestFunction.LEQUAL_DEPTH_TEST);

	/** The same, drawn over the terrain rather than behind it. */
	public static final RenderPipeline FACES_SEE_THROUGH =
			faces("faces_see_through", DepthTestFunction.NO_DEPTH_TEST);

	/** Wireframes, beacon markers and the pyramid footprint. */
	public static final RenderPipeline LINES =
			lines("lines", DepthTestFunction.LEQUAL_DEPTH_TEST);

	/** The same, drawn over the terrain. */
	public static final RenderPipeline LINES_SEE_THROUGH =
			lines("lines_see_through", DepthTestFunction.NO_DEPTH_TEST);

	private Pipelines() {
	}

	private static RenderPipeline faces(String name, DepthTestFunction depthTest) {
		return common(name, depthTest).withVertexFormat(DefaultVertexFormat.POSITION_COLOR,
				VertexFormat.Mode.QUADS).build();
	}

	private static RenderPipeline lines(String name, DepthTestFunction depthTest) {
		return common(name, depthTest).withVertexFormat(DefaultVertexFormat.POSITION_COLOR,
				VertexFormat.Mode.DEBUG_LINES).build();
	}

	/**
	 * Everything the four have in common: vanilla's position and colour shader, alpha blending,
	 * no culling because the boxes are seen from inside as often as outside, and no depth writing
	 * so that overlapping translucent volumes do not punch holes in each other.
	 */
	private static RenderPipeline.Builder common(String name, DepthTestFunction depthTest) {
		return RenderPipeline.builder()
				.withLocation("pipeline/beaconator_" + name)
				.withVertexShader("core/position_color")
				.withFragmentShader("core/position_color")
				// The two buffers core/position_color reads. Vanilla keeps them in a shared
				// snippet that mods cannot reach, so they are spelled out.
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Projection", UniformType.UNIFORM_BUFFER)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withCull(false)
				.withDepthWrite(false)
				.withDepthTestFunction(depthTest);
	}

	/**
	 * Draws a built mesh through a pipeline and closes it, which is what
	 * {@code BufferUploader.drawWithShader} used to do.
	 *
	 * <p>Meshes built by {@link com.mojang.blaze3d.vertex.Tesselator} carry no index buffer, so
	 * the shared quad and line index buffers get used, exactly as vanilla does for its own.
	 */
	public static void draw(RenderPipeline pipeline, MeshData mesh) {
		try (mesh) {
			// The transform the shader will use, written into vanilla's per frame uniform buffer.
			// The white modulator is the "no tint" value: the colour is already per vertex.
			GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(
					RenderSystem.getModelViewMatrix(),
					new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
					RenderSystem.getModelOffset(),
					RenderSystem.getTextureMatrix(),
					RenderSystem.getShaderLineWidth());

			VertexFormat format = pipeline.getVertexFormat();
			GpuBuffer vertices = format.uploadImmediateVertexBuffer(mesh.vertexBuffer());
			MeshData.DrawState drawState = mesh.drawState();
			GpuBuffer indices;
			VertexFormat.IndexType indexType;

			if (mesh.indexBuffer() == null) {
				RenderSystem.AutoStorageIndexBuffer shared =
						RenderSystem.getSequentialBuffer(drawState.mode());
				indices = shared.getBuffer(drawState.indexCount());
				indexType = shared.type();
			} else {
				indices = format.uploadImmediateIndexBuffer(mesh.indexBuffer());
				indexType = drawState.indexType();
			}

			RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
			GpuTextureView color = RenderSystem.outputColorTextureOverride != null
					? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
			GpuTextureView depth = null;

			if (target.useDepth) {
				depth = RenderSystem.outputDepthTextureOverride != null
						? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView();
			}

			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
					() -> "beaconator", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
				pass.setPipeline(pipeline);

				// Nothing this mod draws is inside a scissor, but the world render can be, and a
				// pass that ignores it would draw over the edges of whatever set it.
				ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();

				if (scissor.enabled()) {
					pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
				}

				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transform);
				pass.setVertexBuffer(0, vertices);
				pass.setIndexBuffer(indices, indexType);
				pass.drawIndexed(0, 0, drawState.indexCount(), 1);
			}
		}
	}
}
