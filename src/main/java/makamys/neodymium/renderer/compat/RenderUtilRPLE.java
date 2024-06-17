package makamys.neodymium.renderer.compat;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.val;
import makamys.neodymium.config.Config;
import makamys.neodymium.renderer.ChunkMesh;
import makamys.neodymium.renderer.attribs.AttributeSet;
import makamys.neodymium.util.BufferWriter;

import static makamys.neodymium.renderer.MeshPolygon.DEFAULT_BRIGHTNESS;
import static makamys.neodymium.renderer.MeshPolygon.DEFAULT_COLOR;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_SHORT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_SHORT;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RenderUtilRPLE implements RenderUtil {
    public static final RenderUtilRPLE INSTANCE = new RenderUtilRPLE();

    public static final int POLYGON_OFFSET_U = 3;
    public static final int POLYGON_OFFSET_V = 4;
    public static final int POLYGON_OFFSET_C = 5;
    public static final int POLYGON_OFFSET_BR = 6;
    public static final int POLYGON_OFFSET_BG = 7;
    public static final int POLYGON_OFFSET_BB = 8;

    @Override
    public void readMeshPolygon(int[] tessBuffer, int tessOffset, int[] polygonBuffer, int polygonOffset, float offsetX, float offsetY, float offsetZ, int vertices, ChunkMesh.Flags flags) {
        val tessVertexSize = vertexSizeInTessellator();
        val polygonVertexSize = vertexSizeInPolygonBuffer();

        for(int vi = 0; vi < vertices; vi++) {
            int tI = tessOffset + vi * tessVertexSize;
            int qI = polygonOffset + vi * polygonVertexSize;

            polygonBuffer[qI + POLYGON_OFFSET_XPOS] = Float.floatToRawIntBits(Float.intBitsToFloat(tessBuffer[tI]) + offsetX);
            polygonBuffer[qI + POLYGON_OFFSET_YPOS] = Float.floatToRawIntBits(Float.intBitsToFloat(tessBuffer[tI + 1]) + offsetY);
            polygonBuffer[qI + POLYGON_OFFSET_ZPOS] = Float.floatToRawIntBits(Float.intBitsToFloat(tessBuffer[tI + 2]) + offsetZ);

            polygonBuffer[qI + POLYGON_OFFSET_U] = tessBuffer[tI + 3];
            polygonBuffer[qI + POLYGON_OFFSET_V] = tessBuffer[tI + 4];

            polygonBuffer[qI + POLYGON_OFFSET_C] = flags.hasColor ? tessBuffer[tI + 5] : DEFAULT_COLOR;

            // TODO normals?

            if (flags.hasBrightness) {
                polygonBuffer[qI + POLYGON_OFFSET_BR] = tessBuffer[tI + 7];
                polygonBuffer[qI + POLYGON_OFFSET_BG] = tessBuffer[tI + 8];
                polygonBuffer[qI + POLYGON_OFFSET_BB] = tessBuffer[tI + 9];
            } else {
                polygonBuffer[qI + POLYGON_OFFSET_BR] = DEFAULT_BRIGHTNESS;
                polygonBuffer[qI + POLYGON_OFFSET_BG] = DEFAULT_BRIGHTNESS;
                polygonBuffer[qI + POLYGON_OFFSET_BB] = DEFAULT_BRIGHTNESS;
            }
        }
    }

    @Override
    public void writeMeshPolygonToBuffer(int[] meshPolygonBuffer, int polygonOffset, BufferWriter out, int expectedStride, int verticesPerPolygon) {
        val vertexSize = vertexSizeInPolygonBuffer();
        for(int vi = 0; vi < verticesPerPolygon; vi++) {
            int offset = polygonOffset + vi * vertexSize;
            out.writeFloat(Float.intBitsToFloat(meshPolygonBuffer[offset + POLYGON_OFFSET_XPOS]));
            out.writeFloat(Float.intBitsToFloat(meshPolygonBuffer[offset + POLYGON_OFFSET_YPOS]));
            out.writeFloat(Float.intBitsToFloat(meshPolygonBuffer[offset + POLYGON_OFFSET_ZPOS]));

            float u = Float.intBitsToFloat(meshPolygonBuffer[offset + POLYGON_OFFSET_U]);
            float v = Float.intBitsToFloat(meshPolygonBuffer[offset + POLYGON_OFFSET_V]);

            if(Config.shortUV) {
                out.writeShort((short)(Math.round(u * 32768f)));
                out.writeShort((short)(Math.round(v * 32768f)));
            } else {
                out.writeFloat(u);
                out.writeFloat(v);
            }

            out.writeInt(meshPolygonBuffer[offset + POLYGON_OFFSET_C]);

            out.writeInt(meshPolygonBuffer[offset + POLYGON_OFFSET_BR]);
            out.writeInt(meshPolygonBuffer[offset + POLYGON_OFFSET_BG]);
            out.writeInt(meshPolygonBuffer[offset + POLYGON_OFFSET_BB]);

            assert out.position() % expectedStride == 0;
        }
    }

    @Override
    public int vertexSizeInTessellator() {
        // pos + uv + color + normal + brightnessRGB + <VertexAPI wasted space>
        return 3 + 2 + 1 + 1 + 3 + 2;
    }

    @Override
    public int vertexSizeInPolygonBuffer() {
        // pos + uv + color + brightnessRGB
        return 3 + 2 + 1 + 3;
    }

    @Override
    public void initVertexAttributes(AttributeSet attributes) {
        attributes.addAttribute("POS", 3, 4, GL_FLOAT);
        if (Config.shortUV) {
            attributes.addAttribute("TEXTURE", 2, 2, GL_UNSIGNED_SHORT);
        } else {
            attributes.addAttribute("TEXTURE", 2, 4, GL_FLOAT);
        }
        attributes.addAttribute("COLOR", 4, 1, GL_UNSIGNED_BYTE);
        attributes.addAttribute("BRIGHTNESS_RED", 2, 2, GL_SHORT);
        attributes.addAttribute("BRIGHTNESS_GREEN", 2, 2, GL_SHORT);
        attributes.addAttribute("BRIGHTNESS_BLUE", 2, 2, GL_SHORT);
    }
}
