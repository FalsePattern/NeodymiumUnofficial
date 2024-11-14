package makamys.neodymium.renderer;

import static makamys.neodymium.Constants.LOGGER;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import gnu.trove.list.array.TIntArrayList;
import lombok.val;
import makamys.neodymium.Neodymium;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import makamys.neodymium.config.Config;
import makamys.neodymium.ducks.NeodymiumWorldRenderer;
import makamys.neodymium.util.BufferWriter;
import makamys.neodymium.util.Util;
import makamys.neodymium.util.WarningHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;

/** A mesh for a 16x16x16 region of the world. */
public class ChunkMesh extends Mesh {
    
    WorldRenderer wr;
    private int tesselatorDataCount;

    private int[] subMeshStart = new int[NORMAL_ORDER.length]; 
    
    public static final AtomicLong usedRAM = new AtomicLong();
    public static final AtomicInteger instances = new AtomicInteger();

    public static final ThreadLocal<PolygonMeshBuffer> polygonBuf = ThreadLocal.withInitial(PolygonMeshBuffer::new);

    private static final PolygonNormal[] NORMAL_ORDER = new PolygonNormal[] {PolygonNormal.NONE, PolygonNormal.POSITIVE_Y, PolygonNormal.POSITIVE_X, PolygonNormal.POSITIVE_Z, PolygonNormal.NEGATIVE_X, PolygonNormal.NEGATIVE_Z, PolygonNormal.NEGATIVE_Y};
    private static final int[] POLYGON_NORMAL_TO_NORMAL_ORDER;
    private static final int[] NORMAL_ORDER_TO_POLYGON_NORMAL;

    private static final Flags FLAGS = new Flags(true, true, true, false);
    
    static {
        POLYGON_NORMAL_TO_NORMAL_ORDER = new int[PolygonNormal.values().length];
        NORMAL_ORDER_TO_POLYGON_NORMAL = new int[PolygonNormal.values().length];
        for(int i = 0; i < PolygonNormal.values().length; i++) {
            int idx = Arrays.asList(NORMAL_ORDER).indexOf(PolygonNormal.values()[i]);
            if(idx == -1) {
                idx = 0;
            }
            POLYGON_NORMAL_TO_NORMAL_ORDER[i] = idx;
            NORMAL_ORDER_TO_POLYGON_NORMAL[idx] = i;
        }
    }
    
    public ChunkMesh(WorldRenderer wr, int pass) {
        this.x = wr.posX / 16;
        this.y = wr.posY / 16;
        this.z = wr.posZ / 16;
        this.wr = wr;
        this.pass = pass;
        Arrays.fill(subMeshStart, -1);
        
        instances.getAndIncrement();
        
        if(!polygonBuf.get().isEmpty()) {
            LOGGER.error("Invalid state: tried to construct a chunk mesh before the previous one has finished constructing!");
        }
    }

    public void addTessellatorData(Tessellator t) {
        tesselatorDataCount++;
        
        if(t.vertexCount == 0) {
            // Sometimes the tessellator has no vertices and weird flags. Don't warn in this case, just silently return.
            return;
        }
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if(t.drawMode != GL11.GL_QUADS && t.drawMode != GL11.GL_TRIANGLES) {
            errors.add("Unsupported draw mode: " + t.drawMode);
        }
        boolean triangulate = t.drawMode == GL11.GL_QUADS;
        if (drawMode == -1) {
            drawMode = GL11.GL_TRIANGLES;
            verticesPerPolygon = 3;
        }

        if(!t.hasTexture) {
            errors.add(String.format("Texture data is missing."));
        }
        if(!t.hasBrightness) {
            warnings.add("Brightness data is missing");
        }
        if(!t.hasColor) {
            warnings.add("Color data is missing");
        }
        // TODO This opengl call crashes the JVM when not run on the client thread.
//        if(t.hasNormals && GL11.glIsEnabled(GL11.GL_LIGHTING)) {
//            errors.add("Chunk uses GL lighting, this is not implemented.");
//        }
        FLAGS.hasBrightness = t.hasBrightness;
        FLAGS.hasColor = t.hasColor;

        int inputVertices = triangulate ? 4 : 3;
        int outputVertexMultiplier = triangulate ? 2 : 1;

        int tessellatorVertexSize = Neodymium.util.vertexSizeInTessellator();
        int polygonSize = Neodymium.util.polygonSize(3) * outputVertexMultiplier;

        int polygonCount = t.vertexCount / inputVertices;

        val buf = polygonBuf.get();
        buf.ensureCapacity(polygonCount * polygonSize);
        for(int polygonI = 0; polygonI < polygonCount; polygonI++) {
            boolean deleted = MeshPolygon.processPolygon(t.rawBuffer, polygonI * inputVertices * tessellatorVertexSize, buf.data, buf.size, NeoRegion.toRelativeOffset(-t.xOffset), NeoRegion.toRelativeOffset(-t.yOffset), NeoRegion.toRelativeOffset(-t.zOffset), triangulate, FLAGS);
            if (!deleted) {
                buf.size += polygonSize;
            }
        }
        
        if(!buf.isEmpty()) {
            // Only show errors if we're actually supposed to be drawing something
            if(!errors.isEmpty() || !warnings.isEmpty()) {
                if(!Config.silenceErrors) {
                    String dimId = wr.worldObj != null && wr.worldObj.provider != null ? "" + wr.worldObj.provider.dimensionId : "UNKNOWN";
                    if(!errors.isEmpty()) {
                        LOGGER.error("Errors in chunk ({}, {}, {}) in dimension {}:", x, y, z, dimId);
                        for(String error : errors) {
                            LOGGER.error("Error: " + error);
                        }
                        for(String warning : warnings) {
                            LOGGER.error("Warning: " + warning);
                        }
                        LOGGER.error("(World renderer pos: ({}, {}, {}), Tessellator pos: ({}, {}, {}), Tessellation count: {}", wr.posX, wr.posY, wr.posZ, t.xOffset, t.yOffset, t.zOffset, tesselatorDataCount);
                        LOGGER.error("Stack trace:");
                        try {
                            // Generate a stack trace
                            throw new IllegalArgumentException();
                        } catch(IllegalArgumentException e) {
                            e.printStackTrace();
                        }
                        LOGGER.error("Skipping chunk due to errors.");
                        buf.reset();
                    } else {
                        WarningHelper.showDebugMessageOnce(String.format("Warnings in chunk (%d, %d, %d) in dimension %s: %s", x, y, z, dimId, String.join(", ", warnings)));
                    }
                }
            }
        }
    }
    
