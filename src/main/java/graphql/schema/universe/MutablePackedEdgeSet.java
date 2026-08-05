package graphql.schema.universe;

import graphql.Internal;

import java.util.Arrays;

import static graphql.Assert.assertTrue;

/**
 * A transient mutable copy of one source vertex's outgoing edges.
 *
 * <p>{@link SUSchemaBuilder} creates this object lazily when a source vertex is first changed.
 * Additions may be unsorted and may contain exact duplicates while editing. {@link #freeze()}
 * canonicalizes the data into an immutable {@link PackedEdgeSet}, folds exact duplicates, and
 * validates the cardinality rules declared by {@link SUEdgeKind}.</p>
 */
@Internal
public final class MutablePackedEdgeSet {

    private long[] edges;
    private int size;

    /**
     * Creates a mutable copy of an immutable edge set.
     *
     * @param original the source edge set
     */
    public MutablePackedEdgeSet(PackedEdgeSet original) {
        this.edges = original.copyEdges();
        this.size = edges.length;
    }

    /**
     * Appends a packed edge.
     *
     * <p>Ordering and duplicate handling are deferred until {@link #freeze()}.</p>
     *
     * @param edge the packed edge
     */
    public void add(long edge) {
        ensureCapacity(size + 1);
        edges[size++] = edge;
    }

    /**
     * Replaces the first exact occurrence of a packed edge without changing its position.
     *
     * @param currentEdge the edge to replace
     * @param replacementEdge the replacement edge
     *
     * @return {@code true} when the current edge was present
     */
    public boolean replace(long currentEdge, long replacementEdge) {
        for (int i = 0; i < size; i++) {
            if (edges[i] != currentEdge) {
                continue;
            }
            edges[i] = replacementEdge;
            return true;
        }
        return false;
    }

    /**
     * Removes the first exact occurrence of a packed edge.
     *
     * @param edge the packed edge
     */
    public void remove(long edge) {
        for (int i = 0; i < size; i++) {
            if (edges[i] != edge) {
                continue;
            }
            removeAt(i);
            return;
        }
    }

    /**
     * Removes every edge with the given kind and target name.
     *
     * @param kind the relationship kind
     * @param nameId the target name ID
     */
    public void removeByName(SUEdgeKind kind, int nameId) {
        int i = 0;
        while (i < size) {
            long edge = edges[i];
            boolean matches = PackedEdgeSet.edgeKind(edge) == kind
                    && PackedEdgeSet.edgeNameId(edge) == nameId;
            if (!matches) {
                i++;
                continue;
            }
            removeAt(i);
        }
    }

    /**
     * Removes every edge of a relationship kind.
     *
     * @param kind the relationship kind
     */
    public void removeKind(SUEdgeKind kind) {
        int i = 0;
        while (i < size) {
            if (PackedEdgeSet.edgeKind(edges[i]) != kind) {
                i++;
                continue;
            }
            removeAt(i);
        }
    }

    /**
     * Produces a canonical immutable edge set.
     *
     * <p>The result is grouped by edge kind. Unordered kinds are sorted, ordered kinds retain first
     * encounter order, exact duplicates are removed, and cardinality rules are validated.</p>
     *
     * @return the immutable edge set
     */
    public PackedEdgeSet freeze() {
        if (size == 0) {
            return PackedEdgeSet.empty();
        }
        long[] canonical = Arrays.copyOf(edges, size);
        Arrays.sort(canonical);
        int uniqueSize = removeExactDuplicates(canonical);
        canonical = Arrays.copyOf(canonical, uniqueSize);
        restoreOrderedRanges(canonical);
        validateCardinality(canonical);
        return new PackedEdgeSet(canonical);
    }

    private int removeExactDuplicates(long[] sorted) {
        int writeIndex = 1;
        for (int readIndex = 1; readIndex < sorted.length; readIndex++) {
            if (sorted[readIndex] == sorted[writeIndex - 1]) {
                continue;
            }
            sorted[writeIndex++] = sorted[readIndex];
        }
        return writeIndex;
    }

    private void restoreOrderedRanges(long[] canonical) {
        for (SUEdgeKind kind : SUEdgeKind.values()) {
            if (!kind.isOrdered()) {
                continue;
            }
            restoreOrderedRange(canonical, kind);
        }
    }

    private void restoreOrderedRange(long[] canonical, SUEdgeKind kind) {
        int start = firstIndex(canonical, kind);
        if (start == canonical.length) {
            return;
        }
        int end = endIndex(canonical, start, kind);
        long[] sortedRange = Arrays.copyOfRange(canonical, start, end);
        boolean[] emitted = new boolean[sortedRange.length];
        int writeIndex = start;
        for (int i = 0; i < size; i++) {
            long edge = edges[i];
            if (PackedEdgeSet.edgeKind(edge) != kind) {
                continue;
            }
            int sortedIndex = Arrays.binarySearch(sortedRange, edge);
            assertTrue(sortedIndex >= 0, "Ordered edge is missing from its canonical range");
            if (emitted[sortedIndex]) {
                continue;
            }
            canonical[writeIndex++] = edge;
            emitted[sortedIndex] = true;
        }
        assertTrue(writeIndex == end, "Ordered edge range was not completely restored");
    }

    private int firstIndex(long[] canonical, SUEdgeKind kind) {
        for (int i = 0; i < canonical.length; i++) {
            if (PackedEdgeSet.edgeKind(canonical[i]) == kind) {
                return i;
            }
        }
        return canonical.length;
    }

    private int endIndex(long[] canonical, int start, SUEdgeKind kind) {
        int index = start;
        while (index < canonical.length
                && PackedEdgeSet.edgeKind(canonical[index]) == kind) {
            index++;
        }
        return index;
    }

    private void validateCardinality(long[] canonical) {
        for (int i = 1; i < canonical.length; i++) {
            long previous = canonical[i - 1];
            long current = canonical[i];
            SUEdgeKind kind = PackedEdgeSet.edgeKind(current);
            if (kind != PackedEdgeSet.edgeKind(previous)) {
                continue;
            }
            assertTrue(!kind.isSingle(), "Edge kind %s only allows one target", kind);
            int nameId = PackedEdgeSet.edgeNameId(current);
            boolean duplicateName = nameId == PackedEdgeSet.edgeNameId(previous);
            assertTrue(!kind.isUniqueName() || !duplicateName, "Edge kind %s requires unique target names", kind);
        }
    }

    private void ensureCapacity(int requiredSize) {
        if (requiredSize <= edges.length) {
            return;
        }
        int newSize = Math.max(requiredSize, Math.max(4, edges.length * 2));
        edges = Arrays.copyOf(edges, newSize);
    }

    private void removeAt(int index) {
        int moved = size - index - 1;
        if (moved > 0) {
            System.arraycopy(edges, index + 1, edges, index, moved);
        }
        size--;
    }
}
