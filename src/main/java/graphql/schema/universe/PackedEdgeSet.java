package graphql.schema.universe;

import graphql.Internal;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;

import static graphql.Assert.assertTrue;

/**
 * The immutable outgoing adjacency of one source vertex.
 *
 * <p>Each edge occupies one {@code long} with this layout:</p>
 *
 * <pre>
 *  63          56 55                  32 31                    0
 * +--------------+----------------------+-----------------------+
 * | edge kind    | target name ID       | target vertex ID      |
 * | 8 bits       | 24 bits              | 32 bits               |
 * +--------------+----------------------+-----------------------+
 * </pre>
 *
 * <p>The packed values are grouped by kind. Unordered kinds are sorted by name ID and target ID,
 * while ordered kinds retain attachment order. Exact and name-based lookups use binary search for
 * unordered kinds and scan the normally small ordered ranges. Instances are values in
 * {@link PersistentEdgeMap}; unchanged source vertices share the same instance across schema
 * snapshots.</p>
 */
@Internal
@NullMarked
public final class PackedEdgeSet {

    private static final int MAX_NAME_ID = 0x00ff_ffff;
    private static final long TARGET_MASK = 0xffff_ffffL;
    private static final long NAME_MASK = 0x00ff_ffffL;
    private static final PackedEdgeSet EMPTY = new PackedEdgeSet(new long[0]);

    private final long[] edges;

    /**
     * Creates a set that takes ownership of an already grouped and validated edge array.
     *
     * <p>The array is not copied and must not be modified after construction. General callers
     * should build through {@link MutablePackedEdgeSet#freeze()}.</p>
     *
     * @param edges kind-grouped, unique packed edges
     */
    public PackedEdgeSet(long[] edges) {
        this.edges = edges;
    }

    /**
     * Returns the shared empty edge set.
     *
     * @return the empty edge set
     */
    public static PackedEdgeSet empty() {
        return EMPTY;
    }

    /**
     * Packs an edge into its compact long representation.
     *
     * @param kind the relationship kind
     * @param nameId the universe-interned target name ID, or zero for an unnamed target
     * @param targetId the target vertex ID
     *
     * @return the packed edge
     */
    public static long pack(SUEdgeKind kind, int nameId, int targetId) {
        assertTrue(nameId >= 0 && nameId <= MAX_NAME_ID, "Schema universe name id is too large: %s", nameId);
        assertTrue(targetId >= 0, "Schema universe target id must be positive: %s", targetId);
        return ((long) kind.getCode() << 56)
                | ((long) nameId << 32)
                | (targetId & TARGET_MASK);
    }

    /**
     * Returns the number of edges.
     *
     * @return the edge count
     */
    public int size() {
        return edges.length;
    }

    /**
     * Reports whether this set contains no edges.
     *
     * @return {@code true} when empty
     */
    public boolean isEmpty() {
        return edges.length == 0;
    }

    /**
     * Tests for one exact edge.
     *
     * @param kind the relationship kind
     * @param nameId the target name ID
     * @param targetId the target vertex ID
     *
     * @return {@code true} when the exact edge exists
     */
    public boolean contains(SUEdgeKind kind, int nameId, int targetId) {
        long edge = pack(kind, nameId, targetId);
        int start = firstIndex(kind);
        int end = endIndex(kind);
        if (!kind.isOrdered()) {
            return Arrays.binarySearch(edges, start, end, edge) >= 0;
        }
        for (int i = start; i < end; i++) {
            if (edges[i] == edge) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds a target by relationship kind and target name.
     *
     * <p>For edge kinds that permit duplicate names, this returns the first target in attachment
     * order for ordered kinds and target-ID order for unordered kinds.</p>
     *
     * @param kind the relationship kind
     * @param nameId the target name ID
     *
     * @return the target vertex ID, or {@code -1} when absent
     */
    public int targetByName(SUEdgeKind kind, int nameId) {
        int start = firstIndex(kind);
        int end = endIndex(kind);
        if (kind.isOrdered()) {
            for (int i = start; i < end; i++) {
                if (edgeNameId(edges[i]) == nameId) {
                    return targetId(edges[i]);
                }
            }
            return -1;
        }
        long prefix = prefix(kind, nameId);
        int index = lowerBound(prefix, start, end);
        if (index == end || edgePrefix(edges[index]) != prefix) {
            return -1;
        }
        return targetId(edges[index]);
    }

    /**
     * Returns the first target for an edge kind.
     *
     * @param kind the relationship kind
     *
     * @return the first target vertex ID, or {@code -1} when absent
     */
    public int firstTarget(SUEdgeKind kind) {
        int index = firstIndex(kind);
        if (index == edges.length || edgeKind(edges[index]) != kind) {
            return -1;
        }
        return targetId(edges[index]);
    }

    /**
     * Returns the inclusive array index at which an edge kind begins.
     *
     * @param kind the relationship kind
     *
     * @return the first possible index for the kind
     */
    public int firstIndex(SUEdgeKind kind) {
        return lowerBound(prefix(kind, 0));
    }

    /**
     * Returns the exclusive array index at which an edge kind ends.
     *
     * @param kind the relationship kind
     *
     * @return the index immediately after the kind's edges
     */
    public int endIndex(SUEdgeKind kind) {
        long nextKindPrefix = (long) (kind.getCode() + 1) << 56;
        return lowerBound(nextKindPrefix);
    }

    /**
     * Extracts the target vertex ID at an array index.
     *
     * @param index an index between {@link #firstIndex(SUEdgeKind)} and
     *              {@link #endIndex(SUEdgeKind)}
     *
     * @return the target vertex ID
     */
    public int targetIdAt(int index) {
        return targetId(edges[index]);
    }

    /**
     * Extracts the target name ID at an array index.
     *
     * @param index an index between {@link #firstIndex(SUEdgeKind)} and
     *              {@link #endIndex(SUEdgeKind)}
     *
     * @return the target name ID
     */
    public int targetNameIdAt(int index) {
        return edgeNameId(edges[index]);
    }

    /**
     * Copies the packed edge array for mutable editing.
     *
     * @return a new array containing all packed edges
     */
    public long[] copyEdges() {
        return Arrays.copyOf(edges, edges.length);
    }

    /**
     * Extracts the relationship kind from a packed edge.
     *
     * @param edge a packed edge
     *
     * @return the relationship kind
     */
    public static SUEdgeKind edgeKind(long edge) {
        return SUEdgeKind.fromCode((int) (edge >>> 56));
    }

    /**
     * Extracts the target name ID from a packed edge.
     *
     * @param edge a packed edge
     *
     * @return the target name ID
     */
    public static int edgeNameId(long edge) {
        return (int) ((edge >>> 32) & NAME_MASK);
    }

    /**
     * Extracts the target vertex ID from a packed edge.
     *
     * @param edge a packed edge
     *
     * @return the target vertex ID
     */
    public static int targetId(long edge) {
        return (int) edge;
    }

    private static long prefix(SUEdgeKind kind, int nameId) {
        return ((long) kind.getCode() << 56) | ((long) nameId << 32);
    }

    private static long edgePrefix(long edge) {
        return edge & 0xffff_ffff_0000_0000L;
    }

    private int lowerBound(long value) {
        return lowerBound(value, 0, edges.length);
    }

    private int lowerBound(long value, int start, int end) {
        int low = start;
        int high = end;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (edges[middle] < value) {
                low = middle + 1;
                continue;
            }
            high = middle;
        }
        return low;
    }
}