    private static String tessellatorToString(Tessellator t) {
        return "(" + t.xOffset + ", " + t.yOffset + ", " + t.zOffset + ")";
    }

    private int bufferSize = 0;
    @Override
    public int bufferSize() {
        return bufferSize;
    }

    public void finishConstruction() {
        val buf = polygonBuf.get();
        polygonCount = buf.size / Neodymium.util.polygonSize(verticesPerPolygon);
        buffer = createBuffer(buf.data);
        bufferSize = buffer.limit();
        usedRAM.getAndAdd(bufferSize);

        buf.reset();
    }

    //Used by FalseTweaks when cancelling a threaded render job
    public static void cancelRendering() {
        val buf = polygonBuf.get();
        if (!buf.isEmpty()) {
            buf.reset();
            LOGGER.debug("Cancelled unfinished render pass!");
        }
    }

    private static final ThreadLocal<MeshPolygonBucketSort> threadBucketer = ThreadLocal.withInitial(
            MeshPolygonBucketSort::new);

    private ByteBuffer createBuffer(int[] polygons) {
        val stride = Neodymium.renderer.getStride();
        ByteBuffer buffer = BufferUtils.createByteBuffer(polygonCount * verticesPerPolygon * stride);
        BufferWriter out = new BufferWriter(buffer);
        
        boolean sortByNormals = pass == 0;

        val polygonSize = Neodymium.util.polygonSize(verticesPerPolygon);
        int[] indices = null;
        if(sortByNormals) {
            indices = threadBucketer.get().sort(polygons, polygonSize, polygonCount);
        }

        for (int i = 0; i < polygonCount; i++) {
            int index = indices != null ? indices[i] : i;
            int subMeshStartIdx = sortByNormals ? POLYGON_NORMAL_TO_NORMAL_ORDER[polygons[(index + 1) * polygonSize - 1]] : 0;
            if(subMeshStart[subMeshStartIdx] == -1) {
                subMeshStart[subMeshStartIdx] = i;
            }
            Neodymium.util.writeMeshPolygonToBuffer(polygons, index * polygonSize, out, stride, verticesPerPolygon);
        }

        
        buffer.flip();
        return buffer;
    }
    
    void destroy() {
        if(buffer != null) {
            usedRAM.getAndAdd(-buffer.limit());
            instances.getAndDecrement();
            buffer = null;
            
            if(gpuStatus == Mesh.GPUStatus.SENT) {
                gpuStatus = Mesh.GPUStatus.PENDING_DELETE;
            }
        }
    }
    
    @Override
    public void destroyBuffer() {
        destroy();
    }
    
    static List<ChunkMesh> getChunkMesh(int theX, int theY, int theZ) {
        WorldRenderer wr = new WorldRenderer(Minecraft.getMinecraft().theWorld, new ArrayList<TileEntity>(), theX * 16, theY * 16, theZ * 16, 100000);
    
        wr.isWaitingOnOcclusionQuery = false;
        wr.isVisible = true;
        wr.isInFrustum = true;
        wr.chunkIndex = 0;
        wr.markDirty();
        wr.updateRenderer(Minecraft.getMinecraft().thePlayer);
        return ((NeodymiumWorldRenderer)wr).nd$getChunkMeshes();
    }
    
