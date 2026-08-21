package graphql.schema.universe;

import graphql.Internal;
import org.jspecify.annotations.Nullable;

import java.util.Set;

import static graphql.Assert.assertNotNull;

/**
 * An immutable persistent map from integer keys to non-null values.
 *
 * <p>The map is a bitmap-compressed hash-array mapped trie with a logical branching factor of 32.
 * Updating one key copies only nodes on its trie path.</p>
 *
 * @param <V> the stored value type
 */
@Internal
public final class PersistentIntMap<V> {

    private static final PersistentIntMap<Object> EMPTY = new PersistentIntMap<>(null);

    private final @Nullable IntMapNode<V> root;

    /**
     * Creates a map around an immutable trie root.
     *
     * @param root the trie root, or {@code null} for an empty map
     */
    public PersistentIntMap(@Nullable IntMapNode<V> root) {
        this.root = root;
    }

    /**
     * Returns the shared empty map.
     *
     * @param <V> the stored value type
     *
     * @return the empty map
     */
    @SuppressWarnings("unchecked")
    public static <V> PersistentIntMap<V> empty() {
        return (PersistentIntMap<V>) EMPTY;
    }

    /**
     * Returns the value stored for a key.
     *
     * @param key the integer key
     *
     * @return the value, or {@code null} when absent
     */
    public @Nullable V get(int key) {
        if (root == null) {
            return null;
        }
        return root.get(key, mix(key), 0);
    }

    /**
     * Returns a map associating a key with a value.
     *
     * @param key the integer key
     * @param value the non-null value
     *
     * @return this map for an identity no-op, otherwise a replacement
     */
    public PersistentIntMap<V> put(int key, V value) {
        V nonNullValue = assertNotNull(value);
        int hash = mix(key);
        IntMapNode<V> newRoot = root == null
                ? new IntMapLeaf<>(key, hash, nonNullValue)
                : root.put(key, hash, nonNullValue, 0);
        return root == newRoot ? this : new PersistentIntMap<>(newRoot);
    }

    /**
     * Returns a map without a key.
     *
     * @param key the integer key
     *
     * @return this map when absent, otherwise a replacement
     */
    public PersistentIntMap<V> remove(int key) {
        if (root == null) {
            return this;
        }
        IntMapNode<V> newRoot = root.remove(key, mix(key), 0);
        return root == newRoot ? this : new PersistentIntMap<>(newRoot);
    }

    /**
     * Visits every entry.
     *
     * @param visitor the entry visitor
     */
    public void forEachEntry(IntMapEntryVisitor<V> visitor) {
        assertNotNull(visitor);
        if (root != null) {
            root.forEachEntry(visitor);
        }
    }

    /**
     * Visits entries not already covered by a structurally shared node.
     *
     * @param visitedNodes identity-based set of previously visited nodes
     * @param visitor the entry visitor
     */
    public void visitUniqueEntries(
            Set<IntMapNode<V>> visitedNodes,
            IntMapEntryVisitor<V> visitor) {
        assertNotNull(visitedNodes);
        assertNotNull(visitor);
        if (root != null) {
            root.visitEntries(visitedNodes, visitor);
        }
    }

    /**
     * Mixes an integer key into a trie hash.
     *
     * @param value the integer key
     *
     * @return a one-to-one mixed hash
     */
    public static int mix(int value) {
        int result = value;
        result ^= result >>> 16;
        result *= 0x7feb352d;
        result ^= result >>> 15;
        result *= 0x846ca68b;
        result ^= result >>> 16;
        return result;
    }
}
