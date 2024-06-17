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

    public static boolean processPolygon(int[] tessBuffer, int tessOffset, int[] polygonBuffer, int polygonOffset, float offsetX, float offsetY, float offsetZ, int vertices, ChunkMesh.Flags flags) {
        val util = Neodymium.util;
        util.readMeshPolygon(tessBuffer, tessOffset, polygonBuffer, polygonOffset, offsetX, offsetY, offsetZ, vertices, flags);
        int stride = util.vertexSizeInPolygonBuffer();
        boolean deleted = true;
        for (int i = 1; i < vertices; i++) {
            int offset = polygonOffset + stride * i;
            if (polygonBuffer[polygonOffset + POLYGON_OFFSET_XPOS] != polygonBuffer[offset + POLYGON_OFFSET_XPOS] ||
                polygonBuffer[polygonOffset + POLYGON_OFFSET_YPOS] != polygonBuffer[offset + POLYGON_OFFSET_YPOS] ||
                polygonBuffer[polygonOffset + POLYGON_OFFSET_ZPOS] != polygonBuffer[offset + POLYGON_OFFSET_ZPOS]) {
                deleted = false;
                break;
            }
        }

        if (deleted)
            return true;

        float X0 = Float.intBitsToFloat(polygonBuffer[polygonOffset + POLYGON_OFFSET_XPOS]);
        float Y0 = Float.intBitsToFloat(polygonBuffer[polygonOffset + POLYGON_OFFSET_YPOS]);
        float Z0 = Float.intBitsToFloat(polygonBuffer[polygonOffset + POLYGON_OFFSET_ZPOS]);
        float X1 = Float.intBitsToFloat(polygonBuffer[polygonOffset + stride + POLYGON_OFFSET_XPOS]);
        float Y1 = Float.intBitsToFloat(polygonBuffer[polygonOffset + stride + POLYGON_OFFSET_YPOS]);
        float Z1 = Float.intBitsToFloat(polygonBuffer[polygonOffset + stride + POLYGON_OFFSET_ZPOS]);
        float X2 = Float.intBitsToFloat(polygonBuffer[polygonOffset + stride * 2 + POLYGON_OFFSET_XPOS]);
        float Y2 = Float.intBitsToFloat(polygonBuffer[polygonOffset + stride * 2 + POLYGON_OFFSET_YPOS]);
        float Z2 = Float.intBitsToFloat(polygonBuffer[polygonOffset + stride * 2 + POLYGON_OFFSET_ZPOS]);

        val vectors = VECTORS.get();
        vectors.A.set(X1 - X0, Y1 - Y0, Z1 - Z0);
        vectors.B.set(X2 - X1, Y2 - Y1, Z2 - Z1);
        Vector3f.cross(vectors.A, vectors.B, vectors.C);

        polygonBuffer[polygonOffset + stride * vertices] = PolygonNormal.fromVector(vectors.C).ordinal();

        return false;
    }
}
