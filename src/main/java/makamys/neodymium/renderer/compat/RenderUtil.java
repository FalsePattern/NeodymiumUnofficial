package makamys.neodymium.renderer.compat;

import makamys.neodymium.renderer.ChunkMesh;
import makamys.neodymium.renderer.NeoRenderer;
import makamys.neodymium.renderer.attribs.AttributeSet;
import makamys.neodymium.util.BufferWriter;

public interface RenderUtil {
    int POLYGON_OFFSET_XPOS = 0;
    int POLYGON_OFFSET_YPOS = 1;
    int POLYGON_OFFSET_ZPOS = 2;

    void polygonize(int[] tessBuffer, int tessOffset, int[] polygonBuffer, int polygonOffset, float offsetX, float offsetY, float offsetZ, ChunkMesh.Flags flags);

    /**
     * @implSpec These needs to be kept in sync with the attributes in {@link NeoRenderer#init()}
     */
    void writeMeshPolygonToBuffer(int[] meshPolygonBuffer, int polygonOffset, BufferWriter out, int expectedStride, int verticesPerPolygon);

    int vertexSizeInTessellator();

    int vertexSizeInPolygonBuffer();

    // Include the polygon normal
    default int polygonSize(int verticesPerPolygon) {
        return vertexSizeInPolygonBuffer() * verticesPerPolygon + 1;
    }

    void initVertexAttributes(AttributeSet attributes);

    default void applyVertexAttributes(AttributeSet attributes) {
        attributes.enable();
    }
}
