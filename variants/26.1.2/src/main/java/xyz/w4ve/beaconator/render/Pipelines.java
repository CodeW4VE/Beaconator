package xyz.w4ve.beaconator.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * How the mod draws, from 1.21.11 on. Same four pipelines as before and the same reason for there
 * being four: see through terrain is a second pipeline rather than a switch.
 *
 * <p>Two things changed from the 1.21.6 version. The per draw transform lost its line width
 * argument, because line width stopped being global state. And the lines are drawn with vanilla's
 * own line shader rather than {@code position_color}, which is what reads that width back: it
 * takes the direction of each segment and expands it to a screen space quad. That shader wants
 * fog and screen size as well, so the line pipelines declare two more uniform buffers than the
 * face ones. {@link com.mojang.blaze3d.systems.RenderSystem#bindDefaultUniforms} fills those in.
 */
public final class Pipelines {
	private static final Vector3f NO_OFFSET = new Vector3f();
	private static final Matrix4f IDENTITY = new Matrix4f();

	/** Translucent boxes: the coverage volumes, the beams and the gap strips. */
	public static final RenderPipeline FACES =
			faces("faces", CompareOp.LESS_THAN_OR_EQUAL);

	/** The same, drawn over the terrain rather than behind it. */
	public static final RenderPipeline FACES_SEE_THROUGH =
			faces("faces_see_through", CompareOp.ALWAYS_PASS);

	/** Wireframes, beacon markers and the pyramid footprint. */
	public static final RenderPipeline LINES =
			lines("lines", CompareOp.LESS_THAN_OR_EQUAL);

	/** The same, drawn over the terrain. */
	public static final RenderPipeline LINES_SEE_THROUGH =
			lines("lines_see_through", CompareOp.ALWAYS_PASS);

	private Pipelines() {
	}

	private static RenderPipeline faces(String name, CompareOp depthTest) {
		return common(name, depthTest)
				.withVertexShader("core/position_color")
				.withFragmentShader("core/position_color")
				.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
				.build();
	}

	/**
	 * Lines go through vanilla's line shader, which is the only thing that reads the per vertex
	 * width. It needs the segment direction in the normal, which {@link ShapeRenderer#line} puts
	 * there, and the mode has to be {@code LINES} rather than {@code DEBUG_LINES} so that each
	 * segment is indexed as the quad the shader expands it into.
	 */
	private static RenderPipeline lines(String name, CompareOp depthTest) {
		return common(name, depthTest)
				.withVertexShader("core/rendertype_lines")
				.withFragmentShader("core/rendertype_lines")
				// Beyond the two below: the fog the fragment shader applies, and the screen size
				// the vertex shader measures the width against.
				.withUniform("Fog", UniformType.UNIFORM_BUFFER)
				.withUniform("Globals", UniformType.UNIFORM_BUFFER)
				.withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH,
						VertexFormat.Mode.LINES)
				.build();
	}

	/**
	 * Everything the four have in common: alpha blending, no culling because the boxes are seen
	 * from inside as often as outside, and no depth writing so that overlapping translucent
	 * volumes do not punch holes in each other.
	 */
	private static RenderPipeline.Builder common(String name, CompareOp depthTest) {
		return RenderPipeline.builder()
				.withLocation("pipeline/beaconator_" + name)
				// The two buffers every one of these shaders reads. Vanilla keeps them in a shared
				// snippet that mods cannot reach, so they are spelled out.
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Projection", UniformType.UNIFORM_BUFFER)
				// Blending and depth are each one state object now rather than a call apiece.
				// Not writing depth is part of the depth state, which is why there is no longer a
				// withDepthWrite next to it.
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
				.withCull(false)
				.withDepthStencilState(new DepthStencilState(depthTest, false));
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
			// The white modulator is the "no tint" value: the colour is already per vertex. The
			// offset and texture matrix are the identity ones this mod has always drawn with;
			// they used to be read back off RenderSystem, which no longer keeps them.
			GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(
					RenderSystem.getModelViewMatrix(),
					new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
					NO_OFFSET,
					IDENTITY);

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
