package graphql.schema.universe;

import graphql.Internal;
import org.jspecify.annotations.Nullable;

import java.util.BitSet;

/**
 * A fixed-size arena chunk whose vertex slots may be reclaimed.
 */
@Internal
public final class VertexChunk {

    private static final VertexChunk EMPTY = new VertexChunk(0);

    private final @Nullable SUVertex[] vertices;
    private int retainedCount;

    /**
     * Creates an allocated chunk with the requested number of nullable slots.
     *
     * @param size the number of slots
     */
    public VertexChunk(int size) {
        this.vertices = new @Nullable SUVertex[size];
    }

    /**
     * Returns the shared unallocated chunk marker.
     *
     * @return the empty chunk
     */
    public static VertexChunk empty() {
        return EMPTY;
    }

    /**
     * Returns whether this chunk has allocated slots.
     *
     * @return {@code true} for an allocated chunk
     */
    public boolean isAllocated() {
        return vertices.length != 0;
    }

    /**
     * Returns one vertex slot.
     *
     * @param index the index within the chunk
     *
     * @return the stored vertex, or {@code null} for a reclaimed slot
     */
    public @Nullable SUVertex get(int index) {
        return vertices[index];
    }

    /**
     * Updates one vertex slot.
     *
     * @param index the index within the chunk
     * @param vertex the vertex, or {@code null} to reclaim the slot
     */
    public void set(int index, @Nullable SUVertex vertex) {
        SUVertex previous = vertices[index];
        if (previous == null && vertex != null) {
            retainedCount++;
        }
        if (previous != null && vertex == null) {
            retainedCount--;
        }
        vertices[index] = vertex;
    }

    /**
     * Reclaims every slot not marked in the supplied live-ID set.
     *
     * @param liveVertexIds IDs retained by registered schemas
     * @param firstId the global ID of slot zero
     * @param slotCount the allocated slots within the current ID watermark
     *
     * @return the number of slots reclaimed
     */
    public int reclaimUnmarked(
            BitSet liveVertexIds,
            int firstId,
            int slotCount) {
        int removed = 0;
        for (int offset = 0; offset < slotCount; offset++) {
            SUVertex vertex = vertices[offset];
            if (vertex == null || liveVertexIds.get(firstId + offset)) {
                continue;
            }
            set(offset, null);
            removed++;
        }
        return removed;
    }

    /**
     * Returns whether this chunk contains any retained vertices.
     *
     * @return {@code true} when at least one slot is occupied
     */
    public boolean hasVertices() {
        return retainedCount != 0;
    }
}
