package makamys.neodymium.renderer.compat;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.val;
import makamys.neodymium.renderer.ChunkMesh;
import makamys.neodymium.renderer.attribs.AttributeSet;
import makamys.neodymium.util.BufferWriter;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.ARBVertexShader;

import static makamys.neodymium.renderer.MeshPolygon.DEFAULT_BRIGHTNESS;
import static makamys.neodymium.renderer.MeshPolygon.DEFAULT_COLOR;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_SHORT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RenderUtilShaders implements RenderUtil {
    public static final RenderUtilShaders INSTANCE = new RenderUtilShaders();

    public static final int POLYGON_OFFSET_U = 3;
    public static final int POLYGON_OFFSET_V = 4;
    public static final int POLYGON_OFFSET_C = 5;
    public static final int POLYGON_OFFSET_B = 6;
    public static final int POLYGON_OFFSET_E1 = 7;
    public static final int POLYGON_OFFSET_E2 = 8;
    public static final int POLYGON_OFFSET_XN = 9;
    public static final int POLYGON_OFFSET_YN = 10;
    public static final int POLYGON_OFFSET_ZN = 11;
    public static final int POLYGON_OFFSET_XT = 12;
    public static final int POLYGON_OFFSET_YT = 13;
    public static final int POLYGON_OFFSET_ZT = 14;
    public static final int POLYGON_OFFSET_WT = 15;
    public static final int POLYGON_OFFSET_UM = 16;
    public static final int POLYGON_OFFSET_VM = 17;

    public static final int POLYGON_OFFSET_OPTIFINE_START = POLYGON_OFFSET_E1;
    public static final int POLYGON_OFFSET_OPTIFINE_END = POLYGON_OFFSET_VM;
    public static final int POLYGON_OFFSET_OPTIFINE_COUNT = POLYGON_OFFSET_OPTIFINE_END - POLYGON_OFFSET_OPTIFINE_START + 1;

    @Override
    public void polygonize(int[] tessBuffer, int tessOffset, int[] polygonBuffer, int polygonOffset, float offsetX, float offsetY, float offsetZ, ChunkMesh.Flags flags) {
        polygonBuffer[polygonOffset + POLYGON_OFFSET_XPOS] = Float.floatToRawIntBits(Float.intBitsToFloat(tessBuffer[tessOffset]) + offsetX);
        polygonBuffer[polygonOffset + POLYGON_OFFSET_YPOS] = Float.floatToRawIntBits(Float.intBitsToFloat(tessBuffer[tessOffset + 1]) + offsetY);
        polygonBuffer[polygonOffset + POLYGON_OFFSET_ZPOS] = Float.floatToRawIntBits(Float.intBitsToFloat(tessBuffer[tessOffset + 2]) + offsetZ);

        polygonBuffer[polygonOffset + POLYGON_OFFSET_U] = tessBuffer[tessOffset + 3];
        polygonBuffer[polygonOffset + POLYGON_OFFSET_V] = tessBuffer[tessOffset + 4];

        polygonBuffer[polygonOffset + POLYGON_OFFSET_C] = flags.hasColor ? tessBuffer[tessOffset + 5] : DEFAULT_COLOR;

        polygonBuffer[polygonOffset + POLYGON_OFFSET_B] = flags.hasBrightness ? tessBuffer[tessOffset + 6] : DEFAULT_BRIGHTNESS;

        System.arraycopy(tessBuffer, tessOffset + 7, polygonBuffer, polygonOffset + POLYGON_OFFSET_OPTIFINE_START, POLYGON_OFFSET_OPTIFINE_COUNT);
    }

    @Override
    public void writeMeshPolygonToBuffer(int[] meshPolygonBuffer, int polygonOffset, BufferWriter out, int expectedStride, int verticesPerPolygon) {
        val vertexSize = vertexSizeInPolygonBuffer();
        for(int vi = 0; vi < verticesPerPolygon; vi++) {
            int offset = polygonOffset + vi * vertexSize;
            for (int i = 0; i < vertexSize; i++) {
                out.writeInt(meshPolygonBuffer[offset + i]);
            }

            assert out.position() % expectedStride == 0;
        }
    }

    @Override
    public int vertexSizeInTessellator() {
        // pos + uv + color + brightness + entityData + normal + tangent + midtexture
        return 3 + 2 + 1 + 1 + 2 + 3 + 4 + 2;
    }

    @Override
    public int vertexSizeInPolygonBuffer() {
        // pos + uv + color + brightness + entityData + normal + tangent + midtexture
        return 3 + 2 + 1 + 1 + 2 + 3 + 4 + 2;
    }

    @Override
    public void initVertexAttributes(AttributeSet attributes) {
        attributes.addAttribute("POS", 3, 4, GL_FLOAT);
        attributes.addAttribute("TEXTURE", 2, 4, GL_FLOAT);
        attributes.addAttribute("COLOR", 4, 1, GL_UNSIGNED_BYTE);
        attributes.addAttribute("BRIGHTNESS", 2, 2, GL_SHORT);
        attributes.addAttribute("ENTITY_DATA_1", 1, 4, GL_UNSIGNED_INT);
        attributes.addAttribute("ENTITY_DATA_2", 1, 4, GL_UNSIGNED_INT);
        attributes.addAttribute("NORMAL", 3, 4, GL_FLOAT);
        attributes.addAttribute("TANGENT", 4, 4, GL_FLOAT);
        attributes.addAttribute("MIDTEXTURE", 2, 4, GL_FLOAT);
    }


    /**
     * TODO: This format is nice, we should have it in the docs too!
     * position   3 floats 12 bytes offset  0
     * texture    2 floats  8 bytes offset 12
     * color      4 bytes   4 bytes offset 20
     * brightness 2 shorts  4 bytes offset 24
     * entitydata 3 shorts  6 bytes offset 28
     * [padding]  --------  2 bytes offset 34
     * normal     3 floats 12 bytes offset 36
     * tangent    4 floats 16 bytes offset 48
     * midtexture 2 floats  8 bytes offset 64
     *
     * @param attributes Configured Attributes
     */
    @Override
    public void applyVertexAttributes(AttributeSet attributes) {
        val stride = attributes.stride();

        val entityAttrib = 10;
        val midTexCoordAttrib = 11;
        val tangentAttrib = 12;

        // position   3 floats 12 bytes offset 0
        glVertexPointer(3, GL_FLOAT, stride, 0);
        glEnableClientState(GL_VERTEX_ARRAY);

        // texture    2 floats  8 bytes offset 12
        glTexCoordPointer(2, GL_FLOAT, stride, 12);
        glEnableClientState(GL_TEXTURE_COORD_ARRAY);

        // color      4 bytes   4 bytes offset 20
        glColorPointer(4, GL_UNSIGNED_BYTE, stride, 20);
        glEnableClientState(GL_COLOR_ARRAY);

        // brightness 2 shorts  4 bytes offset 24
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        glTexCoordPointer(2, GL_SHORT, stride, 24);
        glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);

        // entitydata 3 shorts  6 bytes offset 28
        glVertexAttribPointer(entityAttrib, 3, GL_SHORT, false, stride, 28);
        glEnableVertexAttribArray(entityAttrib);

        // normal     3 floats 12 bytes offset 36
        glNormalPointer(GL_FLOAT, stride, 36);
        glEnableClientState(GL_NORMAL_ARRAY);

        // tangent    4 floats 16 bytes offset 48
        glVertexAttribPointer(tangentAttrib, 4, GL_FLOAT, false, stride, 48);
        glEnableVertexAttribArray(tangentAttrib);

        // midtexture 2 floats  8 bytes offset 64
        glClientActiveTexture(GL_TEXTURE3);
        glTexCoordPointer(2, GL_FLOAT, stride, 64);
        glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);

        ARBVertexShader.glVertexAttribPointerARB(midTexCoordAttrib, 2, GL_FLOAT, false, stride, 64);
        ARBVertexShader.glEnableVertexAttribArrayARB(midTexCoordAttrib);
    }
}
