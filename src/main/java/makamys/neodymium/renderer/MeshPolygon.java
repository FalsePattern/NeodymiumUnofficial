package makamys.neodymium.renderer;

import lombok.experimental.UtilityClass;
import lombok.val;
import makamys.neodymium.Neodymium;
import makamys.neodymium.util.Util;
import org.lwjgl.util.vector.Vector3f;

import static makamys.neodymium.renderer.compat.RenderUtil.POLYGON_OFFSET_XPOS;
import static makamys.neodymium.renderer.compat.RenderUtil.POLYGON_OFFSET_YPOS;
import static makamys.neodymium.renderer.compat.RenderUtil.POLYGON_OFFSET_ZPOS;

@UtilityClass
public final class MeshPolygon {
    public final static int DEFAULT_BRIGHTNESS = Util.createBrightness(15, 15);
    public final static int DEFAULT_COLOR = 0xFFFFFFFF;


    private static class Vectors {
        public final Vector3f A = new Vector3f();
        public final Vector3f B = new Vector3f();
        public final Vector3f C = new Vector3f();
    }

    private static final ThreadLocal<Vectors> VECTORS = ThreadLocal.withInitial(Vectors::new);

    public static boolean processPolygon(int[] tessBuffer, int tessOffset, int[] polygonBuffer, int polygonOffset, float offsetX, float offsetY, float offsetZ, boolean triangulate, ChunkMesh.Flags flags) {
        val util = Neodymium.util;
        val tessVertexSize = util.vertexSizeInTessellator();
        val polygonVertexSize = util.vertexSizeInPolygonBuffer();
        for (int i = 0; i < 3; i++) {
            util.polygonize(tessBuffer, tessOffset + i * tessVertexSize, polygonBuffer, polygonOffset + i * polygonVertexSize, offsetX, offsetY, offsetZ, flags);
        }
        if (triangulate) {
            util.polygonize(tessBuffer, tessOffset + 3 * tessVertexSize, polygonBuffer, polygonOffset + 3 * polygonVertexSize + 1, offsetX, offsetY, offsetZ, flags);
            util.polygonize(tessBuffer, tessOffset, polygonBuffer, polygonOffset + 4 * polygonVertexSize + 1, offsetX, offsetY, offsetZ, flags);
            util.polygonize(tessBuffer, tessOffset + 2 * tessVertexSize, polygonBuffer, polygonOffset + 5 * polygonVertexSize + 1, offsetX, offsetY, offsetZ, flags);
        }
        boolean deleted = true;
        for (int i = 1; i < 3; i++) {
            int offset = polygonOffset + polygonVertexSize * i;
            if (polygonBuffer[polygonOffset + POLYGON_OFFSET_XPOS] != polygonBuffer[offset + POLYGON_OFFSET_XPOS] ||
                polygonBuffer[polygonOffset + POLYGON_OFFSET_YPOS] != polygonBuffer[offset + POLYGON_OFFSET_YPOS] ||
                polygonBuffer[polygonOffset + POLYGON_OFFSET_ZPOS] != polygonBuffer[offset + POLYGON_OFFSET_ZPOS]) {
                deleted = false;
                break;
            }
        }
        if (triangulate) {
            int offset = polygonOffset + polygonVertexSize * 3 + 1;
            if (polygonBuffer[polygonOffset + POLYGON_OFFSET_XPOS] != polygonBuffer[offset + POLYGON_OFFSET_XPOS] ||
                polygonBuffer[polygonOffset + POLYGON_OFFSET_YPOS] != polygonBuffer[offset + POLYGON_OFFSET_YPOS] ||
                polygonBuffer[polygonOffset + POLYGON_OFFSET_ZPOS] != polygonBuffer[offset + POLYGON_OFFSET_ZPOS]) {
                deleted = false;
            }
        }

        if (deleted)
            return true;

        float X0 = Float.intBitsToFloat(polygonBuffer[polygonOffset + POLYGON_OFFSET_XPOS]);
        float Y0 = Float.intBitsToFloat(polygonBuffer[polygonOffset + POLYGON_OFFSET_YPOS]);
        float Z0 = Float.intBitsToFloat(polygonBuffer[polygonOffset + POLYGON_OFFSET_ZPOS]);
        float X1 = Float.intBitsToFloat(polygonBuffer[polygonOffset + polygonVertexSize + POLYGON_OFFSET_XPOS]);
        float Y1 = Float.intBitsToFloat(polygonBuffer[polygonOffset + polygonVertexSize + POLYGON_OFFSET_YPOS]);
        float Z1 = Float.intBitsToFloat(polygonBuffer[polygonOffset + polygonVertexSize + POLYGON_OFFSET_ZPOS]);
        float X2 = Float.intBitsToFloat(polygonBuffer[polygonOffset + polygonVertexSize * 2 + POLYGON_OFFSET_XPOS]);
        float Y2 = Float.intBitsToFloat(polygonBuffer[polygonOffset + polygonVertexSize * 2 + POLYGON_OFFSET_YPOS]);
        float Z2 = Float.intBitsToFloat(polygonBuffer[polygonOffset + polygonVertexSize * 2 + POLYGON_OFFSET_ZPOS]);

        val vectors = VECTORS.get();
        vectors.A.set(X1 - X0, Y1 - Y0, Z1 - Z0);
        vectors.B.set(X2 - X1, Y2 - Y1, Z2 - Z1);
        Vector3f.cross(vectors.A, vectors.B, vectors.C);

        polygonBuffer[polygonOffset + polygonVertexSize * 3] = PolygonNormal.fromVector(vectors.C).ordinal();

        if (triangulate) {
            float X3 = Float.intBitsToFloat(polygonBuffer[polygonOffset + polygonVertexSize * 3 + 1 + POLYGON_OFFSET_XPOS]);
            float Y3 = Float.intBitsToFloat(polygonBuffer[polygonOffset + polygonVertexSize * 3 + 1 + POLYGON_OFFSET_YPOS]);
            float Z3 = Float.intBitsToFloat(polygonBuffer[polygonOffset + polygonVertexSize * 3 + 1 + POLYGON_OFFSET_ZPOS]);
            vectors.A.set(X0 - X3, Y0 - Y3, Z0 - Z3);
            vectors.B.set(X2 - X0, Y2 - Y0, Z2 - Z0);
            Vector3f.cross(vectors.A, vectors.B, vectors.C);

            polygonBuffer[polygonOffset + polygonVertexSize * 6 + 1] = PolygonNormal.fromVector(vectors.C).ordinal();
        }

        return false;
    }
}