    @Override
    public int writeToIndexBuffer(IntBuffer piFirst, IntBuffer piCount, int cameraXDiv, int cameraYDiv, int cameraZDiv, int pass) {
        if(!Config.cullFaces) {
            return super.writeToIndexBuffer(piFirst, piCount, cameraXDiv, cameraYDiv, cameraZDiv, pass);
        }
        
        int renderedMeshes = 0;
        
        int startIndex = -1;
        for(int i = 0; i < NORMAL_ORDER.length + 1; i++) {
            if(i < subMeshStart.length && subMeshStart[i] == -1) continue;
            
            PolygonNormal normal = i < NORMAL_ORDER.length ? NORMAL_ORDER[i] : null;
            boolean isVisible = normal != null && isNormalVisible(normal, cameraXDiv, cameraYDiv, cameraZDiv, pass);
            
            if(isVisible && startIndex == -1) {
                startIndex = subMeshStart[POLYGON_NORMAL_TO_NORMAL_ORDER[normal.ordinal()]];
            } else if(!isVisible && startIndex != -1) {
                int endIndex = i < subMeshStart.length ? subMeshStart[i] : polygonCount;
                
                piFirst.put(iFirst + (startIndex*verticesPerPolygon));
                piCount.put((endIndex - startIndex)*verticesPerPolygon);
                renderedMeshes++;
                
                startIndex = -1;
            }
        }
        
        return renderedMeshes;
    }
    
    private boolean isNormalVisible(PolygonNormal normal, int interpXDiv, int interpYDiv, int interpZDiv, int pass) {
        switch(normal) {
        case POSITIVE_X:
            return interpXDiv >= ((x + 0));
        case NEGATIVE_X:
            return interpXDiv < ((x + 1));
        case POSITIVE_Y:
            return interpYDiv >= ((y + 0));
        case NEGATIVE_Y:
            return interpYDiv < ((y + 1));
        case POSITIVE_Z:
            return interpZDiv >= ((z + 0));
        case NEGATIVE_Z:
            return interpZDiv < ((z + 1));
        default:
            return pass != 0 || Config.maxUnalignedPolygonDistance == Integer.MAX_VALUE
            || Util.distSq(interpXDiv, interpYDiv, interpZDiv, x, y, z) < Math.pow((double)Config.maxUnalignedPolygonDistance, 2);
        }
    }
    
    public double distSq(Entity player) {
        int centerX = x * 16 + 8;
        int centerY = y * 16 + 8;
        int centerZ = z * 16 + 8;
        
        return player.getDistanceSq(centerX, centerY, centerZ); 
    }

    public static class Flags {
        public boolean hasTexture;
        public boolean hasBrightness;
        public boolean hasColor;
        public boolean hasNormals;
        
        public Flags(byte flags) {
            hasTexture = (flags & 1) != 0;
            hasBrightness = (flags & 2) != 0;
            hasColor = (flags & 4) != 0;
            hasNormals = (flags & 8) != 0;
        }
        
        public Flags(boolean hasTexture, boolean hasBrightness, boolean hasColor, boolean hasNormals) {
            this.hasTexture = hasTexture;
            this.hasBrightness = hasBrightness;
            this.hasColor = hasColor;
            this.hasNormals = hasNormals;
        }
        
        public byte toByte() {
            byte flags = 0;
            if(hasTexture) {
                flags |= 1;
            }
            if(hasBrightness) {
                flags |= 2;
            }
            if(hasColor) {
                flags |= 4;
            }
            if(hasNormals) {
                flags |= 8;
            }
            return flags;
        }
    }

    public static class PolygonMeshBuffer {
        public int[] data = new int[1024];
        public int size = 0;

        public void ensureCapacity(int maxNewAmount) {
            int newSize = size + maxNewAmount;
            if (newSize > data.length) {
                data = Arrays.copyOf(data, newSize);
            }
        }

        public boolean isEmpty() {
            return size == 0;
        }

        public void reset() {
            size = 0;
        }
    }

    public static class MeshPolygonBucketSort {
        private static final int bucketCount = NORMAL_ORDER_TO_POLYGON_NORMAL.length;
        private final TIntArrayList[] buckets;
        private int[] resultBuffer;

        public MeshPolygonBucketSort() {
            buckets = new TIntArrayList[bucketCount];
            for (int i = 0; i < bucketCount; i++) {
                buckets[i] = new TIntArrayList();
            }
        }

        private static int bucket(int[] buffer, int polygonSize, int index) {
            return buffer[polygonSize * (index + 1) - 1];
        }

        public int[] sort(int[] buffer, int polygonSize, int polygonCount) {
            for (int i = 0; i < bucketCount; i++) {
                buckets[i].resetQuick();
            }
            for (int i = 0; i < polygonCount; i++) {
                buckets[bucket(buffer, polygonSize, i)].add(i);
            }
            if (resultBuffer == null || resultBuffer.length < polygonCount) {
                resultBuffer = new int[polygonCount];
            }
            val result = resultBuffer;
            int offset = 0;
            for (int i = 0; i < bucketCount; i++) {
                val bucket = buckets[NORMAL_ORDER_TO_POLYGON_NORMAL[i]];
                val size = bucket.size();
                bucket.toArray(result, 0, offset, size);
                offset += size;
            }
            assert offset == polygonCount;
            return result;
        }
    }

}

