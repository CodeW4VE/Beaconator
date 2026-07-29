package xyz.w4ve.beaconator.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;

/**
 * How the mod draws, from 1.21.5 on.
 *
 * <p>Up to 1.21.4 drawing was a sequence of switches: turn blending on, turn culling off, stop
 * writing depth, pick a shader, upload the buffer. All of that is gone. Blending, culling and
 * depth are now baked into a {@link RenderPipeline} declared up front, and you draw by handing a
 * mesh to one.
 *
 * <p>That is why there are four pipelines and not two. "See through terrain" used to be a switch
 * flipped before the draw; it cannot be, now, so it is a second pipeline of each kind that
 * differs only in its depth test. The renderer picks per draw.
 *
 * <p>These are deliberately not registered with vanilla. Registration exists so the shader loader
 * can precompile a pipeline at resource reload; ours compile the first time they are used, which
 * costs one frame, once, on a mod that draws nothing until you load a plan.
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
				// The three uniforms core/position_color declares. Vanilla keeps them in a shared
				// snippet that mods cannot reach, so they are spelled out.
				.withUniform("ModelViewMat", UniformType.MATRIX4X4)
				.withUniform("ProjMat", UniformType.MATRIX4X4)
				.withUniform("ColorModulator", UniformType.VEC4)
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

			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
					target.getColorTexture(), OptionalInt.empty(),
					target.useDepth ? target.getDepthTexture() : null, OptionalDouble.empty())) {
				pass.setPipeline(pipeline);
				pass.setVertexBuffer(0, vertices);

				// Nothing this mod draws is inside a scissor, but the world render can be, and a
				// pass that ignores it would draw over the edges of whatever set it.
				if (RenderSystem.SCISSOR_STATE.isEnabled()) {
					pass.enableScissor(RenderSystem.SCISSOR_STATE);
				}

				pass.setIndexBuffer(indices, indexType);
				pass.drawIndexed(0, drawState.indexCount());
			}
		}
	}
}
