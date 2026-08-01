package graphql.schema.universe;

import graphql.Internal;
import org.jspecify.annotations.NullMarked;

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
@NullMarked
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
     * <p>The result is sorted, exact duplicates are removed, single-valued edge kinds are checked,
     * and kinds requiring unique target names are validated.</p>
     *
     * @return the immutable edge set
     */
    public PackedEdgeSet freeze() {
        if (size == 0) {
            return PackedEdgeSet.empty();
        }
        long[] sorted = Arrays.copyOf(edges, size);
        Arrays.sort(sorted);
        int uniqueSize = removeExactDuplicates(sorted);
        validateCardinality(sorted, uniqueSize);
        return new PackedEdgeSet(Arrays.copyOf(sorted, uniqueSize));
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

    private void validateCardinality(long[] sorted, int uniqueSize) {
        for (int i = 1; i < uniqueSize; i++) {
            long previous = sorted[i - 1];
            long current = sorted[i];
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
