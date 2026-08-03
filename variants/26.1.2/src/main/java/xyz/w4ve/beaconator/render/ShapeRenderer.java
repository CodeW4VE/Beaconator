package xyz.w4ve.beaconator.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Box drawing helpers. Written against the 1.21 buffer API: vertices go in as
 * {@code addVertex(matrix, x, y, z).setColor(r, g, b, a)}.
 *
 * <p>This is the 1.21.11 and newer version of the file. It draws the same shapes; what changed is
 * line thickness. It used to be one GL call around the draw, {@code RenderSystem.lineWidth}, and
 * that call is gone: a line's width is now an attribute of each of its vertices, and vanilla's
 * line shader expands the line to that many pixels in screen space. So the width is carried here
 * as state of the helper rather than state of the driver, which also means it is honoured on
 * hardware that always ignored {@code glLineWidth} and drew everything one pixel wide.
 */
public final class ShapeRenderer {
	private static float lineWidth = 1.0f;

	private ShapeRenderer() {
	}

	/** Width, in pixels, for every line drawn until this is called again. */
	public static void lineWidth(float width) {
		lineWidth = width;
	}

	public static float red(int argb) {
		return (argb >> 16 & 0xFF) / 255.0f;
	}

	public static float green(int argb) {
		return (argb >> 8 & 0xFF) / 255.0f;
	}

	public static float blue(int argb) {
		return (argb & 0xFF) / 255.0f;
	}

	/** Six translucent faces. Culling is off while these are drawn, so winding does not matter. */
	public static void boxFaces(VertexConsumer buffer, Matrix4f matrix,
			double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
			float r, float g, float b, float a) {
		float x0 = (float) minX;
		float y0 = (float) minY;
		float z0 = (float) minZ;
		float x1 = (float) maxX;
		float y1 = (float) maxY;
		float z1 = (float) maxZ;

		// bottom
		quad(buffer, matrix, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
		// top
		quad(buffer, matrix, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
		// north
		quad(buffer, matrix, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a);
		// south
		quad(buffer, matrix, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a);
		// west
		quad(buffer, matrix, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a);
		// east
		quad(buffer, matrix, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a);
	}

	/** A single horizontal quad, used for the flat overlays. */
	public static void flatQuad(VertexConsumer buffer, Matrix4f matrix,
			double minX, double y, double minZ, double maxX, double maxZ,
			float r, float g, float b, float a) {
		quad(buffer, matrix,
				(float) minX, (float) y, (float) minZ,
				(float) minX, (float) y, (float) maxZ,
				(float) maxX, (float) y, (float) maxZ,
				(float) maxX, (float) y, (float) minZ,
				r, g, b, a);
	}

	/** The twelve edges of a box, for a line mode buffer. */
	public static void boxEdges(VertexConsumer buffer, Matrix4f matrix,
			double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
			float r, float g, float b, float a) {
		float x0 = (float) minX;
		float y0 = (float) minY;
		float z0 = (float) minZ;
		float x1 = (float) maxX;
		float y1 = (float) maxY;
		float z1 = (float) maxZ;

		line(buffer, matrix, x0, y0, z0, x1, y0, z0, r, g, b, a);
		line(buffer, matrix, x1, y0, z0, x1, y0, z1, r, g, b, a);
		line(buffer, matrix, x1, y0, z1, x0, y0, z1, r, g, b, a);
		line(buffer, matrix, x0, y0, z1, x0, y0, z0, r, g, b, a);

		line(buffer, matrix, x0, y1, z0, x1, y1, z0, r, g, b, a);
		line(buffer, matrix, x1, y1, z0, x1, y1, z1, r, g, b, a);
		line(buffer, matrix, x1, y1, z1, x0, y1, z1, r, g, b, a);
		line(buffer, matrix, x0, y1, z1, x0, y1, z0, r, g, b, a);

		line(buffer, matrix, x0, y0, z0, x0, y1, z0, r, g, b, a);
		line(buffer, matrix, x1, y0, z0, x1, y1, z0, r, g, b, a);
		line(buffer, matrix, x1, y0, z1, x1, y1, z1, r, g, b, a);
		line(buffer, matrix, x0, y0, z1, x0, y1, z1, r, g, b, a);
	}

	/** The four edges of a horizontal rectangle. */
	public static void rectEdges(VertexConsumer buffer, Matrix4f matrix,
			double minX, double y, double minZ, double maxX, double maxZ,
			float r, float g, float b, float a) {
		float x0 = (float) minX;
		float x1 = (float) maxX;
		float z0 = (float) minZ;
		float z1 = (float) maxZ;
		float yy = (float) y;

		line(buffer, matrix, x0, yy, z0, x1, yy, z0, r, g, b, a);
		line(buffer, matrix, x1, yy, z0, x1, yy, z1, r, g, b, a);
		line(buffer, matrix, x1, yy, z1, x0, yy, z1, r, g, b, a);
		line(buffer, matrix, x0, yy, z1, x0, yy, z0, r, g, b, a);
	}

	/**
	 * One line segment, the way vanilla's line shader wants it: both ends carry the direction of
	 * the segment as their normal and the width to draw it at.
	 *
	 * <p>The normal is what the shader offsets along to give the line thickness, so a zero length
	 * segment has nothing to offset along and is dropped rather than drawn as a dot.
	 */
	public static void line(VertexConsumer buffer, Matrix4f matrix,
			float x0, float y0, float z0, float x1, float y1, float z1,
			float r, float g, float b, float a) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float dz = z1 - z0;
		float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

		if (length == 0.0f) {
			return;
		}

		dx /= length;
		dy /= length;
		dz /= length;

		buffer.addVertex(matrix, x0, y0, z0).setColor(r, g, b, a).setNormal(dx, dy, dz)
				.setLineWidth(lineWidth);
		buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(dx, dy, dz)
				.setLineWidth(lineWidth);
	}

	private static void quad(VertexConsumer buffer, Matrix4f matrix,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz,
			float r, float g, float b, float a) {
		buffer.addVertex(matrix, ax, ay, az).setColor(r, g, b, a);
		buffer.addVertex(matrix, bx, by, bz).setColor(r, g, b, a);
		buffer.addVertex(matrix, cx, cy, cz).setColor(r, g, b, a);
		buffer.addVertex(matrix, dx, dy, dz).setColor(r, g, b, a);
	}
}
