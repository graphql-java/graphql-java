package graphql.schema.universe;

import graphql.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Set;

import static graphql.Assert.assertNotNull;

/**
 * An immutable map from source vertex IDs to complete outgoing {@link PackedEdgeSet}s.
 *
 * <p>The map is implemented as a bitmap-compressed hash-array mapped trie with a logical branching
 * factor of 32. Updating one source copies only the nodes on its trie path; unaffected branches and
 * edge sets remain shared between {@link SUSchema} snapshots.</p>
 *
 * <p>Only non-empty edge sets are stored. Looking up an absent source returns the shared empty edge
 * set, and putting an empty set removes the source binding. The integer mixer is a permutation:
 * XOR-shifts are reversible and multiplication by an odd integer is invertible modulo
 * {@code 2^32}. Distinct source IDs therefore cannot produce a full-hash collision.</p>
 */
@Internal
@NullMarked
public final class PersistentEdgeMap {

    private static final PersistentEdgeMap EMPTY = new PersistentEdgeMap(null);

    private final @Nullable IntMapNode<PackedEdgeSet> root;

    /**
     * Creates a map around an immutable trie root.
     *
     * @param root the trie root, or {@code null} for an empty map
     */
    public PersistentEdgeMap(@Nullable IntMapNode<PackedEdgeSet> root) {
        this.root = root;
    }

    /**
     * Returns the shared empty map.
     *
     * @return the empty map
     */
    public static PersistentEdgeMap empty() {
        return EMPTY;
    }

    /**
     * Returns all outgoing edges stored for a source vertex.
     *
     * @param key the source vertex ID
     *
     * @return the stored edge set, or the shared empty set when absent
     */
    public PackedEdgeSet get(int key) {
        if (root == null) {
            return PackedEdgeSet.empty();
        }
        PackedEdgeSet result = root.get(key, PersistentIntMap.mix(key), 0);
        return result == null ? PackedEdgeSet.empty() : result;
    }

    /**
     * Returns a map associating a source vertex with its complete outgoing edge set.
     *
     * <p>An empty value removes the binding. Supplying the same value instance for an existing key
     * is an identity no-op and returns this map.</p>
     *
     * @param key the source vertex ID
     * @param value the complete outgoing edge set
     *
     * @return this map for a no-op, otherwise a structurally shared replacement
     */
    public PersistentEdgeMap put(int key, PackedEdgeSet value) {
        if (value.isEmpty()) {
            return remove(key);
        }
        int hash = PersistentIntMap.mix(key);
        IntMapNode<PackedEdgeSet> newRoot = root == null
                ? new IntMapLeaf<>(key, hash, value)
                : root.put(key, hash, value, 0);
        return root == newRoot ? this : new PersistentEdgeMap(newRoot);
    }

    /**
     * Returns a map without the source vertex binding.
     *
     * @param key the source vertex ID
     *
     * @return this map when absent, otherwise a structurally shared replacement
     */
    public PersistentEdgeMap remove(int key) {
        if (root == null) {
            return this;
        }
        IntMapNode<PackedEdgeSet> newRoot =
                root.remove(key, PersistentIntMap.mix(key), 0);
        return root == newRoot ? this : new PersistentEdgeMap(newRoot);
    }

    /**
     * Visits every entry not already covered by a structurally shared node in another map.
     *
     * <p>The supplied set must use identity equality. Reusing it across schema snapshots avoids
     * revisiting unchanged HAMT subtrees.</p>
     *
     * @param visitedNodes identity-based set of previously visited nodes
     * @param visitor the entry visitor
     */
    public void visitUniqueEntries(
            Set<IntMapNode<PackedEdgeSet>> visitedNodes,
            IntMapEntryVisitor<PackedEdgeSet> visitor) {
        assertNotNull(visitedNodes);
        assertNotNull(visitor);
        if (root != null) {
            root.visitEntries(visitedNodes, visitor);
        }
    }
}
